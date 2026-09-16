# SPEC — `RF-MV-012` Comprar un paquete para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-012` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 16-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

!!! abstract "Esta especificación hereda de `RF-MV-002` y no la repite"

    La venta que produce esta operación es **la misma de siempre**: mismo tipo, mismo estado inicial, mismo comprobante, mismo vendedor congelado en cada línea, y las mismas reglas sobre quién puede comprar y con qué se paga. Lo que cambia es **qué se compra**: un paquete en lugar de un producto suelto, y con él **varias líneas y un descuento por cada una**.

    Todo lo que no aparezca aquí es idéntico a [`RF-MV-002`](../002-comprar-producto-uno-mismo/spec.md) —que a su vez hereda de [`RF-MV-001`](../001-registrar-venta/spec.md)—: el cliente **es quien pide**, la fecha del hecho **no se admite**, la respuesta **no lleva el vendedor** y **no hace falta ningún permiso**. Las cuatro diferencias que aquel documento argumenta valen aquí sin cambio, y no se vuelven a defender.

    Este documento recoge **lo que el paquete añade**, que es una sola idea con cuatro caras: **el paquete se compra entero, uno, solo y tal como está hoy** (`RN-MV-028`).

---

## 1. Objetivo

Que **un cliente se compre el paquete entero** que su oferta le muestra, pagando **lo que ese paquete vale** —la suma de sus productos rebajados— y recibiendo **cada producto** con el descuento que el paquete le declara, congelado.

## 2. Contexto

**Los paquetes existen desde el 14-09-2026 y hasta hoy no se podían comprar.** `PM` los define, los valora y los publica —en la oferta y en el hotlink— y dejó dicho al nacer que la compra era de otra tanda: «una venta multilínea es de `MV` y `CM`» (`requirements/pm.md` v0.31.0). Esta operación es esa tanda.

**Lo que se compra es el paquete, no sus productos.** El cliente no elige qué llevarse de dentro ni cuántos: elige **el paquete**, y lo que la venta registra es **una línea por cada producto que el paquete contiene**, cada una con su precio de catálogo y **su rebaja**. Esa forma es deliberada y tiene consecuencia: lo vendido queda **producto a producto**, de modo que confirmar la venta entrega cada cosa por su cuenta y `CM` podrá comisionar por línea el día que lo decida.

**Y lo que se cobra es la suma de las líneas rebajadas**, que es exactamente el precio que `RN-PM-036` publica. **No se copia un importe de paquete**: se reconstruye sumando, y esa es la única forma de que el total sea verificable contra lo que el cliente vio.

!!! danger "El precio no se envía, y aquí menos que nunca"

    Vale la prohibición de `RF-MV-001` —el precio se toma del catálogo— y se añade la suya: **tampoco se envía el descuento**. Un descuento que llegara en la petición sería un descuento que elige quien compra, y el que manda es **el que el paquete declara** (`RN-PM-037`). El cliente envía **el paquete y con qué paga**; todo lo demás se resuelve.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cliente | **Es el sujeto y el actor a la vez** (`RN-MV-026`): compra para sí mismo, como en `RF-MV-002` |
| Vendedor | **Ni lo pide ni lo teclea**: sale de quien compra y se congela **en cada línea** (`RN-MV-003`). Todas las líneas de esta venta llevan el mismo |

## 4. Lo que el paquete añade

### 4.1 Se compra ENTERO: si algo no procede, no se vende nada

**Si al registrar un producto del paquete no se puede vender —está inactivo, retirado, fuera de la oferta de quien compra, o su upgrade no sube— la compra se rechaza completa**, nombrando el producto (`RN-MV-028`).

**No se vende lo que queda**, y el motivo es de negocio y no técnico: `RN-PM-044` ofrece el paquete **a quien puede comprarlo todo**, y el precio rebajado se pactó sobre **el conjunto**. Entregar tres de cuatro productos cobrando el precio de los cuatro es un cobro de más; entregar tres y recalcular el precio es **inventar un paquete que nadie configuró**. La tercera salida —rechazar— es la única que no obliga a decidir por el cliente.

**Y se acepta lo que cuesta**: un paquete con un producto retirado **deja de poder venderse hasta que alguien lo arregle**, y quien lo intente recibe un rechazo que nombra el producto. Es visible y se corrige en el catálogo, que es donde está el problema.

### 4.2 Se compra UNO: no hay cantidad

