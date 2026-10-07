# PLAN — `RF-CM-024` Revertir las comisiones de una línea cuyo vendedor se corrige

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-024` |
| Especificación | [`spec.md`](spec.md) v0.3.0 |
| `spec.md` aprobada el | 30-09-2026 |
| Versión | 0.3.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |
| Enmendado el | 07-10-2026 — la cadena vieja se borra, y `V80` retira la marca (§12) |
| Enmendado el | 07-10-2026 — la liberación deja una marca y la cadena nueva va al lote más reciente sin pagar; `V82` (§13) |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

El orden de los bloqueos y el esquema son los de [`RF-CM-022`](../022-retirar-comision-de-lote/plan.md) §1 y §2. El bloqueo de la línea es el de [`RF-CM-013`](../013-devengar-comision-linea/plan.md) §1.

---

## 1. Enfoque

**Un puerto que `MV` declara y `CM` implementa, invocado dentro de la transacción de `MV`** ([`architecture.md`](../../../architecture.md) §15.2, la segunda inversión).

```java
// movements.application — lo declara MV
public interface CommissionedLineRelease {
  ReleaseOutcome release(UUID movementDetailId, UUID actorId);   // MANDATORY
}
public enum ReleaseOutcome { LIBERADA, COMISION_PAGADA, FTD_CONTADO }
```

`CM` lo implementa en `ReleaseCommissionedLineService`:

```
pg_advisory_xact_lock(ns, hashtext(:linea))                 — el de RF-CM-013: espera a un devengo en curso
EXISTS afftrack_ftds WHERE movement_detail_id = :linea      → FTD_CONTADO
SELECT … FROM commissions WHERE movement_detail_id = :linea AND reverted_at IS NULL FOR UPDATE
lockBatches(sus batch_id)                                   — por identificador (RF-CM-023)
  alguno PAGADO                                             → COMISION_PAGADA
UPDATE commissions SET reverted_at = :ahora, reverted_by = :actor WHERE id IN (…)
por lote: adjustTotal(lote, -suma de las suyas)
DELETE FROM commission_accruals WHERE movement_detail_id = :linea
                                                            → LIBERADA
