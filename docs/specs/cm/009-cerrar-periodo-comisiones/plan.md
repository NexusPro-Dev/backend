# PLAN — `RF-CM-009` Cerrar el periodo de comisiones, y consultar los cierres

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-009` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 28-09-2026 |
| Versión | 0.6.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |
| Enmendado el | 29-09-2026 — la liquidación afftrack dentro de la transacción externa (§12) |
| Enmendado el | 30-09-2026 — el paso a pendiente salta los abiertos sin comisiones vivas (§13) |
| Enmendado el | 07-10-2026 — el abierto vacío se mira sin la marca de revertida (§14) |
| Enmendado el | 08-10-2026 — el cierre borra los abiertos vacíos (§15) |
| Enmendado el | 08-10-2026 — el cierre programado lee la elección al abrir el turno y paga lo que cerró; `V87` (§16) |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

Hereda de [`RF-CM-013`](../013-devengar-comision-linea/plan.md) el esquema, el servicio de devengo y la secuencia del lote abierto, y **no los repite**.

---

## 1. Enfoque

**Una transacción externa que sostiene un bloqueo consultivo de principio a fin, y dentro de ella el barrido en transacciones propias.**

```
0. turno     (solo PROGRAMADO, tx propia y confirmada)
             INSERT INTO commission_closings (… scheduled_for = :turno …) ON CONFLICT DO NOTHING
             → 0 filas: otra instancia tomó el turno; se sale sin hacer nada
tx externa   PROGRAMADO: pg_advisory_xact_lock(ns_cierre)      — espera a un manual en curso
             MANUAL:     pg_try_advisory_xact_lock(ns_cierre)  — si no lo toma: 409
             MANUAL:     INSERT de la constancia (REQUIRES_NEW, confirmada)
  barrido    CommissionAccrualService.accrue(pendientes) y retryRejected()
             — cada línea en REQUIRES_NEW, en su propia conexión: el bloqueo sigue tomado
  cierre     SELECT … FROM commission_batches WHERE status = 'ABIERTO' FOR UPDATE
             UPDATE … SET status = 'PENDIENTE', period_end = :ahora, closing_id = :c
             UPDATE commission_closings SET closed_at = :ahora, contadores