**La petición no admite cantidad** (`RN-MV-028`). No es una limitación de esta operación: `RN-PM-038` ya decidió que **dentro del paquete no hay cantidad** —«dos veces el mismo bot sería otro producto»—, y ofrecer «dos veces este paquete» sería contradecir esa decisión desde fuera.

Cada línea nace con cantidad **uno**, de modo que `RN-MV-015` —la cantidad es uno en los upgrades— **no tiene nada que comprobar**: se cumple por construcción.

### 4.3 Se compra SOLO: ni productos sueltos ni un segundo paquete

**Una venta de paquete lleva ese paquete y nada más** (`RN-MV-028`). No hay carrito.

**Lo que esto evita no es el desorden, es una regla rota**: mezclar un paquete con productos sueltos permitiría **dos upgrades en la misma venta por caminos distintos** —uno dentro del paquete, otro suelto—, y `RN-MV-010` quedaría comprobando algo que la petición no enseña. Mientras el paquete sea lo único que se compra, el upgrade del paquete es el único que puede haber (`RN-PM-046`).

**Quien quiera las dos cosas hace dos compras**, y son dos ventas. Es más papel y es honesto: cada una dice qué se pagó por qué.

### 4.4 Se compra TAL COMO ESTÁ HOY, y la oferta no es una promesa

**Al registrar se vuelve a comprobar todo lo que la oferta ya había mirado**: que el paquete esté activo, que su alcance lo publique, que tenga sus productos, y **que esté dentro de su vigencia** (`RN-PM-047`).

