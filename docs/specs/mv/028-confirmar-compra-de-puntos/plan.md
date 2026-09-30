# PLAN — `RF-MV-028` Confirmar el pago de una compra de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-028` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 30-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**La forma de aprobar un retiro** (`RF-MV-020`): una transición condicionada al estado anterior —`UPDATE … WHERE status = 'PENDIENTE' AND tipo = 'COMPRA_PUNTOS'`— que devuelve cero filas si alguien ganó antes, y solo quien la gana confirma el pago pendiente y llama al `Ledger` con `PUNTOS_EMITIDOS` −N y `PUNTOS` +N, evento `ABONO`, con el `payment_id`. **N es `movements.points_amount`**, leído de la fila y no recalculado.

**La carrera con `RF-MV-029`** la resuelve la misma transición: las dos operaciones condicionan el mismo `UPDATE`, y solo una lo gana (`CA-MV-323`).

---

## 2. Cambios de esquema

Ninguno. Lo trae `RF-MV-025` · `T-01`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `JpaMovementRepository` | `confirmPointsPurchaseIfPending(id, instante)` | Filtra el tipo en la sentencia, como `confirmIfPending` filtra `VENTA` |
| `domain/repository` | `JpaPaymentRepository` | `confirmPending(movimiento, referencia, instante)` | El de `RF-MV-018`, con la referencia |
| `domain/service` | `ConfirmPointsPurchaseService` | Nuevo | Transición, pago, `Ledger`, auditoría |
| `application` | `ConfirmPointsPurchaseRequest` | Nuevo | `providerReference`, nulable |
| `application` | `PointsPurchaseResponse` | Se reutiliza | El de `RF-MV-027` |
| `interfaces` | `PointsPurchaseController` | `POST /movements/{id}/points-purchase-confirmation` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/{id}/points-purchase-confirmation` | `movements:confirm-points-purchase` |

**Una acción con nombre**, como `…/withdrawal-approval`. **Cuerpo**: `{ "providerReference": "…" }` o `{}`.

| Código | Cuándo |
|---|---|
| `200` | Confirmada, con la compra y su pago |
| `400` | Identificador malformado o referencia demasiado larga |
| `401` / `403` | Sin token / sin `movements:confirm-points-purchase` |
| `404` | No existe o no es una compra de puntos |
| `409` | No está pendiente, con el estado en el mensaje |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:confirm-points-purchase')")`.

---

## 6. Auditoría

Un `ChangeEvent` sobre `movements`, `UPDATE`, de `PENDIENTE` a `CONFIRMADA`, con los puntos abonados y la referencia.

---

## 7. Transaccionalidad

`@Transactional`: transición, pago y dos asientos. El abono **no puede fallar por saldo**: `PUNTOS_EMITIDOS` es de la empresa y la cuenta de la persona solo sube.

---

## 8. Impacto sobre otros módulos

**`CM`**: ninguno. No se publica aviso de líneas comisionables (`RN-MV-049`). **El frontend**: la pantalla de administración de compras pendientes, que las encuentra por `RF-MV-006` con `type=COMPRA_PUNTOS&status=PENDIENTE`.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Reutilizar `…/confirmation` de la venta | Dos operaciones con dos efectos y dos permisos (`RN-SEG-014`); esa sentencia filtra `VENTA` |
| Recalcular los puntos con la tasa vigente | `RN-MV-051`: se pagó a la tasa de la compra |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Doble abono por dos confirmaciones simultáneas | La transición condicionada; `CA-MV-322` y `CA-MV-323` con dos hilos |

---

## 11. Estrategia de prueba

Integración, `ConfirmPointsPurchaseIT`: `CA-MV-318` a `CA-MV-325`; `CA-MV-323` con dos hilos, confirmar contra rechazar; `CA-MV-319` lee los saldos y los asientos.
