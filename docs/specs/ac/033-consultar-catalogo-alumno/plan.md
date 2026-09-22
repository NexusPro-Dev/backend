# PLAN — `RF-AC-033` Consultar el catálogo de cursos como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-033` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**El aula lee lo mismo que administración y se queda con menos.** La sentencia de cursos es la del listado (`RF-AC-009`) acotada a vivos y `ACTIVOS`, con las mismas subconsultas que `CourseOfferability` necesita —descripciones, cuántas membresías, cuántos módulos ofrecibles— más dos que el aula estrena: **la duración y la cuenta de las lecciones ofrecibles** de los módulos ofrecibles. El filtro «se ofrece» se aplica **en Java con `CourseOfferability`**, no en un `WHERE`: es la decisión de `spec.md` §14.1, y lo que compra es que la regla exista una sola vez y que enmendarla —como hoy, con dos motivos nuevos— no obligue a buscarla en dos sitios.

**Lo que sí se escribe una vez en SQL es «lección ofrecible»** —activa, viva, con contenido— y «módulo ofrecible» —activo, vivo, con al menos una lección ofrecible—, porque son cuentas y las cuentas se hacen en la base. Van en **un fragmento constante** del repositorio (`JpaCourseQueryRepository.LECCION_OFRECIBLE`, `MODULO_OFRECIBLE`) que reutilizan el listado, la categoría, el detalle y las tres lecturas del aula: cuatro sentencias que dicen lo mismo con el mismo texto.

**`accessible` lo decide `StudentAccess`**, un objeto de dominio que nace aquí y sirven las tres vistas: «la vigente está en la lista del curso», y para la lección «o está abierta». Las membresías de los cursos que quedan se leen en una sentencia por lista, agrupadas en Java; la vigente la da `CurrentMembershipLookup`, el puerto que `SP` publica desde el 27-08-2026 y que aquí se consume **tal cual** (`ac.md` §3).

**Controlador propio.** `ClassroomController` bajo `/api/v1/courses/available`, separado de `CourseController`: otro actor, otro permiso, otra forma. Spring resuelve el segmento literal antes que `/{id}` por especificidad y no por orden de declaración, y `CA-AC-193` lo fija por prueba, como `ProductCommentController` lo dejó escrito para `PM`.

## 2. Cambios de esquema

**Ninguno.** Los índices de las tres relaciones (`RF-AC-016`, `RF-AC-018`, `RF-AC-020`) y los de `course_modules` y `lessons` por padre sostienen las subconsultas.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | **`StudentAccess`** — `courseAccessible(currentMembershipId, membershipIds)` y `lessonAccessible(courseAccessible, open)`; sin vigente, falso | `AC` |
| `domain/repository` | `CourseQueryRepository` + `Jpa…`: **`findOfferedCandidates(categoryId, difficulty)`** —vivos y `ACTIVOS`, con instructor por `JOIN users`, las entradas de `CourseOfferability` y la duración y cuenta de lo ofrecible—, `findCategoriesOfCourses(ids)` (de `RF-AC-016`), **`findMembershipIdsOfCourses(ids)`**; los fragmentos `LECCION_OFRECIBLE` y `MODULO_OFRECIBLE` extraídos y reutilizados por el listado, la categoría y el detalle | `AC` |
| `domain/repository` | `CourseCategoryQueryRepository`: `findAlive()` en orden, sin cuentas | `AC` |
| `domain/service` | **`ClassroomCatalogReader`**: puerto, candidatos, `CourseOfferability` por fila, categorías y membresías de los que quedan, `StudentAccess`; **`GetClassroomCatalogService`** | `AC` |
| `application` | `ClassroomCatalogRequest` (`categoryId`, `difficulty`), **`ClassroomCatalogResponse`** con `CurrentMembershipRef`, `ClassroomCategoryItem` y `ClassroomCourseItem` | `AC` |
| `interfaces` | **`ClassroomController`** — `GET /api/v1/courses/available` | `AC` |
| `SP` (`application`) | `CurrentMembershipLookup`, consumido sin cambio | `SP` |

