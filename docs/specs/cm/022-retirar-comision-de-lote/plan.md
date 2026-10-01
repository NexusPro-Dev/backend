# PLAN — `RF-CM-022` Retirar una comisión de un lote pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-022` |
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

La mecánica común de `CM` la fijó [`RF-CM-001`](../001-registrar-tasa-comision-rol/plan.md), y la del lote abierto [`RF-CM-013`](../013-devengar-comision-linea/plan.md) §4: **se heredan sin repetirlas**. La forma de una acción sobre un lote es la de [`RF-CM-011`](../011-marcar-lote-pagado/plan.md).

---

## 1. Enfoque

**Bloquear la comisión, bloquear los dos lotes por identificador, comprobar, mover y ajustar — en una transacción.**

```
SELECT … FROM commissions WHERE id = :comision FOR UPDATE            → 404 si no hay fila
  batch_id <> :lote → 404 (EX-002) · reverted_at no nulo → 409 (EX-005)
SELECT … FROM commission_batches WHERE id = :lote FOR UPDATE          → 404 si no hay fila
  ABIERTO → 409 (EX-003) · PAGADO → 409 (EX-004)
lockOpenBatch(user, currency)                                          — la de RF-CM-013, abre si no hay
UPDATE commissions SET batch_id = :abierto, withdrawn_from_batch_id = :lote WHERE id = :comision
UPDATE commission_batches SET total_amount = total_amount - :importe, updated_at = :ahora WHERE id = :lote
UPDATE commission_batches SET total_amount = total_amount + :importe, updated_at = :ahora WHERE id = :abierto
```

**El orden de los bloqueos es el que evita el interbloqueo, y es uno solo en todo el módulo** desde esta tripleta: **primero las comisiones, después los lotes, y los lotes por identificador**. Retirar (`RF-CM-022`), devolver (`RF-CM-023`) y revertir (`RF-CM-024`) toman comisiones y luego lotes; pagar (`RF-CM-011`) y cerrar (`RF-CM-009`) solo toman lotes, y devengar (`RF-CM-013`) solo el abierto. **Con UUID v7 el pendiente de una persona tiene siempre un identificador menor que su abierto** —nació antes—, de modo que «por identificador» y «el pendiente antes que el abierto» dicen lo mismo aquí; el plan se apoya en el primero, que vale también cuando la reversión toma lotes de varias personas.

**`lockOpenBatch` se reutiliza tal cual**, con su `GREATEST` (`RF-CM-013` `tasks.md` §3.1): si un cierre acaba de cerrar el abierto, el retiro abre uno nuevo que empieza en el fin del cerrado, y `FA-004` se cumple por el mismo mecanismo que ya protege al devengo.

**El pago que espera** (`CA-CM-280`): `RF-CM-011` toma el lote con `FOR UPDATE`. Si el retiro lo tiene, el pago espera y abona el total ya rebajado; si lo tiene el pago, el retiro espera, lee `PAGADO` y responde `409` sin mover nada.

---

## 2. Cambios de esquema

**`V59__cm_corregir_lote_pendiente.sql`** —la siguiente libre a 30-09-2026— lleva **todo el esquema de §5.10**, aunque `RF-CM-023` y `RF-CM-024` lo usen también: las columnas y los permisos de un cambio nacen juntos, como en `V51`.

| Objeto | Definición | Regla |
|---|---|---|
| `commissions.reverted_at` | `timestamptz NULL` | `RN-CM-047` |
| `commissions.reverted_by` | `uuid NULL`, `fk_commissions_reverted_by` → `users` | `RN-CM-047` |
| `commissions.withdrawn_from_batch_id` | `uuid NULL`, `fk_commissions_withdrawn_from` → `commission_batches` | `RN-CM-046` |
| `ck_commissions_reverted` | `(reverted_at IS NULL) = (reverted_by IS NULL)` | `RN-CM-047` |
| `ck_commissions_withdrawn` | `withdrawn_from_batch_id IS NULL OR withdrawn_from_batch_id <> batch_id` | `RN-CM-046` |
| `uq_commissions_detail_user` | **Se retira la restricción y nace un índice único parcial con el mismo nombre**, `ON commissions (movement_detail_id, user_id) WHERE reverted_at IS NULL` | `RN-CM-027` |
| `ix_commissions_withdrawn_from` | Sobre `withdrawn_from_batch_id`, parcial `WHERE withdrawn_from_batch_id IS NOT NULL` | La lista de retiradas del detalle (`RF-CM-010`) |
| Permisos | `commission-batches:withdraw-commission` y `commission-batches:return-commission`, serie de `CM`, el siguiente sufijo libre; a `SUPERADMIN` y `ADMIN` **explícitos** | `security.md` v0.89.0 |

