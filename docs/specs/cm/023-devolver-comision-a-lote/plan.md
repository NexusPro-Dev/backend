# PLAN — `RF-CM-023` Devolver a su lote pendiente una comisión retirada

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-023` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 30-09-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |
| Enmendado el | 07-10-2026 — sin la comisión revertida (§12) |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

**Es el espejo de [`RF-CM-022`](../022-retirar-comision-de-lote/plan.md)**, y hereda de él el orden de los bloqueos, el esquema de `V59`, la auditoría y la forma de la ruta. Aquí solo va lo que cambia.

---

## 1. Enfoque

```
SELECT … FROM commissions WHERE id = :comision FOR UPDATE              → 404 si no hay fila
  withdrawn_from_batch_id <> :lote (o nulo) → 404 (EX-002) · reverted_at no nulo → 409 (EX-005)
SELECT … FROM commission_batches WHERE id IN (:lote, :actual) ORDER BY id FOR UPDATE
  el origen PAGADO → 409 (EX-003) · el actual no ABIERTO → 409 (EX-004)
UPDATE commissions SET batch_id = :lote, withdrawn_from_batch_id = NULL WHERE id = :comision
UPDATE commission_batches SET total_amount = total_amount - :importe … WHERE id = :actual
UPDATE commission_batches SET total_amount = total_amount + :importe … WHERE id = :lote
```

**Los dos lotes se bloquean en una sola sentencia ordenada por identificador**: aquí se conocen los dos de antemano —el origen por la ruta y el actual por la comisión—, y no hace falta `lockOpenBatch` porque no se abre nada.

**`FA-002`, contra el cierre**: `RF-CM-009` toma los lotes `ABIERTO` con `FOR UPDATE`. Si el cierre tiene el actual, la devolución espera, lo lee `PENDIENTE` y responde `409`; si lo tiene la devolución, el cierre espera y encuentra la comisión fuera.

---

## 2. Cambios de esquema

**Ninguno**: `V59` (`RF-CM-022` `T-01`).

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `CommissionBatchRepository` y su adaptador | Gana `lockBatches(Collection<UUID>)`, ordenado | `lockCommission`, `moveCommission` y `adjustTotal` son de `RF-CM-022` |
| `domain/service` | `ReturnCommissionService` | Nuevo | §1, auditoría |
| `interfaces` | `CommissionBatchController` | Gana `POST /{id}/commissions/{commissionId}/return` | Devuelve el detalle de `RF-CM-010` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/commission-batches/{id}/commissions/{commissionId}/return` | `commission-batches:return-commission` |

**`{id}` es el pendiente de origen**, no el lote en que la comisión está ahora: es el que Finanzas tiene delante, y el que lista lo que se le retiró (`RF-CM-010`). **Sin cuerpo.** Códigos: los de `RF-CM-022`, con `409` para origen pagado, abierto ya cerrado o comisión revertida.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('commission-batches:return-commission')")`; en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

La de `RF-CM-022`: un `ChangeEvent` `UPDATE` sobre `commissions` con `batch_id` y `withdrawn_from_batch_id` antes y después.

---

## 7. Transaccionalidad

`@Transactional`.

---

## 8. Impacto sobre otros módulos

**Ninguno.**

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Entrar por el lote en que está la comisión (`/{abierto}/…/return`) | El front revisa el pendiente, y es ahí donde ve lo retirado; obligarle a buscar el abierto es un paso más sin información nueva |
| Conservar `withdrawn_from_batch_id` al devolver, como historial | Una comisión devuelta volvería a aparecer entre las retiradas de su propio lote; el historial ya lo guarda la auditoría |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Devolver a un lote que se está pagando | El `FOR UPDATE` del origen, el mismo del pago |
| Devolver mientras el cierre cierra el abierto | El `FOR UPDATE` del actual, el mismo del cierre (`CA-CM-288`) |

---

## 11. Estrategia de prueba

`ReturnCommissionIT`: `CA-CM-282` a `CA-CM-289`, sobre retiros hechos por la ruta de `RF-CM-022`. `CA-CM-288` con dos hilos. `CA-CM-284` paga el pendiente por la ruta de `RF-CM-011` tras devolver.

## 12. Sin la comisión revertida — enmienda del 07-10-2026

`RN-CM-047` enmendada. Sale de §1 la comprobación `reverted_at no nulo → 409 (EX-005)`, y del contrato su motivo del `409`. Una retirada cuya línea cambió de vendedor ya no existe, y la ruta responde `404` (`EX-002`) como a cualquier comisión que no está entre las retiradas del lote. Lo construye [`RF-CM-024`](../024-revertir-comisiones-de-linea/plan.md) §12, y `ReturnCommissionIT` pierde el caso de la revertida.