**El instructor por `JOIN users` de lectura**, con el precedente de `RF-PM-012` y de `RF-AC-009`; **la membresía vigente por el puerto**, porque «vigente» es una regla de `SP` que este módulo no reimplementa (`CurrentMembershipLookup`, Javadoc).

## 4. Contrato de API

`GET /api/v1/courses/available?categoryId=&difficulty=` — `courses:learn`.

```json
{
  "currentMembership": { "id": "…", "code": "ORO", "name": "Oro", "color": "D4AF37" },
  "categories": [
    { "id": "…", "name": "Trading", "color": "1E88E5", "icon": "chart", "displayOrder": 0, "coverImageUrl": null }
  ],
  "courses": [
    {
      "id": "…", "title": "Velas japonesas", "instructor": { "id": "…", "username": "…", "fullName": "…" },
      "difficulty": "PRINCIPIANTE", "shortDescription": "…", "displayOrder": 0,
      "coverImageUrl": "/api/v1/academy-images/…",
      "categories": [{ "id": "…", "name": "Trading", "color": "1E88E5", "icon": "chart" }],
      "totalDurationMinutes": 84, "lessonCount": 7, "accessible": true
    }
  ]
}
```

| Código | Cuándo |
|---|---|
| `400` | `VAL-001`, `VAL-002` |
| `401` / `403` | Sin sesión / sin `courses:learn` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:learn')")`. El actor sale del token; no hay parámetro de persona.

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Cuatro sentencias fijas** —candidatos, categorías de los que quedan, membresías de los que quedan, categorías vivas— **más las del puerto**, que son de `SP` y no se cuentan aquí. Con cero candidatos, la segunda y la tercera no se ejecutan (`CA-AC-192`).

## 8. Impacto sobre otros módulos

**Ninguno en código.** `CurrentMembershipLookup` ya existe y se consume tal cual. Dentro de `AC`, el listado (`RF-AC-009`), los cursos de la categoría (`RF-AC-003`/`RF-AC-016`) y el detalle (`RF-AC-010`) pasan a usar los fragmentos `LECCION_OFRECIBLE` y `MODULO_OFRECIBLE` — es una refactorización sin cambio de resultado, con sus suites en verde como condición.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Filtrar «se ofrece» en el `WHERE`** | Dos definiciones de `RN-AC-015` (`spec.md` §14.1); la del aula sería la que nadie enmendaría |
| **`accessible` con un `EXISTS` y la vigente como parámetro** | Tercera forma de decir «está en la lista»; `StudentAccess` lo decide para las tres vistas |
| **Reutilizar `CourseDetailReader` por curso** | Seis sentencias por curso; el catálogo es una lista |
| **La ruta dentro de `CourseController`** | Otro actor, otro permiso, otra forma; la especificidad de Spring no depende de en qué clase esté |
| **Paginar** | `spec.md` §14.2 |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La cuenta de módulos ofrecibles del listado y la del aula divergen** | Un solo fragmento SQL para «lección ofrecible» y «módulo ofrecible», y `CA-AC-194` compara el aula con el detalle de administración |
| 2 | **`accessible` compara niveles** en lugar de pertenencia | `StudentAccess` no recibe el nivel; `CA-AC-188` lo prueba con `ORO` y `PLATINO` |
| 3 | **La ruta `/available` cae en `/{id}`** | `CA-AC-193` |

## 11. Estrategia de prueba

- **Unitarias**: `StudentAccess` (sin vigente, vigente en la lista, vigente fuera, abierta); `CourseOfferability` con los cinco motivos ya está en `RF-AC-008`.
- **Integración de API** (`ClassroomCatalogIT`): `CA-AC-186` a `CA-AC-193`; **la que define el requerimiento es `CA-AC-187`**, la marca por membresía.
- **De concordancia** (`ClassroomOfferabilityAgreementIT`): `CA-AC-194`, recorriendo las combinaciones de estado, descripciones, membresías, módulos y lecciones y comparando la presencia en el aula con `offerable` del detalle.
- **De sentencias**: `CA-AC-192`, con cero y con varios.
