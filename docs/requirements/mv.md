# Requerimientos del Módulo — `MV` Movimientos

| Campo | Valor |
|---|---|
| Módulo | `MV` — Movimientos |
| Paquete | `modules/movements` |
| Prefijos de permiso | `movements:` |
| Versión | 0.5.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 02-09-2026 |
| Última actualización | 04-09-2026 |

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
| `SP` | Consume | **Usuarios**: que el cliente exista, y en qué estado está |
| `SP` | Consume | **La estructura comercial**: de qué vendedor cuelga quien compra (`user_supervisors`), **si cuelga de alguno** — desde el 04-09-2026 el vendedor es opcional (`RN-MV-003`) |
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
| `RF-MV-008` | Consultar las ventas propias | Ventas | Autenticado |
| `RF-MV-009` | Consultar los métodos de pago | Medios de pago | Autenticado |

**Registrar y comprar son dos requerimientos y no uno**, y eso **se aparta del precedente** que `PM` y `CM` fijaron —«el alta es una, no dos»—. La razón por la que aquí no aplica no es el contenido de la venta sino **quién la pide y por dónde entra**: una la origina un funcionario sobre la cuenta de otro y exige `movements:create`; la otra la origina el interesado sobre la suya y no exige permiso ninguno, como `RF-SP-039` y `RF-PM-007`. Fundirlas daría un endpoint con **dos modelos de seguridad**, que es donde se cuela el que sobra.

**Y las dos producen exactamente la misma venta**: mismo tipo, mismo estado inicial, mismas reglas, mismo vendedor congelado. Lo único que cambia es quién puede llamar y sobre quién.

**Confirmar y rechazar comparten permiso y no requerimiento.** Son las dos salidas de la misma revisión —entró el dinero o no entró—, de modo que separar los permisos obligaría a conceder siempre los dos a la misma persona. Son dos requerimientos porque son **dos operaciones con dos resultados distintos**, y porque el día que exista la pasarela una la escribirá un webhook y la otra también, pero por caminos que no se parecen.

**Anular no es rechazar, y la diferencia es de negocio.** Una venta **rechazada** es un cobro que se intentó y no entró; una **anulada** es una venta que no debía existir —se registró por error, o al cliente equivocado—. Fundirlas ahorraría un estado y borraría el único número que responde **cuánto se intenta cobrar y no entra**.

**Anular solo se admite mientras la venta esté pendiente** (`RN-MV-005`). Una venta confirmada ya concedió un nivel, y retirarlo es **una operación distinta que no existe todavía** (§5.3).

**No hay requerimiento para editar una venta**, y no es un olvido: `RN-MV-001` lo prohíbe. Corregirse, se corrige anulando y registrando otra.

