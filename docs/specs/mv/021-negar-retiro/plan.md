# PLAN — `RF-MV-021` Negar un retiro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-021` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 26-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Es `RF-MV-005` para un retiro, con devolución.** La transición condicionada —`UPDATE movements SET status = 'RECHAZADA', rejected_at = :at, rejection_reason = :reason WHERE id = :id AND status = 'PENDIENTE' AND movement_type_id = <RETIRO>`— y, si acierta, el evento `RECHAZO` por el `Ledger` de `RF-MV-019`: `RETENIDO` −X, `BILLETERA` +X. El motivo se valida primero, con la forma de `VoidReason`.

---

## 2. Cambios de esquema

**Ninguno propio**: `rejected_at`, `rejection_reason` y `ck_movements_rejected` los trae la migración de `RF-MV-019`. **El permiso `movements:reject-withdrawal`** entra con la siguiente migración libre, a `SUPERADMIN` y `ADMIN` explícito.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `RejectWithdrawalRequest` | Nuevo | `reason` |
| `domain/models` | `RejectionReason` | **Reutilizado** de `RF-MV-004` | |
| `domain/repository` | `JpaMovementRepository` | Gana `rejectWithdrawalIfPending` | |
| `domain/service` | `RejectWithdrawalService` | Nuevo | Motivo, transición, `Ledger`, auditoría |
| `application` | `WithdrawalResponse` | Gana `rejectedAt` y `rejectionReason` | |
| `interfaces` | `MovementController` | `POST /{id}/withdrawal-rejection` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/{id}/withdrawal-rejection` | `movements:reject-withdrawal` |

**Cuerpo**: `{ "reason": "…" }`.

| Código | Cuándo |
|---|---|
| `200` | Negado, con el retiro |
| `400` | Identificador malformado, motivo vacío o demasiado largo |
| `401` / `403` | Sin token / sin `movements:reject-withdrawal` |
| `404` | No existe o no es un retiro |
| `409` | No está pendiente, con el estado en el mensaje |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:reject-withdrawal')")`. **`CA-MV-250` se ejercita con `movements:approve-withdrawal` puesto.**

---

## 6. Auditoría

Un `ChangeEvent` sobre `movements`, `UPDATE`, `PENDIENTE` → `RECHAZADA`, con el motivo, que cita el evento `RECHAZO`.

---

## 7. Transaccionalidad

`@Transactional`: transición y dos asientos. Frente a una aprobación simultánea, la transición decide (`RF-MV-020` · `plan.md` §7).

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Reutilizar `…/rejection` de `RF-MV-004` | Es del pago de una venta, con otro permiso (`RN-SEG-014`) |
| Motivo solo en la auditoría | La persona tendría que preguntar por qué no le pagaron |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Devolver dos veces | La transición primero; `CA-MV-247` |

---

## 11. Estrategia de prueba

Integración, `RejectWithdrawalIT`: `CA-MV-244` a `CA-MV-250`; `CA-MV-246` pide un segundo retiro por la ruta de `RF-MV-019`.
