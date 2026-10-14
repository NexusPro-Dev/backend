# TASKS — `RF-AC-067` a `RF-AC-072` Las señales de trading

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-067`, `RF-AC-068`, `RF-AC-069`, `RF-AC-070`, `RF-AC-071`, `RF-AC-072` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 14-10-2026 |
| Estado | **En revisión** — `T-01` a `T-11` `Hecha` el 14-10-2026 |
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
| `T-07` | `V109`: `direction`, retirar las existentes y `ck_signals_direction` | `T-01` | La migración aplica | Hecha |
| `T-08` | `SignalDirection`, `Signal.direccion`, las peticiones, las respuestas, el CSV y su ejemplo | `T-07` | `SignalTest`, `SignalCsvTest` | Hecha |
| `T-09` | `SignalsIT` con la dirección; contrato | `T-08` | `CA-AC-348`, `CA-AC-349` | Hecha |
| `T-10` | `V110` y `Signal.visibleUntil` hasta la medianoche del día de creación | `T-09` | `SignalTest` | Hecha |
| `T-11` | `SignalsIT` y la prosa del contrato con la ventana del día | `T-10` | `CA-AC-350` | Hecha |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-339` | `T-01`, `T-05` |
| `CA-AC-340` | `T-02` (reemplazado por `CA-AC-350`) |
| `CA-AC-350` | `T-10`, `T-11` |
| `CA-AC-341` a `CA-AC-343` | `T-03`, `T-04`, `T-05` |
| `CA-AC-344` a `CA-AC-347` | `T-03`, `T-04`, `T-05` |
| `CA-AC-348` | `T-08`, `T-09` |
| `CA-AC-349` | `T-07`, `T-09` |

## 3. Bloqueos

Ninguno.
