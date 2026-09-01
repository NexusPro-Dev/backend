# Requerimientos del Módulo — `MV` Movimientos

| Campo | Valor |
|---|---|
| Módulo | `MV` — Movimientos |
| Paquete | `modules/movements` |
| Prefijos de permiso | `movements:` |
| Versión | 0.1.0 |
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
    3. **D-26 — la primera escritura entre módulos.** Un depósito confirmado tiene que **cambiar el estado de una cuenta**, y `users` es de `SP`. Todas las interfaces publicadas hasta hoy son **de solo lectura**; esta no puede serlo. Ver §3.

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
- Registrar una **compra**: qué producto pidió quién, a qué precio, en qué moneda y con qué vigencia — todo **copiado en el momento**, nunca leído después del catálogo.
- Consultar los movimientos, en lista y en detalle, y que **cada persona consulte los suyos**.
- **Anular** un movimiento emitiendo su inverso.
- El **comprobante interno** de cada movimiento, con numeración propia.
- El catálogo de **métodos de pago**, y qué **pasarela** procesó cada transacción con su referencia externa.

**No incluye**

- **Cobrar.** Este módulo registra que se pagó; **quién ejecuta el cobro es la pasarela**, y la integración con ella es un requerimiento propio que no se abre aquí. Un movimiento en `PENDIENTE` es exactamente eso: un cobro que todavía no confirmó nadie.
- **Aplicar el efecto de lo comprado.** Que un upgrade cambie el nivel de alguien es escribir en `user_memberships`, tabla de `SP` con sus propias reglas (`RF-SP-032`, `RN-SP-018`). Ver §1.4.
- **La factura fiscal.** El comprobante de este módulo **no es un documento DIAN**: no lleva resolución, ni rango autorizado, ni nota crédito. Ver §1.5, que existe para que nadie lo use como si lo fuera.
- **El cálculo de la comisión.** Cuánto se le debe a quién lo resuelve `CM` (`RF-CM-005`); aquí solo se registra el movimiento con el que se le paga.
- **Retiros, balances y egresos.** Son la tercera etapa del módulo y no se registran todavía: una salida de dinero tiene aprobación, saldo disponible y un perfil de riesgo entero que no se puede escribir de paso. Quedan declarados en §4.2.
- **Generar el PDF del comprobante.** El backend **no maneja ningún binario**: ningún controlador acepta ni devuelve ficheros, y abrir esa puerta es una decisión de infraestructura que ningún requerimiento respalda hoy. Se publican los datos; quien los pinte los pinta.

### 1.4 La frontera, y por qué está donde está

**Un movimiento no cambia el nivel de nadie.** Registra que alguien compró un upgrade; quien lo aplica es `SP`, porque `user_memberships` es suya. Es la misma frontera que [`requirements/pm.md` §1.4](pm.md) trazó para el catálogo, y por las mismas dos razones — con una diferencia que sí es nueva y que este documento no puede esquivar.

`PM` podía quedarse fuera porque **no necesitaba escribir nada** en `SP`: publicaba un catálogo y ahí terminaba su trabajo. `MV` no puede: un **depósito confirmado tiene que habilitar la cuenta del cliente**, o `RF-SP-045` deja a todo el mundo encerrado en `FTD_PENDIENTE` para siempre. Eso es una escritura, cruza la frontera, y **todas las interfaces publicadas hasta hoy son de solo lectura**. De ahí sale **D-26** (§3).

**Y el efecto solo lo produce un movimiento confirmado.** Un movimiento `PENDIENTE` es un cobro que nadie verificó todavía: no habilita cuentas, no sube niveles y no comisiona. Es la traducción exacta del argumento con el que `PM` se negó a registrar compras antes de que existiera el cobro — «un objeto que dice que alguien pagó cuando nadie verificó que pagara» —, con la diferencia de que aquí el objeto **sí** existe y lo que lo separa de la realidad es un **estado**, no su ausencia.

**Lo que este módulo sí deja resuelto para quien venga después:** el movimiento **congela** el importe, la moneda, la vigencia y el vendedor. Ninguno se vuelve a leer del catálogo ni de la estructura comercial. Es la condición que `PM` y `CM` le impusieron a este módulo antes de que existiera, y es la que impide que corregir un precio reescriba una factura, o que reasignar un cliente cambie a quién se le pagó por una venta de hace un año.

### 1.5 El comprobante no es una factura, y conviene decirlo aquí

