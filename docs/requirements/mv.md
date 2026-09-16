# Requerimientos del Módulo — `MV` Movimientos

| Campo | Valor |
|---|---|
| Módulo | `MV` — Movimientos |
| Paquete | `modules/movements` |
| Prefijos de permiso | `movements:` |
| Versión | 0.16.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 02-09-2026 |
| Última actualización | 16-09-2026 |

!!! info "Qué va en este documento"

    El catálogo de requerimientos del módulo: qué debe hacer, bajo qué reglas y con qué permisos.

    El comportamiento detallado de cada requerimiento —flujos, validaciones, criterios de aceptación y casos límite— vive en su tripleta, en `docs/specs/mv/`. Aquí no se repite.

!!! warning "Este módulo ya existió una vez, y se retiró entero"

    El 01-09-2026 se descartó el diseño anterior de `MV` —catorce requerimientos, treinta y nueve reglas, cuatro submódulos y el libro de todo hecho económico— por decisión del responsable del proyecto, y se acordó **empezar de nuevo**.

    Este documento no es aquel corregido: **es otro**. Los identificadores vuelven a empezar en `RF-MV-001` y ninguna regla hereda su número. Lo que sobrevive lo hace porque se vuelve a defender aquí, no porque estuviera escrito antes.

    **Lo que se hace distinto**, y es la lección del primer intento: aquel documento diseñó **el libro completo** —ventas, depósitos, puntos, comisiones, pasarela y notificaciones entrantes— antes de que existiera una sola venta. El alcance del módulo sigue siendo el mismo; lo que cambia es que **se construye por etapas y este documento solo escribe la primera** (§4.2).

---

## 1. Información del módulo

### 1.1 Descripción

`MV` es dueño de **lo que ocurrió con el dinero**. Un movimiento es un **hecho económico ya sucedido**: alguien compró algo, alguien depositó, a alguien se le pagó.

Lleva un **tipo**, y eso es lo que permite que la venta de un upgrade, el depósito inicial de un cliente y el pago de una comisión vivan en la misma tabla sin dejar de ser cosas distintas. Hoy hay **un solo tipo sembrado —la venta—** y la tabla ya está preparada para los demás, que es distinto de tenerlos.

**Un movimiento no se edita y no se borra.** Lo único que se mueve en él es su **estado**, y solo hacia adelante.

### 1.2 Objetivo

El sistema sabe **qué se vende** (`PM`), **quién vende y a quién trae cada quien** (`SP`) y **cuánto le corresponde a cada rol por cada producto** (`CM`). No sabe **qué se vendió**.

Esa ausencia es la que bloquea a los otros tres: `requirements/pm.md` §1.4 no registra la compra «porque no existe el cobro», y `requirements/cm.md` §1.4 no liquida porque **no hay ninguna tabla de ventas a la que aplicar un porcentaje**. Este módulo pone ese objeto en el sistema.

### 1.3 Alcance

**La etapa 1 —lo que este documento escribe— incluye:**

- **Registrar una venta**: qué productos pidió quién —**pueden ser varios**—, a qué precio, en qué moneda y con qué vigencia. Todo **copiado en el momento**, línea a línea, nunca leído después del catálogo.
- **Dos entradas para la misma venta**: la que registra un funcionario y la que hace el propio cliente desde su panel.
- **Resolverla**: confirmarla cuando el dinero entró, **rechazarla** cuando no entró, **anularla** cuando no debía existir.
- **Consultarla**, en lista y en detalle, y que **cada persona consulte las suyas**.
- Su **comprobante interno**, con código legible.
- El catálogo de **métodos de pago**, sembrado y de solo lectura.

**No incluye**

- **Cobrar.** Este módulo registra que se pagó; **quién ejecuta el cobro es una pasarela**, y su integración es una etapa propia. Una venta `PENDIENTE` es exactamente eso: un cobro que todavía no confirmó nadie.
- **Aplicar el efecto de lo comprado.** Que un upgrade cambie el nivel de alguien es escribir en `user_memberships`, tabla de `SP` con sus propias reglas. Ver §1.4 y **D-26**.
- **El depósito y el FTD**, los **puntos**, las **notificaciones de la pasarela**, el **devengo de comisiones** y los **retiros**. Son las etapas 2 a 5 y están en §4.2, con lo que ya se decidió de cada una.
- **La factura fiscal.** El comprobante de este módulo **no es un documento DIAN**: no lleva resolución, ni rango autorizado, ni nota crédito. Ver §1.5.
- **Descuentos e impuestos.** Decisión del responsable del proyecto del 02-09-2026: **por ahora no hay ninguno de los dos**. El descuento tiene columna y vale cero (§7.1); el impuesto **no tiene columna**, porque separarlo de la base exige decidir con qué tasa se recalcula lo ya vendido y esa decisión llega con la factura fiscal.

### 1.4 La frontera con `SP` y con `PM`, y por qué cuesta

**Una venta no cambia el nivel de nadie por su cuenta.** Registra que alguien compró un upgrade; quien lo aplica es `SP`, porque `user_memberships` es suya. Es la misma frontera que [`requirements/pm.md` §1.4](pm.md) trazó para el catálogo.

**Y aquí sí hay que cruzarla.** `PM` podía quedarse fuera porque no necesitaba escribir nada en `SP`: publicaba un catálogo y ahí terminaba. `MV` no puede — una venta confirmada de un upgrade **tiene que conceder el nivel**, o confirmar no significa nada. Todas las interfaces publicadas entre módulos hasta hoy son **de solo lectura**, así que esto abre una forma nueva: es **D-26**, en §3.

**Lo que este módulo sí deja resuelto para quien venga después:** la venta **congela** el importe, la moneda, la vigencia y el vendedor. Ninguno se vuelve a leer del catálogo ni de la estructura comercial. Es la condición que `PM` le impuso a este módulo **antes de que existiera** —«cada compra guardará el importe que se pagó y la vigencia que compró»— y es la que impide que corregir un precio reescriba una venta de hace un año.

### 1.5 El comprobante no es una factura, y conviene decirlo aquí

El comprobante que emite este módulo lleva **código propio y único**, y sirve para que una persona sepa qué compró y por cuánto. **No es un documento fiscal.**

La diferencia no es de matiz. Una factura electrónica en Colombia va bajo **resolución de la DIAN** con rango numérico autorizado, no se modifica jamás —se anula con **nota crédito**— y se emite a través de un proveedor autorizado. FACTECH GROUP SAS es una sociedad colombiana, de modo que esto llegará.

**Se decide por adelantado cómo llegará**, para que llegue como una ampliación y no como una reescritura: la factura fiscal será una **entidad aparte** que apunta al movimiento y tiene su propia numeración, y no un campo más de esta tabla. Un movimiento puede existir sin factura y una factura no puede existir sin el hecho que la origina.

**Y por eso el campo no se llama `resolucion`.** Se llama `code`: «resolución» es la palabra con la que la DIAN autoriza una numeración, y usarla aquí invita exactamente a la confusión que esta sección existe para evitar.

---

## 2. Submódulos

Según [`modules.md` §5](../modules.md).

| Submódulo | Responsabilidad | Entidades principales |
|---|---|---|
| Ventas | Registrar, resolver y consultar lo vendido | `movements`, `movement_types`, `movement_details` |
| Medios de pago | Con qué se pagó, y **dónde no se puede pagar así** | `payment_methods`, `payment_method_exclusions` |