```

**La exclusión entre instancias es la fila del turno, no el bloqueo** (`requirements/cm.md` v0.20.0 §7.8): un bloqueo consultivo solo ordena, y la instancia que llegase segunda cerraría otra vez. `:turno` es **la hora nominal** del disparo, calculada de la expresión `cron` —`CronExpression.next` desde un instante justo anterior—, y no `now()`, que difiere en milisegundos entre instancias. **Se confirma antes de empezar** para que la otra instancia choque y se vaya, en lugar de quedarse esperando a que esta termine.

**El bloqueo consultivo hace otra cosa**: que un cierre programado y uno manual **no se crucen**. Se toma en una transacción que dura todo el cierre —`xact`, de modo que se suelta solo si la instancia cae— y el barrido abre **las suyas** en otras conexiones del pool, así que el bloqueo sigue tomado mientras barre. El manual lo **intenta** y, si está tomado, responde `409` (`EX-001`); el programado **espera**, porque no hay nadie a quien responder. **La constancia se escribe en su propia transacción** para que un fallo del cierre no la borre (`EX-002`).

**El `FOR UPDATE` del paso 3 es la frontera con el devengo** (`CA-CM-178`): `RF-CM-013` toma el lote abierto con `FOR UPDATE` antes de sumarle. Si el devengo lo tomó primero, el cierre espera y cierra con la comisión dentro; si el cierre lo tomó primero, el devengo espera, lo encuentra `PENDIENTE` y abre uno nuevo.

**El barrido recorre las líneas por clave**, `CommissionableLines.idsAfter(cursor, 500)`, y para cada tanda pregunta a `commission_accruals` cuáles no tienen fila. Es **lineal en las líneas vendidas**, y se acepta (§10).

---

## 2. Cambios de esquema

**Ninguno propio**: `commission_closings` la crea `V51` (`RF-CM-013`), **con las columnas de `cm.md` v0.20.0** —`scheduled_for`, `started_at`, `closed_at` nulable y `uq_commission_closings_scheduled`—. El plan de `RF-CM-013` dice «como `cm.md` las declara», de modo que no necesita enmienda.

**Configuración** (`application.yml`, con sus valores en `.env.example`):

| Propiedad | Por defecto | Qué |
|---|---|---|
| `nexus.commissions.closing.enabled` | `true` | Apaga el programado (`CA-CM-179`); el manual sigue |
| `nexus.commissions.closing.cron` | `0 0 0 1 * *` | Mensual, 00:00 del día 1 (`spec.md` §14) |

La zona es `nexus.business.zone` (`BusinessCalendar`, `RF-CM-013`), **no** un literal (`architecture.md` v0.39.0 §15.1.1).

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `ClosingOrigin` | Nuevo | `PROGRAMADO`, `MANUAL` |
| `domain/repository` | `CommissionClosingRepository` y adaptador | Nuevo | Apertura con `ON CONFLICT`, cierre de lotes, contadores, listado |
| `domain/service` | `CloseCommissionPeriodService` | Nuevo | Los tres pasos de §1. `ClosingSummary closeScheduled(OffsetDateTime turno)` y `closeManually(UUID actor)` |
| `domain/service` | `ListCommissionClosingsService` | Nuevo | La lectura |
| `interfaces` | `CommissionClosingJob` | Nuevo | `@Scheduled(cron = "${nexus.commissions.closing.cron}", zone = "${nexus.business.zone}")`; `@ConditionalOnProperty` sobre `enabled`. Captura y registra cualquier excepción, como `ExpiredTokenPurgeJob`: una tarea programada que lanza no avisa a nadie |
| `interfaces` | `CommissionBatchController` | Nuevo | `POST /commission-batches/closing` |
| `interfaces` | `CommissionClosingController` | Nuevo | `GET /commission-closings` |
| `application` | `CommissionClosingResponse`, `CommissionClosingPageResponse`, `ListCommissionClosingsRequest` | Nuevos | `@Schema(name = …)` explícito en cada `record` (springdoc funde los de igual nombre simple) |
| `shared/scheduling` | `SchedulingConfig` | Javadoc | «Qué se programa hoy» pasa a nombrar dos tareas, y **declara el `TaskScheduler` de dos hilos** que su propio Javadoc pedía para el día en que hubiera dos |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/commission-batches/closing` | `commission-batches:settle` |
| `GET` | `/api/v1/commission-closings` | `commission-closings:read` |

**Cierre**: sin cuerpo. `200` con la constancia —`id`, `origin`, `triggeredBy`, `startedAt`, `closedAt`, `batchesClosed`, `linesSwept`, `linesRetried`, `linesRecovered`—. **`200` y no `201`**: no se crea un recurso que el cliente vaya a leer por su dirección, se ejecuta una acción.

| Código | Cuándo |
|---|---|
| `200` | Cerrado, aunque fuesen cero lotes |
| `401` / `403` | Sin token / sin el permiso |
| `409` | Hay un cierre en curso (`EX-001`) |

**Listado**: página envuelta de siempre, `closedAt` descendente —los que fallaron, sin `closedAt`, por `startedAt`—; filtros `origin`, `from`, `to` sobre `startedAt`. `400` con todos los errores juntos.

---

## 5. Autorización

