# PLAN — `RF-CM-027` Borrar los lotes vacíos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-027` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 08-10-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |
| Enmendado el | 08-10-2026 — el borrado se acota por estado y lo usan el cierre y el pago (§12) |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

**El borrado ya existe**: lo construyó el 07-10-2026 [`RF-CM-022`](../022-retirar-comision-de-lote/plan.md) §13 —`deleteIfEmpty` en el repositorio y `EmptyBatchRemoval`, con su auditoría—, y lo llamaban retirar, devolver y liberar una línea. **Este requerimiento cambia quién lo llama**: ellos dejan de hacerlo (`RF-CM-022` §14, `RF-CM-023` §14, `RF-CM-024` §15) y lo llama una ruta.

---

## 1. Enfoque

**Una sola transacción que busca los vacíos, los bloquea en el orden del módulo y borra cada uno que siga vacío.**

```
candidatos = ids de los lotes ABIERTO o PENDIENTE sin ninguna comisión      — sin bloquear
si no hay → respuesta vacía
bloquear las comisiones retiradas de alguno de ellos (withdrawn_from_batch_id)  — FOR UPDATE
bloquear los candidatos, por identificador                                     — lockBatches
por cada uno: deleteIfEmpty → si borró, auditar (EmptyBatchRemoval)
responder los borrados, por identificador
```

**La búsqueda no decide; decide el `DELETE`.** La lista se toma sin bloqueo y puede envejecer antes de bloquear: un candidato puede recibir una comisión entretanto. Por eso cada borrado vuelve a preguntar, **en su propia sentencia y con el lote ya bloqueado**, si sigue sin comisiones y sin pagar (`deleteIfEmpty`, que ya lo hace). Una sentencia nueva ve lo que confirmó quien tenía el bloqueo antes; una lectura vieja no (`CA-CM-374`).

**Por qué las comisiones se bloquean antes que los lotes.** Borrar un pendiente pone a nulo `withdrawn_from_batch_id` en lo que se retiró de él (`V84`, `ON DELETE SET NULL`), y eso **escribe en esas comisiones**. Devolver una de ellas (`RF-CM-023`) bloquea primero la comisión y después los lotes. Si el borrado tomara el lote primero y la comisión después, los dos se esperarían el uno al otro. **El orden del módulo es uno —comisiones, después lotes por identificador— y este requerimiento lo sigue** (`RF-CM-022` `plan.md` §1).

**El devengo no se toca.** Un devengo que espera el bloqueo de un abierto que acaba de borrarse no lo encuentra al despertar —una fila borrada no vuelve en un `FOR UPDATE`— y abre otro: `lockOpenBatch` ya reintenta. Si el devengo bloqueó primero, el `DELETE` encuentra su comisión y no borra. **`lockLatestUnpaidBatch`**, el de la cadena de una línea reatribuida, busca sin bloquear y bloquea después por identificador: **si el candidato desapareció entre las dos sentencias, vuelve a buscar** en vez de leer una fila que no hay. `RF-CM-022` §13 lo anunció y no se llegó a escribir; ahora es necesario.

---

## 2. Cambios de esquema

**`V86__cm_borrar_lotes_vacios.sql`**: el permiso `commission-batches:delete-empty`, con la marca v7 de `V79` (`01a10e829000`), secuencia `7010` —la siguiente a la `7009` de `V83`— y la serie de `CM` donde `V81` la dejó (`000032` → `000033`), a `SUPERADMIN` y `ADMIN` **explícitos**, como `V60`. **Catálogo 206 → 207**, `ADMIN` 204 → 205. Con la guarda del recuento, como `V83`.

**Ninguna tabla cambia**: `fk_commissions_withdrawn_from` ya es `ON DELETE SET NULL` desde `V84`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `CommissionBatchRepository` | Gana **`findEmptyUnpaidBatchIds()`** —los `ABIERTO` y `PENDIENTE` sin comisiones, por identificador— y **`lockWithdrawnFrom(lotes)`** —`FOR UPDATE` sobre las comisiones retiradas de esos lotes, por identificador—. `lockLatestUnpaidBatch` vuelve a buscar si el candidato desapareció | §1 |
| `domain/service` | `EmptyBatchRemoval` | **Se queda**, con su auditoría; cambia su motivo, que ahora cita `RF-CM-027`. Su único llamador pasa a ser el servicio nuevo | |
| `domain/service` | `DeleteEmptyBatchesService` | Nuevo, `@Transactional` | §1 |
| `application` | `EmptyBatchesDeletionResponse` | Nuevo | `deleted` —por lote: `id`, `code`, `userId`, `currencyId`, `status`, `periodStart`, `periodEnd`— y `deletedCount`. `@Schema(name)` en los dos `record`s |
| `interfaces` | `CommissionBatchController` | Gana `DELETE /empty` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `DELETE` | `/api/v1/commission-batches/empty` | `commission-batches:delete-empty` |

**Sin cuerpo.**

```json
{
  "deleted": [
    { "id": "…", "code": "LOT-20261008-ABC234", "userId": "…", "currencyId": "…",
      "status": "PENDIENTE", "periodStart": "…", "periodEnd": "…" }
  ],
  "deletedCount": 1
}
```