**Mientras no exista la pasarela, confirma una persona.** `RF-MV-003` es hoy un acto humano: alguien mira el comprobante bancario y dice que el dinero entró. Queda escrito lo que eso significa —**el sistema le cree a una persona lo que mañana le creerá a la pasarela**— y también que el camino no cambia cuando llegue: la venta ya nace `PENDIENTE`, que es justo el estado que una integración necesita y que no habría si la venta naciera confirmada.

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
| `RN-MV-003` | El vendedor **sale de quien compra y se congela**, y **puede no haberlo** | Al registrar | No se teclea: se toma el **superior vigente de quien compra** en `user_supervisors` y se guarda en la venta. Reasignarlo mañana **no puede cambiar quién ganó por lo vendido hoy**. **Quién registró la operación no se guarda aquí**: va al registro de auditoría, y no cobra nada — sin esa separación, el día que alguien de oficina registre la venta de un agente la cadena de comisiones arranca en la persona equivocada. **Desde el 04-09-2026 el vendedor es opcional**: quien compra sin colgar de nadie compra igual, y la venta se registra **sin atribución** | **Crítica** |
| `RN-MV-004` | Solo una venta **confirmada** produce efectos | Siempre | Una venta `PENDIENTE` no concede ningún nivel, no habilita ninguna cuenta y no comisiona. Sin esta regla, **iniciar un cobro concede lo que se está comprando**, y no hace falta mala fe: basta con que el pago no llegue | **Crítica** |
| `RN-MV-005` | Los estados y sus transiciones son **cuatro y cerradas** | Al resolver | `PENDIENTE` → `CONFIRMADA`, `RECHAZADA` o `ANULADA`. **De `CONFIRMADA` no se sale**, porque ya concedió lo comprado y deshacerlo es una operación que no existe (§5.3). `RECHAZADA` y `ANULADA` son finales. Se declara en el esquema como `CHECK`, y la transición en el caso de uso | **Crítica** |
| `RN-MV-006` | **Solo se sube de nivel** | Al registrar una venta con upgrade | Comprar una membresía **igual o inferior** a la vigente **se rechaza al registrar**, antes de cobrar nada. Cobrar por algo que no da nada es el reclamo que ninguna prueba de camino feliz encuentra. La comparación es sobre el **nivel** de la cadena de membresías, donde **superior significa número menor** | **Crítica** |
| `RN-MV-007` | El producto tiene que estar **en la oferta de quien compra** | Al registrar, por las dos entradas | Se valida contra lo que `RF-PM-007` ya resuelve —lo activo, lo que corresponde al nivel de esa persona—, **compre el cliente o registre el funcionario**. Se comprueba **otra vez al registrar** aunque la interfaz ya la haya pintado, porque entre mirar la oferta y pagarla el producto pudo retirarse. Que el funcionario tampoco pueda saltársela es deliberado: si hay un acuerdo comercial que la oferta no contempla, la salida es el catálogo, no una excepción invisible | Alta |
| `RN-MV-008` | A una cuenta en `FTD_PENDIENTE` **no se le vende** | Al registrar | Esa cuenta **autentica y no opera** (`RN-SP-026`): venderle antes del depósito sería saltarse justo lo que ese estado existe para exigir. Vale para las dos entradas | **Crítica** |
| `RN-MV-009` | Una venta lleva **al menos una línea** | Al registrar | Lo que se vendió vive en `movement_details`, y una venta sin líneas no dice qué se vendió. **No se puede declarar en el esquema**, porque un `CHECK` no cuenta filas de otra tabla: vive en el caso de uso | Alta |
| `RN-MV-010` | **Como mucho un upgrade** por venta | Al registrar | Bots, los que se quiera; **upgrades, uno**. Dos en la misma venta son **dos cambios de nivel en una sola operación**, y no hay forma no arbitraria de decidir en cuál queda la persona ni de justificar cobrarle los dos | **Crítica** |
| `RN-MV-011` | El mismo producto **no se repite** en una venta | Al registrar | Dos líneas del mismo producto son una con el doble de cantidad. Admitirlas obligaría a sumar para responder «¿cuántos compró?», y la respuesta dependería de que nadie olvidara hacerlo. Se declara en el esquema | Media |
| `RN-MV-012` | Todas las líneas comparten la **moneda** de la cabecera | Al registrar | La moneda es de la venta y no de la línea: un cobro se hace en **una** moneda. Un producto en otra distinta no se rechaza por gusto — es que **no hay un total que calcular** sin una tasa de cambio, y este sistema no tiene ninguna | Alta |
| `RN-MV-013` | El total es la **suma de las líneas**, y se congela | Al registrar | `total_amount` se calcula **una vez** y no se recalcula al leer: es el número que aparece en el comprobante, y recalcularlo haría que un cambio de redondeo reescribiera documentos ya entregados. **`payable_amount = total_amount − discount_amount`**, y esa igualdad **sí** se declara en el esquema porque cruza tres columnas de la misma fila | Alta |
| `RN-MV-014` | El importe respeta los **decimales de su moneda** | Al registrar | Igual que `RN-PM-007` para el precio del catálogo, y por lo mismo: la escala la decide la moneda y no la columna | Media |
| `RN-MV-015` | La cantidad es **uno** en los upgrades | Al registrar | Un upgrade con cantidad dos no significa nada: no se sube dos veces al mismo nivel. Los bots admiten más de uno. **No cabe en un `CHECK`** —no consulta `products`— y vive en el caso de uso | Alta |
| `RN-MV-016` | Toda venta lleva un **código legible**, y su día sale del hecho | Al registrar | `<prefijo del tipo>-<AAAAMMDD>-<seis aleatorios>`, único (§7.2.1). El día se corta en la **zona de la operación —`America/Bogota`—** y no en UTC ([`architecture.md` §15.1.1](../architecture.md)): con UTC, una venta de las 23:30 en Bogotá llevaría el día siguiente. La fecha es la de **`occurred_at`**. El aleatorio usa el alfabeto de 32 de Crockford —**sin `I`, `L`, `O` ni `U`**— porque este código se dicta por teléfono y se teclea, y `O` contra `0` es el error que se comete. **No sustituye al identificador interno**: `id` sigue siendo el UUID | Alta |
| `RN-MV-017` | El catálogo de tipos **no se edita por API** y no se borra | Siempre | Se siembra por migración, como el de monedas. El motivo es más fuerte que allí: **el caso de uso decide según el tipo**, de modo que uno añadido en caliente sería un tipo que ningún código sabe procesar — el sistema aceptaría el movimiento y no haría nada con él. Es el defecto que no falla: promete. Y **no lleva borrado**, porque un tipo eliminado deja movimientos históricos apuntando a algo que ya no significa nada | **Crítica** |
| `RN-MV-018` | Un método de pago desactivado **no invalida** lo que se pagó con él | Siempre | La validación es del **momento del registro**, no permanente. Es el mismo criterio de `RN-PM-008` con las monedas | Media |
| `RN-MV-019` | Un método de pago puede estar **excluido en países concretos**, y esa exclusión **se publica, no se comprueba** | Al consultar los métodos de pago (`RF-MV-009`) | No todos los medios operan en todas partes: `PSE` es colombiano y no significa nada en México. El sistema **declara dónde NO vale cada método** y lo devuelve junto al catálogo; **quién aplica ese filtro es el cliente que lo consume**. Un método **sin exclusiones vale en todos los países**, que es el estado de los tres sembrados hoy. **Esta regla no interviene al registrar una venta** — ver §5.3 | Media |

