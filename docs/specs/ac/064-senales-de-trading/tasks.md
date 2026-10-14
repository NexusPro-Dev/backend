# TASKS — `RF-AC-067` a `RF-AC-072` Las señales de trading

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-067`, `RF-AC-068`, `RF-AC-069`, `RF-AC-070`, `RF-AC-071`, `RF-AC-072` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 14-10-2026 |
| Estado | **En revisión** — `T-01` a `T-06` `Hecha` el 14-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V108`: `signals`, `signal_memberships` y los seis permisos | — | La migración aplica | Hecha |
| `T-02` | `Signal`, `SubscriptionType`, `SignalRepository` y su implementación | `T-01` | `SignalTest` | Hecha |
| `T-03` | `SignalCsv` y `SignalService`: una, varias, CSV, retirar, listar y vigentes | `T-02` | `SignalCsvTest` | Hecha |
| `T-04` | `SignalController` con las seis rutas | `T-03` | — | Hecha |
| `T-05` | `SignalsIT`; las suites del catálogo y `EndpointPermissionsIT` | `T-04` | `CA-AC-339` a `CA-AC-347` | Hecha |
| `T-06` | Contrato, `api/index.md` | `T-05` | — | Hecha |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-339` | `T-01`, `T-05` |
| `CA-AC-340` | `T-02` |
| `CA-AC-341` a `CA-AC-343` | `T-03`, `T-04`, `T-05` |
| `CA-AC-344` a `CA-AC-347` | `T-03`, `T-04`, `T-05` |

## 3. Bloqueos

Ninguno.