**El nombre se conserva** porque el adaptador de `RF-CM-013` traduce la violación por nombre de restricción, y un índice único viola con su nombre igual que una restricción. **Catálogo 169 → 171**, `ADMIN` 167 → 169.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `CommissionBatchRepository` y su adaptador | Gana `lockCommission`, `moveCommission` y `adjustTotal` | `lockById` y `lockOpenBatch` ya existen |
| `domain/service` | `WithdrawCommissionService` | Nuevo | §1, auditoría |
| `interfaces` | `CommissionBatchController` | Gana `POST /{id}/commissions/{commissionId}/withdrawal` | Devuelve el detalle de `RF-CM-010` |

**Lo que la respuesta muestra de más** —las retiradas del lote, y de dónde viene cada comisión— es de `RF-CM-010`, enmendado el mismo día (su §14).

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/commission-batches/{id}/commissions/{commissionId}/withdrawal` | `commission-batches:withdraw-commission` |

**Sin cuerpo.** Una acción con nombre sobre un recurso, como `…/payment`.

| Código | Cuándo |
|---|---|
| `200` | Retirada, con el pendiente como queda |
| `400` | Identificador malformado |
| `401` / `403` | Sin token / sin el permiso |
| `404` | El lote o la comisión no existen, o la comisión no es de ese lote |
| `409` | Lote abierto o pagado, o comisión revertida, con el motivo en el mensaje |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('commission-batches:withdraw-commission')")`; en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

Un `ChangeEvent` `UPDATE` sobre `commissions`, con `before` —`batch_id` y `withdrawn_from_batch_id`— y `after`, y el importe. **Es la constancia de quién movió qué y cuándo** que `requirements/cm.md` §5.10 le pide a la auditoría en lugar de a una tabla propia. Los totales de los dos lotes no se auditan aparte: son consecuencia del movimiento.

---

## 7. Transaccionalidad

`@Transactional`: los bloqueos, la apertura del lote si hace falta, el movimiento y los dos ajustes.

---

## 8. Impacto sobre otros módulos

**Ninguno.** `MV` no se entera: el lote no se ha pagado y no hay abono que tocar.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Recalcular los dos totales con un `SUM` en lugar de sumar y restar | Correcto, pero lee todas las comisiones de los dos lotes en cada retiro; el `UPDATE` que suma sobre la fila es el patrón del devengo y ya está bloqueado |
| Retirar varias en una petición | `spec.md` §2.1: se decidió una por petición |
| Una columna de estado en la comisión (`RETIRADA`) en lugar de moverla | La comisión seguiría en el pendiente y el total tendría que excluirla en cada lectura; moverla deja cada lote diciendo lo que va a pagar |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Interbloqueo con la reversión de `RF-CM-024` | El orden único de §1 |
| Retirar de un lote que se está pagando | El `FOR UPDATE` del lote, el mismo del pago (`CA-CM-280`) |
| Los recuentos del catálogo | Todas las suites que lo cuentan: `PermissionIT`, `PermissionsSeedIT`, `MovementsPermissionsSeedIT`, `TeamsPermissionsSeedIT`, `SaleLinesPermissionSeedIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`, `RoleDetailIT`, `SystemRolesSeedIT` —buscar `169` y `167`, no solo `isEqualTo`— |

---

## 11. Estrategia de prueba

`WithdrawCommissionIT`: `CA-CM-273` a `CA-CM-281`, con lotes producidos por devengo y cierre reales —confirmando por la API de `MV` y cerrando por `POST /closing`—, como `PayCommissionBatchIT`. `CA-CM-280` con dos hilos (`ConcurrencyHarness`). La suite **no** es transaccional: el devengo es `AFTER_COMMIT`.
