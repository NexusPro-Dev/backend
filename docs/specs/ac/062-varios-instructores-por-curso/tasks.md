# TASKS — `RF-AC-062` Varios instructores por curso

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-062` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 13-10-2026 |
| Estado | **En revisión** — `T-01` a `T-07` `Hecha` el 13-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V106`: `course_instructors` rellenada, `courses` sin `instructor_id` | — | La migración aplica | Hecha |
| `T-02` | `Course` con la colección: crear, corregir, la instantánea | `T-01` | `CourseTest` | Hecha |
| `T-03` | Registrar y editar con `instructorIds` y la comprobación de cada uno | `T-02` | — | Hecha |
| `T-04` | Las lecturas con `instructors`, el filtro por pertenencia, el aula y el progreso | `T-01` | — | Hecha |
| `T-05` | La semilla de academia | `T-01` | `DevelopmentSeedIT` | Hecha |
| `T-06` | Las suites del curso con `instructorIds`; `CourseInstructorsIT`; el aula y el progreso | `T-03`, `T-04` | `CA-AC-324` a `CA-AC-329`; suite completa en verde | Hecha |
| `T-07` | Contrato, `api/index.md` | `T-06` | — | Hecha |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-324`, `CA-AC-325` | `T-02`, `T-03`, `T-06` |
| `CA-AC-326`, `CA-AC-327`, `CA-AC-328` | `T-04`, `T-06` |
| `CA-AC-329` | `T-01`, `T-06` |

## 3. Bloqueos

Ninguno.

## Desviaciones respecto del plan

- **El retiro del curso cuesta una sentencia más** (`CourseDeletionIT`, de 8 a 9): la instantánea de la auditoría lleva los instructores, y la colección se carga al leerla. Es lo que el riesgo 2 del plan anticipaba.
- **La suite completa del 13-10-2026 dio 122 errores en `system.auth`**, todos al limpiar `users` por una fila de `user_supervisors` que otra suite deja: es el fallo que la otra sesión de backend ya había visto antes de este cambio, y este no toca `user_supervisors`. El resto, en verde.
