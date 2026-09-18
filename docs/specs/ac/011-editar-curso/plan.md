# PLAN — `RF-AC-011` Editar curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-011` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`RF-AC-004` con siete campos y los dos puertos del alta.** Se hereda entera la mecánica —`Patchable` con sus tres estados, validación conjunta antes de consultar, unicidad del título con `existsAliveTitleForOther`, bloqueo de fila, auditoría de lo que cambió, relectura del detalle— y se añade la reasignación del instructor, que reutiliza **la misma comprobación del alta** extraída a un colaborador del módulo, `InstructorVerifier`, para que las dos operaciones no puedan divergir en qué exigen.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `UpdateCourseRequest` — siete `Patchable`: `title`, `instructorId` (`Patchable<UUID>`), `difficulty` (`Patchable<CourseDifficulty>`), `shortDescription`, `longDescription`, `introVideoUrl`, `displayOrder`; `informaAlgo()` | `AC` |
| `domain/models` | `Course.update(...)` que devuelve el mapa de cambios y avanza `updatedAt` solo si no está vacío; el video pasa por `VideoUrl` | `AC` |
| `domain/repository` | `CourseRepository.existsAliveTitleForOther(title, id)` | `AC` |
| `domain/service` | **`InstructorVerifier`** —`UserCatalog` + `PermissionHolderLookup`, con los dos `422`—, extraído de `RegisterCourseService` y usado por los dos; `UpdateCourseService` | `AC` |
| `interfaces` | `CourseController` — `PATCH /api/v1/courses/{id}` | `AC` |

## 4. Contrato de API

`PATCH /api/v1/courses/{id}` — `courses:update`. Cuerpo con cualquiera de los siete; `200` con `CourseDetailResponse`.

- `shortDescription: null`, `longDescription: null` e `introVideoUrl: null` **vacían**; `title: null`, `instructorId: null`, `difficulty: null` y `displayOrder: null` son `400`, juntos.
- Cuerpo vacío: `400` `VAL-007`. Campo desconocido: `400` `VAL-008`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-008` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-002` |
| `409` | `EX-001` |
| `422` | `EX-003`, `EX-004` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`UPDATE` con solo los campos que cambiaron, `instructor_id` con antes y después cuando se reasigna. Sin fila si no cambió nada.

## 7. Transaccionalidad

`@Transactional`. El curso vivo con `FOR UPDATE`; el título contra otros si viene; el instructor por los dos puertos si viene; escritura y auditoría solo si algo cambió; relectura del detalle.

## 8. Impacto sobre otros módulos

**Ninguno.** Los dos puertos de `SP` ya existen desde `RF-AC-008`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Desactivar el curso al vaciar una descripción** | Cambiaría el estado que alguien decidió por un hecho (`spec.md` §14.1, `RN-PM-040` como precedente) |
| **Comprobar el instructor en el agregado** | Cruza a `SP`; el agregado no tiene puertos. Vive en el caso de uso, compartido por `InstructorVerifier` |
| **Hacer inmutable al instructor** | Un instructor que se va y un curso que se queda son el caso normal; reasignar es corregir |
| **Comprobar el instructor solo si cambia** | Reasignar al mismo repite la comprobación y no audita; es más simple que distinguir, y un instructor que perdió el permiso y se «reasigna» a sí mismo recibe `422`, que es lo honesto |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El alta y la corrección divergen** en qué exigen del instructor | `InstructorVerifier` es el único sitio; `CA-AC-036` y `CA-AC-061` prueban los mismos tres casos |
| 2 | **El nulo del instructor se trata como ausente** | `Patchable<UUID>` con `PatchableUuidDeserializer`; `CA-AC-058` |

## 11. Estrategia de prueba

- **Unitaria**: `Course.update` — cada campo, los tres nulos que vacían, los cuatro que se rechazan, sin cambios, el video mal formado.
- **Integración de API** (`CourseUpdateIT`): los siete criterios; **la que define el requerimiento es `CA-AC-061`**, la reasignación con los tres `422` y la auditoría del cambio.
