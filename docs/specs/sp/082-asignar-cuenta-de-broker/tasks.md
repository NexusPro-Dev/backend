# TASKS — `RF-SP-082` Asignar una cuenta de broker sin titular

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-082` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 09-10-2026 |
| Estado | **En revisión** — `T-01` a `T-04` `Hecha` el 09-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | El permiso en `V96` y los recuentos (`T-13` de `RF-SP-053`) | — | | **Hecha** — 09-10-2026 |
| `T-02` | `lockAny`, `assignUser`; `assignHolder`; la ruta con su `@Operation` | `T-01` | | **Hecha** — 09-10-2026 |
| `T-03` | El listado global con cuentas sin titular y `hasHolder` | — | | **Hecha** — 09-10-2026 |
| `T-04` | `ManageBrokerAccountsIT`: `CA-SP-969` a `CA-SP-971`; `EndpointPermissionsIT`; contrato; `api/index.md` | `T-02`, `T-03` | `mvn verify` en verde | **Hecha** — 09-10-2026 |

## 2. Orden de ejecución

`T-01` → `T-02`; `T-03` en paralelo → `T-04`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-969`, `CA-SP-970` | `T-02`, `T-04` |
| `CA-SP-971` | `T-03`, `T-04` |
| `CA-SP-972` | `T-01` |

## 4. Bloqueos

Ninguno.

## 5. Desviaciones respecto del plan

**Las pruebas viven en `ManageBrokerAccountsIT`** y no en una `AssignBrokerAccountHolderIT` propia: comparten el reparto —titular, vendedora, cuentas sin titular— y una clase más abriría otro contexto de Spring.
