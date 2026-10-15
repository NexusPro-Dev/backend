# TASKS — `RF-SP-084` Editar el enlace y el modelo de un broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-084` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 15-10-2026 |
| Estado | **En revisión** — `T-01` a `T-03` `Hecha` el 15-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V112`: `brokers:update` a `SUPERADMIN` y `ADMIN`; los recuentos del catálogo | — | `CA-SP-1023` | **Hecha** — 15-10-2026 |
| `T-02` | `UpdateBrokerRequest`, `UpdateBrokerService`, lectura con bloqueo y escritura; la ruta con su `@Operation` | `T-01` | | **Hecha** — 15-10-2026 |
| `T-03` | `BrokersIT`: `CA-SP-1020` a `CA-SP-1022`; `EndpointPermissionsIT`; contrato; `api/index.md` | `T-02` | `mvn verify` en verde | **Hecha** — 15-10-2026 |

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-1020` a `CA-SP-1022` | `T-02`, `T-03` |
| `CA-SP-1023` | `T-01` |

## 4. Bloqueos

Ninguno.
