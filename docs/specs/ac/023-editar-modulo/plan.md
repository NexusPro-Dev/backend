# PLAN — `RF-AC-023` Editar módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-023` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`UpdateCourseService` con cinco campos, sin puertos y con el padre en la ruta.** La mecánica de `RF-AC-011` entera —`Patchable`, validación conjunta antes de consultar, unicidad frente a otros con `existsAliveTitleInCourseForOther`, bloqueo de fila, auditoría de lo que cambió, relectura— sobre `CourseModule`. **La fila se resuelve por `(courseId, moduleId)`**: `findAliveByIdInCourseForUpdate`, que devuelve vacío si el módulo es de otro curso, y ese vacío es el `404`.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `UpdateCourseModuleRequest` — cinco `Patchable`; `informaAlgo()` | `AC` |
| `domain/models` | `CourseModule.update(...)` | `AC` |
| `domain/repository` | `CourseModuleRepository.existsAliveTitleInCourseForOther(courseId, title, moduleId)` y `findAliveByIdInCourseForUpdate(courseId, moduleId)` | `AC` |
| `domain/service` | `UpdateCourseModuleService` | `AC` |
| `interfaces` | `CourseModuleController` — `PATCH /api/v1/courses/{courseId}/modules/{moduleId}` | `AC` |

## 4. Contrato de API

`PATCH /api/v1/courses/{courseId}/modules/{moduleId}` — `courses:update`. Cuerpo con cualquiera de los cinco; `200` con `CourseModuleDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-006` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-002` |
| `409` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`UPDATE` de `course_modules` con solo lo que cambió. Sin fila si nada.

## 7. Transaccionalidad

`@Transactional`. El módulo del curso con `FOR UPDATE`; el título contra otros si viene; escritura y auditoría si cambió; relectura.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Bloquear también el curso** | Nada del curso cambia; el orden de bloqueo padre → hijo solo hace falta a quien toca los dos |
| **Resolver el módulo por identificador y comprobar el curso después** | Dos lecturas donde basta una con los dos predicados; y un `404` que dijera «es de otro curso» revelaría más de lo que la ruta afirma |
| **Mover de curso** | `RN-AC-019` |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La unicidad choca consigo misma** al cambiar la caja | `existsAliveTitleInCourseForOther` excluye el propio; `CA-AC-097` |

## 11. Estrategia de prueba

- **Unitaria**: `CourseModule.update` — cada campo, los nulos que vacían y los que se rechazan, sin cambios.
- **Integración de API** (`CourseModuleUpdateIT`): los cinco criterios; **la que define el requerimiento es `CA-AC-097`**, el `404` del módulo de otro curso.
