# TASKS — `RF-MV-022` Consultar mis saldos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-022` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 26-09-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 26-09-2026; la última, de documentación y contrato (`T-06`), el 30-09-2026 |
| Issue | [#122](https://github.com/NexusPro-Dev/backend/issues/122) |
| Rama | `feature/pagos-y-saldos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración: `movements:read-own-balances` y `movements:list-own-entries`, por tipo de rol | `RF-MV-019` `T-01` | El catálogo cuenta dos más | **Hecha** — 26-09-2026 |
| `T-02` | `LedgerRepository.saldosDe` e `historialDe` | `RF-MV-019` `T-03` | Una sola sentencia por página | **Hecha** — 26-09-2026 |
| `T-03` | `MyBalancesService`, `MyEntriesService`, `EntryResponse`, `MyEntriesRequest` | `T-02` | Los `400` salen juntos | **Hecha** — 26-09-2026 |
| `T-04` | `MovementController`: `GET /mine/balances` y `GET /mine/balances/entries` | `T-03` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 26-09-2026 |
| `T-05` | `MyBalancesIT`: `CA-MV-251` a `CA-MV-259` | `T-04`, `RF-MV-019` `T-07`, `RF-MV-020` `T-05`, `RF-MV-021` `T-05` | Estadísticas de Hibernate | **Hecha** — 26-09-2026 |
| `T-06` | `PermissionIT`, `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md`, `security.md` | `T-05` | | Hecha |

---

## 2. Orden de ejecución

**Después de `RF-MV-019` a `RF-MV-021`**: `T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-251` a `CA-MV-254` | `T-02`, `T-03`, `T-05` |
| `CA-MV-255` a `CA-MV-258` | `T-02`, `T-03`, `T-05` |
| `CA-MV-259` | `T-04`, `T-05` |

---

## 3.1 Desviaciones respecto del plan

**Los dos permisos los siembra `V49`.** Un solo servicio (`BalanceService`) para las dos lecturas. La suite es `BalancesAndBonusIT`, compartida con `RF-MV-023`.

## 4. Bloqueos

**`RF-MV-019`**, que crea las cuentas y los asientos.

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde.
- [x] Los nueve criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

**Cierre documental el 30-09-2026**: construido el 26-09-2026 y mezclado por el PR [#124](https://github.com/NexusPro-Dev/backend/pull/124) sin marcar la definición de terminado. Las casillas se marcan con la suite completa en verde el 30-09-2026, cada criterio con su afirmación, `EndpointPermissionsIT` exigiendo el permiso de la ruta y la prosa del contrato releída. `CA-MV-253` gana la vuelta de los saldos tras negar, `CA-MV-256` la aprobación —dos filas de `SOLICITUD` y una de `APROBACION`— y `CA-MV-257` el filtro por moneda combinado, en `BalancesAndBonusIT`; y el contrato de las dos consultas declara sus `400`, `401` y `403`, que no publicaba.
