# SPEC — `RF-MV-040` Cobrar con tarjeta por la pasarela

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-040` |
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

Que quien compra con **tarjeta de crédito** pague **en el acto, dentro de la app**, y que el dinero entre por la pasarela de pago **sin que los datos de la tarjeta pasen por este sistema**.

---

## 2. Contexto

Lo pidió el responsable del proyecto el 01-10-2026 ([`requirements/mv.md`](../../../requirements/mv.md) v0.64.0 §4.6): «al pagar por tarjeta de crédito use Stripe, pero que los datos de la tarjeta se pidan en la app». Hasta ese día **pagar con tarjeta era declararlo**: la venta nacía pendiente y una persona confirmaba a mano que el dinero había entrado.

**Este requerimiento no tiene ruta propia**, como pagar con puntos (`RF-MV-030`): es lo que ocurre **dentro** de las entradas que registran un pago con tarjeta cuando quien paga está al otro lado. Abre **el cobro** en la pasarela y devuelve a la app lo que necesita para pedir la tarjeta y confirmarlo **ante la pasarela**. **No confirma nada**: eso lo hace la notificación de la pasarela (`RF-MV-041`).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Dónde se abre el cobro** | En las compras propias —producto y paquete, para uno mismo y por hotlink—, al volver a pagar y al comprar puntos. **No** en la venta que registra un funcionario ni en la del alta por enlace: nacen pendientes sin cobro (`RN-MV-057`), y las paga después quien compró (`RF-MV-042`) |
| **Por cuánto y en qué moneda** | El importe del pago, en la moneda del movimiento, sin conversión |
| **Sin cobro no hay compra** | Si la pasarela no responde o rechaza abrir el cobro, **no se registra nada**: ni el movimiento ni el pago. Una venta pendiente con tarjeta y sin cobro sería una venta que nadie puede pagar |
| **La misma petición no abre dos cobros** | El cobro se abre con la clave del pago (`RN-MV-040`): repetirla devuelve el mismo |
| **Un importe por debajo del mínimo de la pasarela no se cobra con tarjeta** | La pasarela no cobra menos de un mínimo por moneda —50 centavos en USD—. Se rechaza antes de registrar y el error lo dice |
| **Con la pasarela apagada, la tarjeta vuelve a ser lo de antes** | En un entorno sin sus credenciales, el pago con tarjeta nace pendiente **sin cobro** y lo confirma una persona. Es como corren las pruebas y el entorno local sin claves |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien compra, en una entrada en que también paga | Recibe el cobro abierto y lo completa en la app |
| La pasarela de pago | Abre el cobro y, después, lo resuelve (`RF-MV-041`) |

---

## 4. Alcance

### 4.1 Incluye

- Abrir el cobro al registrar un pago con tarjeta en las entradas de §2.1.
- Anotar en el pago la referencia del cobro.
- Devolver a la app el secreto del cobro.

### 4.2 No incluye

- **Confirmar o rechazar el pago**: es `RF-MV-041`.
- **Retomar un cobro, o empezarlo en una venta que registró otro**: es `RF-MV-042`.
- **Guardar tarjetas**, **cobrar en otra moneda** y **cobrar otro método** por la pasarela.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-057` | Los datos de la tarjeta no tocan el sistema; el cobro se abre por el importe y la moneda del pago, con su clave; sin cobro no queda nada |
| `RN-MV-039` | El pago nace pendiente, y a lo sumo uno pendiente por movimiento |
| `RN-MV-040` | La clave de idempotencia del pago y la referencia de quien cobra |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

Los de cada entrada de compra, con el método de tarjeta. **Ningún dato de la tarjeta.**

### 6.2 Salida

Lo que cada entrada ya devolvía y, además, **el cobro**: el pago al que pertenece, la pasarela y **el secreto de cliente** con el que la app pide la tarjeta. Vacío cuando no se abrió cobro —otro método, o la pasarela apagada—.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | La entrada admite el pago; el método es la tarjeta; el importe alcanza el mínimo de la pasarela; la pasarela responde |
| Postcondición | El movimiento y su pago pendiente existen, el pago lleva la referencia del cobro, y el cobro existe en la pasarela por el mismo importe y moneda. **Nada está confirmado** |

