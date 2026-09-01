# Requerimientos del Módulo — `MV` Movimientos

| Campo | Valor |
|---|---|
| Módulo | `MV` — Movimientos |
| Paquete | `modules/movements` |
| Prefijos de permiso | `movements:` |
| Versión | 0.9.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 01-09-2026 |
| Última actualización | 01-09-2026 |

!!! info "Qué va en este documento"

    El catálogo de requerimientos del módulo: qué debe hacer, bajo qué reglas y con qué permisos.

    El comportamiento detallado de cada requerimiento —flujos, validaciones, criterios de aceptación y casos límite— vive en su tripleta, en `docs/specs/mv/`. Aquí no se repite.

!!! warning "Documento en Borrador: tres decisiones lo condicionan"

    1. **El código `MV`.** Un código, en cuanto aparece en un identificador, no se cambia jamás ([`modules.md` §2.1](../modules.md#21-regla-de-decision)). En cuanto exista `RF-MV-001`, estas dos letras quedan fijadas para siempre. Se procede por decisión del responsable del proyecto, como ya se hizo con `PM` y con `CM`.
    2. **La frontera del alcance** (§1.3): este módulo **registra lo que ocurrió con el dinero**; no cobra, no aplica el efecto de lo comprado y no emite documentos fiscales. El motivo, en §1.4.
    3. ~~**D-26 — la primera escritura entre módulos.**~~ **Cerrada el 01-09-2026**: `SP` publica la operación concreta y `MV` la invoca, síncrona y en la misma transacción ([`architecture.md` §15.2.1](../architecture.md)). Lo que queda condicionando este documento en su lugar es **qué operaciones deshacen lo ya aplicado** (§5.3), que `RN-MV-031` deja a la vista al cerrar la puerta de la anulación.

---

## 1. Información del módulo

### 1.1 Descripción

`MV` es dueño de **lo que ocurrió con el dinero**. Un movimiento es un **hecho económico** ya sucedido: alguien compró algo, alguien depositó, alguien retiró, a alguien se le pagó una comisión.

No es un registro de ventas. Es el **libro** del sistema, y por eso lleva un tipo: es lo que permite que la compra de un upgrade, el depósito inicial de un cliente y el pago de una comisión vivan en la misma tabla sin dejar de ser cosas distintas.

**Un movimiento no se modifica y no desaparece.** Corregir uno es emitir otro que lo revierte, y eso no es formalismo contable: es la única forma de que la pregunta «¿qué se pagó, cuándo y por qué?» siga teniendo respuesta dentro de tres años.

### 1.2 Objetivo

Hoy el sistema sabe **qué se vende** (`PM`), **quién vende** y a quién trae cada quien (`SP`), y **cuánto se le paga a quien vende** (`CM`). No sabe **qué se vendió**.

Esa ausencia no es un hueco más: es la que bloquea a los otros tres. `requirements/pm.md` §1.4 no registra la compra «porque no existe el cobro»; `requirements/cm.md` §1.4 no calcula «porque no hay tabla de ventas a la que aplicar un porcentaje»; y `RF-SP-045` deja la cuenta de un cliente nuevo en `FTD_PENDIENTE` **sin nada que pueda sacarla de ahí**.

Este módulo pone ese objeto en el sistema, y con él se cierran los tres.

### 1.3 Alcance

**Incluye**

- Registrar un **depósito**: el dinero que entra a nombre de un cliente. El primero de todos es el **FTD**, y es el que habilita su cuenta.
- Registrar una **compra**: qué productos pidió quién —**pueden ser varios**—, a qué precio, en qué moneda y con qué vigencia. Todo **copiado en el momento**, línea a línea, nunca leído después del catálogo.
- Consultar los movimientos, en lista y en detalle, y que **cada persona consulte los suyos**.
- **Anular** un movimiento emitiendo su inverso.
- El **comprobante interno** de cada movimiento, con numeración propia.
- El catálogo de **métodos de pago**, y qué **pasarela** procesó cada transacción con su referencia externa.
- **Recibir y conservar lo que los sistemas externos notifican**, tal como lo envían: la pasarela de pago y el bróker. Es lo único que permite reconciliar cuando el sistema y el proveedor no coinciden.

**No incluye**

- **Cobrar.** Este módulo registra que se pagó; **quién ejecuta el cobro es la pasarela**, y la integración con ella es un requerimiento propio que no se abre aquí. Un movimiento en `PENDIENTE` es exactamente eso: un cobro que todavía no confirmó nadie.
- **Aplicar el efecto de lo comprado.** Que un upgrade cambie el nivel de alguien es escribir en `user_memberships`, tabla de `SP` con sus propias reglas (`RF-SP-032`, `RN-SP-018`). Ver §1.4.
- **La factura fiscal.** El comprobante de este módulo **no es un documento DIAN**: no lleva resolución, ni rango autorizado, ni nota crédito. Ver §1.5, que existe para que nadie lo use como si lo fuera.
- **La resolución del porcentaje.** Cuál le corresponde a cada persona lo responde `CM` (`RF-CM-005`), y este módulo lo llama **una vez por cada nivel de la cadena**. Lo que sí es de aquí es **aplicarlo**: el devengo y el pago son movimientos (`RN-MV-019` a `RN-MV-023`), y esa es la mitad que la etapa 2 construye.
- **Retiros, balances y egresos.** Son la tercera etapa del módulo y no se registran todavía: una salida de dinero tiene aprobación, saldo disponible y un perfil de riesgo entero que no se puede escribir de paso. Quedan declarados en §4.2.
- **Generar el PDF del comprobante.** El backend **no maneja ningún binario**: ningún controlador acepta ni devuelve ficheros, y abrir esa puerta es una decisión de infraestructura que ningún requerimiento respalda hoy. Se publican los datos; quien los pinte los pinta.

### 1.4 La frontera, y por qué está donde está

**Un movimiento no cambia el nivel de nadie.** Registra que alguien compró un upgrade; quien lo aplica es `SP`, porque `user_memberships` es suya. Es la misma frontera que [`requirements/pm.md` §1.4](pm.md) trazó para el catálogo, y por las mismas dos razones — con una diferencia que sí es nueva y que este documento no puede esquivar.

`PM` podía quedarse fuera porque **no necesitaba escribir nada** en `SP`: publicaba un catálogo y ahí terminaba su trabajo. `MV` no puede: un **depósito confirmado tiene que habilitar la cuenta del cliente**, o `RF-SP-045` deja a todo el mundo encerrado en `FTD_PENDIENTE` para siempre. Eso es una escritura, cruza la frontera, y **todas las interfaces publicadas hasta hoy son de solo lectura**. De ahí sale **D-26** (§3).

**Y el efecto solo lo produce un movimiento confirmado.** Un movimiento `PENDIENTE` es un cobro que nadie verificó todavía: no habilita cuentas, no sube niveles y no comisiona. Es la traducción exacta del argumento con el que `PM` se negó a registrar compras antes de que existiera el cobro — «un objeto que dice que alguien pagó cuando nadie verificó que pagara» —, con la diferencia de que aquí el objeto **sí** existe y lo que lo separa de la realidad es un **estado**, no su ausencia.

**Lo que este módulo sí deja resuelto para quien venga después:** el movimiento **congela** el importe, la moneda, la vigencia y el vendedor. Ninguno se vuelve a leer del catálogo ni de la estructura comercial. Es la condición que `PM` y `CM` le impusieron a este módulo antes de que existiera, y es la que impide que corregir un precio reescriba una factura, o que reasignar un cliente cambie a quién se le pagó por una venta de hace un año.

### 1.5 El comprobante no es una factura, y conviene decirlo aquí

El comprobante que emite este módulo lleva **numeración propia y única por tipo**, y sirve para que una persona sepa qué compró y por cuánto. **No es un documento fiscal.**

La diferencia no es de matiz. Una factura electrónica en Colombia va bajo **resolución de la DIAN** con rango numérico autorizado, no se modifica jamás —se anula con **nota crédito**— y su emisión pasa por un proveedor tecnológico autorizado. FACTECH GROUP SAS es una sociedad colombiana, de modo que esto llegará.

**Se decide por adelantado cómo llegará**, para que llegue como una ampliación y no como una reescritura: la factura fiscal será una **entidad aparte** que apunta al movimiento y tiene su propia numeración, y no un campo más de esta tabla. Un movimiento puede existir sin factura —un depósito no se factura— y una factura no puede existir sin el hecho que la origina.

### 1.6 Este módulo nace con datos que ya existen

**Hay que importar movimientos de una plataforma anterior** (decisión del responsable del proyecto, 01-09-2026). No es un detalle de puesta en marcha: cambia el diseño en cuatro sitios, y dos de ellos ya estaban resueltos por casualidad.

| Qué cambia | Por qué |
|---|---|
| **Nada de numeración sin huecos** | Lo importado trae sus propias fechas y su propio orden. Una serie estrictamente correlativa no sobrevive a una carga histórica, y prometerla obligaría a renumerar lo migrado — que es reescribir el pasado para que encaje en la forma nueva. `RN-MV-008` lo retira |
| **`occurred_at` separado de `created_at`** | Ya estaba, y ahora se ve para qué: lo importado ocurrió hace meses y se registra hoy. Confundirlos habría desplazado años de historia al día de la carga |
| **La fecha del código sale del hecho** | `RN-MV-017`. Un movimiento de marzo importado en septiembre lleva `20260315` en su código, no `20260901` |
| **Hay que poder distinguir lo importado de lo nacido aquí** | Sin marca, no se puede reconciliar contra la plataforma vieja ni saber qué parte del libro tiene la calidad de origen y qué parte la calidad de una carga |

**Lo último se resuelve con lo que ya hay**: `inbound_notifications` lleva `source` y `movements` lleva `gateway` y `external_reference`. La plataforma anterior es **un emisor más** —un `source`—, y el identificador que cada movimiento tenía allí es su `external_reference`. Con eso, la unicidad de `RN-MV-013` hace además que **reejecutar la importación no duplique nada**, que es la propiedad que toda carga necesita y casi ninguna tiene.

**Lo que este documento no resuelve** es el mapeo: qué campos de la plataforma anterior corresponden a cuáles de aquí, qué se hace con lo que no encaja, y si lo importado nace `CONFIRMADO` sin pasar por las validaciones del caso de uso. Es un requerimiento propio y probablemente **de una sola ejecución**, y mezclarlo con el registro ordinario dejaría el caso de uso normal lleno de ramas que solo se usan una vez.

---

## 2. Submódulos

Según [`modules.md` §5](../modules.md#5-fichas-de-modulo).

| Submódulo | Responsabilidad | Entidades principales |
|---|---|---|
| Movimientos | Registrar, consultar y anular hechos económicos | `movements`, `movement_details` |
| Medios de pago | Con qué se pagó y quién lo procesó | `payment_methods` |
| Notificaciones entrantes | Lo que los sistemas externos dicen, tal como lo dicen | `inbound_notifications` |

**Por qué las notificaciones son un submódulo y no una columna más.** Lo que llega de fuera y lo que el sistema concluye son **dos cosas distintas**, y la diferencia es toda la utilidad de guardarlo: el movimiento es la interpretación, la notificación es el hecho. Meterla como columna de `movements` obligaría a que toda notificación produjera un movimiento — y muchas no lo hacen: las que llegan repetidas, las que avisan de estados que no nos interesan, y las que no validan la firma.

**Por qué los medios de pago son un submódulo y no un catálogo de `SP`.** Los catálogos de `SP` —monedas, países, membresías— los necesita **el sistema entero** para autorizar, validar o mostrar. Un método de pago solo lo necesita quien registra dinero, y `modules.md` §2.1 es explícito: si solo lo usa un módulo, es un submódulo suyo.

---

## 3. Dependencias

| Módulo | Tipo | Para qué |
|---|---|---|
| `SP` | Consume | **Usuarios**: que el cliente y el vendedor existan |
| `SP` | Consume | **Monedas**: la del importe, con sus decimales |
| `SP` | **Escribe** | **Habilitar la cuenta** de un cliente cuando su depósito se confirma (`RF-SP-045`) |
| `PM` | Consume | **Productos**: su precio, su moneda, su vigencia y su membresía destino, para copiarlos |
| `CM` | — | **No lo consume `MV`: es `CM` quien consumirá a `MV`** cuando exista la liquidación |

La dependencia sigue siendo **acíclica**: `MV` → `PM` → `SP`, y `MV` → `SP`. Nadie apunta hacia `MV`.

!!! danger "D-26 — la primera escritura entre módulos, y no está resuelta"

    Las cuatro interfaces publicadas hasta hoy —tres de `SP` hacia `PM`, una de `PM` hacia `SP`— son **de solo lectura**, y [`architecture.md` §15.2](../architecture.md) lo declara como parte de la norma: «se devuelven **modelos de lectura y nunca entidades** — el agregado filtraría JPA y daría **con qué escribir**».

    **Aquí hace falta escribir.** Un depósito confirmado debe sacar una cuenta de `FTD_PENDIENTE`, y `users` es de `SP`. Las tres salidas posibles son distintas y ninguna es obviamente mejor:

    | Salida | Qué implica |
    |---|---|
    | `SP` publica una **operación de aplicación** —«habilitar cuenta por depósito»— que `MV` invoca | Mantiene la regla dentro de `SP`, que es donde vive. Es lo que [`requirements/pm.md` §1.4](pm.md) ya anticipó para el upgrade: «obliga a que `SP` **publique** esa escritura como interfaz de aplicación, con sus reglas intactas» |
    | `MV` **emite un evento** y `SP` reacciona | Desacopla, y a cambio la habilitación deja de ser inmediata y aparece la pregunta de qué pasa si nadie la atiende |
    | `SP` **consulta** los movimientos para decidir | Invierte la dirección y **abre el ciclo** `SP` → `MV` → `SP`, que §7 prohíbe. Descartada de entrada |

    **La primera es la que sigue el precedente escrito**, y es la que este documento recomienda. Queda como decisión abierta porque establece cómo se escribirá entre módulos **para siempre**, y esa forma no la debe fijar un requerimiento de paso.

---

## 4. Requerimientos funcionales

### 4.1 Resumen

| ID | Nombre | Submódulo | Permiso |
|---|---|---|---|
| `RF-MV-001` | Registrar un depósito | Movimientos | `movements:create` |
| `RF-MV-002` | Registrar una compra | Movimientos | `movements:create` |
| `RF-MV-003` | Confirmar una compra pendiente | Movimientos | `movements:confirm` |
| `RF-MV-004` | Consultar movimientos | Movimientos | `movements:read` |
| `RF-MV-005` | Consultar el detalle de un movimiento, con su comprobante | Movimientos | `movements:read` |
| `RF-MV-006` | Consultar los movimientos propios | Movimientos | Autenticado |
| `RF-MV-007` | Anular un movimiento | Movimientos | `movements:void` |
| `RF-MV-008` | Consultar los métodos de pago | Medios de pago | Autenticado |
| `RF-MV-009` | Recibir una notificación de un sistema externo | Notificaciones entrantes | **Público**, autenticado por firma |
| `RF-MV-010` | Consultar las notificaciones recibidas | Notificaciones entrantes | `movements:read` |
| `RF-MV-011` | Reprocesar una notificación | Notificaciones entrantes | `movements:reprocess` |

**`RF-MV-009` es el segundo endpoint público del sistema que escribe**, después de `RF-SP-045`. Lo que lo autoriza no es un token sino la **firma** con la que el emisor sella su mensaje, igual que a `RF-SP-040` lo autoriza el permiso temporal que él mismo emitió.

**`RF-MV-011` existe porque un documento guardado y no reprocesable no sirve de nada.** Si un defecto de interpretación deja veinte notificaciones sin convertir en movimientos, la tabla las tiene y hace falta una operación para volver a intentarlo — corregido el defecto. Sin ella, la única salida sería pedirle a la pasarela que reenvíe, que no siempre se puede.

**Lleva permiso propio y no reutiliza `movements:create`**, aunque acabe creando movimientos. Reprocesar es la operación con más capacidad de hacer daño del módulo: aplicada sobre algo ya procesado, y si la idempotencia falla, duplica dinero. Quien concilia pagos no tiene por qué poder lanzarla.

**El depósito y la compra son dos requerimientos y no uno**, y eso **se aparta del precedente** que `PM` y `CM` fijaron —«el alta es una, no dos»—. La razón por la que aquí no aplica no es el contenido del movimiento sino **quién lo pide y por dónde entra**: la compra la origina una persona autenticada en un flujo de pago; el depósito lo origina un **sistema externo** por un canal con otra autenticación, otra idempotencia y otro límite de tasa. Fundirlos obligaría a un endpoint con dos modelos de seguridad, que es donde se cuela el que sobra.

**`RF-MV-003` es de compras y no de depósitos**, y esa asimetría la destapó dibujar los flujos (`flujos/mv/flujos-del-modulo.md` §6.1). Un **depósito nace `CONFIRMADO`**: es un hecho que ya ocurrió y del que un tercero avisa, de modo que confirmarlo después sería preguntarle al sistema si cree lo que el bróker acaba de decirle. Una **compra nace `PENDIENTE`** porque el cobro está en marcha y todavía puede fallar.

**Y existe porque el registro y la confirmación de una compra están separados en el tiempo.** Un cobro se inicia, viaja a la pasarela y vuelve; entre las dos cosas el movimiento existe y no produce efectos. Sin este requerimiento habría que elegir entre registrar el movimiento cuando ya no se puede fallar —perdiendo el rastro de los cobros que no llegaron— o darlo por bueno al crearlo, que es lo que `PM` §1.4 llama «un objeto que dice que alguien pagó cuando nadie lo verificó».

**No hay requerimiento para editar un movimiento**, y no es un olvido: `RN-MV-001` lo prohíbe. Lo más parecido es `RF-MV-007`, que **no lo cambia**: emite otro.

### 4.1.1 El ciclo de una comisión, y por qué no necesita estados nuevos

Una comisión pasa por tres momentos y **los tres caben en la máquina de estados que `movements` ya tiene**:

| Momento | Estado | Qué significa |
|---|---|---|
| La venta se confirma | **`PENDIENTE`** | La comisión existe y **no es exigible**. `RN-MV-004` ya dice que lo pendiente no produce efectos |
| El primero de mes, 00:00 | **`CONFIRMADO`** | **Causada**: pasa a ser una obligación firme |
| Se paga | — | Un movimiento aparte, `COMISION_PAGADA`, que la salda (`RN-MV-023`) |

**Causar es confirmar**, y descubrirlo tiene valor: no hay que inventar `CAUSADA` ni ampliar `ck_movements_status`. El trabajo mensual **usa `RF-MV-003`**, que ya existe, en lote.

!!! success "La espera hasta el cierre resuelve el caso feo que este documento tenía abierto"

    Hasta ahora, anular una venta **ya comisionada y ya pagada** dejaba un **saldo negativo** contra el vendedor, y no había forma limpia de recuperarlo.

    Con la comisión esperando al cierre, **una venta anulada dentro del mes simplemente anula sus comisiones pendientes**: nadie cobró nada, no hay nada que recuperar. El caso desagradable **no desaparece** —anular en febrero una venta de enero sí lo produce— pero deja de ser el camino habitual y pasa a ser la excepción.

!!! warning "La protección que da la espera es MUY desigual, y conviene saberlo"

    Una venta del día 31 se causa **horas después**; una del día 1 espera **un mes entero**. No es un defecto del diseño —es lo que significa un cierre de periodo— pero sí significa que **la venta de fin de mes está casi sin proteger**.

    Si lo que se busca es una ventana de seguridad y no un cierre contable, la forma sería otra: causar a los *n* días de cada venta. Queda anotado por si el objetivo era el primero.

**Y el trabajo mensual tiene que ser recuperable.** Si no corre el día 1 —un despliegue, una caída—, el día 3 tiene que causar lo que quedó pendiente, y hacerlo **dos veces no puede causar dos veces**. Es la misma forma de `ExpiredTokenPurgeJob`: `@Scheduled` con cron configurable y **cerrojo consultivo**, para que con varias instancias corra una sola.

### 4.2 Lo que se registrará después, y por qué no ahora

| Etapa | Qué trae | Por qué espera |
|---|---|---|
| **2 — Comisiones** | Al confirmar una venta, crear las comisiones **pendientes** de toda la cadena; el **primero de cada mes causarlas**; y pagar lo causado | El modelo está **decidido** (`RN-MV-019` a `RN-MV-023` y `RN-MV-029`) y solo falta escribirlo. Se deja para la etapa 2 porque necesita movimientos reales contra los que probarse: una comisión sobre una venta inventada no prueba nada. **Y una decisión sigue abierta**: cómo se comisiona un depósito (§5.3) |
| **3 — Retiros y balances** | Salidas de dinero y saldo disponible | Una salida tiene **aprobación**, **saldo** y un perfil de riesgo propio. Colgarla de este documento ahora la haría parecer una variante del depósito con el signo cambiado, que es exactamente lo que no es |

---

## 5. Reglas de negocio

### 5.1 Catálogo

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-MV-001` | El movimiento no se modifica ni desaparece | Siempre | Ningún importe, moneda, cantidad, producto ni participante cambia después de creado, y **no hay eliminación** — ni lógica ni física. Corregir es **emitir el inverso** (`RF-MV-007`) | **Crítica** |
| `RN-MV-002` | Se copia **lo que puede cambiar**; lo inmutable se referencia | Al registrar una compra | Precio unitario y vigencia se guardan **en la línea**, porque `RF-PM-004` **los corrige**. La **membresía destino no se copia**: `EX-004` de ese mismo requerimiento **rechaza cambiarla**, de modo que leerla del producto da siempre el mismo valor y copiarla solo añadiría un sitio donde el dato pudiera discrepar de sí mismo. No se leen del catálogo después. Es la condición que `requirements/pm.md` §1.4 impuso a este módulo antes de que existiera: sin ella, corregir un precio reescribe lo ya vendido | **Crítica** |
| `RN-MV-003` | El vendedor se congela | Al registrar cualquier movimiento de un cliente | Se guarda **quién era su vendedor en ese momento**, no se resuelve después por `user_supervisors`. Reasignar un cliente no debe cambiar a quién se le atribuyó una venta pasada — es el mismo argumento de `RN-MV-002` aplicado a la estructura comercial | **Crítica** |
| `RN-MV-004` | Solo un movimiento **confirmado** produce efectos | Siempre | Un movimiento `PENDIENTE` no habilita cuentas, no concede membresías y no comisiona | **Crítica** |
| `RN-MV-031` | **Lo ya aplicado no se anula** | Al anular | Anular solo se admite mientras el movimiento **no haya producido efectos**: una compra pendiente que la pasarela rechazó, una comisión todavía sin causar. **Una vez aplicado, no hay marcha atrás por esta vía** — deshacer un nivel concedido o una comisión causada es una **operación distinta**, con su propia decisión explícita, y **ninguna existe todavía**. Se eligió esto sobre deshacerlo todo —que obligaría a quitarle el nivel a quien lleva un mes usándolo— y sobre revertir solo el dinero —que dejaría el libro y el acceso discrepando—. **El precio está declarado: hasta que esas operaciones existan, un movimiento aplicado por error no se puede corregir** | **Crítica** |
| `RN-MV-005` | La referencia externa es única por pasarela | Al registrar y al confirmar | La misma notificación entregada dos veces —cosa que **toda** pasarela hace— debe producir **un** movimiento y no dos. Se declara en el esquema, porque una comprobación en el caso de uso no sobrevive a dos entregas simultáneas | **Crítica** |
| `RN-MV-006` | Un depósito no lleva producto; una compra sí | Al registrar | La condición se exige **en los dos sentidos**, como `RN-PM-002`: un depósito con producto promete una entrega que nadie hará, y una compra sin producto no dice qué se compró | Alta |
| `RN-MV-007` | La cantidad es uno en los upgrades | Al registrar una compra | Un upgrade con cantidad dos no significa nada: no se sube dos veces al mismo nivel. Los bots admiten más de uno. **No se puede declarar en el esquema** —un `CHECK` no consulta `products`— y vive en el caso de uso, como `RN-CM-001` | Alta |
| `RN-MV-008` | El comprobante es **único por tipo** y **no es fiscal** | Al confirmar | Cada tipo lleva su propia serie y un número no se reutiliza jamás. **No se promete «sin huecos»**, y la retirada de esa promesa es deliberada: hay que **importar movimientos de otra plataforma**, que traen sus propias fechas y su propio orden, y una serie sin huecos no sobrevive a eso — además de que una `SEQUENCE` de PostgreSQL deja huecos por diseño en cada transacción revertida. **No sustituye a la factura electrónica** (§1.5), que sí exigirá numeración estricta y será una entidad aparte | Alta |
| `RN-MV-009` | El importe respeta los decimales de su moneda | Al registrar | Igual que `RN-PM-007` para el precio del catálogo, y por lo mismo: la escala la decide la moneda y no la columna | Media |
| `RN-MV-012` | La notificación se guarda **antes** de procesarse | Al recibir | Recibir, verificar la firma, **guardar**, responder, y solo entonces interpretar. Guardarla después es tenerla en todos los casos **menos en el único que importa**: aquel en que procesarla falló. Y responder antes de procesar no es una optimización — las pasarelas tienen espera corta y reintentan, de modo que procesar primero convierte cada operación lenta en una reentrega | **Crítica** |
| `RN-MV-013` | La idempotencia tiene **dos capas**, no una | Al recibir y al registrar | El **identificador del evento** es único por emisor en `inbound_notifications`, y la **referencia externa** lo es en `movements` (`RN-MV-005`). La primera atrapa la reentrega **antes de interpretarla**, que es más barato y más seguro; la segunda la atrapa aunque el emisor mande dos eventos distintos para el mismo cobro. Las dos se declaran en el esquema | **Crítica** |
| `RN-MV-014` | La firma inválida **se guarda y no se procesa** | Al recibir | La fila queda con la firma marcada como no válida y el evento no produce nada. Es lo que convierte «alguien está intentando falsificar confirmaciones de pago» en algo que se puede **ver** en lugar de en un `401` que nadie mira. Exige límite de tasa por origen: sin él, el endpoint es un vertedero abierto | **Crítica** |
| `RN-MV-015` | El documento crudo caduca; la fila no | Siempre | El `payload` se purga a los **180 días** y la fila **permanece** con sus metadatos —emisor, tipo, identificador, firma, estado y qué movimiento produjo—. Así la trazabilidad sobrevive y los **datos personales de terceros** no: un documento de pasarela lleva nombre, correo, documento y últimos dígitos de una tarjeta. El plazo cubre la ventana de contracargo con margen; pasada esa, el documento crudo ya no es evidencia que nadie necesite. **Esta tabla no espera a D-10** justamente porque, al revés que `request_log`, tiene un final natural | Alta |
| `RN-MV-016` | El secreto compartido **no se guarda jamás** | Siempre | Se conserva la **firma** que el emisor envía —que es un resumen y no una llave— y **nunca** la clave con la que se calcula, ni ninguna cabecera de autorización. Es el Art. VI.5 y el mismo criterio con el que `request_log` se niega a guardar cabeceras | **Crítica** |
| `RN-MV-029` | La comisión **nace pendiente** y se causa el primero de cada mes | Al confirmar una venta, y en el cierre mensual | Toda venta —**de upgrade o de bot**— crea sus comisiones en estado **`PENDIENTE`**. El **primero de cada mes a las 00:00 de `America/Bogota`** ([`architecture.md` §15.1.1](../architecture.md)) un trabajo programado **causa** las del periodo cerrado, y causar es exactamente **confirmar**: no hace falta un estado nuevo, porque `RN-MV-004` ya dice que solo lo confirmado produce efectos. **Lo que esta espera compra** es que una venta anulada antes del cierre **no deje nunca una comisión pagada que haya que recuperar** | **Crítica** |
| `RN-MV-030` | El depósito habilita **si alcanza el precio** del producto gratuito | Al confirmar un depósito | La cuenta que entró por la membresía gratuita sale de `FTD_PENDIENTE` cuando deposita **al menos el precio del producto asociado a esa membresía** — el que `RN-PM-006` obliga a que sea mayor que cero, y que es justamente el importe del depósito. **Al menos y no exactamente**: quien deposita de más no tiene por qué quedarse fuera, y exigir el importe exacto convertiría una comisión bancaria en un bloqueo | **Crítica** |
| `RN-MV-024` | Una compra lleva **al menos una línea**; el **FTD**, exactamente una | Al registrar | Sustituye a la comprobación de columna que hacía `RN-MV-006`: lo que se compró vive en `movement_details` (§7.2.2) y no en la cabecera. **El depósito inicial no es una excepción a esto**: lleva **una línea con el producto de la membresía gratuita**, que es el mismo que fija su importe (`RN-MV-030`). Los depósitos posteriores no llevan ninguna. **Ya no se puede declarar en el esquema**, porque un `CHECK` no cuenta filas de otra tabla | **Crítica** |
| `RN-MV-025` | **Como mucho un upgrade** por movimiento | Al registrar una compra | Bots, los que se quiera; **upgrades, uno**. Dos en la misma compra son **dos cambios de nivel en una sola operación**, y no hay forma no arbitraria de decidir en cuál queda la persona ni de justificar cobrarle los dos. Es un agujero que **no existía** mientras un movimiento admitía un solo producto | **Crítica** |
| `RN-MV-026` | Todas las líneas comparten la **moneda** de la cabecera | Al registrar | La moneda es del movimiento y no de la línea: un cobro se hace en **una** moneda. Un producto en otra distinta no se rechaza por gusto — es que **no hay un total que calcular** sin una tasa de cambio, y este sistema no tiene ninguna | Alta |
| `RN-MV-027` | El total es la **suma de las líneas**, y se congela | Al registrar | `total_amount` se calcula **una vez** y no se recalcula al leer. No cabe en un `CHECK` —cruza dos tablas— y es justo el número que aparece en el comprobante: recalcularlo haría que un cambio en el redondeo reescribiera documentos ya entregados | Alta |
| `RN-MV-028` | El mismo producto **no se repite** en un movimiento | Al registrar | Dos líneas del mismo producto son una con el doble de cantidad. Admitirlas obligaría a sumar para responder «¿cuántos compró?», y la respuesta dependería de que nadie olvidara hacerlo. Se declara en el esquema | Media |
| `RN-MV-019` | Una venta devenga para **toda la cadena**, cada uno su porcentaje | Al confirmar una compra o un depósito | **Override**, por decisión del responsable del proyecto: no comisiona solo quien vendió. Se recorre `user_supervisors` **hacia arriba** desde el vendedor congelado, y **cada persona de la cadena devenga su propio porcentaje sobre el mismo importe** — el agente su 10 %, su director su 4 %, su manager el suyo. Un movimiento produce **tantos devengos como niveles tenga la cadena**, y quien no tenga tarifa declarada no devenga nada: eso es «sin tarifa», que **no es cero** (`RF-CM-005` `FA-001`) | **Crítica** |
| `RN-MV-020` | La cadena se recorre **como estaba entonces**, no como está hoy | Al devengar | El recorrido usa el estado de `user_supervisors` **en la fecha del movimiento**, no el vigente. Reorganizar la fuerza comercial en marzo **no puede cambiar quién ganó por una venta de enero**. Es lo que `RN-SP-021` conserva el historial para poder responder —«determina a quién se atribuía cada resultado en cada momento»— y esta es la primera regla que lo usa | **Crítica** |
| `RN-MV-021` | Una comisión **no es caja** | Siempre | Un movimiento de tipo `COMISION` es **deuda contraída**, no dinero movido: lo único que mueve dinero es el pago. Se distingue por `movement_types.affects_cash`, de modo que **ninguna suma de caja lo incluye** y ninguna suma de deuda incluye a los otros. Sin esa separación, sumar el libro no responde ninguna pregunta | **Crítica** |
| `RN-MV-022` | La suma de la cadena **no puede superar el importe** | Al devengar | `RN-CM-007` acota **cada** porcentaje a cien, y con override **la suma de la cadena no está acotada por nada**: `AGENTE 60 %` más `DIRECTOR 30 %` más `MANAGER 20 %` paga el 110 % de la venta, y ninguna regla de hoy lo impide. La operación **se rechaza** y no se recorta: recortar decidiría en silencio a quién se le quita, y esa decisión no la toma un algoritmo. **No se puede declarar en el esquema**: depende de tantas filas como niveles tenga la cadena | **Crítica** |
| `RN-MV-023` | El pago liquida devengos concretos, y queda dicho cuáles | Al pagar una comisión | Un `COMISION_PAGADA` **apunta a los devengos que salda** (`settled_by_movement_id`), y un devengo saldado no se vuelve a pagar. Sin ese vínculo, «¿por qué me pagaron esto?» solo se puede responder sumando a mano y esperando que cuadre | Alta |
| `RN-MV-017` | Todo movimiento lleva un **código legible**, y su día sale del hecho | Al registrar | `<prefijo del tipo>-<AAAAMMDD>-<seis aleatorios>` (§7.2.1). El día se corta en la **zona de la operación —`America/Bogota`—** y no en UTC ([`architecture.md` §15.1.1](../architecture.md)): con UTC, un movimiento de las 23:30 en Bogotá llevaría el día siguiente. La fecha es la de **`occurred_at`**, no la del registro: un movimiento importado de la plataforma anterior lleva el día en que ocurrió. El aleatorio usa el alfabeto de 32 de Crockford —**sin `I`, `L`, `O` ni `U`**— porque este código se dicta por teléfono y se teclea, y `O` contra `0` es el error que se comete. **No sustituye al identificador interno**: `id` sigue siendo el UUID | Alta |
| `RN-MV-018` | El catálogo de tipos **no se edita por API** | Siempre | Se siembra por migración, como el de monedas (`RN-SP-010`). El motivo aquí es más fuerte: el caso de uso **decide según el tipo**, de modo que uno añadido en caliente sería un tipo que **ningún código sabe procesar** — el sistema aceptaría el movimiento y no haría nada con él. Es el defecto que no falla: promete | **Crítica** |
| `RN-MV-011` | Habilita la **transición**, no el depósito | Al confirmar un depósito | Un depósito solo habilita la cuenta si estaba en `FTD_PENDIENTE`. **El segundo depósito de la misma persona no toca su estado**, y decirlo importa: la implementación evidente —«todo depósito confirmado habilita»— es correcta por accidente y deja de serlo el día que exista una cuenta desactivada por otro motivo, a la que un depósito la reactivaría sin que nadie lo hubiera decidido | **Crítica** |
| `RN-MV-010` | Método de pago y pasarela son dos datos | Al registrar | **El método** es con qué pagó la persona —transferencia, tarjeta, cripto—; **la pasarela** es quién procesó la transacción. Una pasarela ofrece varios métodos y un método lo ofrecen varias pasarelas: en un solo campo, cambiar de proveedor obligaría a reescribir datos históricos | Media |

### 5.2 Por qué las críticas son críticas

**`RN-MV-001` — el movimiento no se toca.** Es lo que separa un libro de una tabla. Un importe que se puede editar convierte cualquier pregunta sobre el pasado en una conjetura, y el daño no se descubre al editar: se descubre meses después, cuando dos personas miran el mismo número y no coinciden. Es la misma postura de `RN-PM-010` y `RN-CM-005`, llevada un paso más allá — allí la fila permanece aunque se retire; aquí **ni siquiera se retira**.

**`RN-MV-002` y `RN-MV-003` — congelar, no referenciar.** Son la misma regla aplicada a dos cosas distintas, y las dos las **impusieron otros documentos antes de que este módulo existiera**: `pm.md` §1.4 para el precio y la vigencia, y la estructura comercial para el vendedor. El defecto que evitan no falla en el momento: aparece cuando alguien corrige un precio, o reasigna un cliente, y descubre que acaba de cambiar el pasado.

**`RN-MV-004` — solo lo confirmado surte efecto.** Sin ella, iniciar un cobro concede lo que se está comprando. Es el agujero por el que se regalan membresías, y no hace falta mala fe: basta con que la pasarela rechace la tarjeta después.

**`RN-MV-005` — idempotencia.** Toda pasarela reintenta cuando no recibe respuesta a tiempo, de modo que **la doble entrega no es un caso raro: es el caso normal**. Sin unicidad en el esquema, la segunda entrega duplica el depósito, y un depósito duplicado es dinero que el sistema cree tener. Se declara en la base y no en el código por lo mismo que `RN-SP-018` tuvo que corregirse el 26-08-2026: dos peticiones simultáneas burlan cualquier comprobación previa.

### 5.3 Lo que este módulo NO decide, y por qué

**Quién ve los movimientos de quién.** Un director que consulta movimientos ¿ve los de su equipo, los de todos, o solo los suyos? Es **alcance de datos**, depende de **D-22** —abierta, issue #28— y es el mismo aplazamiento que `requirements/cm.md` §5.3 ya hizo. Los requerimientos se especifican con **alcance global explícito** para quien tenga el permiso, y `RF-MV-006` cubre el caso propio sin depender de esa decisión.

~~**Cómo se escribe en `SP`.**~~ **Cerrada el 01-09-2026**: `SP` publica la **operación concreta** —«habilitar cuenta por depósito», «aplicar upgrade comprado»— y `MV` la invoca, **síncrona y en la misma transacción**. Es la misma norma que para leer, y la desarrolla [`architecture.md` §15.2.1](../architecture.md).

**Qué operaciones deshacen lo ya aplicado.** `RN-MV-031` cierra la puerta de la anulación —lo aplicado no se anula— y con ello **deja un hueco a la vista**: retirar un nivel concedido o revertir una comisión ya causada son **operaciones que no existen** y que hay que decidir una a una. No es una omisión: es lo que el responsable eligió al descartar deshacerlo todo automáticamente, y el precio queda escrito — **hoy un movimiento aplicado por error no tiene corrección por ninguna vía**.

**Si los depósitos posteriores al primero comisionan.** El **FTD** sí, porque lleva su línea con el producto de la membresía gratuita y `RF-CM-005` lo resuelve como a cualquier otro. Un **segundo depósito no lleva línea**, y nadie ha decidido si genera comisión ni contra qué tarifa. Es menor y no bloquea la etapa 1.

**Qué pasarelas se integran.** Este documento declara que hay una y que deja su referencia; con cuál se firma y cómo se autentica su notificación es un requerimiento de integración propio.

---

## 6. Permisos

| Código | Recurso | Acción | Para qué |
|---|---|---|---|
| `movements:read` | `movements` | `read` | Consultar movimientos y su detalle |
| `movements:create` | `movements` | `create` | Registrar un depósito o una compra |
| `movements:confirm` | `movements` | `confirm` | Dar por bueno un movimiento pendiente |
| `movements:void` | `movements` | `void` | Anular emitiendo el inverso |
| `movements:reprocess` | `movements` | `reprocess` | Volver a interpretar una notificación ya recibida |

**`confirm` y `void` no reutilizan `update`**, y esa es la única decisión de esta sección. Un movimiento **no se actualiza nunca** (`RN-MV-001`), de modo que un permiso llamado `movements:update` prometería algo que no existe. Y son dos permisos y no uno porque confirmar es operación de caja diaria mientras que anular deshace dinero: quien concilia pagos no tiene por qué poder revertirlos.

---

## 7. Modelo de datos

### 7.1 `movements`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `type_id` | `uuid` | No | `movement_types` — de él sale el prefijo del código |
| `code` | `varchar(30)` | No | `DEP-20260901-A7K2P9` (§7.2.1) |
| `status` | `varchar(20)` | No | `PENDIENTE`, `CONFIRMADO`, `ANULADO` |
| `person_id` | `uuid` | No | `users` — **de quién es el movimiento** |
| `seller_id` | `uuid` | **Sí** | `users` — congelado |
| `source_movement_id` | `uuid` | **Sí** | `movements` — la venta que devengó esta comisión |
| `source_detail_id` | `uuid` | **Sí** | `movement_details` — **la línea** que la devengó |
| `percentage` | `numeric(5,2)` | **Sí** | El que aplicó, **copiado** de la tarifa |
| `settled_by_movement_id` | `uuid` | **Sí** | `movements` — el pago que liquidó este devengo |
| ~~`product_id`~~ | — | — | **Se mudó a `movement_details`** en la v0.6.0 |
| ~~`quantity`~~ | — | — | **Se mudó a `movement_details`** |
| ~~`unit_amount`~~ | — | — | **Se mudó a `movement_details`** |
| `total_amount` | `numeric(14,4)` | No | **Suma de las líneas**, congelada |
| `currency_id` | `uuid` | No | `currencies` |
| ~~`validity_days`~~ | — | — | **Se mudó a `movement_details`** |
| ~~`target_membership_id`~~ | — | — | **Retirada**: es inmutable en el producto, se lee de él (§7.2.2) |
| `payment_method_id` | `uuid` | **Sí** | `payment_methods` |
| `gateway` | `varchar(50)` | **Sí** | Quién procesó |
| `external_reference` | `varchar(200)` | **Sí** | La referencia de la pasarela |
| `receipt_number` | `bigint` | **Sí** | Comprobante, **único por tipo**; se puebla al confirmar |
| `reverses_movement_id` | `uuid` | **Sí** | `movements` — solo en una `REVERSION` |
| `occurred_at` | `timestamptz` | No | Cuándo ocurrió el hecho |
| `confirmed_at` | `timestamptz` | **Sí** | — |
| `created_at` | `timestamptz` | No | — |

!!! important "`person_id` y no `client_id`, y el cambio de nombre lleva significado"

    Hasta la v0.4.0 esta columna se llamaba `client_id`, porque los dos únicos tipos eran una compra y un depósito, y los dos son de un cliente.

    Con los devengos y los pagos de comisión **la mitad de los movimientos no son de un cliente**: son de un vendedor. Llamarla `client_id` obligaría a leer «cliente» como «la persona de la fila», que es exactamente el tipo de nombre que se cree y se usa mal.

    **Es de quién es el movimiento**: el cliente en una compra o un depósito, el **beneficiario** en un devengo o un pago de comisión. `seller_id` sigue siendo la atribución congelada y solo tiene sentido en los dos primeros.

**Sin `updated_at` y sin `deleted_at`**, y las dos ausencias son la implementación de `RN-MV-001`. Una columna de última modificación en una tabla que no se modifica es una invitación escrita; una de borrado lógico, más todavía.

**`seller_id` es nulable** porque no todo movimiento tiene vendedor: el de un cliente sí, el pago de una comisión a un vendedor no tiene un tercero detrás.

**`total_amount` se guarda además de `unit_amount` y `quantity`**, aunque sea su producto. No es redundancia perezosa: es el importe **por el que se emitió el comprobante**, y calcularlo al leer haría que un cambio futuro en cómo se redondea reescribiera comprobantes ya entregados.

**`occurred_at` no es `created_at`.** Un depósito ocurre cuando el bróker lo recibe y se registra cuando su notificación llega, que puede ser horas después. Confundirlos desplaza los hechos al momento en que el sistema se enteró.

### 7.2 `movement_types`

El tipo de movimiento **es un catálogo y no un dominio cerrado**, y de él sale el prefijo con el que se nombran los movimientos.

| Columna | Tipo | Nula |
|---|---|---|
| `id` | `uuid` | No |
| `code` | `varchar(30)` | No |
| `prefix` | `char(3)` | No |
| `name` | `varchar(100)` | No |
| `requires_product` | `boolean` | No |
| `affects_cash` | `boolean` | No |
| `last_receipt_number` | `bigint` | No |
| `status` | `varchar(20)` | No |
| `created_at` | `timestamptz` | No |

**Por qué tabla y no `varchar` con `CHECK`, que era el diseño anterior.** Porque **este proyecto ya pagó dos veces por esa forma**: el catálogo de `event_type` de `audit_security_log` es un `CHECK`, y añadirle **un** valor costó `V34` —`RATE_LIMIT_EXCEEDED`— y otro `V36` —la purga de tokens—, las dos con `DROP CONSTRAINT` y `ADD CONSTRAINT` sobre una tabla en uso. `V36` lo dice en su primera línea: «POR QUÉ HACE FALTA UNA MIGRACIÓN PARA ESTO».

Un tipo nuevo aquí es previsible —una comisión pagada, un retiro, un ajuste— y con tabla es una fila sembrada.

**Y sobre todo: el tipo lleva datos.** Un dominio cerrado es una etiqueta; esto trae el **prefijo** del código, si **exige producto**, y el **contador** de su serie de comprobantes. Eso ya no es una etiqueta.

!!! danger "`affects_cash` es lo que impide que el libro deje de significar algo"

    Al meter los **devengos** en `movements` (v0.5.0), la tabla pasa a contener dos cosas que no son iguales: **dinero que se movió** —una compra, un depósito, el pago de una comisión— y **deuda que se contrajo** —lo que un vendedor ganó y todavía no ha cobrado—.

    Sin distinguirlas, **sumar la tabla no responde ninguna pregunta**: ni cuánto entró, ni cuánto se debe. Y el defecto no se ve al escribir la consulta: se ve cuando dos informes no cuadran.

    Con la marca en el catálogo, la distinción es **un dato y no una convención**: quien sume caja filtra por `affects_cash`, y el día que haya un tipo nuevo, quien lo siembre **tiene que decidir de qué lado cae**. Es la misma idea con la que `requires_product` saca de un `if` una condición de negocio.

    Se propuso llevar los devengos a una tabla propia de `CM` y **el responsable del proyecto decidió que fueran movimientos** (01-09-2026). Esta columna es lo que hace esa decisión sostenible.

!!! warning "Es un catálogo cerrado: se siembra por migración y NO se edita por API"

    Es el mismo trato que `RN-SP-010` da a las monedas, y aquí el motivo es más fuerte. El caso de uso **decide según el tipo** —qué valida, qué efecto produce, qué escribe fuera—, de modo que un tipo añadido en caliente sería uno que **ningún código sabe procesar**: el sistema aceptaría el movimiento y no haría nada con él.

    Es el defecto que este proyecto describe una y otra vez: **no falla, promete**.

**Lo que este cambio cuesta, y hay que decirlo.** `ck_movements_type_product` —la condición cruzada de `RN-MV-006`— **vivía en el esquema y ya no puede**: un `CHECK` no consulta otra tabla. Se muda al dominio, con `RN-CM-001` y `RN-MV-007`, que están ahí por lo mismo. Es una pérdida real frente al Art. V.6 y es el precio de poder añadir tipos sin tocar una restricción en uso.

### 7.2.1 Los dos códigos, que no son el mismo

| Código | Forma | Para qué |
|---|---|---|
| **Del movimiento** | `DEP-20260901-A7K2P9` | Identificarlo. Es el que una persona cita en soporte |
| **Del comprobante** | Consecutivo **único por tipo** | Numerar el documento. Es el que un contador espera |

**Son dos porque responden a dos preguntas.** El primero dice *cuál* es; el segundo, *cuántos van*. Fundirlos obligaría al identificador a ser secuencial, y un identificador secuencial **publica el volumen del negocio**: quien recibe `COM-000412` sabe cuántas compras se llevan.

**El código del movimiento no sustituye al identificador interno.** `id` sigue siendo un UUID v7 y sigue siendo la clave: el código es la identidad **hacia fuera**, no hacia dentro.

**Sus tres partes:**

- **`DEP`** — el prefijo del tipo, de `movement_types.prefix`.
- **`20260901`** — el día de `occurred_at`, **no el de `created_at`**. Un movimiento migrado de la plataforma anterior lleva el día en que ocurrió, que es lo que hace que su código signifique algo.
- **`A7K2P9`** — seis caracteres aleatorios, del alfabeto de **32 de Crockford**: sin `I`, `L`, `O` ni `U`. La exclusión no es estética — este código se dicta por teléfono y se teclea, y `O` contra `0` es el error que se comete.

!!! danger "Hay que fijar con qué zona horaria se corta el día, o el código miente"

    Un movimiento ocurrido a las 23:30 en Bogotá es el día siguiente en UTC. **Con el corte en UTC, el código de ese movimiento llevaría una fecha que no coincide con la que ve quien lo hizo**, y nadie entendería por qué.

    El día se corta con la **zona de la operación**, no con la del servidor. Es el mismo problema que `requirements/cm.md` §7.1 resolvió al elegir `date` para la vigencia de una tarifa —«declararla con instante obligaría a decidir en qué zona se corta el día»—, y aquí no se puede esquivar porque la fecha va **dentro** del código.

**La colisión es improbable y no imposible.** Con 32⁶ hay mil millones de combinaciones por tipo y día, de modo que un choque es rarísimo — y «rarísimo» no es «nunca». La unicidad la sostiene el índice y la generación **reintenta** ante la violación; confiar en la probabilidad es lo que produce el defecto que aparece una vez al año y nadie reproduce.

### 7.2.2 `movement_details` — qué se compró, línea a línea

Una compra puede llevar **varios productos**, de modo que el producto, la cantidad y el importe **no son de la cabecera**: son de cada línea.

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `movement_id` | `uuid` | No | `movements` |
| `product_id` | `uuid` | No | `products` |
| `quantity` | `integer` | No | — |
| `unit_amount` | `numeric(14,4)` | No | **Copiado** del producto |
| `line_amount` | `numeric(14,4)` | No | `quantity × unit_amount` |
| `validity_days` | `integer` | **Sí** | **Copiada**; nula = no caduca |


**Se llama `movement_details` en plural** por lo mismo que `role_permissions` y `user_memberships`: la tabla contiene la colección, no un elemento.

**`line_amount` se guarda además de sus dos factores**, por el mismo motivo que `total_amount` en la cabecera: es el importe **por el que se cobró esa línea**, y calcularlo al leer haría que un cambio futuro en cómo se redondea reescribiera comprobantes ya entregados.

**Y lo que se copia se copia aquí**, no en la cabecera: `RN-MV-002` se cumple **por línea**. Cada una guarda el precio y la vigencia **del momento en que se compró ese producto**.

!!! important "La membresía destino NO se copia, y la diferencia es el criterio"

    Se propuso copiarla junto al precio y la vigencia, y **es redundante**: `RF-PM-004` `EX-004` **rechaza cambiar el destino de un producto** —junto al tipo y al código—, de modo que leerlo del producto dentro de tres años da **exactamente el mismo valor**. Y el producto no desaparece nunca (`RN-PM-010`), así que siempre habrá de dónde leerlo.

    De ahí sale el criterio, que es más afilado que «copiarlo todo»: **se copia lo que puede cambiar; lo inmutable se referencia.** El precio y la vigencia **sí** se corrigen (`RF-PM-004`), y por eso copiarlos evita que corregirlos reescriba lo ya vendido. El destino no se corrige nunca, y copiarlo solo añadiría un sitio más donde el dato pudiera discrepar de sí mismo.

!!! danger "Poder comprar varios productos abre un agujero que antes no existía"

    Con un solo producto por movimiento, la pregunta no se podía hacer. Ahora sí: **¿se pueden comprar dos upgrades en la misma compra?**

    `UPGRADE_PLATINO` y `UPGRADE_ORO` juntos son **dos cambios de nivel en una sola operación**, y no hay forma no arbitraria de decidir en cuál queda la persona — ni de justificar cobrarle los dos. `RN-MV-025` lo rechaza: **como mucho un upgrade por movimiento**, tantos bots como se quiera.

    No se puede declarar en el esquema —un `CHECK` no cuenta filas de otra tabla— y vive en el caso de uso, como `RN-MV-007` y `RN-CM-001`.

### 7.3 `payment_methods`

| Columna | Tipo | Nula |
|---|---|---|
| `id` | `uuid` | No |
| `code` | `varchar(50)` | No |
| `name` | `varchar(100)` | No |
| `status` | `varchar(20)` | No |
| `created_at` | `timestamptz` | No |

Mismo formato de código que `roles`, `memberships` y `products`: `^[A-Z][A-Z0-9_]*$`.

**Un método desactivado no invalida los movimientos que lo usaron.** Es el mismo criterio de `RN-PM-008` con las monedas: la validación es del momento del registro, no permanente.

### 7.3 `inbound_notifications`

Lo que un sistema externo dijo, **tal como lo dijo**.

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `source` | `varchar(50)` | No | Quién la envió: la pasarela, el bróker |
| `event_type` | `varchar(100)` | **Sí** | El tipo que declara el emisor, **sin traducir** |
| `external_event_id` | `varchar(200)` | **Sí** | El identificador que el emisor le da al evento |
| `payload` | `jsonb` | **Sí** | El documento **verbatim**. Nulo solo después de la purga |
| `signature_valid` | `boolean` | No | — |
| `status` | `varchar(20)` | No | `RECIBIDA`, `PROCESADA`, `DESCARTADA`, `FALLIDA` |
| `failure_reason` | `text` | **Sí** | Por qué no se pudo interpretar |
| `movement_id` | `uuid` | **Sí** | `movements` — qué produjo, si produjo algo |
| `correlation_id` | `uuid` | No | Cruza con `request_log` |
| `received_at` | `timestamptz` | No | — |
| `processed_at` | `timestamptz` | **Sí** | — |
| `payload_purged_at` | `timestamptz` | **Sí** | `RN-MV-015` |

!!! danger "Esta tabla guarda justo lo que `request_log` se niega a guardar, y hay que decir por qué"

    `V35__create_request_log.sql` lo dejó escrito como decisión, no como omisión: **«el CUERPO de la petición. Ahí viajan contraseñas […] ningún saneador es de fiar sobre un cuerpo arbitrario: la única forma segura de no registrar un secreto es no registrar el cuerpo»**.

    **Aquí sí se guarda, y la diferencia es «arbitrario».** `request_log` cubre **todos** los endpoints, incluido el inicio de sesión: el cuerpo puede llevar una credencial **nuestra**, y no hay forma de saberlo mirándolo. Esta tabla cubre **un interlocutor conocido con esquema conocido** que nunca recibe credenciales del sistema, de modo que el cuerpo no puede contenerlas.

    **Lo que sí contiene son datos personales de terceros** —nombre, correo, documento, últimos dígitos de una tarjeta—, y eso es exactamente el «riesgo legal» que `ADR-003` nombró: conservarlos indefinidamente es una decisión que nadie ha tomado. Aquí **se toma**: `RN-MV-015` los purga a los 180 días y conserva la fila. Al revés que `request_log`, esta tabla tiene un final natural.

    **Y la excepción no alcanza a las cabeceras.** Se guarda la **firma** —un resumen, no una llave— y jamás el secreto con el que se calcula (`RN-MV-016`).

**`event_type` va sin traducir**, con el valor que el emisor use. Traducirlo al vocabulario del sistema aquí haría que un tipo desconocido —una pasarela que añade uno— **rompiera la escritura**, que es el único paso que no puede fallar.

**`payload` es nulable, y solo por la purga.** Una fila con `payload` nulo y `payload_purged_at` poblado es una notificación cuyo documento caducó; una con los dos nulos sería un defecto, y por eso el esquema lo impide.

**`movement_id` es nulable porque muchas notificaciones no producen ninguno**: la reentregada, la que avisa de un estado que no interesa, y la que no valida la firma. Exigir un movimiento por notificación obligaría a inventarse uno.

### 7.4 Restricciones exigidas en el esquema

| Restricción | Sobre | Regla |
|---|---|---|
| `fk_movements_type` | `type_id` → `movement_types(id)` | §7.2. **Sustituye al `CHECK` de dominio cerrado** que este documento declaraba hasta la v0.4.0 |
| `uq_movements_code` | Único sobre `code` | §7.2.1. Es lo que sostiene la unicidad del aleatorio; la generación reintenta ante la violación |
| `ck_movements_code_format` | `code ~ '^[A-Z]{3}-[0-9]{8}-[0-9A-HJKMNP-TV-Z]{6}$'` | §7.2.1. El alfabeto excluye `I`, `L`, `O` y `U` |
| `uq_movements_receipt_por_tipo` | Único sobre `(type_id, receipt_number)`, **parcial**: `WHERE receipt_number IS NOT NULL` | `RN-MV-008`. **Único por tipo**, no global |
| `ck_movements_status` | `status IN ('PENDIENTE','CONFIRMADO','ANULADO')` | `RN-MV-004` |
| `ck_movements_quantity` | `quantity >= 1` | `RN-MV-007`, en lo que el esquema puede sostener |
| `ck_movements_amounts` | `unit_amount > 0 AND total_amount > 0` | `RN-MV-009` |
| ~~`ck_movements_type_product`~~ | **Retirada en la v0.6.0**: lo que se compró se mudó a `movement_details`, y un `CHECK` no cuenta filas de otra tabla. `RN-MV-024` vive en el caso de uso |
| `fk_movement_details_movement` | `movement_id` → `movements(id)`, con borrado en cascada — una línea sin cabecera no significa nada |
| `fk_movement_details_product` | `product_id` → `products(id)` |
| `ck_movement_details_quantity` | `quantity >= 1` | `RN-MV-007`, en lo que el esquema puede sostener |
| `ck_movement_details_amounts` | `unit_amount > 0 AND line_amount > 0` | `RN-MV-009` |
| `uq_movement_details_producto` | Único sobre `(movement_id, product_id)` | `RN-MV-028`. Dos líneas del mismo producto son una con el doble de cantidad |
| `ck_movements_reversion` | `(type = 'REVERSION') = (reverses_movement_id IS NOT NULL)` | Solo una reversión apunta a otro movimiento, y toda reversión apunta a uno |
| `uq_movements_external_reference` | Único sobre `(gateway, external_reference)`, **parcial**: `WHERE external_reference IS NOT NULL` | `RN-MV-005`. Parcial porque un movimiento registrado a mano no tiene referencia, y en PostgreSQL dos nulos no compiten |
| `uq_movements_receipt` | Único sobre `receipt_number`, **parcial**: `WHERE receipt_number IS NOT NULL` | `RN-MV-008` |
| `fk_movements_*` | `person_id`, `seller_id`, `currency_id`, `payment_method_id`, `type_id`, `reverses_movement_id`, `source_movement_id`, `source_detail_id`, `settled_by_movement_id` | Integridad |
| `ck_inbound_status` | `status IN ('RECIBIDA','PROCESADA','DESCARTADA','FALLIDA')` | §7.3 |
| `uq_inbound_event` | Único sobre `(source, external_event_id)`, **parcial**: `WHERE external_event_id IS NOT NULL` | `RN-MV-013`, primera capa. Parcial porque un emisor puede no dar identificador, y en PostgreSQL dos nulos no compiten |
| `ck_inbound_payload` | `payload IS NOT NULL OR payload_purged_at IS NOT NULL` | `RN-MV-015`. Un documento ausente **solo** se admite si consta que se purgó; los dos nulos serían un defecto. Las dos ramas son predicados `IS NULL`/`IS NOT NULL` y **nunca evalúan a `NULL`** |
| `fk_inbound_movement` | `movement_id` → `movements(id)` | Integridad |
| `ix_inbound_recepcion` | `(received_at DESC, id DESC)` | La consulta por defecto: «las últimas notificaciones» |
| `ix_inbound_pendientes` | **Índice parcial**: `(source, received_at) WHERE status IN ('RECIBIDA','FALLIDA')` | Lo que hay que reprocesar. Parcial porque lo ya procesado no forma parte de esa respuesta y crecería sin límite dentro del índice |

**Lo que NO se puede declarar en el esquema, y por eso vive en el dominio:** que la cantidad sea uno **cuando el producto es un upgrade** (`RN-MV-007`) —un `CHECK` no consulta `products`—, que el importe respete los decimales de **su** moneda (`RN-MV-009`), que solo lo confirmado surta efecto (`RN-MV-004`) y **las cuatro que trajo separar las líneas**: la cardinalidad por tipo (`RN-MV-024`), el upgrade único (`RN-MV-025`), la moneda compartida (`RN-MV-026`) y el total como suma (`RN-MV-027`). **Las cuatro cruzan dos tablas, y ningún `CHECK` lo hace.** Es el precio de partir la cabecera de las líneas, y conviene tenerlo escrito: el esquema defiende menos que antes, y el caso de uso, más.

---

## 8. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 01-09-2026 | **Creación del módulo `MV`**, el cuarto del sistema, por decisión del responsable del proyecto, con **ocho requerimientos** y **diez reglas propias**. Es dueño de `movements`, el **libro de hechos económicos**: compras, depósitos y sus reversiones, con un campo de tipo que los distingue sin separarlos. Con él **se cierran tres aplazamientos que llevaban semanas abiertos y que se citaban entre sí**: `requirements/pm.md` §1.4 no registraba la compra «porque no existe el cobro», `requirements/cm.md` §1.4 no calculaba «porque no hay tabla de ventas», y `RF-SP-045` dejaba a los clientes nuevos en `FTD_PENDIENTE` **sin nada capaz de sacarlos**. El alcance elegido es **todo hecho económico** y no solo la venta, de modo que el módulo **absorbe el candidato «Finanzas»** de [`modules.md` §6](../modules.md#6-alcance-por-inventariar). Las dos reglas que lo definen ya venían impuestas desde fuera: **`RN-MV-002`** —el precio, la moneda y la vigencia se **copian** del producto— la exigió `pm.md` §1.4 antes de que este módulo existiera, y **`RN-MV-003`** —el vendedor se congela— es la misma idea aplicada a la estructura comercial, para que reasignar un cliente no cambie a quién se le pagó por una venta pasada. **`RN-MV-001` va un paso más allá que sus hermanas de `PM` y `CM`**: allí la fila permanece aunque se retire; aquí **no se retira ni se edita**, y corregir es emitir el inverso. Se decide además que el **comprobante no es una factura fiscal** (§1.5) y **cómo llegará la que sí lo sea** —una entidad aparte que apunta al movimiento—, para que llegue como ampliación y no como reescritura. **Y el módulo abre D-26**, que este documento no cierra: un depósito confirmado tiene que **escribir** en `users`, y las cuatro interfaces publicadas hasta hoy son **de solo lectura** por norma explícita de `architecture.md` §15.2. Se recomienda la salida que el propio `pm.md` §1.4 ya anticipó —que `SP` publique la operación con sus reglas intactas— y se deja abierta porque fija cómo se escribirá entre módulos para siempre. Las etapas 2 y 3 —liquidación, y retiros con balances— quedan **declaradas y sin registrar** en §4.2. | Responsable del proyecto |
| 0.2.0 | 01-09-2026 | **Consecuencias de dibujar los flujos antes de las tripletas**, por decisión del responsable del proyecto. Los diagramas de [`flujos/mv/`](../flujos/mv/flujos-del-modulo.md) destaparon tres cosas que este documento no decía, y dos tienen respuesta. **(1) El depósito y la compra no comparten máquina de estados**: `RF-MV-003` se describía como si todo movimiento naciera `PENDIENTE`, y no es así — un **depósito nace `CONFIRMADO`** porque es un hecho que ya ocurrió y del que un tercero avisa, de modo que confirmarlo después sería preguntarle al sistema si cree lo que el bróker acaba de decirle; una **compra nace `PENDIENTE`** porque el cobro sigue en marcha. `RF-MV-003` pasa a ser «confirmar una **compra** pendiente». **(2) Nace `RN-MV-011`: habilita la transición, no el depósito.** Un depósito solo saca de `FTD_PENDIENTE` a quien estaba ahí; el segundo depósito de la misma persona no toca su estado. Sin la regla, la implementación evidente —«todo depósito confirmado habilita»— es correcta por accidente y deja de serlo el día que exista una cuenta desactivada por otro motivo, a la que un depósito reactivaría sin que nadie lo hubiera decidido. **(3) Queda abierta, en §5.3, qué se deshace al anular un movimiento ya aplicado**: si se anula la compra de un upgrade que ya cambió el nivel de alguien, nadie ha decidido si se le retira. No bloquea la etapa 1 —anular una compra que la pasarela rechazó no aplicó nada— y es lo primero que hay que cerrar antes de la tripleta de `RF-MV-007`. | Responsable técnico |
| 0.3.0 | 01-09-2026 | **El módulo gana `inbound_notifications`**, por decisión del responsable del proyecto: lo que un sistema externo dice, **tal como lo dice**. Con ella el módulo pasa de ocho a **once requerimientos** —recibir, consultar y **reprocesar**— y de once reglas a **dieciséis**. Sirve para cuatro cosas que hoy no se pueden hacer: **reconciliar** cuando la pasarela y el sistema no coinciden, **detectar la reentrega antes de interpretarla**, **reprocesar** lo que un defecto se comió, y **probar** que la confirmación llegó de fuera y no la inventó el sistema. **Una sola tabla con columna `source`** y no una por proveedor: cubre la pasarela de pago y también al bróker que notifica depósitos —misma forma, mismo problema—, con el mismo criterio con el que `movements` lleva un tipo. **`RN-MV-012` es la regla que la hace útil o inútil**: se guarda **antes** de procesar, porque guardarla después es tenerla en todos los casos menos en el único que importa —aquel en que procesarla falló— y porque las pasarelas reintentan si la respuesta tarda. **`RN-MV-013` da dos capas de idempotencia** y no una: el identificador del evento aquí, la referencia externa en `movements`. **`RN-MV-014`: la firma inválida se guarda marcada y no se procesa**, porque es la evidencia de que alguien intenta falsificar confirmaciones de pago, y eso vale más visible que en un `401` que nadie mira. **Y se afronta de frente la tensión con `request_log`**, que se niega a guardar cuerpos con un argumento explícito: la diferencia es «arbitrario» —aquel cubre todos los endpoints y el cuerpo puede llevar una credencial nuestra; este cubre un interlocutor conocido que nunca recibe credenciales—. Lo que sí lleva son **datos personales de terceros**, y por eso **esta tabla no espera a D-10**: `RN-MV-015` purga el documento a los **180 días** conservando la fila con sus metadatos, porque al revés que `request_log` tiene un final natural — pasada la ventana de contracargo, el documento crudo ya no es evidencia que nadie necesite. `RN-MV-016` prohíbe guardar el secreto compartido: se conserva la firma, que es un resumen, y nunca la llave. | Responsable del proyecto |
| 0.4.0 | 01-09-2026 | **El tipo de movimiento pasa de dominio cerrado a catálogo**, por decisión del responsable del proyecto, y con él llegan **el código del movimiento** y un dato nuevo que cambia el diseño: **hay datos que migrar de otra plataforma**. §7.2 incorpora `movement_types` con su `prefix`, su `requires_product` y el contador de su serie. El motivo no es de gusto: **este proyecto ya pagó dos veces por la forma anterior** — el catálogo de `event_type` de `audit_security_log` es un `CHECK`, y añadirle un valor costó `V34` y otro `V36`, las dos con `DROP CONSTRAINT` sobre una tabla en uso. **Y lo que cuesta queda escrito**: `ck_movements_type_product` vivía en el esquema y ya no puede —un `CHECK` no consulta otra tabla—, de modo que `RN-MV-006` se muda al dominio con `RN-CM-001` y `RN-MV-007`. §7.2.1 declara **dos códigos que no son el mismo y que nadie debe fundir**: el **del movimiento** —`DEP-20260901-A7K2P9`, `RN-MV-017`— identifica, y el **del comprobante** —único por tipo— numera. Fundirlos obligaría al identificador a ser secuencial, y **un identificador secuencial publica el volumen del negocio**. El aleatorio usa el alfabeto de 32 de Crockford, sin `I`, `L`, `O` ni `U`, porque el código se dicta por teléfono; la colisión la atrapa el índice y la generación reintenta. **Y queda declarado un problema que la fecha dentro del código no deja esquivar**: hay que fijar con qué zona horaria se corta el día, o el código de un movimiento hecho a las 23:30 en Bogotá llevará el día siguiente — es el mismo problema que `requirements/cm.md` §7.1 resolvió eligiendo `date` para la vigencia. **`RN-MV-008` retira la promesa de «sin huecos»**: una `SEQUENCE` deja huecos por diseño en cada transacción revertida, y sobre todo **lo importado trae sus propias fechas y su propio orden**, de modo que sostener una serie correlativa obligaría a renumerar el pasado para que encajara. `RN-MV-018` cierra el catálogo de tipos a la API, con el mismo criterio que `RN-SP-010` aplica a las monedas y un motivo más fuerte: el caso de uso decide según el tipo, así que uno añadido en caliente sería uno que ningún código sabe procesar. **§1.6 recoge la migración**, que resuelve con lo que ya había —la plataforma anterior es un `source` más y el identificador de allí es la `external_reference`, con lo que reejecutar la carga no duplica nada— y deja fuera el mapeo, que es un requerimiento propio y de una sola ejecución. | Responsable del proyecto |
| 0.5.0 | 01-09-2026 | **Se decide cómo se comisiona, y las dos decisiones son del responsable del proyecto.** (1) **Override**: una venta devenga para **toda la cadena** y no solo para quien vendió — el agente su porcentaje, su director el suyo, su manager el suyo, todos sobre el mismo importe (`RN-MV-019`). `RF-CM-005` **no cambia**: se le llama una vez por nivel, que es exactamente lo que ya sabe responder. Y el recorrido de `user_supervisors` es **histórico y no vigente** (`RN-MV-020`): reorganizar la fuerza comercial en marzo no puede cambiar quién ganó por una venta de enero — es la primera regla del sistema que usa el historial que `RN-SP-021` conserva precisamente para eso. (2) **El devengo es un tipo de movimiento** y no una tabla propia de `CM`. Se propuso lo segundo con el argumento de que **una comisión devengada no es dinero que se movió, es deuda**, y que meterla en el libro haría que sumarlo dejara de significar algo; el responsable eligió el movimiento, y **la objeción se convierte en columna**: `movement_types` gana **`affects_cash`** (`RN-MV-021`), de modo que la distinción entre caja y deuda queda **en datos y no en convención**, y quien siembre un tipo nuevo tiene que decidir de qué lado cae. **Y dibujar el override destapó un agujero que ninguna regla cubría**: `RN-CM-007` acota **cada** porcentaje a cien, pero **la suma de la cadena no está acotada por nada** — `60 + 30 + 20` paga el 110 % de la venta. `RN-MV-022` la rechaza, y **no la recorta**: recortar decidiría en silencio a quién se le quita. `RN-MV-023` ata cada pago a los devengos que salda. La tabla incorpora `source_movement_id`, `percentage` y `settled_by_movement_id`, y **`client_id` pasa a llamarse `person_id`**: con la mitad de los movimientos perteneciendo a un vendedor y no a un cliente, el nombre viejo era de los que se creen y se usan mal. | Responsable del proyecto |
| 0.6.0 | 01-09-2026 | **Una compra puede llevar varios productos**, por decisión del responsable del proyecto. §7.2.2 incorpora **`movement_details`** y el producto, la cantidad, el importe unitario, la vigencia y la membresía destino **se mudan de la cabecera a la línea**: `RN-MV-002` pasa a cumplirse **por línea**, porque cada producto se compró a su precio y con su vigencia. La cabecera conserva la moneda, el total y el comprobante. **Y el cambio abre un agujero que no existía mientras un movimiento admitía un solo producto**: `RN-MV-025` prohíbe **más de un upgrade por movimiento**, porque dos son dos cambios de nivel en una sola operación y no hay forma no arbitraria de decidir en cuál queda la persona ni de justificar cobrarle los dos — bots, los que se quiera. Tres reglas más que la partición hace necesarias: `RN-MV-024` —una compra al menos una línea, un depósito ninguna, que sustituye a la comprobación de columna de `RN-MV-006`—, `RN-MV-026` —**todas las líneas comparten la moneda**, porque sin tasa de cambio no hay total que calcular— y `RN-MV-027` —el total es la suma **congelada**, no un cálculo de lectura—. **Lo que esto cuesta queda escrito**: las cuatro cruzan dos tablas y **ningún `CHECK` lo hace**, de modo que `ck_movements_type_product` se retira y el esquema pasa a defender menos que antes mientras el caso de uso defiende más. **Y destapa un hueco de comisiones que nadie había visto**: `RF-CM-005` resuelve por persona **y producto** —su firma exige los dos— y **un depósito no tiene producto**, de modo que hoy no hay forma de resolver cuánto se gana por un FTD, que es precisamente el hecho comisionable del camino gratuito. Declarado en §5.3 con sus tres salidas; no bloquea la etapa 1 y bloquea la 2. | Responsable del proyecto |
| 0.7.0 | 01-09-2026 | **Se precisa cómo funciona la comisión en el tiempo**, por decisión del responsable del proyecto. **Toda venta —de upgrade o de bot— crea sus comisiones en `PENDIENTE`, y el primero de cada mes a las 00:00 se causan** (`RN-MV-029`). El hallazgo al escribirlo es que **no hace falta ningún estado nuevo**: causar **es** confirmar, y `RN-MV-004` ya dice que lo pendiente no produce efectos, de modo que el trabajo mensual usa `RF-MV-003` en lote y `ck_movements_status` no se toca. El tipo `COMISION_DEVENGADA` pasa a llamarse simplemente **`COMISION`**, porque lo que distingue a la devengada de la causada es su **estado** y no su tipo. **Y esta espera resuelve el caso feo que este documento tenía abierto**: anular una venta dentro del mes anula comisiones **pendientes** —nadie cobró nada y no hay nada que recuperar—, de modo que el saldo negativo deja de ser el camino habitual y pasa a ser la excepción de quien anula en febrero una venta de enero. **Queda anotado lo que la forma elegida no da**: la protección es **muy desigual** —una venta del día 31 se causa horas después y una del día 1 espera un mes— porque es un **cierre de periodo** y no una ventana de seguridad; si lo que se buscaba era lo segundo, la forma sería causar a los *n* días de cada venta. Y el trabajo mensual **tiene que ser recuperable e idempotente**: si no corre el día 1, el 3 debe causar lo pendiente sin causarlo dos veces — misma forma que `ExpiredTokenPurgeJob`, con cron configurable y cerrojo consultivo. **`RN-MV-030`** fija además el importe del FTD: la cuenta sale de `FTD_PENDIENTE` al depositar **al menos el precio del producto de la membresía gratuita** — **al menos y no exactamente**, porque exigir el importe exacto convertiría una comisión bancaria en un bloqueo. | Responsable del proyecto |
| 0.8.0 | 01-09-2026 | **Cuatro decisiones del responsable del proyecto, y con ellas el módulo queda sin nada que le impida escribir tripletas de la etapa 1.** (1) **D-26 cerrada**: `SP` publica la **operación concreta** —«habilitar cuenta por depósito», «aplicar upgrade comprado»— y `MV` la invoca, **síncrona y en la misma transacción**, con las reglas viviendo en el dueño; se descartó el evento de dominio porque la habilitación dejaría de ser inmediata y **una cuenta retenida sin que nada haya fallado visiblemente es la avería que nadie reporta**. (2) **Todo opera en `America/Bogota`**: era la segunda vez que hacía falta esa decisión —el `AAAAMMDD` del código y el cierre mensual— y se toma **una sola vez** para el sistema entero; `RN-MV-017` y `RN-MV-029` la citan. (3) **El FTD sí tiene producto**: lleva **una línea con el producto de la membresía gratuita**, que es el mismo que fija su importe, de modo que `RF-CM-005` lo resuelve como a cualquier otra venta y **su firma no se toca** — la comisión registra ese producto y su importe es el de la comisión, no el de la venta. `RN-MV-024` se enmienda para decirlo. (4) **`RN-MV-031`: lo ya aplicado no se anula.** Anular solo se admite mientras el movimiento no haya producido efectos; deshacer un nivel concedido o una comisión causada es una **operación distinta que no existe todavía**. Se eligió sobre deshacerlo todo —que obligaría a quitarle el nivel a quien lleva un mes usándolo— y sobre revertir solo el dinero —que dejaría el libro y el acceso discrepando—, **y el precio queda escrito**: hasta que esas operaciones existan, un movimiento aplicado por error no tiene corrección por ninguna vía. §5.3 conserva dos abiertas y ninguna bloquea: qué operaciones deshacen lo aplicado, y si los depósitos posteriores al primero comisionan. | Responsable del proyecto |
| 0.9.0 | 01-09-2026 | **Se retira `movement_details.target_membership_id`**, a señalamiento del responsable del proyecto: **es redundante**. `RF-PM-004` `EX-004` **rechaza cambiar la membresía destino de un producto** —junto al tipo y al código—, de modo que leerla del producto dentro de tres años da **exactamente el mismo valor**; y `RN-PM-010` garantiza que el producto no desaparece nunca, así que siempre habrá de dónde leerla. **Y de ahí sale un criterio más afilado que el que este documento venía aplicando**: `RN-MV-002` dejaba entender «cópialo todo», y pasa a decir **se copia lo que puede cambiar; lo inmutable se referencia**. Bajo esa prueba, el **precio** y la **vigencia** siguen copiándose —`RF-PM-004` los corrige, y copiarlos es lo que impide que corregirlos reescriba lo ya vendido—, el **porcentaje** de una comisión también —`RF-CM-003` lo corrige— y el **vendedor** también —una reasignación lo cambia—; el **destino** no, porque nada puede cambiarlo. Una columna menos y una regla que ahora dice **por qué** copia, en lugar de solo que copia. | Responsable del proyecto |
