# TASKS — `RF-MV-021` Negar un retiro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-021` |
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
| `T-01` | Migración: permiso `movements:reject-withdrawal` a `SUPERADMIN` y `ADMIN` explícito | `RF-MV-019` `T-01` | El catálogo cuenta uno más | Pendiente |
| `T-02` | `RejectWithdrawalRequest` | `RF-MV-004` `T-02` | | Pendiente |
| `T-03` | `JpaMovementRepository.rejectWithdrawalIfPending` | `RF-MV-019` `T-01` | Cero filas sobre una venta o un retiro no pendiente | Pendiente |
| `T-04` | `RejectWithdrawalService`: motivo, transición, evento `RECHAZO`, auditoría | `T-02`, `T-03`, `RF-MV-019` `T-04` | El motivo se valida antes de leer el retiro | Pendiente |
| `T-05` | `MovementController`: `POST /{id}/withdrawal-rejection` | `T-04` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-06` | `RejectWithdrawalIT`: `CA-MV-244` a `CA-MV-250` | `T-05` | `CA-MV-250` con `movements:approve-withdrawal` puesto | Pendiente |
| `T-07` | `PermissionIT`, `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md`, `security.md` | `T-06` | | Pendiente |

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

## 4. Bloqueos

**`RF-MV-019`**, que trae el `Ledger` y las columnas del rechazo.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los siete criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