Un `@PreAuthorize` por operación; las dos rutas entran en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT`. **El programado no pasa por autorización**: no hay actor. Su constancia lleva `triggered_by` nulo, y `ck_commission_closings_origin` lo ata al origen.

---

## 6. Auditoría

Un `ChangeEvent` sobre `commission_closings`, `INSERT` al abrir y `UPDATE` al cerrar, con el actor si es manual. **Los lotes no se auditan uno a uno**: la constancia dice cuántos, y cada lote dice qué cierre lo cerró.

---

## 7. Transaccionalidad

La externa, con el bloqueo y el cierre de los lotes; una por línea en el barrido; y una para la constancia (§1). **Pide dos conexiones a la vez como mínimo**, lo que el pool ya permite. **Si el cierre falla**, se revierte entero —ningún lote a medio cerrar— y la constancia queda sin `closed_at` (`EX-002`). **Si falla una línea del barrido**, cada línea tiene su transacción y el cierre sigue (`FA-003`).

---

## 8. Impacto sobre otros módulos

**`MV`**: el barrido usa `CommissionableLines.idsAfter`, publicada por `RF-CM-013`. Ninguna enmienda.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Solo un bloqueo consultivo para las réplicas | Ordena y no excluye: la segunda cerraría otra vez (`cm.md` v0.20.0) |
| ShedLock u otra librería | Hace lo mismo que una fila con clave única, con una tabla y una dependencia más |
| Barrido y cierre en una transacción | Una línea que falla revertiría el cierre entero |
| `201 Created` en el manual | No se crea un recurso direccionable |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| El barrido crece con cada venta | Tandas de 500 por clave; si pesa, una marca de agua por fecha de confirmación es el siguiente paso, sin cambiar el contrato |
| El planificador de un hilo retrasa la purga de sesiones mientras cierra | `TaskScheduler` de dos hilos (§3) |
| Una instancia calcula otro `:turno` por un reloj desviado | El turno sale de la expresión, redondeado al segundo; un desvío de minutos es una avería del servidor, no del cierre |

---

## 11. Estrategia de prueba

- **`CloseCommissionPeriodIT`**: `CA-CM-170` a `CA-CM-178`, llamando al servicio con un turno fijo y por la API para el manual. `CA-CM-174` con **dos hilos y el mismo turno**. `CA-CM-178` con un devengo y un cierre en dos hilos, comprobando que la suma de totales cuadra con la de comisiones.
- **`CommissionClosingJobIT`**: `CA-CM-179`, con `enabled=false` el bean no existe; con `true`, la anotación lleva la zona de la propiedad.
- **`ListCommissionClosingsIT`**: `CA-CM-180`.

## 12. La liquidación afftrack — enmienda del 29-09-2026

`RN-CM-043`, construida por [`RF-CM-020`](../020-liquidar-comisiones-afftrack/plan.md) §1. **`CloseCommissionPeriodService` gana un paso en la transacción externa**, después del barrido y antes del `SELECT … FOR UPDATE` de los lotes:

```
  corte    = now()
  afftrack AfftrackSettlementService.settle(closingId, corte)   — MISMA tx
  cierre   ahora = max(now(), corte + 1 µs)                     — antes, now()
```

**Dos cambios de comportamiento, y los dos se dicen**: el instante del cierre deja de ser un `now()` suelto —es posterior al corte, porque un lote que la liquidación abre nace en el corte y `ck_commission_batches_periodo` exige un fin mayor que el inicio—; y **un fallo de la liquidación revierte el cierre entero**, que es `EX-002`, mientras que un fallo del barrido sigue sin pararlo. **La respuesta del cierre a mano no cambia.** `CloseCommissionPeriodIT` tiene que seguir en verde sin tocar sus criterios; los de la liquidación viven en `AfftrackSettlementIT`.

## 13. Los abiertos vacíos — enmienda del 30-09-2026

`RN-CM-048`. **El paso a `PENDIENTE` gana una condición**, y es la única sentencia que cambia:

```
… WHERE status = 'ABIERTO'
    AND EXISTS (SELECT 1 FROM commissions c WHERE c.batch_id = b.id AND c.reverted_at IS NULL)
