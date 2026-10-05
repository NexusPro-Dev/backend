# SPEC — `RF-MV-049` Recibir los avisos de la pasarela local

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-049` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que cuando la pasarela local avise de que un cobro terminó, **el sistema se entere de verdad** —preguntándole a la pasarela— y confirme o rechace el pago, con todo lo que eso arrastra.

---

## 2. Contexto

([`requirements/mv.md`](../../../requirements/mv.md) v0.78.0 §4.10, `RN-MV-064`.) PayRetailers avisa cuando un cobro llega a un estado final, pero **no firma sus avisos ni los reintenta**. Un aviso puede venir de cualquiera, y uno que se pierde no vuelve. Por eso **el aviso no se cree**: solo dice «pregunta por este cobro». **La respuesta de la pasarela a nuestra pregunta** es lo que manda. El mismo efecto lo produce el barrido de `RF-MV-050`, que hace la pregunta sin esperar al aviso.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Cada aviso se guarda tal como llegó** | Antes de hacer nada, como los de la tarjeta (`RN-MV-059`). Lo que llegó se puede auditar aunque no se haya creído |
| **Se responde enseguida** | La pregunta a la pasarela y lo que siga se hacen después de responder: la pasarela no espera |
| **Aprobado confirma** | El pago y su compra, por el camino de siempre (`RN-MV-061`): entrega y comisiones en una venta, puntos en una compra de puntos |
| **Fallido, rechazado, cancelado o caducado rechazan** | El pago queda rechazado y **ese cobro no se reintenta**: se vuelve a pagar con otro |
| **Pendiente no hace nada** | El barrido volverá a preguntar |
| **Un aviso de un cobro que no es de ningún pago se ignora** | Sin preguntar: la ruta no debe servir para hacer que el sistema consulte a la pasarela por cobros ajenos |
| **Un cobro aprobado sobre un pago que ya no lo espera** | No confirma nada: se marca la incidencia **cobro tardío** (`RN-MV-064`) para devolver el dinero a mano |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| La pasarela local | Envía el aviso, y responde a la consulta |

---

## 4. Alcance

### 4.1 Incluye

- Recibir el aviso, guardarlo y responder.
- Preguntar a la pasarela por el cobro y aplicar lo que diga.
- **Anular una venta o volver a pagar con un cobro local abierto**: el pago se rechaza aquí y ahora, sin esperar a la pasarela, y si el cobro se aprueba después es un cobro tardío.

### 4.2 No incluye

- **El barrido**: `RF-MV-050`.
- **Devolver un cobro tardío**: se hace a mano, fuera del sistema.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-064` | El aviso dispara la consulta; manda la respuesta |
| `RN-MV-059` | El aviso se guarda antes de interpretarse |
| `RN-MV-061` | Confirmar o rechazar el pago arrastra su movimiento |
| `RN-MV-060` | La incidencia del cobro tardío |

---

## 6. Datos

### 6.1 Entrada

El aviso de la pasarela, tal como lo envía. De él solo se usa **el identificador del cobro**.

### 6.2 Salida

Ninguna para la pasarela, más allá de recibido. **El efecto** es el pago confirmado, rechazado, sin cambios o con incidencia.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | La pasarela local está configurada |
| Postcondición | El aviso queda guardado con su desenlace; el pago, en el estado que dijo la pasarela |

---

## 8. Flujo principal

1. Llega un aviso. El sistema lo guarda y responde.
2. Busca el pago del cobro. Si no hay, el aviso queda ignorado (§2.1).
3. Pregunta a la pasarela por ese cobro.
4. Aprobado: confirma el pago y su compra. Fallido, rechazado, cancelado o caducado: lo rechaza. Pendiente: nada.

---

## 9. Flujos alternativos

### FA-001 — El pago ya estaba confirmado

El aviso queda ignorado: la pasarela puede avisar dos veces, y el barrido puede haberse adelantado.

### FA-002 — Cobro aprobado sobre un pago que ya no está pendiente

El pago se rechazó porque se anuló la venta o se volvió a pagar con otro método. No se confirma nada: **se marca la incidencia cobro tardío** en ese pago.

### FA-003 — Anular o volver a pagar con un cobro local abierto

La pasarela local no deja cancelar un cobro. **Se rechaza el pago en el acto**, con un motivo que lo dice, y la anulación o el nuevo pago siguen. Si el cliente paga después en la página de la pasarela, es `FA-002`.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La pasarela local está apagada | No disponible, sin guardar nada |
| `EX-002` | La pasarela no responde a la consulta | El aviso queda pendiente de reintento; el barrido también lo recogerá |
| `EX-003` | El aviso no se puede leer | Rechazo, sin guardar nada |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El aviso es un JSON con identificador de cobro |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-612` | Un aviso de un cobro que la pasarela da por **aprobado** confirma el pago y la venta —con su entrega y el aviso a `CM`—; en una compra de puntos, abona los puntos |
| `CA-MV-613` | Si la pasarela dice **fallido, rechazado, cancelado o caducado**, el pago queda rechazado con un motivo que lo dice; la venta sigue pendiente y se puede volver a pagar |
| `CA-MV-614` | **El aviso no se cree**: un aviso que dice aprobado de un cobro que la pasarela da por **pendiente** no cambia nada |
| `CA-MV-615` | Un aviso de un cobro que **no es de ningún pago** se guarda como ignorado y **no provoca ninguna consulta** a la pasarela |
| `CA-MV-616` | El mismo aviso dos veces, o un aviso tras el barrido, no confirma dos veces |
| `CA-MV-617` | **Anular** una venta, o **volver a pagar con otro método**, con un cobro local abierto rechaza ese pago en el acto; si después la pasarela lo da por aprobado, el pago queda con la incidencia **cobro tardío** y la venta no cambia |
| `CA-MV-618` | Apagada la pasarela, la ruta responde no disponible y no guarda nada; un aviso ilegible se rechaza sin guardar nada |
| `CA-MV-619` | La ruta **no exige token**, y queda en la lista de rutas públicas |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El aviso llega antes de que se haya guardado el cobro en el pago | No hay pago con ese cobro: se ignora, y el barrido lo encuentra después |
| La pasarela da por aprobado un importe distinto del cobrado | Error en el aviso, para revisarlo a mano; el pago no se confirma |

---

## 14. Preguntas abiertas

**Si PayRetailers publica una firma o sus direcciones IP**, se añade como verificación previa; la consulta se queda.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 05-10-2026 | Primera versión, con la pasarela local ([`requirements/mv.md`](../../../requirements/mv.md) v0.78.0 §4.10, `RN-MV-064`). **El aviso no se cree: se pregunta a la pasarela**; aprobado confirma, los finales rechazan, pendiente espera; el cobro tardío se marca. Criterios `CA-MV-612` a `CA-MV-619`. | Responsable del proyecto |