### 5.2 Por qué las críticas son críticas

**`RN-MV-001` — la venta no se toca.** Es lo que separa un libro de una tabla. Un importe que se puede editar convierte cualquier pregunta sobre el pasado en una conjetura, y el daño no se descubre al editar: se descubre meses después, cuando dos personas miran el mismo número y no coinciden. Es la postura de `RN-PM-010` y `RN-CM-005` llevada un paso más allá — allí la fila permanece aunque se retire; aquí **ni siquiera se retira**.

**`RN-MV-002` y `RN-MV-003` — congelar, no referenciar.** Son la misma regla aplicada a dos cosas distintas, y las dos las **impusieron otros documentos antes de que este módulo existiera**: `pm.md` §1.4 para el precio y la vigencia, y la estructura comercial para el vendedor. El defecto que evitan no falla en el momento: aparece cuando alguien corrige un precio, o reasigna un cliente, y descubre que acaba de cambiar el pasado.

!!! important "El vendedor pasó a ser opcional el 04-09-2026, y no compra solo quien es cliente"

    Hasta hoy, una venta **exigía** que quien compraba colgase de alguien: sin superior vigente, la operación se rechazaba para no registrar una venta sin dueño. La decisión del responsable del proyecto es retirar esa exigencia, **porque comprar no es cosa solo de los clientes**: un agente también compra, y la fuerza comercial no está hecha para que todos sus miembros cuelguen de otro.

    **El caso que lo destapa está escrito en `sp.md` desde el principio.** `RN-SP-019` dice que **la cúspide de la fuerza comercial no declara superior** —quien porta el rol vendedor de mayor rango, aquel cuyo rol padre ya no es `VENDEDOR`—. Esa persona existe, no cuelga de nadie por diseño, y con la regla anterior **no podía comprar nada**: no por una decisión de negocio, sino porque la venta no sabía a quién atribuirla.

    **Lo que se retira es la exigencia, no la deducción.** Quien tiene superior sigue produciendo una venta atribuida a él, exactamente igual que antes; quien no lo tiene produce una venta **sin vendedor**, y eso es un estado legítimo y no un dato que falte.

    **Y lo que cuesta hay que leerlo entero: una venta sin vendedor NO COMISIONA A NADIE.** `RN-CM-011` liquida por *override* recorriendo la cadena **hacia arriba desde el vendedor**, de modo que sin ese punto de partida no hay cadena que recorrer. Es correcto —nadie vendió, nadie cobra— y es una diferencia que quien construya la liquidación tiene que tratar: **la alternativa era inventar una atribución**, y una comisión pagada a quien no vendió es un error que no se detecta, porque el dinero sale y el número cuadra.

    **Esto no cambia `RN-SP-027` ni `RN-SP-020`.** Un cliente registrado por enlace sigue exigiendo vendedor: es una regla de `SP` sobre **cómo se crean** los clientes, no sobre quién puede comprar. Lo que `MV` deja de hacer es **exigir el cumplimiento de una promesa ajena** en cada venta, que además era el único sitio donde se comprobaba.

