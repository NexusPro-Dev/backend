# TASKS — `RF-SP-053` Registrar una cuenta de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-053` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 08-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V90`: los seis permisos de `RF-SP-053`, `RF-SP-080` y `RF-SP-081`, su reparto y sus guardas (`plan.md` §2) | — | Las guardas pasan al migrar | Pendiente |
| `T-02` | Recuentos del catálogo 210 → 216 (`ADMIN` 208 → 214, `CLIENTE` 37 → 40) y listas por rol | `T-01` | Las suites de siembra en verde | Pendiente |
| `T-03` | `BrokerAccountWriter` y `JpaBrokerAccountWriter`, con la traducción de `uq_user_brokers_cuenta` | — | | Pendiente |
| `T-04` | `CreateBrokerAccountRequest`, `UpdateBrokerAccountRequest`; `ManageBrokerAccountsService` con las tres operaciones y sus dos puertas | `T-03` | La validación va antes de tocar nada | Pendiente |
| `T-05` | `UserController`: las seis rutas (`plan.md` §4) con sus `@Operation` | `T-04` | Prosa releída en el contrato | Pendiente |
| `T-06` | `ManageBrokerAccountsIT`: `CA-SP-915` a `CA-SP-922` | `T-05` | Cada criterio afirmado en el cuerpo | Pendiente |
| `T-07` | `EndpointPermissionsIT`, `OwnScopePermissionsIT`; contrato regenerado; `api/index.md` | `T-06` | `mvn verify` en verde | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02`; `T-03` → `T-04` → `T-05` → `T-06` → `T-07`. **Carga también lo de `RF-SP-080` y `RF-SP-081`**, cuyas tasks dependen de `T-04` y `T-05` de aquí.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-915` a `CA-SP-922` | `T-03`, `T-04`, `T-05`, `T-06` |
| `CA-SP-937` | `T-01`, `T-02` |

## 4. Bloqueos

Ninguno.
