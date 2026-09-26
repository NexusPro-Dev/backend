# PLAN — `RF-MV-004` Rechazar el pago pendiente de una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-004` |
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

**Es `RF-MV-005` sobre el pago en lugar de sobre la venta.** La misma transición condicionada —un `UPDATE` cuya cuenta de filas decide—, el mismo motivo validado antes de mirar nada, y lo que `RF-MV-003` · `plan.md` §1 argumenta sobre la atomicidad vale aquí sin repetirse. La condición cruza las dos tablas en una sola sentencia:

```sql
UPDATE payments p
   SET status = 'RECHAZADO', rejected_at = :at, rejection_reason = :reason
  FROM movements m, movement_types t
 WHERE p.movement_id = :id AND p.status = 'PENDIENTE'
   AND m.id = p.movement_id AND m.status = 'PENDIENTE'
   AND t.id = m.movement_type_id AND t.code = 'VENTA'
```

**Si toca cero filas**, una segunda lectura decide qué decir: no existe (`EX-001`), no está pendiente (`EX-002`) o no tiene pago pendiente (`EX-003`). La lectura va **después** de la escritura y no antes, porque solo explica el fallo: no decide nada.

**Depende de `RF-MV-018`**, que crea `payments` y mueve a ella el método (`RF-MV-018` · `tasks.md` `T-01` a `T-03` y `T-07`).

---

## 2. Cambios de esquema

**Ninguno propio.** `payments` —con `rejected_at`, `rejection_reason` y `ck_payments_rejected`— la crea la migración de `RF-MV-018`. **El permiso `movements:reject-payment`** entra con la siguiente migración libre al construir, a `SUPERADMIN` y a `ADMIN` explícito, como `movements:confirm`: conciliar es tarea de administración.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `RejectPaymentRequest` | Nuevo | `reason` |
| `domain/models` | `RejectionReason` | Nuevo | La forma de `VoidReason`: con contenido, recortado y acotado a quinientos |
| `domain/repository` | `PaymentRepository` | Gana `rejectPendingOfSale` | La sentencia de §1 |
| `domain/service` | `RejectPaymentService` | Nuevo | Motivo, transición, explicación del fallo, auditoría, respuesta |
| `interfaces` | `MovementController` | `POST /{id}/rejection` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/{id}/rejection` | `movements:reject-payment` |

**La ruta que `RF-MV-005` · `plan.md` §4 reservó el 17-09-2026** —«`RF-MV-004` será `…/rejection`»—, junto a `…/confirmation` y `…/voiding`. **Sobre la venta y no sobre el pago** (`spec.md` §6.1): quien concilia conoce el comprobante de la venta.

**Cuerpo**: `{ "reason": "…" }`, por `POST` y no en la URL, por lo mismo que en anular.

**Respuesta**: `200` con `SaleResponse`, con `payments`.

| Código | Cuándo |
|---|---|
| `200` | Rechazado |
| `400` | Identificador malformado, motivo vacío (`VAL-002`) o demasiado largo (`VAL-003`) |
| `401` / `403` | Sin token / sin `movements:reject-payment` |
| `404` | No existe, o no es una venta |
| `409` | No está pendiente, con el estado en el mensaje (`EX-002`), o no tiene pago pendiente (`EX-003`) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:reject-payment')")`. **`CA-MV-203` se ejercita con `movements:confirm` puesto.**

---

## 6. Auditoría

Un `ChangeEvent` de `MV` sobre `payments`, `UPDATE`: `before {status: PENDIENTE}`, `after {status: RECHAZADO, rejected_at, rejection_reason}`.

---

## 7. Transaccionalidad

`@Transactional`; una sentencia y el asiento. Frente a una confirmación simultánea, el bloqueo de fila del pago decide quién gana (`FA-001`).

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Rechazar la venta, como estaba declarado | Obligaba a registrar otra venta para reintentar (`spec.md` §2) |
| `POST /payments/{paymentId}/rejection` | Obligaría a quien concilia a conocer un identificador que no ve; la venta tiene a lo sumo un pago pendiente y no hay ambigüedad |
| Compartir `movements:confirm` | `RN-SEG-014` |
| Motivo opcional | El comprador no sabría por qué tiene que volver a pagar |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Rechazar el pago de una venta ya confirmada | La condición `m.status = 'PENDIENTE'` en la misma sentencia; `CA-MV-200` |
| Que los permisos se confundan | `CA-MV-203` con `movements:confirm` |

---

## 11. Estrategia de prueba

Integración, `RejectPaymentIT`: `CA-MV-197` a `CA-MV-205`, con el reintento de `CA-MV-198` por la ruta de `RF-MV-018`.