**`RN-MV-004` y `RN-MV-005` — el estado es lo único que se mueve, y no se mueve hacia atrás.** Sin la primera, iniciar un cobro concede lo que se está comprando. Sin la segunda, «confirmada» deja de significar algo: una venta que puede volver a pendiente es una venta cuyo efecto nadie puede dar por firme.

**`RN-MV-006`, `RN-MV-008` y `RN-MV-010` — las tres que protegen a quien paga.** Cobrar un nivel que no sube, venderle a una cuenta que todavía no opera, o cobrar dos upgrades que no se pueden aplicar los dos. Ninguna de las tres rompe nada técnicamente: las tres producen un cobro que hay que devolver, y devolver dinero en este sistema **es una etapa que ni siquiera existe**.

### 5.3 Lo que este módulo NO decide, y por qué

**Cómo se corrige una venta confirmada por error.** `RN-MV-005` cierra la puerta —de `CONFIRMADA` no se sale— y con ello **deja un hueco a la vista**: retirar un nivel ya concedido es una operación que **no existe**, y hay que decidirla aparte. Se elige esto sobre deshacerlo todo automáticamente, que obligaría a quitarle el nivel a quien lleva un mes usándolo. **El precio queda declarado: hoy una venta confirmada por error no se puede corregir por ninguna vía.**

**Quién ve las ventas de quién.** Un director que consulta ventas ¿ve las de su equipo, las de todos, o solo las suyas? Es **alcance de datos**, depende de **D-22** —abierta— y es el mismo aplazamiento que `requirements/cm.md` §5.3 ya hizo. `RF-MV-006` y `RF-MV-007` se especifican con **alcance global explícito** para quien tenga el permiso, y `RF-MV-008` cubre el caso propio sin depender de esa decisión.

**Cómo se escribe en `SP`.** Es **D-26** (§3). Este documento recomienda una salida y no la fija.

**Qué métodos de pago hay.** Se siembran los mínimos por migración y se leen (`RF-MV-009`). **Administrarlos por API sigue quedando para después**, por decisión del responsable del proyecto del 02-09-2026.

**En qué países vale cada método: decidido el 04-09-2026, y NO como se había supuesto.** Aquella nota daba por hecho que restringir por país exigiría antes darle un país a alguien —«hoy nadie tiene país, porque `users` no lo guarda y `countries` no tiene una sola clave foránea entrante»—. **No hace falta**, porque el responsable del proyecto decidió que la restricción **es informativa y no ejecutiva**: el sistema declara dónde no vale cada método y lo publica; **quien filtra es el cliente que consume el catálogo** (`RN-MV-019`).

**La consecuencia hay que leerla dos veces, porque es lo que este módulo NO hace:** `RF-MV-001` y `RF-MV-002` **no comprueban el país al registrar una venta**, y una petición que use un método excluido **se registra con normalidad**. No es un descuido ni una fase pendiente: es lo que significa que la restricción sea del cliente. **Nada impide hoy cobrar con `PSE` fuera de Colombia por API**, y quien quiera que eso se impida tiene que decidir antes de qué país se trata — que es la pregunta que sigue sin respuesta y que esta decisión **aplaza en lugar de resolver**.

