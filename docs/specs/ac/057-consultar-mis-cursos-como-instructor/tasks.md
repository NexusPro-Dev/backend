# TASKS — `RF-AC-057` Consultar los cursos que dicto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-057` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 13-10-2026 |
| Estado | **En revisión** — `T-01` a `T-04` `Hecha` el 13-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `CourseQueryRepository.findTaughtBy` y `ListTaughtCoursesService` | — | — | Hecha |
| `T-02` | `GET /api/v1/courses/mine` con su `@Operation` | `T-01`, `V104` (`RF-AC-056` · `T-01`) | — | Hecha |
| `T-03` | `TaughtCoursesIT` y `EndpointPermissionsIT` | `T-02` | `CA-AC-315`, `CA-AC-316`; suite completa en verde | Hecha |
| `T-04` | Contrato, `api/index.md` y la matriz | `T-03` | — | Hecha |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-315` | `T-01`, `T-03` |
| `CA-AC-316` | `T-02`, `T-03` |

## 3. Bloqueos

Ninguno.

## Desviaciones respecto del plan

- **Una suite para las dos lecturas**, `InstructorReadsIT`, en lugar de repartirlas en `OwnLiveSessionsIT` y otra de cursos: comparten al instructor, sus cursos y la siembra de `V104`.
- **Un controlador aparte**, `TaughtCourseController`, bajo la misma ruta base y la misma etiqueta que `CourseController`: aquel es la administración del catálogo, y este la vista del instructor.
