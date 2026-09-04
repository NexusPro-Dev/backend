# SPEC — `RF-MV-001` Registrar una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-001` |
| Módulo | `MV` — Movimientos |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

    Debe poder leerlo alguien del negocio y entenderlo completo. Es la primera compuerta del Art. I.6: hasta que no esté aprobada, no se escribe `plan.md`.

---

## 1. Objetivo

Dejar constancia de **qué le vendió la empresa a un cliente**: qué productos, en qué cantidad, a qué precio y con qué vigencia, **atribuida al vendedor que le corresponde** — como un hecho que todavía **no está pagado**.

## 2. Contexto

Es el **primer requerimiento del módulo** y el que pone en el sistema el objeto que le faltaba. Hasta hoy el sistema sabe qué se vende (`PM`), quién vende (`SP`) y cuánto gana cada rol por cada producto (`CM`), y **no sabe qué se vendió**: `requirements/cm.md` §1.4 no liquida porque no hay ninguna tabla de ventas a la que aplicar un porcentaje.

**Lo que esta operación registra no es un cobro.** La venta nace **pendiente**, que significa exactamente que alguien dijo que iba a pagar y nadie ha comprobado que pagara. No concede ningún nivel, no habilita ninguna cuenta y no comisiona (`RN-MV-004`). Confirmarla es otra operación, `RF-MV-003`.

**Y lo que registra queda congelado.** El precio y la vigencia se **copian** del catálogo en el momento, y el vendedor se **toma del cliente** y se guarda. Ninguno de los tres se vuelve a leer: corregir mañana el precio de un producto, o reasignar un cliente a otro agente, **no puede cambiar lo que se vendió hoy** (`RN-MV-002`, `RN-MV-003`).

!!! danger "El precio no se envía: se toma del catálogo, y esa es una decisión de negocio"

    Quien registra la venta indica **qué productos y cuántos**, y **nunca cuánto cuestan**. Un precio que llega en la petición es **un precio que elige quien vende**, y eso tiene un nombre: es un descuento — que hoy no existe por decisión del responsable del proyecto (`requirements/mv.md` §1.3).

    La consecuencia es que **el importe de la venta no es negociable en el momento de registrarla**. Si hace falta que lo sea, la salida es el descuento con su autorización y su rastro, y no un campo abierto en esta operación.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Funcionario | Registra la venta a nombre de un cliente. Es quien la teclea, y **no cobra nada por ella** |
| Cliente | Es **el sujeto** de la venta, no el actor. Aquí no interviene; cuando compra él, la operación es `RF-MV-002` |
| Vendedor | **Ni la pide ni la teclea**: el sistema lo deduce del cliente y lo congela. De él colgará la comisión |

## 4. Alcance

### 4.1 Incluye

- Registrar una venta a nombre de un cliente, con **una o varias líneas**.
- **Copiar** de cada producto su precio unitario y su vigencia en días.
- **Deducir el vendedor** del cliente y congelarlo en la venta.
- Calcular el total como la **suma de las líneas**, y con él el importe a pagar.
- Emitir el **código del comprobante**.
- Dejarla **pendiente de pago**.
- Verificar que el cliente existe, que **puede comprar** y que cada producto está **en la oferta que le corresponde**.
- Verificar las reglas de composición: un solo upgrade, sin productos repetidos, una sola moneda, y cantidad uno en los upgrades.
- Dejar constancia del alta en la auditoría de cambios.

### 4.2 No incluye