**Por qué los medios de pago son un submódulo y no un catálogo de `SP`.** Los catálogos de `SP` —monedas, países, membresías— los necesita **el sistema entero** para autorizar, validar o mostrar. Un método de pago solo lo necesita quien registra dinero, y [`modules.md` §2.1](../modules.md#21-regla-de-decision) es explícito: si solo lo usa un módulo, es un submódulo suyo.

---

## 3. Dependencias

| Módulo | Tipo | Para qué |
|---|---|---|
| `SP` | Consume | **Usuarios**: que el sujeto del movimiento exista, y en qué estado está (`RN-MV-026`) |
| `SP` | Consume | **La estructura comercial**: de qué vendedor cuelga quien compra (`user_supervisors`). Desde el 16-09-2026 **quien no cuelga de nadie se vende a sí mismo** (`RN-MV-003`): la lectura puede no devolver superior, y la venta lleva vendedor igual |
| `SP` | Consume | **Monedas**: la del importe, con sus decimales |
| `SP` | Consume | **Países**: en cuáles no vale cada método de pago (`RN-MV-019`). Es una **clave foránea y no una interfaz publicada**, y ahí está la diferencia con las tres de arriba: `payment_method_exclusions` es una tabla de `MV` que apunta a `countries`, no una lectura que `MV` le haga a `SP` |
| `SP` | **Escribe** | **Conceder el nivel comprado** cuando la venta se confirma — ver **D-26** |
| `PM` | Consume | **Productos**: precio, moneda, vigencia en días y membresía destino |
| `PM` | Consume | **La oferta de quien compra** (`RF-PM-007`), que es lo que este módulo valida en `RN-MV-007` |
| `CM` | — | **No lo consume `MV`: es `CM` quien consumirá a `MV`** cuando exista la liquidación |

La dependencia es **acíclica**: `MV` → `PM` → `SP`, y `MV` → `SP`.

!!! danger "D-26 — la primera escritura entre módulos, y hay que decidirla antes del `plan.md`"

    Las interfaces publicadas entre módulos hasta hoy son **de solo lectura**, y [`architecture.md` §15.2](../architecture.md) lo declara como norma: se devuelven **modelos de lectura y nunca entidades**, precisamente para no dar **con qué escribir**.

    **Aquí hace falta escribir.** Una venta confirmada de un upgrade tiene que conceder el nivel, y `user_memberships` es de `SP`. Las tres salidas son distintas y ninguna es obviamente mejor:

    | Salida | Qué implica |
    |---|---|
    | `SP` publica una **operación de aplicación** —«conceder el nivel comprado»— que `MV` invoca, síncrona y en la misma transacción | Mantiene la regla dentro de `SP`, que es donde vive. Es lo que [`requirements/pm.md` §1.4](pm.md) ya anticipó: «obliga a que `SP` **publique** esa escritura como interfaz de aplicación, con sus reglas intactas» |
    | `MV` **emite un evento** y `SP` reacciona | Desacopla, y a cambio conceder el nivel deja de ser inmediato: aparece la pregunta de qué pasa si nadie atiende el evento, y **una cuenta que pagó y no subió es la avería que nadie reporta** |
    | `SP` **consulta** las ventas para decidir | Invierte la dirección y **abre el ciclo** `SP` → `MV` → `SP`. Descartada de entrada |

    **La primera es la que sigue el precedente escrito y es la que este documento recomienda.** Queda como decisión abierta porque fija cómo se escribirá entre módulos **para siempre**, y esa forma no la debe fijar un requerimiento de paso. Se retiró junto con el módulo anterior y **vuelve a abrirse con el mismo número, porque es la misma pregunta**.

!!! warning "Este módulo no se puede construir antes que `RF-SP-045`"

    La venta necesita **un cliente que cuelgue de un vendedor** y **el estado `FTD_PENDIENTE`**, y las dos cosas las crea `RF-SP-045`, que hoy está en `Tasks en revisión` y **sin una línea de código**. Sin él, `RN-MV-003` no tiene de dónde sacar el vendedor y `RN-MV-008` no tiene qué rechazar.

    No bloquea escribir las tripletas de este módulo; bloquea implementarlas.

---

## 4. Requerimientos funcionales

### 4.1 Resumen

| ID | Nombre | Submódulo | Permiso |
|---|---|---|---|
| `RF-MV-001` | Registrar una venta | Ventas | `movements:create` |
| `RF-MV-002` | Comprar un producto para uno mismo | Ventas | Autenticado |
| `RF-MV-003` | Confirmar una venta pendiente | Ventas | `movements:confirm` |
| `RF-MV-004` | Rechazar una venta pendiente | Ventas | `movements:confirm` |
| `RF-MV-005` | Anular una venta pendiente | Ventas | `movements:void` |
| `RF-MV-006` | Consultar ventas | Ventas | `movements:read` |
| `RF-MV-007` | Consultar el detalle de una venta, con su comprobante | Ventas | `movements:read` |
| `RF-MV-008` | Consultar los movimientos propios | Ventas | Autenticado |
| `RF-MV-009` | Consultar los métodos de pago | Medios de pago | **Ninguno: es público** |
| `RF-MV-010` | Autorizar la entrega de lo comprado con implementación manual | Ventas | `movements:implement` |
| `RF-MV-011` | Comprar un producto por el hotlink de un vendedor | Ventas | Autenticado |

**Registrar y comprar son dos requerimientos y no uno**, y eso **se aparta del precedente** que `PM` y `CM` fijaron —«el alta es una, no dos»—. La razón por la que aquí no aplica no es el contenido de la venta sino **quién la pide y por dónde entra**: una la origina un funcionario sobre la cuenta de otro y exige `movements:create`; la otra la origina el interesado sobre la suya y no exige permiso ninguno, como `RF-SP-039` y `RF-PM-007`. Fundirlas daría un endpoint con **dos modelos de seguridad**, que es donde se cuela el que sobra.

**Y las dos producen exactamente la misma venta**: mismo tipo, mismo estado inicial, mismas reglas, mismo vendedor congelado **en cada línea**. Lo único que cambia es quién puede llamar y sobre quién.

**Confirmar y rechazar comparten permiso y no requerimiento.** Son las dos salidas de la misma revisión —entró el dinero o no entró—, de modo que separar los permisos obligaría a conceder siempre los dos a la misma persona. Son dos requerimientos porque son **dos operaciones con dos resultados distintos**, y porque el día que exista la pasarela una la escribirá un webhook y la otra también, pero por caminos que no se parecen.

**Anular no es rechazar, y la diferencia es de negocio.** Una venta **rechazada** es un cobro que se intentó y no entró; una **anulada** es una venta que no debía existir —se registró por error, o al cliente equivocado—. Fundirlas ahorraría un estado y borraría el único número que responde **cuánto se intenta cobrar y no entra**.

**Anular solo se admite mientras la venta esté pendiente** (`RN-MV-005`). Una venta confirmada ya concedió un nivel, y retirarlo es **una operación distinta que no existe todavía** (§5.3).

**No hay requerimiento para editar una venta**, y no es un olvido: `RN-MV-001` lo prohíbe. Corregirse, se corrige anulando y registrando otra.

**Mientras no exista la pasarela, confirma una persona.** `RF-MV-003` es hoy un acto humano: alguien mira el comprobante bancario y dice que el dinero entró. Queda escrito lo que eso significa —**el sistema le cree a una persona lo que mañana le creerá a la pasarela**— y también que el camino no cambia cuando llegue: la venta ya nace `PENDIENTE`, que es justo el estado que una integración necesita y que no habría si la venta naciera confirmada.

**`RF-MV-010` nace el 07-09-2026 y no lo pide este módulo: lo pide el catálogo.** `RN-PM-020` deja que un producto declare que lo suyo **no se entrega solo**, y esa declaración necesita a alguien que lo entregue: sin este requerimiento, un producto manual sería un producto que se cobra y **no se aplica nunca**. Se registra **sin tripleta y sin código**, como `RF-MV-004` a `RF-MV-007`, y **depende de `RF-MV-003`** — no se puede autorizar la entrega de una venta que todavía no se ha confirmado.

**`RF-MV-009` es público desde el 09-09-2026, y es el único requerimiento de este módulo que no exige ni siquiera estar autenticado** (`RN-MV-024`). No lo pide `MV`: lo pide el formulario de registro por enlace (`RF-SP-045`), que elige con qué se paga **antes de que exista la cuenta** — y hasta hoy el selector de pago no tenía de dónde sacar las opciones. Es el mismo hueco que `RN-SP-041` cerró un día antes con países, tipos de documento y brokers, y **se cierra igual**: solo el `GET`.

**Lo que hace que abrirlo no cueste nada es lo que ya estaba escrito.** La respuesta lleva desde el 09-09-2026 acotada por **dos ejes** —`is_active` y `visibility`— y por `RN-MV-023` lo `INTERNO` no sale bajo ninguna petición: el anónimo recibe **exactamente lo mismo** que recibía el autenticado, que son las opciones de un selector y no identifican a nadie. **Y no deja ningún permiso huérfano**, al revés que los tres catálogos de `SP`: este endpoint nunca exigió ninguno (§6).

**`RF-MV-011` nace el 16-09-2026 y es la compra que cierra el círculo del hotlink.** `RF-PM-008` publica el enlace sin token y `RF-SP-045` registra por él a quien no tiene cuenta; faltaba **quien ya tiene cuenta y compra por el enlace de otro vendedor**. Es `RF-MV-002` con dos diferencias, y solo dos: el producto llega **por el código del hotlink** —nombre de usuario del vendedor y código del producto, resueltos con las mismas reglas que `RF-PM-008`— y **el vendedor de la línea es el dueño del enlace**, no el superior de quien compra (`RN-MV-003` enmendada, `RN-MV-025`). Y deja una huella que `RF-MV-002` no deja: **el vínculo** entre el cliente y ese vendedor (`RN-SP-049`), que es lo que permite que un cliente tenga varios vendedores sin tener varios superiores.

#### `RF-MV-011` — Comprar un producto por el hotlink de un vendedor

| Campo | Valor |
|---|---|
| Objetivo | Que un cliente **con cuenta** compre el producto que le llegó por el enlace de un vendedor, y que **ese** vendedor cobre la venta aunque no sea su agente |
| Actor | Cualquier persona autenticada que pueda comprar (las mismas condiciones que `RF-MV-002`) |
| Permiso requerido | **Autenticado**, sin permiso: es una compra propia |
| Prioridad | Alta |
| Reglas aplicables | `RN-MV-001` a `RN-MV-016`, `RN-MV-022`, `RN-MV-025`, `RN-MV-026`; `RN-PM-021`, `RN-PM-022`; `RN-SP-049` |
| Depende de | `RF-MV-002`, `RF-PM-008`, `RF-SP-045` |
| Tripleta | `docs/specs/mv/011-comprar-por-hotlink/` |
| Estado | **Pendiente** — registrado el 16-09-2026 |

**Entra por `POST /api/v1/hotlinks/{username}/{code}/purchases`**, con el método de pago en el cuerpo, y produce **la misma venta** que `RF-MV-002`: tipo `VENTA`, estado `PENDIENTE`, una línea con el precio y la vigencia copiados, y las mismas validaciones sobre el producto y sobre quien compra. Lo que resuelve antes es **el enlace**: el vendedor por `PublicSellerLookup` y el producto por su código, **publicado por hotlink** (`RN-PM-021`), y todo lo que no procede responde el mismo `404` del hotlink. Y lo que hace después, en la misma transacción: **el vínculo** cliente-vendedor si no existía (`RN-SP-049`), con `first_movement_id` apuntando a esta venta.

**El vendedor de la línea es el dueño del enlace, siempre.** También cuando quien compra es cliente de otro agente, y también cuando el dueño del enlace **es** su agente: en ese caso el resultado coincide con `RF-MV-002` y el vínculo ya existía. Lo que no ocurre nunca es que la compra por hotlink **cambie el principal**.

**Comprarse a uno mismo por el propio hotlink se rechaza**: un vendedor no es su propio cliente. Y la compra por el hotlink de un **paquete** (`RF-PM-026`) es otro requerimiento, que llegará con la compra de paquetes.

### 4.2 Lo que viene después, y lo que ya se decidió de ello

| Etapa | Qué trae | Qué queda ya decidido |
|---|---|---|
| **2 — Depósitos y FTD** | El dinero que entra a nombre de un cliente, y el primero de todos, que **habilita su cuenta** | Es lo único que saca a una cuenta de `FTD_PENDIENTE` (`RN-SP-026`). Hasta que exista, ese estado lo mueve un actor a mano con `RF-SP-028` |
| **3 — Puntos** | Comprarlos y pagar con ellos: valor almacenado, con su tasa y su saldo | **Su saldo se deriva del libro y no se guarda.** Y **pagar con puntos no mueve caja aunque la venta valga lo mismo**, que es la costura que obliga a distinguir *cuánto vale* de *cuánto entró* |
| **4 — Pasarela** | El cobro de verdad, y **conservar lo que la pasarela notifica tal como lo dice** | La notificación **se guarda antes de interpretarse** y la respuesta se da **antes** de trabajar; la idempotencia va **en el esquema** y no en el caso de uso, porque toda pasarela reentrega y la doble entrega no es el caso raro: es el normal |
| **5 — Comisiones** | El devengo de toda la cadena al confirmar una venta, y su pago | **La base es el valor de cada línea**, no el total de la venta (decisión del responsable, 02-09-2026): `CM` resuelve la tasa por **(rol o persona, producto, fecha)**, de modo que una venta de tres productos con una cadena de tres niveles produce **nueve devengos**. Es más trabajo, y es lo único que impide que dos productos con tarifas distintas se promedien. El **override** —que comisione toda la cadena y no solo quien vendió— ya vive en `CM` como `RN-CM-011` |
| **6 — Retiros y balances** | Salidas de dinero y saldo disponible | Una salida tiene **aprobación**, **saldo** y un perfil de riesgo propio. Colgarla de aquí ahora la haría parecer un depósito con el signo cambiado, que es exactamente lo que no es |

**Ninguna de estas etapas añade una tabla nueva a las cuatro de §7 salvo la 4 y la 6.** Los depósitos y los puntos son **tipos de movimiento**, que es para lo que existe `movement_types`.

---

## 5. Reglas de negocio

### 5.1 Catálogo

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-MV-001` | La venta **no se edita y no se borra**; solo avanza su estado | Siempre | Ningún importe, cantidad, producto ni participante cambia después de creada, y **no hay eliminación — ni lógica ni física**. Lo único mutable es `status`, y solo hacia adelante (`RN-MV-005`). **Por eso la tabla no lleva `updated_at` ni `deleted_at`**: una columna que promete una edición que ninguna operación debe hacer no falla — invita | **Crítica** |
| `RN-MV-002` | Se copia **lo que puede cambiar**; lo inmutable se referencia | Al registrar | **Precio unitario y vigencia en días se guardan en la línea**, porque `RF-PM-004` corrige el precio y `RN-PM-015` declara la vigencia del producto. La **membresía destino no se copia**: `RF-PM-004` **rechaza cambiarla**, de modo que leerla del producto da siempre el mismo valor y copiarla solo añadiría un sitio donde el dato pudiera discrepar de sí mismo. Nada se relee del catálogo después | **Crítica** |
| `RN-MV-003` | El vendedor **es de la línea**, sale de quien compra y se congela; en una venta **siempre lo hay** | Al registrar | No se teclea: **por la tienda y por registro de un funcionario** (`RF-MV-001`, `RF-MV-002`) se toma el **superior vigente de quien compra** en `user_supervisors` —para un cliente, su agente **principal**—; **por el hotlink** (`RF-MV-011`) se toma **el dueño del enlace** (`RN-MV-025`, 16-09-2026). **Desde el 16-09-2026 se guarda en cada línea** (`movement_details.seller_id`) **y no en la cabecera**, por decisión del responsable del proyecto: es en la línea donde `CM` sabrá a quién crearle la comisión (§4.2, etapa 5 — la base es el valor de cada línea), y un movimiento que no sea una venta —un depósito, una comisión— **no tiene vendedor** y no puede llevar una columna que lo prometa. Cada línea declara el suyo y **el modelo no obliga a que coincidan**: hoy las tres entradas ponen el mismo en todas las líneas de una venta, y el día que un carrito mezcle productos de enlaces distintos no habrá que tocar el esquema. Reasignar al vendedor mañana **no puede cambiar quién ganó por lo vendido hoy**. **Quién registró la operación no se guarda aquí**: va al registro de auditoría, y no cobra nada — sin esa separación, el día que alguien de oficina registre la venta de un agente la cadena de comisiones arranca en la persona equivocada. **Y en una venta el vendedor es obligatorio, sin excepción**: quien compra sin colgar de nadie —la cúspide de la fuerza comercial, `RN-SP-019`— **se vende a sí mismo**, con `seller_id = user_id` en cada una de sus líneas. Desaparece la «venta sin atribución» del 04-09-2026; lo que hace `CM` con una autoventa lo decide `CM`. La columna admite nulo **solo** por los tipos de movimiento que no venden nada, y la obligatoriedad en `VENTA` **vive en el caso de uso**: un `CHECK` no consulta `movement_types` | **Crítica** |
| `RN-MV-004` | Solo una venta **confirmada** produce efectos | Siempre | Una venta `PENDIENTE` no concede ningún nivel, no habilita ninguna cuenta y no comisiona. Sin esta regla, **iniciar un cobro concede lo que se está comprando**, y no hace falta mala fe: basta con que el pago no llegue | **Crítica** |
| `RN-MV-005` | Los estados y sus transiciones son **cuatro y cerradas** | Al resolver | `PENDIENTE` → `CONFIRMADA`, `RECHAZADA` o `ANULADA`. **De `CONFIRMADA` no se sale**, porque ya concedió lo comprado y deshacerlo es una operación que no existe (§5.3). `RECHAZADA` y `ANULADA` son finales. Se declara en el esquema como `CHECK`, y la transición en el caso de uso | **Crítica** |
| `RN-MV-006` | **No se baja de nivel**, y renovar el mismo **sí** se admite | Al registrar una venta con upgrade | Comprar una membresía **inferior** a la vigente **se rechaza al registrar**, antes de cobrar nada: cobrar por algo que quita es el reclamo que ninguna prueba de camino feliz encuentra. **Comprar la MISMA se admite desde el 07-09-2026**: es una **renovación**, y lo que se paga ahí es **tiempo y no nivel** (`requirements/pm.md` §5.2.3). La comparación es sobre el **nivel** de la cadena, donde **superior significa número menor** | **Crítica** |
| `RN-MV-007` | El producto tiene que estar **en la oferta de quien compra** | Al registrar, por las dos entradas | Se valida contra lo que `RF-PM-007` ya resuelve —lo activo, lo que corresponde al nivel de esa persona—, **compre el cliente o registre el funcionario**. Se comprueba **otra vez al registrar** aunque la interfaz ya la haya pintado, porque entre mirar la oferta y pagarla el producto pudo retirarse. Que el funcionario tampoco pueda saltársela es deliberado: si hay un acuerdo comercial que la oferta no contempla, la salida es el catálogo, no una excepción invisible | Alta |
| `RN-MV-008` | A una cuenta en `FTD_PENDIENTE` **no se le vende**, salvo la venta que la creó | Al registrar | Esa cuenta **autentica y no opera** (`RN-SP-026`): venderle antes del depósito sería saltarse justo lo que ese estado existe para exigir. Vale para las dos entradas. **Con una excepción, declarada el 09-09-2026**: la venta del **alta por enlace** (`RN-SP-043`), que es la que **pone** a la cuenta en ese estado y no una compra posterior desde él. Sin ella, el registro gratuito sería imposible — la venta que lo origina se rechazaría a sí misma. La regla no se relaja, **se acota**: lo que prohíbe es que una cuenta a la espera de su depósito **siga comprando**, y la exención es de paquete, de modo que solo la alcanza el adaptador del registro; ni el endpoint de `RF-MV-001` ni ningún otro módulo pueden invocarla | **Crítica** |
| `RN-MV-009` | Una venta lleva **al menos una línea** | Al registrar | Lo que se vendió vive en `movement_details`, y una venta sin líneas no dice qué se vendió. **No se puede declarar en el esquema**, porque un `CHECK` no cuenta filas de otra tabla: vive en el caso de uso | Alta |
| `RN-MV-010` | **Como mucho un upgrade** por venta | Al registrar | Bots, los que se quiera; **upgrades, uno**. Dos en la misma venta son **dos cambios de nivel en una sola operación**, y no hay forma no arbitraria de decidir en cuál queda la persona ni de justificar cobrarle los dos | **Crítica** |
| `RN-MV-011` | El mismo producto **no se repite** en una venta | Al registrar | Dos líneas del mismo producto son una con el doble de cantidad. Admitirlas obligaría a sumar para responder «¿cuántos compró?», y la respuesta dependería de que nadie olvidara hacerlo. Se declara en el esquema | Media |
| `RN-MV-012` | Todas las líneas comparten la **moneda** de la cabecera | Al registrar | La moneda es de la venta y no de la línea: un cobro se hace en **una** moneda. **El motivo de esta regla cambió el 07-09-2026 y la regla no**: decía que un producto en otra moneda se rechaza porque «no hay un total que calcular sin una tasa de cambio, y este sistema no tiene ninguna», y desde hoy `SP` **sí las tiene** (`RF-SP-047` a `RF-SP-050`). La venta sigue en una sola moneda **por decisión y no por ausencia** — convertir obligaría a elegir qué tasa aplica y a **congelarla en la línea** como el precio (`RN-MV-002`), y eso es un cambio que nadie ha pedido | Alta |
| `RN-MV-013` | El total es la **suma de las líneas**, y se congela | Al registrar | `total_amount` se calcula **una vez** y no se recalcula al leer: es el número que aparece en el comprobante, y recalcularlo haría que un cambio de redondeo reescribiera documentos ya entregados. **`payable_amount = total_amount − discount_amount`**, y esa igualdad **sí** se declara en el esquema porque cruza tres columnas de la misma fila | Alta |
| `RN-MV-014` | El importe respeta los **decimales de su moneda** | Al registrar | Igual que `RN-PM-007` para el precio del catálogo, y por lo mismo: la escala la decide la moneda y no la columna | Media |
| `RN-MV-015` | La cantidad es **uno** en los upgrades | Al registrar | Un upgrade con cantidad dos no significa nada: no se sube dos veces al mismo nivel. Los bots admiten más de uno. **No cabe en un `CHECK`** —no consulta `products`— y vive en el caso de uso | Alta |
| `RN-MV-016` | Toda venta lleva un **código legible**, y su día sale del hecho | Al registrar | `<prefijo del tipo>-<AAAAMMDD>-<seis aleatorios>`, único (§7.2.1). El día se corta en la **zona de la operación —`America/Bogota`—** y no en UTC ([`architecture.md` §15.1.1](../architecture.md)): con UTC, una venta de las 23:30 en Bogotá llevaría el día siguiente. La fecha es la de **`occurred_at`**. El aleatorio usa el alfabeto de 32 de Crockford —**sin `I`, `L`, `O` ni `U`**— porque este código se dicta por teléfono y se teclea, y `O` contra `0` es el error que se comete. **No sustituye al identificador interno**: `id` sigue siendo el UUID | Alta |
| `RN-MV-017` | El catálogo de tipos **no se edita por API** y no se borra | Siempre | Se siembra por migración, como el de monedas. El motivo es más fuerte que allí: **el caso de uso decide según el tipo**, de modo que uno añadido en caliente sería un tipo que ningún código sabe procesar — el sistema aceptaría el movimiento y no haría nada con él. Es el defecto que no falla: promete. Y **no lleva borrado**, porque un tipo eliminado deja movimientos históricos apuntando a algo que ya no significa nada | **Crítica** |
| `RN-MV-018` | Un método de pago desactivado **no invalida** lo que se pagó con él | Siempre | La validación es del **momento del registro**, no permanente. Es el mismo criterio de `RN-PM-008` con las monedas | Media |
| `RN-MV-019` | Un método de pago puede estar **excluido en países concretos**, y esa exclusión **se publica, no se comprueba** | Al consultar los métodos de pago (`RF-MV-009`) | No todos los medios operan en todas partes: `PSE` es colombiano y no significa nada en México. El sistema **declara dónde NO vale cada método** y lo devuelve junto al catálogo; **quién aplica ese filtro es el cliente que lo consume**. Un método **sin exclusiones vale en todos los países**, que es el estado de los tres sembrados hoy. **Esta regla no interviene al registrar una venta** — ver §5.3 | Media |
| `RN-MV-020` | Una venta **confirmada** que lleva un upgrade concede la membresía comprada **si el producto es de implementación automática** | Al confirmar el pago **completo** de una venta (`RF-MV-003`) | Es la contraparte de `RN-MV-004`: si registrar no concede nada, **confirmar tiene que conceder**, o confirmar no significa nada. La membresía es la **destino del producto de la línea de upgrade** —a lo sumo una, por `RN-MV-010`— y la vigencia sale de los `validity_days` **copiados en la línea**, contados desde la confirmación. **Desde el 07-09-2026 no concede siempre**: solo cuando el producto declara `AUTOMATICA` (`RN-PM-020`). Escribe en `user_memberships`, que es de `SP`: es la primera escritura entre módulos y la gobierna **D-26**. Ver §5.4 | **Crítica** |
| `RN-MV-022` | **Importe cero y pago gratuito son lo mismo, en los dos sentidos** | Al registrar una venta | Una venta de importe **cero** se registra con el método `GRATIS`, y el método `GRATIS` **solo** vale para importe cero. Las dos mitades se rechazan con `409`. **La primera cierra la mentira** que `RN-PM-006` abrió el 08-09-2026 —una compra gratuita declarando tarjeta—; **la segunda cierra la contraria**, que es peor: una venta cobrada declarando que fue gratis dejaría a `CM` comisionando sobre un cobro que sí ocurrió y al padrón diciendo que no. **Y tiene una consecuencia sobre el contrato**: `paymentMethodId` deja de ser obligatorio y pasa a ser **condicional en los dos sentidos** —prohibido si el importe es cero, exigido si no—, con la forma que `RN-SP-019` ya usa para el superior comercial | **Crítica** |
| `RN-MV-023` | **Lo `INTERNO` no se ofrece nunca** | Al consultar los métodos de pago (`RF-MV-009`) | Un método con `visibility = INTERNO` **no aparece en el catálogo bajo ninguna petición**: no hay parámetro que lo traiga. Es la asimetría deliberada con `is_active`, que sí se podría exponer bajo petición, y con `RN-MV-019`, donde la exclusión por país **se publica y el cliente filtra**. Aquí el cliente no filtra porque **no lo ve**: lo elige el sistema, no una persona, y publicarlo solo daría ocasión de ofrecerlo por error. **El precio se acepta y queda escrito**: quien depure una venta gratuita verá un identificador que este catálogo no resuelve | Alta |
| `RN-MV-021` | Lo comprado con implementación **manual** queda **pendiente de autorización**, y el pago se confirma igual | Al confirmar (`RF-MV-003`) y al autorizar (`RF-MV-010`) | El producto que declara `MANUAL` (`RN-PM-020`) **no se entrega al confirmar el pago**: espera a que un funcionario lo autorice. **Lo que queda pendiente es la entrega y no el cobro**, de modo que la venta pasa a `CONFIRMADA` con normalidad y `RN-MV-005` sigue intacta — **el estado de la autorización es de la línea, no de la venta**. El valor que manda es el que se copió en la línea al registrar (`RN-MV-002`), no el que el catálogo tenga el día de la confirmación | **Crítica** |
| `RN-MV-024` | **El catálogo de métodos de pago se consulta sin iniciar sesión** | Al consultar los métodos de pago (`RF-MV-009`) | El `GET` es **público** (09-09-2026): quien rellena el formulario de registro por enlace (`RF-SP-045`) elige con qué paga **antes de tener cuenta**, y sin esto el selector de pago no tiene de dónde sacar las opciones. Es el mismo hueco que `RN-SP-041` cerró un día antes con países, tipos de documento y brokers. **Lo que sale sigue acotado por los dos ejes de siempre** —**activos** (`RN-MV-018`) y de visibilidad **`PUBLICO`** (`RN-MV-023`)—, de modo que abrirlo **no publica nada que un autenticado no viera ya**: el anónimo recibe exactamente la misma respuesta. **No identifica a nadie** —son opciones de un selector— y **no deja ningún permiso huérfano**, al revés que `RN-SP-041`: este endpoint nunca exigió permiso. Solo el `GET`: el día que el catálogo se administre por API, esas escrituras **no** se abren | Alta |
| `RN-MV-025` | **La compra por hotlink se atribuye al dueño del enlace, y lo vincula al cliente** | Al comprar por el hotlink de un vendedor (`RF-MV-011`) | Decisión del responsable del proyecto, 16-09-2026. El `seller_id` **de cada línea** es **quien reparte el enlace**, aunque el comprador tenga otro agente principal: el enlace es la prueba de quién trajo esa venta, y `RN-MV-003` lo congela como cualquier otro. En la misma transacción nace —si no existía— el **vínculo** en `client_sellers` (`RN-SP-049`), con esta venta como `first_movement_id`. **La compra no cambia el principal** ni mueve la estructura comercial: lo único que cambia de manos es esta venta. El vendedor del enlace la ve en sus movimientos propios (`RF-MV-008`); al cliente no lo ve en su equipo. | Alta |
| `RN-MV-026` | **Todo movimiento tiene UN sujeto, y es quien lo protagoniza** | Siempre | Decisión del responsable del proyecto, 16-09-2026. `movements.user_id` es **la persona a cuyo nombre ocurre el hecho**: en una venta, **quien compra**; en un depósito, quien deposita; en una comisión, quien la cobra. Sustituye a `client_id`, que **nombraba un rol que solo la venta tiene** — el sujeto de un depósito no es «cliente» de nadie, y una tabla que llame `client_id` a quien cobra una comisión miente en el nombre. **No es quien ejecutó la operación**: que un funcionario registre la venta de un cliente (`RF-MV-001`) no lo convierte en sujeto de nada, y ese dato va a la auditoría (`RN-MV-003`). **El sistema está hecho para que cada persona sea responsable de lo que compra**, y `user_id` es la columna que lo dice. Es obligatorio en todo tipo: **no existe un movimiento sin sujeto**. Quién vendió cada cosa **no va aquí**: va en la línea, porque puede ser más de uno | **Crítica** |

### 5.2 Por qué las críticas son críticas

**`RN-MV-001` — la venta no se toca.** Es lo que separa un libro de una tabla. Un importe que se puede editar convierte cualquier pregunta sobre el pasado en una conjetura, y el daño no se descubre al editar: se descubre meses después, cuando dos personas miran el mismo número y no coinciden. Es la postura de `RN-PM-010` y `RN-CM-005` llevada un paso más allá — allí la fila permanece aunque se retire; aquí **ni siquiera se retira**.

**`RN-MV-002` y `RN-MV-003` — congelar, no referenciar.** Son la misma regla aplicada a dos cosas distintas, y las dos las **impusieron otros documentos antes de que este módulo existiera**: `pm.md` §1.4 para el precio y la vigencia, y la estructura comercial para el vendedor. El defecto que evitan no falla en el momento: aparece cuando alguien corrige un precio, o reasigna un cliente, y descubre que acaba de cambiar el pasado.

!!! important "El vendedor bajó de la cabecera a la línea el 16-09-2026, y la venta sin vendedor dejó de existir"

    **Dos decisiones del responsable del proyecto, el mismo día, y conviene leerlas juntas porque la segunda solo tiene sentido con la primera.**

    **La primera: `movements` ya no lleva `client_id` ni `seller_id`; lleva `user_id`** (`RN-MV-026`). La cabecera nombraba dos papeles que **solo la venta tiene**, y este módulo se construye por etapas para acabar siendo un libro con depósitos, puntos, comisiones y retiros (§4.2): en un depósito no hay vendedor, y quien cobra una comisión **no es cliente de nadie**. Una columna que se llame `client_id` en esas filas no falla — miente en el nombre, que es el defecto que `payment_methods.visibility` ya corrigió una vez en este documento. `user_id` es **el sujeto**: a nombre de quién ocurre el hecho. Y **el vendedor se va a la línea**, `movement_details.seller_id`, porque es ahí donde se le va a crear la comisión —la base es el valor de cada línea, decidido el 02-09-2026— y porque una línea puede tenerlo y otra no tener nada que ver con él.

    **La segunda: en una venta el vendedor es obligatorio, y quien no cuelga de nadie se vende a sí mismo.** El 04-09-2026 este documento hizo el vendedor opcional para que la cúspide de la fuerza comercial —que por `RN-SP-019` no declara superior— pudiera comprar, y aceptó a cambio que esa venta **no comisionara a nadie**. Hoy se resuelve al revés: esa persona compra **con `seller_id = user_id` en cada línea**. La venta queda atribuida, `CM` tiene de dónde arrancar la cadena, y lo que haga con una autoventa —comisionarla o no— **lo decide `CM`**, que es donde vive la liquidación. Lo que se gana es que **ninguna línea de una venta carece de vendedor**, y lo que se retira es un caso especial que cada consumidor del libro tenía que tratar por su cuenta.

    **Lo que NO cambia.** El vendedor **se sigue deduciendo y no se teclea**: superior vigente por la tienda y por oficina, dueño del enlace por el hotlink. **Sigue congelado**: reasignar mañana no cambia lo de hoy. Y **`RN-SP-027` y `RN-SP-020` siguen igual**: un cliente registrado por enlace sigue exigiendo vendedor porque es una regla sobre cómo se crean los clientes, no sobre quién compra.

    **Y lo que el esquema no puede sostener, dicho de una vez.** `movement_details.seller_id` **admite nulo** porque los tipos de movimiento que vienen no venden nada, y **«obligatorio en `VENTA`» no cabe en un `CHECK`**: decidirlo exige mirar `movement_types`, que es otra tabla. Vive en el caso de uso y en el agregado, como `RN-MV-009` y `RN-MV-015`.

**`RN-MV-004` y `RN-MV-005` — el estado es lo único que se mueve, y no se mueve hacia atrás.** Sin la primera, iniciar un cobro concede lo que se está comprando. Sin la segunda, «confirmada» deja de significar algo: una venta que puede volver a pendiente es una venta cuyo efecto nadie puede dar por firme.

**`RN-MV-020` — y por eso confirmar tiene que conceder.** Es la contraparte de `RN-MV-004`, y las dos solo valen juntas: una dice que registrar no hace nada y la otra dice qué hace confirmar. Con la primera sola, «pendiente» y «confirmada» serían dos palabras para el mismo hecho sin efecto — que es exactamente lo que hoy ocurre, porque `RF-MV-003` no está construido. Se desarrolla en §5.4, **con un caso deliberadamente sin decidir**.

**`RN-MV-021` — y por eso no siempre entrega al confirmar.** Desde el 07-09-2026 lo que decide es **el producto**: `AUTOMATICA` entrega, `MANUAL` espera autorización. Sin esta regla, el catálogo podría declarar que algo exige revisión y este módulo lo entregaría igual — **el defecto no falla, entrega**, con el cobro hecho y sin que nadie lo hubiera aprobado. Y al revés vale lo mismo: tratar todo como manual dejaría sin entregar lo que se vendió como instantáneo, que es un cobro sin contraprestación hasta que alguien se acuerde.

**`RN-MV-006`, `RN-MV-008` y `RN-MV-010` — las tres que protegen a quien paga.** Cobrar un nivel que **baja**, venderle a una cuenta que todavía no opera, o cobrar dos upgrades que no se pueden aplicar los dos. Ninguna de las tres rompe nada técnicamente: las tres producen un cobro que hay que devolver, y devolver dinero en este sistema **es una etapa que ni siquiera existe**.

**La primera perdió una mitad el 07-09-2026, y conviene leer cuál.** Rechazaba el nivel «igual o inferior»; ahora solo el **inferior**. La mitad de «igual» no protegía a nadie: impedía **renovar**, que es pagar por tiempo sobre el nivel que ya se tiene. La que se queda es la que importa — **una venta no baja a nadie de nivel**, y esa sigue siendo una regla de este módulo que no depende de que `PM` siga decidiendo lo mismo en su oferta.

### 5.3 Lo que este módulo NO decide, y por qué

**Cómo se corrige una venta confirmada por error.** `RN-MV-005` cierra la puerta —de `CONFIRMADA` no se sale— y con ello **deja un hueco a la vista**: retirar un nivel ya concedido es una operación que **no existe**, y hay que decidirla aparte. Se elige esto sobre deshacerlo todo automáticamente, que obligaría a quitarle el nivel a quien lleva un mes usándolo. **El precio queda declarado: hoy una venta confirmada por error no se puede corregir por ninguna vía.**

**Quién ve las ventas de quién.** Un director que consulta ventas ¿ve las de su equipo, las de todos, o solo las suyas? Es **alcance de datos**, depende de **D-22** —abierta— y es el mismo aplazamiento que `requirements/cm.md` §5.3 ya hizo. `RF-MV-006` y `RF-MV-007` se especifican con **alcance global explícito** para quien tenga el permiso, y `RF-MV-008` cubre el caso propio sin depender de esa decisión.

**Cómo se escribe en `SP`.** Es **D-26** (§3). Este documento recomienda una salida y no la fija.

**Qué métodos de pago hay.** Se siembran los mínimos por migración y se leen (`RF-MV-009`). **Administrarlos por API sigue quedando para después**, por decisión del responsable del proyecto del 02-09-2026.

**En qué países vale cada método: decidido el 04-09-2026, y NO como se había supuesto.** Aquella nota daba por hecho que restringir por país exigiría antes darle un país a alguien —«hoy nadie tiene país, porque `users` no lo guarda y `countries` no tiene una sola clave foránea entrante»—. **No hace falta**, porque el responsable del proyecto decidió que la restricción **es informativa y no ejecutiva**: el sistema declara dónde no vale cada método y lo publica; **quien filtra es el cliente que consume el catálogo** (`RN-MV-019`).

**La consecuencia hay que leerla dos veces, porque es lo que este módulo NO hace:** `RF-MV-001` y `RF-MV-002` **no comprueban el país al registrar una venta**, y una petición que use un método excluido **se registra con normalidad**. No es un descuido ni una fase pendiente: es lo que significa que la restricción sea del cliente. **Nada impide hoy cobrar con `PSE` fuera de Colombia por API**, y quien quiera que eso se impida tiene que decidir antes de qué país se trata — que es la pregunta que sigue sin respuesta y que esta decisión **aplaza en lugar de resolver**.

**Por qué se acepta.** El caso real es una pantalla que ofrece medios de pago, y ofrecer uno que no va a funcionar es el defecto que se quería quitar. Convertirlo en una validación del servidor exigiría `users.country_id`, tocar `RF-SP-024` y `RF-SP-045`, y decidir qué país tienen las personas que ya existen — todo para cerrar una puerta por la que hoy solo pasa el superadministrador (§6.1).

### 5.4 `RN-MV-020` — qué concede exactamente una venta confirmada

**La regla en una frase:** confirmado el pago **completo** de una venta que lleva un upgrade **de implementación automática**, esa persona **pasa a tener la membresía que compró**. Si la implementación es **manual**, el pago se confirma igual y **lo comprado queda esperando autorización** (`RN-MV-021`).

Es la contraparte exacta de `RN-MV-004`. Si registrar no concede nada, **confirmar tiene que conceder**, o «confirmada» no significa nada. Las dos reglas juntas son las que hacen que el estado de una venta sea el único sitio donde vive esa diferencia.

**Cuál membresía, sin ambigüedad.** La **destino del producto** de la línea de upgrade. Hay **a lo sumo una** por venta, y no por casualidad: `RN-MV-010` lo exige desde el registro precisamente para que este momento no tenga que elegir entre dos. Una venta de solo bots confirma y **no concede ninguna membresía** — no es un caso especial, es el caso común.

**Y no se relee del catálogo.** La membresía destino **no se copia en la línea** (§7.3) porque `RF-PM-004` rechaza cambiarla y `RN-PM-010` garantiza que el producto no desaparece: leerla del producto dentro de tres años da el mismo valor. Lo que **sí** está copiado y **sí** se usa es la **vigencia en días**.

#### La implementación del producto decide si confirmar entrega — 07-09-2026

Hasta hoy esta regla no distinguía: **toda** venta confirmada con un upgrade concedía. Desde el 07-09-2026 el producto declara **cómo se implementa lo que otorga** (`RN-PM-020`), y de ahí salen dos caminos que confirman igual y entregan distinto:

| Implementación del producto | Qué hace confirmar el pago |
|---|---|
| `AUTOMATICA` | Concede la membresía comprada, exactamente como se describe abajo |
| `MANUAL` | **No concede nada.** Lo comprado queda **pendiente de autorización**, y lo entrega `RF-MV-010` cuando un funcionario lo aprueba |

**Lo que queda pendiente es la entrega, no el cobro.** La venta pasa a `CONFIRMADA` con normalidad —el dinero entró, y eso es lo que ese estado significa—, de modo que `RN-MV-005` sigue intacta: de `CONFIRMADA` no se sale, y esta regla no abre ninguna puerta para volver atrás.

!!! important "Y por eso el estado de la autorización es de la LÍNEA, no de la venta"

    Una venta puede llevar varios productos, y **no tienen por qué implementarse igual**: un upgrade automático y dos bots que alguien tiene que activar a mano. Un quinto estado de `movements` —«confirmada a medias»— obligaría a inventar qué significa cuando una mitad se entregó y la otra no, y a decidirlo **una vez para toda la venta**, que es justo lo que el dato no permite.

    Dónde vive exactamente ese estado —una columna en `movement_details`, o una tabla propia con quién autorizó y cuándo— **lo decide `RF-MV-010`**, y este documento no lo fija: es una decisión de diseño con su propia compuerta, y tomarla aquí de paso sería el mismo error que este proyecto ya nombró al escribir tripletas después del código.

!!! danger "La implementación tiene que copiarse en la línea, y HOY NO SE COPIA"

    `RN-MV-002` es explícita: **se copia lo que puede cambiar; lo inmutable se referencia**. La membresía destino se referencia porque `RF-PM-004` rechaza cambiarla; **la implementación se corrige** ([`requirements/pm.md` §5.2.2](pm.md)), de modo que **se copia**, como el precio y la vigencia.

    Sin esa copia, corregir un producto de `AUTOMATICA` a `MANUAL` dejaría **esperando autorización a ventas hechas cuando el producto se entregaba solo**, y el cambio contrario **entregaría sin revisión lo que se vendió con revisión prometida**. Ninguno de los dos falla: los dos entregan mal, y con el cobro ya hecho.

    **`movement_details` no tiene hoy esa columna** (`V54`), y este cambio **no la escribe**: `RF-MV-003` —el único que leería el valor— está en `Pendiente` y bloqueado por **D-26**, de modo que hoy no hay nadie a quien le falte. Lo que queda declarado es que **la copia debe existir antes de que ese requerimiento se construya**, y que quien lo construya la encontrará ausente si no lee esto. Es el mismo hueco que la v0.8.0 de este documento dejó anotado con el código y el nombre del producto, y conviene cerrar los tres a la vez.

**El resto de esta sección describe el camino automático**, que es el que concede.

#### Las tres decisiones que esta regla lleva dentro

**1. La vigencia se cuenta desde la confirmación, no desde la venta.** Los `validity_days` copiados en la línea se suman al instante en que se confirma. Comprar el sábado y confirmar el lunes **no puede comerse dos días** de lo comprado: quien paga treinta días recibe treinta días de uso, y el retraso en comprobar el dinero es del sistema y no suyo. Vigencia nula significa **indefinida** (`RN-PM-015`), y entonces no hay fecha de fin que escribir.

**2. Conceder CIERRA la anterior y abre una fila nueva.** `user_memberships` es un **historial** (`RN-SP-014`, enmendada el 05-09-2026): confirmar no reescribe la membresía que la persona tenía, la **cierra** —`closed_at` en el instante de la confirmación— y **inserta** la comprada, con `started_at` ahí mismo. **Cierra siempre, aunque la anterior ya estuviera vencida**; dejarla abierta dejaría dos filas actuales, que es lo que `uq_user_memberships_abierta` no admite.

**Y `ends_at` de la nueva sale de los `validity_days` copiados en la línea, no de la anterior.** Lo comprado no hereda nada de lo que había: quien tenía `VIP` hasta fin de mes y compra `PLATINO` de treinta días recibe treinta días desde la confirmación, y los que le quedaban de `VIP` no se suman ni se descuentan. Que se pierdan **está escrito y no resuelto**: `closed_at` deja constancia de cuántos días pagados no se usaron, y qué hacer con ellos —nada, prorratear, extender— no es una decisión de este módulo.

!!! success "Esto era una deuda hasta el 05-09-2026, y ya no lo es"

    La versión anterior de esta decisión decía que conceder **sustituía la fila** y que **el nivel que alguien tuvo antes no se podía reconstruir desde `SP`** — que quien lo necesitara «tendría que leerlo de las ventas confirmadas». Era cierto y era un coste aceptado, hermano del que `RN-CM-008` obliga a pagar copiando el porcentaje que aplicó cada liquidación.

    **Dejó de serlo.** `SP` guarda ahora el periodo completo de cada membresía, de modo que «en qué nivel estaba esta persona el 12 de marzo» se responde en `SP` y **no** reconstruyéndolo desde `MV`. Queda anotado porque invierte la dirección de una dependencia: era `MV` quien tenía que sostener la memoria de `SP`, y ya no.

**3. Confirmar dos veces no concede dos veces.** Lo sostiene `RN-MV-005` —de `CONFIRMADA` no se sale—, y eso obliga a que la transición sea **atómica**: no un `SELECT` y luego un `UPDATE`, sino una escritura condicionada al estado anterior. Importa más de lo que parece porque **el disparador será una pasarela**, y una pasarela reintenta: sin esa atomicidad, dos webhooks del mismo pago conceden el nivel dos veces y devengan la comisión dos veces.

!!! danger "Un caso sin decidir: confirmar podría BAJAR de nivel a alguien"

    `RN-MV-006` comprueba **al registrar** que el upgrade sube. **Entre registrar y confirmar puede pasar cualquier cosa**, y una de ellas es que la persona haya subido más por otra vía: compró `BECA → VIP`, y antes de confirmarse le concedieron `PLATINO`. Confirmar la primera venta la **devolvería a `VIP`**.

    No es hipotético: `RN-MV-010` limita a un upgrade **por venta**, no por persona, y `spec.md` §13 de `RF-MV-001` ya declara que **dos ventas simultáneas del mismo upgrade se registran las dos** y que el conflicto lo resuelve quien confirma.

    **Queda sin decidir a propósito**, porque las dos salidas son razonables y la elección no es técnica: **conceder siempre lo comprado** —quien pagó recibe lo que pagó, aunque le baje— o **no bajar nunca** —la venta confirma, cobra y no toca el nivel, con lo que alguien paga por algo que no recibe—. La segunda es la segura y **deja un problema peor sin resolver**: una venta cobrada que no entregó nada, y `RN-MV-005` impide corregirla (§5.3).

    **El historial no decide esto, pero cambia lo que cuesta equivocarse** (05-09-2026). Antes, conceder lo comprado y bajar a alguien de `PLATINO` a `VIP` **borraba el `PLATINO`**: no quedaba dónde leer que lo tuvo. Ahora queda su fila cerrada, con el periodo que alcanzó a durar, de modo que la salida «conceder siempre lo comprado» pasa a ser **reversible a mano** en lugar de destructiva. Sigue sin ser gratis y **sigue sin estar decidida**: bajar de nivel a quien acaba de subir es un hecho de negocio y no un accidente de esquema.

    Lo que **no** puede pasar es que se decida por omisión al escribir el código.

---

## 6. Permisos

| Código | Recurso | Acción | Para qué |
|---|---|---|---|
| `movements:read` | `movements` | `read` | Consultar ventas y su detalle |
| `movements:create` | `movements` | `create` | Registrar una venta a nombre de otra persona |
| `movements:confirm` | `movements` | `confirm` | Dar por pagada, o por no pagada, una venta pendiente |
| `movements:void` | `movements` | `void` | Anular una venta que no debía existir |
| `movements:implement` | `movements` | `implement` | Autorizar la entrega de lo comprado cuando el producto es de implementación manual (`RF-MV-010`). **Declarado y SIN SEMBRAR** |

**`confirm` y `void` no reutilizan `update`**, y esa es la única decisión de esta sección. Una venta **no se actualiza nunca** (`RN-MV-001`), de modo que un permiso llamado `movements:update` prometería algo que no existe. Y son dos permisos y no uno porque confirmar es operación de caja diaria mientras que anular **borra del embudo** una venta que alguien registró: quien concilia pagos no tiene por qué poder hacer desaparecer ventas ajenas.

**Comprar y consultar lo propio no llevan permiso**, por lo mismo que `RF-SP-039`: exigirlo obligaría a concedérselo a todos los clientes, que es la forma de que un permiso deje de significar nada. `RF-PM-007` seguía el mismo criterio hasta el 02-09-2026, cuando pasó a exigir `products:sale` — un permiso propio de esa vista y no de administración, que se concede por rol como cualquier otro y no cambia el argumento de esta sección.

**Y desde el 09-09-2026 `RF-MV-009` no lleva ni permiso ni sesión** (`RN-MV-024`): su `GET` es público. **No sobra ningún permiso por ello**, y esa es la diferencia con los tres catálogos que `SP` abrió el día antes —`countries:read`, `document-types:read` y `brokers:read` quedaron sembrados sin endpoint que los exija ([`security.md` §6](../security.md))—. Aquí no ocurre porque el catálogo de métodos de pago **nunca exigió uno**: los cuatro `movements:` gobiernan ventas, y ninguno gobernaba esta lectura.

**`movements:implement` está declarado y no existe todavía en la base de datos**, y conviene que se lea así en lugar de aparecer como un olvido. `V51` sembró **cuatro** permisos y este es el quinto: entrará con la migración de `RF-MV-010`, que está en `Pendiente`, y por eso **no figura en el catálogo de [`security.md` §4.4](../security.md#44-catalogo-de-permisos)** — esa lista enumera lo que está sembrado, y adelantarlo ahí haría que su recuento dejara de cuadrar con `permissions`.

**Y es un permiso propio, no `movements:confirm`.** Confirmar responde «¿entró el dinero?»; autorizar responde «¿se le entrega?». Son dos decisiones distintas, de dos personas que no tienen por qué ser la misma: quien concilia pagos mira un extracto bancario, y quien autoriza la entrega mira si esa cuenta debe recibir lo que compró. Reutilizar el de confirmar habría hecho que conceder lo primero concediera lo segundo sin que nadie lo decidiera.

### 6.1 Los cuatro se sembraron el 02-09-2026, y solo para `SUPERADMIN`

`V51__seed_movements_permissions.sql` los estrena **antes que el resto del módulo**: la tarea que los siembra no depende de ninguna otra, y un permiso sin endpoint que lo exija no rompe nada — el catálogo es datos, y su único efecto es poder concederse—. No crea ninguna de las cuatro tablas de §7.

**El número de esta migración cambió dos veces desde que se aplicó**, la segunda el 03-09-2026 al fusionarse en `develop` un permiso de otro módulo (`products:sale`, PR #56) que tomó un número que esta rama ya usaba para otra cosa: como esta rama no estaba en `origin`, fue la que cedió el puesto. El motivo completo de las dos veces, con los números exactos de cada paso, vive en el encabezado del propio archivo — no se repite aquí para no tener dos versiones de la misma historia que puedan divergir.

!!! danger "`ADMIN` no los recibe, y eso deja hoy el módulo sin vendedores que lo operen"

    [`security.md` §4.4](../security.md) obliga a que toda migración que siembre permisos los asocie a `SUPERADMIN` **y a `ADMIN`**. Esta se aparta, por **decisión del responsable del proyecto del 02-09-2026**, y §4.4 recoge la excepción con su coste.

    El coste hay que leerlo entero, porque **no es el de las dos reservas que ya existían**. `audit:read-security` y `currencies:update` cubren operaciones que de verdad solo hace el superadministrador. Estas cuatro cubren el trabajo de la fuerza comercial, y `V7` la cuelga entera de `ADMIN`:

    ```
    SUPERADMIN → ADMIN → MANAGER → DIRECTOR → AGENTE
    ```

    `RN-SEG-003` exige que los permisos de un rol sean subconjunto de los de su padre. Con `ADMIN` fuera, **ningún rol de esa cadena podrá declarar `movements:create`** — no es que `ADMIN` no pueda delegarlo: es que no hay a quién delegárselo—. Mientras la reserva siga en pie, `RF-MV-001`, `RF-MV-003`, `RF-MV-004`, `RF-MV-005`, `RF-MV-006` y `RF-MV-007` los ejecuta el superadministrador y nadie más.

    **No bloquea construir el módulo** y no invalida ninguna de sus reglas: `RF-MV-002`, `RF-MV-008` y `RF-MV-009` no llevan permiso y quedan intactos. Bloquea **operarlo**, y revertirlo es una migración de dos `INSERT` sobre `role_permissions`.

---

## 7. Modelo de datos

### 7.1 `movements`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `movement_type_id` | `uuid` | No | `movement_types` |
| `user_id` | `uuid` | No | `users` |
| `payment_method_id` | `uuid` | No | `payment_methods` |
| `currency_id` | `uuid` | No | `currencies` |
| `code` | `varchar(30)` | No | — |
| `status` | `varchar(20)` | No | — |
| `total_amount` | `numeric(14,2)` | No | — |
| `discount_amount` | `numeric(14,2)` | No | — |
| `payable_amount` | `numeric(14,2)` | No | — |
| `occurred_at` | `timestamptz` | No | — |
| `confirmed_at` | `timestamptz` | **Sí** | — |
| `created_at` | `timestamptz` | No | — |
| `reference_id` | `uuid` | **Sí** | **Pendiente de definir** |

**No lleva `updated_at` ni `deleted_at`** (`RN-MV-001`). Es la diferencia con todas las demás tablas del sistema y es deliberada: aquí no hay nada que actualizar y nada que retirar.

**`user_id` es el sujeto, y desde el 16-09-2026 es la única persona de la cabecera** (`RN-MV-026`). Sustituye a `client_id` y a `seller_id`, que existieron de `V54` (hoy `V7`) al 16-09-2026 y se retiran con `V12`. **El vendedor no desaparece: baja a la línea** (§7.3). El nombre es deliberadamente neutro porque esta tabla es el libro de todos los tipos de §4.2, y en la mayoría de ellos el sujeto no es un cliente. **Toda venta de este módulo es una compra de alguien**: no hay ventas «de la casa» sin sujeto, y la columna no admite nulo.

**`reference_id` se reserva sin FK todavía.** La idea, a falta de que se termine de definir, es que quede disponible para que otro registro —una cotización, hoy sin tabla ni requerimiento propio— apunte a la venta de la que salió. Sin regla de negocio, sin `CHECK` y sin la tabla a la que referenciaría: se documenta como columna reservada y se completa esta entrada —con su restricción, su referencia real y la regla que la gobierna— en cuanto se decida cómo funciona.

**`occurred_at` y `created_at` no son lo mismo, y separarlos cuesta una columna.** Cuándo ocurrió la venta y cuándo se registró coinciden casi siempre y **no tienen por qué**: un funcionario registra hoy el pago que entró ayer. De `occurred_at` sale además el día del código (`RN-MV-016`), de modo que confundirlas haría que una venta de ayer llevara la fecha de hoy en el papel que se le entrega al cliente.

**`discount_amount` existe y hoy vale siempre cero.** Por decisión del responsable del proyecto del 02-09-2026 no hay descuentos todavía. La columna se declara ahora —con su `CHECK` de coherencia— para que el día que lleguen **no haya que tocar ni una fila de lo ya vendido**; lo que sí habrá que decidir entonces es de dónde sale el descuento, si es de cabecera o de línea, y **sobre qué importe comisiona**, que es la pregunta cara de las tres.

**No hay columna de impuestos**, y no es un olvido: separar base e impuesto exige decidir con qué tasa se recalcula lo ya vendido, y esa decisión llega con la factura fiscal (§1.5).

### 7.2 `movement_types`

| Columna | Tipo | Nula |
|---|---|---|
| `id` | `uuid` | No |
| `code` | `varchar(50)` | No |
| `name` | `varchar(100)` | No |
| `prefix` | `varchar(6)` | No |
| `created_at` | `timestamptz` | No |

Mismo formato de código que `roles`, `memberships` y `products`: `^[A-Z][A-Z0-9_]*$`. **Sin `updated_at` y sin `deleted_at`** (`RN-MV-017`).

**Se siembra con un solo tipo —`VENTA`, prefijo `VTA`—** y la tabla existe igualmente. La alternativa era una columna `type` con un `CHECK`, y **este proyecto ya pagó dos veces por esa forma**: el catálogo de `event_type` de `audit_security_log` es un `CHECK`, y añadirle un valor costó dos migraciones con `DROP CONSTRAINT` sobre una tabla en uso. Los tipos que traen las etapas 2 a 6 —depósito, compra de puntos, comisión— entran entonces **como filas**.

**Las banderas de comportamiento no se declaran todavía.** El diseño anterior llevaba `requires_product`, `affects_cash` y `generates_commission`, y con un solo tipo **las tres serían constantes**: una columna que no distingue nada no dice nada. Entran cuando entre el segundo tipo, que es cuando empiezan a significar algo.

#### 7.2.1 El código de la venta

`<prefijo>-<AAAAMMDD>-<seis aleatorios>` — por ejemplo `VTA-20260902-K7M2QX`.

**Los seis dígitos son aleatorios y no correlativos**, y eso es una decisión. Una serie sin huecos no sobrevive ni a una transacción revertida —una `SEQUENCE` de PostgreSQL los deja por diseño— ni a una carga histórica, y prometerla obligaría a renumerar. Lo que sí se promete es que **el código es único** y que se declara así en el esquema.

**Y el alfabeto es el de 32 de Crockford, sin `I`, `L`, `O` ni `U`.** Este código se dicta por teléfono y se teclea: `O` contra `0` es el error que se comete, y `U` se descarta porque completa palabras que nadie quiere leer en un comprobante.

### 7.3 `movement_details`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `movement_id` | `uuid` | No | `movements` |
| `product_id` | `uuid` | No | `products` |
| `seller_id` | `uuid` | **Sí** | `users` |
| `quantity` | `integer` | No | — |
| `unit_price` | `numeric(14,2)` | No | — |
| `line_amount` | `numeric(14,2)` | No | — |
| `validity_days` | `integer` | **Sí** | — |

**`seller_id` es quien le vendió ESTA línea, y a quien se le creará la comisión por ella** (`RN-MV-003`, desde el 16-09-2026). Es la primera columna de esta tabla que apunta a una persona, y está aquí y no en la cabecera por dos motivos que se suman: la comisión se devenga **por línea** (§4.2, etapa 5), y los tipos de movimiento que vienen —depósito, comisión, retiro— **no venden nada**, de modo que una columna de vendedor en `movements` sería nula en la mayoría de las filas del libro. **Admite nulo por eso y solo por eso**: en una venta **siempre está**, y lo comprueba el caso de uso porque un `CHECK` no consulta `movement_types`. Hoy todas las líneas de una venta llevan **el mismo** vendedor —la entrada lo decide, no la línea—, y el modelo **no lo fuerza**: cuando un carrito mezcle productos de enlaces distintos, cada línea sabrá de quién es sin tocar el esquema. Lleva índice `(seller_id, movement_id)` para la mitad «lo que vendí» de `RF-MV-008`, **parcial sobre `seller_id IS NOT NULL`**, que sustituye al que la cabecera tenía.

**`unit_price` y `validity_days` son copias, y ahí está toda la razón de ser de esta tabla** (`RN-MV-002`). `RF-PM-004` corrige el precio de un producto y `RN-PM-015` declara su vigencia en días —opcional, y sin ella lo adquirido no caduca—: leerlas del catálogo al mostrar una venta de hace un año **reescribiría lo que alguien pagó y lo que compró**.

**`line_amount` se guarda aunque sea `quantity × unit_price`.** Es el mismo argumento de `RN-MV-013` una fila más abajo: es el número que se imprimió.

**La membresía destino no se copia**, porque `RF-PM-004` rechaza cambiarla: leerla del producto dentro de tres años da exactamente el mismo valor, y `RN-PM-010` garantiza que el producto no desaparece nunca. Copiarla solo añadiría un sitio donde el dato pudiera discrepar de sí mismo.

### 7.4 `payment_methods`

| Columna | Tipo | Nula |
|---|---|---|
| `id` | `uuid` | No |
| `code` | `varchar(50)` | No |
| `name` | `varchar(100)` | No |
| `is_active` | `boolean` | No |
| `visibility` | `varchar(20)` | No |
| `created_at` | `timestamptz` | No |

Se siembra por migración y **no se administra por API todavía** (§5.3). Lo mínimo para que una venta pueda decir con qué se pagó.

**`visibility` es un enumerado —`PUBLICO` e `INTERNO`— y NO un booleano**, por decisión del responsable del proyecto (09-09-2026). Es la forma de `products.scope` y no la de `countries.is_active`, y la diferencia entre las dos está en si el dominio **es candidato a crecer**: aquí lo es. «Visible solo para administración» y «visible solo en un canal» son distinciones que este eje puede necesitar, y con un booleano cada una costaría una columna nueva.

!!! important "`visibility` NO es `is_active` con otro nombre, y mezclarlas sería el error"

    Responden **dos preguntas distintas que no son excluyentes**:

    - **`is_active` — ¿sirve para pagar?** Un método inactivo está retirado: ninguna venta puede usarlo.
    - **`visibility` — ¿se le ofrece a alguien?** Un método `INTERNO` **sí sirve**, y **nadie lo elige**: lo pone el sistema.

    Las cuatro combinaciones significan algo, y ahí está el argumento para no fundirlas en un `status` de tres valores. `ACTIVO`+`PUBLICO` es un método corriente; `ACTIVO`+`INTERNO` es el pago gratuito; `INACTIVO`+`PUBLICO` es un método retirado que algún día vuelve; `INACTIVO`+`INTERNO` es uno interno ya retirado.

    Y hay un precedente que conviene citar entero, porque este documento lo escribió tres párrafos más abajo: la salida que se propuso para `POINTS` —«sembrarlo con `is_active` en falso: sigue en la tabla, no se ofrece»— **usaba `is_active` para significar visibilidad**, que es justo lo que ahora tiene su propia columna. Con `visibility`, `POINTS` puede sembrarse `ACTIVO`+`INTERNO` el día que se quiera esa semántica sin fingir que está retirado.

**`V54` la sembró con tres filas** —`CREDIT_CARD`, `PSE` y `POINTS`—, por decisión del responsable del proyecto del 04-09-2026. Sustituyen al `EFECTIVO`/`TRANSFERENCIA` que la tripleta de `RF-MV-001` había supuesto. **Las tres son `PUBLICO`.**

**Y desde el 09-09-2026 hay una cuarta, `GRATIS`, sembrada `ACTIVO` + `INTERNO`** (`RN-MV-022`). Es la única del catálogo que **ningún cliente puede elegir**, porque `RF-MV-009` no la devuelve nunca (`RN-MV-023`).

**Y desde el mismo 09-09-2026 este catálogo se lee SIN SESIÓN** (`RN-MV-024`, §4.1). Eso sube el peso de `visibility` sin cambiarle una línea: lo que la columna decide ya no es solo qué ve un cliente autenticado, sino **qué ve cualquiera**. `PUBLICO` pasa a significar *publicado de verdad*, y por eso el predicado de la consulta lleva los dos ejes y no uno — con solo `is_active`, abrir la ruta habría puesto el pago gratuito delante de un anónimo.

!!! danger "Por qué `GRATIS` tuvo que existir: hasta hoy una compra gratuita tenía que mentir"

    `movements.payment_method_id` es **`NOT NULL`**: toda venta declara con qué se pagó. Y desde el 08-09-2026 `RN-PM-006` **admite el precio cero** — lo tumbó la renovación `BECA → BECA`, que es un producto legítimo que vale cero.

    Las dos cosas juntas dejaban una venta de importe cero obligada a declarar `CREDIT_CARD`, `PSE` o `POINTS`, **y las tres son falsas**. No fallaba nada: el padrón de ventas simplemente dejaba de poder decir qué se cobró de verdad, y `CM` comisionaría sobre un cobro que nunca ocurrió.

    **La ausencia de un medio gratuito no era una carencia del catálogo: era un agujero abierto por `RN-PM-006` el día anterior**, y nadie lo vio porque el esquema no falla — miente.


!!! warning "`POINTS` está sembrado y todavía no se puede pagar con él"

    Este documento decía, hasta hoy, que `PUNTOS` **no se sembraba** porque «es de la etapa 3 y sembrarlo hoy ofrecería un método con el que no se puede pagar». **Se siembra igual**, por decisión del responsable del proyecto, y el argumento anterior no ha dejado de valer: **no existe saldo de puntos**, de modo que una venta pagada con `POINTS` se registra sin que haya nada de dónde descontar.

    **La consecuencia se acepta y queda escrita**: hoy el método aparece en el selector y la venta entra igual que cualquier otra. Cuando llegue la etapa 3 habrá que decidir qué se hace con las ventas que se registraron así — y **no habrá forma de distinguirlas** de las que se registren después, porque nada marca cuándo empezó a existir el saldo.

    Si lo que se quiere es que aparezca en el catálogo sin poder usarse todavía, la salida barata es sembrarlo con `is_active` en falso: sigue en la tabla, no se ofrece, y activarlo el día que haya puntos es un `UPDATE` de una fila.

### 7.5 `payment_method_exclusions`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `payment_method_id` | `uuid` | No | `payment_methods` |
| `country_id` | `uuid` | No | `countries` |
| `created_at` | `timestamptz` | No | — |

**Dónde NO vale cada método** (`RN-MV-019`). Clave primaria compuesta por las dos columnas, como `role_permissions`: la fila **es** la relación y no tiene identidad propia que valga la pena nombrar.

**Declara la exclusión y no el permiso, y esa elección tiene un coste que conviene tener escrito.** Un método **sin filas vale en todos los países**, de modo que sembrar los tres actuales no exige declarar nada y añadir un país nuevo no obliga a revisar el catálogo de medios. Lo que se paga a cambio es que **olvidar una exclusión no falla: ofrece**. Es la postura contraria a la que `RN-CM-012` tomó con las tasas —donde la ausencia significa «ninguno»— y se elige aquí por dos motivos: la lista de países crece sola y la de métodos no, y **esto no bloquea un cobro, solo pinta un selector**.

**Es la primera clave foránea entrante que recibe `countries`.** Ese catálogo existe desde `V16` y hasta hoy no lo referenciaba ni una tabla: se consultaba para pintar selectores y nada más. Con esto pasa a tener dependientes, y **retirar un país** —`RF-SP-022` cambia `is_active`, no borra— sigue sin romper nada porque nadie borra filas de `countries`.

**No se administra por API todavía** (§5.3): las exclusiones se siembran por migración, igual que los métodos. El día que haya pantalla de administración será un requerimiento propio, y entonces habrá que decidir si retirar una exclusión es auditable — hoy no hay operación que auditar.

### 7.6 Restricciones exigidas en el esquema

| Restricción | Sobre | Por qué ahí y no en el código |
|---|---|---|
| `uq_movements_code` | `movements(code)` | El comprobante es único. Dos peticiones simultáneas burlan cualquier comprobación previa |
| `ck_movements_status` | `status` en (`PENDIENTE`, `CONFIRMADA`, `RECHAZADA`, `ANULADA`) | `RN-MV-005`. El dominio, no la transición |
| `ck_movements_payable` | `payable_amount = total_amount - discount_amount` | `RN-MV-013`. Cruza tres columnas de la misma fila, que es exactamente lo que un `CHECK` sabe hacer |
| `ck_movements_amounts` | Los tres importes `>= 0` | Una venta negativa es un retiro disfrazado, y los retiros son la etapa 6 |
| `ck_movements_confirmed` | `confirmed_at IS NOT NULL` si y solo si `status = 'CONFIRMADA'` | Sin ella, una venta confirmada sin fecha o una pendiente con fecha son estados que el código puede escribir y nadie detecta |
| `uq_movement_details_producto` | `(movement_id, product_id)` | `RN-MV-011` |
| `ck_movement_details_quantity` | `quantity > 0` | Una línea de cero unidades no es una línea |
| `ck_movement_details_validity` | `validity_days IS NULL OR validity_days > 0` | La rama `IS NULL` va **delante y explícita**, por lo mismo que en `ck_products_icon_solo_upgrade`: un `CHECK` que evalúa a `NULL` **acepta** la fila |
| `pk_payment_method_exclusions` | `(payment_method_id, country_id)` | Un método no se excluye dos veces del mismo país. La clave primaria compuesta lo cierra sin que ninguna operación tenga que comprobarlo, igual que en `role_permissions` |

**`RN-MV-019` no aparece en esa lista, y es a propósito.** No hay nada que declarar: la exclusión **no se comprueba en ninguna operación**, solo se publica. Lo único que el esquema sostiene es que la relación no se duplique y que apunte a filas que existen.

**Lo que no se puede declarar en el esquema** y vive en el caso de uso: que haya al menos una línea (`RN-MV-009`), que haya como mucho un upgrade (`RN-MV-010`), que la cantidad sea uno en los upgrades (`RN-MV-015`), que el producto esté en la oferta (`RN-MV-007`), que el nivel suba (`RN-MV-006`), que la cuenta no esté en `FTD_PENDIENTE` (`RN-MV-008`) y **que toda línea de una venta lleve vendedor** (`RN-MV-003`, desde el 16-09-2026). Las siete dependen de filas de otras tablas, y un `CHECK` no consulta otras tablas — la última, de `movement_types`, porque `seller_id` solo es obligatorio cuando el tipo es `VENTA`.

---

## 8. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 02-09-2026 | **`MV` vuelve a nacer, y esta vez empieza por vender**, por decisión del responsable del proyecto. El módulo anterior se retiró entero el 01-09-2026 —catorce requerimientos y treinta y nueve reglas— y este documento **no es aquel corregido**: los identificadores vuelven a `RF-MV-001` y ninguna regla hereda su número. **La lección del primer intento está en el alcance**: aquel diseñó el libro completo —ventas, depósitos, puntos, comisiones, pasarela y notificaciones— antes de que existiera una sola venta; este declara el mismo destino y **escribe solo la etapa 1**, con las cinco siguientes en §4.2 y lo ya decidido de cada una anotado ahí. Nueve requerimientos y dieciocho reglas. **Once decisiones del responsable, todas del 02-09-2026.** (1) **Registrar y comprar son dos requerimientos**, porque una la origina un funcionario sobre la cuenta de otro y la otra el interesado sobre la suya: fundirlas daría un endpoint con dos modelos de seguridad. (2) **El vendedor sale del cliente y se congela** (`RN-MV-003`) — no se teclea, y quien registra queda en la auditoría sin cobrar nada; con un solo campo, el día que alguien de oficina registre la venta de un agente la cadena arranca en la persona equivocada. (3) **Cuatro estados**, con `RECHAZADA` separada de `ANULADA` porque un cobro que no entró y una venta que no debía existir son dos hechos distintos, y fundirlos borraría el número que dice cuánto se intenta cobrar y no entra. (4) **De `CONFIRMADA` no se sale**, y el precio queda escrito: hoy una venta confirmada por error **no se puede corregir por ninguna vía**. (5) **Solo se sube de nivel** (`RN-MV-006`), rechazado **al registrar** y no al confirmar, porque cobrar primero y descubrirlo después obliga a devolver dinero en un sistema donde devolver es una etapa que no existe. (6) **La oferta de `RF-PM-007` es condición de la venta**, también cuando registra el funcionario. (7) **A una cuenta en `FTD_PENDIENTE` no se le vende.** (8) **Sin descuentos y sin impuestos**: la columna de descuento se declara hoy y vale cero, para que su llegada no toque lo ya vendido; el impuesto no tiene columna porque separarlo exige decidir con qué tasa se recalcula el pasado. (9) **La comisión se devengará sobre el valor de cada línea** y no sobre el total, que es lo que encaja con `CM` rehecho —la tasa se resuelve por producto— y lo que impide que dos productos con tarifas distintas se promedien. (10) **Los métodos de pago se siembran y se leen**; administrarlos y restringirlos por país queda para después, y queda anotado el obstáculo real: **hoy nadie tiene país**. (11) **El campo del comprobante se llama `code` y no `resolucion`**, porque «resolución» es la palabra con la que la DIAN autoriza una numeración y usarla aquí invita justo a la confusión que §1.5 existe para evitar. **De la propuesta de tablas del responsable se conservan los tres importes y caen `updated_at` y `deleted_at`** (`RN-MV-001`): un libro con borrado lógico deja de ser un libro. **Y vuelve a abrirse D-26 con el mismo número**, porque es la misma pregunta que se retiró con el módulo: conceder el nivel comprado obliga a **escribir en `SP`**, y todas las interfaces entre módulos son de solo lectura. | Responsable del proyecto |
| 0.2.0 | 02-09-2026 | **Los cuatro permisos de §6 se siembran, y se reservan al superadministrador.** `V51__seed_movements_permissions.sql` los estrena adelantándose al resto del módulo —la tarea no depende de ninguna otra— y **no crea ninguna de las cuatro tablas de §7**. Nueva **§6.1** con la decisión que se aparta de [`security.md` §4.4](../security.md): **`ADMIN` no los recibe**, por decisión del responsable del proyecto, y ahí queda escrito lo que cuesta — la fuerza comercial cuelga de `ADMIN`, de modo que por `RN-SEG-003` **ningún rol de la cadena podrá declarar `movements:create`** y seis de los nueve requerimientos quedan operables solo por el superadministrador. No invalida ninguna regla ni bloquea construir el módulo: `RF-MV-002`, `RF-MV-008` y `RF-MV-009` no llevan permiso. **La reserva de las cuatro tablas del módulo (§7) no tiene número fijo**: lo toma quien se aplica primero, y esta migración —que no depende de nada— ya demostró que una reserva por adelantado sin código escrito no vale nada frente a eso. | Responsable técnico |
| 0.3.0 | 03-09-2026 | **`RF-PM-007` deja de ser el ejemplo de «comprar y consultar lo propio no llevan permiso»** (§6): pasó a exigir `products:sale` el 02-09-2026, y esta sección se corrige para no citarlo como si siguiera abierto. **El número de la siembra de permisos vuelve a correr**, esta vez porque `develop` fusionó su propio `V48` (`products:sale`, PR #56) mientras esta rama —sin empujar a `origin`— ya tenía el suyo: la que cede el número es siempre la rama que no está publicada, y esta migración pasa a `V51`. El motivo completo, con los números exactos de cada paso, vive en el encabezado de `V51__seed_movements_permissions.sql`. | Responsable técnico |
| 0.4.0 | 04-09-2026 | **Nace `RN-MV-019`: un método de pago puede estar excluido en países concretos.** Lo pidió el responsable del proyecto —«no en todos los países se pueden usar los métodos de pago»— y la decisión de fondo la tomó él mismo al precisarlo: **la restricción es informativa y no ejecutiva**. El sistema declara dónde NO vale cada método y lo publica con el catálogo (`RF-MV-009`); **quien filtra es el cliente que lo consume**. **Eso desmonta el bloqueo que §5.3 daba por seguro desde el 02-09-2026** —«hoy nadie tiene país, porque `users` no lo guarda y `countries` no tiene una sola clave foránea entrante»—: era cierto y **dejó de ser relevante**, porque si nadie valida en el servidor, nadie necesita saber de qué país se trata. Nace `payment_method_exclusions` (§7.5), con clave primaria compuesta como `role_permissions`, y con ella **`countries` recibe su primera clave foránea entrante** en los veinte días que lleva existiendo. **Declara la exclusión y no el permiso**, que es la postura CONTRARIA a la que `RN-CM-012` tomó con las tasas: un método sin filas vale en todas partes, de modo que sembrar los tres actuales no exige declarar nada y añadir un país no obliga a revisar el catálogo de medios — a cambio, **olvidar una exclusión no falla: ofrece**. Se elige así porque la lista de países crece sola y la de métodos no, y porque **esto no bloquea un cobro, solo pinta un selector**. **Y queda escrito lo que el módulo NO hace, que es la mitad de esta decisión**: `RF-MV-001` y `RF-MV-002` **no comprueban el país al registrar**, y una venta con un método excluido se registra con normalidad — hoy nada impide cobrar con `PSE` fuera de Colombia por API. Cerrar esa puerta exigiría `users.country_id`, tocar `RF-SP-024` y `RF-SP-045` y decidir qué país tienen las personas que ya existen, todo para una puerta por la que hoy solo pasa el superadministrador (§6.1). **La pregunta de qué país es el que manda sigue abierta y esta decisión la aplaza en lugar de resolverla.** | Responsable del proyecto |
| 0.5.0 | 04-09-2026 | **El vendedor deja de ser obligatorio en una venta** (`RN-MV-003`), por decisión del responsable del proyecto, **porque comprar no es cosa solo de los clientes**: un agente también compra. Hasta hoy la venta exigía que quien compraba colgase de alguien y rechazaba la operación si no —`EX-003` de `RF-MV-001`—, y esa exigencia **dejaba fuera a una persona que este mismo documento sabía que existe**: `RN-SP-019` declara desde el principio que **la cúspide de la fuerza comercial no declara superior**, de modo que quien porta el rol vendedor de mayor rango no podía comprar nada — no por una decisión de negocio, sino porque la venta no sabía a quién atribuirla. **Se retira la exigencia, no la deducción**: quien tiene superior sigue produciendo una venta atribuida a él, exactamente igual que antes; quien no lo tiene produce una venta **sin vendedor**, que es un estado legítimo y no un dato que falte. `movements.seller_id` pasa a admitir nulo, `EX-003` desaparece y `CA-MV-017` **invierte su sentido**: afirmaba que la venta se rechazaba, y ahora afirma que se registra. **Lo que cuesta hay que leerlo entero: una venta sin vendedor NO COMISIONA A NADIE.** `RN-CM-011` liquida por *override* recorriendo la cadena **hacia arriba desde el vendedor**, y sin ese punto de partida no hay cadena que recorrer. Es correcto —nadie vendió, nadie cobra— y quien construya la liquidación tiene que tratarlo: la alternativa era **inventar una atribución**, y una comisión pagada a quien no vendió es un error que no se detecta, porque el dinero sale y el número cuadra. **Esto no toca `RN-SP-027` ni `RN-SP-020`**: un cliente registrado por enlace sigue exigiendo vendedor, porque esa es una regla sobre **cómo se crean** los clientes y no sobre quién puede comprar. Lo que `MV` deja de hacer es **exigir el cumplimiento de una promesa ajena** en cada venta — que además era el único sitio donde se comprobaba. | Responsable del proyecto |
| 0.6.0 | 04-09-2026 | **Nace `RN-MV-020`: una venta confirmada que lleva un upgrade concede la membresía comprada**, por decisión del responsable del proyecto. Es **la contraparte de `RN-MV-004`**, y hasta hoy faltaba: aquella dice que registrar no concede nada, y sin esta el módulo no decía en ninguna parte **qué hace confirmar** — con lo que «pendiente» y «confirmada» eran dos palabras para el mismo hecho sin efecto. La regla se desarrolla en **§5.4** con las tres decisiones que lleva dentro. **(1) La vigencia se cuenta desde la confirmación y no desde la venta**: comprar el sábado y confirmar el lunes no puede comerse dos días de lo comprado — el retraso en comprobar el dinero es del sistema, no de quien pagó. **(2) Conceder SUSTITUYE y no deja historial**, porque `user_memberships` tiene la clave primaria en `user_id` (`RN-SP-014`): el nivel que alguien tuvo antes **no se puede reconstruir desde `SP`**, y quien lo necesite tendrá que leerlo de las ventas confirmadas. Es la misma deuda que `RN-CM-008` ya obliga a pagar con los porcentajes. **(3) Confirmar dos veces no concede dos veces**, lo que obliga a que la transición de estado sea **atómica** — y no es celo: el disparador será una pasarela, y una pasarela reintenta. **Cuál membresía no es ambiguo** gracias a `RN-MV-010`: hay a lo sumo un upgrade por venta, y esa restricción existía desde el registro precisamente para que este momento no tuviera que elegir. **Y queda un caso SIN DECIDIR a propósito**: entre registrar y confirmar, la persona puede haber subido más por otra vía, de modo que confirmar **la bajaría de nivel**. Las dos salidas son razonables —conceder siempre lo comprado, o no bajar nunca y dejar una venta cobrada que no entregó nada, que `RN-MV-005` impide corregir— y la elección no es técnica. Lo que no puede pasar es que se decida por omisión al escribir el código. **La regla no se puede construir todavía**: escribe en `user_memberships`, que es de `SP`, y eso lo gobierna **D-26**, abierta. Con esto, D-26 pasa a ser lo único que separa al módulo de `RF-MV-003`. | Responsable del proyecto |
| 0.7.0 | 05-09-2026 | **La decisión 2 de `RN-MV-020` se cae entera, un día después de escribirla.** El responsable del proyecto decidió que `user_memberships` sea un **historial** (`requirements/sp.md` v1.35.0): conceder **cierra** la membresía anterior e **inserta** la comprada, en lugar de reescribir la única fila de la persona. §5.4 se rehace en consecuencia. **Lo que desaparece es una deuda, no un detalle**: aquella versión declaraba que «el nivel que alguien tuvo antes no se puede reconstruir desde esa tabla» y que quien lo necesitara **tendría que leerlo de las ventas confirmadas**. Eso invertía una dependencia —era `MV` quien sostenía la memoria de `SP`— y ya no ocurre: «en qué nivel estaba esta persona el 12 de marzo» se responde en `SP`. **Nace además una precisión que la versión anterior no necesitaba**: `ends_at` de la membresía concedida sale de los `validity_days` copiados en la línea y **no hereda nada** de la que se cierra — quien tenía `VIP` hasta fin de mes y compra `PLATINO` de treinta días recibe treinta días desde la confirmación, y los que le quedaban **se pierden**. Que se pierdan queda escrito y **sin resolver**: `closed_at` deja constancia de cuántos días pagados no se usaron, y qué hacer con ellos —nada, prorratear, extender— no es decisión de este módulo. **El caso sin decidir sigue sin decidir**, y solo cambia su precio: conceder lo comprado ya no **borra** el nivel superior que la persona tuviera, deja su fila cerrada, de modo que esa salida pasa de destructiva a reversible a mano. **Y no cambia nada del calendario**: la regla la sigue implementando `RF-MV-003`, sin tripleta, y **D-26 sigue siendo lo único que la separa de poder construirse**. | Responsable del proyecto |
| 0.8.0 | 05-09-2026 | **`RF-MV-008` se especifica y se construye**, a petición del responsable del proyecto —«quiero ser capaz de ver mis ventas»—. Estaba **declarado desde el 02-09-2026** y sin tripleta, y §5.3 lo había acotado a propósito para que **no dependiera de D-22**: `RF-MV-006` y `RF-MV-007` responden con alcance global para quien tenga `movements:read`, y este cubre el caso propio. Sigue sin tocar esa decisión. **La decisión que carga el requerimiento es que «propio» son DOS papeles y no uno**: un movimiento lleva a quien recibe lo comprado y a quien lo vendió, y desde `RN-MV-003` v0.5.0 —comprar no es cosa solo de los clientes— **la misma persona puede estar en los dos, incluso en el mismo movimiento**. Se devuelven los dos en un listado y cada fila dice en cuál aparece quien pregunta (`BUYER`, `SELLER`, `BOTH`); el que es las dos cosas **aparece una sola vez**, que es lo que un `UNION` habría duplicado. Se descartaron las tres alternativas por lo que costaban: solo lo vendido deja al cliente con un listado siempre vacío, solo lo comprado deja al vendedor sin su pregunta, y un parámetro que eligiera el papel añade una decisión que el propio dato ya resuelve. **El alcance incluye el DETALLE además del listado**, y sin él el requerimiento no serviría: quien ve que compró algo no podría abrirlo, porque `RF-MV-007` exige `movements:read`. **Un movimiento ajeno responde `404`, igual que uno inexistente** —un `403` confirmaría que el identificador existe—. Nace `V58` con **dos** índices, y son dos porque son dos accesos: ningún índice sirve a un `OR` sobre columnas distintas, y el de vendedor es **parcial** porque una venta sin vendedor nunca forma parte de esta respuesta. **Y queda descubierto un hueco que no es de este requerimiento**: `movement_details` **no congela el código ni el nombre del producto** —`V54` guarda el identificador, la cantidad, el precio y la vigencia—, de modo que el detalle los lee de `products` y **renombrar un producto cambia cómo se ve una venta ya registrada**. Contradice el «lo copiado queda congelado» que `RF-MV-001` promete, y **no se resuelve aquí**. | Responsable del proyecto |
| 0.9.0 | 07-09-2026 | **Confirmar deja de entregar siempre: ahora lo decide el producto.** El catálogo gana `implementation` (`RN-PM-020`, [`requirements/pm.md` v0.17.0](pm.md)) y con ella `RN-MV-020` **se acota**: concede la membresía comprada **solo si el producto es de implementación automática**. Nace `RN-MV-021` para la otra mitad — lo comprado con implementación **manual** queda **pendiente de autorización**—, y nace `RF-MV-010` para que alguien pueda darla: sin él, un producto manual sería un producto que se cobra y **no se aplica nunca**. **La decisión que sostiene todo lo demás es que lo pendiente es la ENTREGA y no el COBRO**: la venta pasa a `CONFIRMADA` con normalidad cuando el dinero entra, de modo que `RN-MV-005` —de `CONFIRMADA` no se sale— **sigue intacta** y no hace falta un quinto estado. **Y de ahí sale la segunda: el estado de la autorización es de la LÍNEA, no de la venta.** Una venta puede llevar un upgrade automático y dos bots que alguien active a mano, y un estado de cabecera obligaría a decidir **una sola vez para toda la venta** qué significa «entregada a medias». Dónde vive exactamente ese estado —columna en `movement_details` o tabla propia con quién autorizó y cuándo— **lo decide `RF-MV-010`** y este documento no lo fija a propósito. **`movements:implement` se declara y NO se siembra**: entrará con la migración de ese requerimiento, y por eso no figura en el catálogo de [`security.md` §4.4](../security.md#44-catalogo-de-permisos), que enumera lo que existe en la base. Es un permiso propio y no `movements:confirm` porque son dos preguntas de dos personas distintas — «¿entró el dinero?» y «¿se le entrega?»—. **Queda declarado un hueco que este cambio NO cierra**: `RN-MV-002` obliga a copiar en la línea lo que puede cambiar, la implementación **se corrige** en `PM`, y `movement_details` **no tiene esa columna** (`V54`). Sin ella, corregir un producto reescribiría cómo se entregan ventas ya hechas — en los dos sentidos, y ninguno falla: los dos entregan mal con el cobro hecho. No se escribe hoy porque `RF-MV-003` —el único que leería el valor— sigue **bloqueado por D-26**, y lo que queda dicho es que la copia **debe existir antes** de que ese requerimiento se construya, junto al código y el nombre del producto que la v0.8.0 dejó anotados. **Nada de esto tiene código**: el módulo no cambia una línea, y el calendario tampoco — D-26 sigue siendo lo único que separa a `RF-MV-003` de poder escribirse, y ahora arrastra a `RF-MV-010` detrás. | Responsable del proyecto |
| 0.10.0 | 07-09-2026 | **`RN-MV-006` pierde la mitad de «igual»: renovar el mismo nivel se admite**, por decisión del responsable del proyecto. `PM` deja que un upgrade declare la misma membresía en los dos lados ([`requirements/pm.md` v0.19.0](pm.md) §5.2.3), y lo que se vende ahí es **tiempo y no nivel**. La regla pasa de rechazar «igual o inferior» a rechazar **solo el inferior**, y esa mitad —**una venta no baja a nadie de nivel**— es la que de verdad protegía a quien paga. `EX-005` conserva su identificador y estrecha su contenido. **El módulo no necesita nada más para que la renovación funcione**: `RN-MV-020` ya cierra la membresía abierta e inserta la comprada, y en una renovación las dos son del mismo nivel — la vigencia sale de los `validity_days` copiados en la línea y se cuenta desde la confirmación, que es exactamente lo que se compró. **Y esto no es una sorpresa para el código**: `RegisterSaleService.verificarQueSube` llevaba escrito desde el 04-09-2026 que la comprobación existe aunque la oferta la garantice, «porque la oferta puede ampliarse — **el día que se vendan renovaciones del mismo nivel**, por ejemplo». Ese día es hoy, y el comentario se convierte en el cambio de una comparación. | Responsable del proyecto |
| 0.11.0 | 07-09-2026 | **`RN-MV-012` no cambia, y su MOTIVO sí.** `SP` estrena el submódulo de **tasas de cambio** (`requirements/sp.md` v1.37.0), y con él se cae la premisa con la que esa regla se escribió: «no hay un total que calcular sin una tasa de cambio, y **este sistema no tiene ninguna**». Ahora las tiene. **La venta sigue exigiendo una sola moneda por decisión y no por ausencia**, y queda escrito lo que costaría levantarla: habría que elegir **qué tasa aplica** —la del día de la venta, presumiblemente— y **congelarla en la línea** junto al precio y la vigencia, porque `RN-MV-002` obliga a copiar lo que puede cambiar y una tasa cambia por definición. Sin esa copia, reconsultar el catálogo dentro de un año daría otro total para la misma venta. **Ninguna línea de código de este módulo cambia**, y esta entrada existe para que nadie lea el motivo viejo y crea que la conversión sigue siendo imposible: es que no se ha pedido. | Responsable del proyecto |
| 0.12.0 | 09-09-2026 | **Nace el pago gratuito, y con él la visibilidad como eje propio**, por decisión del responsable del proyecto. `payment_methods` gana `visibility` —enumerado `PUBLICO`/`INTERNO`, no booleano— y una cuarta fila, `GRATIS`, sembrada `ACTIVO` + `INTERNO` (§7.4). Dos reglas nuevas: `RN-MV-022` —importe cero ⟺ pago gratuito, **en los dos sentidos**— y `RN-MV-023` —lo `INTERNO` no se ofrece nunca—. **Lo que esto cierra no es una carencia del catálogo: es un agujero que `RN-PM-006` abrió el día anterior.** Aquella regla pasó a admitir el precio cero —lo tumbó la renovación `BECA → BECA`—, y `movements.payment_method_id` es `NOT NULL`: desde entonces toda compra gratuita estaba **obligada a declarar tarjeta, PSE o puntos**, y las tres son falsas. No fallaba nada; el padrón de ventas simplemente dejaba de poder decir qué se cobró, y `CM` comisionaría sobre un cobro que nunca ocurrió. **`visibility` no es `is_active` con otro nombre**, y fundirlas en un `status` de tres valores era la salida fácil y equivocada: responden preguntas distintas que **no son excluyentes** —«¿sirve para pagar?» y «¿se le ofrece a alguien?»— y las cuatro combinaciones significan algo. Hay además un precedente que lo confirma: la salida que este documento proponía para `POINTS` —«sembrarlo con `is_active` en falso: sigue en la tabla, no se ofrece»— **usaba `is_active` para significar visibilidad**, que es exactamente lo que ahora tiene columna propia. **`RN-MV-023` se aparta de `RN-MV-019` a propósito**: allí la exclusión por país **se publica y el cliente filtra**; aquí el cliente no filtra porque **no lo ve** — lo elige el sistema, no una persona, y publicarlo solo daría ocasión de ofrecerlo por error. **Y arrastra un cambio de contrato y de orden**: `paymentMethodId` pasa a ser **condicional en los dos sentidos** —prohibido si el importe es cero, exigido si no—, con la forma que `RN-SP-019` usa para el superior comercial; y el método **deja de verificarse el primero** en `RF-MV-001`, porque la regla necesita el importe, que hasta ahora se calculaba después. | Responsable del proyecto |
| 0.13.0 | 09-09-2026 | **`RN-MV-008` gana su única excepción, y `CA-MV-008` deja de ser inalcanzable.** La venta del **alta por enlace** (`RN-SP-043`) queda exenta: el registro gratuito crea la cuenta en `FTD_PENDIENTE` y anota su venta a continuación, de modo que con la regla aplicada tal cual **esa venta se rechazaría a sí misma** y ninguna alta gratuita sería posible. Lo que la regla prohíbe es que una cuenta a la espera de su depósito **siga comprando**, y esta venta es **anterior** a que haya nada que esperar — es el hecho que pone a la cuenta en ese estado. **No se relaja: se acota.** La entrada exenta es **de paquete**, de modo que solo la alcanza el adaptador que `SP` consume; el endpoint de `RF-MV-001` vive en `interfaces` y no puede llamarla, y todo lo demás —oferta, nivel, moneda, método de pago, comprobante— es idéntico, con lo que sigue habiendo **una sola definición de vender**. Y el mismo día `CA-MV-008` **pasa a estar probado**: era el único criterio de `RF-MV-001` sin prueba porque `ck_users_status` no admitía el estado y ningún camino lo producía (`spec.md` §12, `tasks.md` §4); `V77` lo admite y `RF-SP-045` lo produce. | Responsable del proyecto |
| 0.14.0 | 09-09-2026 | **El catálogo de métodos de pago se abre SIN INICIAR SESIÓN**, por decisión del responsable del proyecto —«que los métodos de pago puedan ser públicos, pero solo los activos y visibles»—. Nace `RN-MV-024`: el `GET` de `RF-MV-009` es **público**, y con él `MV` estrena su primera ruta pública. **Lo pide `SP` y no `MV`**: el formulario de registro por enlace (`RF-SP-045`) declara `movement.paymentMethodId` y **elige con qué se paga antes de que exista la cuenta**, de modo que el selector no tenía de dónde sacar las opciones — el mismo hueco que `RN-SP-041` cerró un día antes con países, tipos de documento y brokers, y se cierra igual. **Lo que se abre estaba ya acotado, y por eso abrirlo no publica nada nuevo**: la respuesta lleva desde el 09-09-2026 los **dos ejes** —`is_active` (`RN-MV-018`) y `visibility` (`RN-MV-023`)—, de modo que el anónimo recibe **exactamente la misma respuesta** que el autenticado. Ese detalle no es cosmético: **con un solo eje, abrir la ruta habría puesto el pago gratuito delante de cualquiera**, y `RN-MV-022` dejaría de ser una regla del sistema para pasar a ser una opción de un selector público. **Solo el `GET`**, como allí: el día que el catálogo se administre por API, esas escrituras no se abren y siguen respondiendo `401` sin token en lugar de `403`. **Y no deja ningún permiso huérfano**, al revés que `RN-SP-041`: este endpoint nunca exigió permiso, porque los cuatro `movements:` gobiernan ventas y ninguno gobernaba esta lectura. Lo que sí gana es una **cota de tasa**: entra al cubo de catálogos públicos —120/min por origen y con cubo propio— por lo mismo que los otros tres, que es que consulta la base en cada llamada ([`security.md`](../security.md) §6). | Responsable del proyecto |
| 0.15.0 | 16-09-2026 | **Nace `RF-MV-011`, comprar un producto por el hotlink de un vendedor**, por decisión del responsable del proyecto: un cliente con cuenta puede comprar por el enlace de **otro** vendedor, y esa venta **se atribuye al dueño del enlace** (`RN-MV-025`), no a su agente principal. Es `RF-MV-002` con el producto resuelto por el hotlink y el vendedor tomado del enlace; el resto —tipo, estado, línea, validaciones— no cambia. **`RN-MV-003` se enmienda** para decir de dónde sale el vendedor en cada entrada: tienda y funcionario → el superior vigente; hotlink → el dueño del enlace. En la misma transacción nace el vínculo cliente-vendedor en `client_sellers` ([`requirements/sp.md`](sp.md) v1.56.0, `RN-SP-049`), que es lo que permite que un cliente tenga varios vendedores sin tener varios superiores. La compra de un **paquete** por hotlink queda para la tanda de compra de paquetes. Sin código todavía. | Responsable del proyecto |
| 0.16.0 | 16-09-2026 | **`movements` cambia de personas: pierde `client_id` y `seller_id` y gana `user_id`; el vendedor baja a `movement_details.seller_id`**, por decisión del responsable del proyecto. Nace `RN-MV-026` —**todo movimiento tiene UN sujeto**, la persona a cuyo nombre ocurre el hecho, que en una venta es quien compra y en un depósito quien deposita— y se enmienda `RN-MV-003` **en tres cosas**: el vendedor es **de la línea** y no de la cabecera, porque ahí se le creará la comisión (§4.2, la base es el valor de cada línea) y porque los tipos que vienen no venden nada; **puede variar entre líneas**, aunque hoy las tres entradas pongan el mismo en todas; y **en una venta es obligatorio sin excepción** — quien no cuelga de nadie (`RN-SP-019`) **se vende a sí mismo**, con `seller_id = user_id`. **Eso deshace la v0.5.0**: la «venta sin atribución» del 04-09-2026 deja de existir, y lo que `CM` haga con una autoventa lo decide `CM`. `RN-MV-025` pasa a decir «el `seller_id` de cada línea». **Lo que el esquema no sostiene queda escrito**: la columna de la línea admite nulo por los tipos sin vendedor, y «obligatorio en `VENTA`» vive en el caso de uso, porque un `CHECK` no consulta `movement_types`. **El contrato cambia**: `client` pasa a `user` en las dos lecturas, `seller` se va de la cabecera a cada línea de `SaleResponse`, `RegisterSaleRequest` pide `userId` en lugar de `clientId`, y en `MyMovement` el papel `SELLER` se resuelve por las líneas. Lo escribe `V12`; tripletas `RF-MV-001`, `RF-MV-002` y `RF-MV-008` enmendadas, `RF-SP-045` en lo que registra. | Responsable del proyecto |
