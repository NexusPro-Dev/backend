# PLAN — `RF-AC-034` Consultar el detalle de un curso como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-034` |
| Especificación | [`spec.md`](spec.md), aprobada el 26-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026; reescrito el 26-09-2026 |

---

## 1. Enfoque

**Las mismas lecturas que `CourseDetailReader`, otro lector.** `GetClassroomCourseService` llama a las lecturas del repositorio que `RF-AC-010` y sus enmiendas dejaron —`findDetail`, `findCategoriesOf`, `findRecommendedOf`, `findMembershipsOf`, `findProductsOf`, `findModulesOf`, `findLessonsOfModules`— y **decide con los mismos objetos** —`CourseOfferability`, `ModuleOfferability` y `LessonOfferability`— qué se queda. Lo que administración devuelve marcado, el aula lo descarta; lo que administración suma sobre lo vivo, el aula lo suma sobre lo que queda. Es el mismo objeto, y por eso el módulo que administración ve `offerable: false` es exactamente el que el alumno no ve.

**Una sola lectura del curso decide el `404`.** `findDetail` ya trae las entradas de `CourseOfferability`, de modo que «no se ofrece» se sabe antes de leer nada más y con el mismo `ResourceNotFoundException` que «no existe». Los puertos se preguntan **después**: el `404` no los necesita.

**La duración del módulo se recalcula en Java** sobre sus lecciones ofrecidas: la columna `duration_seconds` de `findModulesOf` suma las activas —con y sin contenido— para administración, y el alumno no ve una lección vacía.

**`accessible` con `StudentAccess` y `StudentKeys`** de `RF-AC-033`: las listas del curso son las que ya se leyeron para publicarlas.

## 2. Cambios de esquema

**`V47`** siembra `courses:read-available` (y `lessons:learn`, de `RF-AC-035`) en **todo rol que porte `courses:learn`**, con la guarda de conteo de toda migración de permisos (`security.md` §4.4).

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/service` | **`GetClassroomCourseService`**: curso o `404`, relaciones y árbol por las lecturas de `RF-AC-010`, filtro por los tres objetos de ofrecibilidad, sumas, `StudentKeys`, `StudentAccess` | `AC` |
| `application` | **`ClassroomCourseResponse`** con `RecommendedCourseItem`, `ClassroomModuleItem`, `ClassroomLessonItem`; `MembershipRef` y `ProductRef` del repositorio | `AC` |
| `interfaces` | `ClassroomController` — `GET /api/v1/courses/available/{id}` | `AC` |
| `db/migration` | **`V47__ac_permisos_del_aula.sql`** | `AC` |

## 4. Contrato de API

`GET /api/v1/courses/available/{id}` — `courses:read-available`.

```json
{
  "id": "…", "title": "Velas japonesas",
  "instructor": { "id": "…", "username": "…", "fullName": "…" },
  "difficulty": "PRINCIPIANTE", "shortDescription": "…", "longDescription": "…",
  "introVideoUrl": "https://youtu.be/…", "displayOrder": 0, "coverImageUrl": null,
  "categories": [{ "id": "…", "name": "Trading", "color": "1E88E5", "icon": "chart" }],
  "recommendedCourses": [],
  "memberships": [{ "id": "…", "code": "ORO", "name": "Oro", "color": "D4AF37" }],
  "products": [{ "id": "…", "code": "BOT_VELAS", "name": "Bot de velas" }],
  "currentMembership": { "id": "…", "code": "BRONCE", "name": "Bronce", "color": "CD7F32" },
  "accessible": false,
  "modules": [
    {
      "id": "…", "title": "Lo básico", "shortDescription": "…", "longDescription": null,
      "presentationVideoUrl": null, "displayOrder": 0, "coverImageUrl": null, "durationSeconds": 300,
      "lessons": [
        { "id": "…", "type": "VIDEO", "title": "Qué es una vela", "description": "…",
          "durationSeconds": 120, "displayOrder": 0, "open": true, "accessible": true },
        { "id": "…", "type": "TEXTO", "title": "Los cuerpos", "description": null,
          "durationSeconds": 180, "displayOrder": 1, "open": false, "accessible": false }
      ]
    }
  ],
  "totalDurationSeconds": 300, "lessonCount": 2, "openLessonCount": 1
}
```

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:read-available` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:read-available')")`. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Seis sentencias** —curso, categorías, membresías, servicios, módulos, lecciones— más las de los dos puertos; la de recomendados no consulta hasta `RF-AC-018` y la de lecciones se cortocircuita sin módulos (`CA-AC-200`). El `404` cuesta una.

## 8. Impacto sobre otros módulos

**`SP`**: `V47` escribe en `permissions` y `role_permissions`, como toda migración de permisos; el catálogo pasa de 134 a 136 (`security.md` 0.77.0).

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Filtrar la respuesta de `CourseDetailReader`** | `spec.md` §14.2 |
| **`404` distinto para «no se ofrece»** | `spec.md` §14.1 |
| **Sentencias del árbol con `WHERE` de ofrecibilidad** | Segunda definición de `RN-AC-015` |
| **Una columna nueva con la duración ofrecible del módulo** | La calcula Java sobre las lecciones que ya se leyeron, sin tocar la sentencia de administración |
| **Seguir con `courses:learn`** | `RN-SEG-014` (`spec.md` §14.3) |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El aula enseña un módulo que administración ve no ofrecible** | Mismo objeto; `CA-AC-197` con cada motivo del módulo y de la lección |
| 2 | **`accessible` de la lección ignora `open`** | `StudentAccess.lessonAccessible` es la única forma de decidirlo; `CA-AC-198` |
| 3 | **Un rol pierde el aula al separar el permiso** | `V47` concede a todo rol que ya porte `courses:learn` |

## 11. Estrategia de prueba

- **Integración de API** (`ClassroomCourseDetailIT`): los siete criterios; **la que define el requerimiento es `CA-AC-198`**, el curso cerrado que se enseña entero con la demostración abierta.
- **De sentencias**: `CA-AC-200`, con y sin módulos.
- **De siembra**: el catálogo en 136 y los dos códigos nuevos donde está `courses:learn`.
