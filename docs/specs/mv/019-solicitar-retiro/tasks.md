# TASKS — `RF-MV-019` Solicitar un retiro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-019` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 26-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/pagos-y-saldos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V49__mv_saldos_y_retiros.sql`** (o la siguiente libre): `accounts`, `movement_entries`, `f_movement_entries_cuadre` y su disparador, los tres tipos con `REGISTRADO`, las columnas nuevas de `movements`, el permiso | `RF-MV-018` `T-01` | Un asiento cojo aborta el `COMMIT`; una billetera negativa la rechaza el esquema | Pendiente |
| `T-02` | `AccountKind`, `EntryEvent`, `Money`, `AccountNumber` | — | Unitarias | Pendiente |
| `T-03` | `LedgerRepository` y `JpaLedgerRepository` | `T-01`, `T-02` | `cuentaDe` es idempotente con dos hilos | Pendiente |
| `T-04` | `Ledger`: orden de bloqueo, `balance_after`, traducción de `ck_accounts_saldo` | `T-03` | `LedgerIT` | Pendiente |
| `T-05` | `Movement.retiro(...)` | — | Sin líneas, `REGISTRADO` | Pendiente |
| `T-06` | `RequestWithdrawalService`, `WithdrawalRequest`, `WithdrawalResponse`, `BalancesResponse` | `T-04`, `T-05` | La validación va antes de tocar un saldo | Pendiente |
| `T-07` | `MovementController`: `POST /mine/withdrawals` con `@PreAuthorize('movements:request-withdrawal')` | `T-06` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-08` | Confirmar, anular, rechazar el pago y volver a pagar fijan `VENTA` | `RF-MV-018` `T-08`, `T-10`; `RF-MV-004` `T-03` | `CA-MV-233` | Pendiente |
| `T-09` | `RequestWithdrawalIT`: `CA-MV-224` a `CA-MV-235`; `LedgerIT` | `T-07`, `T-08` | `CA-MV-228` con dos hilos; `CA-MV-234` contra la base | Pendiente |
| `T-10` | `PermissionIT`, `EndpointPermissionsIT`; contrato regenerado con la prosa releída; `requirements.md`, `security.md`, `modelo-datos.md` (las tres tablas pasan a escritas) | `T-09` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-MV-018` y `RF-MV-004`**: `T-01` → (`T-02`, `T-05`) → `T-03` → `T-04` → `T-06` → `T-07` → `T-08` → `T-09` → `T-10`.

**`RF-MV-020` y `RF-MV-021` reutilizan el `Ledger`** (`T-04`) y se construyen detrás.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-224` a `CA-MV-227` | `T-04`, `T-06`, `T-09` |
| `CA-MV-228` | `T-04`, `T-09` |
| `CA-MV-229` a `CA-MV-232` | `T-06`, `T-07`, `T-09` |
| `CA-MV-233` | `T-08`, `T-09` |
| `CA-MV-234` | `T-01`, `T-09` |
| `CA-MV-235` | `T-06`, `T-09` |

---

## 4. Bloqueos

**`RF-MV-018`**, que crea `payments`: `movement_entries` la referencia.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los doce criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md`, `security.md` y `modelo-datos.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