- **Cobrar.** Ninguna pasarela interviene: la venta queda pendiente y el dinero se comprueba fuera del sistema.
- **Confirmarla, rechazarla o anularla.** Son `RF-MV-003`, `RF-MV-004` y `RF-MV-005`.
- **Conceder el nivel comprado.** Eso ocurre **al confirmar** y solo entonces (`RN-MV-004`).
- **Que el cliente compre por su cuenta**, que es `RF-MV-002`.
- **Descuentos e impuestos**: no existen (`requirements/mv.md` §1.3). El importe a pagar es siempre igual al total.
- **Pagar con puntos**, que es la etapa 3 del módulo.
- **Registrar un depósito**, que es la etapa 2 y es otra cosa: no lleva producto y no la origina una venta.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-MV-002` | Se copia lo que puede cambiar | `requirements/mv.md` §5.1 |
| `RN-MV-003` | El vendedor sale de quien compra y se congela, y **puede no haberlo** | `requirements/mv.md` §5.1 |
| `RN-MV-004` | Solo una venta confirmada produce efectos | `requirements/mv.md` §5.1 |
| `RN-MV-006` | Solo se sube de nivel | `requirements/mv.md` §5.1 |
| `RN-MV-007` | El producto tiene que estar en la oferta de quien compra | `requirements/mv.md` §5.1 |
| `RN-MV-008` | A una cuenta en `FTD_PENDIENTE` no se le vende | `requirements/mv.md` §5.1 |
| `RN-MV-009` | Una venta lleva al menos una línea | `requirements/mv.md` §5.1 |
| `RN-MV-010` | Como mucho un upgrade por venta | `requirements/mv.md` §5.1 |
| `RN-MV-011` | El mismo producto no se repite | `requirements/mv.md` §5.1 |
| `RN-MV-012` | Todas las líneas comparten la moneda de la cabecera | `requirements/mv.md` §5.1 |
| `RN-MV-013` | El total es la suma de las líneas, y se congela | `requirements/mv.md` §5.1 |
| `RN-MV-014` | El importe respeta los decimales de su moneda | `requirements/mv.md` §5.1 |
| `RN-MV-015` | La cantidad es uno en los upgrades | `requirements/mv.md` §5.1 |
| `RN-MV-016` | Toda venta lleva un código legible | `requirements/mv.md` §5.1 |
| `RN-MV-018` | Un método de pago desactivado no invalida lo pagado con él | `requirements/mv.md` §5.1 |
| `RN-SP-026` | La cuenta registrada por enlace autentica y no opera | `requirements/sp.md` §5.1 |
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |

**Este requerimiento hace cumplir catorce de ellas y sufre las otras tres.** `RN-MV-004` no se comprueba aquí: se **respeta** dejando la venta pendiente. `RN-SP-026` y `RN-PM-009` son de otros módulos y este las consulta, no las evalúa — es `SP` quien dice en qué estado está la cuenta y `PM` quien dice qué se le puede ofrecer a esa persona.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Cliente | Sí | A nombre de quién es la venta | Debe existir, **no estar eliminado** y **no estar en `FTD_PENDIENTE`** (`RN-MV-008`) |
| Método de pago | Sí | Con qué se va a pagar | Debe existir y estar activo (`RN-MV-018`) |
| Líneas | Sí | Qué se vende | **Al menos una** (`RN-MV-009`), sin productos repetidos (`RN-MV-011`) |
| — Producto | Sí | Cuál | Debe existir y **estar en la oferta del cliente** (`RN-MV-007`) |
| — Cantidad | Sí | Cuántos | Entera y mayor que cero. **Uno**, si el producto es un upgrade (`RN-MV-015`) |
| Fecha del hecho | No | Cuándo ocurrió la venta | Por omisión, **ahora**. **No puede estar en el futuro** |

**Ni el precio, ni la vigencia, ni la moneda, ni el vendedor son datos de entrada**, y esa lista de ausencias es la mitad del diseño de esta operación:

- **El precio y la vigencia** se copian del catálogo (§2). Enviarlos sería fijarlos.
- **La moneda** es la del producto que se vende, y por eso `RN-MV-012` no es una comprobación contra un campo enviado sino contra **las líneas entre sí**: si dos productos vienen en monedas distintas, no hay ninguna venta posible que las contenga.
- **El vendedor** sale del cliente (`RN-MV-003`). Pedirlo permitiría atribuirse la venta de otro, que es exactamente lo que congelarlo evita.

**La fecha del hecho es el único campo opcional, y existe por un caso real**: un funcionario registra el lunes la venta que se cerró el sábado. Sin él, todo lo vendido lleva la fecha en que alguien tuvo tiempo de teclearlo, y esa fecha es además **la que sale impresa en el código del comprobante** (`RN-MV-016`).

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Venta | La venta registrada, con su identificador y su **código de comprobante** |
| Estado | **Pendiente**, siempre. No hay ningún camino por el que esta operación devuelva otra cosa |
| Cliente resuelto | Quién compró, con su nombre, y no solo su identificador |
| **Vendedor resuelto** | **A quién se le atribuye**, con su nombre. Es el dato que el actor no envió y que más le importa a quien mira la venta |
| Líneas | Cada producto con su nombre, su cantidad, **el precio que se le copió**, la vigencia copiada y el importe de la línea |
| Moneda | La de la venta, resuelta |
| Importes | Total, descuento e importe a pagar |
| Fecha del hecho | La que se registró, sea la enviada o la de ahora |

**El vendedor se devuelve siempre que lo haya**, y no es un adorno. Quien registra la venta **no lo eligió**, de modo que la respuesta es el único momento en que puede ver a quién acaba de atribuirse lo que vendió — y si es el equivocado, el problema está en la estructura comercial y no en esta venta.

**Desde el 04-09-2026 puede venir vacío**, cuando quien compra no cuelga de nadie. **Viaja igual, en nulo y no ausente**: la diferencia entre «esta venta no tiene vendedor» y «esta respuesta no lo trae» es exactamente la que decide si alguien va a cobrar por ella, y colapsarla dejaría a cada consumidor adivinando.

**El descuento se devuelve aunque valga siempre cero.** Omitirlo obligaría a añadirlo al contrato el día que exista, y a que todos los consumidores lo traten como opcional para siempre.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de movimientos.
- El cliente existe, **cuelga de un vendedor** y no está en `FTD_PENDIENTE`.
- Existe al menos un producto en la oferta de ese cliente, y al menos un método de pago activo.

**Postcondiciones**

- La venta queda registrada **en estado pendiente**, con su código, sus líneas y su vendedor congelado.
- La auditoría de cambios contiene un evento de creación con el estado inicial completo de la venta.
- **Nadie ha subido de nivel, nadie ha cobrado y nadie ha comisionado.** Es la postcondición que conviene leer dos veces: registrar una venta no cambia absolutamente nada fuera de este módulo.

## 8. Flujo principal

1. El actor envía el cliente, el método de pago y las líneas.
2. El sistema comprueba que el cliente existe y **que puede comprar**: ni eliminado, ni en `FTD_PENDIENTE`.
3. El sistema resuelve **el vendedor de quien compra**, si lo hay, y lo retiene.
4. El sistema comprueba la composición de las líneas: al menos una, sin productos repetidos, **como mucho un upgrade**, y cantidad uno en él.
5. El sistema comprueba que **cada producto está en la oferta que le corresponde a ese cliente**.
6. El sistema comprueba que **todos los productos comparten moneda**.
7. El sistema **copia** de cada producto su precio unitario y su vigencia, y calcula el importe de cada línea.
8. El sistema suma las líneas, fija el total y el importe a pagar, y comprueba que la escala corresponde a la moneda.
9. El sistema emite el **código del comprobante** con la fecha del hecho.
10. El sistema registra la venta **pendiente**, con su vendedor congelado, y emite el evento de auditoría de creación.
11. El sistema devuelve la venta con su código, su vendedor resuelto y sus líneas con lo que se les copió.

**El paso 5 va después del 4 a propósito.** Comprobar la oferta es lo más caro de la operación —hay que resolver qué puede comprar esa persona—, y hacerlo antes de saber si la petición está bien formada gastaría ese trabajo para rechazarla por un producto repetido.

**El paso 3 ya no puede fallar, y se conserva donde está.** Desde el 04-09-2026 no encontrar vendedor **no rechaza nada**: la venta se registra sin atribución. Resolverlo aquí y no al final sigue siendo lo correcto —es una lectura del mismo cliente que el paso 2 acaba de verificar—, pero deja de ser una comprobación y pasa a ser un dato que se recoge.

## 9. Flujos alternativos

### FA-001 — Venta de varios bots

**Cuándo ocurre:** todas las líneas son productos que no cambian de nivel.

1. Se registra con normalidad, con tantas líneas como productos.
2. **No hay ninguna comprobación de nivel**, porque no hay upgrade: `RN-MV-006` no aplica.
3. Es el caso más común y se enumera para que quede claro que **una venta sin upgrade es una venta completa**, no una a medias.

### FA-002 — Venta con un upgrade y varios bots

**Cuándo ocurre:** una de las líneas cambia de nivel y las demás no.

1. Se registra con normalidad. `RN-MV-010` permite **uno** y este es uno.
2. La comprobación de nivel se hace **solo sobre esa línea**.
3. Al confirmarse, esa línea será la que conceda el nivel, y las demás no harán nada.

### FA-003 — El precio del producto cambia después de registrada la venta

**Cuándo ocurre:** alguien corrige el precio en el catálogo al día siguiente.

1. **La venta no cambia.** Lo que se copió, copiado está (`RN-MV-002`).
2. Una venta del mismo producto registrada después llevará el precio nuevo, y las dos convivirán con importes distintos. **Eso es lo correcto**, y es la razón de ser de la copia.

### FA-004 — El cliente se reasigna a otro vendedor después

**Cuándo ocurre:** la estructura comercial cambia.

1. **La venta sigue atribuida a quien la vendió** (`RN-MV-003`).
2. Las ventas siguientes se atribuirán al vendedor nuevo. Esto es lo que impide que reorganizar la fuerza comercial en marzo cambie quién ganó por una venta de enero.

### FA-005 — Se registra hoy una venta de una fecha anterior

**Cuándo ocurre:** el actor envía la fecha del hecho.

1. La venta se registra con esa fecha, y **el código del comprobante lleva ese día** y no el de hoy (`RN-MV-016`).
2. Queda constancia de las dos fechas: cuándo ocurrió y cuándo se registró.

## 10. Excepciones

### EX-001 — El cliente no existe

**Condición:** el identificador de cliente no corresponde a nadie, o la persona está eliminada.
**Respuesta del sistema:** rechaza la venta diciendo que el cliente indicado no existe, y no registra nada.

### EX-002 — El cliente está en `FTD_PENDIENTE`

**Condición:** la cuenta se registró por enlace y su depósito no se ha confirmado.
**Respuesta del sistema:** rechaza la venta diciendo que esa cuenta **todavía no puede operar**, y que lo que le falta es su depósito. **No se le vende y no se le deja a medias** (`RN-MV-008`).

### ~~EX-003~~ — Retirada el 04-09-2026

**Decía:** «el cliente no cuelga de ningún vendedor → rechaza la venta diciendo que no se puede atribuir».

**Se retira por decisión del responsable del proyecto, y no se borra**, porque el motivo por el que existía sigue leyéndose bien y llevaba a la conclusión equivocada: *«`RN-SP-027` promete que esto no ocurre, y una promesa de otro módulo no es una comprobación de este»*. El argumento era correcto **y la premisa incompleta** — daba por hecho que **quien compra es siempre un cliente**.

**No lo es.** Un agente también compra, y la fuerza comercial no está hecha para que todos sus miembros cuelguen de otro: `RN-SP-019` declara que **la cúspide no declara superior**. Con esta excepción en pie, esa persona no podía comprar nada.

**Qué ocurre ahora:** la venta se registra **sin vendedor**. No es un dato que falte ni un error que se tolera: es un estado legítimo. Lo que cuesta está en `spec.md` §13 y en `requirements/mv.md` §5.2 — **no comisiona a nadie**.

**El número no se reutiliza.** Las excepciones siguen siendo `EX-001`, `EX-002`, `EX-004` a `EX-011`: renumerarlas cambiaría el código que ya devuelven las que no han cambiado.

### EX-004 — Un producto no está en la oferta del cliente

**Condición:** el producto existe pero no es de los que esa persona puede comprar — está retirado, inactivo, o no le corresponde por su nivel.
**Respuesta del sistema:** rechaza la venta **nombrando el producto**, y no registra ninguna línea.

### EX-005 — El upgrade no sube de nivel

**Condición:** el producto lleva a una membresía **igual o inferior** a la que el cliente ya tiene.
**Respuesta del sistema:** rechaza la venta diciendo que esa membresía no está por encima de la actual. Se rechaza **al registrar y no al confirmar**, que es lo único que evita cobrarle a alguien por algo que no le da nada (`RN-MV-006`).

### EX-006 — Dos upgrades en la misma venta

**Condición:** dos líneas llevan a membresías.
**Respuesta del sistema:** rechaza la venta diciendo que solo se admite un cambio de nivel por operación (`RN-MV-010`).

### EX-007 — El mismo producto repetido

**Condición:** dos líneas nombran el mismo producto.
**Respuesta del sistema:** rechaza la venta pidiendo que se sumen en una sola línea (`RN-MV-011`).

### EX-008 — Productos en monedas distintas

**Condición:** las líneas no comparten moneda.
**Respuesta del sistema:** rechaza la venta diciendo que una venta se cobra en una sola moneda. **No hay conversión posible**: el sistema no tiene ninguna tasa de cambio (`RN-MV-012`).

### EX-009 — Cantidad distinta de uno en un upgrade

**Condición:** la línea del upgrade pide dos o más.
**Respuesta del sistema:** rechaza la venta diciendo que un cambio de nivel no admite cantidad (`RN-MV-015`).

### EX-010 — El método de pago no existe o está inactivo

**Condición:** el método indicado no está en el catálogo, o está desactivado.
**Respuesta del sistema:** rechaza la venta. **Un método desactivado no invalida lo ya vendido con él** (`RN-MV-018`), pero tampoco sirve para vender hoy.

### EX-011 — El producto no existe

**Condición:** el identificador de producto no corresponde a nada.
**Respuesta del sistema:** rechaza la venta diciendo que el producto indicado no existe. **Se distingue de `EX-004`** a propósito: quien escribió mal un identificador no debe buscar el error en la oferta.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Cliente obligatorio | El cliente de la venta es obligatorio. |
| `VAL-002` | Método de pago obligatorio | El método de pago es obligatorio. |
| `VAL-003` | Al menos una línea | Una venta debe llevar al menos un producto. |
| `VAL-004` | Producto obligatorio en la línea | Cada línea debe indicar su producto. |
| `VAL-005` | Cantidad mayor que cero | La cantidad de cada línea debe ser mayor que cero. |
| `VAL-006` | Producto no repetido | Un producto no puede aparecer dos veces en la misma venta. |
| `VAL-007` | Fecha del hecho no futura | La fecha de la venta no puede estar en el futuro. |

**`VAL-006` es de entrada y no de negocio**, aunque `RN-MV-011` sea una regla: la repetición se ve **mirando la petición**, sin consultar nada, y rechazarla ahí ahorra resolver ofertas y precios de una venta que ya se sabe mal formada.

**No hay validación de precio, de moneda ni de importe.** No se envían (§6.1), de modo que no hay nada que validar: lo que en otras operaciones sería una comprobación, aquí es **una ausencia de campo**.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-001` | El sistema registra una venta de un producto y la devuelve **pendiente**, con su código de comprobante |
| `CA-MV-002` | La venta devuelve **el vendedor resuelto**, que el actor no envió, y es el superior comercial del cliente |
| `CA-MV-003` | El precio y la vigencia de cada línea son **los del catálogo en ese momento**, y **corregir el producto después no los cambia** |
| `CA-MV-004` | El total es la suma de las líneas y **el importe a pagar es igual al total**, con el descuento en cero |
| `CA-MV-005` | El sistema registra una venta de **varios productos**, incluida una con un upgrade y varios bots |
| `CA-MV-006` | El código del comprobante lleva el prefijo del tipo, **el día de la fecha del hecho** y seis caracteres del alfabeto sin `I`, `L`, `O` ni `U` |
| `CA-MV-007` | La venta **no cambia el nivel de nadie**: el cliente conserva su membresía después de registrarla |
| `CA-MV-008` | El sistema rechaza vender a una cuenta en `FTD_PENDIENTE`, **diciendo que le falta el depósito** |
| `CA-MV-009` | El sistema rechaza un cliente inexistente, y **lo distingue** de un cliente que no puede operar |
| `CA-MV-010` | El sistema rechaza un producto **fuera de la oferta** del cliente, nombrándolo, y lo distingue de un producto inexistente |
| `CA-MV-011` | El sistema rechaza un upgrade a una membresía **igual o inferior** a la vigente |
| `CA-MV-012` | El sistema rechaza **dos upgrades** en la misma venta |
| `CA-MV-013` | El sistema rechaza el **mismo producto repetido** y una cantidad mayor que uno en un upgrade |
| `CA-MV-014` | El sistema rechaza productos en **monedas distintas** |
| `CA-MV-015` | El sistema rechaza un método de pago inexistente o inactivo |
| `CA-MV-016` | El sistema rechaza una venta **sin líneas**, sin cliente y con fecha futura |
| `CA-MV-017` | **Invertido el 04-09-2026.** El sistema **registra** la venta de quien no cuelga de ningún vendedor, **sin atribución** y sin error. Antes afirmaba lo contrario |
| `CA-MV-018` | La auditoría de cambios contiene la creación con la instantánea completa, **incluido el vendedor congelado** |

