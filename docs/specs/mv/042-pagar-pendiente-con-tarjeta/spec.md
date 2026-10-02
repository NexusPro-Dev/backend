# SPEC — `RF-MV-042` Pagar con tarjeta un pago pendiente propio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-042` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien tiene una compra **pendiente de pagar con tarjeta** pueda pagarla **desde su app** cuando quiera: porque cerró la app a mitad del pago, porque la tarjeta le fue rechazada y quiere probar otra, o porque la venta la registró otra persona y nadie había abierto todavía el cobro.

---

## 2. Contexto

`RF-MV-040` abre el cobro y devuelve su secreto **una vez**, en la respuesta de la compra. Si la app se cierra, ese secreto se pierde, y **el sistema no lo guarda**. Y la venta que registra un funcionario —o la del alta por enlace— nace pendiente con tarjeta y **sin cobro** (`RN-MV-057`), porque no había nadie al otro lado para escribir la tarjeta. Decisión del responsable del proyecto del 01-10-2026: **esas las paga después quien compró, desde su app** ([`requirements/mv.md`](../../../requirements/mv.md) v0.64.0 §4.6).

**Este requerimiento es la puerta para las dos cosas**: si el pago pendiente ya tiene cobro, devuelve **su** secreto —de nuevo, pedido a la pasarela—; si no lo tiene, **lo abre**, con las reglas de `RF-MV-040`.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo lo propio** | Quien pregunta es quien compró. Un movimiento ajeno responde como inexistente |
| **Solo un pago pendiente con tarjeta** | Si el pago pendiente es de otro método, no hay nada que pagar con tarjeta: para cambiar de método está volver a pagar (`RF-MV-018`). Si no hay pago pendiente, tampoco |
| **El mismo cobro, no otro** | Si ya hay cobro, se devuelve ese: abrir otro permitiría pagar dos veces lo mismo |
| **Ventas y compras de puntos** | Los dos movimientos que se pagan con tarjeta (§4.6) |
| **Con la pasarela apagada no hay nada que hacer** | Responde servicio no disponible: sin pasarela no hay cobro que retomar |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien compró, con `movements:pay-pending-by-card` | Paga con tarjeta su pago pendiente |

---

## 4. Alcance

### 4.1 Incluye

- Devolver el secreto del cobro abierto de un pago pendiente propio.
- Abrir el cobro de un pago pendiente con tarjeta que no lo tiene.

### 4.2 No incluye

- **Cambiar de método**: es `RF-MV-018`.
- **Confirmar**: lo hace la notificación de la pasarela (`RF-MV-041`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-057` | El cobro se abre por el importe y la moneda del pago, con su clave; los datos de la tarjeta no pasan por aquí |
| `RN-MV-039` | A lo sumo un pago pendiente por movimiento |
| `RN-MV-026` | Lo propio es lo que está a nombre del actor |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Movimiento | Sí | La venta o la compra de puntos propia |

### 6.2 Salida

**El cobro**: el pago, la pasarela y el secreto de cliente, con la forma de `RF-MV-040`.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene el permiso; el movimiento es suyo, es una venta o una compra de puntos, está pendiente y tiene un pago pendiente con tarjeta; la pasarela está encendida |
| Postcondición | El pago pendiente tiene un cobro abierto en la pasarela, y la respuesta trae su secreto. **Nada está confirmado** |

---

## 8. Flujo principal

1. El actor indica el movimiento.
2. El sistema lo lee: suyo, venta o compra de puntos, pendiente, con un pago pendiente con tarjeta.
3. Si el pago tiene cobro, pide a la pasarela su secreto. Si no, lo abre como `RF-MV-040` y guarda su referencia.
4. Devuelve el cobro.

---

## 9. Flujos alternativos

### FA-001 — El cobro ya estaba cancelado en la pasarela

El pago ya debería estar rechazado (`RF-MV-041`). Si la notificación todavía no llegó, se responde conflicto: no hay cobro que retomar, y volver a pagar abre otro.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El movimiento no existe, no es suyo, o no es una venta ni una compra de puntos | No encontrado |
| `EX-002` | No está pendiente, o no tiene pago pendiente, o su pago pendiente no es con tarjeta, o el cobro está cancelado | Conflicto, diciendo cuál |
| `EX-003` | La pasarela está apagada, no responde o rechaza abrir el cobro | Servicio no disponible |
| `EX-004` | El importe no alcanza el mínimo de la pasarela | Rechazo, como `RF-MV-040` |
| `EX-005` | Sin `movements:pay-pending-by-card` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El movimiento tiene forma de identificador |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-453` | Sobre una venta propia cuyo pago con tarjeta **ya tiene cobro**, devuelve **el mismo** cobro y su secreto, **sin abrir otro** |
| `CA-MV-454` | Sobre una venta **registrada por un funcionario** con tarjeta y sin cobro, **abre** el cobro, guarda su referencia y devuelve su secreto; pedirlo otra vez devuelve **el mismo** |
| `CA-MV-455` | Funciona igual sobre una **compra de puntos** pendiente con tarjeta |
| `CA-MV-456` | Un movimiento **ajeno**, inexistente, o que no es venta ni compra de puntos, responde no encontrado |
| `CA-MV-457` | Una venta **confirmada** o **anulada**, sin pago pendiente, con pago pendiente de **otro método**, o con el cobro **cancelado**, responde conflicto, y nada cambia |
| `CA-MV-458` | Con la pasarela **apagada** o **sin responder**, responde servicio no disponible, y nada cambia |
| `CA-MV-459` | **Nada se confirma**: la venta sigue pendiente |
| `CA-MV-460` | Sin `movements:pay-pending-by-card` responde prohibido; sin autenticar, `401` |
| `CA-MV-461` | El permiso llega **por tipo de rol**: un cliente lo porta |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Dos peticiones a la vez sobre una venta sin cobro | Abren **un** cobro: la clave del pago hace que la pasarela devuelva el mismo, y la referencia se escribe una vez |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, con la etapa 4 de `MV` para la tarjeta ([`requirements/mv.md`](../../../requirements/mv.md) v0.64.0 §4.6). Retoma el cobro abierto o lo abre si la venta la registró otro; solo lo propio; el mismo cobro, no otro. Criterios `CA-MV-453` a `CA-MV-461`. | Responsable del proyecto |
