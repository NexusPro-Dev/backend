# PLAN — `RF-AC-033` Consultar el catálogo de cursos como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-033` |
| Especificación | [`spec.md`](spec.md), aprobada el 26-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026; reescrito el 26-09-2026 |

---

## 1. Enfoque

**El aula lee lo mismo que administración y se queda con menos.** La sentencia de candidatos reutiliza el bloque de columnas del listado (`JpaCourseQueryRepository.COLUMNAS`) —instructor por `JOIN users`, las cuentas que `CourseOfferability` necesita, las de membresías y servicios— acotado a vivos y `ACTIVOS`, y le añade **tres cuentas que el aula estrena**: la duración, las lecciones y las lecciones abiertas **ofrecibles de los módulos ofrecibles**. El filtro «se ofrece» se aplica **en Java con `CourseOfferability`**, no en un `WHERE` (`spec.md` §14.1): la regla existe una vez.

**Lo que sí se escribe una vez en SQL es «lección ofrecible» y «módulo ofrecible»**, porque son cuentas. Ya existen como fragmentos constantes (`JpaCourseModuleQueryRepository.LECCION_OFRECIBLE` y `MODULO_OFRECIBLE`), que el listado, la categoría y el detalle usan; las tres cuentas nuevas se escriben sobre ellos.

**`accessible` lo decide `StudentAccess`**, un objeto de dominio que nace aquí y sirven las tres vistas: «el curso no declara llaves, o la vigente está en su lista, o tiene vigente uno de sus servicios», y para la lección «o está abierta». Las llaves de los cursos que quedan se leen en **una** sentencia —membresías y servicios con un `UNION ALL` y una columna que dice cuál es cuál—, agrupadas en Java. Lo del alumno lo dan **dos puertos de `SP`**: `CurrentMembershipLookup`, que existe desde el 27-08-2026, y **`CurrentProductsLookup`**, que nace aquí (`ac.md` §3, `sp.md` 1.87.0 §8).

**`onlyAccessible` se aplica después de `StudentAccess`** (`spec.md` §14.4): conserva lo que es `accessible` o tiene `openLessonCount > 0`.

**Controlador propio.** `ClassroomController` bajo `/api/v1/courses/available`, separado de `CourseController`: otro actor, otro permiso, otra forma. Spring resuelve el segmento literal antes que `/{id}` por especificidad, y `CA-AC-193` lo fija por prueba.

## 2. Cambios de esquema

**Ninguno en tablas.** `V47` siembra los dos permisos nuevos del aula (`RF-AC-034`, `RF-AC-035`), no los de este requerimiento: `courses:learn` existe desde `V22`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` (`SP`) | **`CurrentProductsLookup`** — `Set<UUID> currentProductIdsOf(UUID userId)`: los productos de las filas de `user_products` empezadas, sin fin pasado y sin cerrar | `SP` |
| `domain/repository` (`SP`) | **`JpaCurrentProductsLookup`** — una sentencia sobre `user_products` | `SP` |
| `domain/models` | **`StudentAccess`** — `courseAccessible(membresíaVigente, productosVigentes, membresíasDelCurso, serviciosDelCurso)` y `lessonAccessible(cursoAccesible, abierta)` | `AC` |
| `domain/repository` | `CourseQueryRepository` + `Jpa…`: **`findClassroomCandidates(categoryId, difficulty)`** —vivos y `ACTIVOS`, `COLUMNAS` y las tres cuentas del alumno— y **`findKeysOfCourses(ids)`** —membresías y servicios de varios cursos en una sentencia—; `findCategoriesOfCourses(ids)` de `RF-AC-016` | `AC` |
| `domain/repository` | `CourseCategoryQueryRepository`: **`findAlive()`**, en orden, sin cuentas | `AC` |
| `domain/service` | **`StudentKeys`** —lo que el alumno trae: membresía vigente y productos vigentes, pedidos a los dos puertos—; **`GetClassroomCatalogService`** | `AC` |
| `application` | `ClassroomCatalogRequest` (`categoryId`, `difficulty`, `onlyAccessible`), **`ClassroomCatalogResponse`** con `CurrentMembershipRef`, `ClassroomCategoryItem` y `ClassroomCourseItem` | `AC` |
| `interfaces` | **`ClassroomController`** — `GET /api/v1/courses/available` | `AC` |

**El instructor por `JOIN users` de lectura**, con el precedente de `RF-PM-012` y `RF-AC-009`; **la membresía y los productos vigentes por los puertos**, porque «vigente» es una regla de `SP` que este módulo no reimplementa.

## 4. Contrato de API

`GET /api/v1/courses/available?categoryId=&difficulty=&onlyAccessible=` — `courses:learn`.

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
      "totalDurationSeconds": 840, "lessonCount": 7, "openLessonCount": 2, "accessible": false
    }
  ]
}
```

| Código | Cuándo |
|---|---|
| `400` | `VAL-001`, `VAL-002` |
| `401` / `403` | Sin sesión / sin `courses:learn` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:learn')")`. El actor sale del token; no hay parámetro de persona. Entra en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT`.

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Cuatro sentencias fijas** —candidatos, llaves de los que quedan, categorías de los que quedan, categorías vivas— **más las de los dos puertos**. Con cero candidatos, la segunda y la tercera no se ejecutan (`CA-AC-192`).

## 8. Impacto sobre otros módulos

**`SP` publica `CurrentProductsLookup`** —interfaz y adaptador en paquetes de `SP`, como `PermissionHolderLookup`—; `sp.md` 1.87.0 §8 lo recoge. Ningún otro cambio fuera de `AC`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Filtrar «se ofrece» en el `WHERE`** | Dos definiciones de `RN-AC-015` (`spec.md` §14.1) |
| **`accessible` con un `EXISTS` y las llaves del alumno como parámetros** | Tercera forma de decir «está en la lista»; `StudentAccess` lo decide para las tres vistas |
| **Una sentencia para membresías y otra para servicios** | Cinco sentencias en vez de cuatro para leer dos listas del mismo tamaño; el `UNION ALL` con una columna de tipo cuesta nada |
| **`CurrentProductsLookup.holds(userId, productId)`** | Una llamada por curso y servicio; el catálogo es una lista |
| **Esconder lo cerrado siempre** | `ac.md` §5.2.13 |
| **Paginar** | `spec.md` §14.2 |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La cuenta de módulos ofrecibles del listado y la del aula divergen** | Los mismos fragmentos SQL; `CA-AC-186` prueba cada motivo |
| 2 | **`accessible` compara niveles** en lugar de pertenencia | `StudentAccess` no recibe el nivel; `CA-AC-188` |
| 3 | **Un servicio vencido o futuro abre** | La vigencia la decide `SP`; `CA-AC-237` prueba los tres casos |
| 4 | **La ruta `/available` cae en `/{id}`** | `CA-AC-193` |

## 11. Estrategia de prueba

- **Unitarias** (`StudentAccessTest`): sin llaves; membresía en la lista; fuera; servicio en la lista; ninguno; la lección abierta.
- **Integración de API** (`ClassroomCatalogIT`): `CA-AC-186` a `CA-AC-193`, `CA-AC-237` y `CA-AC-238`; **la que define el requerimiento es `CA-AC-238`**, «los cursos que puedo ver».
- **De sentencias**: `CA-AC-192`, con cero y con varios.
- **Del puerto** (`CurrentProductsLookupIT`): vigente, vencido, cerrado, futuro, y la fila de nivel sin producto que no aparece.