**`CA-MV-007` afirma que el sistema NO hace algo**, y es el criterio que sostiene todo el módulo. Sin él, la diferencia entre registrar y confirmar es una palabra en un documento; con él, es algo que falla si alguien la borra.

**`CA-MV-003` se prueba corrigiendo el producto después**, y no solo comparando el precio al registrar. La copia solo se puede verificar cambiando el original: si la venta leyera el catálogo al mostrarse, un precio idéntico pasaría la prueba igual.

## 13. Casos límite

- **Un cliente sin membresía vigente:** no tiene oferta que resolver, de modo que **cualquier producto que se le intente vender cae en `EX-004`**. No es un caso especial de esta operación: es lo que `RF-PM-007` responde cuando no hay nivel del que partir.
- **Una persona que no es cliente:** solo los consumidores tienen membresía (`RN-SP-018`), de modo que quien no lo es **no tiene nivel del que partir** y su oferta son **los bots y nada más**. Puede comprarlos: intentar venderle un upgrade cae en `EX-004`, y el mensaje hablará de la oferta y no del rol. **Desde el 04-09-2026 esto importa de verdad**, porque es el caso que la decisión abrió: un agente compra bots con normalidad.
- **Quien compra y no cuelga de nadie:** la venta se registra **sin vendedor** (`RN-MV-003`, desde el 04-09-2026). Ocurre con la **cúspide de la fuerza comercial**, que por `RN-SP-019` no declara superior, y con cualquiera a quien nadie haya colgado todavía. **La consecuencia es que esa venta no comisiona a nadie**: `RN-CM-011` liquida recorriendo la cadena hacia arriba desde el vendedor, y sin punto de partida no hay cadena. Es correcto —nadie vendió, nadie cobra— y **es la mitad cara de esta decisión**: la alternativa era inventar una atribución, y una comisión pagada a quien no vendió no se detecta, porque el dinero sale y el número cuadra.
- **La membresía del cliente vence entre registrar y confirmar:** la venta ya está registrada y **no se revalida al confirmar**. Es una consecuencia aceptada de que la comprobación sea del momento del registro, y la alternativa —revalidar— haría que una venta pagada pudiera rechazarse por algo que el cliente no controla.
- **El producto se retira del catálogo entre registrar y confirmar:** igual. Lo vendido está copiado y `RN-PM-010` garantiza que el producto no desaparece nunca.
- **Dos ventas simultáneas del mismo upgrade al mismo cliente:** **las dos se registran**, y no hay ninguna regla que lo impida — ninguna de las dos ha concedido nada todavía. El conflicto aparece al confirmar la segunda, y es `RF-MV-003` quien tiene que resolverlo.
- **Una venta de importe cero:** posible si el producto es gratuito. Se registra con normalidad; `RN-PM-006` exige precio mayor que cero en el catálogo, de modo que hoy no puede ocurrir, y esta operación no añade una comprobación propia para algo que el catálogo ya impide.
- **Una cantidad muy grande de un bot:** no hay tope. Nada acota cuántos bots caben en una venta, y ponerle un número aquí sería inventarlo.
- **La fecha del hecho muy antigua:** se admite. Solo se rechaza el futuro, porque una venta que aún no ha ocurrido no es un hecho — y el pasado remoto es exactamente lo que hace falta para registrar lo que ya ocurrió.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Queda declarado un condicionante que no es una pregunta de este requerimiento:** quién puede ver las ventas de quién depende de **D-22**, abierta. Esta operación **crea** ventas y no las lista, de modo que no la afecta; lo que puede tener que cambiar el día que D-22 se cierre es `RF-MV-006`.

