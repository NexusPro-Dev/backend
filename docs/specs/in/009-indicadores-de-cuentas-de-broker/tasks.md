# TASKS — `RF-IN-009` Consultar los indicadores de cuentas de broker de la red

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-009` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 10-10-2026 |
| Estado | **En revisión** — `T-01` a `T-05` `Hecha` el 10-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V101`: el permiso nuevo con su guarda, el viejo fuera; los recuentos y listas por rol | — | Flyway aplica | **Hecha** — 10-10-2026 |
| `T-02` | `BrokerAccountFigures` y `JpaBrokerAccountFigures`: la fuerza comercial, las cifras por vendedor de origen y broker, los consumidores y los tramos | — | | **Hecha** — 10-10-2026 |
| `T-03` | `BrokerNetworkIndicatorsResponse`, `GetBrokerNetworkIndicatorsService` y `BrokerAccountIndicatorsController` con su `@Operation` | `T-02` | | **Hecha** — 10-10-2026 |
| `T-04` | Retirar de `SP` la ruta, el servicio, la respuesta, los conteos y `NetworkIndicatorsIT` | `T-03` | | **Hecha** — 10-10-2026 |
| `T-05` | `BrokerNetworkIndicatorsIT`: `CA-IN-104` a `CA-IN-110`; `EndpointPermissionsIT`; contrato; `api/index.md` | `T-01`, `T-03`, `T-04` | `mvn verify` en verde | **Hecha** — 10-10-2026 |

## 2. Orden de ejecución

`T-01` y `T-02` en paralelo → `T-03` → `T-04` → `T-05`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-104` a `CA-IN-107` | `T-02`, `T-03`, `T-05` |
| `CA-IN-108`, `CA-IN-109` | `T-03`, `T-05` |
| `CA-IN-110` | `T-01`, `T-04`, `T-05` |

## 4. Bloqueos

Ninguno.