| Código | Cuándo |
|---|---|
| `200` | Siempre, **aunque no se borre ninguno** (`CA-CM-370`) |
| `401` / `403` | Sin token / sin el permiso |

**`DELETE` y no un `POST` de acción**, como el cierre: lo que hace es quitar recursos, y lo dice el verbo. **`200` con cuerpo y no `204`**: la respuesta es la lista de lo que se borró, que es lo que pidió el responsable del proyecto. **`/empty` no choca con `/{id}`**: no hay `DELETE /{id}`, y la variable es un UUID.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('commission-batches:delete-empty')")`; en `PERMISO_DE_CADA_OPERACION`. **De administración y sin alcance**: borra lotes de todas las personas.

---

## 6. Auditoría

**Una eliminación física por lote**, la de `EmptyBatchRemoval`: `DeletionEvent` sobre `commission_batches`, `DeletionType.PHYSICAL`, con código, persona, moneda, estado y periodo (`CA-CM-371`). La petición no se audita aparte: no cambia nada que no esté en esas filas.

---

## 7. Transaccionalidad

**Una transacción para todos.** Lo que se hace por lote es una sentencia, no un abono: no hay nada en un lote que pueda fallar sin que falle la base entera, de modo que partirlo, como `RF-CM-025`, no protege nada y deja una respuesta a medias si la conexión se corta. **Los bloqueos duran lo que dura la transacción**, y la retienen solo los lotes vacíos, en los que nadie más escribe salvo un devengo o una devolución que llegan a la vez.

---

## 8. Impacto sobre otros módulos

**Ninguno.** Un lote sin pagar no tiene movimiento en `MV` (`commission_batches.movement_id` solo existe en los `PAGADO`), y `IN` cuenta lotes sin guardarlos.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un solo `DELETE … WHERE NOT EXISTS … RETURNING` | No sigue el orden de bloqueos del módulo —tomaría los lotes antes que las comisiones retiradas— y no da a cada lote un instante en que esté bloqueado y se vuelva a mirar |
| Una transacción por lote, como `RF-CM-025` | §7 |
| Tomar el bloqueo consultivo del cierre | Cerrar no toca los abiertos vacíos (`RN-CM-048`) y no hay carrera que ordenar; impediría borrar mientras corre un cierre sin ninguna razón |
| Retirar `EmptyBatchRemoval` y escribir el borrado aquí | Ya está escrito, auditado y probado; cambia de llamador, no de forma |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Un interbloqueo con devolver** | §1: comisiones antes que lotes. `CA-CM-372` lo cubre en serie |
| **Una comisión perdida por la cascada** de `fk_commissions_batch` (`ON DELETE CASCADE`) | El `DELETE` exige que no haya ninguna, con el lote bloqueado; y una comisión que llegue después no puede referirse a un lote borrado: la clave la rechaza y el devengo, que lo bloqueó antes de escribir, ya abrió otro (`CA-CM-374`) |
| Los recuentos del catálogo | `PermissionIT`, `PermissionsSeedIT`, `SaleLinesPermissionSeedIT` y `TeamsPermissionsSeedIT` |

---

## 11. Estrategia de prueba

`DeleteEmptyBatchesIT`, con lotes de devengo y cierre reales, como `WithdrawCommissionIT`; un lote vacío se fabrica **retirando y devolviendo** por la API, ya que desde hoy esas rutas lo dejan donde está. `CA-CM-368` a `CA-CM-373` y `CA-CM-375`. **`CA-CM-374`** con dos hilos —borrar y confirmar una venta del mismo vendedor— y una barrera, varias vueltas: al final cada comisión está en un lote que existe.

## 12. El borrado se acota por estado — enmienda del 08-10-2026

`RN-CM-052` enmendada. **El borrado de §1 pasa a recibir los estados** que mira: `DeleteEmptyBatchesService.deleteEmpty(estados)`, y `deleteAll()` es `deleteEmpty(ABIERTO, PENDIENTE)`. `findEmptyUnpaidBatchIds` recibe los mismos estados. Lo llaman:

| Quién | Estados | Transacción |
|---|---|---|
| La ruta de esta tripleta | `ABIERTO`, `PENDIENTE` | La suya |
| El cierre ([`RF-CM-009`](../009-cerrar-periodo-comisiones/plan.md) §15) | `ABIERTO` | La del cierre |
| `EmptyBatchesAfterPayment`, tras un pago ([`RF-CM-011`](../011-marcar-lote-pagado/plan.md) §13, [`RF-CM-025`](../025-pagar-varios-lotes/plan.md) §12) | `PENDIENTE` | La suya, después del pago |

**`EmptyBatchesAfterPayment`** (nuevo, `domain/service`, sin `@Transactional`) llama a `deleteEmpty(PENDIENTE)` y **registra y se traga** cualquier excepción: el pago ya está hecho. `EmptyBatchRemoval` recibe el motivo de quien llama, para que la auditoría diga si el lote lo borró la orden, el cierre o un pago.
