# SPEC — `RF-MV-044` Confirmar un pago pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-044` |
| Módulo | `MV` — Movimientos |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien concilia diga **«este pago entró»** sobre **el pago**, sea cual sea su método y sea de lo que sea —una venta o una compra de puntos—, y que **el movimiento lo siga** en el mismo acto: la venta se confirma y entrega, y la compra de puntos se confirma y abona.

---

## 2. Contexto

Desde el 26-09-2026 lo que se concilia es el pago —cada intento de cobrar un movimiento (`RN-MV-039`)—, y la conciliación seguía entrando **por el movimiento**, con una operación por tipo: `RF-MV-003` para la venta y `RF-MV-028` para la compra de puntos. Quien mira el extracto ve **una transferencia**, y tenía que saber de qué era para elegir cuál usar.

Decisión del responsable del proyecto del 01-10-2026 ([`requirements/mv.md`](../../../requirements/mv.md) v0.67.0 §4.8): **«centralizarlo todo desde pagos»**. Este requerimiento es la entrada única para confirmar; `RF-MV-003` y `RF-MV-028` se quedan sin entrada propia y describen **lo que confirmar le hace** a cada tipo. Su espejo, el rechazo, es `RF-MV-045`.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Se nombra el pago** | La petición lleva el pago, no el movimiento. El movimiento se deduce del pago |
| **El efecto es el de cada tipo, sin cambios** | Una venta: lo de `RF-MV-003` —entregar y avisar a `CM`—. Una compra de puntos: lo de `RF-MV-028` —abonar los puntos congelados—. **Este requerimiento no redefine ninguno de los dos** |
| **Solo lo que cobra** | Venta y compra de puntos. El pago de un retiro **sale** de la plataforma y se resuelve al aprobar el retiro (`RF-MV-020`): aquí responde conflicto y dice dónde se hace |
| **Cualquier método** | Transferencia, `PSE`, tarjeta sin cobro abierto, `MANUAL`: el método no cambia la operación. **Lo único que la impide es un cobro abierto en la pasarela** (`RN-MV-058`), que lo resuelve su notificación |
| **La referencia, opcional** | Quien confirma puede anotar la referencia del extracto —el número de la transferencia—, como ya permitía `RF-MV-028`. Ahora vale también para la venta |
| **Responde el movimiento** | El detalle del movimiento (`RF-MV-007`), con el pago ya confirmado: **una sola forma para los dos tipos** |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien concilia, con `movements:confirm-payment` | Confirma un pago pendiente |
| La pasarela (`RF-MV-041`) | **No usa esta entrada**: su notificación recorre el mismo efecto por dentro, sin pasar por aquí |

---

## 4. Alcance

### 4.1 Incluye

- Confirmar el pago pendiente de una venta, con el efecto de `RF-MV-003`.
- Confirmar el pago pendiente de una compra de puntos, con el efecto de `RF-MV-028`.
- Anotar en el pago, si se da, la referencia del extracto.

### 4.2 No incluye

- **Rechazar**: es `RF-MV-045`.
- **Aprobar un retiro**: es `RF-MV-020`.
- **Confirmar un movimiento sin nombrar su pago**: esa entrada se retira (§4.8 del módulo).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| **`RN-MV-061`** | **Nace aquí.** Se concilia el pago y el movimiento lo sigue, en la misma transacción y según su tipo; si el movimiento no puede seguirlo, el pago no cambia |
| `RN-MV-039` | A lo sumo un pago pendiente y uno confirmado por movimiento; del pago no se vuelve |
| `RN-MV-058` | Un pago con cobro abierto en la pasarela no se confirma a mano |
| `RN-MV-004`, `RN-MV-005` | Solo lo confirmado produce efectos; de `CONFIRMADA` no se sale |
| `RN-MV-020`, `RN-MV-029`, `RN-MV-030`, `RN-MV-049` | Lo que confirmar una venta entrega y avisa, como en `RF-MV-003` |
| `RN-MV-051` | Los puntos que se abonan son los congelados al comprar, como en `RF-MV-028` |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| El pago | Sí | Cuál se confirma, por su identificador |
| Referencia | No | La del extracto o la transferencia, hasta 120 caracteres. Se guarda en el pago |

**Nada más.** Ni el importe —es el del pago o no se confirma—, ni la fecha —es ahora—, ni el método —ya está en el pago—.

### 6.2 Salida

**El movimiento, tal como queda**, con la forma de su detalle (`RF-MV-007`): estado, pagos —el confirmado, con su instante y su referencia—, y en una venta, sus líneas con el estado de entrega.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene el permiso; el pago existe, está pendiente, es de una venta o de una compra de puntos, y no tiene cobro abierto en la pasarela |
| Postcondición | El pago está **confirmado** y su movimiento **confirmado**, con el mismo instante; lo de cada tipo está hecho —la entrega y el aviso, o el abono—, y queda auditado |

---

## 8. Flujo principal

1. El actor indica el pago y, si quiere, la referencia.
2. El sistema lee el pago y su movimiento, y fija el movimiento para que nadie más lo resuelva a la vez.
3. Comprueba que el movimiento cobra, que el pago sigue pendiente y que no tiene cobro abierto.
4. Confirma el pago y el movimiento, y hace lo que corresponde a su tipo.
5. Devuelve el movimiento.

