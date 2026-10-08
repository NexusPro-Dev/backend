# TASKS — `RF-SP-079` Consultar mis cuentas de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-079` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 08-10-2026 |
| Estado | **En revisión** — `T-01` a `T-06` `Hecha` el 08-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V89`: el permiso, su reparto por tipo de rol y sus guardas (`plan.md` §2) | — | Las guardas pasan al migrar | **Hecha** — 08-10-2026 |
| `T-02` | Recuentos del catálogo 209 → 210 (`ADMIN` 207 → 208) y listas por rol en las suites que los cuentan | `T-01` | `grep -rnE "\b209\b" src/test` sin restos de catálogo | **Hecha** — 08-10-2026 |
| `T-03` | `GetBrokerAccountsService.mine()` | — | | **Hecha** — 08-10-2026 |
| `T-04` | `UserController`: `GET /me/broker-accounts` con su `@Operation` | `T-03` | Prosa releída en el contrato | **Hecha** — 08-10-2026 |
| `T-05` | `BrokerAccountsIT`, junto a `RF-SP-055` y `RF-SP-056`: `CA-SP-909` a `CA-SP-912` y `CA-SP-914` | `T-04` | Cada criterio afirmado en el cuerpo | **Hecha** — 08-10-2026 |
| `T-06` | `EndpointPermissionsIT`, `OwnScopePermissionsIT`; contrato regenerado; `api/index.md` | `T-05` | `mvn verify` en verde | **Hecha** — 08-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02`; `T-03` → `T-04` → `T-05` → `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-909`, `CA-SP-910`, `CA-SP-911`, `CA-SP-914` | `T-03`, `T-05` |
| `CA-SP-912` | `T-04`, `T-05` |
| `CA-SP-913` | `T-01`, `T-02` |

## 4. Bloqueos

Ninguno.

## 5. Desviaciones respecto del plan

**Sin suite propia**: los criterios viven en `BrokerAccountsIT`, que ya monta la cadena de mando, el cliente y sus cuentas; una clase aparte la habría repetido. `CA-SP-914` amplía la prueba del titular de `RF-SP-055`, que ahora afirma el `404` también portando `broker-accounts:read-own`.
