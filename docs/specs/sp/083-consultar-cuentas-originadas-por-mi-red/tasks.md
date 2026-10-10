# TASKS — `RF-SP-083` Consultar las cuentas de broker que originó mi red

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-083` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 10-10-2026 |
| Estado | **En revisión** — `T-01` a `T-04` `Hecha` el 10-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V100`: el permiso con su guarda y el índice; los recuentos del catálogo | — | Flyway aplica | **Hecha** — 10-10-2026 |
| `T-02` | `BrokerAccountFilters` con `referrerNetworkOf` y `referrerUserId`; la recursiva de origen y su predicado | — | | **Hecha** — 10-10-2026 |
| `T-03` | `ListReferredBrokerAccountsRequest`; `ListBrokerAccountsService.listReferred`; la ruta en `UserController` con su `@Operation` | `T-01`, `T-02` | | **Hecha** — 10-10-2026 |
| `T-04` | `ReferredBrokerAccountsIT`: `CA-SP-1003` a `CA-SP-1006`; `EndpointPermissionsIT`; contrato; `api/index.md` | `T-03` | `mvn verify` en verde | **Hecha** — 10-10-2026 |

## 2. Orden de ejecución

`T-01` y `T-02` en paralelo → `T-03` → `T-04`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-1003`, `CA-SP-1004` | `T-02`, `T-04` |
| `CA-SP-1005` | `T-02`, `T-03`, `T-04` |
| `CA-SP-1006` | `T-01`, `T-04` |

## 4. Bloqueos

Ninguno.
