# SPEC — `RF-CM-022` Retirar una comisión de un lote pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-022` |
| Módulo | `CM` — Comisiones |
| Versión | 0.4.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |
| Enmendada el | 07-10-2026 — **se retira `EX-005`**: ya no hay comisiones revertidas (`RN-CM-047`) |
| Enmendada el | 07-10-2026 — **el pendiente que se queda sin comisiones se borra** (`RN-CM-052`): `CA-CM-363` a `CA-CM-365` |
| Enmendada el | 08-10-2026 — **el pendiente vacío ya no se borra al retirar**: se borra a mano (`RN-CM-052` enmendada, `RF-CM-027`). Vuelve `CA-CM-279`; se retiran `CA-CM-363` a `CA-CM-365` |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que Finanzas, al revisar un lote cerrado antes de pagarlo, **saque de él lo que no quiere pagar todavía**, y que eso **no se pierda**: pasa al lote abierto de la misma persona y se paga en el cierre siguiente.

---

## 2. Contexto

**Es la mitad de «confirmar antes de pagar»** (`requirements/cm.md` v0.26.0 §5.10). El responsable del proyecto lo pidió así: «una vez hecho el cierre, entrar al detalle y decir cuál se confirma para pasar a pagar». **Se modela al revés de como se dice** —se retira lo que no se confirma, y lo que queda es lo confirmado— porque lo normal es pagarlo todo, y marcar una por una todo lo que sí se paga sería el trabajo de cada cierre.

**Hasta hoy un lote cerrado no cambiaba** (`RN-CM-029`). Desde el 30-09-2026 **cambia de contenido hasta que se paga**, y lo que sigue sin cambiar es **cada comisión**: sale con la tasa, la base y el importe con que nació.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo de un lote pendiente** | Un lote abierto aún no se ha cerrado —no hay nada que revisar— y uno pagado no cambia nunca |
| **A dónde va** | Al lote **abierto** de la misma persona y moneda; si no lo hay, **se abre** (`RN-CM-046`) |
| **Recuerda de dónde salió** | Es lo que permite devolverla si fue un error (`RF-CM-023`) |
| **Las dos clases** | Una comisión por venta y una afftrack se retiran igual: mover un escalón no cambia la liquidación que lo generó |
| **Una por petición** | Retirar varias son varias peticiones: cada una deja el lote en un estado que se puede leer |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Finanzas o administración | Retira la comisión (`commission-batches:withdraw-commission`) |

---

## 4. Alcance

### 4.1 Incluye

- Sacar una comisión viva de un lote pendiente y ponerla en el lote abierto de su persona y moneda, abriéndolo si hace falta.
- Ajustar el total de los dos lotes en el mismo acto.
- Dejar escrito de qué lote salió, y auditarlo.

### 4.2 No incluye