---

## 9. Flujos alternativos

### FA-001 — Es el pago de una venta

El efecto es el de `RF-MV-003`, completo: entrega por línea, retención si baja de nivel y aviso a `CM`. Sus flujos alternativos (`FA-001` a `FA-007`) aplican sin cambios.

### FA-002 — Es el pago de una compra de puntos

El efecto es el de `RF-MV-028`: se abonan los puntos congelados.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El pago no existe | No encontrado |
| `EX-002` | El pago es de un movimiento que **no cobra** —un retiro— | Conflicto, diciendo que se resuelve al aprobar el retiro. Nada cambia |
| `EX-003` | El pago **no está pendiente** —ya confirmado o rechazado— | Conflicto, diciendo en qué estado está. Nada cambia |
| `EX-004` | El pago tiene **cobro abierto** en la pasarela | Conflicto: lo resuelve la pasarela. Nada cambia |
| `EX-005` | Sin `movements:confirm-payment` | Prohibido |
| `EX-006` | Dos confirmaciones **simultáneas** del mismo pago, o una confirmación y un rechazo | Uno acierta; el otro recibe `EX-003`. Nunca se aplican los dos |
| `EX-007` | Lo de cada tipo **falla** por un error del sistema —conceder la membresía, escribir el abono— | Fallo del sistema; **ni el pago ni el movimiento cambian** |

**`EX-003` dice el estado** por lo mismo que `RF-MV-003`: quien reintenta tiene que poder saber que ya se hizo.

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El pago tiene forma de identificador |
| `VAL-002` | La referencia, si llega, no pasa de 120 caracteres. En blanco equivale a no darla |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-495` | Confirmar el pago pendiente de una **venta** deja el pago `CONFIRMADO` y la venta `CONFIRMADA` **con el mismo instante**, entrega sus líneas como `RF-MV-003`, y la respuesta es el detalle de la venta con el pago confirmado |
| `CA-MV-496` | Confirmar el pago pendiente de una **compra de puntos** deja el pago `CONFIRMADO` y la compra `CONFIRMADA`, y abona en la cuenta de puntos **los puntos congelados** |
| `CA-MV-497` | Funciona con **cualquier método sin cobro abierto**: una transferencia y una tarjeta que registró un funcionario sin cobro se confirman igual |
| `CA-MV-498` | La **referencia** dada queda en el pago confirmado, también en una venta; sin ella, el pago queda sin referencia; una de más de 120 caracteres responde `400` y nada cambia |
| `CA-MV-499` | Un pago que **no existe** responde no encontrado; un identificador **malformado**, `400` |
| `CA-MV-500` | Un pago **ya confirmado** o **rechazado** responde conflicto **diciendo su estado**, y nada cambia: ni la entrega ni el abono se repiten |
| `CA-MV-501` | El pago de un **retiro** responde conflicto, y nada cambia |
| `CA-MV-502` | Un pago con **cobro abierto** en la pasarela responde conflicto, y nada cambia |
| `CA-MV-503` | Dos confirmaciones **simultáneas** del mismo pago producen **un** pago confirmado, **una** entrega y **un** abono; la otra responde conflicto |
| `CA-MV-504` | Una confirmación y un rechazo **simultáneos** del mismo pago: **solo uno** se aplica, y el movimiento queda coherente con él |
| `CA-MV-505` | Sin `movements:confirm-payment` responde prohibido; sin autenticar, `401`. **Lo portan `SUPERADMIN` y `ADMIN`** |
| `CA-MV-506` | Queda **auditado** como en `RF-MV-003` y `RF-MV-028`: el movimiento antes y después, con el pago y su referencia |

**`CA-MV-503` y `CA-MV-504` son los que sostienen el requerimiento**: son la forma en que dos personas conciliando el mismo extracto no entregan dos veces.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El pago pendiente es de una **venta anulada** | No ocurre: anular cierra el pago pendiente (`RF-MV-005`). Si se pidiera sobre ese pago, ya está rechazado: `EX-003` |
| Un pago **anterior** de una venta, ya rechazado, cuando hay otro pendiente | `EX-003` sobre el rechazado. Se confirma el pendiente, nombrándolo |
| El pago es de una venta de **importe cero** (`GRATIS`) | Se confirma como cualquier otro |
| La pasarela **está apagada** y el pago con tarjeta no tiene cobro | Se confirma a mano: no hay cobro abierto |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión ([`requirements/mv.md`](../../../requirements/mv.md) v0.67.0 §4.8). **Se concilia el pago**: una entrada para la venta y la compra de puntos, con cualquier método; el efecto es el de `RF-MV-003` o `RF-MV-028`, sin cambios; referencia opcional también en la venta; responde el detalle del movimiento. Nace `RN-MV-061`. Criterios `CA-MV-495` a `CA-MV-506`. | Responsable del proyecto |
| 0.2.0 | 05-10-2026 | **Enmendada por la pasarela local** (`RF-MV-048`, [`requirements/mv.md`](../../../requirements/mv.md) v0.80.0 §4.10): un pago con **cobro abierto en la pasarela local** tampoco se confirma a mano (`RN-MV-058`); uno de `PSE` sin cobro, sí. Se prueba en `LocalChargeIT`. | Responsable del proyecto |
