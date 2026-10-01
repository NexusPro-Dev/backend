# SPEC — `RF-MV-028` Confirmar el pago de una compra de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-028` |
| Módulo | `MV` — Movimientos |
| Versión | 0.3.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |
| Enmendada el | 01-10-2026 — **sin ruta propia**: se concilia el pago, por `RF-MV-044`. Ver §14.3 · Antes, 01-10-2026 — **una compra con cobro abierto en la pasarela no se confirma a mano** (`RN-MV-058`). Ver §14.2 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que administración declare que **el dinero de una compra de puntos entró**, y que en ese mismo acto **los puntos aparezcan** en la cuenta de quien los compró.

---

## 2. Contexto

**Es el único camino que llena una cuenta de puntos** ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). Mientras no haya pasarela lo hace una persona, como confirmar una venta (`RF-MV-003`) o aprobar un retiro (`RF-MV-020`), y **tiene operación y permiso propios** porque confirmar una venta entrega lo vendido y avisa a comisiones, y aquí no hay nada que entregar ni que comisionar.

**Se abonan los puntos congelados al comprar**, no los que daría la tasa de hoy (`RN-MV-051`): quien compró pagó a esa tasa.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **La referencia del cobro es opcional** | Quien confirma puede anotar la del comprobante bancario, como al aprobar un retiro |
| **Se resuelve una sola vez** | Confirmar lo ya confirmado o lo rechazado es un conflicto, y no abona nada |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:confirm-points-purchase` | Confirma el pago de cualquier compra de puntos pendiente |

---

## 4. Alcance

### 4.1 Incluye

- Confirmar el pago pendiente y la compra, en un solo acto.
- Abonar los puntos desde la cuenta de puntos emitidos de la empresa.

### 4.2 No incluye

- **Rechazar**: es `RF-MV-029`.
- **Confirmar una venta**: es `RF-MV-003`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-051` | Abona exactamente los puntos congelados |
| `RN-MV-042` | Dos asientos de `ABONO` que suman cero, con el pago |
| `RN-MV-039` | El pago pasa a confirmado, y con él el movimiento |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Compra | Sí | Cuál |
| Referencia | No | La del cobro, acotada |

### 6.2 Salida

**La compra confirmada**, con su pago.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:confirm-points-purchase`; la compra existe y está pendiente |
| Postcondición | La compra y su pago están confirmados; la cuenta de puntos de quien compró subió en los puntos congelados y la de puntos emitidos de la empresa bajó en lo mismo; está auditado |

---

## 8. Flujo principal

1. El actor indica la compra y, si quiere, la referencia.
2. El sistema la pasa de pendiente a confirmada —solo si seguía pendiente—, confirma su pago y abona los puntos, **en un solo acto**.
3. Audita y devuelve la compra.

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | No existe, o no es una compra de puntos | No encontrado |
| `EX-002` | No está pendiente | Conflicto, diciendo en qué estado está. Nada cambia |
| `EX-003` | Referencia demasiado larga | Rechazo |
| `EX-004` | Quien pide no tiene `movements:confirm-points-purchase` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Referencia, si viene, dentro de la longitud máxima |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-318` | Una compra pendiente pasa a **confirmada**, y su pago también, con la referencia indicada |
| `CA-MV-319` | **La cuenta de puntos** de quien compró sube en **los puntos congelados** y la de **puntos emitidos** de la empresa baja en lo mismo; los dos asientos son de `ABONO`, suman cero y llevan el pago |
| `CA-MV-320` | Si la tasa **cambió** después de comprar, se abonan **los de la compra**, no los de la tasa nueva |
| `CA-MV-321` | Sin referencia se confirma igual |
| `CA-MV-322` | Confirmar **por segunda vez**, o una compra **rechazada**: conflicto, y **no se abona nada** |
| `CA-MV-323` | **Confirmar y rechazar a la vez** abonan **una sola vez o ninguna** |
| `CA-MV-324` | El identificador de una **venta** o de un **retiro** responde no encontrado |
| `CA-MV-325` | Sin `movements:confirm-points-purchase` responde prohibido; sin autenticar, `401`. El cambio queda auditado, con quién confirmó |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Quien compró no tiene cuenta de puntos en esa moneda | Se crea al abonar |
| La empresa no tiene cuenta de puntos emitidos en esa moneda | Se crea, y queda en negativo: es una contrapartida |
| La cuenta de quien compró se bloqueó después de comprar | Se confirma igual: el dinero entró, y los puntos son suyos |