**Por qué se acepta.** El caso real es una pantalla que ofrece medios de pago, y ofrecer uno que no va a funcionar es el defecto que se quería quitar. Convertirlo en una validación del servidor exigiría `users.country_id`, tocar `RF-SP-024` y `RF-SP-045`, y decidir qué país tienen las personas que ya existen — todo para cerrar una puerta por la que hoy solo pasa el superadministrador (§6.1).

---

## 6. Permisos

| Código | Recurso | Acción | Para qué |
|---|---|---|---|
| `movements:read` | `movements` | `read` | Consultar ventas y su detalle |
| `movements:create` | `movements` | `create` | Registrar una venta a nombre de otra persona |
| `movements:confirm` | `movements` | `confirm` | Dar por pagada, o por no pagada, una venta pendiente |
| `movements:void` | `movements` | `void` | Anular una venta que no debía existir |

**`confirm` y `void` no reutilizan `update`**, y esa es la única decisión de esta sección. Una venta **no se actualiza nunca** (`RN-MV-001`), de modo que un permiso llamado `movements:update` prometería algo que no existe. Y son dos permisos y no uno porque confirmar es operación de caja diaria mientras que anular **borra del embudo** una venta que alguien registró: quien concilia pagos no tiene por qué poder hacer desaparecer ventas ajenas.

**Comprar y consultar lo propio no llevan permiso**, por lo mismo que `RF-SP-039`: exigirlo obligaría a concedérselo a todos los clientes, que es la forma de que un permiso deje de significar nada. `RF-PM-007` seguía el mismo criterio hasta el 02-09-2026, cuando pasó a exigir `products:sale` — un permiso propio de esa vista y no de administración, que se concede por rol como cualquier otro y no cambia el argumento de esta sección.

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
| `client_id` | `uuid` | No | `users` |
| `seller_id` | `uuid` | **Sí** | `users` |
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
| `quantity` | `integer` | No | — |
| `unit_price` | `numeric(14,2)` | No | — |
| `line_amount` | `numeric(14,2)` | No | — |
| `validity_days` | `integer` | **Sí** | — |

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
| `created_at` | `timestamptz` | No |

Se siembra por migración y **no se administra por API todavía** (§5.3). Lo mínimo para que una venta pueda decir con qué se pagó.

**`V54` la sembró con tres filas** —`CREDIT_CARD`, `PSE` y `POINTS`—, por decisión del responsable del proyecto del 04-09-2026. Sustituyen al `EFECTIVO`/`TRANSFERENCIA` que la tripleta de `RF-MV-001` había supuesto.

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

**Lo que no se puede declarar en el esquema** y vive en el caso de uso: que haya al menos una línea (`RN-MV-009`), que haya como mucho un upgrade (`RN-MV-010`), que la cantidad sea uno en los upgrades (`RN-MV-015`), que el producto esté en la oferta (`RN-MV-007`), que el nivel suba (`RN-MV-006`) y que la cuenta no esté en `FTD_PENDIENTE` (`RN-MV-008`). Las seis dependen de filas de otras tablas, y un `CHECK` no consulta otras tablas.

---

