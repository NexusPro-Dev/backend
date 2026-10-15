# TASKS — `RF-IN-010` Consultar el resumen de cuentas de broker y de mi personal

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-010` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 15-10-2026 |
| Estado | **En revisión** — `T-01` a `T-04` `Hecha` el 15-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V115`: el permiso con su guarda; los recuentos y listas por rol | — | Flyway aplica | **Hecha** — 15-10-2026 |
| `T-02` | `BrokerAccountsSummaryResponse` y `GetBrokerAccountsSummaryService` | — | | **Hecha** — 15-10-2026 |
| `T-03` | La ruta en `BrokerAccountIndicatorsController` con su `@Operation` | `T-02` | | **Hecha** — 15-10-2026 |
| `T-04` | `BrokerAccountsSummaryIT`: `CA-IN-111` a `CA-IN-115`; `EndpointPermissionsIT`; contrato; `api/index.md` | `T-01`, `T-03` | `mvn verify` de las suites en verde | **Hecha** — 15-10-2026 |

## 2. Orden de ejecución

`T-01` y `T-02` en paralelo → `T-03` → `T-04`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-111` a `CA-IN-114` | `T-02`, `T-03`, `T-04` |
| `CA-IN-115` | `T-01`, `T-04` |

## 4. Bloqueos

Ninguno.