- **Devolverla**: es `RF-CM-023`.
- **Retirar de un lote pagado**, o **cambiar el importe** de una comisión (`RN-CM-029`).
- **Moverla a otra persona o a otra moneda**: la comisión es de quien es.
- **Retirar varias en una petición.**

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-046` | Qué se retira, a dónde va y qué recuerda |
| `RN-CM-029` | La comisión conserva su tasa, su base y su importe; nada se borra |
| `RN-CM-033` | El lote abierto se abre si no lo hay; la comisión conserva su instante de devengo |
| `RN-CM-030` | Un lote pagado no cambia |
| `RN-CM-048` | El pendiente puede quedarse sin comisiones, y entonces no se paga. Del 07 al 08-10-2026 solo valía para los vacíos de antes |
| `RN-CM-052` | ~~El pendiente que se queda sin comisiones se borra, y lo retirado de él pierde su origen~~ Desde el 08-10-2026 retirar no borra: el pendiente vacío lo borra [`RF-CM-027`](../027-borrar-lotes-vacios/spec.md)

---

## 6. Datos

### 6.1 Entrada

| Dato | Descripción |
|---|---|
| Lote | El lote pendiente, por su identificador |
| Comisión | La comisión que se retira, por su identificador |

### 6.2 Salida

**El lote pendiente como queda**, en la forma del detalle de `RF-CM-010`: con su total rebajado, sin la comisión retirada entre las suyas y **con ella entre las retiradas de este lote**, diciendo en qué lote está ahora. Es la vista en la que Finanzas sigue revisando.

~~**Si era la última y el pendiente se borró** (`FA-002`), no hay pendiente que devolver: la salida es **el lote abierto adonde fue**, en la misma forma. Que el identificador no sea el pedido es lo que dice que el pendiente ya no existe.~~ **Desde el 08-10-2026 el pendiente no se borra al retirar**, y la salida es siempre él.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El lote existe y está pendiente; la comisión es suya, y está viva |
| Postcondición | La comisión está en el lote abierto de su persona y moneda, con el lote de origen anotado; el total del pendiente bajó y el del abierto subió en su importe; queda auditado |

---

## 8. Flujo principal

1. Finanzas pide retirar una comisión de un lote.
2. Se comprueba que el lote está pendiente, que la comisión es suya y que está viva.
3. Se busca el lote abierto de la misma persona y moneda; si no lo hay, se abre.
4. La comisión pasa a él, con el lote de origen anotado.
5. Se ajustan los dos totales.
6. Se audita y se devuelve el lote pendiente.

---

## 9. Flujos alternativos

### FA-001 — La persona no tiene lote abierto

Se abre uno, como al devengar. **Su periodo empieza ahora**, aunque la comisión naciera antes: la comisión conserva su instante de devengo.

### FA-002 — Se retiran todas

~~El pendiente se queda sin comisiones vivas. **Sigue pendiente, y no se puede pagar** (`RN-CM-048`) hasta que se le devuelva alguna.~~

**Desde el 08-10-2026, otra vez**: el pendiente se queda sin comisiones, con total cero, **sigue pendiente y no se puede pagar** (`RN-CM-048`) hasta que se le devuelva alguna o se borre a mano ([`RF-CM-027`](../027-borrar-lotes-vacios/spec.md)); si se borra, lo retirado de él pierde su origen.

~~**Del 07 al 08-10-2026 el pendiente se borraba** al retirar la última (`RN-CM-052`), en el mismo acto, y quedaba auditado. Todo lo que se retiró de él —esta y las anteriores— **pierde su origen**: está en el abierto como cualquier otra comisión y ya no se puede devolver. La salida es el abierto (§6.2).~~

### FA-003 — Retirar y pagar el mismo lote a la vez

Uno espera al otro. Si el pago llega antes, el retiro encuentra el lote pagado y **no mueve nada**; si el retiro llega antes, el pago abona **el total rebajado**.

### FA-004 — Retirar mientras corre un cierre

Si el cierre cierra el lote abierto de esa persona mientras se retira, la comisión acaba **en el lote cerrado o en uno nuevo**, nunca en ninguno ni en los dos.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El lote no existe | No encontrado |
| `EX-002` | La comisión no existe, o **no es de este lote** | No encontrado |
| `EX-003` | El lote está **abierto** | Conflicto: «El lote sigue abierto: aún no hay nada que revisar» |
| `EX-004` | El lote está **pagado** | Conflicto: «El lote ya está pagado» |
| ~~`EX-005`~~ | ~~La comisión está **revertida**~~ | **Retirada el 07-10-2026**: la comisión de una línea cuyo vendedor se corrige se borra, y ya no puede llegar aquí (`RN-CM-047`) |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Los dos identificadores bien formados |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-273` | Retirar una comisión de un lote **pendiente** la pone en el lote **abierto** de su persona y moneda, con su importe, su tasa y su instante de devengo **intactos** y el lote de origen anotado; el total del pendiente **baja** y el del abierto **sube** en su importe |
| `CA-CM-274` | Si la persona **no tiene lote abierto** en esa moneda, se abre uno con la comisión dentro |
| `CA-CM-275` | Una comisión **afftrack** se retira igual, y su liquidación **no cambia** |
| `CA-CM-276` | La respuesta es el pendiente como queda, **con la retirada entre las retiradas de este lote** y el lote en que está ahora |
| `CA-CM-277` | Un lote **abierto** o **pagado** responde conflicto, y nada se mueve |
| `CA-CM-278` | Una comisión de **otro lote**, o que no existe, responde no encontrado; una **revertida**, conflicto |
| `CA-CM-279` | Retirar **todas** deja el pendiente sin comisiones, con total cero, y **pagarlo responde conflicto**. Enmendado por `CA-CM-363` el 07-10-2026 y **vuelve a valer el 08-10-2026** |
| `CA-CM-280` | **Retirar y pagar** el mismo lote a la vez: o se paga el total rebajado con la comisión ya fuera, o el retiro responde conflicto con el lote pagado; **nunca** se abona una comisión que también está en el abierto |
| `CA-CM-281` | Sin `commission-batches:withdraw-commission`, se rechaza; queda **auditado**, con la comisión, los dos lotes y el importe |
| ~~`CA-CM-363`~~ | ~~Retirar la **última** comisión de un pendiente **lo borra**: ya no aparece en el listado ni en su detalle (no encontrado), pagarlo responde **no encontrado**, y la respuesta es **el detalle del abierto** con la comisión dentro (07-10-2026). Enmienda `CA-CM-279`~~ **Retirado el 08-10-2026**: retirar ya no borra |
| ~~`CA-CM-364`~~ | ~~Las comisiones retiradas **antes** de ese pendiente, y la última, quedan en el abierto **sin origen**: ninguna se lista como devolvible, y devolverlas responde no encontrado~~ **Retirado el 08-10-2026**: lo prueba `RF-CM-027` `CA-CM-372`, tras el borrado a mano |
| ~~`CA-CM-365`~~ | ~~El borrado queda **auditado** como eliminación física, con el código, la persona, la moneda, el estado y el periodo del lote; un pendiente al que le queda **alguna** comisión no se borra~~ **Retirado el 08-10-2026**: lo prueba `RF-CM-027` `CA-CM-371` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La persona del lote está eliminada | Se retira igual: la comisión es suya |
| La comisión es de importe cero | Se retira; los totales no cambian |
| Se retira, se devuelve y se retira otra vez | Cada retiro es uno más; el lote de origen anotado es siempre el último |
| Un pendiente vaciado por retiros | Sigue pendiente y no se paga (`RN-CM-048`), hasta que se le devuelva algo o se borre a mano ([`RF-CM-027`](../027-borrar-lotes-vacios/spec.md)) |
| El abierto al que fue se cierra antes de devolverla | Queda en ese nuevo pendiente, que puede revisarse y retirarse a su vez; del primero ya no vuelve (`RF-CM-023`) |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.26.0 §5.10, `RN-CM-046`), por decisión del responsable del proyecto: lo que no se confirma de un lote pendiente **se retira** al abierto de su persona y se paga en el cierre siguiente. Criterios `CA-CM-273` a `CA-CM-281`. | Responsable del proyecto |
| 0.3.0 | 07-10-2026 | **El pendiente que se queda sin comisiones se borra** ([`requirements/cm.md`](../../../requirements/cm.md) v0.40.0, `RN-CM-052`), por decisión del responsable del proyecto: «si un lote se queda sin comisiones, que se elimine». Lo retirado de él pierde su origen; la salida pasa a ser el abierto (§6.2). `FA-002` cambia; `CA-CM-363` enmienda `CA-CM-279`, y nacen `CA-CM-364` y `CA-CM-365`. | Responsable del proyecto |
| 0.2.0 | 07-10-2026 | **Se retira `EX-005`** ([`requirements/cm.md`](../../../requirements/cm.md) v0.34.0, `RN-CM-047` enmendada): desde que la cadena vieja de una línea reatribuida se borra, no hay comisión revertida que rechazar. **`CA-CM-278` se lee** sin su última mitad: una comisión de otro lote, o que no existe, responde no encontrado. | Responsable del proyecto |
| 0.4.0 | 08-10-2026 | **Retirar ya no borra el pendiente que vacía** ([`requirements/cm.md`](../../../requirements/cm.md) v0.41.0, `RN-CM-052` enmendada), por decisión del responsable del proyecto: los lotes vacíos se borran a mano, todos a la vez (`RF-CM-027`). `FA-002` y §6.2 vuelven a lo de antes del 07-10-2026; vuelve `CA-CM-279`, y se retiran `CA-CM-363` a `CA-CM-365`. | Responsable del proyecto |