**No es desconfianza de `PM`: es que entre mirar y pagar pasa tiempo.** Un paquete puede vencer a medianoche, y la pantalla que lo pintó a las 23:58 no obliga a nadie. Es el mismo argumento con el que `RN-MV-007` revalida la oferta de cada producto aunque la interfaz ya la haya pintado.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-MV-028` | El paquete se compra **entero, uno, solo y tal como está hoy** | `requirements/mv.md` §5.1 |
| `RN-MV-027` | El descuento es **de la línea**, y se congela en dinero | `requirements/mv.md` §5.1 |
| `RN-MV-002` | Se copia lo que puede cambiar; lo inmutable se referencia | `requirements/mv.md` §5.1 |
| `RN-MV-003` | El vendedor es de la línea, sale de quien compra y se congela | `requirements/mv.md` §5.1 |
| `RN-MV-004` | Solo una venta confirmada produce efectos | `requirements/mv.md` §5.1 |
| `RN-MV-006` | **No se baja de nivel**; renovar el mismo se admite | `requirements/mv.md` §5.1 |
| `RN-MV-007` | El producto tiene que estar en la oferta de quien compra | `requirements/mv.md` §5.1 |
| `RN-MV-008` | A una cuenta en `FTD_PENDIENTE` no se le vende | `requirements/mv.md` §5.1 |
| `RN-MV-012` | Todas las líneas comparten la moneda de la cabecera | `requirements/mv.md` §5.1 |
| `RN-MV-013` | El total es la suma de las líneas, y se congela | `requirements/mv.md` §5.1 |
| `RN-MV-014` | El importe respeta los decimales de su moneda | `requirements/mv.md` §5.1 |
| `RN-MV-016` | Toda venta lleva un código legible | `requirements/mv.md` §5.1 |
| `RN-MV-022` | Importe cero y pago gratuito son lo mismo | `requirements/mv.md` §5.1 |
| `RN-MV-026` | Todo movimiento tiene un sujeto | `requirements/mv.md` §5.1 |
| `RN-PM-036` | El precio del paquete es la suma de sus productos rebajados | `requirements/pm.md` §5.1 |
| `RN-PM-037` | El descuento no deja a ningún producto por debajo de cero | `requirements/pm.md` §5.1 |
| `RN-PM-039` | Cuándo un paquete se puede ofrecer | `requirements/pm.md` §5.1 |
| `RN-PM-044` | El upgrade del paquete decide a quién se ofrece | `requirements/pm.md` §5.1 |
| `RN-PM-047` | El paquete se oculta fuera de su vigencia | `requirements/pm.md` §5.1 |

**`RN-MV-010`, `RN-MV-011` y `RN-MV-015` no aparecen, y su ausencia es una afirmación.** Las tres son imposibles de incumplir aquí: `RN-PM-046` garantiza **un upgrade como máximo** por paquete, `RN-PM-038` que **ningún producto se repite** dentro de él, y §4.2 que **la cantidad es uno**. No se comprueban porque no hay petición capaz de violarlas — y el día que el carrito exista (§4.3), volverán.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Paquete | Sí | Cuál se compra | Debe existir, estar **activo**, publicado donde corresponde, **dentro de su vigencia** y **ofrecible a quien compra** |
| Método de pago | **Condicional** | Con qué se paga | Igual que en `RF-MV-001`: prohibido si el total es cero —se asigna el gratuito— y obligatorio si tiene importe (`RN-MV-022`) |

**Dos campos, y los dos ya existían en otra operación.** Lo que define esta entrada es **todo lo que no admite**: ni cliente, ni fecha, ni productos, ni cantidades, ni precios, ni descuentos. Cada una de esas ausencias está argumentada —las cuatro primeras en `RF-MV-002`, las tres últimas en §2 y §4.2—, y **juntas significan que el cliente no puede negociar nada**: elige un paquete y dice con qué paga.

### 6.2 Salida

La de `RF-MV-002` —la venta sin el vendedor—, **con dos añadidos**:

| Dato | Descripción |
|---|---|
| **El paquete** | Cuál se compró, para que el comprobante lo diga: la venta es de un paquete y no de cuatro cosas sueltas |
| **Las líneas, con su descuento** | Cada producto con **lo que se le copió** —nombre, descripción, precio unitario, vigencia—, **lo que se le rebajó** y **cómo se pactó esa rebaja**: porcentaje o importe fijo, y su valor |

**El descuento viaja explicado y no solo restado.** Quien recibe esta respuesta tiene que poder decirle al cliente «10 % sobre 49.99, que son 5.00 menos», y no solo «pagas 44.99». Es la misma razón por la que se guarda (`RN-MV-027`): un importe sin su declaración no explica de dónde salió.

**Y el precio del paquete no viaja como un campo aparte**: es `payableAmount`, la suma de las líneas. Publicarlo dos veces daría dos números que pueden discrepar.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado, **no está en `FTD_PENDIENTE`** y puede comprar.
- El paquete existe, está activo, dentro de su vigencia, y **se le ofrece a ese actor**.
- Todos sus productos están activos y en la oferta que le corresponde.

**Postcondiciones**

- La venta queda registrada **pendiente**, con **una línea por producto del paquete**, cada una con su rebaja congelada, y el paquete anotado en la venta.
- El importe a pagar es **la suma de las líneas rebajadas**, y el total es lo que valdrían sin rebaja.
- **Nadie ha subido de nivel y nadie ha cobrado**: es la postcondición de `RN-MV-004`, y aquí vale igual.

## 8. Flujo principal

1. El actor pide comprar un paquete, indicando con qué paga.
2. El sistema resuelve **quién es** por su credencial y comprueba que **puede comprar**.
3. El sistema resuelve **el paquete** y comprueba que **hoy se le puede ofrecer a esa persona**: activo, publicado, **dentro de su vigencia**, con sus productos y con el nivel que le corresponde.
4. El sistema resuelve **cada producto del paquete** con el descuento que el paquete le declara.
5. El sistema comprueba, **producto a producto**, que está en la oferta de quien compra; y si alguno lleva upgrade, que **sube de nivel**.
6. El sistema comprueba que **todos comparten moneda** — la del paquete.
7. El sistema **copia** en cada línea el nombre, la descripción, el precio unitario y la vigencia, y **congela su rebaja** en dinero.
8. El sistema suma: total, descuento e importe a pagar, y comprueba que la escala corresponde a la moneda.
9. El sistema resuelve **el método de pago** contra el importe a pagar (`RN-MV-022`).
10. El sistema emite el **código del comprobante**, registra la venta **pendiente** con el paquete anotado, y emite el evento de auditoría.
11. El sistema devuelve la venta, con sus líneas y sus descuentos explicados.

**El paso 3 va antes que el 4 a propósito**: resolver los productos de un paquete que no se puede ofrecer es trabajo tirado, y además daría mensajes de producto a un problema que es del paquete.

**El paso 5 se hace aunque el paso 3 lo dé por bueno.** `RN-PM-039` mira el paquete como un todo y `RN-MV-007` mira **a quién se le vende cada producto**: son dos preguntas distintas, y la segunda es la que protege de vender algo fuera de la oferta de esa persona en concreto.

## 9. Flujos alternativos

### FA-001 — El paquete no lleva upgrade

**Cuándo ocurre:** son todos bots.

1. Se registra con normalidad. Ninguna comprobación de nivel interviene.
2. Es el caso más común y se enumera para que quede claro que **un paquete sin upgrade es un paquete completo**, no uno a medias.

### FA-002 — El paquete lleva un upgrade y varios bots

1. Se registra con normalidad: `RN-PM-046` garantiza que el upgrade es **uno**.
2. La comprobación de nivel se hace **solo sobre esa línea**.
3. Al confirmarse, esa línea concederá el nivel y las demás entregarán lo suyo.

### FA-003 — El descuento del paquete cambia después de la compra

1. **La venta no cambia.** Lo que se congeló, congelado está (`RN-MV-027`).
2. Una compra posterior del mismo paquete llevará el descuento nuevo, y las dos convivirán con importes distintos. **Eso es lo correcto**, y es la razón de ser de congelarlo.

### FA-004 — Un producto sale del paquete después de la compra

1. **La venta no cambia**, y sigue diciendo qué se vendió: la línea guarda el nombre y la descripción de aquel día (`RN-MV-002`).
2. La venta **conserva la referencia al paquete**, aunque el paquete ya no contenga ese producto. Es historia, y la historia no se reescribe.

### FA-005 — El paquete vence entre que se mira y se paga

1. La compra **se rechaza** (`RN-PM-047`, §4.4).
2. Es raro y es correcto: lo que estaba en oferta a las 23:58 puede no estarlo a las 00:01, y una promoción vencida que se cobra es una promoción que no terminó nunca.

## 10. Excepciones

### EX-001 — El paquete no existe

**Condición:** el identificador no corresponde a ningún paquete, o está retirado.
**Respuesta del sistema:** rechaza diciendo que el paquete indicado no existe, y no registra nada.

### EX-002 — El paquete no se puede ofrecer hoy

**Condición:** está inactivo, su alcance no lo publica, tiene menos de dos productos, le falta descripción, o **está fuera de su vigencia** (`RN-PM-039`, `RN-PM-047`).
**Respuesta del sistema:** rechaza **diciendo cuál de las cosas falla**, con el mismo detalle que el catálogo ya publica en `offerable`.

### EX-003 — El paquete no le corresponde a quien compra

**Condición:** su upgrade parte de una membresía que el actor no tiene vigente (`RN-PM-044`).
**Respuesta del sistema:** rechaza diciendo que ese paquete no está entre los que esa persona puede comprar. **Se distingue de `EX-002`** a propósito: uno dice «este paquete no se ofrece» y el otro «no se te ofrece a ti».

### EX-004 — Un producto del paquete no procede

**Condición:** está inactivo, retirado, o fuera de la oferta de quien compra.
**Respuesta del sistema:** rechaza **la compra completa**, nombrando el producto (`RN-MV-028`, §4.1).

### EX-005 — El upgrade del paquete BAJA de nivel

**Condición:** el producto de upgrade lleva a una membresía **inferior** a la vigente del actor.
**Respuesta del sistema:** rechaza la compra entera, como `EX-005` de `RF-MV-001` y por lo mismo: no se cobra por algo que quita.

### EX-006 — La cuenta no puede operar

**Condición:** el actor está en `FTD_PENDIENTE`.
**Respuesta del sistema:** rechaza diciendo **que le falta su depósito** (`RN-MV-008`).

### EX-007 — El método de pago no cuadra con el importe

**Condición:** se envía método en un paquete que vale cero, o no se envía en uno con importe, o se envía el gratuito con importe (`RN-MV-022`).
**Respuesta del sistema:** rechaza, con el mismo mensaje y el mismo código que `RF-MV-001`.

### EX-008 — El método de pago no existe o está inactivo

**Condición:** el método indicado no está en el catálogo, o está desactivado.
**Respuesta del sistema:** rechaza. Igual que en `RF-MV-001`.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Paquete obligatorio | El paquete de la compra es obligatorio. |

**Una sola, y esa escasez es el diseño.** No hay nada más que validar mirando la petición: no llegan líneas, ni cantidades, ni precios, ni fechas. Todo lo demás depende de **resolver** el paquete, y por eso vive en las excepciones y no aquí.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-049` | Un cliente autenticado **sin ningún permiso** compra un paquete de su oferta y la venta nace **pendiente**, con **una línea por producto** |
| `CA-MV-050` | El importe a pagar es **la suma de las líneas rebajadas**, y coincide con el `price` que el catálogo publica para ese paquete |
| `CA-MV-051` | El total es lo que valdrían **sin rebaja** y el descuento es la diferencia: las tres cifras cuadran |
| `CA-MV-052` | Cada línea trae **su rebaja explicada** —tipo, valor pactado y valor en dinero— y el nombre y la descripción **copiados** |
| `CA-MV-053` | La venta **recuerda el paquete**, y corregir el descuento del paquete después **no cambia** lo que se cobró |
| `CA-MV-054` | El sistema rechaza la compra **completa** cuando un producto del paquete está inactivo, **nombrándolo** |
| `CA-MV-055` | El sistema rechaza un paquete **fuera de su vigencia**, y lo distingue de uno inactivo |
| `CA-MV-056` | El sistema rechaza un paquete que **no le corresponde** al actor por su nivel, y lo distingue de uno que no se ofrece a nadie |
| `CA-MV-057` | El sistema rechaza el upgrade del paquete que **baja de nivel**, y **admite** el que renueva el mismo |
| `CA-MV-058` | La petición **no admite cantidad ni productos**: no hay forma de comprar dos paquetes ni de elegir qué llevarse |
| `CA-MV-059` | La venta creada aquí es **indistinguible** de cualquier otra venta una vez registrada, salvo por el paquete que recuerda |
| `CA-MV-060` | La auditoría guarda la instantánea completa: el paquete, cada línea con lo copiado y **cada rebaja como se pactó** |

