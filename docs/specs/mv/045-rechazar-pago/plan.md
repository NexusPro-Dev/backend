# PLAN — `RF-MV-045` Rechazar un pago pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-045` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 01-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**El de `RF-MV-044` · `plan.md` §1, con el rechazo en lugar de la confirmación.** `PaymentResolutionService.reject(pago, Rejection)` valida el motivo (`RejectionReason`) **antes** de bloquear nada, bloquea el movimiento (`lockMovementOf`), lee el pago (`findTarget`), comprueba tipo, estado y cobro abierto, y delega: `RejectPaymentService.rejectPayment(movimiento, motivo)` para la venta y `PointsPurchaseService.rejectPayment(compra, motivo)` para la compra de puntos, dos métodos de paquete que entran en el rechazo que ya tienen. Responde `GetMovementService.get(movimiento)`.

---

## 2. Cambios de esquema

**Los de `RF-MV-044` · `plan.md` §2**, en la misma `V63`. Lo propio de este requerimiento: `movements:reject-payment` **se conserva** —mismo identificador, `01a0f6c0-8800-7002-9c4f-5e7ad700000c`, y mismos portadores— y cambia su nombre a «Rechazar un pago pendiente» y su descripción a la de `RF-MV-045`; quien portaba `movements:reject-points-purchase` lo recibe antes de que aquel se borre.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/service` | `PaymentResolutionService` | Gana `reject(pago, Rejection)` | |
| `domain/service` | `RejectPaymentService` | `reject(movimiento, motivo)` **se retira**; nace `rejectPayment(movimiento, motivo)`, de paquete. `rejectByGateway` no cambia | |
| `domain/service` | `PointsPurchaseService` | `reject(compra, Rejection)` **se retira**; nace `rejectPayment(compra, motivo)`, de paquete | |
| `application` | `PaymentRequests.Rejection(reason)` | Nuevo | Sustituye al cuerpo de `RF-MV-004` y a `PointsRequests.Rejection` |
| `interfaces` | `PaymentController` | `POST /movements/payments/{paymentId}/rejection`; **se retira** `POST /movements/{id}/rejection` | |
| `interfaces` | `PointsController` | **Se retira** `POST /movements/{id}/points-purchase-rejection` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/payments/{paymentId}/rejection` | `movements:reject-payment` |

**Cuerpo**: `{ "reason": "…" }`, obligatorio.

| Código | Cuándo |
|---|---|
| `200` | El movimiento, con la forma de `GET /movements/{id}` (`SaleResponse`) |
| `400` | Identificador malformado (`VAL-001`) o motivo vacío o demasiado largo (`VAL-002`) |
| `401` / `403` | Sin token / sin `movements:reject-payment` |
| `404` | El pago no existe (`EX-001`) |
| `409` | Es de un retiro (`EX-002`), no está pendiente (`EX-003`) o tiene cobro abierto (`EX-004`) |

**Cambio rompedor**: las dos rutas por movimiento responden `404`.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:reject-payment')")`, sin alcance. `EndpointPermissionsIT` cambia la ruta del permiso y pierde la de la compra de puntos.

---

## 6. Auditoría

**La de cada tipo, sin cambios.**

---

## 7. Transaccionalidad

La de `RF-MV-044` · `plan.md` §7.

---

## 8. Impacto sobre otros módulos

**El frontend**, como `RF-MV-044`. **La pasarela**: `GatewayEventProcessor.cancelado` sigue llamando a `rejectByGateway`.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un permiso nuevo y retirar `movements:reject-payment` | El nombre ya es exactamente el de la operación; cambiarle el identificador obligaría a repartirlo otra vez sin ganar nada |
| Unificar el efecto —que la compra de puntos también quede pendiente— | Es una decisión de negocio que `requirements/mv.md` §4.4 tomó y nadie ha pedido cambiar |

---

## 10. Riesgos

Los de `RF-MV-044` · `plan.md` §10.

---

## 11. Estrategia de prueba

Integración, **`RejectPaymentIT`** —la suite de `RF-MV-004`, que pasa a la ruta nueva y gana la compra de puntos—: `CA-MV-507` a `CA-MV-515`. `CA-MV-513` con `FakeCardGateway` encendido.
