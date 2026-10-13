# TASKS — `RF-AC-056` Consultar el detalle de una clase en vivo de mi curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-056` |
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
| `T-01` | `V104`: `live-sessions:read-own` y `courses:list-own` donde está `courses:teach`, con las guardas | — | La migración aplica | Hecha |
| `T-02` | `ListLiveSessionsService.getOwn` y la ruta con su `@Operation` | `T-01` | — | Hecha |
| `T-03` | `OwnLiveSessionsIT`, `EndpointPermissionsIT` y las suites que cuentan el catálogo | `T-02` | `CA-AC-312` a `CA-AC-314`; suite completa en verde | Hecha |
| `T-04` | Contrato, `api/index.md`, `security.md` y la matriz | `T-03` | — | Hecha |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-312`, `CA-AC-313` | `T-02`, `T-03` |
| `CA-AC-314` | `T-01`, `T-03` |

## 3. Bloqueos

Ninguno.

## Desviaciones respecto del plan

- **Una suite para las dos lecturas**, `InstructorReadsIT`, en lugar de repartirlas en `OwnLiveSessionsIT` y otra de cursos: comparten al instructor, sus cursos y la siembra de `V104`.