## 8. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 02-09-2026 | **`MV` vuelve a nacer, y esta vez empieza por vender**, por decisión del responsable del proyecto. El módulo anterior se retiró entero el 01-09-2026 —catorce requerimientos y treinta y nueve reglas— y este documento **no es aquel corregido**: los identificadores vuelven a `RF-MV-001` y ninguna regla hereda su número. **La lección del primer intento está en el alcance**: aquel diseñó el libro completo —ventas, depósitos, puntos, comisiones, pasarela y notificaciones— antes de que existiera una sola venta; este declara el mismo destino y **escribe solo la etapa 1**, con las cinco siguientes en §4.2 y lo ya decidido de cada una anotado ahí. Nueve requerimientos y dieciocho reglas. **Once decisiones del responsable, todas del 02-09-2026.** (1) **Registrar y comprar son dos requerimientos**, porque una la origina un funcionario sobre la cuenta de otro y la otra el interesado sobre la suya: fundirlas daría un endpoint con dos modelos de seguridad. (2) **El vendedor sale del cliente y se congela** (`RN-MV-003`) — no se teclea, y quien registra queda en la auditoría sin cobrar nada; con un solo campo, el día que alguien de oficina registre la venta de un agente la cadena arranca en la persona equivocada. (3) **Cuatro estados**, con `RECHAZADA` separada de `ANULADA` porque un cobro que no entró y una venta que no debía existir son dos hechos distintos, y fundirlos borraría el número que dice cuánto se intenta cobrar y no entra. (4) **De `CONFIRMADA` no se sale**, y el precio queda escrito: hoy una venta confirmada por error **no se puede corregir por ninguna vía**. (5) **Solo se sube de nivel** (`RN-MV-006`), rechazado **al registrar** y no al confirmar, porque cobrar primero y descubrirlo después obliga a devolver dinero en un sistema donde devolver es una etapa que no existe. (6) **La oferta de `RF-PM-007` es condición de la venta**, también cuando registra el funcionario. (7) **A una cuenta en `FTD_PENDIENTE` no se le vende.** (8) **Sin descuentos y sin impuestos**: la columna de descuento se declara hoy y vale cero, para que su llegada no toque lo ya vendido; el impuesto no tiene columna porque separarlo exige decidir con qué tasa se recalcula el pasado. (9) **La comisión se devengará sobre el valor de cada línea** y no sobre el total, que es lo que encaja con `CM` rehecho —la tasa se resuelve por producto— y lo que impide que dos productos con tarifas distintas se promedien. (10) **Los métodos de pago se siembran y se leen**; administrarlos y restringirlos por país queda para después, y queda anotado el obstáculo real: **hoy nadie tiene país**. (11) **El campo del comprobante se llama `code` y no `resolucion`**, porque «resolución» es la palabra con la que la DIAN autoriza una numeración y usarla aquí invita justo a la confusión que §1.5 existe para evitar. **De la propuesta de tablas del responsable se conservan los tres importes y caen `updated_at` y `deleted_at`** (`RN-MV-001`): un libro con borrado lógico deja de ser un libro. **Y vuelve a abrirse D-26 con el mismo número**, porque es la misma pregunta que se retiró con el módulo: conceder el nivel comprado obliga a **escribir en `SP`**, y todas las interfaces entre módulos son de solo lectura. | Responsable del proyecto |
| 0.2.0 | 02-09-2026 | **Los cuatro permisos de §6 se siembran, y se reservan al superadministrador.** `V51__seed_movements_permissions.sql` los estrena adelantándose al resto del módulo —la tarea no depende de ninguna otra— y **no crea ninguna de las cuatro tablas de §7**. Nueva **§6.1** con la decisión que se aparta de [`security.md` §4.4](../security.md): **`ADMIN` no los recibe**, por decisión del responsable del proyecto, y ahí queda escrito lo que cuesta — la fuerza comercial cuelga de `ADMIN`, de modo que por `RN-SEG-003` **ningún rol de la cadena podrá declarar `movements:create`** y seis de los nueve requerimientos quedan operables solo por el superadministrador. No invalida ninguna regla ni bloquea construir el módulo: `RF-MV-002`, `RF-MV-008` y `RF-MV-009` no llevan permiso. **La reserva de las cuatro tablas del módulo (§7) no tiene número fijo**: lo toma quien se aplica primero, y esta migración —que no depende de nada— ya demostró que una reserva por adelantado sin código escrito no vale nada frente a eso. | Responsable técnico |
| 0.3.0 | 03-09-2026 | **`RF-PM-007` deja de ser el ejemplo de «comprar y consultar lo propio no llevan permiso»** (§6): pasó a exigir `products:sale` el 02-09-2026, y esta sección se corrige para no citarlo como si siguiera abierto. **El número de la siembra de permisos vuelve a correr**, esta vez porque `develop` fusionó su propio `V48` (`products:sale`, PR #56) mientras esta rama —sin empujar a `origin`— ya tenía el suyo: la que cede el número es siempre la rama que no está publicada, y esta migración pasa a `V51`. El motivo completo, con los números exactos de cada paso, vive en el encabezado de `V51__seed_movements_permissions.sql`. | Responsable técnico |
| 0.4.0 | 04-09-2026 | **Nace `RN-MV-019`: un método de pago puede estar excluido en países concretos.** Lo pidió el responsable del proyecto —«no en todos los países se pueden usar los métodos de pago»— y la decisión de fondo la tomó él mismo al precisarlo: **la restricción es informativa y no ejecutiva**. El sistema declara dónde NO vale cada método y lo publica con el catálogo (`RF-MV-009`); **quien filtra es el cliente que lo consume**. **Eso desmonta el bloqueo que §5.3 daba por seguro desde el 02-09-2026** —«hoy nadie tiene país, porque `users` no lo guarda y `countries` no tiene una sola clave foránea entrante»—: era cierto y **dejó de ser relevante**, porque si nadie valida en el servidor, nadie necesita saber de qué país se trata. Nace `payment_method_exclusions` (§7.5), con clave primaria compuesta como `role_permissions`, y con ella **`countries` recibe su primera clave foránea entrante** en los veinte días que lleva existiendo. **Declara la exclusión y no el permiso**, que es la postura CONTRARIA a la que `RN-CM-012` tomó con las tasas: un método sin filas vale en todas partes, de modo que sembrar los tres actuales no exige declarar nada y añadir un país no obliga a revisar el catálogo de medios — a cambio, **olvidar una exclusión no falla: ofrece**. Se elige así porque la lista de países crece sola y la de métodos no, y porque **esto no bloquea un cobro, solo pinta un selector**. **Y queda escrito lo que el módulo NO hace, que es la mitad de esta decisión**: `RF-MV-001` y `RF-MV-002` **no comprueban el país al registrar**, y una venta con un método excluido se registra con normalidad — hoy nada impide cobrar con `PSE` fuera de Colombia por API. Cerrar esa puerta exigiría `users.country_id`, tocar `RF-SP-024` y `RF-SP-045` y decidir qué país tienen las personas que ya existen, todo para una puerta por la que hoy solo pasa el superadministrador (§6.1). **La pregunta de qué país es el que manda sigue abierta y esta decisión la aplaza en lugar de resolverla.** | Responsable del proyecto |
| 0.5.0 | 04-09-2026 | **El vendedor deja de ser obligatorio en una venta** (`RN-MV-003`), por decisión del responsable del proyecto, **porque comprar no es cosa solo de los clientes**: un agente también compra. Hasta hoy la venta exigía que quien compraba colgase de alguien y rechazaba la operación si no —`EX-003` de `RF-MV-001`—, y esa exigencia **dejaba fuera a una persona que este mismo documento sabía que existe**: `RN-SP-019` declara desde el principio que **la cúspide de la fuerza comercial no declara superior**, de modo que quien porta el rol vendedor de mayor rango no podía comprar nada — no por una decisión de negocio, sino porque la venta no sabía a quién atribuirla. **Se retira la exigencia, no la deducción**: quien tiene superior sigue produciendo una venta atribuida a él, exactamente igual que antes; quien no lo tiene produce una venta **sin vendedor**, que es un estado legítimo y no un dato que falte. `movements.seller_id` pasa a admitir nulo, `EX-003` desaparece y `CA-MV-017` **invierte su sentido**: afirmaba que la venta se rechazaba, y ahora afirma que se registra. **Lo que cuesta hay que leerlo entero: una venta sin vendedor NO COMISIONA A NADIE.** `RN-CM-011` liquida por *override* recorriendo la cadena **hacia arriba desde el vendedor**, y sin ese punto de partida no hay cadena que recorrer. Es correcto —nadie vendió, nadie cobra— y quien construya la liquidación tiene que tratarlo: la alternativa era **inventar una atribución**, y una comisión pagada a quien no vendió es un error que no se detecta, porque el dinero sale y el número cuadra. **Esto no toca `RN-SP-027` ni `RN-SP-020`**: un cliente registrado por enlace sigue exigiendo vendedor, porque esa es una regla sobre **cómo se crean** los clientes y no sobre quién puede comprar. Lo que `MV` deja de hacer es **exigir el cumplimiento de una promesa ajena** en cada venta — que además era el único sitio donde se comprobaba. | Responsable del proyecto |
