# TASKS — `RF-AC-058` a `RF-AC-061` La portada de una clase en vivo, y la clase sin curso

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-058` a `RF-AC-061` |
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
| `T-01` | `V105`: instructores, portada, sin curso; cuatro permisos y el retiro de `courses:list-own` | — | La migración aplica | Hecha |
| `T-02` | Repositorios: la lista de instructores, la portada, sin curso | `T-01` | — | Hecha |
| `T-03` | Programar, corregir, leer y la vitrina con instructores y sin curso | `T-02` | — | Hecha |
| `T-04` | `LiveSessionCoverService` y las cuatro rutas | `T-02` | — | Hecha |
| `T-05` | Retirar `RF-AC-057` | `T-01` | — | Hecha |
| `T-06` | Las suites de clases en vivo sin curso; `LiveSessionInstructorsIT`, `LiveSessionCoverIT`; las que cuentan el catálogo; `EndpointPermissionsIT` | `T-03` a `T-05` | `CA-AC-317` a `CA-AC-323`; suite completa en verde | Hecha |
| `T-07` | Contrato, `api/index.md` | `T-06` | — | Hecha |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-317` a `CA-AC-319`, `CA-AC-322` | `T-02`, `T-03`, `T-06` |
| `CA-AC-320`, `CA-AC-321` | `T-04`, `T-06` |
| `CA-AC-323` | `T-01`, `T-05`, `T-06` |

## 3. Bloqueos

Ninguno.

## Desviaciones respecto del plan

- **`OwnLiveSessionsIT` y `InstructorReadsIT` se reescribieron** en lugar de nacer `LiveSessionInstructorsIT`: los criterios `CA-AC-318` y `CA-AC-319` son las mismas rutas propias con la regla nueva, y `CA-AC-317` y `CA-AC-322` caben en `LiveSessionAdminIT`.
- **Por `/mine`, quien programa también se comprueba** con `InstructorVerifier`: tiene que portar `courses:teach`, como cualquier instructor nombrado. En la práctica lo porta, porque `live-sessions:create-own` se siembra donde está `courses:teach`.
- **La auditoría de `AC` que limpian las suites** incluye ahora `live_sessions`, y la limpieza de portadas huérfanas mira también `live_sessions.cover_image_id`.
