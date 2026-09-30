# SPEC — `RF-MV-030` Pagar una compra con puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-030` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien tiene puntos pueda **comprar en la tienda con ellos**, y que la compra quede **pagada y entregada en el acto**, sin esperar a que nadie confirme nada.

---

## 2. Contexto

**Es la otra mitad de la etapa 3** ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4), y **cierra una deuda de 04-09-2026**: el método «pagar con puntos» se ofrece desde entonces y pagar con él no descontaba nada (§7.4 de ese documento). Una venta con ese método quedaba pendiente como cualquier otra, y confirmarla entregaba lo comprado **gratis**.

**No es una operación nueva, sino un método dentro de las que ya existen.** Quien compra un producto o un paquete para sí mismo, o vuelve a pagar una venta suya pendiente, elige «pagar con puntos» como elegiría una tarjeta. Lo que cambia es lo que ocurre después: el dinero no viene de fuera —el saldo ya está en la plataforma—, de modo que **se descuenta y se confirma en ese mismo acto**, con la entrega y el aviso a comisiones de siempre (`RN-MV-052`). **Por eso comisiona**: es una venta, y la comisión que la compra de los puntos no generó se genera aquí, una sola vez.

**Rige la tasa del día**, no la de la compra de los puntos: con un solo saldo no hay forma de saber a qué tasa se compró cada punto, y el precio en puntos de la tienda tiene que ser uno.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Redondeo hacia arriba** | `importe × tasa`, a dos decimales, hacia arriba (`RN-MV-052`) |
| **Los puntos de la moneda de la venta** | Con puntos comprados en otra moneda no se paga |
| **Solo quien compra para sí mismo** | Un funcionario que registra la venta de otra persona no puede gastar sus puntos, y la venta que nace con el alta por enlace tampoco |
| **Si no alcanzan, nada** | Ni la venta ni el pago quedan escritos |
| **Las ventas ya registradas con este método** | Decisión del responsable del proyecto, 30-09-2026: **su pago pendiente se rechaza**, con un motivo que lo explica. La venta sigue pendiente y quien compró la vuelve a pagar —con otro método o con puntos de verdad—. No se pierde ninguna venta |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien compra para sí mismo | Paga con sus puntos, con el permiso de la entrada que use |
| Quien registra una venta a nombre de otro | **No** puede elegir este método |

---

## 4. Alcance

### 4.1 Incluye

- Pagar con puntos al comprar para uno mismo —un producto por el enlace de un vendedor, un paquete por la tienda o por el enlace— y al volver a pagar una venta propia pendiente. **Y la compra de un producto por la tienda** cuando se construya.
- Descontar los puntos, confirmar el pago y confirmar la venta, con sus efectos.
- Rechazar el método en las dos entradas en que la venta la origina otro.
- Cerrar los pagos pendientes con este método que ya existían.

### 4.2 No incluye