---

## 14. Preguntas abiertas

Ninguna.

---

## 14.2 La tarjeta por Stripe — enmienda del 01-10-2026

Desde el 01-10-2026 ([`requirements/mv.md`](../../../requirements/mv.md) v0.64.0 §4.6) confirmar a mano una compra de puntos **cuyo pago tiene cobro abierto** responde **conflicto**: la confirma la notificación de la pasarela, que **abona los puntos** por el mismo camino (`RF-MV-041`). Sin cobro, se confirma a mano como siempre.

| ID | Situación | Resultado |
|---|---|---|
| `EX-005` | El pago tiene cobro abierto en la pasarela | Conflicto. Nada cambia |

| ID | Criterio |
|---|---|
| `CA-MV-478` | Confirmar a mano una compra con **cobro abierto** responde conflicto y **no abona** nada; sin cobro, se confirma como siempre |

---

## 14.3 Sin ruta propia — enmienda del 01-10-2026

Desde el 01-10-2026 ([`requirements/mv.md`](../../../requirements/mv.md) v0.67.0 §4.8) **se concilia el pago, no el movimiento**: se entra por `RF-MV-044`, que nombra el pago, y la ruta de este requerimiento —`POST /movements/{id}/points-purchase-confirmation`— **se retira sin alias**. **Lo que este documento describe no cambia** —la transición, el pago confirmado con su referencia y el abono de los puntos congelados—: es lo que `RF-MV-044` hace cuando el pago es de este tipo, y lo que la notificación de la pasarela (`RF-MV-041`) hace por dentro.

**Lo que cambia, por eso.** `EX-001`, `EX-002`, `EX-004` y `EX-005` los responde `RF-MV-044` sobre el pago nombrado; `EX-003`, la referencia, es su `VAL-002`. `movements:confirm-points-purchase` se retira y lo sustituye `movements:confirm-payment`. **Los criterios de este documento se siguen probando por la ruta nueva**, salvo los que la entrada sustituye: `CA-MV-322` lo sustituye `CA-MV-500`; `CA-MV-323`, `CA-MV-504`; `CA-MV-324`, `CA-MV-501` —el pago de un retiro es conflicto y no «no encontrado», y el de una venta **se confirma**, porque desde hoy la entrada es la misma—; `CA-MV-325`, `CA-MV-505` y `CA-MV-506`; `CA-MV-478`, `CA-MV-502`.

| ID | Criterio |
|---|---|
| `CA-MV-518` | `POST /movements/{id}/points-purchase-confirmation` responde `404`: la ruta no existe |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión, con la etapa 3 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). **Operación propia**, no la confirmación de la venta; abona los puntos congelados, con referencia opcional. Criterios `CA-MV-318` a `CA-MV-325`. | Responsable del proyecto |
| 0.2.0 | 01-10-2026 | **La tarjeta por Stripe** ([`requirements/mv.md`](../../../requirements/mv.md) v0.64.0 §4.6): **una compra con cobro abierto en la pasarela no se confirma a mano** (`RN-MV-058`). Criterios `CA-MV-478`. | Responsable del proyecto |
| 0.3.0 | 01-10-2026 | **Sin ruta propia** ([`requirements/mv.md`](../../../requirements/mv.md) v0.67.0 §4.8): se concilia el pago, y se entra por `RF-MV-044`. El efecto no cambia; la ruta y su permiso se retiran (§14.3). Criterio `CA-MV-518`. | Responsable del proyecto |
