# PLAN — `RF-AC-034` Consultar el detalle de un curso como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-034` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**Las mismas lecturas que `CourseDetailReader`, otro lector.** `ClassroomCourseReader` llama a las lecturas del repositorio que `RF-AC-010` y sus enmiendas dejaron —curso con instructor, `findCategoriesOf`, `findRecommendedOf`, `findMembershipsOf`, `findModulesOf`, `findLessonsOfModules`— y **decide con los mismos objetos** —`CourseOfferability`, `ModuleOfferability` y la ofrecibilidad de la lección— qué se queda. Lo que administración devuelve marcado, el aula lo descarta; lo que administración suma sobre lo vivo, el aula lo suma sobre lo que queda. Es lo que hace que un módulo que administración ve `offerable: false` sea exactamente el que el alumno no ve, sin una prueba de concordancia aparte: **es el mismo objeto**.

**Una sola lectura del curso decide el `404`.** La sentencia del curso trae, además del instructor, las entradas de `CourseOfferability` —retiro, estado, descripciones, cuántas membresías, cuántos módulos ofrecibles con los fragmentos de `RF-AC-033`—, de modo que «no se ofrece» se sabe antes de leer nada más y con el mismo `ResourceNotFoundException` que «no existe».

**Los recomendados traen sus propias entradas.** `findRecommendedOf` ya devuelve estado y `offerable` para administración (`RF-AC-018`); el aula filtra por `offerable` y se queda con identificador, título, dificultad y portada.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | **`LessonOfferability`** — `offered(status, deleted, hasContent)`, el tercer hermano: es lo que `ModuleOfferability` ya cuenta, sacado a un sitio con nombre para que el árbol del aula y `RF-AC-035` lo pregunten igual | `AC` |
| `domain/repository` | `CourseQueryRepository`: **`findOfferedDetail(id)`** —curso vivo con instructor y entradas de `CourseOfferability`—; `findRecommendedOf` con dificultad y portada | `AC` |
| `domain/service` | **`ClassroomCourseReader`**: puerto, curso o `404`, relaciones y árbol por las lecturas de `RF-AC-010`, filtro por los tres objetos de ofrecibilidad, sumas, `StudentAccess`; **`GetClassroomCourseService`** | `AC` |
| `application` | **`ClassroomCourseResponse`** con `RecommendedCourseItem`, `MembershipRef` (de `RF-AC-010`), `ClassroomModuleItem`, `ClassroomLessonItem` | `AC` |
| `interfaces` | `ClassroomController` — `GET /api/v1/courses/available/{id}` | `AC` |

**`ModuleOfferability` pasa a contar lecciones ofrecibles y no lecciones activas**: es la enmienda de `RN-AC-015` del 18-09-2026 —«sin contenido» es motivo— y la construye `RF-AC-028` · `T-02` con su cuenta; aquí solo se consume.

## 4. Contrato de API

`GET /api/v1/courses/available/{id}` — `courses:learn`.

```json
{
  "id": "…", "title": "Velas japonesas",
  "instructor": { "id": "…", "username": "…", "fullName": "…" },
  "difficulty": "PRINCIPIANTE", "shortDescription": "…", "longDescription": "…",
  "introVideoUrl": "https://…", "displayOrder": 0, "coverImageUrl": null,
  "categories": [{ "id": "…", "name": "Trading", "color": "1E88E5", "icon": "chart" }],
  "recommendedCourses": [{ "id": "…", "title": "Antes de operar", "difficulty": "PRINCIPIANTE", "coverImageUrl": null }],
  "memberships": [{ "id": "…", "code": "ORO", "name": "Oro", "color": "D4AF37" }],
  "currentMembership": { "id": "…", "code": "BRONCE", "name": "Bronce", "color": "CD7F32" },
  "accessible": false,
  "modules": [
    {
      "id": "…", "title": "Lo básico", "shortDescription": "…", "longDescription": null,
      "presentationVideoUrl": null, "displayOrder": 0, "coverImageUrl": null, "durationMinutes": 30,
      "lessons": [
        { "id": "…", "type": "VIDEO", "title": "Qué es una vela", "description": "…",
          "durationMinutes": 12, "displayOrder": 0, "open": true, "accessible": true },
        { "id": "…", "type": "TEXTO", "title": "Los cuerpos", "description": null,
          "durationMinutes": 18, "displayOrder": 1, "open": false, "accessible": false }
      ]
    }
  ],
  "totalDurationMinutes": 30, "lessonCount": 2
}
```

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:learn` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:learn')")`.

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Seis sentencias fijas** —curso, categorías, recomendados, membresías, módulos, lecciones— más las del puerto; la de lecciones se cortocircuita sin módulos (`CA-AC-200`). El `404` cuesta una.

## 8. Impacto sobre otros módulos

**Ninguno.** Dentro de `AC`, `LessonOfferability` nace aquí y `ModuleOfferability` (`RF-AC-022`) pasa a apoyarse en él — refactorización sin cambio de resultado sobre la cuenta que `RF-AC-028` ya enmienda.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Filtrar la respuesta de `CourseDetailReader`** | `spec.md` §14.2 |
| **`404` distinto para «no se ofrece»** | `spec.md` §14.1 |
| **Sentencias del árbol con `WHERE` de ofrecibilidad** | Segunda definición de `RN-AC-015`; se lee lo vivo y deciden los objetos (`RF-AC-033` §14.1) |
| **Traer el contenido de las lecciones accesibles** | Un Markdown por lección en cada detalle; `RF-AC-035` lo sirve una a una y es donde se comprueba el acceso |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El aula enseña un módulo que administración ve no ofrecible** | Mismo objeto; `CA-AC-197` con cada motivo del módulo y de la lección |
| 2 | **`accessible` de la lección ignora `open`** | `StudentAccess.lessonAccessible` es la única forma de decidirlo; `CA-AC-198` |
| 3 | **Un curso que se ofrece pero al que se le vació una descripción** sale con `null` | Ya no se ofrece: «sin descripción» es motivo desde el 18-09-2026; `CA-AC-196` lo cubre |

## 11. Estrategia de prueba

- **Unitarias**: `LessonOfferability` (activa con contenido; inactiva; vacía; retirada); `ModuleOfferability` sobre él.
- **Integración de API** (`ClassroomCourseDetailIT`): los siete criterios; **la que define el requerimiento es `CA-AC-198`**, el curso cerrado que se enseña entero con la demostración abierta.
- **De sentencias**: `CA-AC-200`, con y sin módulos.
