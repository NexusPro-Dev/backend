# TASKS — `RF-MV-004` Rechazar el pago pendiente de una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-004` |
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
| `T-01` | Migración que siembra `movements:reject-payment` a `SUPERADMIN` y `ADMIN` explícito | `RF-MV-018` `T-01` | El catálogo cuenta uno más | Pendiente |
| `T-02` | `RejectionReason`, `RejectPaymentRequest` | — | Motivo vacío → `VAL-002`; largo → `VAL-003` | Pendiente |
| `T-03` | `PaymentRepository.rejectPendingOfSale` | `RF-MV-018` `T-03` | Cero filas sobre una venta confirmada, anulada o sin pago pendiente | Pendiente |
| `T-04` | `RejectPaymentService`: motivo primero, transición, explicación del fallo, auditoría, respuesta | `T-02`, `T-03`, `RF-MV-018` `T-07` | El motivo se valida antes de leer la venta | Pendiente |
| `T-05` | `MovementController`: `POST /{id}/rejection` con `@PreAuthorize('movements:reject-payment')` | `T-04` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-06` | `RejectPaymentIT`: `CA-MV-197` a `CA-MV-205` | `T-05`, `RF-MV-018` `T-11` | `CA-MV-203` con `movements:confirm` puesto | Pendiente |
| `T-07` | `PermissionIT` y `EndpointPermissionsIT`; contrato regenerado con la prosa releída; `requirements.md` y `security.md` | `T-06` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-MV-018` `T-01` a `T-03` y `T-07`**, y antes de su `T-15`, para regenerar el contrato una sola vez: `T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06` → `T-07`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-197` a `CA-MV-201` | `T-03`, `T-04`, `T-06` |
| `CA-MV-202` | `T-02`, `T-04`, `T-06` |
| `CA-MV-203` | `T-05`, `T-06` |
| `CA-MV-204`, `CA-MV-205` | `T-04`, `T-06` |

---

## 4. Bloqueos

**`RF-MV-018`**, que crea `payments`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los nueve criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
