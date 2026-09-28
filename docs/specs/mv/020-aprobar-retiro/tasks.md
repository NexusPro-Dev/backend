# TASKS — `RF-MV-020` Aprobar un retiro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-020` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 26-09-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 26-09-2026 salvo la última, de documentación y contrato, en curso |
| Issue | Pendiente de crear |
| Rama | `feature/pagos-y-saldos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración: método `MANUAL` (`ACTIVO` + `INTERNO`) con su `DO $`; permiso `movements:approve-withdrawal` | `RF-MV-019` `T-01` | `RF-MV-009` no devuelve `MANUAL` | **Hecha** — 26-09-2026 |
| `T-02` | `ProviderReference`, `ApproveWithdrawalRequest` | — | Unitarias | **Hecha** — 26-09-2026 |
| `T-03` | `JpaMovementRepository.confirmWithdrawalIfPending` | `T-01` | Cero filas sobre una venta o un retiro no pendiente | **Hecha** — 26-09-2026 |
| `T-04` | `ApproveWithdrawalService`: transición, pago confirmado, evento `APROBACION`, auditoría | `T-02`, `T-03`, `RF-MV-019` `T-04` | | **Hecha** — 26-09-2026 |
| `T-05` | `MovementController`: `POST /{id}/withdrawal-approval` | `T-04` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 26-09-2026 |
| `T-06` | `ApproveWithdrawalIT`: `CA-MV-236` a `CA-MV-243` | `T-05`, `RF-MV-021` `T-05` | `CA-MV-241` con dos hilos | **Hecha** — 26-09-2026 |
| `T-07` | `PermissionIT`, `EndpointPermissionsIT`, `PaymentMethodsIT`; contrato con la prosa releída; `requirements.md`, `security.md` | `T-06` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-MV-019`**, y en paralelo con `RF-MV-021`: `CA-MV-241` necesita las dos rutas. `T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06` → `T-07`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-236` a `CA-MV-238` | `T-01`, `T-04`, `T-06` |
| `CA-MV-239` a `CA-MV-242` | `T-03`, `T-04`, `T-06` |
| `CA-MV-243` | `T-05`, `T-06` |

---

## 3.1 Desviaciones respecto del plan

**El método `MANUAL` y el permiso los siembra `V49`** (`RF-MV-019` · `tasks.md` §3.1), y el servicio es `WithdrawalService.approve`. La suite es `WithdrawalIT`.

## 4. Bloqueos

**`RF-MV-019`**, que trae el `Ledger`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