```

**Lo que no se cierra no se cuenta** en `batches_closed`. El `FOR UPDATE` sobre los abiertos lleva la misma condición, de modo que un abierto vacío **no se bloquea**: una devolución que lo esté vaciando a la vez no espera al cierre, y un devengo que lo llene justo antes lo hace cerrable —si llega a tiempo, entra en este cierre; si no, en el siguiente—. `CloseCommissionPeriodIT` gana `CA-CM-300`, con el abierto vaciado por una devolución (`RF-CM-023`).

## 14. Sin la marca de revertida — enmienda del 07-10-2026

`RN-CM-047` enmendada y `RN-CM-048` precisada: la condición de §13 pasa a `EXISTS (SELECT 1 FROM commissions c WHERE c.batch_id = b.id)`, porque desde `V80` toda comisión de un lote es viva. **El comportamiento no cambia**. Lo hace [`RF-CM-024`](../024-revertir-comisiones-de-linea/plan.md) `T-09`.

## 15. El cierre borra los abiertos vacíos — enmienda del 08-10-2026

`RN-CM-052` enmendada ([`requirements/cm.md`](../../../requirements/cm.md) v0.42.0 §5.10, «Quinta enmienda»). **Tras `closeOpenBatches`, y en la misma transacción**, `cerrar` llama a `DeleteEmptyBatchesService.deleteEmpty(ABIERTO)` —el borrado de [`RF-CM-027`](../027-borrar-lotes-vacios/plan.md), acotado a los abiertos—, que se une a la transacción del cierre: si el cierre falla después, los abiertos vuelven. Su número entra en la auditoría del cierre como `empty_batches_deleted` (`CA-CM-377`). **`commission_closings` no gana columna**: la constancia cuenta lotes cerrados, y lo borrado ya está, lote a lote, en `audit_deletion_log`.

**El orden de bloqueos se respeta**: un abierto nunca es el origen de una retirada —se retira desde un pendiente—, de modo que `lockWithdrawnFrom` no bloquea nada, y los abiertos vacíos se bloquean por identificador, sin cruzarse con los que `closeOpenBatches` ya tomó, que tienen comisiones. **Un devengo que llega a la vez** gana o pierde el bloqueo del abierto: si gana, el `DELETE` encuentra su comisión y no borra, y el lote queda abierto para el cierre siguiente; si pierde, `lockOpenBatch` no lo encuentra y abre otro.

**Pruebas**: `DeleteEmptyBatchesIT` gana `CA-CM-376` y `CA-CM-377`; `ReturnCommissionIT` cambia `CA-CM-300` por `CA-CM-376`: el abierto que se vació al devolver ya no sigue abierto tras el cierre, se borra.

## 16. El cierre programado paga lo que cerró — enmienda del 08-10-2026

`RN-CM-053`, `RN-CM-054`. **Dos pasos nuevos en `closeScheduled`, uno al abrir y otro al terminar**:

```
openScheduled(id, turno):                                  — su transacción aparte, la de siempre
    pg_advisory_xact_lock(clave del turno)                  — el de RF-CM-029 §1
    modo = commission_payment_choices(turno) o AUTOMATICO
    INSERT commission_closings (…, payment_mode = modo)     — la fila única del turno, como antes
cerrar(id) …                                               — sin cambios: confirma el cierre
si modo = AUTOMATICO:
    lotes = commission_batches con closing_id = id y PENDIENTE
    PayCommissionBatchesService.pay(lotes)                  — cada uno en su transacción (RF-CM-025)
    commission_closings: batches_paid, batches_not_paid     — una sentencia más
```

**La elección se lee al abrir el turno**, bajo el bloqueo del turno, y **se escribe en la fila del cierre**: lo que pague después no vuelve a mirar `commission_payment_choices`. Es lo que hace que una elección no pueda colarse a mitad (`RF-CM-029` `CA-CM-389`).

**El pago va después de confirmar el cierre**, fuera de su transacción y con la de cada lote, **reutilizando `PayCommissionBatchesService`**: pagar así es el pago de `RF-CM-011` —abono, auditoría y borrado de los pendientes vacíos (`RN-CM-052`)— sin escribirlo otra vez. **La auditoría de cada pago va sin persona**, como la del cierre programado. Si el proceso se cae entre el cierre y el pago, los lotes se quedan `PENDIENTE` y la fila del cierre con cero pagados: **no hay reintento** (`requirements/cm.md` §5.12).

**Solo los lotes de este cierre** (`CA-CM-395`): `closing_id` es el cierre que los pasó a `PENDIENTE`, y los de cierres anteriores tienen otro. **`closeManually` no cambia**: su fila nace sin modo y no paga (`CA-CM-397`).

**`CommissionClosingResponse`** gana `paymentMode`, `batchesPaid` y `batchesNotPaid`, para que `GET /commission-closings` diga cómo se pagó cada cierre.

**Pruebas**: `CloseCommissionPeriodIT` gana `CA-CM-393` a `CA-CM-398`. **Sus ocho llamadas a `closeScheduled` de antes** esperaban lotes `PENDIENTE` tras el cierre: cada una escribe por SQL una elección `MANUAL` para su turno, que es lo que hacía el cierre hasta hoy.
