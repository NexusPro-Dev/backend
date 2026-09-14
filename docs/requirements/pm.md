# Requerimientos del Módulo — `PM` Productos y Mercadeo

| Campo | Valor |
|---|---|
| Módulo | `PM` — Productos y Mercadeo |
| Paquete | `modules/products` |
| Prefijos de permiso | `products:` |
| Versión | 0.27.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 26-08-2026 |
| Última actualización | 14-09-2026 |

!!! info "Qué va en este documento"

    El catálogo de requerimientos del módulo: qué debe hacer, bajo qué reglas y con qué permisos.

    El comportamiento detallado de cada requerimiento —flujos, validaciones, criterios de aceptación y casos límite— vive en su tripleta, en `docs/specs/pm/`. Aquí no se repite.

!!! warning "Documento en Borrador: tres decisiones lo condicionan"

    Este documento se redacta antes de su primera compuerta y contiene **tres propuestas que necesitan aprobación explícita**, porque una vez usadas no se deshacen o cuestan caro:

    1. **El código `PM`.** Un código, en cuanto aparece en un identificador, no se cambia jamás ([`modules.md` §2.1](../modules.md#21-regla-de-decision)). En cuanto exista `RF-PM-001`, esta letra queda fijada para siempre.
    2. **La frontera del alcance** (§1.3): este módulo **define y publica** el catálogo; **no cobra ni entrega**. El motivo, en §1.4.
    3. ~~**Cómo lee este módulo las membresías y monedas de `SP`**~~ — **resuelta el 26-08-2026** con el cierre de **D-25**: `SP` publica tres interfaces de solo lectura y `PM` las importa (§3).

    `modules.md` §6 advierte además que los códigos de los módulos candidatos no deberían fijarse hasta conocer el alcance completo del producto. Se procede igualmente por decisión del responsable del proyecto, y queda escrito que se procedió sabiéndolo.

---

## 1. Información del módulo

### 1.1 Descripción

`PM` es dueño de **lo que la plataforma vende**. Un producto es una unidad de venta con nombre, precio y moneda, y existe en **dos tipos que no se mezclan**: el **upgrade de membresía**, que da derecho a pasar al nivel de acceso que declara, y el **bot del sistema**, que da derecho a una prestación de la plataforma.

**Desde el 08-09-2026 un producto lleva DOS precios y solo uno de ellos se cobra**: el **precio del sistema**, que es el que la venta copia y sobre el que comisiona `CM`, y un segundo importe opcional que **cambió de significado el 12-09-2026**: nació como **precio público** —lo que se le enseñaba a quien no administra— y desde entonces es el **precio de compra**, **lo que NEXUS paga por el producto** cuando tiene que comprarlo, y que **no sale de administración** (§5.2.4, §5.2.6).

El módulo gobierna ese catálogo —lo crea, lo consulta, lo corrige, lo activa y lo retira— y **publica a cada persona lo que puede comprar**, que no es lo mismo que el catálogo completo.

**Y desde el 14-09-2026 un producto se RESEÑA.** Quien porta `products:comment` deja sobre un producto **una** reseña —una puntuación de uno a cinco y un texto—, la corrige y la retira, y **nadie más que su autor la toca**. Las reseñas se leen **sin token** y el producto publica, en sus cuatro lecturas, el promedio y la cantidad de las que tiene vivas (§5.2.7). Es la primera vez que este módulo guarda algo que **escribe un cliente**, y por eso es también la primera vez que una fila suya lleva a una persona.

### 1.2 Objetivo

Hoy la membresía de una persona solo cambia porque un administrador se la asigna (`RF-SP-032`). No existe **nada que comprar**: ni un precio, ni una oferta, ni un lugar donde diga qué cuesta subir de nivel. Este módulo pone ese objeto en el sistema, que es el paso sin el cual la venta —y con ella las comisiones y la facturación— no tiene sobre qué operar.

### 1.3 Alcance

**Incluye**

- Registrar un producto de cualquiera de los dos tipos, con su precio, su moneda, **hasta dónde se muestra**, **cómo se implementa lo que otorga** y —si se quiere— **el precio con el que se anuncia**.
- Consultar el catálogo completo, en lista y en detalle, con filtros por tipo, estado y membresía —**de origen y de destino**—, **con los dos precios a la vista** — el de venta y el de compra, que **solo aquí** se ven juntos.
- Corregir un producto: nombre, descripción, icono, **los dos precios**, moneda, vigencia, **alcance** e **implementación**.
- Activar y desactivar un producto, que es lo que decide si se ofrece.
- Retirar un producto por eliminación lógica y con motivo.
- **Publicar a cada persona la oferta que le aplica**, que en los upgrades son **los declarados desde su membresía vigente** — su salto y su renovación.
- **Reseñar un producto**: una puntuación de uno a cinco y un texto, **una por persona y producto**, que su autor corrige y retira; **leer las reseñas** de un producto sin autenticación, y ver en cada lectura del producto **su promedio y cuántas tiene**.

**No incluye**

- **La compra y el cobro.** Ni orden, ni estado de pago, ni pasarela. Corresponde al área de **Finanzas** del inventario ([`modules.md` §6](../modules.md#6-alcance-por-inventariar)), todavía por definir.
- **La aplicación del upgrade sobre la persona.** Cambiar el nivel de alguien es escribir en `user_memberships`, que es tabla de `SP` y tiene su propio requerimiento (`RF-SP-032`). Ver §1.4.
- **El contenido de lo que se vende.** Qué cursos o qué sesiones incluye un nivel pertenece a **Academia**; qué señales, a **Señales**. Este módulo vende el derecho, no lo entrega.
- **Comisiones y atribución de la venta.** A quién se le paga por vender un producto es del área de **Comisiones**.
- **Promociones, descuentos y campañas.** El nombre del módulo las anticipa y su alcance las admite, pero no se registran todavía: un precio promocional con vigencia es un requerimiento con su propia tabla, y escribirlo hoy sería adelantarlo sin necesidad. **El precio de compra de `RN-PM-023` no las abre**: no tiene vigencia, no lo ve quien compra y **no cambia lo que se cobra** — es el costo, no un descuento (§5.2.6).
- **La moderación de las reseñas.** Nadie distinto del autor retira una reseña —ni un administrador— (`RN-PM-027`), y no hay denuncia, ocultación ni respuesta del vendedor. Es una decisión del responsable del proyecto, tomada sabiendo lo que cuesta (§5.2.7), y el día que haga falta será **otro requerimiento con otro permiso**, no una excepción a esta regla.
- **Exigir haber comprado para reseñar.** `PM` no puede leer las ventas de `MV` sin cerrar el ciclo `MV → PM → MV` que `modules.md` §7 prohíbe. Quien opina es quien porta el permiso, no quien pagó (§5.2.7).
- **Hilos, respuestas y votos de utilidad.** Una reseña es una opinión sobre el producto, no una conversación.

### 1.4 La frontera, y por qué está donde está

**Un producto de upgrade no cambia la membresía de nadie.** Declara un derecho y su precio; quien lo ejerce es la operación de compra, que no existe todavía. La tentación es cerrar el círculo aquí mismo —comprar y aplicar en un solo paso— y hay dos razones para no hacerlo:

1. **`user_memberships` es de `SP`.** Un módulo no accede a las tablas ni a los repositorios de otro ([`modules.md` §7](../modules.md#7-reglas-de-dependencia)). Aplicar el upgrade desde aquí obliga a que `SP` **publique** esa escritura como interfaz de aplicación, con sus reglas intactas —`RN-SP-018` incluida—, y eso es una ampliación de `SP`, no de este módulo.
2. **Comprar sin cobrar es una venta que no ocurrió.** Registrar la compra antes de que exista el cobro produce un objeto que dice que alguien pagó cuando nadie verificó que pagara. Es peor que no tenerlo, porque parece que se tiene.

Lo que este documento sí deja resuelto es que **el catálogo esté diseñado para esa continuación**, y en dos sentidos. El producto **no desaparece nunca** (`RN-PM-010`), de modo que una compra futura siempre podrá resolver qué se compró. Y **cada compra guardará el importe que se pagó y la vigencia que compró**, en lugar de leerlos del producto: resuelto el 26-08-2026 al aprobar `RF-PM-004` y ampliado el 27-08-2026 con `RN-PM-015`, es una condición que este módulo **impone a uno que todavía no existe**, porque sin ella corregir un precio o una vigencia pasaría a reescribir lo ya vendido.

**Y qué ocurre cuando la vigencia vence**, decidido el 27-08-2026: la persona **se queda sin nivel vigente**. No vuelve al que tenía antes ni baja al más bajo de la cadena; es lo que `SP` ya hace, porque `user_memberships` admite fecha de fin y una membresía vencida deja de conceder —vencer no es lo mismo que no tener, pero para el acceso da igual—. Volver al nivel anterior habría exigido que la compra **guardase cuál era**, porque después de asignar el nuevo esa información no está en ningún sitio.

---

## 2. Submódulos

Según [`modules.md` §5](../modules.md#5-fichas-de-modulo).

| Submódulo | Responsabilidad | Requerimientos |
|---|---|---|
| Productos | Alta, consulta, edición, estado y retiro del catálogo | `RF-PM-001` a `RF-PM-006` |
| Oferta | Qué puede comprar quien mira, que no es el catálogo completo | `RF-PM-007` |
| **Hotlinks** | El enlace público que un vendedor reparte: un producto y quién lo ofrece, sin autenticación | `RF-PM-008` |
| **Reseñas** | Lo que quien compra dice del producto: una puntuación y un texto por persona, que solo su autor toca, y que se leen sin token | `RF-PM-009` a `RF-PM-013` |

**Por qué la oferta es un submódulo y no una consulta más.** Responde una pregunta distinta y a otro actor: el catálogo lo lee quien administra y contiene todo —lo inactivo, lo retirado, el motivo del retiro—; la oferta la lee el cliente y contiene **solo lo que le aplica a él**. Separarlas evita el error que consiste en filtrar la respuesta en el navegador.

---

## 3. Dependencias

| Módulo | Tipo | Para qué |
|---|---|---|
| `SP` | Consume | **Membresías** (`RN-PM-003`): validar que el destino de un upgrade existe, y conocer su nivel para decidir la oferta |
| `SP` | Consume | **Monedas** (`RN-PM-008`): validar que la moneda existe, está activa, y con cuántos decimales se expresa su importe |
| `SP` | Consume | **Membresía vigente del actor** (`RN-PM-011`): sin ella no puede decidirse qué upgrades ofrecerle |
| `SP` | Consume | **La persona que escribe una reseña** (`RN-PM-030`): que existe, por clave foránea a `users`; y su **nombre y apellido** para publicarlos, por `JOIN` en la consulta de lectura —el precedente es el `JOIN` a `memberships` de `RF-PM-002`—. **Ninguna regla se decide con ese `JOIN`**: quién puede reseñar lo dice el permiso, y quién es el autor lo dice la propia fila |
| `SP` | Consume | Autorización, auditoría, paginación y jerarquía de errores, que son infraestructura compartida y no una dependencia de negocio |

La dependencia es **acíclica**: `PM` consume `SP` y `SP` no consume nada ([`modules.md` §7](../modules.md#7-reglas-de-dependencia)).

!!! success "D-25 — cerrada el 26-08-2026"

    Las tres primeras filas de esta tabla se resuelven con **interfaces de aplicación de solo lectura que publica `SP`**, una por lectura: si una membresía existe y qué nivel tiene, si una moneda está activa y cuántos decimales declara, y cuál es la membresía vigente de una persona. `PM` las importa; `SP` no se entera de que `PM` existe.

    **Lo que cruza la frontera son modelos de lectura, nunca entidades**, y la definición de «vigente» **se queda en `SP`**: reimplementarla aquí es el defecto que devuelve resultados plausibles durante meses. La ausencia del dato llega como valor vacío, y qué `4xx` produce lo decide este módulo, que es quien tiene el contrato. Una regla de **ArchUnit** impide que `modules/products` importe repositorios o entidades de `modules/system`.

    **`MembershipView` gana el `color` el 07-09-2026**, y con él la referencia que las cinco respuestas del módulo publican. **Es un dato puramente estético y aun así cruza la frontera por el puerto y no por un `JOIN` de conveniencia**: quien decide qué se sabe de una membresía es `SP` (`RN-SP-024`), y abrir una excepción «porque solo es un color» sería la primera grieta en la única regla que sostiene D-25.

    **Ampliar el puerto es aditivo y no rompe a nadie**: quien ya lo consume sigue leyendo los cuatro campos que leía.

    Las tareas que escriben esos puertos pertenecen a **`RF-PM-001` y `RF-PM-007`**, aunque el código viva en paquetes de `SP`. El detalle completo, en [`architecture.md` §15.2](../architecture.md#152-como-consume-un-modulo-los-datos-de-otro-cierre-de-d-25).

---

## 4. Actores

| Actor | Rol en el módulo | Permisos típicos |
|---|---|---|
| Administrador | Define y gobierna el catálogo entero | `products:create`, `products:read`, `products:update`, `products:delete` |
| Funcionario · fuerza comercial | Consulta el catálogo para vender o para atender a un cliente | `products:read` |
| Consumidor | Ve lo que puede comprar, **y opina sobre ello** | `products:sale`, `products:comment` |

**El consumidor no lleva `products:read`, y es a propósito.** Ese permiso abre el catálogo completo, con lo inactivo y lo retirado dentro. `RF-PM-007` responde con lo suyo y solo con lo suyo, de modo que concederlo obligaría a dar a cada cliente la lectura de todo el catálogo para que pudiera ver tres líneas. Es la misma decisión que `RF-SP-039` tomó con el perfil propio.

**`RF-PM-007` respondía hasta el 02-09-2026 a cualquier persona autenticada, sin exigir nada.** Por decisión del responsable del proyecto pasa a exigir `products:sale` — un permiso de negocio, no de administración: gobierna quién ve la **vista de venta**, y no el catálogo. Se concede a los roles de tipo `CONSUMIDOR` como cualquier otro permiso, por `RF-SP-006`, y no por siembra: el rol `CLIENTE` nace sin permisos a propósito (`V30`), y este cambio no le abre una excepción.

**Nace `products:hotlink` el 07-09-2026**, por decisión del responsable del proyecto: es el **segundo permiso de vista** del módulo y gobierna la **vista de hotlinks** —los productos cuyo alcance llega a ese canal (`RN-PM-019`)—, igual que `products:sale` gobierna la de venta. No reutiliza `products:read` por el mismo motivo de siempre: aquel abre el catálogo administrativo entero.

!!! info "Nace sin endpoint que lo exija, y es deliberado"

    **El canal de hotlinks todavía no está construido.** Sembrar el permiso antes **no rompe nada** —el catálogo de permisos es datos, y su único efecto es poder concederse— y es exactamente lo que hizo `V51` con los cuatro `movements:`, adelantados al resto de su módulo.

    Lo que evita es la alternativa: llegar al requerimiento que lo necesite y tener que sembrar el permiso **y** construir la vista en el mismo Pull Request.

    **Se asocia a `SUPERADMIN` y a `ADMIN`** en `V60`, sin reserva ([`security.md` §4.4](../security.md#44-catalogo-de-permisos)): decidirlo de otro modo habría creado la cuarta reserva del superadministrador, y ver qué se publica en un canal comercial no es una operación que deba quedar exclusiva de la raíz. **A `CLIENTE` no**, por lo mismo que `products:sale`.

**Nace `products:comment` el 14-09-2026**, por decisión del responsable del proyecto, y es el **primer permiso de escritura del módulo que no es de administración**: gobierna las tres operaciones sobre la reseña propia —escribirla, corregirla, retirarla— y la lectura de la propia (`RF-PM-013`). Se preguntó antes de escribir si bastaba con `products:sale`, y la respuesta fue que no: ver qué se puede comprar y opinar sobre ello son dos capacidades, y quien administre roles tiene que poder conceder una sin la otra. **El permiso habilita; ser el autor autoriza** (`RN-PM-027`): un administrador con `products:comment` escribe las suyas y no toca las ajenas.

**Se siembra asociado a `SUPERADMIN` y a `ADMIN`**, por la obligación de §4.4 y sin reserva, y **a `CLIENTE` no**, por lo mismo de siempre: quien administre roles se lo concede a los de tipo `CONSUMIDOR` por `RF-SP-006`.

---

## 5. Reglas de negocio

### 5.1 Reglas propias del módulo

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-PM-001` | Dos tipos, y el tipo es inmutable | Al registrar y en toda edición | El producto es `UPGRADE_MEMBRESIA` o `BOT`. El tipo se fija al crear y **ninguna operación lo cambia** | Crítica |
| `RN-PM-002` | **Origen y destino** obligatorios en el upgrade, prohibidos en el bot | Al registrar | Un `UPGRADE_MEMBRESIA` declara **de qué membresía sale y a cuál lleva**; un `BOT` **no puede** declarar ninguna de las dos. La condición se exige en los dos sentidos. El origen entró el 02-09-2026 (§5.2.1) | **Crítica** |
| `RN-PM-003` | Origen y destino son membresías reales de la cadena | Al registrar un upgrade | Las dos deben existir en `SP`. Se declaran además como claves foráneas | Crítica |
| `RN-PM-017` | **El origen no está por encima del destino** | Al registrar un upgrade | Un upgrade **no baja**: `level` del origen **mayor o igual** que el del destino —la cadena numera del uno hacia abajo, de modo que **mayor `level` es más bajo**—. Declarar `ORO → BECA` sería vender un descenso llamándolo upgrade y se rechaza. **Declarar el mismo en los dos lados SÍ se admite desde el 07-09-2026**: es una **renovación** (§5.2.3), y era lo único que esta regla prohibía sin motivo | **Crítica** |
| `RN-PM-018` | **Se admite saltar niveles** | Siempre | El origen **no tiene por qué ser el inmediatamente inferior** al destino: `BECA → ORO` es legítimo y es la razón de que el origen se declare en lugar de deducirse de la cadena. Deducirlo habría hecho imposible exactamente el caso que el campo existe para permitir | Alta |
| `RN-PM-004` | Un solo upgrade activo **por pareja origen→destino** | **Al activar**, y no al registrar | No pueden coexistir **dos productos de upgrade activos con el mismo origen y el mismo destino**. **Hasta el 02-09-2026 la unicidad era solo por destino**, y eso hacía imposible vender `BECA → ORO` y `PLATINO → ORO` a la vez — que es precisamente lo que el origen existe para permitir. Se comprueba en un solo sitio porque el producto **nace inactivo** (`RN-PM-012`): dos copias de esta regla —una en el alta y otra en la activación— acabarían divergiendo, y la que se quedara atrás no fallaría, admitiría | Crítica |
| `RN-PM-005` | Nombre único entre los vivos | Al registrar y al editar | El nombre no se repite entre los productos no eliminados, **sin distinguir mayúsculas ni acentos** | Alta |
| `RN-PM-006` | **Ningún precio es negativo**, y el cero se admite | Al registrar y al editar | **Los dos importes** —el del sistema y el público— se rechazan si son negativos, y **se aceptan en cero**. **Decía «mayor que cero» hasta el 08-09-2026**, con el argumento de que lo gratuito se concede en lugar de venderse; lo que lo tumbó fue la **renovación** (§5.2.3): un `BECA → BECA` es un producto legítimo que vale cero, y prohibirlo obligaba a inventarle un céntimo | Alta |
| `RN-PM-007` | **Los precios respetan** los decimales de su moneda | Al registrar y al editar | **Ninguno de los dos importes** puede tener más decimales que los que declara su moneda (`currencies.decimal_places`). **Es una sola moneda para los dos**: el precio de compra se expresa en la del producto y no en otra | Media |
| `RN-PM-008` | La moneda debe estar activa al declararla | Al registrar y al editar el precio | Se rechaza una moneda inexistente o inactiva. Que **después** se desactive no invalida lo ya registrado | Media |
| `RN-PM-009` | Solo se ofrece lo activo | Siempre que se publique la oferta | Un producto inactivo o eliminado no aparece en `RF-PM-007`, aunque siga siendo visible en el catálogo administrativo | Alta |
| `RN-PM-010` | El producto no desaparece | Al eliminar | La eliminación es **lógica y con motivo** (Art. V.13). La fila permanece para que lo que se venda siga resolviendo qué era y cuánto costaba | Alta |
| `RN-PM-011` | **La oferta coincide por ORIGEN**, no compara niveles | Al publicar la oferta | A una persona se le ofrecen los upgrades cuyo **origen es su membresía vigente**, y ningún otro. **Deja de ser una comparación de niveles el 07-09-2026** —lo declaraba §5.2.1 desde el 02-09-2026 y esta regla no se había reescrito—: comparar niveles ofrecía a quien está en `ORO` un `PLATINO → ORO`, que no es suyo. Quien no tiene membresía vigente no coincide con ningún origen y **no ve ningún upgrade**, sin que haya que escribirlo aparte | Alta |
| `RN-PM-012` | El producto nace inactivo | Al registrar | Todo producto se registra **`INACTIVO`**: existe, no se ofrece, y se publica con `RF-PM-005`. Es lo que permite revisar precio y texto antes de ponerlo a la venta, y lo que deja `RN-PM-004` viviendo en un solo sitio | Alta |
| `RN-PM-013` | El código no se libera nunca | Siempre | Todo producto lleva un **código corto, estable e inmutable**, único **incluso frente a los eliminados** — al revés que el nombre. Es la referencia desde la que una factura o una comisión dirán qué se vendió, y el nombre no sirve porque `RF-PM-004` lo deja corregir | **Crítica** |
| `RN-PM-014` | No se publica lo que no se explica | Al activar | Un producto **sin descripción no puede activarse**. Registrarlo sin ella es legítimo —está preparándose—; ofrecérselo a un cliente sin decirle qué se lleva, no | Media |
| `RN-PM-015` | La vigencia se mide en días y es opcional | Al registrar y al editar | Un producto puede declarar **cuántos días dura lo que otorga**, contados desde la compra. Es **opcional en los dos tipos**: sin ella, lo adquirido **no caduca**. Si se declara, es un entero **mayor que cero** | Alta |
| `RN-PM-016` | El icono solo existe en el upgrade | Al registrar y al editar | Un `UPGRADE_MEMBRESIA` **puede** declarar el icono con el que el frontend lo pinta; un `BOT` **no puede**. Es un **identificador**, no una imagen, y es **opcional** incluso donde se admite | Media |
| `RN-PM-019` | **El alcance dice hasta dónde se muestra el producto, y es acumulativo** | Al registrar y al editar | Todo producto declara `TIENDA` o `HOTLINKS`, **obligatorio en los dos tipos y sin valor por omisión**. No son dos canales que se reparten el catálogo: `HOTLINKS` **incluye** la tienda, de modo que la escala crece. Se corrige libremente (§5.2.2) | Alta |
| `RN-PM-020` | **La implementación dice si lo comprado se aplica solo o espera autorización** | Al registrar y al editar | Todo producto declara `AUTOMATICA` o `MANUAL`, **obligatorio en los dos tipos y sin valor por omisión**. Gobierna qué hace `MV` al confirmar una venta: `RN-MV-020` concede la membresía **solo** si el producto es automático, y con `MANUAL` lo comprado queda esperando a que un funcionario lo autorice | **Crítica** |
| `RN-PM-021` | **El hotlink solo publica lo activo y de alcance `HOTLINKS`** | Al responder el enlace público (`RF-PM-008`) | Un producto inactivo, retirado o de alcance `TIENDA` **no se publica sin autenticación**, y su ausencia se responde con el **mismo `404`** que un código inexistente. Es el primer sitio donde `RN-PM-019` **filtra de verdad**: hasta hoy el alcance se declaraba y no acotaba ninguna consulta | **Crítica** |
| `RN-PM-022` | **De la persona solo se publica su nombre, y solo si es fuerza comercial** | Al responder el enlace público (`RF-PM-008`) | El enlace devuelve **nombre y apellido** y nada más —ni correo, ni identificador, ni estado, ni roles—, y **solo de quien porta un rol de tipo `VENDEDOR`**. Un cliente, un administrador o un nombre de usuario inexistente responden **lo mismo**: `404`. Sin esa uniformidad, el endpoint confirmaría qué nombres de usuario existen | **Crítica** |
| `RN-PM-023` | **Un producto puede declarar su precio de compra, y ese no se cobra** | Al registrar y al editar | El **precio de compra** es **lo que NEXUS paga por el producto** cuando tiene que comprarlo —una licencia, un bot, un servicio de un tercero— y **ahí se guarda lo que costó**. Es **opcional** y **no interviene en ninguna venta**: no lo copia `movement_details`, no comisiona `CM` y no se convierte. Se expresa en **la moneda del producto** y obedece a `RN-PM-006` y `RN-PM-007` como el otro. **Nulo no es cero**: significa «no se conoce» —no se ha comprado todavía, o no aplica—, mientras que cero significa que **no costó nada**. **Se corrige libremente y se puede vaciar** (`RF-PM-004`). **Decía «precio público» hasta el 12-09-2026** (§5.2.6) | Alta |
| `RN-PM-024` | **El precio de compra no sale de administración; el precio y la conversión salen en toda lectura** | Siempre que se consulte un producto, con token o sin él | **Reescrita por tercera vez el 12-09-2026.** Las **cuatro** lecturas del módulo —`RF-PM-002`, `RF-PM-003`, `RF-PM-007` y `RF-PM-008`— devuelven `price` —el que se cobra— y `exchange`, la conversión de **ese** importe a la moneda por omisión con la tasa vigente hoy (`RF-SP-047`), **presente y nula** cuando no hay nada que convertir. **`purchasePrice` lo devuelven solo las dos lecturas de administración** —`RF-PM-002` y `RF-PM-003`, bajo `products:read`—, **presente y nulo** cuando no se conoce. La oferta propia (`RF-PM-007`) y el hotlink (`RF-PM-008`) **no lo seleccionan siquiera**: es el costo de NEXUS, y publicarlo enseñaría el margen a quien compra — en el hotlink, **sin token** (§5.2.6) | **Crítica** |
| `RN-PM-025` | **La puntuación es un entero de uno a cinco, y va siempre con texto** | Al reseñar y al corregir | Una reseña declara **siempre** su puntuación, entera y entre `1` y `5`, los dos incluidos: no hay medias estrellas ni cero — el cero no es «malo», es «sin puntuar», y una reseña sin puntuar no existe. **El texto es obligatorio también**, de uno a mil caracteres sin contar los espacios de los extremos: una puntuación sola no explica nada, y las reseñas existen para explicar | Alta |
| `RN-PM-026` | **Una reseña por persona y producto, entre las vivas** | Al reseñar | Quien ya tiene una reseña viva sobre un producto **no escribe otra: corrige la que tiene** (`RF-PM-010`). Retirada la suya, puede escribir una nueva. Es lo que hace que el promedio signifique algo — con varias por persona, quien más escribe más pesa— y lo que convierte la reseña en una **opinión** y no en un hilo | Alta |
| `RN-PM-027` | **Solo el autor corrige y retira su reseña — nadie más, ni administración** | Al corregir y al retirar | Una reseña la toca únicamente quien la escribió. **`products:comment` habilita la operación; ser el autor la autoriza**, y son dos cosas distintas: un administrador con el permiso escribe las suyas y **no puede** tocar las ajenas. No existe moderación (§5.2.7), y quien intenta tocar una ajena recibe `403` | **Crítica** |
| `RN-PM-028` | **Solo se reseña lo que se puede comprar, y solo eso se lee sin token** | Al reseñar y al leer las reseñas | Se reseña un producto **activo y no retirado**; la lista pública (`RF-PM-012`) responde **solo sobre esos mismos**, y lo demás —inexistente, inactivo, retirado— recibe el **mismo `404`**, como el hotlink. La reseña ya escrita **sobrevive** a que el producto se desactive o se retire: sigue viva, su autor la ve, la corrige y la retira igual, y sigue contando en el promedio que ven las lecturas de administración | Alta |
| `RN-PM-029` | **La reseña se retira sin motivo declarado, y no desaparece** | Al retirar | Eliminación **lógica** (`deleted_at`) y **sin motivo** que declare quien la ejecuta: es la **tercera excepción del Art. V.13**, enmendado el 14-09-2026 para el **contenido propio**. El registro de eliminación se escribe igual —instantánea incluida—, con el motivo **suplido por un valor fijo** que la especificación declara, porque el único «por qué» posible ya está en el evento: quien retira y quien escribió son la misma persona | Alta |
| `RN-PM-030` | **Del autor solo se publica su nombre y apellido** | Al leer las reseñas | La lista es **pública** (`RF-PM-012`), y de quien escribió cada reseña viaja **nombre y apellido** y nada más — ni identificador, ni nombre de usuario, ni correo, ni estado, ni roles—. Es `RN-PM-022` aplicada a otra persona: allí el vendedor, aquí el autor. Y por lo mismo, **la lista no dice cuál es la del actor**: la propia se lee aparte, con token (`RF-PM-013`) | **Crítica** |
| `RN-PM-031` | **El producto publica el promedio y la cantidad de sus reseñas vivas, en toda lectura** | Siempre que se consulte un producto, con token o sin él | Las cuatro lecturas del módulo devuelven `rating` con `average` —**dos decimales**, **nulo** cuando no hay ninguna— y `count`. Cuentan solo las **vivas**: una reseña retirada sale del promedio en el acto. Se calcula **en la misma sentencia** que trae el producto, y no con una consulta por fila: un listado que preguntara producto a producto sería el `N+1` que `RF-PM-002` existe para evitar | Alta |
| `RN-PM-032` | **Un producto puede enlazar un video, y el enlace sale en toda lectura** | Al registrar, al editar y siempre que se consulte un producto, con token o sin él | Todo producto —**de los dos tipos**— puede declarar **la dirección de un video** que lo presenta: una **URL absoluta `http` o `https`, sin espacios y de hasta 500 caracteres**, de cualquier dominio. Es **opcional**, se corrige y **se vacía** (`RF-PM-004`), su nulo significa «no tiene video» y **no condiciona la activación**. **Es un enlace, no un archivo**: el sistema comprueba su forma y **no lo sigue** —no comprueba que exista, no lo descarga, no lo incrusta—. Las **cuatro** lecturas lo devuelven, **presente y nulo** cuando no hay, **incluido el hotlink sin token**: es material de venta, no un costo (§5.2.8) | Media |

### 5.2 Por qué las críticas son críticas

**`RN-PM-001` — el tipo no cambia.** Convertir un `BOT` en `UPGRADE_MEMBRESIA` después de venderlo reescribe qué compró quien lo compró. El campo no es una etiqueta: decide qué otras columnas son obligatorias y qué derecho se adquiere.

**`RN-PM-002` — la condición va en los dos sentidos.** Un upgrade sin destino no dice a qué nivel lleva y es inservible; un bot **con** destino promete un cambio de membresía que nadie va a aplicar. La segunda mitad es la que se olvida, y es la peligrosa: no falla, promete.

**`RN-PM-016` — el icono tiene una sola mitad, y es la contraria de la anterior.** `RN-PM-002` obliga y prohíbe; esta solo prohíbe. Un upgrade **sin** icono es un producto normal, de modo que no hay nada que exigir; lo que se rechaza es el icono **de más** en un bot, que sería un dato que el frontend pintaría en un sitio donde nadie ha decidido que vaya un icono. **Y es un identificador, no una imagen**: el backend guarda el nombre —`crown`, `arrow-up-circle`— y no sabe pintarlo, igual que con el color de la membresía (`RN-SP-024`). El sistema no almacena binarios, y dónde vivirían es una decisión que esta regla no necesita abrir.

**`RN-PM-003` — el destino existe.** Sin esta regla un upgrade puede apuntar a un identificador que no es nada, y el defecto solo se ve al intentar aplicarlo: con el cobro ya hecho.

**`RN-PM-004` — un solo upgrade activo por pareja.** Dos productos activos **desde el mismo sitio y hacia el mismo sitio** son **dos precios simultáneos para exactamente lo mismo**, y quien compre pagará el que la interfaz liste primero. Esto no se descubre como un error: se descubre como una discrepancia de facturación meses después.

**Lo que la pareja SÍ admite, y antes no**: dos productos activos hacia `ORO`, uno desde `BECA` y otro desde `PLATINO`. No son el mismo producto con dos precios — **son dos saltos distintos**, y que cuesten distinto es lo normal.

**`RN-PM-024` — el precio de compra no sale de administración.** Volvió a esta sección el 12-09-2026, y por el mismo motivo por el que la había dejado el 08-09-2026, pero al revés: cuando el segundo importe era **lo que se anunciaba**, publicarlo en las cuatro lecturas era una decisión de forma y la regla dejó de ser crítica; ahora que es **lo que NEXUS paga**, publicarlo en la oferta o en el hotlink enseña **el margen** a quien compra, y en el hotlink **sin token**. Incumplirla **no falla, publica** — que es la marca de todas las críticas de este catálogo. Lo que la sostiene no es una restricción sino una **ausencia**: `OfferItem` y la respuesta del hotlink **no tienen dónde ponerlo**, y las dos consultas públicas **no lo seleccionan** (§5.2.6, §10.3).

**`RN-PM-027` — solo el autor toca su reseña.** Es crítica por lo que ocurre si se incumple: **no falla, silencia**. Una reseña retirada por quien no la escribió es una opinión que desaparece sin que su autor lo sepa ni pueda impedirlo, y un sistema en el que eso puede pasar publica solo las opiniones que alguien dejó pasar. La regla vive en el caso de uso —comparar el autor de la fila con el actor del token— y el esquema no puede sostenerla, de modo que lo único que la defiende es la prueba que intenta tocar una ajena y espera `403` (§10.4).

**`RN-PM-030` — del autor solo el nombre.** La lista es **pública**, y publicar el identificador o el nombre de usuario del autor convertiría cada reseña en una fila del padrón de clientes leíble **sin token**. Es la misma decisión que `RN-PM-022` tomó con el vendedor, y aquí pesa más: el vendedor reparte su nombre a propósito; el cliente solo quiso opinar. Lo que la sostiene es que la proyección de la lista **no tenga** el campo, igual que `OfferItem` no tiene el costo.

**`RN-PM-020` — la implementación decide si el dinero cobrado entrega algo.** Es la primera regla de este catálogo que gobierna a otro módulo: `RN-MV-020` concede la membresía comprada **solo** si el producto es automático. Omitirla —dejando que toda venta confirmada entregue— produce el defecto que este documento ya nombró una vez: **no falla, entrega**. Un producto que exigía revisión se aplicaría solo, con el cobro hecho, sin que nadie lo hubiera aprobado y sin que quedara en ningún sitio el rastro de que debía revisarse. Se desarrolla en §5.2.2.

### 5.2.1 El origen de un upgrade — 02-09-2026

Hasta esta fecha un upgrade solo decía **a dónde lleva**, y quién podía comprarlo se **deducía**: cualquiera por debajo de ese nivel. Por decisión del responsable del proyecto, ahora declara también **de dónde sale**.

**Lo que eso compra es el salto.** `BECA → ORO` no se podía expresar: la deducción por niveles ofrecía «subir a ORO» a todo el mundo por debajo, al mismo precio, sin poder distinguir a quien salta tres escalones de quien sube uno. Con el origen declarado, **cada salto es un producto** y cada uno tiene su precio.

**El origen es obligatorio en todo upgrade** (`RN-PM-002`), y de ahí sale una consecuencia que hay que aceptar entera:

!!! warning "Un origen sin producto no falla: no se ofrece"

    Si nadie declara un upgrade desde `VIP`, quien esté en `VIP` **no verá ninguna oferta de subida**. No hay error, no hay aviso, y el catálogo se ve perfectamente bien desde administración.

    Es el precio de que la oferta sea **explícita** en lugar de deducida: antes la cadena cubría sola todos los casos porque el sistema los calculaba; ahora los cubre **quien declara los productos**. Cubrir la cadena entera exige un producto por cada pareja que se quiera vender.

    Se acepta a conciencia porque la alternativa —dejar el origen opcional, con «vacío = desde cualquiera»— obliga a que **dos reglas convivan** en cada consulta de oferta, y a que un mismo comprador vea dos caminos al mismo destino sin que nadie lo haya decidido.

**Y `RF-PM-007` deja de comparar niveles.** La oferta pasa a ser una coincidencia exacta —*los upgrades cuyo origen es mi membresía*— y con ello la regla de niveles se muda: deja de ser un filtro que se evalúa en **cada consulta** y pasa a ser una validación que se comprueba **una vez, al registrar** (`RN-PM-017`).

Eso conserva sin escribir nada lo que aquel requerimiento ya decía: **quien no tiene membresía no ve ningún upgrade**, porque no coincide con ningún origen.

!!! warning "Esa coincidencia se declaró aquí el 02-09-2026 y NO se construyó hasta el 07-09-2026"

    `RN-PM-011` siguió diciendo «solo si su membresía vigente es de nivel inferior al destino» durante cinco días, y `findOffer` siguió comparando niveles. **El documento se contradecía consigo mismo**, y el código estaba del lado de la regla vieja.

    Lo destapó la renovación (§5.2.3), que **no se puede expresar comparando niveles**: abrir la comparación a «inferior o igual» le ofrecería a quien está en `ORO` un `PLATINO → ORO`, que no es suyo. La deuda se paga entera ahí.

### 5.2.2 El alcance y la implementación — 07-09-2026

Por decisión del responsable del proyecto, un producto declara desde hoy **dos cosas que hasta ahora no decía en ninguna parte**: hasta dónde se muestra y quién aplica lo que se compra. Son dos preguntas distintas y con dos respuestas independientes — un producto puede verse en todas partes y entregarse a mano, o verse solo en la tienda y aplicarse solo.

#### El alcance es una escala, y por eso no hay «solo hotlinks»

`TIENDA` y `HOTLINKS` **no se reparten el catálogo**: el segundo **incluye** al primero. Un producto de alcance `HOTLINKS` se ve en la tienda **y** en los hotlinks; uno de alcance `TIENDA` se ve solo en la tienda. La pregunta que el campo responde es «hasta dónde llega», no «en cuál de los dos está».

**La alternativa era declararlos excluyentes**, un canal cada uno, y se descartó por lo que obligaba a hacer: publicar algo en los dos sitios exigiría **dos productos** —dos códigos, dos precios que mantener sincronizados y dos filas que `RN-PM-004` tendría que aprender a distinguir—, o bien un tercer valor «ambos» que convierte el campo en una escala de tres con dos nombres que fingen ser canales.

!!! warning "Lo que la escala cuesta: no se puede esconder un producto de la tienda"

    **No existe forma de publicar algo SOLO en hotlinks.** El alcance más corto es la tienda, de modo que todo producto que quiera enlazarse aparece también en la tienda, lo quiera quien lo declara o no.

    Se acepta a conciencia, y con la salida escrita: el día que ese caso exista, lo que entra es un **tercer valor** —`SOLO_HOTLINKS`—, no un cambio de significado de los dos que hay. Cambiarles el significado reescribiría en silencio lo que ya está declarado: doscientos productos que dicen `HOTLINKS` pasarían a significar otra cosa sin que nadie tocara una fila.

**Y el alcance no filtra la tienda.** `RF-PM-007` **no puede** filtrar por él —los dos valores llegan a la tienda, de modo que el predicado sobraría—, con lo que su único uso hoy es el filtro del catálogo administrativo (`RF-PM-002`). Conviene decirlo en voz alta: **el campo se declara antes de que exista quien lo consuma**. El canal de hotlinks no está construido, y hasta que lo esté, el alcance es un dato que se escribe, se corrige y se consulta desde administración, y no cambia lo que ve nadie.

#### La implementación cruza a `MV`, y es lo primero de este catálogo que lo hace

`AUTOMATICA` significa que el sistema aplica lo comprado sin que intervenga nadie. `MANUAL` significa que **lo comprado espera a que un funcionario lo autorice**, y que confirmar el pago **no** lo entrega.

Hasta hoy `RN-MV-020` decía que una venta confirmada con un upgrade concede la membresía comprada, **sin distinguir**. Desde hoy la concede **solo si el producto es automático** ([`requirements/mv.md` §5.4](mv.md), v0.9.0). Ese es todo el efecto del campo, y es grande: **el catálogo pasa a gobernar lo que otro módulo hace con el dinero ya cobrado**.

**Lo que no cambia es el cobro.** Una venta de un producto manual se confirma con normalidad cuando el dinero entra: lo que queda pendiente no es el pago, es la **entrega**. Distinguir las dos cosas es lo que permite que `RN-MV-005` —de `CONFIRMADA` no se sale— siga intacta.

!!! danger "La condición que este cambio le impone a `MV`, y que todavía no está construida"

    **La implementación tiene que copiarse en la línea de la venta**, junto al importe y la vigencia que `RF-MV-001` ya copia. El criterio es el que este proyecto tiene escrito desde el 01-09-2026 en [`modelo-datos.md` §4.1](../modelo-datos.md): **se copia lo que puede cambiar; lo inmutable se referencia**. La membresía destino se referencia porque `RF-PM-004` la rechaza; la implementación **se corrige**, de modo que se copia.

    Sin esa copia, corregir un producto de `AUTOMATICA` a `MANUAL` dejaría **esperando autorización a ventas que se hicieron cuando el producto se entregaba solo** — y al revés, entregaría sin revisión lo que se vendió con revisión prometida. Ninguna de las dos falla: las dos entregan mal y con el cobro hecho.

    **`movement_details` no tiene hoy esa columna** (`V54`), y esta decisión **no la escribe**: `RF-MV-003` —quien lee el valor— está en `Pendiente` y bloqueado por **D-26**, de modo que hoy no hay nadie que la lea. Lo que queda declarado es que **la copia debe existir antes de que ese requerimiento se construya**, y no después.

#### Las dos son obligatorias, en los dos tipos, y las dos se corrigen

**Obligatorias sin valor por omisión**, ni en el esquema ni en la petición: un producto sin alcance no se sabe dónde se ve, y uno sin implementación no se sabe quién lo entrega. Poner un `DEFAULT` habría dejado que la columna tomara una decisión comercial que nadie escribió — y el defecto no se vería, porque un producto con el valor por omisión se ve exactamente igual que uno declarado.

**En los dos tipos**, y ahí se apartan de `RN-PM-002` y `RN-PM-016`: aquellas obligan o prohíben **según el tipo**, y estas no distinguen. Un bot también se muestra en algún sitio y también se entrega de alguna forma — de hecho es el caso donde la implementación manual es más probable, porque una prestación del sistema puede exigir que alguien la active.

**Y se corrigen**, al revés que el tipo y las dos membresías. La frontera es la misma de siempre: lo que define **qué derecho otorga** el producto no se toca; lo que define **dónde se ve y cómo se entrega**, sí. Mover un producto de la tienda a los hotlinks no puede costar un alta y un retiro.

### 5.2.3 La renovación — 07-09-2026

Por decisión del responsable del proyecto, **un upgrade puede declarar la misma membresía en los dos lados**: `BECA → BECA`, `ORO → ORO`. Hasta hoy `RN-PM-017` lo rechazaba con `VAL-014`.

**Qué es lo que se vende ahí.** No un cambio de nivel, sino **tiempo**: la vigencia que el producto declara (`RN-PM-015`), contada desde la confirmación. Quien está en `ORO` y compra `ORO → ORO` sigue en `ORO`, con el periodo que acaba de pagar. Es exactamente lo que `RN-MV-020` hace ya con cualquier upgrade —cierra la membresía abierta e inserta la comprada—, sin una sola línea nueva: la fila que se cierra y la que se abre son del mismo nivel.

**Lo que la regla prohibía sin motivo, y lo que sigue prohibiendo.** `RN-PM-017` tenía dos mitades metidas en una: «no bajes» y «no repitas». La primera protege de vender un descenso llamándolo upgrade y **se queda**; la segunda solo impedía cobrar por tiempo, que es un producto legítimo y de los más comunes que existen. La comparación pasa de estricta a **mayor o igual**.

!!! danger "Y cae `ck_products_origen_distinto`, que era la mitad que el motor sostenía"

    Esa restricción decía `source_membership_id <> target_membership_id`, y era justo lo que ahora se admite. **Se retira en `V61`.**

    Queda dicho lo que eso significa: de `RN-PM-017` **ya no queda nada declarado en el esquema**. La mitad que sobrevive —«el origen no está por encima»— necesita el `level` de dos filas de `memberships` y un `CHECK` no consulta otra tabla, de modo que **vive entera en el caso de uso**. Es el mismo reparto que `RN-PM-007` tiene con los decimales de la moneda, con la diferencia de que aquí antes había una red y ahora no.

#### Por qué esto obligó a construir la coincidencia por origen

**Una comparación de niveles no puede expresar una renovación.** La oferta filtraba con `level(destino) < level(actor)`; para que quepa el mismo nivel habría que abrirla a `<=`, y entonces a quien está en `ORO` se le ofrecería también `PLATINO → ORO` — un producto **cuyo origen no es el suyo**. El filtro no distingue «renovar lo mío» de «el salto de otro que acaba en mi nivel».

Lo que sí lo distingue es la **coincidencia exacta por origen**, que §5.2.1 declaró decidida el 02-09-2026 y que nunca se construyó. Con ella:

| Actor | Producto | ¿Se le ofrece? |
|---|---|---|
| `BECA` | `BECA → BECA` | **Sí** — es su renovación |
| `BECA` | `BECA → ORO` | **Sí** — es su salto |
| `ORO` | `PLATINO → ORO` | **No** — el origen no es suyo |
| `ORO` | `ORO → ORO` | **Sí** — es su renovación |
| Sin membresía | cualquiera | **No** — no coincide con ningún origen |

**Y la garantía de «no se ofrecen bajadas» no se pierde al quitar el filtro de niveles**: la sostiene `RN-PM-017`, comprobada **al registrar**. Un producto cuyo origen sea la membresía del actor no puede apuntar por debajo, porque no habría podido darse de alta.

#### Lo que esto le pide a `MV`

`RN-MV-006` rechazaba comprar una membresía **igual o inferior** a la vigente. La mitad de «igual» pasa a admitirse: renovar es exactamente eso. **La de «inferior» se queda**, y es la que protege de cobrar una bajada.

Conviene leer que el código de `MV` ya lo había anticipado por escrito: `RegisterSaleService.verificarQueSube` advertía que la comprobación existe aunque la oferta la garantice, «porque la oferta puede ampliarse — **el día que se vendan renovaciones del mismo nivel**, por ejemplo». Ese día es hoy.

### 5.2.4 Los dos precios — 08-09-2026

!!! warning "El segundo precio cambió de significado el 12-09-2026"

    Lo que sigue describe el segundo importe como **precio público** —lo que se anunciaba—. Desde el 12-09-2026 la misma columna es el **precio de compra**: lo que NEXUS paga por el producto (§5.2.6). Se conserva porque las decisiones que se tomaron aquí —que el segundo importe **no se cobra**, que su **nulo significa algo**, que `RN-PM-006` admite el cero— siguen vigentes; lo que cambió es **qué es** ese número y **quién puede verlo**.

Por decisión del responsable del proyecto, un producto declara desde hoy **dos importes**, y solo uno de ellos es dinero:

| | Qué es | Quién lo ve | Qué hace |
|---|---|---|---|
| **Precio del sistema** (`price`) | Lo que cuesta el producto | Solo quien tenga `products:read` | **Es el que se cobra**: lo copia `movement_details.unit_price`, y sobre él calcula `CM` (`RN-CM-019`) |
| **Precio público** (`public_price`) | Lo que se anuncia | Todo el mundo, y es **lo único** que ve quien no administra | **Nada.** No se copia, no comisiona, no interviene en ningún cálculo |

**El precio público es opcional, y su nulo significa algo.** No es «cero» ni «sin dato»: es **este producto no declara precio público**, y entonces lo que se muestra es el del sistema. Es lo que permite que la migración no invente un valor para lo ya registrado y que declarar dos precios sea un acto deliberado, no el estado por omisión de cada producto del catálogo.

**Por qué no se reutiliza `price` para lo que se muestra.** La alternativa era guardar solo el importe anunciado y calcular el otro, y no se sostiene: no hay ninguna operación que relacione los dos —no es un porcentaje, ni un impuesto, ni un redondeo— porque **la relación es una decisión comercial que se toma producto a producto**. Lo único que puede guardarla es una segunda columna.

#### Lo que cuesta, escrito entero

!!! danger "Quien compra ve un importe y se le cobra el otro"

    La oferta (`RF-PM-007`) y el hotlink (`RF-PM-008`) enseñan el **precio público**; la venta (`RF-MV-001`, `RF-MV-002`) cobra y copia el **del sistema**. Si los dos números no coinciden, **el comprador ve uno y paga otro**, y no hay nada en el sistema que lo impida: `RN-PM-006` acota cada importe por separado y **ninguna regla los compara entre sí**.

    Se acepta a conciencia, porque comparar los dos es cerrarle la puerta al caso que el campo existe para permitir —anunciar por debajo de lo que se cobra es tan legítimo como lo contrario, y quién decide eso es quien pone los precios—, y porque **la salida es barata y está escrita**: `public_price >= price` es un `CHECK` entre dos columnas de la misma fila, una migración de tres líneas el día que se decida que el anuncio nunca puede quedar por debajo.

    Lo que **no** se puede hacer es taparlo con la interfaz: mientras los dos importes existan y solo uno se cobre, quien los declara es el único que puede mantenerlos coherentes.

!!! warning "Este aviso dejó de aplicar el 08-09-2026, y se conserva para que se entienda el cambio"

    Lo que sigue describe la regla **anterior**, la que acotaba el precio del sistema al catálogo. Con `RN-PM-024` reescrita (§5.2.5), el comprobante ya no es una excepción a nada: el precio del sistema se publica en todas partes, de modo que el caso que este aviso protegía —que alguien «arreglara» la fuga ocultando el importe de la venta— dejó de existir.

!!! warning "`RN-PM-024` se rompía en el comprobante, y ahí era correcto que se rompiera"

    Un cliente **acaba viendo el precio del sistema**: en cuanto compra, `RF-MV-002` le devuelve la venta con el importe que se le cobró. La regla acota **el catálogo y la oferta**, no el comprobante — un documento que no dice lo que se cobró no sirve para nada, y ocultarlo ahí sería el defecto grave, no la fuga.

    Queda escrito para que nadie intente «arreglarlo» después ocultando el importe de la venta.

#### La condición que este cambio le impone a `CM`, y que sí hubo que construir

`RN-PM-006` admite desde hoy el **precio cero**, y eso rompe una cuenta que ya existía. `RN-CM-019` convierte un valor fijo a su porcentaje equivalente con `fixed_amount ÷ precio × 100`, y `ProductCommissionCapGuard` **confiaba por escrito en que el precio nunca fuera cero**, citando la restricción que este cambio relaja: con un producto gratuito, esa división es un fallo aritmético y un `500`.

La resolución no necesita una regla nueva, porque es lo que `RN-CM-019` ya dice llevado al límite: **sobre un producto de precio cero, cualquier valor fijo mayor que cero paga más del 100 % de lo que el producto cobra**, y se rechaza con el mismo mensaje que cualquier otro exceso. Un valor fijo de cero ocupa cero. Ver [`requirements/cm.md` §5.2](cm.md).

### 5.2.5 Los dos precios se publican, y la conversión con ellos — 08-09-2026

!!! warning "La mitad de esta sección se deshizo el 12-09-2026"

    **La conversión sigue viajando en las cuatro lecturas**, y eso se conserva. **Lo que se deshizo es que el segundo importe viaje con ella**: convertido en precio de compra, salió de la oferta y del hotlink (§5.2.6). El párrafo de lo que esta decisión publicaba dejó de aplicar: ya no hay diferencia entre lo anunciado y lo cobrado, porque **ya no hay nada anunciado** — `price` es lo único que ve quien compra.

**Decisión del responsable del proyecto, tomada el mismo día que la anterior y sobre la advertencia de lo que cuesta.** `RN-PM-024` decía que la oferta y el hotlink devolvían **un solo importe**; ahora las **cuatro** lecturas del módulo devuelven **los dos** —`price` y `publicPrice`— y además `exchange`, la conversión a la moneda por omisión.

**Lo que se gana es una sola forma.** Hasta hoy el front leía `price` con dos significados según el endpoint: en el catálogo era el del sistema y en la oferta y el hotlink era «el que se muestra», resuelto por un `COALESCE` que el cliente no veía. Dos endpoints del mismo módulo devolvían el mismo nombre de campo con distinto contenido, y **nada en la respuesta decía cuál era cuál**. Con los dos campos siempre presentes, `price` es siempre lo que se cobra y `publicPrice` siempre lo que se anuncia — o **nulo**, que significa «este producto no declara precio público» y no «cero».

**Y la conversión deja de ser cosa del hotlink.** Era el único sitio donde se veía a cuánto sale un producto en la moneda de casa, de modo que el catálogo y la oferta enseñaban importes en monedas distintas sin nada que los hiciera comparables. Ahora el bloque viaja en las cuatro, **presente y nulo** cuando el producto ya está en la moneda por omisión o cuando nadie declaró una tasa vigente — que no es un error y no convierte la lectura en un `404`.

!!! danger "Lo que esta decisión publica, dicho entero"

    **La diferencia entre lo que se anuncia y lo que se cobra queda visible, y en el hotlink sin token.** Quien abra un enlace repartido por WhatsApp ve los dos números y la resta es inmediata. Era exactamente lo que la regla anterior existía para impedir, y se retira **a conciencia**: lo advertí antes de construirlo y el responsable del proyecto lo confirmó.

    **No hay vuelta atrás sobre lo ya publicado.** Lo que un endpoint público devolvió una vez está fuera; si algún día se decide volver a ocultarlo, lo que se recupera es lo de mañana, no lo de ayer.

    **Y sigue sin haber ninguna regla que compare los dos importes** (§5.2.4): anunciar por debajo de lo que se cobra es tan legítimo como lo contrario, y ahora esa diferencia —además de no validarse— se publica.

**La conversión se calcula sobre el importe que se muestra** —el público si existe y el del sistema si no—, y no sobre los dos. Es el único de ellos que el comprador va a comparar con lo que ve, y dar dos importes convertidos obligaría a decir en la respuesta cuál corresponde a cuál, que es la ambigüedad que este cambio viene a quitar.

### 5.2.6 El precio público se convierte en precio de compra — 12-09-2026

**Decisión del responsable del proyecto.** El segundo importe que nació el 08-09-2026 como **lo que se anuncia** pasa a ser **lo que NEXUS paga por el producto**: el precio de compra. La columna es la misma —`public_price` se renombra a `purchase_price` y conserva forma, opcionalidad y `CHECK`—, pero **el número significa otra cosa, y eso cambia quién puede verlo**.

| | Antes (08-09-2026) | Ahora (12-09-2026) |
|---|---|---|
| Qué es | Lo que se anuncia a quien no administra | **Lo que NEXUS paga** cuando tiene que comprar el producto; ahí se guarda lo que costó |
| Quién lo ve | Todo el mundo, incluso sin token | **Solo `products:read`**: `RF-PM-002` y `RF-PM-003` |
| Qué hace | Nada: no se cobra ni se calcula | **Lo mismo**: no se cobra, no comisiona, no se convierte |
| Su nulo | «Se anuncia con el del sistema» | **«No se conoce»**: no se ha comprado todavía, o no aplica |
| Sobre qué se convierte | El que se muestra: el público si existe | **Siempre `price`** — es el único importe que hay que comparar con la moneda de casa |

**Tres cosas se preguntaron antes de escribir y las tres quedaron decididas por el responsable del proyecto:** que es **el costo** y no lo que paga el cliente —`price` sigue siendo lo único que la venta copia—; que **solo se ve en administración**; y que **sigue siendo opcional**, porque un producto que todavía no se ha comprado no tiene costo que declarar, y la migración no tiene ningún valor honesto que inventar para las filas de hoy.

**Lo que se simplifica.** Desaparece «el importe que se muestra»: la conversión se calculaba sobre el público si existía y sobre el del sistema si no, y ese `if` vivía en un solo sitio para que nadie lo invirtiera. Ya no hace falta: **`price` es lo único que se muestra fuera de administración**, y `exchange` se calcula siempre sobre él, en las cuatro lecturas. La advertencia de §5.2.4 —«quien compra ve un importe y se le cobra el otro»— **deja de aplicar**, porque quien compra ve **uno solo**.

**Lo que vuelve.** `RN-PM-024` recupera su forma crítica original —«el otro precio no sale de administración»— con el costo en el lugar donde estuvo el precio del sistema. Y vuelve con la misma sujeción: **el esquema no puede decir que un número no se publique**; lo único que lo sostiene es que `OfferItem` y la respuesta del hotlink **no tengan el campo** y que sus dos consultas **no lo seleccionen**. Por eso las pruebas de esas dos lecturas vuelven a probar una **ausencia**, como el 08-09-2026 por la mañana y al contrario que por la tarde.

!!! danger "Lo que un hotlink publicó ya está fuera"

    Entre el 08-09-2026 y hoy, el hotlink devolvió `publicPrice` sin token. Ese número era **lo que se anunciaba**, no el costo, de modo que **ningún precio de compra llegó a publicarse**: la columna cambia de significado **vacía de ese significado**. Quien quiera guardar costos en ella puede hacerlo desde hoy sin que nada anterior los haya enseñado.

    Lo que sí queda dicho: **si algún día se decide volver a publicar el segundo importe, hay que decidirlo sabiendo que es el margen**. `CA-PM-160` y `CA-PM-163` se reescriben para exigir la ausencia, y la prueba que invirtió `CA-PM-169` **se invierte de vuelta** en vez de borrarse.

### 5.2.7 Las reseñas — 14-09-2026

**Decisión del responsable del proyecto.** Un producto se reseña: una puntuación de uno a cinco y un texto, con quién lo escribió y cuándo se escribió, se corrigió y se retiró. Y **solo quien la escribió puede retirarla.** Es la primera vez que este módulo guarda algo que **escribe un cliente**, y cuatro cosas se preguntaron antes de escribir una línea. Las cuatro quedaron decididas por él:

| Pregunta | Decisión | Lo que se descartó, y por qué |
|---|---|---|
| **¿Quién puede reseñar?** | Quien porte **`products:comment`**, un permiso nuevo (§4) | *`products:sale`* — ver la oferta y opinar son dos capacidades, y quien administre roles tiene que poder darlas por separado. *Cualquier autenticado* — va contra la decisión del 02-09-2026 que le puso permiso a la vista de venta. *Solo quien compró* — exige leer las ventas de `MV`, y `PM` no puede consumir a `MV` sin cerrar el ciclo `MV → PM → MV`: la reseña tendría que vivir en `MV` o en un módulo nuevo, y una opinión sobre el producto es del catálogo |
| **¿Cuántas por persona y producto?** | **Una, y se corrige** (`RN-PM-026`) | *Varias sin límite* — es un hilo, no una reseña: el promedio se sesga por quien más escribe y la edición pierde sentido |
| **¿Motivo al retirar?** | **No: se enmienda el Art. V.13** con una tercera excepción, el **contenido propio** (`RN-PM-029`) | *Exigir motivo como en `RF-PM-006`* — cumplía la constitución sin tocarla, y se descartó porque pedirle a un cliente que justifique por qué borra lo suyo produce «lo borro» en cada fila, que es exactamente el ruido que la excepción de las asociaciones existe para evitar |
| **¿Dónde se leen, y qué publica el producto?** | **Lista pública, y promedio y cantidad en las cuatro lecturas** (`RN-PM-030`, `RN-PM-031`) | *Solo el endpoint, sin tocar las lecturas* — obliga al front a una segunda llamada por producto para pintar estrellas en la oferta. *Lista solo autenticada* — el hotlink, que es público, no podría enseñar reseñas |

#### Lo que la decisión de la moderación cuesta, escrito entero

!!! danger "Nadie puede retirar una reseña ajena, y eso incluye a la administración"

    `RN-PM-027` no tiene excepción. Una reseña injuriosa, falsa o escrita para hundir un producto **se queda hasta que su autor la retire**, y la única acción posible desde administración es sobre la **persona** —`RF-SP-028`, cambiarle el estado— y no sobre la reseña, que sigue publicada mientras exista.

    Se acepta a conciencia, y con la salida escrita: **la moderación es otro requerimiento con otro permiso** —`products:moderate`, por ejemplo—, que retiraría **con motivo** porque quien lo ejerce no es el autor, y por tanto no cabe en la excepción del contenido propio. Añadirlo es una operación nueva; no es una relajación de esta regla, y no debe construirse como tal.

#### Por qué la reseña lleva `user_id` y eso no infringe el Art. V.7

El Art. V.7 prohíbe que **el actor de cada cambio** se duplique en la tabla: quién creó, corrigió o retiró una fila vive solo en la auditoría. `product_comments.user_id` no es eso: es **el autor de la opinión**, un dato de negocio sin el cual la fila no significa nada — igual que `user_commission_rates.user_id` dice de quién es la excepción y `user_memberships.user_id` de quién es el nivel. Quién **corrigió** la reseña sigue viviendo en `audit_change_log`; coincide con el autor porque `RN-PM-027` lo obliga, no porque la columna lo diga.

#### Lo que la lista pública publica, y lo que no

Las reseñas de un producto se leen **sin token** porque la pantalla del hotlink las necesita y no tiene con qué autenticarse. La lista responde **solo sobre productos activos y no retirados** (`RN-PM-028`), y lo demás recibe el `404` uniforme del hotlink — un anónimo no debe poder saber si un identificador corresponde a un producto en preparación.

!!! warning "Las reseñas de un producto de alcance `TIENDA` se leen sin token, y el producto no"

    La oferta exige `products:sale`; la lista de reseñas no exige nada, y **no distingue el alcance**. De modo que quien tenga el identificador de un producto que solo se vende dentro puede leer sus reseñas desde fuera, y con ellas el nombre de quien opinó.

    Se acepta porque el identificador no se publica en ningún sitio sin token —el hotlink resuelve por **código**, no por identificador— y porque la alternativa, que la lista exigiera token según el alcance del producto, haría que **el mismo endpoint fuera público o no según el dato**, que es lo que `security.md` §6 pide evitar. **La salida es barata y está escrita**: acotar la lista anónima a `scope = 'HOTLINKS'` es un predicado más, el día que se decida.

**Y la lista no dice cuál reseña es la del actor.** Un campo `mine` obligaría a que la respuesta cambiara con el token, y el hotlink ya fijó que una ruta pública responde lo mismo a todo el mundo (`RF-PM-008` §3). La reseña propia se lee **aparte**, con `products:comment` (`RF-PM-013`), que es de donde el front saca qué prellenar en el formulario de corrección.

#### El promedio es una cuenta, no una columna

`rating.average` y `rating.count` **no se guardan en `products`**: se calculan sobre las reseñas vivas cada vez, en la misma sentencia que trae el producto. Una columna desnormalizada obligaría a mantenerla en el alta, la corrección y el retiro de cada reseña, y **la que se quedara atrás no fallaría, mentiría** — que es la marca de todo lo que este catálogo decide no duplicar. El coste es un agregado por producto en cada lectura, y con un índice por `(product_id) WHERE deleted_at IS NULL` es una lectura de índice; el día que el catálogo tenga cien mil reseñas por producto se revisa, y ese día no es hoy.

### 5.2.8 El video — 14-09-2026

**Decisión del responsable del proyecto.** Un producto puede enlazar un video: **la dirección donde el video vive, no el video**. Es la segunda cosa que el producto declara para que el frontend la pinte y que el sistema no sabe interpretar —la primera fue el icono (`RN-PM-016`)—, y como entonces, cuatro cosas se preguntaron antes de escribir una línea. Las cuatro quedaron decididas por él, y las cuatro del lado más abierto:

| Pregunta | Decisión | Lo que se descartó, y por qué |
|---|---|---|
| **¿Dónde se ve?** | **En las cuatro lecturas**, incluido el hotlink sin token (`RN-PM-032`) | *Solo administración, como el precio de compra* — el video es **material de venta**: existe para que lo vea quien compra, y esconderlo de la oferta y del hotlink lo dejaría justo donde nadie lo necesita. *Solo oferta y hotlink* — administración no podría revisar lo que publica |
| **¿Qué se valida?** | **Una URL absoluta `http` o `https`, sin espacios y de hasta 500 caracteres, de cualquier dominio** | *Solo YouTube y Vimeo* — cada plataforma nueva sería una migración, y el sistema no gana nada sabiendo dónde está alojado el video: no lo descarga ni lo incrusta. *Texto libre con tope* — el front recibiría algo que no es un enlace y fallaría al pintarlo, y el esquema no podría decir nada de la columna |
| **¿En qué tipos?** | **En los dos**, `BOT` y `UPGRADE_MEMBRESIA` | *Solo en uno, como el icono es solo del upgrade* — el icono tiene esa mitad porque nadie decidió dónde iría en un bot; un video explica igual una prestación que una subida de nivel, y no hay ninguna condición cruzada que justificar |
| **¿Es obligatorio?** | **No: opcional, se corrige y se vacía** (`RF-PM-004`), y **no condiciona la activación** | *Obligatorio al activar, como la descripción (`RN-PM-014`)* — la descripción es lo mínimo para saber qué se compra; el video es un complemento, y exigirlo dejaría sin publicar todo producto que no tenga uno. *Obligatorio siempre* — la migración no tiene ningún enlace honesto que inventar para lo ya registrado |

#### Es un enlace, y el sistema no lo sigue

Como el icono es un nombre y no una imagen, **el video es una dirección y no un archivo**: el sistema guarda lo que se declaró, comprueba que tiene forma de enlace y **nada más**. No comprueba que el video exista, no lo descarga, no lo incrusta y no sabe si el enlace sigue vivo mañana. Es la frontera de siempre —el sistema no almacena binarios— llevada un paso más allá: **tampoco los consulta**. Hacerlo obligaría al backend a salir a Internet en cada alta y en cada corrección, con lo que un producto no podría registrarse mientras la plataforma del video esté caída, y dejaría al sistema dependiendo de un tercero para algo que el frontend resuelve solo al pintar el enlace.

!!! warning "Lo que se publica sin token es una dirección que alguien escribió"

    El hotlink devuelve el enlace **tal cual se declaró**, a cualquiera y sin token. Quien administra el catálogo puede escribir cualquier dirección `https://`, y el sistema **no la sigue ni la valida más allá de su forma**: un enlace roto, o uno que lleve a otro sitio, se publica igual. Es el mismo trato que la descripción —texto que escribe administración y lee el público— y se acepta por lo mismo: la confianza está en `products:create` y `products:update`, no en el dato.

**Y `RN-PM-024` no se toca.** El video va a las cuatro lecturas precisamente porque **no es el costo**: aquella regla dice qué no sale de administración, y el enlace de un video es lo contrario de un margen — es lo que se quiere que vean. Lo que sí hereda de aquella es la forma del nulo: **presente y nulo** cuando no hay video, en las cuatro, porque un campo que desaparece es indistinguible de uno que el cliente no conoce.

### 5.3 Reglas de otros documentos que este módulo aplica

No se copian: se referencian, porque dos copias de una regla acaban divergiendo.

| Regla | Dónde vive | Cómo alcanza a este módulo |
|---|---|---|
| `RN-SP-006`, `RN-SP-007` | [`requirements/sp.md` §5.1](sp.md#51-reglas-propias-del-modulo) | La cadena de membresías es **lineal y ordenada por `level`**. `RN-PM-011` se apoya en ese orden: sin él, «nivel superior» no significa nada |
| `RN-SP-018` | [`requirements/sp.md` §5.1](sp.md#51-reglas-propias-del-modulo) | Consumidor ⟺ membresía. Es lo que garantiza que todo cliente tenga un nivel del que partir, y por tanto que `RF-PM-007` pueda decidir su oferta |
| `RN-SP-010` | [`requirements/sp.md` §5.1](sp.md#51-reglas-propias-del-modulo) | El catálogo de monedas no se edita por API. Este módulo lo **lee**, nunca lo toca |
| `RN-SEG-003` | [`security.md` §4](../security.md) | Los cinco permisos `products:` se conceden por rol como cualquier otro, y ningún rol puede conceder lo que su padre no tiene |
| Art. V.13 | [`constitution.md`](../constitution.md) | Toda eliminación exige motivo, que viaja al registro de eliminación con la instantánea de lo borrado. **Desde el 14-09-2026 con una tercera excepción, escrita para este módulo**: el **contenido propio** se retira sin motivo declarado (`RN-PM-029`), y la instantánea viaja igual |
| Art. V.7 | [`constitution.md`](../constitution.md) | El actor de cada cambio no se duplica en la tabla. `product_comments.user_id` **no es el actor, es el autor** (§5.2.7): un dato de negocio, no una copia de la auditoría |

---

## 6. Requerimientos funcionales

### 6.1 Resumen

| ID | Requerimiento | Prioridad | Permiso | Estado |
|---|---|---|---|---|
| `RF-PM-001` | Registrar producto | **Crítica** | `products:create` | **En desarrollo** |
| `RF-PM-002` | Consultar productos | **Crítica** | `products:read` | **En desarrollo** |
| `RF-PM-003` | Consultar el detalle de un producto | Alta | `products:read` | **En desarrollo** |
| `RF-PM-004` | Editar producto | Alta | `products:update` | **En desarrollo** |
| `RF-PM-005` | Cambiar el estado de un producto | Alta | `products:update` | **En desarrollo** |
| `RF-PM-006` | Eliminar producto | Media | `products:delete` | **En desarrollo** |
| `RF-PM-007` | Consultar la oferta disponible para uno mismo | Alta | `products:sale` | **En desarrollo** |
| `RF-PM-008` | Consultar un hotlink: producto y vendedor, sin autenticación | Alta | **Público** | **En desarrollo** |
| `RF-PM-009` | Reseñar un producto | Alta | `products:comment` | **Tasks en revisión** |
| `RF-PM-010` | Corregir la reseña propia | Media | `products:comment` | **Tasks en revisión** |
| `RF-PM-011` | Retirar la reseña propia | Media | `products:comment` | **Tasks en revisión** |
| `RF-PM-012` | Consultar las reseñas de un producto, sin autenticación | Alta | **Público** | **Tasks en revisión** |
| `RF-PM-013` | Consultar la reseña propia sobre un producto | Media | `products:comment` | **Tasks en revisión** |

**Prioridades:** Crítica · Alta · Media · Baja.
**Estados:** los de [`requirements.md` §4](../requirements.md#4-matriz-de-trazabilidad), que es su autoridad.

!!! note "Un solo requerimiento de alta para los dos tipos"

    Podría haber dos —«registrar upgrade» y «registrar bot»—, y se decidió que no: es **un endpoint, un caso de uso y una tabla**, con una validación condicional según el tipo. Partirlo obligaría a dos tripletas que describen la misma operación y a dos Pull Requests sobre el mismo controlador, lo que choca con el Art. XIV.2 en lugar de servirlo.

    El precedente es `RF-SP-024`, que aplica tres reglas condicionales en los dos sentidos —consumidor ⟺ membresía, vendedor ⟺ superior— dentro de un solo requerimiento de alta.

**Orden sugerido de implementación:** `RF-PM-001` → `RF-PM-002` → `RF-PM-003` → `RF-PM-005` → `RF-PM-004` → `RF-PM-006` → `RF-PM-007`.

El alta crea la tabla y el catálogo, y sin catálogo no hay nada que consultar. `RF-PM-007` va **al final** porque es el único que necesita la membresía vigente del actor: de las tres interfaces que `SP` publica (D-25), las otras dos —membresía y moneda— las necesita ya `RF-PM-001`.

**Las reseñas van en su propio orden**: `RF-PM-009` → `RF-PM-012` → `RF-PM-013` → `RF-PM-010` → `RF-PM-011`. El alta crea la tabla y siembra el permiso; la lista pública va segunda porque es la que enseña el resultado y la que obliga a resolver el `JOIN` a `users`; la propia, tercera, porque las dos escrituras que siguen la necesitan para saber qué corregir. **`RN-PM-031` —el promedio en las cuatro lecturas— se construye con `RF-PM-009`** y no con la lista: es una enmienda a cuatro requerimientos ya construidos (Art. I.7), y conviene que exista desde la primera reseña escrita.

### 6.2 Fichas

#### `RF-PM-001` — Registrar producto

| Campo | Valor |
|---|---|
| Objetivo | Poner en el sistema algo que se puede vender, con su precio |
| Actor | Administrador |
| Permiso requerido | `products:create` |
| Prioridad | **Crítica** |
| Reglas aplicables | `RN-PM-001` a `RN-PM-008`, `RN-PM-012`, `RN-PM-013`, `RN-PM-019`, `RN-PM-020`, `RN-PM-023`, `RN-PM-032` |
| Depende de | — |
| Tripleta | `docs/specs/pm/001-registrar-producto/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Registra un producto declarando su **tipo**, su nombre, su precio y su moneda; si el tipo es `UPGRADE_MEMBRESIA`, además **de qué membresía sale y a cuál lleva**, las dos obligatorias ahí y prohibidas en el otro tipo. Es el requerimiento que crea la tabla del módulo y **siembra sus cuatro permisos**, con la obligación de asociarlos a `SUPERADMIN` y `ADMIN` en la misma migración ([`security.md` §4.4](../security.md#44-catalogo-de-permisos)): olvidarlo no falla al aplicar la migración, deja a `ADMIN` incapaz de conceder lo que no tiene.

**Desde el 07-09-2026 declara además el alcance y la implementación**, las dos **obligatorias y en los dos tipos** (`RN-PM-019`, `RN-PM-020`). No tienen valor por omisión ni en el esquema ni en el cuerpo de la petición, y es deliberado: omitir cualquiera de las dos sería dejar que la columna tomara una decisión comercial —dónde se ve el producto, quién lo entrega— que nadie escribió.

**Y desde el 08-09-2026 admite un segundo precio** (`RN-PM-023`), que **sí es opcional** y ahí se aparta de las dos anteriores: omitirlo no deja ninguna decisión sin tomar. **Desde el 12-09-2026 ese segundo precio es el de compra** —lo que NEXUS paga por el producto—, y su nulo significa «no se conoce todavía», que es el estado natural de un producto que se registra antes de comprarse. Los dos importes se validan igual —no negativos, y con los decimales de la **única** moneda del producto—, y el alta devuelve los dos porque quien registra tiene `products:read`.

**Desde el 14-09-2026 admite también el enlace de un video** (`RN-PM-032`), **opcional y en los dos tipos**, validado **solo en su forma**: URL absoluta `http` o `https`, sin espacios, hasta 500 caracteres. Ausente y nulo significan lo mismo —«no tiene video»— y el alta lo devuelve **presente y nulo**, como el precio de compra. El sistema no sigue el enlace (§5.2.8).

#### `RF-PM-002` — Consultar productos

| Campo | Valor |
|---|---|
| Objetivo | Ver y encontrar lo que hay en el catálogo, incluido lo que no se ofrece |
| Actor | Administrador · fuerza comercial |
| Permiso requerido | `products:read` |
| Prioridad | **Crítica** |
| Reglas aplicables | `RN-PM-024`, `RN-PM-032` |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/002-consultar-productos/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Devuelve el catálogo **paginado**, con filtros por tipo, estado, membresía **de origen o de destino**, **alcance** e **implementación**, y búsqueda por nombre. Incluye lo inactivo y **excluye lo eliminado salvo que se pida expresamente**, porque un catálogo que oculta lo retirado impide entender por qué un producto dejó de venderse.

**Devuelve los DOS precios y la conversión vigente** (`RN-PM-024`). Desde el 12-09-2026 **vuelve a ser uno de los dos únicos sitios donde se ven juntos**: el de venta y el de compra, con la conversión calculada sobre el primero. Lo que distingue a esta de la oferta y del hotlink es el costo **y** lo demás —el estado, lo retirado, la membresía de origen—. **No se filtra por ninguno de los dos precios**: el filtro por rango quedó fuera del alcance el 26-08-2026 y el precio de compra no lo reabre.

**La conversión llega resuelta y no cuesta una consulta por fila** (§5.2.5): la moneda por omisión se pide **una vez** por página y las tasas de todas las monedas presentes **en una sola sentencia**. Un listado que preguntara por producto sería el `N+1` que `RF-PM-002` existe para evitar.

**Cada fila trae el enlace del video** (`RN-PM-032`, 14-09-2026), **presente y nulo** en los productos que no lo declaran. **No es un filtro**: se selecciona en la misma sentencia y la consulta no gana ninguna condición.

**Los dos filtros nuevos entran con las columnas** (07-09-2026) y no en una ampliación posterior. El del alcance es el **único sitio del sistema donde ese dato se puede consultar hoy**: `RF-PM-007` no lo filtra —no puede, §5.2.2— y el canal de hotlinks que lo consumirá todavía no existe, de modo que sin este filtro el alcance sería un dato que se declara, se corrige y no se puede ver.

#### `RF-PM-003` — Consultar el detalle de un producto

| Campo | Valor |
|---|---|
| Objetivo | Ver todo lo que se sabe de un producto, incluido su retiro |
| Actor | Administrador · fuerza comercial |
| Permiso requerido | `products:read` |
| Prioridad | Alta |
| Reglas aplicables | `RN-PM-024`, `RN-PM-032` |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/003-consultar-detalle-producto/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Devuelve un producto por su identificador con sus datos completos y, cuando es un upgrade, **las dos membresías resueltas** —código, nombre y nivel de cada una— y no solo sus identificadores: un detalle que obliga a una segunda llamada para ser legible no es un detalle.

Devuelve además **el alcance y la implementación** (`RN-PM-019`, `RN-PM-020`): son configuración declarada y no se deducen de ningún otro campo, de modo que un detalle sin ellas obligaría a abrir la edición para saber dónde se publica un producto y cómo se entrega.

**Y devuelve los dos precios, con el de compra en nulo cuando no se conoce** (`RN-PM-023`): presente y nulo, no ausente. La distinción es la misma que este módulo ya hace con el destino de un bot — un campo que falta es indistinguible de uno que el cliente no conoce, y aquí el nulo **significa** «este producto todavía no tiene costo declarado».

**Desde el 08-09-2026 devuelve también la conversión** (`RN-PM-024`), con el mismo trato: **presente y nula** cuando el producto ya está en la moneda por omisión o cuando no hay tasa vigente. Cuesta **una consulta más** —la moneda de casa— y una segunda **solo si hay algo que convertir**.

**Y desde el 14-09-2026 devuelve el enlace del video** (`RN-PM-032`), presente y nulo cuando el producto no lo tiene, sin ninguna consulta más.

#### `RF-PM-004` — Editar producto

| Campo | Valor |
|---|---|
| Objetivo | Corregir lo que se puede corregir sin reescribir lo vendido |
| Actor | Administrador |
| Permiso requerido | `products:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-PM-001`, `RN-PM-005` a `RN-PM-008`, `RN-PM-019`, `RN-PM-020`, `RN-PM-023`, `RN-PM-032` |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/004-editar-producto/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Permite corregir **nombre, descripción, icono, el enlace del video, los dos precios, moneda, vigencia, alcance e implementación**. **No permite cambiar el tipo** (`RN-PM-001`) **ni ninguna de las dos membresías**: las tres definen qué derecho otorga el producto, y cambiarlas convierte lo comprado en otra cosa. Quien necesite otro origen u otro destino registra otro producto y retira el anterior.

**El alcance y la implementación entran del lado corregible** (07-09-2026), y esa es la línea que las separa de los tres inmutables: ninguna cambia **qué derecho otorga** el producto —una dice hasta dónde se muestra y la otra quién lo aplica—, de modo que corregirlas no reescribe lo que compró quien lo compró. Congelarlas habría obligado a registrar un producto nuevo para mover un enlace de sitio, y a retirar el viejo con lo vendido colgando de él.

**El precio de compra se corrige y además se puede VACIAR** (`RN-PM-023`), y en eso va con la descripción, el icono y la vigencia y no con el precio del sistema: su nulo es un estado legítimo —«no se conoce el costo»— de modo que el nulo explícito **es una orden** y no un error. Es también **donde se guarda lo que costó** cuando el producto se compra: hoy lo escribe quien administra, con esta edición. El del sistema no admite vaciarse: la columna es obligatoria y «bórralo» no tiene ningún estado al que llevar el producto.

**El enlace del video se corrige y se vacía** (`RN-PM-032`, 14-09-2026), y va con la descripción, el icono, la vigencia y el precio de compra: su nulo es un estado legítimo —«no tiene video»— de modo que el nulo explícito **es una orden**. Se corrige **en los dos tipos**, sin la condición cruzada del icono, y con la misma comprobación de forma que en el alta.

**Y no reescriben ninguna venta anterior, porque la venta copia la implementación en su línea** —como el importe y la vigencia—: quien compró algo que se entregaba solo lo sigue teniendo así aunque el catálogo cambie de criterio mañana. Es la condición que §5.2.2 impone a `MV`, y **todavía no está construida**.

#### `RF-PM-005` — Cambiar el estado de un producto

| Campo | Valor |
|---|---|
| Objetivo | Decidir si el producto se ofrece, sin borrarlo |
| Actor | Administrador |
| Permiso requerido | `products:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-PM-004`, `RN-PM-009` |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/005-cambiar-estado-producto/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Activa o desactiva un producto. Es la operación que gobierna la oferta en el día a día: desactivar lo retira de la venta **sin tocar nada de lo ya vendido**. Reactivar un upgrade vuelve a exigir `RN-PM-004`, porque en el intervalo puede haberse activado otro hacia el mismo destino.

#### `RF-PM-006` — Eliminar producto

| Campo | Valor |
|---|---|
| Objetivo | Retirar del catálogo lo que fue un error o ya no existe |
| Actor | Administrador |
| Permiso requerido | `products:delete` |
| Prioridad | Media |
| Reglas aplicables | `RN-PM-009`, `RN-PM-010` |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/006-eliminar-producto/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Elimina lógicamente un producto **exigiendo motivo** (Art. V.13), que viaja al registro de eliminación con la instantánea de lo retirado. El producto deja de ofrecerse y deja de contar para `RN-PM-004`, pero **su fila permanece**: el día que existan compras, cada una tendrá que poder decir qué compró.

#### `RF-PM-007` — Consultar la oferta disponible para uno mismo

| Campo | Valor |
|---|---|
| Objetivo | Que un cliente vea qué puede comprar, sin que el navegador decida la regla |
| Actor | Cualquier persona autenticada con `products:sale` |
| Permiso requerido | `products:sale` |
| Prioridad | Alta |
| Reglas aplicables | `RN-PM-009`, `RN-PM-011`, `RN-PM-019`, `RN-PM-020`, `RN-PM-024`, `RN-PM-032` |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/007-consultar-oferta-propia/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Devuelve **solo productos activos**, y de los de tipo upgrade **solo aquellos cuyo origen es la membresía vigente del actor** (`RN-PM-011`) — lo que incluye su **renovación**, si existe declarada. No admite parámetro de persona: responde sobre quien llama y sobre nadie más, como `RF-SP-039`. Nunca devuelve el motivo de retiro, ni lo inactivo, ni la membresía de terceros.

**Publica UN precio y la conversión** (`RN-PM-024`, reescrita el 12-09-2026): `price` es el que se cobra y `exchange` su conversión a la moneda de casa. **El precio de compra no viaja por aquí ni se selecciona**: es el costo de NEXUS y quien compra no tiene por qué conocer el margen (§5.2.6). Entre el 08-09-2026 y el 12-09-2026 publicó también el segundo importe, cuando ese importe era lo que se anunciaba; con el cambio de significado dejó de tener sentido y se retiró.

**Publica el enlace del video** (`RN-PM-032`, 14-09-2026), presente y nulo cuando no hay. Es lo contrario del precio de compra: material de venta, que existe para que lo vea quien compra, y por eso **sí se selecciona** aquí.

**Publica el alcance y la implementación de cada producto, y no filtra por ninguno de los dos** (`RN-PM-019`, `RN-PM-020`). El alcance **no puede** filtrar aquí: bajo la escala acumulativa los dos valores llegan a la tienda, de modo que un predicado sobre él devolvería siempre lo mismo que no ponerlo. La implementación sí viaja en la respuesta, y por un motivo que no es de simetría: quien compra tiene que poder saber **antes de pagar** que lo que se lleva no se le entrega en el acto. Ocultarlo no evita la espera — la convierte en una incidencia de soporte.

---

#### `RF-PM-008` — Consultar un hotlink: producto y vendedor, sin autenticación

| Campo | Valor |
|---|---|
| Objetivo | Que un enlace repartido por un vendedor abra una pantalla con el producto y con quién lo ofrece |
| Actor | **Cualquiera, sin autenticar** |
| Permiso requerido | **Ninguno: es público** |
| Prioridad | Alta |
| Reglas aplicables | `RN-PM-009`, `RN-PM-019`, `RN-PM-021`, `RN-PM-022`, `RN-PM-024`, `RN-PM-032` |
| Depende de | `RF-PM-001`, **`RF-SP-047`** |
| Tripleta | `docs/specs/pm/008-hotlink-publico/` |
| Estado | **Tasks en revisión** (07-09-2026) |

Devuelve, en **una** llamada y **sin token**, el producto que el enlace señala y el **nombre y apellido** de quien lo reparte. El producto viaja con su precio en su moneda **y con la conversión a la moneda por omisión** usando la tasa vigente hoy (`RF-SP-047`).

**Devuelve UN precio, y esto es lo que volvió a cambiar el 12-09-2026** (`RN-PM-024`): `price` y su conversión. Entre el 08-09-2026 y esa fecha viajaron dos —`price` y `publicPrice`—; convertido el segundo en **precio de compra**, **precisamente porque este endpoint es público** es el último sitio donde puede aparecer el costo de NEXUS. **No se selecciona en la consulta**, no solo se omite en la respuesta (§5.2.6).

**La conversión se calcula sobre `price`**, que es el único número que se enseña.

**Publica el enlace del video, sin token** (`RN-PM-032`, 14-09-2026): la dirección que administración escribió, **tal cual**, presente y nula cuando no hay. El sistema no la sigue ni la valida más allá de su forma, y lo que eso significa en una ruta pública está escrito en §5.2.8.

**Es el primer endpoint público del módulo, y el primero del sistema que publica el nombre de una persona.** De ahí salen las dos reglas que lo gobiernan: solo se publica lo que tiene alcance `HOTLINKS` (`RN-PM-021`) y solo el nombre de quien es fuerza comercial (`RN-PM-022`).

!!! danger "Todo lo que no procede responde el MISMO `404`, y esa uniformidad es la mitad de la seguridad"

    Nombre de usuario inexistente, persona que no es vendedor, código inexistente, producto inactivo, retirado o de alcance `TIENDA`: **los seis responden igual**. Distinguirlos convertiría el endpoint en un oráculo — bastaría fijar un código válido e ir variando el nombre de usuario para saber **quién existe** en el sistema.

    Lo que la uniformidad **no** evita es que alguien recorra nombres de usuario a ciegas; eso lo acota `RateLimitFilter` **por origen**, y queda escrito que **acotar no es impedir**.

**`SP` publica dos lecturas nuevas por la vía de D-25**, y no se leen sus tablas: **el vendedor por nombre de usuario** —que devuelve vacío si no es fuerza comercial, de modo que la regla de quién es publicable vive en `SP`, que es de quien son los roles— y **la tasa vigente entre dos monedas**. Las tareas que las escriben pertenecen a este requerimiento aunque el código viva en paquetes de `SP`, como ocurrió con las tres de `RF-PM-001` y `RF-PM-007`.

#### `RF-PM-009` — Reseñar un producto

| Campo | Valor |
|---|---|
| Objetivo | Que quien puede comprar deje escrito qué le pareció, con una puntuación que se pueda sumar |
| Actor | Cualquier persona con `products:comment` |
| Permiso requerido | `products:comment` |
| Prioridad | Alta |
| Reglas aplicables | `RN-PM-025`, `RN-PM-026`, `RN-PM-028`, `RN-PM-031` |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/009-resenar-producto/` |
| Estado | **Tasks en revisión** (14-09-2026) |

Registra **la** reseña del actor sobre un producto: puntuación entera de uno a cinco y texto de uno a mil caracteres, las dos obligatorias (`RN-PM-025`). Responde sobre quien llama y sobre nadie más —el autor sale del token, no del cuerpo—, y **rechaza la segunda** sobre el mismo producto mientras la primera siga viva (`RN-PM-026`): quien quiera cambiar de opinión corrige. Solo se reseña un producto **activo y no retirado** (`RN-PM-028`).

**Es el requerimiento que crea la tabla y siembra el permiso**, con la obligación de asociarlo a `SUPERADMIN` y `ADMIN` en la misma migración (§4). **Y es el que enmienda las cuatro lecturas del producto** con `rating` —promedio y cantidad de reseñas vivas— (`RN-PM-031`), porque desde la primera reseña escrita el catálogo tiene algo que sumar y ningún sitio donde enseñarlo.

#### `RF-PM-010` — Corregir la reseña propia

| Campo | Valor |
|---|---|
| Objetivo | Cambiar de opinión sin escribir dos veces |
| Actor | El autor de la reseña |
| Permiso requerido | `products:comment` |
| Prioridad | Media |
| Reglas aplicables | `RN-PM-025`, `RN-PM-027` |
| Depende de | `RF-PM-009` |
| Tripleta | `docs/specs/pm/010-corregir-resena-propia/` |
| Estado | **Tasks en revisión** (14-09-2026) |

Corrige la puntuación, el texto o los dos, **solo si quien llama es el autor** (`RN-PM-027`): con el permiso y sin ser el autor, `403`. Una reseña ajena no se distingue de la propia en la respuesta —el `403` dice «no es tuya», y eso es lo único que dice—. **Se corrige aunque el producto ya no se venda**: la reseña sobrevive al retiro del producto (`RN-PM-028`), y lo que se escribió sigue siendo del autor.

#### `RF-PM-011` — Retirar la reseña propia

| Campo | Valor |
|---|---|
| Objetivo | Que el autor pueda quitar lo que escribió, sin explicárselo a nadie |
| Actor | El autor de la reseña |
| Permiso requerido | `products:comment` |
| Prioridad | Media |
| Reglas aplicables | `RN-PM-027`, `RN-PM-029`, `RN-PM-031` |
| Depende de | `RF-PM-009` |
| Tripleta | `docs/specs/pm/011-retirar-resena-propia/` |
| Estado | **Tasks en revisión** (14-09-2026) |

Retira lógicamente la reseña **sin motivo declarado** (`RN-PM-029`, tercera excepción del Art. V.13): es la primera eliminación de una entidad de negocio del sistema que no pide `reason`, y por eso es un `DELETE` sin cuerpo y no un `POST /deletion` — la razón por la que el retiro del producto es un `POST` es que el cuerpo lleva el motivo, y aquí no hay motivo que llevar. El registro de eliminación se escribe igual, con la instantánea y un motivo fijo. Solo el autor (`RN-PM-027`). Retirada la suya, la persona puede escribir otra (`RN-PM-026`), y el promedio del producto la deja de contar en el acto (`RN-PM-031`).

#### `RF-PM-012` — Consultar las reseñas de un producto, sin autenticación

| Campo | Valor |
|---|---|
| Objetivo | Que la pantalla de un producto —dentro o fuera— enseñe qué dicen de él quienes lo compraron |
| Actor | **Cualquiera, sin autenticar** |
| Permiso requerido | **Ninguno: es público** |
| Prioridad | Alta |
| Reglas aplicables | `RN-PM-028`, `RN-PM-030` |
| Depende de | `RF-PM-009` |
| Tripleta | `docs/specs/pm/012-consultar-resenas-producto/` |
| Estado | **Tasks en revisión** (14-09-2026) |

Devuelve las reseñas **vivas** de un producto, **paginadas** y de la más reciente a la más antigua, con la puntuación, el texto, las fechas de escritura y de última corrección, y **del autor solo nombre y apellido** (`RN-PM-030`). Responde solo sobre un producto **activo y no retirado**, y lo demás recibe el `404` uniforme del hotlink (`RN-PM-028`). **La misma respuesta con token que sin él**, y por eso no marca cuál es la del actor: para eso está `RF-PM-013`. Es la **segunda ruta pública del módulo**, y entra en la cota de tasa de los catálogos públicos, no en la del hotlink: aquí no hay nombres que sondear.

#### `RF-PM-013` — Consultar la reseña propia sobre un producto

| Campo | Valor |
|---|---|
| Objetivo | Que el front sepa si el actor ya opinó, y qué escribió, para prellenar la corrección |
| Actor | Cualquier persona con `products:comment` |
| Permiso requerido | `products:comment` |
| Prioridad | Media |
| Reglas aplicables | `RN-PM-026`, `RN-PM-027` |
| Depende de | `RF-PM-009` |
| Tripleta | `docs/specs/pm/013-consultar-resena-propia/` |
| Estado | **Tasks en revisión** (14-09-2026) |

Devuelve **la** reseña viva del actor sobre un producto —una, por `RN-PM-026`— con su identificador, que es lo que las dos escrituras necesitan. **No admite parámetro de persona**: responde sobre quien llama, como `RF-PM-007` y `RF-SP-039`. Sin reseña propia responde `404`, y ese `404` **no dice nada del producto**: lo dice igual si el producto no existe, porque a quien pregunta «¿ya opiné?» la respuesta es la misma. **Sí responde sobre un producto inactivo o retirado** cuando la reseña existe: el autor tiene que poder llegar a la suya para corregirla o retirarla (`RN-PM-028`).
## 7. Requerimientos no funcionales

Definidos en [`security.md` §11](../security.md) y en la constitución. Los que este módulo debe satisfacer:

| ID | Requerimiento |
|---|---|
| `RNF-SEG-001` | Autenticación y autorización basada en roles y permisos |
| `RNF-SEG-002` | Todo endpoint no declarado como público exige autenticación. **Este módulo publica DOS**: `RF-PM-008`, el hotlink, desde el 07-09-2026, y `RF-PM-012`, la lista de reseñas de un producto, desde el 14-09-2026. Las dos declaraciones van en `SecurityConfig` con el motivo escrito al lado, y la segunda **solo en `GET`**: la misma ruta responde a un `POST` que exige `products:comment` |
| `RNF-PERF-001` | Lectura p95 < 500 ms, escritura p95 < 1 s (Art. XV.9) |
| `RNF-MAN-001` | Ninguna regla de negocio del módulo vive en el controlador (`architecture.md` §5) |

---

## 8. Integraciones

| Sistema o módulo | Tipo | Dirección | Descripción |
|---|---|---|---|
| `SP` | Interfaz de aplicación | Entrada | Membresías, monedas y la membresía vigente del actor, por **tres interfaces de solo lectura** que `SP` publica (D-25, cerrada el 26-08-2026) |

Ninguna con sistemas externos. La pasarela de pago, que sería la primera, pertenece al alcance que §1.3 deja fuera.

---

## 9. API

| Método | Ruta | Requerimiento | Permiso |
|---|---|---|---|
| `POST` | `/api/v1/products` | `RF-PM-001` | `products:create` |
| `GET` | `/api/v1/products` | `RF-PM-002` | `products:read` |
| `GET` | `/api/v1/products/available` | `RF-PM-007` | `products:sale` |
| `GET` | `/api/v1/products/{id}` | `RF-PM-003` | `products:read` |
| `PATCH` | `/api/v1/products/{id}` | `RF-PM-004` | `products:update` |
| `PATCH` | `/api/v1/products/{id}/status` | `RF-PM-005` | `products:update` |
| `POST` | `/api/v1/products/{id}/deletion` | `RF-PM-006` | `products:delete` |
| `GET` | `/api/v1/hotlinks/{username}/{code}` | `RF-PM-008` | **Público** |
| `POST` | `/api/v1/products/{id}/comments` | `RF-PM-009` | `products:comment` |
| `GET` | `/api/v1/products/{id}/comments` | `RF-PM-012` | **Público** |
| `GET` | `/api/v1/products/{id}/comments/mine` | `RF-PM-013` | `products:comment` |
| `PATCH` | `/api/v1/products/{id}/comments/{commentId}` | `RF-PM-010` | `products:comment` **y ser el autor** |
| `DELETE` | `/api/v1/products/{id}/comments/{commentId}` | `RF-PM-011` | `products:comment` **y ser el autor** |

!!! note "El retiro de la reseña SÍ es un `DELETE`, y el del producto no, por la misma razón"

    El aviso de abajo explica por qué el retiro del producto es un `POST /deletion`: el cuerpo lleva el **motivo** y la RFC 9110 no garantiza que el cuerpo de un `DELETE` llegue. La reseña **no lleva motivo** (`RN-PM-029`), de modo que no hay cuerpo que perder y el verbo correcto es el que dice lo que hace. Es el mismo criterio que `DELETE /api/v1/users/{id}/membership` aplicó a una asociación: **el verbo lo decide si hay cuerpo que proteger, no la costumbre**.

    **Y `/comments/mine` compite con `/comments/{commentId}`**, como `/products/available` con `/products/{id}`: el segmento literal gana a la variable, es correcto, y **por eso mismo tiene prueba** — el síntoma de romperlo sería un `400` por identificador inválido en la única ruta que el front llama antes de pintar el formulario.

!!! warning "El retiro es un `POST` sobre un subrecurso, y no un `DELETE`"

    Esta tabla decía `DELETE /api/v1/products/{id}` hasta el 01-09-2026, cuando el `plan.md` de `RF-PM-006` **ya se había corregido el 27-08-2026** y el código llevaba desde entonces exponiendo `POST /{id}/deletion`. La enmienda no llegó a esta fila, de modo que el documento transversal contradecía a la vez al plan y a la implementación.

    El motivo del cambio original sigue vigente: la RFC 9110 **no define semántica para el cuerpo de un `DELETE`** y un intermediario puede descartarlo, con lo que la petición llegaría **sin el motivo** que el Art. V.13 exige y se convertiría en un rechazo que quien la envió no puede entender ni corregir. Es la misma forma que usan `RF-SP-009` y `RF-SP-029`.

El contrato detallado de cada endpoint se define en el `plan.md` de su tripleta.

!!! warning "`/products/available` compite con `/products/{id}`, y el orden importa"

    Las dos rutas coinciden en forma. Spring resuelve primero el patrón **más específico** —el segmento literal gana a la variable de ruta—, de modo que `/products/available` no se interpreta como un identificador. Es correcto, y **por eso mismo debe tener prueba**: si alguien reordena o renombra, el síntoma sería un `400` por identificador inválido en la única ruta que un cliente usa a diario.

    La alternativa era colgarla de otro recurso (`/me/products`). Se descartó para que los endpoints del módulo vivan bajo su propio recurso, y la decisión se anota aquí para que el `plan.md` de `RF-PM-007` no la vuelva a abrir sin motivo.

---

## 10. Persistencia

| Entidad | Descripción | Dueño |
|---|---|---|
| `products` | El catálogo: qué se vende, de qué tipo, a qué precio | Este módulo |
| `product_comments` | Las reseñas: qué dijo cada persona de cada producto, con qué puntuación, y cuándo lo escribió, lo corrigió y lo retiró (14-09-2026, §10.4) | Este módulo |

Ninguna otra. `memberships`, `currencies` y —desde el 14-09-2026— `users` se **referencian** por clave foránea y pertenecen a `SP`.

### 10.1 Campos principales — `products`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `code` | `varchar(50)` | No | No | No | — | — |
| `validity_days` | `integer` | No | No | Sí | — | — |
| `type` | `varchar(30)` | No | No | No | — | — |
| `name` | `varchar(150)` | No | No | No | — | — |
| `description` | `text` | No | No | Sí | — | — |
| `icon` | `varchar(50)` | No | No | Sí | — | — |
| `target_membership_id` | `uuid` | No | Sí | Sí | — | `memberships` |
| `source_membership_id` | `uuid` | No | Sí | Sí | — | `memberships` |
| `price` | `numeric(14,4)` | No | No | No | — | — |
| `purchase_price` | `numeric(14,4)` | No | No | **Sí** | — | — |
| `video_url` | `varchar(500)` | No | No | Sí | — | — |
| `currency_id` | `uuid` | No | Sí | No | — | `currencies` |
| `status` | `varchar(20)` | No | No | No | `ACTIVO` | — |
| `scope` | `varchar(20)` | No | No | No | — | — |
| `implementation` | `varchar(20)` | No | No | No | — | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |
| `deleted_at` | `timestamptz` | No | No | Sí | — | — |

Sin columnas de actor, y **sin columna de motivo**: quién retiró el producto y por qué residen en `audit_deletion_log`, con la instantánea de la fila (Art. V.7 y V.13). Es lo que hacen `roles` y `users`.

`type` tiene dominio cerrado:

| Valor | Qué derecho otorga |
|---|---|
| `UPGRADE_MEMBRESIA` | Pasar **de** `source_membership_id` **a** `target_membership_id`. Las dos se declaran; ninguna se deduce |
| `BOT` | Una prestación del sistema, sin efecto sobre el nivel de acceso |

`status` tiene dominio cerrado —`ACTIVO`, `INACTIVO`— y decide si el producto se ofrece (`RN-PM-009`). **No se usa `boolean`**, al revés que los catálogos de `SP`: el dominio es candidato a crecer —un `BORRADOR` que permita preparar un producto sin publicarlo es previsible— y añadir un valor a un `varchar` con `CHECK` es una migración, mientras que convertir un `boolean` en tres estados es una reescritura de todo lo que lo consulta.

`scope` tiene dominio cerrado y **es una escala, no un reparto** (`RN-PM-019`):

| Valor | Hasta dónde llega el producto |
|---|---|
| `TIENDA` | Solo la tienda |
| `HOTLINKS` | La tienda **y** los hotlinks. **Incluye** al anterior: es un alcance mayor, no otro canal |

`implementation` tiene dominio cerrado y dice **quién aplica lo comprado** (`RN-PM-020`):

| Valor | Qué ocurre cuando se confirma el pago de una venta |
|---|---|
| `AUTOMATICA` | El sistema aplica lo comprado sin que intervenga nadie |
| `MANUAL` | Lo comprado **queda esperando** a que un funcionario lo autorice. Confirmar el pago **no** lo entrega |

**Las dos son `varchar` con `CHECK` y no `boolean`**, por el mismo motivo que `status`. En el alcance el dominio ya es candidato a crecer —un tercer valor que publique **solo** en hotlinks es previsible—, y añadirlo a un `varchar` es una migración mientras que convertir un `boolean` en tres estados es reescribir todo lo que lo consulta. En la implementación el dominio parece binario de verdad, y se declara igual: el día que aparezca una tercera forma —diferida, automática con tope— el nombre `boolean` ya habría mentido, y el coste de haberlo elegido se paga entero en ese momento.

**El valor por omisión de `status` es `INACTIVO`** (`RN-PM-012`), y con él se descartó por ahora el tercer valor `BORRADOR`: la distinción entre «nunca publicado» y «retirado de la venta» es fina y no urge, y añadirla después es exactamente la migración barata que este párrafo describe. Resuelto el 26-08-2026 al aprobar `RF-PM-001`.

**`price` se declara `numeric(14,4)` y no `numeric(12,2)`.** La escala no puede fijarse en dos porque `currencies.decimal_places` no siempre vale dos, y el sistema declara ese campo precisamente para no asumirlo. Cuatro decimales cubren toda moneda ISO 4217 en circulación. La escala **efectiva** de cada producto la decide su moneda, y esa es `RN-PM-007`.

**`purchase_price` comparte forma con `price` y es la única columna de dinero de este esquema que admite nulo** (`RN-PM-023`; nació como `public_price` el 08-09-2026 y se renombró el 12-09-2026 al cambiar de significado, §5.2.6). La forma es la misma porque **es el mismo dinero en la misma moneda**: un costo que no cupiera donde cabe el precio de venta sería una asimetría sin causa. Lo que no comparte es la obligatoriedad, y ahí está la decisión: **el nulo significa «no se conoce el costo»** —el producto no se ha comprado todavía, o no aplica—, no «costó cero». Los dos estados existen y son distintos, que es exactamente el motivo por el que la columna admite nulo en lugar de llevar `DEFAULT 0`.

**Y no hay columna de moneda para el precio de compra.** Se expresa en `currency_id`, la del producto. Es una simplificación asumida: si NEXUS paga en otra moneda, quien registra el costo lo convierte al declararlo; una segunda moneda con su tasa y su vigencia es una tabla de compras, no una columna de esta.

**`video_url` es `varchar(500)` y admite nulo** (`RN-PM-032`, 14-09-2026). Es **la dirección de un video, no el video**, por el mismo camino por el que `icon` es un nombre y no una imagen: el sistema no almacena binarios y, desde hoy, **tampoco los consulta** (§5.2.8). El tope de quinientos es el de un enlace que alguien puede repartir —un enlace que no cabe ahí no es uno que nadie vaya a escribir a mano—, y subirlo, el día que haga falta, es un `ALTER` de solo metadatos en PostgreSQL y no una reescritura, al revés que estrechar. **No hay valor por omisión ni cadena vacía**: el nulo significa «no tiene video», y `ck_products_video_url_format` hace que la columna guarde un enlace con forma o nada — la cadena vacía y el texto que no empieza por `http` no caben.

### 10.2 Restricciones exigidas en el esquema

| Restricción | Sobre | Regla que implementa |
|---|---|---|
| `ck_products_type` | `type IN ('UPGRADE_MEMBRESIA','BOT')` | `RN-PM-001` |
| `ck_products_status` | `status IN ('ACTIVO','INACTIVO')`, con `DEFAULT 'INACTIVO'` | `RN-PM-009`, `RN-PM-012` |
| `ck_products_type_target` | `(type = 'UPGRADE_MEMBRESIA' AND target_membership_id IS NOT NULL AND source_membership_id IS NOT NULL) OR (type = 'BOT' AND target_membership_id IS NULL AND source_membership_id IS NULL)` | `RN-PM-002` |
| `ck_products_icon_solo_upgrade` | `icon IS NULL OR type = 'UPGRADE_MEMBRESIA'` | `RN-PM-016`. La rama `IS NULL` va **delante y explícita** por lo mismo que en la vigencia: un `CHECK` que evalúa a `NULL` **acepta** la fila |
| `ck_products_icon_format` | `icon IS NULL OR icon ~ '^[a-z][a-z0-9-]*$'` | `RN-PM-016`. El valor se guarda ya normalizado, de modo que el `CHECK` puede ser una comprobación de forma corriente |
| `ck_products_video_url_format` | `video_url IS NULL OR video_url ~ '^https?://[^[:space:]]+$'` | `RN-PM-032`. La rama `IS NULL` va **delante y explícita**, como en el icono. Comprueba **la forma y nada más** —esquema `http` o `https`, y ningún espacio—; que el enlace resuelva a algo no es cosa del esquema ni del dominio (§5.2.8). El tope de longitud lo da el tipo de la columna |
| `ck_products_scope` | `scope IN ('TIENDA','HOTLINKS')` | `RN-PM-019` |
| `ck_products_implementation` | `implementation IN ('AUTOMATICA','MANUAL')` | `RN-PM-020`. **Ninguna de las dos lleva `DEFAULT`**, al revés que `status`: aquel lo tiene porque una regla lo exige (`RN-PM-012`), y aquí un valor por omisión sería **una decisión comercial tomada por la columna** — hasta dónde se muestra un producto y quién lo entrega los declara quien lo registra |
| ~~`ck_products_price_positive`~~ → `ck_products_price_no_negativo` | `price >= 0`. **Cambia de umbral Y de nombre el 08-09-2026 en `V67`**: la restricción dejó de decir «positivo», y dejarle el nombre viejo habría hecho que quien lo leyera creyera que el cero sigue prohibido. Su relajación es lo que obliga a `ProductCommissionCapGuard` a dejar de dividir a ciegas (§5.2.4) | `RN-PM-006` |
| ~~`ck_products_public_price_no_negativo`~~ → `ck_products_purchase_price_no_negativo` | `purchase_price IS NULL OR purchase_price >= 0`. **Se renombra con la columna el 12-09-2026** (§5.2.6), por lo mismo que `ck_products_price_no_negativo` se renombró con su umbral: un nombre que dice «público» sobre un costo miente. La rama `IS NULL` va **delante y explícita**, por lo mismo que en la vigencia y el icono: un `CHECK` que evalúa a `NULL` **acepta** la fila, y el permiso debe ser deliberado y no accidental | `RN-PM-006`, `RN-PM-023` |
| `ck_products_validity_positive` | `validity_days IS NULL OR validity_days > 0` | `RN-PM-015`. La rama `IS NULL` se escribe **explícita** aunque `validity_days > 0` sola también admitiría el nulo —un `CHECK` que evalúa a `NULL` acepta la fila—: así el permiso es deliberado y no accidental, y el día que la vigencia se vuelva obligatoria basta con quitar esa rama |
| `fk_products_target_membership` | `target_membership_id` → `memberships(id)` | `RN-PM-003` |
| `fk_products_source_membership` | `source_membership_id` → `memberships(id)` | `RN-PM-003` |
| ~~`ck_products_origen_distinto`~~ | ~~`source_membership_id IS NULL OR source_membership_id <> target_membership_id`~~ | **Retirada el 07-09-2026 en `V61`**: prohibía exactamente lo que la **renovación** admite (§5.2.3). Con ella cae **la única mitad de `RN-PM-017` que el esquema sostenía** — la que sobrevive necesita el `level` de dos filas de `memberships`, y un `CHECK` no consulta otra tabla, de modo que la regla vive ahora **entera en el caso de uso** |
| `uq_products_code` | `products(code)` — restricción **total**, no parcial | `RN-PM-013`: al revés que el nombre, el código **no se libera** al retirar un producto. El día que una factura diga `UPGRADE_ORO` tiene que resolver a un solo producto para siempre |
| `ck_products_code_format` | `code ~ '^[A-Z][A-Z0-9_]*$'` | `RN-PM-013`. Mismo formato que `roles` y `memberships` |
| `fk_products_currency` | `currency_id` → `currencies(id)` | `RN-PM-008` |
| `uq_products_name` | Índice único sobre `f_unaccent(lower(name))`, **parcial**: `WHERE deleted_at IS NULL` | `RN-PM-005` |
| `uq_products_upgrade_target` | Índice único sobre **`(source_membership_id, target_membership_id)`**, **parcial**: `WHERE type = 'UPGRADE_MEMBRESIA' AND status = 'ACTIVO' AND deleted_at IS NULL`. **Era solo sobre el destino hasta el 02-09-2026** | `RN-PM-004` |

Se declaran en la base de datos, no solo en Java (Art. V.6).

!!! important "Dos advertencias que este proyecto ya pagó una vez"

    **`ck_products_type_target` no puede evaluar a `NULL`.** Sus dos ramas son predicados `IS NULL` / `IS NOT NULL`, que devuelven siempre verdadero o falso. La precaución no es teórica: `ck_deletion_reason` se escribió con un `OR` cuyo lado nulo evaluaba a `NULL`, y un `CHECK` que devuelve `NULL` **acepta la fila** — la restricción existía y no restringía nada (`requirements.md` v0.31.0).

    **Los dos índices únicos son parciales, y un índice parcial no admite `DEFERRABLE`**, que es propiedad de una *restricción* y no de un índice. Ninguna de estas dos unicidades podrá comprobarse al confirmar la transacción: morderán en el `INSERT` o el `UPDATE` que las viole, y el plan debe traducirlas ahí en lugar de proponer diferirlas (hallazgo de `RF-SP-019`, `requirements.md` v0.31.0).

### 10.3 Lo que no se declara en el esquema

| Regla | Por qué no | Cómo se verifica |
|---|---|---|
| `RN-PM-007` — decimales según la moneda, **de los dos importes** | Un `CHECK` no puede consultar otra tabla, y la escala admisible depende de `currencies.decimal_places` | En el dominio, con prueba unitaria propia sobre una moneda de dos decimales y otra de cero, **y contra el precio de compra además del de venta** |
| `RN-PM-023` — el precio de compra no se cobra | **No es una restricción de integridad**: ninguna columna puede declarar que un número no se use. Lo que la sostiene es **dónde no aparece** — `movement_details` copia `price` y `ProductCatalog.saleViewOf` no publica el otro | Contando qué lee la venta: prueba de que corregir el precio de compra **no cambia** el importe de una venta registrada después |
| `RN-PM-024` — el precio de compra no sale de administración | Tampoco: ninguna columna puede declarar que un número no se publique. Lo sostiene que `OfferItem` y la respuesta del hotlink **no tengan el campo** y que sus dos consultas **no lo seleccionen** — no que lo traigan y lo callen | En las pruebas de la oferta y del hotlink, comprobando que el cuerpo **no trae** `purchasePrice` aunque el producto lo tenga declarado; y en las de administración, que lo trae **presente y nulo** cuando no se conoce |
| `RN-PM-008` — la moneda debe estar **activa** | La clave foránea garantiza que existe, no que esté vigente | En el caso de uso, contra la interfaz que `SP` publique (**D-25**) |
| `RN-PM-011` — la oferta coincide por origen | Es una consulta, no una restricción de integridad | En el caso de uso de `RF-PM-007`, con prueba sobre los cuatro casos: origen que coincide, origen ajeno, **renovación** y actor sin membresía |
| `RN-PM-017` — el origen no está por encima | **Desde el 07-09-2026 no queda NADA de ella en el esquema**: `ck_products_origen_distinto` se retiró con la renovación, y la mitad que sobrevive necesita el `level` de **dos** filas de `memberships`, que un `CHECK` no puede consultar | En `RegisterProductService.verificarOrigen`, con prueba del descenso —que se rechaza— y del mismo nivel —que se admite— |

### 10.4 `product_comments` — la reseña (14-09-2026)

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `product_id` | `uuid` | No | Sí | No | — | `products` |
| `user_id` | `uuid` | No | Sí | No | — | `users` |
| `rating` | `smallint` | No | No | No | — | — |
| `comment` | `text` | No | No | No | — | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |
| `deleted_at` | `timestamptz` | No | No | Sí | — | — |

**`user_id` es el autor, no el actor** (§5.2.7): la reseña no significa nada sin saber de quién es. Quién la corrigió y quién la retiró siguen viviendo en la auditoría (Art. V.7), y coinciden con el autor porque `RN-PM-027` lo obliga.

**Sin columna de motivo**, como en `products`, y aquí además **sin motivo declarado** (`RN-PM-029`): el registro de eliminación lleva la instantánea y un motivo fijo, `Retirada por su autor`, que es lo que el Art. V.13 enmendado admite para el contenido propio. **El esquema de la auditoría no cambia**: `ck_deletion_reason` sigue exigiendo contenido en toda baja lógica, y lo tiene.

**`rating` es `smallint` y no `integer` ni `numeric`**: el dominio son cinco valores y no va a crecer a decimales — media estrella sería otra escala, no la misma con más resolución—. El promedio, que sí lleva decimales, **no se guarda** (§5.2.7).

**`comment` es `text` con `CHECK` de longitud y no `varchar(1000)`**: el límite es una decisión de producto y puede subir; con `varchar` subirlo es alterar el tipo de una columna en uso, con `CHECK` es reemplazar una restricción. El mínimo se comprueba **sin los espacios de los extremos**, como el nombre de una persona en `users`.

#### Restricciones exigidas en el esquema

| Restricción | Sobre | Regla que implementa |
|---|---|---|
| `fk_product_comments_product` | `product_id` → `products(id)` | La reseña es de un producto que existe. **Sin `ON DELETE`**: el producto no se borra físicamente nunca (`RN-PM-010`) |
| `fk_product_comments_user` | `user_id` → `users(id)` | El autor existe. Es la **primera clave foránea de `PM` hacia `users`** |
| `ck_product_comments_rating` | `rating BETWEEN 1 AND 5` | `RN-PM-025`. Sin rama `IS NULL`: la columna es `NOT NULL`, y un `CHECK` sobre una columna obligatoria no puede evaluar a `NULL` |
| `ck_product_comments_comment_length` | `char_length(btrim(comment)) BETWEEN 1 AND 1000` | `RN-PM-025`. El `btrim` va dentro a propósito: mil espacios no son una reseña |
| `uq_product_comments_autor` | Índice único sobre `(product_id, user_id)`, **parcial**: `WHERE deleted_at IS NULL` | `RN-PM-026`. Parcial porque retirada la suya la persona puede escribir otra; y **por parcial no admite `DEFERRABLE`**, de modo que la carrera entre dos altas simultáneas muerde en el segundo `INSERT` y el plan la traduce ahí en `409` (hallazgo de `RF-SP-019`) |
| `ix_product_comments_product` | `(product_id, created_at DESC, id DESC)`, **parcial**: `WHERE deleted_at IS NULL` | No implementa una regla: sostiene la lista de `RF-PM-012` en su orden y el agregado de `RN-PM-031`. Parcial porque las retiradas no se listan ni se suman |

#### Lo que no se declara en el esquema

| Regla | Por qué no | Cómo se verifica |
|---|---|---|
| `RN-PM-027` — solo el autor | Un `CHECK` no sabe quién ejecuta la sentencia. Vive en el caso de uso: `user_id` de la fila contra el actor del token, y `403` si no coinciden | Prueba que intenta corregir y retirar una reseña ajena **con el permiso** y espera `403`; y prueba de que un administrador con `products:comment` tampoco puede |
| `RN-PM-028` — solo lo que se puede comprar | Exige leer `products.status` y `deleted_at` de otra fila, que un `CHECK` no consulta | En el alta, con producto inactivo y con producto retirado; en la lista pública, con los mismos y con uno inexistente, comparando el cuerpo de los tres `404` |
| `RN-PM-030` — del autor solo el nombre | Ninguna restricción puede declarar que una columna no se publique. Lo sostiene que la proyección de la lista **no tenga** `userId` ni `username` | En la prueba de la lista, comprobando el cuerpo entero y no solo los campos esperados |
| `RN-PM-031` — el promedio en toda lectura | Es una cuenta, no una restricción, y **no se guarda** (§5.2.7) | En las cuatro lecturas: promedio y cantidad con reseñas vivas, **nulo y cero** sin ninguna, y una reseña retirada que **sale de la cuenta**; y la prueba de sentencias de `RF-PM-002`, que no debe subir |

---

## 11. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 26-08-2026 | Creación del módulo `PM` con sus **siete requerimientos** y **once reglas propias**. Registra los **dos tipos de producto** —upgrade de membresía y servicio del sistema— con la condición cruzada que los separa (`RN-PM-002`), la unicidad de un solo upgrade activo por destino (`RN-PM-004`) y la oferta que solo mira hacia arriba (`RN-PM-011`). Deja **fuera del alcance la compra y el cobro**, con el motivo escrito en §1.4, y registra **D-25**: `SP` no publica hoy ninguna interfaz de aplicación que este módulo pueda consumir para leer membresías, monedas y la membresía vigente del actor, de modo que la decisión bloquea los `plan.md` de `RF-PM-001` y `RF-PM-007` pero no sus especificaciones. | Responsable técnico |
| 0.2.0 | 26-08-2026 | **Las siete `spec.md` quedan redactadas** y los siete requerimientos pasan a `Spec en revisión`. Traen **veintisiete preguntas abiertas** que hay que resolver antes de aprobarlas, y ninguna es de trámite: qué estado tiene un producto recién creado, si lleva código estable además del nombre, qué orden trae el catálogo, si se puede cambiar el precio de algo ya vendido, si retirar exige desactivar primero, y qué ve en su oferta quien no es consumidor. Las cinco de `RF-PM-007` son las que más lejos llegan: **qué se le ofrece a quien no tiene membresía**, si los servicios dependen del nivel, si el precio se ajusta por nivel —que es la puerta de entrada de las promociones—, si se ofrecen todos los upgrades superiores o solo el siguiente, y si la oferta se pagina. Las especificaciones **no** dependen de **D-25**: qué debe pasar está decidido; por dónde entra el dato de `SP` sigue abierto y bloquea los planes de `RF-PM-001` y `RF-PM-007`. | Responsable técnico |
| 0.3.0 | 26-08-2026 | **`RF-PM-001` cruza su primera compuerta**, y sus cinco resoluciones alcanzan al módulo entero. Tres reglas nuevas. **`RN-PM-012` — el producto nace `INACTIVO`**, y el motivo no es la prudencia sino dónde vive `RN-PM-004`: naciendo activo habría que comprobar «un solo upgrade activo por destino» en el alta **y** en la activación, y la copia que se quedara atrás no fallaría, **admitiría**; ahora la comprobación vive solo en `RF-PM-005`, y con ella se muda allí la excepción y su criterio. **`RN-PM-013` — el código no se libera nunca**: el producto gana código corto, inmutable y único **incluso frente a los eliminados**, al revés que el nombre, porque el nombre es una etiqueta que `RF-PM-004` deja corregir y una factura necesita que `UPGRADE_ORO` resuelva a un solo producto para siempre. **`RN-PM-014` — no se publica lo que no se explica**: la descripción es opcional al registrar y obligatoria al activar, que es la forma de no llenarla de ruido sin dejar publicar algo que el cliente no entiende. §10 incorpora la columna `code` con `uq_products_code` **total** y su formato, y `status` gana `DEFAULT 'INACTIVO'`. Se descarta por ahora el tercer valor `BORRADOR`, con su motivo escrito. | Responsable del proyecto |
| 0.4.0 | 26-08-2026 | **`RF-PM-002` cruza su primera compuerta.** El catálogo del administrador se ordena **por fecha de alta** salvo que se pida otra cosa de una **lista cerrada** —nombre, precio, fecha—, con el identificador como desempate: sin un orden total, dos productos que compartan el valor ordenado pueden repetirse o saltarse entre páginas, y eso se descubre como «faltan productos» sin ningún error de por medio. Sale gratis porque el identificador es un UUID v7 y su orden **es** el cronológico. Los retirados **no exigen permiso propio** —el motivo del retiro no viaja en el catálogo, vive en la auditoría de eliminación con el suyo— y el filtro por rango de precio pasa a lo que no se incluye. **La resolución alcanzó a otra spec**: quedó dicho que las dos consultas tienen órdenes distintos porque responden a actores distintos, de modo que `RF-PM-007` devuelve la oferta **agrupada por tipo**, con los upgrades por **nivel destino** —el único orden en el que «subir» significa algo— y los servicios por fecha. | Responsable del proyecto |
| 0.5.0 | 26-08-2026 | **`RF-PM-003` cruza su primera compuerta**, con dos resoluciones que se apartan de lo recomendado y una que lo confirma. **El detalle devuelve el motivo del retiro** a quien tenga `products:read`: delante de un producto retirado «por qué» es la pregunta de todo el mundo, y obligar a cambiar de pantalla convierte la auditoría en un trámite. La consecuencia se asume por escrito —`products:read` alcanza a un dato que en la auditoría acota `audit:read-deletions`— y se acota a la consulta individual: **el listado no lo lleva**, porque uno a uno es una consulta y en bloque sería una exportación de decisiones comerciales. Esto **enmendó el motivo** con el que se había aprobado la resolución 3 de `RF-PM-002` horas antes (Art. I.7): la decisión sigue en pie, su justificación se reescribió. **No devuelve autoría**: el Art. V.7 mantiene las columnas de actor fuera de las tablas, y traerlas aquí obligaría a duplicar el dato o a leer el almacén de evidencia de otro módulo. **El precio viaja como número**, con los decimales de su moneda y no con la escala de la columna, y queda declarado lo que eso cuesta: un número JSON pasa por coma flotante de doble precisión en cualquier cliente JavaScript, de modo que ningún total calculado en el navegador puede ser el que se cobre. | Responsable del proyecto |
| 0.6.0 | 26-08-2026 | **`RF-PM-004` cruza su primera compuerta**, y de sus cuatro preguntas **solo dos hubo que decidirlas**: las otras las había cerrado ya la aprobación de `RF-PM-001`, que es lo que ocurre cuando una decisión anterior alcanza a una spec posterior. **El precio se puede corregir siempre**, y eso deja escrita en §1.4 una condición que este módulo **impone a uno que todavía no existe**: cada compra guardará el importe que se pagó en lugar de leerlo del producto, porque si lo leyera, corregir un precio pasaría a reescribir facturas ya emitidas. Se descartaron congelar el precio de lo vendido —cada cambio costaría un alta y un retiro, y el catálogo se llenaría de productos casi idénticos— y versionar el precio con vigencia, que es la puerta de entrada de las promociones que §1.3 deja fuera. **No se exige motivo** al corregir: la auditoría ya registra qué cambió, de cuánto a cuánto, quién y cuándo, y exigirlo en cada coma llena ese campo de «ajuste». Las dos cerradas por consecuencia: corregir un producto **inactivo** no solo se admite, es imprescindible —es el estado en el que nace—, y el **código es inmutable**, de modo que se suma al tipo y al destino entre lo que la petición no puede traer. | Responsable del proyecto |
| 0.7.0 | 26-08-2026 | **`RF-PM-005` y `RF-PM-006` cruzan su primera compuerta**, y con ellas quedan aprobadas seis de las siete. **Ninguna de las dos exige motivo para cambiar el estado**, por coherencia con `RF-PM-004` y con los catálogos de `SP`; el retiro sí lo exige, porque lo obliga el Art. V.13. **Un producto se retira en cualquier estado**, sin desactivarlo antes: el motivo obligatorio ya es la barrera, y exigir el paso previo haría que **todos** los registros de eliminación dijeran «inactivo», destruyendo el dato que `CA-PM-052` conserva para saber si el producto estaba a la venta. **Un producto vendido se podrá retirar** —la fila permanece y la compra guardará su propio importe—, con la consecuencia declarada de que quien consulte su compra verá el producto retirado. **Ningún evento de seguridad** en el retiro, igual que en el alta: un producto no concede privilegios. La pregunta del carrito se traslada a quien escriba la compra, con lo único que hoy puede afirmarse: aquí no se reserva nada. | Responsable del proyecto |
| 0.8.0 | 26-08-2026 | **`RF-PM-007` cruza su primera compuerta, y con ella las siete del módulo.** Quien no tiene nivel **ve los servicios y ningún upgrade**: ofrecerle el primero sería venderle una membresía, y un nivel no se obtiene comprando un salto sino recibiendo un rol de consumidor (`RN-SP-018`). Los **servicios no dependen del nivel**, y queda escrito lo que costaría que dependieran — una **relación nueva entre producto y membresía**, con su tabla y una enmienda de `RN-PM-002`, no un filtro más—. El **precio no se ajusta** por quién mira, porque un precio distinto según el actor es un descuento y los descuentos son promociones, que §1.3 deja fuera: admitirlo aquí las colaría sin tabla donde vivir ni vigencia que las acote. Se ofrecen **todos los upgrades superiores** y no solo el siguiente. La paginación se resolvió **sin decidirla**: hoy no se pagina, y las dos colecciones viajan **envueltas en un objeto** —como `RF-SP-017` hizo con la cadena de membresías— para que el día que los servicios crezcan, añadirla no rompa a ningún cliente. | Responsable del proyecto |
| 0.9.0 | 26-08-2026 | **D-25 cerrada**, y con ella la tercera de las tres decisiones que este documento declaraba pendientes al nacer. `SP` publica **tres interfaces de aplicación de solo lectura** —membresía y su nivel, moneda y sus decimales, membresía vigente de una persona— y `PM` las importa; el desarrollo está en `architecture.md` §15.2 y vale para cualquier par de módulos. Las fichas de `RF-PM-001` y `RF-PM-007` **dejan de depender de una decisión pendiente**: los siete requerimientos tienen ya spec aprobada y ninguno tiene bloqueada su segunda compuerta. Las tareas que escriben esos puertos pertenecen a esos dos requerimientos aunque el código viva en paquetes de `SP`. | Responsable del proyecto |
| 0.10.0 | 26-08-2026 | **Los siete `plan.md` aprobados**: el módulo cruza entero la segunda compuerta el mismo día que la primera. Dos decisiones de los planes quedan firmes y alcanzan más allá de su requerimiento: la **lectura estrecha del motivo de eliminación en `shared/audit`**, que es de donde `RF-PM-003` toma el motivo que devuelve, y el **`JOIN` a `memberships`** con el que `RF-PM-002` resuelve el destino de cada upgrade en lugar de llamar al puerto fila a fila. Lo que sigue es la tercera compuerta: las `tasks.md`. | Responsable del proyecto |
| 0.11.0 | 26-08-2026 | **Las siete `tasks.md` aprobadas**: la tripleta del módulo está completa y el código puede escribirse (Art. I.1). **95 tareas**, con el orden de implementación fijado por una dependencia que no es la de los identificadores: `RF-PM-003` necesita una eliminación registrada, que escribe `RF-PM-006`, de modo que la secuencia es `001 → 002 → 005 → 006 → 003 → 004 → 007`. Tres tareas escriben **fuera de `PM`** —dos interfaces en `SP`, una tercera para la membresía vigente, y la lectura estrecha del motivo en `shared/audit`— y son las de mayor riesgo: una regresión ahí alcanza a `SP` entero, y por eso su definición de terminado exige que su suite siga en verde sin cambios. | Responsable del proyecto |
| 0.12.0 | 27-08-2026 | **Los productos ganan vigencia de adquisición, medida en días** (`RN-PM-015`), por decisión del responsable del proyecto. Es **opcional y en los dos tipos**: sin ella, lo adquirido **no caduca** —comprar Oro y quedarse en Oro—; con ella, el derecho dura los días que declare, contados desde la compra. Se descartó hacerla obligatoria porque vender algo permanente habría exigido un valor de relleno —mil años— que ningún `CHECK` distingue de un error de tecleo. §10 incorpora `validity_days` y `ck_products_validity_positive`, cuya rama `IS NULL` se escribe **explícita** aunque la comparación sola también admitiría el nulo: así el permiso es deliberado y no accidental. **Dos condiciones más sobre la compra futura**, en §1.4: cada compra guardará **la vigencia que compró** además del importe —o corregir una vigencia reescribiría lo ya vendido—, y **al vencer, la persona se queda sin nivel vigente**: no vuelve al que tenía antes, porque eso habría exigido que la compra guardase cuál era, ni baja al más bajo, que castigaría a quien ya estaba arriba. Enmienda las siete tripletas, aprobadas el día anterior (Art. I.7). | Responsable del proyecto |
| 0.13.0 | 28-08-2026 | **Dos cambios pedidos por el responsable del proyecto.** (1) **El tipo `SERVICIO` pasa a llamarse `BOT`**. Es un **renombrado y no un cambio de semántica**: sigue siendo el producto que da derecho a una prestación y no toca el nivel de acceso de nadie. Se pudo hacer hoy porque **todavía no existe ninguna tabla de compras** que apunte a un producto; el día que exista, un renombrado de este valor tendrá que arrastrar también lo vendido, y por eso queda escrito. (2) **Nace `RN-PM-016`: el icono, solo en el upgrade.** Un `UPGRADE_MEMBRESIA` puede declarar el icono con el que el frontend lo pinta y un `BOT` no puede, y **es opcional incluso donde se admite**. Es un **identificador y no una imagen** —`crown`, `arrow-up-circle`—, por el mismo camino que el color de la membresía (`RN-SP-024`): el sistema no almacena binarios, y dónde vivirían es una decisión abierta que este cambio no necesitaba abrir. La regla tiene **una sola mitad** —prohíbe, no obliga—, que es lo que la distingue de `RN-PM-002`. Migración **`V43`**: `V39` no se edita, que está aplicada y Flyway valida por suma de comprobación; los dos `CHECK` que nombraban el literal viejo caen primero, porque mientras exijan `SERVICIO` ningún `UPDATE` puede escribir `BOT`. **El contrato publicado cambia**, de modo que la copia del frontend queda vieja: `docs/api/openapi.json` es la autoridad. Enmienda las tripletas de `RF-PM-001` a `RF-PM-004` (Art. I.7). | Responsable técnico |
| 0.14.0 | 01-09-2026 | **`RF-PM-007` implementado**, y con él las siete operaciones del módulo tienen código: `GET /api/v1/products/available` publica a cada persona lo que puede comprar. Estrena la **tercera y última lectura de D-25** —`CurrentMembershipLookup`, la membresía **vigente** de una persona—, que devuelve la membresía **ya evaluada** en lugar de su fecha de fin: publicar la fecha habría invitado a `PM` a rehacer la comparación de vigencia, y ese es el defecto que **no falla** —resultados plausibles durante meses, visibles solo en el borde—. La comparación que decide el requerimiento es `m.level < :nivel` con **menor estricto**, porque la cadena crece hacia abajo y **nivel superior es número menor**; escrita al revés habría ofrecido **bajadas** de nivel cobrándolas, sin que ninguna prueba de camino feliz lo viera, y por eso `ProductOfferIT` comprueba los tres casos —inferior, igual y superior— en una sola vista y sobre una cadena de **cuatro** niveles, que es la única longitud con la que «todos los superiores» y «solo el inmediato» dejan de dar el mismo resultado. **Se corrige además §9** (Art. I.7): la fila del retiro decía `DELETE /api/v1/products/{id}` desde el 26-08-2026, cuando el `plan.md` de `RF-PM-006` se había corregido el 27-08-2026 y el código expone `POST /{id}/deletion` — el documento transversal contradecía al plan y a la implementación a la vez. **Y §6.1 se pone al día**: los siete requerimientos figuraban en `Tasks aprobadas` cuando seis llevaban código desde el 27-08-2026 — esta tabla no es la autoridad del estado, lo es la matriz de [`requirements.md` §4](../requirements.md#4-matriz-de-trazabilidad), y tenerla desactualizada obliga a comprobar cuál de las dos miente. | Responsable técnico |
| 0.15.0 | 02-09-2026 | **Un upgrade declara de dónde sale, no solo a dónde lleva**, por decisión del responsable del proyecto. Hasta hoy solo decía el destino y **quién podía comprarlo se deducía** —cualquiera por debajo de ese nivel—, y esa deducción hacía **imposible el salto**: «`BECA → ORO`» no se podía expresar, porque el mismo producto se ofrecía al mismo precio a quien sube tres escalones y a quien sube uno. Con el origen declarado, **cada salto es un producto** y cada uno tiene su precio. **El origen es obligatorio en todo upgrade** (`RN-PM-002`), y §5.2.1 acepta entera la consecuencia: **un origen sin producto no falla, no se ofrece** — si nadie declara un upgrade desde `VIP`, quien esté en `VIP` no verá ninguna subida, y el catálogo se ve perfectamente bien desde administración. Es el precio de que la oferta sea explícita en lugar de calculada; la alternativa —origen opcional con «vacío = desde cualquiera»— obligaba a que **dos reglas convivieran** en cada consulta y a que un mismo comprador viera dos caminos al mismo destino sin que nadie lo decidiera. **`RN-PM-004` cambia de forma**: la unicidad pasa de ser **por destino** a ser **por pareja origen→destino**, porque la anterior prohibía exactamente lo que el origen existe para permitir — `BECA → ORO` y `PLATINO → ORO` activos a la vez no son dos precios para lo mismo, son **dos saltos distintos**. Nacen `RN-PM-017` —el origen está **por debajo** del destino, y no puede ser el mismo: lo contrario sería vender un descenso llamándolo upgrade, o vender nada— y `RN-PM-018`, que declara que **saltar niveles es legítimo** y es la razón de que el origen se declare en lugar de deducirse de la cadena. **Y `RF-PM-007` deja de comparar niveles**: la oferta pasa a ser una coincidencia exacta —los upgrades cuyo origen es mi membresía— con lo que la regla de niveles se muda de ser un filtro evaluado en cada consulta a una validación comprobada **una vez, al registrar**. Eso conserva sin escribir nada que **quien no tiene membresía no vea ningún upgrade**: no coincide con ningún origen. | Responsable del proyecto |
| 0.16.0 | 02-09-2026 | **`RF-PM-007` deja de responder sin permiso**, por decisión del responsable del proyecto, y **§4 se enmienda bajo Art. I.7** —el requerimiento ya está implementado—. Nace `products:sale`: no es un permiso de administración como los otros cuatro del módulo, es el que gobierna la **vista de venta** que un rol de tipo `CONSUMIDOR` usa para ver qué puede comprar. El razonamiento que justificaba «sin permiso» —que exigir `products:read` daría a cada cliente el catálogo entero— **sigue siendo válido para `products:read`**, y es exactamente por lo que el permiso nuevo no es ese: es uno propio, acotado a esta vista y a nada más. **No se concede por siembra**: el rol `CLIENTE` (`V30`) nace sin permisos a propósito, y quien administre roles se lo concede a `CLIENTE`, a `ESTUDIANTE` o a cualquier `CONSUMIDOR` por `RF-SP-006`, como a cualquier otro permiso. `V48__seed_products_sale_permission.sql` lo siembra y lo asocia a `SUPERADMIN` y `ADMIN` en la misma migración ([`security.md` §4.4](../security.md#44-catalogo-de-permisos)). | Responsable del proyecto |
| 0.17.0 | 07-09-2026 | **Un producto declara HASTA DÓNDE se muestra y CÓMO se entrega**, por decisión del responsable del proyecto. Nacen dos columnas, dos reglas y §5.2.2. **`RN-PM-019` — el alcance es ACUMULATIVO, no un canal**: `TIENDA` es el alcance más corto y `HOTLINKS` **incluye la tienda**, de modo que la escala crece en lugar de repartir el catálogo. Se eligió frente a dos canales excluyentes, y **lo que cuesta hay que leerlo entero: no existe forma de publicar algo SOLO en hotlinks**, ni de esconder de la tienda un producto que se quiere enlazar. El día que haga falta, lo que entra es un **tercer valor** —`SOLO_HOTLINKS`— y no un cambio de significado de los dos que hay, que reescribiría en silencio lo ya declarado. **`RN-PM-020` — la implementación dice si lo comprado se aplica solo o espera a que alguien lo autorice**, `AUTOMATICA` o `MANUAL`, y **es lo primero de este catálogo que gobierna a otro módulo**: `RN-MV-020` deja de conceder la membresía en toda venta confirmada y pasa a concederla **solo** cuando el producto es automático ([`requirements/mv.md` v0.9.0](mv.md)). **Las dos son obligatorias en los dos tipos y las dos se corrigen** (`RF-PM-004`), al revés que el tipo y las dos membresías: ninguna cambia **qué derecho otorga** el producto —una dice dónde se ve y la otra quién lo entrega—, de modo que congelarlas obligaría a registrar un producto nuevo para mover un enlace de sitio, con lo vendido colgando del viejo. **Y queda escrito lo que el alcance NO hace hoy**: `RF-PM-007` —la tienda— **no lo filtra**, porque bajo la escala acumulativa los dos valores llegan a ella; su único uso inmediato es el filtro del catálogo administrativo (`RF-PM-002`), que entra con las columnas y no después — sin él, el alcance sería un dato que se declara y no se puede consultar. `V59` añade las dos columnas **sin valor por omisión** —el dominio las escribe siempre— y rellena lo existente con `TIENDA` y `MANUAL`: el alcance más corto **conserva exactamente la oferta de hoy**, y la implementación más lenta **no concede nada sola** — rellenar con `AUTOMATICA` habría hecho que el día que `RF-MV-003` se construya, productos que nadie revisó entregaran membresías sin que ninguna decisión lo hubiera dicho. Enmienda las tripletas de `RF-PM-001` a `RF-PM-004` y `RF-PM-007` (Art. I.7), y las cinco quedan **construidas el mismo día**: quince criterios nuevos —`CA-PM-110` a `CA-PM-124`— y la suite del proyecto de **992 a 1006 pruebas**, en verde. | Responsable del proyecto |
| 0.18.0 | 07-09-2026 | **Nace `products:hotlink`, el segundo permiso de vista del módulo**, por decisión del responsable del proyecto. Gobierna la **vista de hotlinks** —los productos cuyo alcance llega a ese canal (`RN-PM-019`, v0.17.0)— y no reutiliza `products:read` por el mismo motivo que `products:sale`: aquel abre el catálogo administrativo entero, con lo inactivo y lo retirado dentro, y concederlo para ver un canal comercial sería dar la lectura de todo para ver tres líneas. **Nace SIN ENDPOINT que lo exija**, y es deliberado: el canal de hotlinks no está construido, sembrar el permiso antes **no rompe nada** —el catálogo es datos, y su único efecto es poder concederse— y es lo que ya hizo `V51` con los cuatro `movements:`. Lo que evita es llegar al requerimiento que lo necesite y tener que sembrar el permiso **y** construir la vista en el mismo Pull Request. `V60__seed_products_hotlink_permission.sql` lo siembra y lo asocia a **`SUPERADMIN` y a `ADMIN`** en la misma migración, **sin reserva**: decidirlo de otro modo habría creado la cuarta reserva del superadministrador, y ver qué se publica en un canal comercial no es una operación que deba quedar exclusiva de la raíz — `V40` ya estableció que el catálogo comercial de `PM` es administración ordinaria. La migración lleva la **guarda** que `V51` estrenó, y aquí vale por lo contrario: aquella comprobaba una reserva deliberada y esta comprueba que **no** la hay — olvidar la fila de `ADMIN` no falla al aplicar, deja a `ADMIN` incapaz de conceder lo que no tiene. **A `CLIENTE` no se le asocia**, por lo mismo que `products:sale`: `V30` siembra ese rol sin permisos a propósito. Nace `ProductsPermissionsSeedIT`, que es **lo único que verifica este permiso**: sin endpoint, ninguna prueba de API lo toca, y una asociación que se cayera del guion no rompería nada hasta que alguien intentara crear un rol que la necesitara. El catálogo del sistema pasa de treinta y siete a **treinta y ocho** (suite: 1006 → **1010**, en verde) ([`security.md` §4.4](../security.md#44-catalogo-de-permisos) v0.40.0). | Responsable del proyecto |
| 0.19.0 | 07-09-2026 | **Un upgrade puede declarar la misma membresía en los dos lados: nace la RENOVACIÓN**, por decisión del responsable del proyecto. `RN-PM-017` tenía **dos mitades metidas en una** —«no bajes» y «no repitas»— y solo la primera protegía algo: la segunda impedía cobrar por **tiempo**, que es un producto legítimo. La comparación pasa de estricta a **mayor o igual**, y `V61` **retira `ck_products_origen_distinto`**, que prohibía exactamente lo que ahora se admite. Queda dicho lo que eso cuesta: **de `RN-PM-017` ya no queda nada declarado en el esquema** — la mitad que sobrevive necesita el `level` de dos filas de `memberships` y vive entera en el caso de uso, sin la red que tenía. **Lo que se vende en una renovación es tiempo y no nivel**, y `RN-MV-020` ya lo entrega sin una línea nueva: cierra la membresía abierta e inserta la comprada, y aquí las dos son del mismo nivel. **Y esto obligó a pagar una deuda de cinco días** (§5.2.3): `RN-PM-011` seguía diciendo «solo si su membresía vigente es de nivel inferior al destino» y `findOffer` seguía comparando niveles, cuando §5.2.1 había declarado la **coincidencia por origen** el 02-09-2026 — el documento se contradecía consigo mismo y el código estaba del lado de la regla vieja. **La renovación no se puede expresar comparando niveles**: abrir la comparación a «inferior o igual» le ofrecería a quien está en `ORO` un `PLATINO → ORO`, que no es suyo. `RN-PM-011` se reescribe entera y `RF-PM-007` pasa a `source_membership_id = mi membresía` (`T-20`, pendiente desde el 02-09-2026, **construida**). **La garantía de que no se ofrecen bajadas no se pierde al quitar el filtro de niveles**: la sostiene `RN-PM-017` comprobada **al registrar**, porque un producto declarado desde mi membresía no puede apuntar por debajo. En `MV`, `RN-MV-006` admite el mismo nivel y sigue rechazando el inferior ([`requirements/mv.md` v0.10.0](mv.md)) — su código ya lo había anticipado por escrito: «el día que se vendan renovaciones del mismo nivel». | Responsable del proyecto |
| 0.20.0 | 07-09-2026 | **Nace `RF-PM-008`, el hotlink: el primer endpoint público del módulo y el primero del sistema que publica el nombre de una persona.** Por decisión del responsable del proyecto, un enlace repartido por un vendedor abre —**sin token**— una pantalla con el producto y con quién lo ofrece, y el producto llega con su precio **convertido a la moneda por omisión** usando la tasa vigente (`RF-SP-047`). **Vive en `PM` y no en `SP`, y eso no es una preferencia**: devuelve un producto, un vendedor y una tasa, y `modules.md` §7 prohíbe el ciclo — ponerlo en `SP` obligaría a que la raíz del grafo leyera `products`. `SP` publica **dos lecturas nuevas** por la vía de **D-25**: el vendedor por nombre de usuario y la tasa vigente entre dos monedas. **Nacen dos reglas críticas.** `RN-PM-021` — **solo se publica lo activo y de alcance `HOTLINKS`**, y con ella `RN-PM-019` **filtra por primera vez**: el alcance llevaba cuatro commits declarado sin acotar ninguna consulta. `RN-PM-022` — **de la persona solo el nombre y el apellido, y solo si es fuerza comercial**; un cliente, un administrador y un nombre de usuario inexistente responden **lo mismo**. **La decisión de seguridad es esa uniformidad**: los seis casos que no proceden devuelven el **mismo `404`**, porque distinguirlos convertiría el endpoint en un oráculo — bastaría fijar un código válido e ir variando el usuario para saber quién existe. Queda escrito que **eso no evita el recorrido a ciegas**, que lo acota `RateLimitFilter` por origen, y que **acotar no es impedir**. **Y queda una pregunta abierta que este cambio destapa**: `products:hotlink`, sembrado el mismo día en `V60` para «la vista de hotlinks», **se queda sin endpoint que lo exija** si esa vista es pública. La tripleta propone reconciliarlo —gobernaría la vista **autenticada** donde un vendedor administra sus enlaces— y **la decisión no está tomada**. | Responsable del proyecto |
| 0.21.0 | 08-09-2026 | **Un producto lleva DOS precios, y solo uno de ellos es dinero**, por decisión del responsable del proyecto. El **precio del sistema** —`price`, el que ya existía— sigue siendo el que se cobra: lo copia la venta y sobre él comisiona `CM`. Nace el **precio público** —`public_price`, opcional—, que es **lo único que ve quien no administra el catálogo** y que **no interviene en ningún cálculo**. `RN-PM-023` lo declara y `RN-PM-024` acota dónde puede verse cada uno: los dos en `RF-PM-002` y `RF-PM-003`, con `products:read`; **uno solo** en `RF-PM-007` y `RF-PM-008` — el público si existe y el del sistema si no—, porque devolver el par publicaría la diferencia entre lo que se anuncia y lo que se cobra, que es justo la decisión comercial que el campo existe para no enseñar. **El nulo del precio público significa algo y no es cero**: «este producto no declara precio público», y es lo que permite que la migración no invente un valor para las filas de hoy. **`RN-PM-006` se relaja y cambia de forma**: de «mayor que cero» a **«ningún precio es negativo»**, en los dos importes. Lo que la tumbó no fue este cambio sino la **renovación** — un `BECA → BECA` es un producto legítimo que vale cero, y prohibirlo obligaba a inventarle un céntimo—. `V67` —planificada como `V65` y corrida dos huecos el mismo día, porque las tasas de cambio se llevaron `V65` y `V66`: **una migración reservada no está reservada**— renombra `ck_products_price_positive` a `ck_products_price_no_negativo` porque el nombre viejo habría mentido, y añade el `CHECK` del público con su rama `IS NULL` explícita. **Y esa relajación rompía una cuenta que ya existía**: `RN-CM-019` convierte un valor fijo con `fixed_amount ÷ precio`, y `ProductCommissionCapGuard` confiaba **por escrito** en que el precio nunca fuera cero — con un producto gratuito, esa división es un `500`. Se resuelve sin regla nueva, llevando `RN-CM-019` a su límite: sobre precio cero, cualquier valor fijo mayor que cero paga más del 100 % y se rechaza con el mismo mensaje. **Queda escrito lo que este cambio cuesta y no se tapa**: quien compra ve el precio público y se le cobra el del sistema, ninguna regla compara los dos importes, y la salida —un `CHECK` de `public_price >= price`— es una migración de tres líneas el día que se decida. Y `RN-PM-024` **se rompe en el comprobante a propósito**: `RF-MV-002` devuelve el importe cobrado, porque un comprobante que no lo dice no sirve. | Responsable del proyecto |
| 0.22.0 | 08-09-2026 | **`RN-PM-024` se reescribe y dice lo contrario: los dos precios se publican en toda lectura, y la conversión con ellos.** Decisión del responsable del proyecto, tomada **el mismo día** que la anterior y **sobre la advertencia de lo que cuesta**. Las cuatro lecturas del módulo —`RF-PM-002`, `RF-PM-003`, `RF-PM-007` y `RF-PM-008`— devuelven `price` —el que se cobra—, `publicPrice` —el que se anuncia, **nulo** si no se declara— y `exchange`, la conversión a la moneda por omisión con la tasa vigente de `RF-SP-047`, **presente y nula** cuando no hay nada que convertir. **Lo que se gana es una sola forma**: hasta hoy `price` significaba dos cosas según el endpoint —el del sistema en el catálogo, «el que se muestra» en la oferta y el hotlink, resuelto por un `COALESCE` que el cliente no veía—, y **nada en la respuesta decía cuál era cuál**. **Lo que cuesta está escrito entero en §5.2.5 y no se disimula**: la diferencia entre lo anunciado y lo cobrado queda visible **sin token** en el hotlink, que es exactamente lo que la regla anterior existía para impedir; se retira a conciencia, y lo que un endpoint público devolvió una vez ya no se recupera. `RN-PM-024` **baja de Crítica a Alta**, porque la regla nueva es de forma de la respuesta —faltan campos o no faltan, y eso se ve— mientras que la vieja lo era por lo contrario: incumplirla **no fallaba, publicaba**. **La conversión se calcula sobre el importe que se muestra** —el público si existe y el del sistema si no— y no sobre los dos, porque dar dos importes convertidos obligaría a decir cuál corresponde a cuál, que es la ambigüedad que el cambio viene a quitar. **Y trae una condición de implantación que el listado impone**: la moneda por omisión se resuelve **una vez por página** y las tasas de todas las monedas presentes **en una sola sentencia**, porque preguntar por fila sería el `N+1` que `RF-PM-002` existe para evitar. El aviso de §5.2.4 sobre el comprobante **deja de aplicar**: sin regla que acote el precio del sistema, `RF-MV-002` ya no es una excepción a nada. | Responsable del proyecto |
| 0.23.0 | 12-09-2026 | **El precio público se convierte en precio de compra: la misma columna, otro significado, y por eso otro alcance de visibilidad.** Decisión del responsable del proyecto, con tres respuestas que se le preguntaron antes de escribir: el segundo importe es **lo que NEXUS paga por el producto** cuando tiene que comprarlo —el costo, no lo que paga el cliente, que sigue siendo `price` y lo único que la venta copia—; **solo se ve en administración** —`RF-PM-002` y `RF-PM-003`, bajo `products:read`— y **sale de la oferta propia y del hotlink**, que desde el 08-09-2026 lo publicaban sin token; y **sigue siendo opcional**, porque un producto que todavía no se ha comprado no tiene costo que declarar y la migración no tiene ningún valor honesto que inventar. `public_price` se renombra a `purchase_price` con su `CHECK`, en una migración nueva y no reescribiendo `V67`. **`RN-PM-023` se reescribe** —«su precio de compra», y el nulo pasa de «se anuncia con el del sistema» a «no se conoce»— y **`RN-PM-024` se reescribe por tercera vez y vuelve a Crítica**: recupera su forma original —«el otro precio no sale de administración»— con el costo en el lugar del precio del sistema, y por el mismo motivo: incumplirla **no falla, publica** el margen, y en el hotlink sin token. **Lo que se simplifica**: desaparece «el importe que se muestra», porque fuera de administración `price` es lo único que se muestra y `exchange` se calcula siempre sobre él; la advertencia de §5.2.4 —«quien compra ve un importe y se le cobra el otro»— deja de aplicar. **Lo que vuelve**: `OfferItem` y la respuesta del hotlink pierden el campo, sus consultas dejan de seleccionarlo, y `CA-PM-160` y `CA-PM-163` vuelven a exigir la **ausencia**, con la prueba de `CA-PM-169` invertida de vuelta en vez de borrada. **Queda escrito que ningún costo llegó a publicarse**: lo que el hotlink devolvió entre el 08-09-2026 y hoy era lo que se anunciaba, y la columna cambia de significado vacía de él. §5.2.6 nueva; §5.2.4 y §5.2.5 se conservan con su aviso al frente. | Responsable del proyecto |
| 0.24.0 | 14-09-2026 | **Nacen las RESEÑAS: un producto se puntúa de uno a cinco y se comenta, y solo el autor toca lo suyo.** Por decisión del responsable del proyecto, con **cuatro respuestas preguntadas antes de escribir** (§5.2.7): quien reseña es quien porta **`products:comment`**, un permiso nuevo y el primero de escritura del módulo que no es de administración —se descartó `products:sale`, porque ver la oferta y opinar son dos capacidades, y «solo quien compró», porque `PM` no puede leer a `MV` sin cerrar el ciclo—; **una reseña por persona y producto, y se corrige** (`RN-PM-026`); **se retira SIN motivo**, y para eso **se enmienda el Art. V.13** con una tercera excepción, el **contenido propio** (`RN-PM-029`, `constitution.md` v0.8.0) — se descartó exigirlo como en `RF-PM-006` porque pedirle a un cliente que justifique por qué borra lo suyo produce «lo borro» en cada fila—; y **la lista es pública y el producto publica promedio y cantidad en sus cuatro lecturas** (`RN-PM-030`, `RN-PM-031`). Nace el submódulo **Reseñas** con cinco requerimientos, `RF-PM-009` a `RF-PM-013`, siete reglas —`RN-PM-025` a `RN-PM-031`, dos críticas— y la tabla **`product_comments`** (§10.4), la segunda del módulo y la primera con clave foránea a `users`. **Lo que la decisión de la moderación cuesta está escrito entero y no se tapa**: nadie —ni la administración— retira una reseña ajena, y la salida es otro requerimiento con otro permiso, no una excepción a `RN-PM-027`. Queda escrito también que **`user_id` es el autor y no el actor**, y por qué eso no infringe el Art. V.7; que **el promedio es una cuenta y no una columna**, para que la copia que se quedara atrás no pueda mentir; y que las reseñas de un producto de alcance `TIENDA` **se leen sin token aunque el producto no**, con la salida barata escrita. El retiro es el **primer `DELETE`** del módulo, y lo es por lo mismo que el del producto es un `POST`: sin motivo no hay cuerpo que proteger. Los cinco requerimientos nacen en `Pendiente`; las tripletas son el paso siguiente. | Responsable del proyecto |
| 0.25.0 | 14-09-2026 | **Las cinco `spec.md` de las reseñas quedan redactadas** y `RF-PM-009` a `RF-PM-013` pasan a `Spec en revisión`. Cuatro decisiones de forma que las cinco comparten y que conviene revisar juntas: **el `404` del producto es uniforme** —inexistente, inactivo, retirado— tanto al reseñar como en la lista pública, porque quien reseña es un cliente y ve la oferta, que tampoco distingue; **primero «existe» (`404`) y después «es tuya» (`403`)** en la corrección y el retiro, y el `403` no revela nada porque la lista pública ya enseña la reseña con su identificador; **retirar dos veces responde `404` y no `409`**, al revés que el producto, porque la reseña retirada no la devuelve nadie; y **la lectura de la propia no consulta el producto**, de modo que cuesta una sentencia y responde sobre productos que ya no se venden — la única que le da al autor el camino a la suya. `RF-PM-009` es además el que **enmienda las cuatro lecturas** con `rating` (`RN-PM-031`), calculado en la misma sentencia para que el número de consultas de los listados no suba. Nacen `CA-PM-170` a `CA-PM-218`. §10.4 no cambia: `created_at` y `updated_at` ya estaban declaradas, y el diagrama de `modelo-datos.md` las incorpora hoy. | Responsable técnico |
| 0.26.0 | 14-09-2026 | **Los cinco `plan.md` aprobados y las cinco `tasks.md` redactadas**: la tripleta de las reseñas está completa y `RF-PM-009` a `RF-PM-013` pasan a `Tasks en revisión`. **Cincuenta y nueve tareas.** Tres decisiones de los planes alcanzan más allá de su requerimiento: **el agregado de `rating` entra en las cuatro sentencias por un `LEFT JOIN LATERAL`** sobre el índice parcial, con el redondeo en Java y en un solo sitio, para que el número de consultas de los listados no suba (`RF-PM-009` §4.1); **el `403` de propiedad se hace después de resolver la fila y nunca filtrando por actor en la consulta**, porque eso convertiría la ajena en `404` sin que nadie lo decidiera (`RF-PM-010` §5); y **la lista pública entra en la lista por método de `SecurityConfig` y no en `RUTAS_PUBLICAS`**, con la cota de tasa contada por la familia y la política de los catálogos (`RF-PM-012` §5, §8). **Las enmiendas Art. I.7 por `rating` se aplican en este mismo pase**, y no con el código: las specs de `RF-PM-001` a `RF-PM-004`, `RF-PM-007` y `RF-PM-008` ganan su fila y su párrafo de §6.2 citando `RF-PM-009`. Orden de construcción: `009 → 012 → 013 → 010 → 011`, con `T-10` y `T-11` de `009` —la enmienda a lo ya construido— **antes** que su servicio de alta. | Responsable del proyecto |
| 0.27.0 | 14-09-2026 | **Un producto puede enlazar un VIDEO, y el enlace sale en las cuatro lecturas.** Por decisión del responsable del proyecto, con **cuatro respuestas preguntadas antes de escribir** (§5.2.8), y las cuatro del lado más abierto: **se ve en las cuatro lecturas** —catálogo, detalle, oferta y hotlink sin token— porque es material de venta y no un costo, al revés que el precio de compra; **se valida solo la forma** —URL absoluta `http` o `https`, sin espacios, hasta 500 caracteres, de cualquier dominio— y el sistema **no sigue el enlace**: no comprueba que exista, no lo descarga, no lo incrusta; **en los dos tipos**, sin la condición cruzada del icono; y **opcional, corregible y vaciable**, sin condicionar la activación. Nace **`RN-PM-032`**. §10 gana `video_url` —`varchar(500)`, nulo cuando no hay— y `ck_products_video_url_format`, con la rama `IS NULL` delante. **`RN-PM-024` no se toca**: el enlace va a las cuatro lecturas precisamente porque no es el costo. Queda escrito lo que se acepta al publicarlo sin token: **es una dirección que alguien con `products:update` escribió, tal cual**, con el mismo trato que la descripción. Enmienda las tripletas de `RF-PM-001` a `RF-PM-004`, `RF-PM-007` y `RF-PM-008` (Art. I.7). | Responsable del proyecto |