---

## 8. Flujo principal

1. La entrada valida su petición como siempre, **y además** que el importe alcanza el mínimo de la pasarela.
2. Registra el movimiento y su pago pendiente, como siempre.
3. Abre el cobro en la pasarela, por el importe y la moneda del pago, con su clave, y con el pago y el movimiento anotados en él.
4. Guarda en el pago la referencia del cobro.
5. Responde lo de siempre y el cobro, con su secreto.

**Los pasos 2 a 4 son un solo acto**: si el cobro no se abre, el movimiento y el pago tampoco quedan.

---

## 9. Flujos alternativos

### FA-001 — La pasarela está apagada

No se abre cobro. El pago queda pendiente sin referencia, como antes del 01-10-2026, y la respuesta no trae cobro.

### FA-002 — La misma petición repetida

La entrada responde lo que respondía al repetirse. Si devuelve el pago ya existente, devuelve también **el mismo cobro**.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El importe no alcanza el mínimo de la pasarela para su moneda | Rechazo, sin escribir nada, diciendo el mínimo |
| `EX-002` | La pasarela no responde, o rechaza abrir el cobro | Servicio no disponible, sin escribir nada. Se puede reintentar |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Con tarjeta y la pasarela encendida, el importe es al menos el mínimo de la pasarela para la moneda |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-426` | Una compra **por hotlink** con tarjeta registra la venta **pendiente**, abre **un cobro** en la pasarela por su importe y su moneda, guarda su referencia en el pago y **devuelve el secreto** del cobro |
| `CA-MV-427` | Lo mismo comprando **un paquete**. Las compras propias que aún no tienen ruta —un producto para uno mismo, un paquete por hotlink— lo heredarán al construirse |
| `CA-MV-428` | **Volver a pagar** con tarjeta abre el cobro del pago nuevo y devuelve su secreto |
| `CA-MV-429` | **Comprar puntos** con tarjeta abre el cobro por el importe de la compra |
| `CA-MV-430` | El cobro lleva **la clave de idempotencia del pago**, y anotados el pago y el movimiento |
| `CA-MV-431` | **Nada queda confirmado**: la venta sigue pendiente, sin entrega y sin aviso a comisiones |
| `CA-MV-432` | Si la pasarela **falla al abrir el cobro**, la respuesta es servicio no disponible y **no queda ni movimiento ni pago** |
| `CA-MV-433` | Un importe por debajo del **mínimo** de la pasarela se rechaza **sin escribir nada**, y el error dice el mínimo |
| `CA-MV-434` | La venta que registra **un funcionario** y la del **alta por enlace** con tarjeta nacen pendientes **sin cobro** y sin secreto |
| `CA-MV-435` | Con **la pasarela apagada**, la compra con tarjeta nace pendiente sin cobro y sin secreto, y se puede confirmar a mano |
| `CA-MV-436` | Con **otro método** —`PSE`, puntos— no se abre ningún cobro |
| `CA-MV-437` | **Ninguna ruta acepta datos de tarjeta**: el contrato no declara número, fecha ni código, y un cuerpo que los traiga se rechaza por propiedad desconocida |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La pasarela abre el cobro y la respuesta se pierde antes de guardar | La transacción se deshace y el cobro queda huérfano en la pasarela, **sin pago que lo respalde**. No se puede completar desde la app —nadie tiene su secreto— y si alguien lo cobrara, la notificación no encontraría pago y quedaría con su error (`RF-MV-041`) |
| Dos compras simultáneas con la misma clave | Una registra y abre el cobro; la otra responde como cada entrada responde a una clave repetida |

---

## 14. Preguntas abiertas

**Monedas sin decimales.** Hoy todo es USD. Una moneda que la pasarela trate sin decimales se convertirá con los decimales que declare el catálogo de monedas; si no coinciden, será una regla de este requerimiento.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, con la etapa 4 de `MV` para la tarjeta ([`requirements/mv.md`](../../../requirements/mv.md) v0.64.0 §4.6). **Sin cobro no hay compra**; el cobro no confirma nada; mínimo de la pasarela; apagable. Criterios `CA-MV-426` a `CA-MV-437`. | Responsable del proyecto |
