# TASKS — `RF-SP-053` Registrar una cuenta de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-053` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| Plan | [`plan.md`](plan.md) v0.2.0 |
| `plan.md` aprobado el | 08-10-2026 |
| Estado | **En revisión** — `T-01` a `T-07` `Hecha` el 08-10-2026; `T-08` a `T-12` `Hecha` el 09-10-2026 (enmienda 0.2.0) |
| Issue | Pendiente de crear |
| Rama | `develop` |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V90`: los seis permisos de `RF-SP-053`, `RF-SP-080` y `RF-SP-081`, su reparto y sus guardas (`plan.md` §2) | — | Las guardas pasan al migrar | **Hecha** — 08-10-2026 |
| `T-02` | Recuentos del catálogo 210 → 216 (`ADMIN` 208 → 214, `CLIENTE` 37 → 40) y listas por rol | `T-01` | Las suites de siembra en verde | **Hecha** — 08-10-2026 |
| `T-03` | `BrokerAccountWriter` y `JpaBrokerAccountWriter`, con la traducción de `uq_user_brokers_cuenta` | — | | **Hecha** — 08-10-2026 |
| `T-04` | `CreateBrokerAccountRequest`, `UpdateBrokerAccountRequest`; `ManageBrokerAccountsService` con las tres operaciones y sus dos puertas | `T-03` | La validación va antes de tocar nada | **Hecha** — 08-10-2026 |
| `T-05` | `UserController`: las seis rutas (`plan.md` §4) con sus `@Operation` | `T-04` | Prosa releída en el contrato | **Hecha** — 08-10-2026 |
| `T-06` | `ManageBrokerAccountsIT`: `CA-SP-915` a `CA-SP-922` | `T-05` | Cada criterio afirmado en el cuerpo | **Hecha** — 08-10-2026 |
| `T-07` | `EndpointPermissionsIT`, `OwnScopePermissionsIT`; contrato regenerado; `api/index.md` | `T-06` | `mvn verify` en verde | **Hecha** — 08-10-2026 |
| `T-08` | `V91`: `user_brokers.kind`, relleno y restricciones (`plan.md` §12) | — | Las guardas pasan al migrar; `UserBrokerAccountSchemaIT` | **Hecha** — 09-10-2026 |
| `T-09` | `BrokerAccountKind`; el tipo al declarar en `ManageBrokerAccountsService` y en el registro por enlace | `T-08` | `EX-011` antes de escribir | **Hecha** — 09-10-2026 |
| `T-10` | `kind` en las filas de las consultas y `?kind=` en `RF-SP-057` | `T-08` | | **Hecha** — 09-10-2026 |
| `T-11` | Indicadores por `ub.kind` en lugar de los roles del titular | `T-08` | `NetworkIndicatorsIT` | **Hecha** — 09-10-2026 |
| `T-12` | Las suites que insertan cuentas; `CA-SP-938` a `CA-SP-945`; contrato y `api/index.md` | `T-09`, `T-10`, `T-11` | `mvn verify` en verde | **Hecha** — 09-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02`; `T-03` → `T-04` → `T-05` → `T-06` → `T-07`. **Carga también lo de `RF-SP-080` y `RF-SP-081`**, cuyas tasks dependen de `T-04` y `T-05` de aquí.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-915` a `CA-SP-922` | `T-03`, `T-04`, `T-05`, `T-06` |
| `CA-SP-937` | `T-01`, `T-02` |
| `CA-SP-938` a `CA-SP-945` | `T-08` a `T-12` |

## 4. Bloqueos

Ninguno.