**`CA-MV-050` es el criterio que sostiene la operación.** Si lo que se cobra no coincide con lo que el catálogo publica, el cliente paga algo distinto de lo que vio — y nadie lo detecta, porque los dos números salen de sitios distintos y cada uno cuadra consigo mismo.

**`CA-MV-053` se prueba corrigiendo el paquete después**, y no solo comparando al registrar. Es el mismo argumento de `CA-MV-003`: la copia solo se verifica cambiando el original.

## 13. Casos límite

- **Un paquete gratuito** —todos sus productos rebajados al cien por cien, o todos de precio cero—: se registra con normalidad y con el método **`GRATIS`** (`RN-MV-022`). No es un caso raro: es el que `RN-PM-006` abrió al admitir el precio cero.
- **Un paquete cuyo descuento deja un producto en cero pero no los demás:** se registra. La línea vale cero, la venta no, y el método de pago se decide **por el total** y no línea a línea.
- **El mismo paquete comprado dos veces seguidas:** las dos ventas se registran. Ninguna concede nada todavía, y si llevan upgrade el conflicto aparece **al confirmar la segunda**, que es donde `RF-MV-003` tiene que resolverlo — igual que con dos ventas del mismo upgrade suelto.
- **Un paquete con un solo producto:** no existe. `RN-PM-040` impide activarlo, de modo que nunca llega a la oferta; si alguien lo intenta, cae en `EX-002` diciendo que tiene menos de dos.
- **Un cliente sin membresía vigente:** solo puede comprar paquetes **sin upgrade**. Los demás caen en `EX-003`, y el mensaje habla de la oferta y no de su rol.
- **El precio de un producto del paquete cambia entre mirar y pagar:** se cobra **el de ahora**, no el que se vio. Es lo mismo que ocurre al comprar ese producto suelto (`RN-MV-002`), y el comprobante dice qué se cobró.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Queda declarado lo que esta operación no decide:** **sobre qué importe comisiona** una línea rebajada —lo que valía o lo que se cobró— es de `CM`, y `requirements/mv.md` §4.2 lo deja anotado. Esta venta guarda **las dos cifras**, de modo que la decisión no la bloquea nada.

