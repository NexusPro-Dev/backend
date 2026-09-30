# TASKS — `RF-MV-021` Negar un retiro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-021` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 26-09-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 26-09-2026; la última, de documentación y contrato (`T-07`), el 30-09-2026 |
| Issue | [#122](https://github.com/NexusPro-Dev/backend/issues/122) |
| Rama | `feature/pagos-y-saldos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración: permiso `movements:reject-withdrawal` a `SUPERADMIN` y `ADMIN` explícito | `RF-MV-019` `T-01` | El catálogo cuenta uno más | **Hecha** — 26-09-2026 |
| `T-02` | `RejectWithdrawalRequest` | `RF-MV-004` `T-02` | | **Hecha** — 26-09-2026 |
| `T-03` | `JpaMovementRepository.rejectWithdrawalIfPending` | `RF-MV-019` `T-01` | Cero filas sobre una venta o un retiro no pendiente | **Hecha** — 26-09-2026 |
| `T-04` | `RejectWithdrawalService`: motivo, transición, evento `RECHAZO`, auditoría | `T-02`, `T-03`, `RF-MV-019` `T-04` | El motivo se valida antes de leer el retiro | **Hecha** — 26-09-2026 |
| `T-05` | `MovementController`: `POST /{id}/withdrawal-rejection` | `T-04` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 26-09-2026 |
| `T-06` | `RejectWithdrawalIT`: `CA-MV-244` a `CA-MV-250` | `T-05` | `CA-MV-250` con `movements:approve-withdrawal` puesto | **Hecha** — 26-09-2026 |
| `T-07` | `PermissionIT`, `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md`, `security.md` | `T-06` | | Hecha |

---

## 2. Orden de ejecución

**Después de `RF-MV-019`**, en paralelo con `RF-MV-020`: `T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06` → `T-07`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-244` a `CA-MV-247` | `T-03`, `T-04`, `T-06` |
| `CA-MV-248` | `T-04`, `T-06` |
| `CA-MV-249`, `CA-MV-250` | `T-03`, `T-05`, `T-06` |

---

## 3.1 Desviaciones respecto del plan

**El permiso lo siembra `V49`**, el motivo reutiliza `RejectionReason` de `RF-MV-004` y el servicio es `WithdrawalService.reject`. La suite es `WithdrawalIT`.

## 4. Bloqueos

**`RF-MV-019`**, que trae el `Ledger` y las columnas del rechazo.

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde.
- [x] Los siete criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

**Cierre documental el 30-09-2026**: construido el 26-09-2026 y mezclado por el PR [#124](https://github.com/NexusPro-Dev/backend/pull/124) sin marcar la definición de terminado. Las casillas se marcan con la suite completa en verde el 30-09-2026, cada criterio con su afirmación, `EndpointPermissionsIT` exigiendo el permiso de la ruta y la prosa del contrato releída. `CA-MV-248` gana el motivo ausente y `CA-MV-249` el identificador de una venta, en `WithdrawalIT`.