```

**El bloqueo consultivo de la línea es el mismo que toma el devengo**, y es lo que resuelve la carrera más fina: un barrido que esté devengando esa línea termina antes de que se lea nada, y el devengo de la cadena nueva —que llega por el aviso de `MV` después del commit— espera a que la transacción de `MV` suelte el bloqueo. **Como el bloqueo es de transacción, dura hasta el commit de `MV`**, no hasta el final de este método.

**Responde, no lanza** (regla 3 de §15.2): `MV` traduce la negativa a su `EX-003`. Una excepción de verdad —la base cae— sí sube, y revierte la corrección entera.

**`MANDATORY`**: sin transacción de `MV` alrededor, la reversión quedaría escrita aunque la corrección fallara después (`FA-005`). Lo exige la propagación, como `CommissionPayout`.

---

## 2. Cambios de esquema

**Ninguno**: `V59` (`RF-CM-022` `T-01`) trae `reverted_at`, `reverted_by` y la unicidad parcial.

---

## 3. Componentes afectados

| Módulo | Capa | Componente | Cambio | Nota |
|---|---|---|---|---|
| `MV` | `application` | `CommissionedLineRelease`, `ReleaseOutcome` | **Nuevos — declarados por `MV`** | Documentados en su Javadoc como `CommissionPayout`. Los usa `RF-MV-016` |
| `CM` | `domain/service` | `ReleaseCommissionedLineService` | **Nuevo — implementa el puerto** | §1, auditoría |
| `CM` | `domain/repository` | `CommissionAccrualRepository` y su adaptador | Gana `hasCountedFtd`, `lockLiveCommissionsOf`, `revert` y `deleteOutcome` | `lockBatches` y `adjustTotal` son de `RF-CM-022` y `RF-CM-023` |

**ArchUnit no necesita regla nueva**: `CM` ya puede leer el `application` de `MV`, y `MV` no importa nada de `CM`. **Una prueba de arquitectura sí gana una aserción**: que ninguna clase de `movements` dependa de `commissions` —hoy lo garantiza que nadie lo haya hecho—.

---

## 4. Contrato de API

**Ninguno propio.** Lo que cambia en el contrato es de `RF-MV-016`: su `409` gana dos motivos.

---

## 5. Autorización

**Ninguna propia**: la operación es `RF-MV-016`, con `movements:assign-sellers`.

---

## 6. Auditoría

Un `ChangeEvent` `UPDATE` sobre `commission_accruals`, con la línea como entidad: `before` con su desenlace y cada comisión viva —identificador, persona, lote, importe—, y `after` con las mismas revertidas y sin desenlace. **Una sola constancia por línea**, como el devengo: las comisiones son un hecho derivado, y lo que se decide aquí es qué pasa con la línea. La corrección del vendedor la audita `MV` por su cuenta.

---

## 7. Transaccionalidad

**La de `MV`**, con `MANDATORY` (§1). Nada de aquí se confirma si `MV` no confirma.

---

## 8. Impacto sobre otros módulos

| Módulo | Qué cambia | Dónde se registra |
|---|---|---|
| `MV` | Declara el puerto y lo invoca desde `AssignSellersService` | [`specs/mv/016-asignar-vendedores-de-venta/`](../../mv/016-asignar-vendedores-de-venta/plan.md) §12, enmendado el mismo día |

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un evento síncrono de `MV` que `CM` escuche y que lance para vetar | Un evento que devuelve una respuesta por excepción es un puerto disfrazado, y la regla 3 de §15.2 pide que una respuesta legítima viaje como valor y no como excepción |
| Que `CM` devengue la cadena nueva aquí mismo | Duplicaría `RF-CM-013` en otro camino; el aviso de `MV` ya llega, y el barrido cubre su pérdida |
| Borrar las comisiones revertidas | `RN-CM-029`, y `requirements/cm.md` §5.10 |
| Comprobar «pagado» sin bloquear los lotes | Entre la comprobación y el commit de `MV` un pago podría abonar una comisión que se está revirtiendo (`CA-CM-298`) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Interbloqueo con retirar, devolver o pagar | El orden único de `RF-CM-022` §1: comisiones, después lotes por identificador |
| El bloqueo consultivo mantenido hasta el commit de `MV` retrasa el devengo de otras líneas | Es por línea, no global |
| Una suite de `MV` sin `CM` en el contexto | El contexto de pruebas es la aplicación entera; el puerto siempre tiene su implementación |

---

## 11. Estrategia de prueba

`ReleaseCommissionedLineIT`: `CA-CM-290` a `CA-CM-299`, **corrigiendo por la ruta de `RF-MV-016`** —confirmar, cerrar, pagar y retirar por sus rutas—, para que el puerto, el `MANDATORY`, el aviso y el devengo nuevo entren en la prueba. `CA-CM-298` con dos hilos. `CA-CM-294` cierra por `POST /closing` para contar el FTD. La prueba de arquitectura de §3 en la suite de ArchUnit que ya existe.

---

## 12. La cadena vieja se borra — enmienda del 07-10-2026

`RN-CM-047` enmendada ([`requirements/cm.md`](../../../requirements/cm.md) v0.34.0 §5.10). **El enfoque de §1 no cambia** —el puerto, `MANDATORY`, el bloqueo de la línea y después comisiones y lotes por identificador—; cambia **el paso que escribe**:

```
SELECT … FROM commissions WHERE movement_detail_id = :linea FOR UPDATE   — sin «reverted_at IS NULL»: ya no hay otra clase
…
DELETE FROM commissions WHERE id IN (…)                                  — en lugar del UPDATE que marcaba
por lote: adjustTotal(lote, -suma de las suyas)                          — igual que antes
```

**El esquema** lo cambia `V80`: borra las comisiones que ya estaban revertidas —ninguna contaba en ningún total, así que ningún `total_amount` cambia—, retira `reverted_at`, `reverted_by`, `ck_commissions_reverted` y `fk_commissions_reverted_by`, y devuelve `uq_commissions_detail_user` a la **restricción** de `V51`, con el mismo nombre. El orden importa: primero el `DELETE`, después el índice, y la restricción al final, cuando ya no puede chocar.

**Lo que se retira porque solo existía por la marca:**

| Componente | Cambio |
|---|---|
| `CommissionAccrualRepository` | `revert(ids, actor, at)` pasa a `delete(ids)`; `lockLiveCommissionsOf` deja de filtrar |
| `ReleaseCommissionedLineService` | Borra en vez de marcar. **La auditoría pasa a ser la única constancia** de lo borrado: `before` igual que hoy —desenlace y cada comisión con identificador, persona, lote e importe—, y `after` con `deleted_commissions` y `deleted_by` |
| `CommissionBatchRepository` | `LockedCommission` pierde `reverted`; `hasLiveCommissions` cuenta todas |
| `WithdrawCommissionService`, `ReturnCommissionService` | Pierden la comprobación de la comisión revertida y su `EX-005` (`RF-CM-022`, `RF-CM-023`) |
| `CommissionBatchQueryRepository` y su adaptador | `CommissionRow` pierde `revertedAt` y `revertedBy`; el número de comisiones del listado deja de filtrar |
| `CommissionBatchDetailResponse` | `CommissionLine` pierde `revertedAt` y `revertedBy`; `returnable` deja de mirar si la comisión vive (`RF-CM-010`, `RF-CM-012`) |
| `CommissionClosingRepository` | El cierre deja de filtrar `reverted_at` al decidir qué abierto se cierra (`RF-CM-009`) |
| `CommissionBatchController` | La prosa del detalle, de mis lotes, de retirar y de devolver deja de hablar de lo revertido |

**Contrato**: `CommissionLine` pierde dos campos. **Es un cambio que el front nota**: quien leyera `revertedAt` deja de recibirlo. El esquema se regenera; la prosa de las `@Operation` se corrige a mano.

| Alternativa | Por qué no |
|---|---|
| Seguir marcando | Una fila que no se paga ni suma, que solo se lee, cuando la auditoría ya guarda lo mismo. Fue la decisión del 30-09-2026 y el responsable la cambió |
| Borrar en adelante y dejar las columnas para lo ya marcado | Dos formas de «salir de la cadena» conviviendo, un `revertedAt` en la API que no volvería a aparecer, y una unicidad parcial que ya no protege nada |
| Exigir que todos los lotes de la cadena estén `ABIERTO` | Lo descartó el responsable: un pendiente todavía no se ha pagado |

**Pruebas**: `ReleaseCommissionedLineIT` reescribe `CA-CM-290`, `CA-CM-292`, `CA-CM-296` y `CA-CM-299` como `CA-CM-340` a `CA-CM-343`, y los de las enmiendas de `RF-CM-010`, `RF-CM-012` y `RF-CM-013` como `CA-CM-344` a `CA-CM-346`, con `CA-MV-700`. `CA-CM-305` prepara la línea **borrando** su cadena por SQL. `WithdrawCommissionIT` y `ReturnCommissionIT` pierden el caso de la revertida, y `PayCommissionBatchesIT` vacía su pendiente borrando.

## 13. La cadena nueva va al lote más reciente sin pagar — enmienda del 07-10-2026

`RN-CM-051`. **El destino lo decide el devengo** (`RF-CM-013` `plan.md` §18), y esta liberación solo tiene que **dejar dicho** que la línea se reatribuyó, en su misma transacción —la de `MV`—: si la corrección se deshace, la marca se deshace con ella.

**Esquema, `V82`**: `commission_reattributions (movement_detail_id uuid PRIMARY KEY REFERENCES movement_details ON DELETE CASCADE, released_at timestamptz NOT NULL)`. **`CASCADE`**: la marca no es dinero, y `RESTRICT` obligaría a cada suite que limpia ventas a limpiarla antes.

| Componente | Cambio |
|---|---|
| `CommissionAccrualRepository` | Gana `markReattributed(lineId, at)` —`INSERT … ON CONFLICT (movement_detail_id) DO UPDATE`, porque una segunda corrección antes del devengo la reescribe—, `isReattributed(lineId)` y `clearReattribution(lineId)` |
| `ReleaseCommissionedLineService` | Tras borrar la cadena y el desenlace, `markReattributed`. **Solo al liberar**: con `COMISION_PAGADA` o `FTD_CONTADO` no escribe nada |

| Alternativa | Por qué no |
|---|---|
| Que la marca sea un estado de `commission_accruals` («`REATRIBUIDA`») | El desenlace de la línea se borra para que el devengo la trate como nueva (`RN-CM-047`); un cuarto estado obligaría al barrido, al listado de `RF-CM-014` y a su contrato a conocerlo |
| Pasar el dato en el aviso de `MV` | El barrido no recibe avisos, y es quien recoge el aviso perdido |
| Devengar aquí, en la liberación | Fue descartado en §9 y sigue valiendo |

**Pruebas**: `ReleaseCommissionedLineIT` reescribe `CA-CM-291` como `CA-CM-356` —cerrar, corregir, y la comisión del director en su **pendiente**; la del vendedor nuevo, que no tenía lote, en un **abierto** recién abierto (`CA-CM-358`)— y gana `CA-CM-357` y `CA-CM-360`.
