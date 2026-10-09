# TASKS — `RF-AC-041` Consultar el progreso de un alumno en un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-041` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 09-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `SP`: `CommercialReach.principalClientsOf` y su implementación | — | `CommercialReachIT` | Pendiente |
| `T-02` | `ProgressAudience` | `T-01` | Integración por `T-06` | Pendiente |
| `T-03` | `LessonProgressRepository.findOfUserInCourse` | `RF-AC-039` · `T-03` | Integración por `T-06` | Pendiente |
| `T-04` | `GetStudentCourseProgressService` y `StudentCourseProgressResponse` | `T-02`, `T-03` | — | Pendiente |
| `T-05` | `CourseProgressController`: la ruta con `courses:read-progress` | `T-04` | La ruta entra en `PERMISO_DE_CADA_OPERACION` | Pendiente |
| `T-06` | `StudentCourseProgressIT` | `T-05` | `CA-AC-253` a `CA-AC-257` | Pendiente |
| `T-07` | OpenAPI, `docs/api/index.md` y la matriz | `T-06` | El contrato declara `200`, `400`, `401`, `403`, `404` | Pendiente |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-253`, `CA-AC-254` | `T-03`, `T-04`, `T-06` |
| `CA-AC-255`, `CA-AC-256` | `T-01`, `T-02`, `T-06` |
| `CA-AC-257` | `T-05`, `T-06` |

## 3. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 4. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
