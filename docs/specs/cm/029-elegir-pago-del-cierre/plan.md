# PLAN — `RF-CM-029` Elegir si el pago del próximo cierre es automático o manual

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-029` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 08-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

Hereda de [`RF-CM-028`](../028-consultar-proximo-cierre/plan.md) `ClosingSchedule`, `PaymentChoiceRepository`, `NextClosingResponse` y `V87`. Aquí solo se escribe.

---

## 1. Enfoque

**Una transacción que toma el bloqueo del turno, comprueba la ventana y reescribe la fila.**

```
validar el modo (VAL-001)
si el cierre programado está apagado → 409 (EX-001)
turno = ClosingSchedule.next(ahora)
pg_advisory_xact_lock(clave del turno)                      — el mismo que toma el cierre al abrirlo
si ahora < turno − 48 h, o ya hay cierre con scheduled_for = turno → 409 (EX-002)
upsert commission_payment_choices por scheduled_for; auditar
responder como RF-CM-028
```

**El bloqueo del turno es lo que hace verdad `FA-002`.** Sin él, una elección que se guarda en el mismo milisegundo en que el cierre abre su turno podría comprobar «no hay cierre» antes de que el cierre lo escriba, mientras el cierre lee «no hay elección» antes de que la elección se confirme: los dos seguirían, y la elección quedaría aceptada e ignorada. **Con un bloqueo consultivo de transacción sobre el turno**, tomado aquí y en `openScheduled` del cierre ([`RF-CM-009`](../009-cerrar-periodo-comisiones/plan.md) §16), uno espera al otro: si el cierre va primero, la comprobación de esta tripleta encuentra su fila y responde `409`; si va primero la elección, el cierre la lee confirmada (`CA-CM-389`). **La clave** es la del bloqueo del cierre combinada con el turno en segundos, para no chocar con el bloqueo de sesión que ya tiene el cierre.

**El turno se calcula dentro de la transacción y una sola vez.** Si el reloj cruza la medianoche entre el cálculo y el bloqueo, el turno calculado ya tiene su cierre escrito y la comprobación responde `409`, que es lo correcto.

---

## 2. Cambios de esquema

Ninguno propio: `V87` ([`RF-CM-028`](../028-consultar-proximo-cierre/plan.md) §2) crea la tabla y siembra este permiso.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `PaymentChoiceRepository` | Gana `upsert(scheduledFor, mode, chosenBy, chosenAt)` —`INSERT … ON CONFLICT (scheduled_for) DO UPDATE`— y `lockTurn(scheduledFor)` | El bloqueo |
| `domain/repository` | `CommissionClosingRepository` | Gana `existsScheduled(scheduledFor)` | |
| `domain/service` | `ChoosePaymentModeService` | Nuevo, `@Transactional` | §1 |
| `application` | `PaymentModeRequest` | Nuevo: `paymentMode`, `@NotNull` y del enumerado | `VAL-001` |
| `interfaces` | `CommissionClosingController` | Gana `PUT /next/payment-mode` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `PUT` | `/api/v1/commission-closings/next/payment-mode` | `commission-closings:set-payment-mode` |

```json
{ "paymentMode": "MANUAL" }
```

Responde `NextClosingResponse` ([`RF-CM-028`](../028-consultar-proximo-cierre/plan.md) §4).

| Código | Cuándo |
|---|---|
| `200` | Elegido |
| `409` | `EX-001`: el cierre programado está apagado. `EX-002`: la ventana está cerrada; el mensaje dice cuándo se abre la del próximo |
| `422` | `VAL-001` |
| `401` / `403` | Sin token / sin el permiso |

**`PUT` y no `POST`**: reemplaza el valor de un recurso que existe siempre —el modo de pago del próximo cierre— y repetirlo deja lo mismo.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('commission-closings:set-payment-mode')")`; en `PERMISO_DE_CADA_OPERACION`. **A `SUPERADMIN` y `ADMIN` explícitos** (`V87`), como pagar. **No reutiliza `commission-batches:pay-batches`** (`RN-SEG-014`): decidir que algo se pagará solo no es pagarlo.

---

## 6. Auditoría

**Un `ChangeEvent` por elección** sobre `commission_payment_choices`: `CREATE` la primera del turno y `UPDATE` las siguientes, con el turno, el modo anterior —o ninguno— y el nuevo (`CA-CM-391`). La persona la pone la auditoría.

---

## 7. Transaccionalidad

**Una transacción**, que retiene el bloqueo del turno lo que tarda una comprobación y un `upsert`.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Bloquear la fila de la elección con `FOR UPDATE` | No existe hasta la primera elección, y el cierre tendría que crearla para bloquearla |
| Leer la elección al pagar en vez de al abrir el turno | Entre abrir el turno y pagar corre el cierre entero; una elección que entrara entonces cambiaría un pago a medias |
| `DELETE` para volver a automático | Es la misma operación con otra ruta (`spec.md` §2.1) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Un interbloqueo** con el cierre | Los dos toman primero el bloqueo del turno y nada más hasta soltarlo; ninguno toma otro bloqueo antes |
| La suite apaga el cierre | Como `RF-CM-028`: la ventana se prueba con el servicio construido a mano y un reloj fijo |

---

## 11. Estrategia de prueba

**`ChoosePaymentModeIT`**: `CA-CM-386` a `CA-CM-388` y `CA-CM-391` con el servicio y un reloj fijo; `CA-CM-389` en dos partes —el turno con su cierre ya escrito responde `409`, y dos hilos con una barrera, elegir y `closeScheduled`, varias vueltas: si la elección se aceptó, el cierre la registra—; `CA-CM-390` y `CA-CM-392` por HTTP.
