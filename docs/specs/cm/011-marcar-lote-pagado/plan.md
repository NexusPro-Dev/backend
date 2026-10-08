# PLAN — `RF-CM-011` Marcar un lote como pagado

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-011` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 28-09-2026 |
| Versión | 0.3.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |
| Enmendado el | 30-09-2026 — un lote sin comisiones vivas responde `409` (§12) |
| Enmendado el | 08-10-2026 — tras el pago se borran los pendientes vacíos (§13) |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Bloquear, comprobar, abonar, marcar — en una transacción.**

```
SELECT … FROM commission_batches WHERE id = :id FOR UPDATE      → 404 si no hay fila
  ABIERTO → 409 (EX-002) · PAGADO → 409 (EX-003)
CommissionPayout.pay(new PayoutOrder(user, currency, total, id, code))   — MANDATORY
UPDATE commission_batches SET status = 'PAGADO', paid_at = :ahora,
                              movement_id = :mov, updated_at = :ahora
 WHERE id = :id
```

**El `FOR UPDATE` va antes del abono**, al revés que la transición condicionada de `RF-MV-003`, porque aquí **el abono va antes que la marca**: `ck_commission_batches_pagado` exige `movement_id` en la misma fila que `PAGADO`, de modo que la marca no puede escribirse primero. El bloqueo es lo que hace que el segundo de dos pagos simultáneos **espere**, encuentre `PAGADO` y responda `409` sin llegar a `MV` (`CA-CM-192`). **La clave `lote-<id>` de `RF-MV-024` queda detrás**, como segunda defensa.

**El bloqueo también ordena contra el cierre**: `RF-CM-009` toma los lotes `ABIERTO` con `FOR UPDATE`, y un pago no puede cruzarse con un lote que está pasando a `PENDIENTE`.

---

## 2. Cambios de esquema

**Ninguno**: `V51` (`RF-CM-013`).

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `CommissionBatchRepository` | Gana `lockById` y `markPaid` | |
| `domain/service` | `PayCommissionBatchService` | Nuevo | §1, auditoría |
| `interfaces` | `CommissionBatchController` | Gana `POST /{id}/payment` | Devuelve la forma del detalle de `RF-CM-010` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/commission-batches/{id}/payment` | `commission-batches:pay` |

**Sin cuerpo.** Una acción con nombre, como `…/confirmation` en `MV`.

| Código | Cuándo |
|---|---|
| `200` | Pagado, con el lote y `paidAmount` |
| `400` | Identificador malformado |
| `401` / `403` | Sin token / sin el permiso |
| `404` | No existe |
| `409` | Abierto o ya pagado, con el estado en el mensaje |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('commission-batches:pay')")`; en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

Un `ChangeEvent` sobre `commission_batches`, `UPDATE`, `PENDIENTE` → `PAGADO`, con el total, el importe abonado y el movimiento. El abono lo audita `MV` por su cuenta (`RF-MV-024`).

---

## 7. Transaccionalidad

`@Transactional`: el bloqueo, el abono —que exige esta transacción, `MANDATORY`— y la marca. Si `MV` falla, se revierte todo (`CA-CM-193`).

---

## 8. Impacto sobre otros módulos

**`MV`**: se invoca `CommissionPayout`, ya construido. El `tasks.md` de `RF-MV-024` tenía pendiente avisar a `CM` de que la operación existe; **queda cumplido aquí**.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Transición condicionada primero y abono después | `ck_commission_batches_pagado` no admite `PAGADO` sin movimiento |
| Pagar varios lotes en una llamada | Un fallo en uno obligaría a decidir si revierte los demás; no se ha pedido |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Abonar dos veces | `FOR UPDATE` y la clave del lote en `MV` |
| Pagar un lote que se está cerrando | El mismo `FOR UPDATE` que usa el cierre |

---

## 11. Estrategia de prueba

`PayCommissionBatchIT`: `CA-CM-189` a `CA-CM-196`, con lotes producidos por devengo y cierre reales. `CA-CM-192` con dos hilos. `CA-CM-193` con un doble de `CommissionPayout` que lanza.

## 12. El lote vacío — enmienda del 30-09-2026

`RN-CM-048`. **Tras el `FOR UPDATE` y las comprobaciones de estado**, un `EXISTS` de comisiones vivas del lote; si no hay, `409` (`EX-005`) antes de invocar a `MV`. **Va después del bloqueo** por lo mismo que las otras dos comprobaciones: una devolución concurrente (`RF-CM-023`) toma el mismo lote con `FOR UPDATE`, y el pago que espera lee lo que ella dejó. `PayCommissionBatchIT` gana `CA-CM-301`.

## 13. Tras el pago se borran los pendientes vacíos — enmienda del 08-10-2026

`RN-CM-052` enmendada ([`requirements/cm.md`](../../../requirements/cm.md) v0.42.0 §5.10, «Quinta enmienda»). **Después de que `pay` confirme**, la ruta llama a `EmptyBatchesAfterPayment.run()`, que llama a `DeleteEmptyBatchesService.deleteEmpty(PENDIENTE)` —el borrado de [`RF-CM-027`](../027-borrar-lotes-vacios/plan.md), acotado a los pendientes— **en su propia transacción**.

| Alternativa | Por qué no |
|---|---|
| Borrar dentro de la transacción de `pay` | `pay` bloquea su lote antes que nada, y borrar un pendiente bloquea antes las comisiones retiradas de él: el pago tomaría lotes antes que comisiones, al revés que el resto del módulo. Y un fallo del borrado desharía un abono hecho |
| Borrar en `pay`, con `REQUIRES_NEW` | Correría **antes** de que el pago confirme, con el lote pagado todavía bloqueado |

**`EmptyBatchesAfterPayment` no es transaccional y no deja salir una excepción**: si el borrado falla, lo registra y el pago responde `200`, porque ya está hecho; los vacíos esperan al siguiente pago o a la orden de `RF-CM-027`. **Un pago que no se hace no lo llama** (`CA-CM-379`): la excepción de `pay` sale antes.

**Pruebas**: `DeleteEmptyBatchesIT` gana `CA-CM-378` y `CA-CM-379`, por la ruta. Las pruebas que llaman a `pay` directamente no borran nada, y es lo que se quiere: `PayCommissionBatchesService` también lo llama así, lote a lote.