El comprobante que emite este módulo lleva **numeración propia, correlativa y sin huecos**, y sirve para que una persona sepa qué compró y por cuánto. **No es un documento fiscal.**

La diferencia no es de matiz. Una factura electrónica en Colombia va bajo **resolución de la DIAN** con rango numérico autorizado, no se modifica jamás —se anula con **nota crédito**— y su emisión pasa por un proveedor tecnológico autorizado. FACTECH GROUP SAS es una sociedad colombiana, de modo que esto llegará.

**Se decide por adelantado cómo llegará**, para que llegue como una ampliación y no como una reescritura: la factura fiscal será una **entidad aparte** que apunta al movimiento y tiene su propia numeración, y no un campo más de esta tabla. Un movimiento puede existir sin factura —un depósito no se factura— y una factura no puede existir sin el hecho que la origina.

---

## 2. Submódulos

Según [`modules.md` §5](../modules.md#5-fichas-de-modulo).

| Submódulo | Responsabilidad | Entidades principales |
|---|---|---|
| Movimientos | Registrar, consultar y anular hechos económicos | `movements` |
| Medios de pago | Con qué se pagó y quién lo procesó | `payment_methods` |

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
| `RF-MV-003` | Confirmar un movimiento pendiente | Movimientos | `movements:confirm` |
| `RF-MV-004` | Consultar movimientos | Movimientos | `movements:read` |
| `RF-MV-005` | Consultar el detalle de un movimiento, con su comprobante | Movimientos | `movements:read` |
| `RF-MV-006` | Consultar los movimientos propios | Movimientos | Autenticado |
| `RF-MV-007` | Anular un movimiento | Movimientos | `movements:void` |
| `RF-MV-008` | Consultar los métodos de pago | Medios de pago | Autenticado |

**El depósito y la compra son dos requerimientos y no uno**, y eso **se aparta del precedente** que `PM` y `CM` fijaron —«el alta es una, no dos»—. La razón por la que aquí no aplica no es el contenido del movimiento sino **quién lo pide y por dónde entra**: la compra la origina una persona autenticada en un flujo de pago; el depósito lo origina un **sistema externo** por un canal con otra autenticación, otra idempotencia y otro límite de tasa. Fundirlos obligaría a un endpoint con dos modelos de seguridad, que es donde se cuela el que sobra.

**`RF-MV-003` existe porque el registro y la confirmación están separados en el tiempo.** Un cobro se inicia, viaja a la pasarela y vuelve; entre las dos cosas el movimiento existe y no produce efectos. Sin este requerimiento habría que elegir entre registrar el movimiento cuando ya no se puede fallar —perdiendo el rastro de los cobros que no llegaron— o darlo por bueno al crearlo, que es lo que `PM` §1.4 llama «un objeto que dice que alguien pagó cuando nadie lo verificó».

**No hay requerimiento para editar un movimiento**, y no es un olvido: `RN-MV-001` lo prohíbe. Lo más parecido es `RF-MV-007`, que **no lo cambia**: emite otro.

### 4.2 Lo que se registrará después, y por qué no ahora

| Etapa | Qué trae | Por qué espera |
|---|---|---|
| **2 — Liquidación** | Calcular lo que se le debe a cada vendedor y registrar el movimiento con el que se le paga | El cálculo es de `CM` (`requirements/cm.md` §1.3) y necesita movimientos reales contra los que probarse. Escribirlo antes produce código que no se puede probar contra nada |
| **3 — Retiros y balances** | Salidas de dinero y saldo disponible | Una salida tiene **aprobación**, **saldo** y un perfil de riesgo propio. Colgarla de este documento ahora la haría parecer una variante del depósito con el signo cambiado, que es exactamente lo que no es |

---

## 5. Reglas de negocio

### 5.1 Catálogo

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-MV-001` | El movimiento no se modifica ni desaparece | Siempre | Ningún importe, moneda, cantidad, producto ni participante cambia después de creado, y **no hay eliminación** — ni lógica ni física. Corregir es **emitir el inverso** (`RF-MV-007`) | **Crítica** |
| `RN-MV-002` | Los datos del producto se **copian**, no se referencian | Al registrar una compra | Precio unitario, moneda, vigencia en días y membresía destino se guardan **en el movimiento**. No se leen del catálogo después. Es la condición que `requirements/pm.md` §1.4 impuso a este módulo antes de que existiera: sin ella, corregir un precio reescribe lo ya vendido | **Crítica** |
| `RN-MV-003` | El vendedor se congela | Al registrar cualquier movimiento de un cliente | Se guarda **quién era su vendedor en ese momento**, no se resuelve después por `user_supervisors`. Reasignar un cliente no debe cambiar a quién se le atribuyó una venta pasada — es el mismo argumento de `RN-MV-002` aplicado a la estructura comercial | **Crítica** |
| `RN-MV-004` | Solo un movimiento **confirmado** produce efectos | Siempre | Un movimiento `PENDIENTE` no habilita cuentas, no concede membresías y no comisiona. Un `ANULADO` tampoco, y además deshace lo que el confirmado había producido | **Crítica** |
| `RN-MV-005` | La referencia externa es única por pasarela | Al registrar y al confirmar | La misma notificación entregada dos veces —cosa que **toda** pasarela hace— debe producir **un** movimiento y no dos. Se declara en el esquema, porque una comprobación en el caso de uso no sobrevive a dos entregas simultáneas | **Crítica** |
| `RN-MV-006` | Un depósito no lleva producto; una compra sí | Al registrar | La condición se exige **en los dos sentidos**, como `RN-PM-002`: un depósito con producto promete una entrega que nadie hará, y una compra sin producto no dice qué se compró | Alta |
| `RN-MV-007` | La cantidad es uno en los upgrades | Al registrar una compra | Un upgrade con cantidad dos no significa nada: no se sube dos veces al mismo nivel. Los bots admiten más de uno. **No se puede declarar en el esquema** —un `CHECK` no consulta `products`— y vive en el caso de uso, como `RN-CM-001` | Alta |
| `RN-MV-008` | El comprobante es correlativo y **no es fiscal** | Al confirmar | Numeración propia, sin huecos, que no se reutiliza jamás. **No sustituye a la factura electrónica** (§1.5), y el documento fiscal será una entidad aparte que apunte al movimiento | Alta |
| `RN-MV-009` | El importe respeta los decimales de su moneda | Al registrar | Igual que `RN-PM-007` para el precio del catálogo, y por lo mismo: la escala la decide la moneda y no la columna | Media |
| `RN-MV-010` | Método de pago y pasarela son dos datos | Al registrar | **El método** es con qué pagó la persona —transferencia, tarjeta, cripto—; **la pasarela** es quién procesó la transacción. Una pasarela ofrece varios métodos y un método lo ofrecen varias pasarelas: en un solo campo, cambiar de proveedor obligaría a reescribir datos históricos | Media |

### 5.2 Por qué las críticas son críticas

**`RN-MV-001` — el movimiento no se toca.** Es lo que separa un libro de una tabla. Un importe que se puede editar convierte cualquier pregunta sobre el pasado en una conjetura, y el daño no se descubre al editar: se descubre meses después, cuando dos personas miran el mismo número y no coinciden. Es la misma postura de `RN-PM-010` y `RN-CM-005`, llevada un paso más allá — allí la fila permanece aunque se retire; aquí **ni siquiera se retira**.

**`RN-MV-002` y `RN-MV-003` — congelar, no referenciar.** Son la misma regla aplicada a dos cosas distintas, y las dos las **impusieron otros documentos antes de que este módulo existiera**: `pm.md` §1.4 para el precio y la vigencia, y la estructura comercial para el vendedor. El defecto que evitan no falla en el momento: aparece cuando alguien corrige un precio, o reasigna un cliente, y descubre que acaba de cambiar el pasado.

**`RN-MV-004` — solo lo confirmado surte efecto.** Sin ella, iniciar un cobro concede lo que se está comprando. Es el agujero por el que se regalan membresías, y no hace falta mala fe: basta con que la pasarela rechace la tarjeta después.

**`RN-MV-005` — idempotencia.** Toda pasarela reintenta cuando no recibe respuesta a tiempo, de modo que **la doble entrega no es un caso raro: es el caso normal**. Sin unicidad en el esquema, la segunda entrega duplica el depósito, y un depósito duplicado es dinero que el sistema cree tener. Se declara en la base y no en el código por lo mismo que `RN-SP-018` tuvo que corregirse el 26-08-2026: dos peticiones simultáneas burlan cualquier comprobación previa.

### 5.3 Lo que este módulo NO decide, y por qué

**Quién ve los movimientos de quién.** Un director que consulta movimientos ¿ve los de su equipo, los de todos, o solo los suyos? Es **alcance de datos**, depende de **D-22** —abierta, issue #28— y es el mismo aplazamiento que `requirements/cm.md` §5.3 ya hizo. Los requerimientos se especifican con **alcance global explícito** para quien tenga el permiso, y `RF-MV-006` cubre el caso propio sin depender de esa decisión.

**Cómo se escribe en `SP`.** Es **D-26** (§3), y este documento la abre pero no la cierra: fija cómo se escribirá entre módulos para siempre.

**Qué pasarelas se integran.** Este documento declara que hay una y que deja su referencia; con cuál se firma y cómo se autentica su notificación es un requerimiento de integración propio.

---

## 6. Permisos

| Código | Recurso | Acción | Para qué |
|---|---|---|---|
| `movements:read` | `movements` | `read` | Consultar movimientos y su detalle |
| `movements:create` | `movements` | `create` | Registrar un depósito o una compra |
| `movements:confirm` | `movements` | `confirm` | Dar por bueno un movimiento pendiente |
| `movements:void` | `movements` | `void` | Anular emitiendo el inverso |

**`confirm` y `void` no reutilizan `update`**, y esa es la única decisión de esta sección. Un movimiento **no se actualiza nunca** (`RN-MV-001`), de modo que un permiso llamado `movements:update` prometería algo que no existe. Y son dos permisos y no uno porque confirmar es operación de caja diaria mientras que anular deshace dinero: quien concilia pagos no tiene por qué poder revertirlos.

---

## 7. Modelo de datos

### 7.1 `movements`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `type` | `varchar(20)` | No | `DEPOSITO`, `COMPRA`, `REVERSION` |
| `status` | `varchar(20)` | No | `PENDIENTE`, `CONFIRMADO`, `ANULADO` |
| `client_id` | `uuid` | No | `users` |
| `seller_id` | `uuid` | **Sí** | `users` — congelado |
| `product_id` | `uuid` | **Sí** | `products` — nulo en un depósito |
| `quantity` | `integer` | No | — |
| `unit_amount` | `numeric(14,4)` | No | Copiado del producto |
| `total_amount` | `numeric(14,4)` | No | — |
| `currency_id` | `uuid` | No | `currencies` |
| `validity_days` | `integer` | **Sí** | Copiado del producto; nulo = no caduca |
| `target_membership_id` | `uuid` | **Sí** | `memberships` — copiado del producto |
| `payment_method_id` | `uuid` | **Sí** | `payment_methods` |
| `gateway` | `varchar(50)` | **Sí** | Quién procesó |
| `external_reference` | `varchar(200)` | **Sí** | La referencia de la pasarela |
| `receipt_number` | `bigint` | **Sí** | Comprobante; se puebla al confirmar |
| `reverses_movement_id` | `uuid` | **Sí** | `movements` — solo en una `REVERSION` |
| `occurred_at` | `timestamptz` | No | Cuándo ocurrió el hecho |
| `confirmed_at` | `timestamptz` | **Sí** | — |
| `created_at` | `timestamptz` | No | — |

**Sin `updated_at` y sin `deleted_at`**, y las dos ausencias son la implementación de `RN-MV-001`. Una columna de última modificación en una tabla que no se modifica es una invitación escrita; una de borrado lógico, más todavía.

**`seller_id` es nulable** porque no todo movimiento tiene vendedor: el de un cliente sí, el pago de una comisión a un vendedor no tiene un tercero detrás.

**`total_amount` se guarda además de `unit_amount` y `quantity`**, aunque sea su producto. No es redundancia perezosa: es el importe **por el que se emitió el comprobante**, y calcularlo al leer haría que un cambio futuro en cómo se redondea reescribiera comprobantes ya entregados.

**`occurred_at` no es `created_at`.** Un depósito ocurre cuando el bróker lo recibe y se registra cuando su notificación llega, que puede ser horas después. Confundirlos desplaza los hechos al momento en que el sistema se enteró.

### 7.2 `payment_methods`

| Columna | Tipo | Nula |
|---|---|---|
| `id` | `uuid` | No |
| `code` | `varchar(50)` | No |
| `name` | `varchar(100)` | No |
| `status` | `varchar(20)` | No |
| `created_at` | `timestamptz` | No |

Mismo formato de código que `roles`, `memberships` y `products`: `^[A-Z][A-Z0-9_]*$`.

**Un método desactivado no invalida los movimientos que lo usaron.** Es el mismo criterio de `RN-PM-008` con las monedas: la validación es del momento del registro, no permanente.

### 7.3 Restricciones exigidas en el esquema

| Restricción | Sobre | Regla |
|---|---|---|
| `ck_movements_type` | `type IN ('DEPOSITO','COMPRA','REVERSION')` | §7.1 |
| `ck_movements_status` | `status IN ('PENDIENTE','CONFIRMADO','ANULADO')` | `RN-MV-004` |
| `ck_movements_quantity` | `quantity >= 1` | `RN-MV-007`, en lo que el esquema puede sostener |
| `ck_movements_amounts` | `unit_amount > 0 AND total_amount > 0` | `RN-MV-009` |
| `ck_movements_type_product` | `(type = 'COMPRA' AND product_id IS NOT NULL) OR (type <> 'COMPRA' AND product_id IS NULL)` | `RN-MV-006`. Las dos ramas son predicados `IS NULL`/`IS NOT NULL` y **nunca evalúan a `NULL`** — la precaución no es teórica: `ck_deletion_reason` se escribió con un `OR` cuyo lado nulo evaluaba a `NULL`, y un `CHECK` que devuelve `NULL` **acepta la fila** |
| `ck_movements_reversion` | `(type = 'REVERSION') = (reverses_movement_id IS NOT NULL)` | Solo una reversión apunta a otro movimiento, y toda reversión apunta a uno |
| `uq_movements_external_reference` | Único sobre `(gateway, external_reference)`, **parcial**: `WHERE external_reference IS NOT NULL` | `RN-MV-005`. Parcial porque un movimiento registrado a mano no tiene referencia, y en PostgreSQL dos nulos no compiten |
| `uq_movements_receipt` | Único sobre `receipt_number`, **parcial**: `WHERE receipt_number IS NOT NULL` | `RN-MV-008` |
| `fk_movements_*` | `client_id`, `seller_id`, `product_id`, `currency_id`, `target_membership_id`, `payment_method_id`, `reverses_movement_id` | Integridad |

**Lo que NO se puede declarar en el esquema, y por eso vive en el dominio:** que la cantidad sea uno **cuando el producto es un upgrade** (`RN-MV-007`) —un `CHECK` no consulta `products`—, que el importe respete los decimales de **su** moneda (`RN-MV-009`) y que solo lo confirmado surta efecto (`RN-MV-004`).

---

## 8. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 01-09-2026 | **Creación del módulo `MV`**, el cuarto del sistema, por decisión del responsable del proyecto, con **ocho requerimientos** y **diez reglas propias**. Es dueño de `movements`, el **libro de hechos económicos**: compras, depósitos y sus reversiones, con un campo de tipo que los distingue sin separarlos. Con él **se cierran tres aplazamientos que llevaban semanas abiertos y que se citaban entre sí**: `requirements/pm.md` §1.4 no registraba la compra «porque no existe el cobro», `requirements/cm.md` §1.4 no calculaba «porque no hay tabla de ventas», y `RF-SP-045` dejaba a los clientes nuevos en `FTD_PENDIENTE` **sin nada capaz de sacarlos**. El alcance elegido es **todo hecho económico** y no solo la venta, de modo que el módulo **absorbe el candidato «Finanzas»** de [`modules.md` §6](../modules.md#6-alcance-por-inventariar). Las dos reglas que lo definen ya venían impuestas desde fuera: **`RN-MV-002`** —el precio, la moneda y la vigencia se **copian** del producto— la exigió `pm.md` §1.4 antes de que este módulo existiera, y **`RN-MV-003`** —el vendedor se congela— es la misma idea aplicada a la estructura comercial, para que reasignar un cliente no cambie a quién se le pagó por una venta pasada. **`RN-MV-001` va un paso más allá que sus hermanas de `PM` y `CM`**: allí la fila permanece aunque se retire; aquí **no se retira ni se edita**, y corregir es emitir el inverso. Se decide además que el **comprobante no es una factura fiscal** (§1.5) y **cómo llegará la que sí lo sea** —una entidad aparte que apunta al movimiento—, para que llegue como ampliación y no como reescritura. **Y el módulo abre D-26**, que este documento no cierra: un depósito confirmado tiene que **escribir** en `users`, y las cuatro interfaces publicadas hasta hoy son **de solo lectura** por norma explícita de `architecture.md` §15.2. Se recomienda la salida que el propio `pm.md` §1.4 ya anticipó —que `SP` publique la operación con sus reglas intactas— y se deja abierta porque fija cómo se escribirá entre módulos para siempre. Las etapas 2 y 3 —liquidación, y retiros con balances— quedan **declaradas y sin registrar** en §4.2. | Responsable del proyecto |