- **Pagar una parte con puntos y otra con otro método.**
- **Comprar puntos con puntos** (`RF-MV-027`).
- **Devolver los puntos** de una venta pagada con ellos: una venta confirmada no se deshace (`RN-MV-005`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-052` | Descuenta a la tasa vigente, redondeando hacia arriba; confirma en el acto; solo quien pide y compra a la vez |
| `RN-MV-050` | Sin tasa vigente en la moneda de la venta, no se paga con puntos |
| `RN-MV-042` | Dos asientos de `PAGO` que suman cero, con el pago |
| `RN-MV-043` | La cuenta se bloquea mientras se descuenta: dos compras simultáneas no gastan el mismo saldo |
| `RN-MV-020`, `RN-MV-021`, `RN-MV-030`, `RN-MV-049` | Confirmar entrega y avisa a comisiones exactamente como al confirmar una venta |
| `RN-MV-022` | Una venta de importe cero sigue siendo gratuita: no admite ningún método, tampoco este |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

La de cada entrada de compra, **sin ningún dato nuevo**: el método elegido es el de los puntos.

### 6.2 Salida

**La venta confirmada**, con su pago confirmado y **los puntos que costó**.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | La de la entrada usada; quien pide es quien compra; la moneda de la venta tiene tasa vigente; su cuenta de puntos en esa moneda alcanza |
| Postcondición | La venta y su pago están confirmados; los puntos de quien compró bajaron en lo que costó y los emitidos de la empresa subieron en lo mismo; lo comprado se entregó como en cualquier confirmación; comisiones recibió su aviso |

---

## 8. Flujo principal

1. Quien compra para sí mismo elige pagar con puntos en la entrada de siempre.
2. El sistema hace todas las validaciones de esa entrada, y además comprueba la tasa.
3. Registra la venta y su pago, descuenta los puntos y confirma la venta, **en un solo acto**.
4. Devuelve la venta confirmada.

---

## 9. Flujos alternativos

### FA-001 — Volver a pagar con puntos

Una venta propia pendiente —por ejemplo, cuyo pago con tarjeta se rechazó— se vuelve a pagar con puntos: el pago nuevo se descuenta y la venta se confirma en el acto.

### FA-002 — La misma petición llega dos veces

Con la misma clave de idempotencia se devuelve la venta ya confirmada, y **no se descuenta dos veces**.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Los puntos de esa moneda no alcanzan | Conflicto. **Nada queda escrito** |
| `EX-002` | La moneda de la venta no tiene tasa vigente | Conflicto. Nada queda escrito |
| `EX-003` | Quien registra la venta no es quien compra —registro por un funcionario, alta por enlace— | Conflicto |

Y las de cada entrada, que no cambian.

---

## 11. Validaciones

Las de cada entrada. Ninguna nueva.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-332` | Comprar un producto por el enlace de un vendedor pagando con puntos deja la venta **confirmada**, con su pago **confirmado**, y lo comprado **entregado** |
| `CA-MV-333` | Los puntos de quien compró **bajan** en `importe × tasa vigente`, **redondeado hacia arriba**: 10.00 a 0.3331 (3.331) cuestan 3.34; los emitidos de la empresa **suben** en lo mismo, y los dos asientos son de `PAGO`, suman cero y llevan el pago |
| `CA-MV-334` | La venta **genera sus comisiones**, como cualquier venta confirmada |
| `CA-MV-335` | Comprar un **paquete** con puntos, por la tienda y por el enlace, se comporta igual |
| `CA-MV-336` | **Volver a pagar** con puntos una venta propia pendiente la confirma en el acto |
| `CA-MV-337` | Si los puntos **no alcanzan**: conflicto, y **no existe la venta ni el pago**, ni cambió ningún saldo. Al volver a pagar, **no existe el pago nuevo** y la venta sigue pendiente |
| `CA-MV-338` | Con puntos **de otra moneda** y ninguno en la de la venta: conflicto, y nada queda escrito |
| `CA-MV-339` | Sin tasa vigente en la moneda de la venta: conflicto, y nada queda escrito |
| `CA-MV-340` | **Dos compras simultáneas** que juntas superan el saldo: **una** se confirma y la otra responde conflicto; el saldo **nunca** queda negativo |
| `CA-MV-341` | La **misma petición repetida** devuelve la misma venta y **descuenta una sola vez** |
| `CA-MV-342` | Un **funcionario** que registra una venta a nombre de otra persona con este método: conflicto, y nada queda escrito |
| `CA-MV-343` | Tras la migración, **ninguna venta tiene un pago pendiente con este método**: los que había están rechazados, con su motivo, y sus ventas siguen pendientes y se pueden volver a pagar |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Quien compra no ha tenido nunca puntos en esa moneda | No alcanzan: `EX-001` |
| El saldo alcanza justo | Se paga, y la cuenta queda en cero |
| La tasa cambia entre la consulta de `RF-MV-026` y la compra | Rige la del momento de la compra; lo que enseñó la consulta era orientativo |
| Una venta de importe cero | Es gratuita: no admite este método ni ningún otro (`RN-MV-022`) |

---

## 14. Preguntas abiertas

**Pago mixto** —una parte con puntos y otra con tarjeta—. No se ha pedido.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión, con la etapa 3 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). **Un método dentro de las compras propias**, sin operación ni permiso propios: descuenta a la tasa vigente redondeando hacia arriba, confirma en el acto con entrega y comisiones; si no alcanza no queda nada escrito. **Los pagos pendientes con este método que ya existían se rechazan**, por decisión del responsable del proyecto. Criterios `CA-MV-332` a `CA-MV-343`. | Responsable del proyecto |
