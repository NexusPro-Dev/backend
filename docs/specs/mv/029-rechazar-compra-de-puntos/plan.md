# PLAN — `RF-MV-029` Rechazar el pago de una compra de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-029` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 30-09-2026 |
| Versión | 0.3.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |
| Enmendado el | 01-10-2026 — la tarjeta por Stripe (§12) |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**La forma de negar un retiro** (`RF-MV-021`) sin sus asientos: la transición condicionada `PENDIENTE` → `RECHAZADA` sobre el tipo `COMPRA_PUNTOS`, con `rejected_at` y `rejection_reason` —`ck_movements_rejected` exige los dos—, y el pago pendiente a `RECHAZADO` con el mismo motivo (`rejectPending`, el de `RF-MV-004`). Es la misma transición que `RF-MV-028` condiciona, y por eso no se pisan.

---

## 2. Cambios de esquema

Ninguno.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `JpaMovementRepository` | `rejectPointsPurchaseIfPending(id, motivo, instante)` | Filtra el tipo en la sentencia |
| `domain/models` | `RejectionReason` (o el de `RF-MV-021`) | Se reutiliza | Longitud y contenido |
| `domain/service` | `RejectPointsPurchaseService` | Nuevo | Validación, transición, pago, auditoría |
| `application` | `RejectPointsPurchaseRequest` | Nuevo | `reason` |
| `interfaces` | `PointsPurchaseController` | `POST /movements/{id}/points-purchase-rejection` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/{id}/points-purchase-rejection` | `movements:reject-points-purchase` |

**Cuerpo**: `{ "reason": "…" }`.

| Código | Cuándo |
|---|---|
| `200` | Rechazada, con la compra y su pago |
| `400` | Identificador malformado, motivo vacío o demasiado largo |
| `401` / `403` | Sin token / sin `movements:reject-points-purchase` |
| `404` | No existe o no es una compra de puntos |
| `409` | No está pendiente, con el estado en el mensaje |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:reject-points-purchase')")`.

---

## 6. Auditoría

Un `ChangeEvent` sobre `movements`, `UPDATE`, de `PENDIENTE` a `RECHAZADA`, con el motivo.

---

## 7. Transaccionalidad

`@Transactional`: transición y pago.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Dejarla pendiente, como la venta, para volver a pagarla | Obligaría a un reintento propio de las compras de puntos para conservar algo que no hay que conservar (`spec.md` §2) |
| Sin motivo | Quien compró no sabría por qué no tiene sus puntos |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Rechazar una compra ya abonada | La transición exige `PENDIENTE`; `CA-MV-328` |

---

## 11. Estrategia de prueba

Integración, `RejectPointsPurchaseIT`: `CA-MV-326` a `CA-MV-331`. La carrera con confirmar vive en `ConfirmPointsPurchaseIT` (`CA-MV-323`).

---

## 12. La tarjeta por Stripe — enmienda del 01-10-2026

Por [`requirements/mv.md`](../../../requirements/mv.md) v0.64.0 §4.6 y `spec.md` §14.2. La ruta manual del rechazo de la compra de puntos hace la comprobación de `RN-MV-058`. **Contrato**: la prosa del `409`.

## 13. Sin ruta propia — enmienda del 01-10-2026

Por [`requirements/mv.md`](../../../requirements/mv.md) v0.67.0 §4.8 y `spec.md` §14.3. **La ruta se retira** y el método público del servicio con ella; nace un método **de paquete** que `PaymentResolutionService` invoca con el movimiento ya bloqueado (`RF-MV-045` · `plan.md` §1). La transición, la auditoría y lo demás **no cambian**. El permiso lo retira o lo traslada `V63` (`RF-MV-044` · `plan.md` §2). **Contrato**: la ruta desaparece de OpenAPI.