**Y queda declarado lo que no es este requerimiento:** registrar la venta de un paquete **a nombre de otro** desde oficina. `RF-MV-001` sigue recibiendo productos sueltos; el día que un funcionario deba vender paquetes será un requerimiento propio.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 16-09-2026 | Redacción inicial, sin preguntas abiertas. **Se escribe por diferencias con `RF-MV-002`**, que a su vez hereda de `RF-MV-001`: las cuatro decisiones de aquel —el cliente es quien pide, sin fecha, sin vendedor en la respuesta y sin permiso— valen tal cual y no se repiten. Lo que este documento fija es **lo que el paquete añade**, que es `RN-MV-028` con sus cuatro caras: **entero** —si un producto no procede se rechaza todo, en lugar de vender lo que queda o de recalcular un paquete que nadie configuró—, **uno** —`RN-PM-038` ya decidió que dentro del paquete no hay cantidad—, **solo** —mezclarlo con productos sueltos permitiría dos upgrades por caminos distintos— y **tal como está hoy**, con su vigencia comprobada al registrar porque la oferta que lo pintó no es una promesa. Doce criterios nuevos, `CA-MV-049` a `CA-MV-060`, de los que `CA-MV-050` es el que sostiene todo: **lo que se cobra tiene que ser lo que el catálogo publica**. | Responsable del proyecto |