**Y queda declarado un bloqueo de implementación que no es de diseño:** este requerimiento **no se puede construir antes que `RF-SP-045`**, que es quien crea los clientes colgando de un vendedor y el estado `FTD_PENDIENTE`. Sin él, `EX-002` y `EX-003` no tienen nada que comprobar. No bloquea esta especificación.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 02-09-2026 | Redacción inicial, sin preguntas abiertas. Es la primera tripleta del `MV` renacido, y la que fija cómo se comporta la venta: **nace pendiente**, **congela** precio, vigencia y vendedor, y **no acepta el precio como dato de entrada** — que es la decisión con más consecuencias de este documento, porque convierte «negociar el importe» en algo que no se puede hacer sin descuentos. Once excepciones, de las que tres —`EX-002`, `EX-005` y `EX-003`— existen para que la venta se niegue a nacer antes de cobrar en lugar de después. | Responsable técnico |
| 0.2.0 | 04-09-2026 | **El vendedor deja de ser obligatorio, y con él se retira `EX-003`** (Art. I.7, sobre un requerimiento ya construido). Lo decidió el responsable del proyecto: **comprar no es cosa solo de los clientes** — un agente también compra. El motivo por el que `EX-003` existía sigue leyéndose bien —«una promesa de otro módulo no es una comprobación de este»— y **su premisa estaba incompleta**: daba por hecho que quien compra es siempre un cliente. `RN-SP-019` declara desde el principio que **la cúspide de la fuerza comercial no declara superior**, de modo que con esa excepción en pie esa persona **no podía comprar nada**. `CA-MV-017` **invierte su sentido**: afirmaba que la venta se rechazaba y ahora afirma que se registra, sin atribución y sin error. El vendedor sigue viajando en la respuesta y **puede venir en nulo**, nunca ausente: la diferencia entre «no tiene vendedor» y «no vino el campo» es la que decide si alguien cobra. **Lo que cuesta queda en §13**: una venta sin vendedor **no comisiona a nadie**, porque `RN-CM-011` recorre la cadena hacia arriba desde él. Se acepta a conciencia — la alternativa era inventar una atribución, y una comisión pagada a quien no vendió **no se detecta**. **El número de la excepción no se reutiliza** y el hueco queda a la vista, para que nadie lea `EX-004` creyendo que es la tercera. | Responsable del proyecto |
