# PLAN — `RF-MV-001` Registrar una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-001` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 02-09-2026 |
| Versión | 0.4.0 |
| Estado | **Aprobado** |
| Enmendado el | 16-09-2026 — `V12`: `user_id` en la cabecera, `seller_id` en cada línea (§2.4); `V14`: el descuento de la línea, sus rebajas y su paquete (§2.5) |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

Este plan **funda la mecánica del módulo** y los demás la heredan sin repetirla: el esquema, las lecturas cruzadas hacia `SP` y `PM`, la traducción de errores y el generador del código de comprobante rigen para `RF-MV-002` a `RF-MV-009`.

---

## 1. Enfoque

Un alta con **muchas verificaciones contra dos módulos ajenos** y ninguna comprobación de concurrencia.

Lo que este plan tiene que explicar no es el `INSERT` —que es corriente— sino tres cosas: **de dónde salen los datos que la petición no envía** (precio, vigencia, moneda y vendedor **de cada línea**), **cómo se pregunta por la oferta sin volver a calcularla aquí**, y **por qué esta operación no bloquea nada** pese a que dos peticiones simultáneas puedan vender dos veces el mismo upgrade.

## 2. Cambios de esquema

**Dos migraciones y no una, y el reparto no es el que este plan escribió el 02-09-2026.**

`V51__seed_movements_permissions.sql` — **ya aplicada**. Estrena los cuatro permisos y **nada más**: ninguna tabla.

`V54__create_movements.sql` — **la migración que funda el módulo**. Crea las cuatro tablas y siembra dos catálogos. **Aplicada el 04-09-2026.**

**Por qué se separaron.** Los permisos se adelantaron porque su tarea no depende de ninguna otra, y adelantar una tabla habría sido distinto: un permiso sin endpoint que lo exija no rompe nada —el catálogo es datos, y su único efecto es poder concederse—, mientras que una tabla sin el caso de uso que la escribe es un esquema que promete algo que no existe.

**Y por qué el número acabó siendo `54`, después de haber sido `51`, `52` y `53`.** El número lo toma quien se aplica primero ([`modelo-datos.md` §1](../../../modelo-datos.md)): Flyway deja fuera una migración con número por debajo del último aplicado, de modo que reservar por adelantado y aplicar después es exactamente lo que no se puede hacer. Estas tablas lo comprobaron **tres veces**:

| Reserva | Quién la desplazó |
|---|---|
| `V51` | `RN-SP-025` la tenía reservada, y ninguna de las dos estaba escrita |
| `V52` | `develop` fusionó su propio `V48` (`products:sale`, PR #56) y toda la serie corrió un puesto |
| `V53` | `V53__products_source_membership.sql` —el origen del upgrade, `RF-PM-001`— se fusionó el 03-09-2026, antes de que estas tablas existieran |

Lo que hay que leer de esto no es el número, que es un detalle: es que **una tabla reservada no está reservada**. Mientras el `SQL` no exista, cualquier rama que se fusione antes se lleva el hueco.

### 2.1 Las cuatro tablas

Su forma la fija [`requirements/mv.md` §7](../../../requirements/mv.md) y no se repite aquí. Lo que sí decide este plan es el orden y tres detalles:

| Tabla | Detalle |
|---|---|
| `movement_types` | Se crea **y se siembra en la misma migración**, con una sola fila: `VENTA`, prefijo `VTA`. Una tabla de tipos vacía deja el módulo sin poder registrar nada, y separar la siembra permitiría desplegar ese estado |
| `payment_methods` | Sembrada con **tres filas**: `CREDIT_CARD`, `PSE` y `POINTS`, fijadas por el responsable del proyecto el 04-09-2026 en lugar del `EFECTIVO`/`TRANSFERENCIA` que este plan había supuesto. **`POINTS` se siembra pese a que todavía no hay saldo de puntos**, y la consecuencia está declarada en [`requirements/mv.md` §7.4](../../../requirements/mv.md): una venta pagada así se registra sin que haya de dónde descontar |
| `movements` | **Sin `updated_at` ni `deleted_at`** (`RN-MV-001`). Es la única tabla del sistema que no los lleva, y por eso el gestor de auditoría de la aplicación no puede tratarla como a las demás |
| `movement_details` | Clave foránea a `movements` **con borrado en cascada**, que aquí no significa nada porque nada borra ventas: está para que el esquema no admita líneas huérfanas |

**Los índices que se crean son dos y solo dos**: `movement_details(movement_id)` —que se recorre siempre entero al leer una venta— y `movements(client_id)`, que es el filtro de `RF-MV-008`. El resto se añadirá cuando `RF-MV-006` decida por qué se lista. **Desde el 16-09-2026 el segundo es `movements(user_id)`** (§2.4).

### 2.2 Los permisos, en su propia migración y solo para `SUPERADMIN`

`movements:read`, `movements:create`, `movements:confirm` y `movements:void` se siembran en `V51` y se asocian ahí mismo **únicamente a `SUPERADMIN`**.

**Esto no es lo que hizo `RF-PM-001` ni lo que [`security.md` §4.4](../../../security.md) obliga**, que es asociar también a `ADMIN`. Es una **decisión del responsable del proyecto del 02-09-2026**, recogida en [`mv.md` §6.1](../../../requirements/mv.md) y en la propia §4.4, y **cambia lo que este requerimiento entrega**: por `RN-SEG-003`, con `ADMIN` fuera ningún rol de la fuerza comercial —que cuelga entera de él— podrá declarar `movements:create`. El endpoint que este plan diseña queda construido y **operable solo por el superadministrador** hasta que la reserva se levante, que son dos `INSERT`.

**Olvidar la asociación a `SUPERADMIN` sí rompe la migración**, y a propósito: `V51` termina con una guarda que aborta si alguna de las cuatro filas no se insertó. `RN-SEG-007` acota la raíz por el catálogo completo, y un permiso sembrado y no asociado la dejaría por detrás de sus propios hijos — un defecto que sin la guarda se descubriría mucho después y en otro sitio, con `RN-SEG-003` rechazando un rol sin decir que lo que falta es una siembra.

### 2.3 `ck_movements_confirmed` se declara ahora aunque la use `RF-MV-003`

La restricción que ata `confirmed_at` al estado `CONFIRMADA` no la ejercita este requerimiento: aquí toda venta nace pendiente y con la fecha vacía. Se declara igual, porque **la coherencia entre una fecha y un estado es del esquema**, y añadirla después obligaría a comprobar antes que ninguna fila la incumpla ya.

### 2.4 `V12`: la cabecera lleva un sujeto y el vendedor baja a la línea — 16-09-2026

Enmienda del Art. I.7 sobre un requerimiento construido, por decisión del responsable del proyecto ([`requirements/mv.md`](../../../requirements/mv.md) v0.16.0: `RN-MV-026` nueva, `RN-MV-003` enmendada). **`V7` no se reescribe** —una migración aplicada no se toca, `modelo-datos.md` §5.4— y `V12__mv_sujeto_y_vendedor_por_linea.sql` lo enmienda:

| Cambio | Cómo | Por qué así |
|---|---|---|
| `movements.client_id` → `movements.user_id` | `RENAME COLUMN`, con su FK y su índice renombrados (`fk_movements_user`, `ix_movements_user`) | Es la misma columna con el nombre correcto: **el sujeto** del movimiento, que en una venta es quien compra y mañana será quien deposita o quien cobra. Renombrar conserva las filas y los índices; borrar y crear obligaría a copiar |
| `movements.seller_id` desaparece | `DROP COLUMN`, tras copiar su valor a las líneas | La cabecera **no puede** llevar al vendedor: una venta puede tener varios (`RN-MV-003`) y los tipos de movimiento que vienen no tienen ninguno |
| `movement_details.seller_id` nace | `uuid NULL`, FK a `users` `ON DELETE RESTRICT`, índice **parcial** `(seller_id, movement_id) WHERE seller_id IS NOT NULL` | Nula **solo** por los tipos sin vendedor; en `VENTA` la exige el caso de uso. El índice sirve a la mitad «lo que vendí» de `RF-MV-008` y sustituye a `ix_movements_seller`, que se va con la columna |
| Las filas que ya existen | `UPDATE movement_details d SET seller_id = COALESCE(m.seller_id, m.client_id) FROM movements m WHERE m.id = d.movement_id` **antes** del `DROP` | Ninguna venta pierde su atribución, y **ninguna línea de venta queda sin vendedor**: las que estaban sin él —posibles entre el 04-09 y el 16-09, y solo de quien no colgaba de nadie— reciben **al comprador**, que es exactamente lo que `RN-MV-003` dice hoy de esa persona. No es inventar una atribución: es la única que la regla admite para ese caso |

**Lo que el esquema no puede sostener queda declarado**: «obligatorio en `VENTA`» exige mirar `movement_types`, y un `CHECK` no consulta otras tablas. Lo sostienen `RegisterSaleService` —que no construye una línea sin vendedor— y `MovementLine`, que no ofrece la forma de construirla sin él.

**El orden de las sentencias importa y va en la migración**: renombrar, añadir la columna a la línea, copiar, borrar la de la cabecera. Copiar después de borrar no es posible, y borrar antes de copiar pierde la atribución de todo lo vendido hasta hoy.

### 2.5 `V14`: el descuento es de la línea, y la línea recuerda su paquete — 16-09-2026

Enmienda del Art. I.7 sobre un requerimiento construido, por decisión del responsable del proyecto ([`requirements/mv.md`](../../../requirements/mv.md) v0.17.0: `RN-MV-027` nueva, `RN-MV-013` enmendada). `V14__mv_descuentos_por_linea.sql` enmienda `V7` y `V12`, y **es la primera migración de `MV` que crea una tabla después de la consolidación**:

| Cambio | Cómo | Por qué así |
|---|---|---|
| `movement_details.package_id` | `uuid NULL`, FK a `product_packages` `ON DELETE RESTRICT` | Referencia y no copia (`RN-MV-002`): el paquete no se borra, se retira. Nulo cuando el producto se compró suelto, que hoy es siempre |
| `movement_details.line_discount` | `numeric(14,2) NOT NULL DEFAULT 0`, con `ck_movement_details_discount` (`>= 0` y `<= quantity * unit_price`) | El `DEFAULT 0` **no es comodidad**: es lo que hace que las filas ya existentes y las de las pruebas que insertan en crudo sigan siendo válidas sin reescribirlas. Ninguna línea vendida hasta hoy tuvo rebaja |
| `ck_movement_details_amount` | `line_amount = quantity * unit_price - line_discount` | La igualdad de la cabecera, bajada a la línea. Las filas existentes la cumplen con el cero |
| `movement_detail_discounts` | `id`, `movement_detail_id` (FK `ON DELETE CASCADE`, como la línea con su venta), `type`, `value numeric(14,4)`, `discount_value numeric(14,2)`, `created_at`; `CHECK` de tipo, de rango y de signo; índice por `movement_detail_id` | Una fila por rebaja. La cascada significa lo mismo que en `movement_details`: nada borra ventas, y el esquema no admite rebajas huérfanas |

**Lo que el esquema no puede sostener**: que `line_discount` sea exactamente `quantity × Σ discount_value` de sus rebajas cruza dos tablas, y un `CHECK` no lo hace. Lo sostiene `MovementLine`, que calcula las dos cifras desde las rebajas y **no ofrece constructor que las reciba**, con el mismo argumento con el que `Movement` suma su total.

**El agregado cambia y el caso de uso apenas**: `Movement` deja de fijar `discountAmount` en cero y lo suma de las líneas; `RegisterSaleService` construye cada línea **sin rebajas**, que es lo que esta operación decide. La conversión de un porcentaje a dinero vive en `LineDiscount` con la regla de `ProductPrice` —mitad hacia arriba a los decimales de la moneda— y **no se reimplementa la cuenta de `PM`**: `MV` recibe el tipo y el valor y congela el resultado; cómo se rebaja dentro de un paquete lo decide `RN-PM-036`.

**`MV` no depende de `products..domain..`** (regla de ArchUnit, `T-19`), de modo que el tipo de descuento se declara aquí —`MovementDiscountType`— con los mismos dos valores que `DiscountType` de `PM`. Es un duplicado de dos literales y no un modelo compartido, y es el precio de que la frontera siga siendo verificable.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `Movement` | Nuevo | El agregado: cabecera, líneas, estado y totales. **Calcula el total él mismo** |
| `domain/models` | `MovementLine` | Nuevo | Producto, cantidad y **lo copiado**: precio unitario, vigencia e importe de línea |
| `domain/models` | `MovementStatus` | Nuevo | `PENDIENTE` \| `CONFIRMADA` \| `RECHAZADA` \| `ANULADA` |
| `domain/models` | `MovementCode` | Nuevo | El código de comprobante y su composición (`RN-MV-016`) |
| `domain/repository` | `MovementRepository` y su adaptador | Nuevos | Guardar la venta con sus líneas |
| `domain/service` | `RegisterSaleService` | Nuevo | Caso de uso: resuelve, verifica, arma el agregado y lo guarda |
| `application` | `RegisterSaleRequest`, `SaleResponse`, `SaleLineResponse` | Nuevos | Entrada y salida |
| `interfaces` | `MovementController` | Nuevo | `POST /api/v1/movements` |
| `modules/products/application` | `ProductCatalog` | **Modificado** | Gana la vista de venta y la consulta de oferta. Ver §3.2 |
| `modules/system/users/application` | `ClientCatalog` | **Nuevo, dentro de `SP`** | El estado del cliente y su vendedor vigente. **El nivel NO: lo publica ya `CurrentMembershipLookup`** (enmienda del 04-09-2026, `tasks.md` §2.2). Ver §3.2 |

### 3.1 El total lo calcula el agregado, no el caso de uso

`RN-MV-013` dice que el total es la suma de las líneas y **se congela**. Si lo sumara el caso de uso, la venta podría construirse con un total que no corresponde a sus líneas —y nada lo impediría—, de modo que la regla solo sería cierta si nadie se equivoca.

Construido por el agregado a partir de sus líneas, **no hay ningún instante en que el total no sea la suma**. Es el mismo argumento con el que `CM` metió la forma y el valor en un solo objeto (`RF-CM-001` · `plan.md` §3.1).

**Y la instantánea de auditoría la arma también el agregado**, por lo mismo que en `PM`: si cada caso de uso armara su mapa, dos registros describirían la misma venta con claves distintas y compararlos dejaría de ser posible.

### 3.2 Las lecturas cruzadas: qué se pregunta y a quién

Cuatro datos que la petición no trae, y **ninguno se calcula aquí** ([`architecture.md` §15.2](../../../architecture.md): la interfaz la declara el módulo dueño del dato).

| Qué hace falta | Quién lo publica | Por qué no lo resuelve `MV` |
|---|---|---|
| Precio, moneda, tipo, vigencia y membresía destino de cada producto | `PM` — `ProductCatalog` gana una **vista de venta** | Son datos suyos. La vista actual solo lleva código, nombre y si está retirado, y **no se amplía**: se añade un método con su propio registro, para que `CM`, que ya consume el existente, no cambie |
| **Si el producto está en la oferta de esa persona** | `PM` — la misma interfaz | Es la decisión que `RF-PM-007` ya toma. Recalcularla aquí crearía **dos definiciones de «lo que alguien puede comprar»**, y el día que una cambiara la otra seguiría vendiendo lo que la primera ya no ofrece |
| El estado del cliente y **de qué vendedor cuelga** | `SP` — `ClientCatalog`, nuevo | `users` y `user_supervisors` son suyas, y el vendedor de un cliente es su superior comercial con la rama de consumidor de `RN-SP-020` |
| El nivel de membresía vigente del cliente | `SP` — **`CurrentMembershipLookup`, que YA EXISTE** | `user_memberships` es suya, y desde `RF-PM-007` · `T-01` ya publica esta lectura con su borde fijado por prueba. **Enmendado el 04-09-2026** (`tasks.md` §2.2): declararla otra vez en `ClientCatalog` habría creado la segunda definición de «vigente», que es el defecto que aquel puerto existe para evitar |

**Las dos consultas a `PM` se hacen por lote y no por línea.** Una venta de cinco productos que preguntara cinco veces cruzaría la frontera cinco veces para lo mismo: es una `N+1` que no se ve porque cada llamada es un método Java, y que aparece entera en el registro de sentencias.

!!! warning "La comprobación de nivel se escribe aunque hoy la oferta ya la garantice"

    `RF-PM-007` ofrece solo lo que está por encima del nivel actual, de modo que `RN-MV-007` —el producto está en la oferta— **hoy implica** `RN-MV-006` —el upgrade sube—. Comprobar las dos parece redundante, y lo es.

    **Se escriben las dos igualmente**, y el motivo es de quién manda sobre qué. La oferta es una decisión de `PM` y puede ampliarse —el día que se vendan renovaciones del mismo nivel, por ejemplo—; que **una venta no baje a nadie de nivel** es una regla de `MV`, y no puede depender de que otro módulo siga tomando la misma decisión que hoy.

    Es lo que mantiene `EX-005` alcanzable: hoy no se llega por la oferta, y se llegaría el día siguiente a que `PM` la ampliara.

!!! success "Y el 07-09-2026 se amplió, exactamente como este aviso previó"

    `PM` abrió el catálogo a la **renovación** —`X → X`, `requirements/pm.md` §5.2.3— y la oferta pasó a coincidir por **origen**, de modo que un producto de la misma membresía **sí llega** ahora a `RN-MV-006`.

    La comprobación no se borra: **estrecha**. Pasa de rechazar `destino >= nivelActual` a rechazar `destino > nivelActual` — el mismo nivel se admite y el inferior sigue sin admitirse. Lo que este aviso defendía era justo eso: que la regla siguiera viva y en `MV` para poder cambiarla **aquí** el día que `PM` moviera su oferta, sin que nadie tuviera que ir a buscarla.

## 4. Contrato de API

`POST /api/v1/movements` · `201 Created`, con `Location`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-007`: lo que se ve **mirando la petición** — falta el comprador, no hay líneas, cantidad no positiva, producto repetido, fecha futura |
| `403` | Sin el permiso `movements:create` |
| `409` | Lo que solo se sabe **después de resolver**: cuenta en `FTD_PENDIENTE` (`EX-002`), producto fuera de la oferta (`EX-004`), upgrade que no sube (`EX-005`), dos upgrades (`EX-006`), monedas distintas (`EX-008`), cantidad en un upgrade (`EX-009`), método inactivo (`EX-010`) |
| `422` | `EX-001`, `EX-011` y el método inexistente: un dato **bien formado que no resuelve** contra otro módulo |

**El criterio de reparto es el del proyecto, y aquí se aplica a rajatabla**: `400` es forma, `422` es referencia que no existe, `409` es conflicto con el estado del sistema. Lo que empuja tres excepciones al `409` que un lector pondría en `400` —dos upgrades, monedas distintas, cantidad en un upgrade— es que **ninguna de las tres se puede decidir sin haber leído el catálogo**: la petición es idéntica en forma a una correcta, y lo que la hace inválida es qué son esos productos.

**El endpoint es `/movements` y no `/sales`**, aunque este requerimiento solo registre ventas. La tabla es el libro y los depósitos entran por aquí en la etapa 2; un recurso llamado `sales` obligaría a inventar otro para el mismo objeto o a renombrar el publicado.

**Y desde el 16-09-2026 el cuerpo pide `userId` y la respuesta devuelve `user`, no `client`**, por el mismo argumento: el campo nombra **al sujeto** del movimiento (`RN-MV-026`), y `clientId` en un depósito o en una comisión sería un nombre falso sobre un endpoint que ya es el libro. **El vendedor se va de la cabecera de `SaleResponse` a cada `SaleLineResponse`** como `seller`, y en una venta nunca viaja en nulo; la anotación que lo declara nulable se conserva **porque el contrato es del libro y no de la venta**, y un depósito lo llevará vacío.

## 5. Autorización

Permiso `movements:create`, estrenado por `V51` (§2.2). Alcance global explícito (D-22 abierta): quien lo tiene registra ventas de cualquier cliente.

**Hoy solo lo tiene `SUPERADMIN`**, por la reserva de §2.2: mientras siga en pie, no hay ningún otro rol al que se le pueda conceder.

## 6. Auditoría

Registro de **cambios**, acción de creación, con la instantánea completa: sujeto (`user_id`), método, importes, código y las líneas con lo copiado **y el vendedor congelado de cada una**.

**El vendedor tiene que estar en la instantánea**, y es lo único de esta sección que no es rutina: es un dato que el actor no envió y que determina **a quién se le va a pagar**. Sin él en el registro, la pregunta «¿por qué esta venta se le atribuyó a esta persona?» solo se puede responder reconstruyendo cómo estaba la estructura comercial ese día.

## 7. Transaccionalidad

`@Transactional`. La cabecera, sus líneas y el registro de auditoría, o nada.

**Las lecturas a `SP` y `PM` ocurren dentro de la misma transacción** y son de solo lectura. No hay bloqueo sobre nada suyo: si el catálogo cambia justo después, la venta ya copió lo que necesitaba.

## 8. Impacto sobre otros módulos

**En el código, sí lo hay, y es el riesgo mayor de este requerimiento**: dos módulos ajenos ganan interfaces publicadas.

| Módulo | Qué gana | Quién lo escribe |
|---|---|---|
| `PM` | La vista de venta y la consulta de oferta en `ProductCatalog` | Tareas de este requerimiento, aunque el código viva en `modules/products`. Es el precedente de `RF-PM-001` y `RF-PM-007`, que escribieron puertos dentro de `SP` |
| `SP` | `ClientCatalog`: estado y vendedor vigente. **El nivel no**: ya lo publica `CurrentMembershipLookup` | Igual |

**Su definición de terminado exige que las suites de `PM` y de `SP` sigan en verde sin cambios.** Lo que se añade son métodos nuevos sobre interfaces existentes y una interfaz nueva; nada se modifica. **Comprobado el 04-09-2026: 963 pruebas, cero fallos, y ni una prueba de `PM` o de `SP` tocada.**

**Y `MV` gana la regla de ArchUnit que `PM` ya tenía**, extendida a los dos módulos que consume (`tasks.md` §1.1, `T-19`). Sin ella, «pregunta la oferta, no la recalcules» es una frase de este documento: un `SELECT` propio sobre `products` compilaría igual y pasaría las pruebas igual.

**En la documentación, tres enmiendas ya aplicadas** en el mismo pase que este plan:

| Documento | Enmienda |
|---|---|
| `architecture.md` v0.28.0 | §15.2 registra **tres lecturas cruzadas nuevas** —dos hacia `PM` y una hacia `SP`—, las primeras de `MV`, y deja dicho que **la oferta se pregunta y no se recalcula** |
| `modelo-datos.md` v0.19.0 | Las cuatro tablas de `MV` entran como **diseñadas y sin escribir**, **sin copiar su forma**: la fuente es `requirements/mv.md` §7, y este plan añade el número de migración que allí no está. Se recoge además que la tabla que su §4.1 exigía **ya existe en papel y acepta sus dos condiciones** |
| `requirements.md` v0.92.0 | Las filas de `RF-MV-001` y `RF-MV-002` pasan a `Tasks en revisión` con su rama, y con el bloqueo de `RF-SP-045` anotado |

**D-26 no bloquea este requerimiento.** Registrar una venta **no escribe en `SP`**: solo lee. La escritura aparece al confirmar, y es `RF-MV-003` quien no puede terminarse sin esa decisión.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Aceptar el precio en la petición** | Es un descuento sin llamarlo así, y sin autorización ni rastro. `spec.md` §2 lo argumenta como decisión de negocio |
| **Ampliar `ProductView`** en lugar de añadir una vista nueva | Un registro compartido que crece por cada consumidor acaba llevando campos que a la mitad no le sirven, y obliga a `CM` a recompilar por algo que no usa |
| **Calcular la oferta dentro de `MV`** | Dos definiciones de lo mismo. La segunda divergiría en silencio y seguiría vendiendo lo que `PM` ya no ofrece |
| Preguntar el producto **línea a línea** | Una `N+1` que no parece una porque cada llamada es un método Java |
| **Bloquear al cliente** para impedir dos ventas simultáneas del mismo upgrade | No hay nada que proteger: **ninguna de las dos ventas concede nada**. El conflicto es de `RF-MV-003`, que sí aplica el nivel, y bloquear aquí daría la impresión de que está resuelto |
| Un recurso `/sales` | La tabla es el libro; los depósitos entran por el mismo sitio en la etapa 2 |
| Guardar el total **solo en las líneas** y sumarlo al leer | `RN-MV-013`: recalcular al leer hace que un cambio de redondeo reescriba comprobantes ya entregados |
| **Un consecutivo sin huecos** en lugar del aleatorio | Una `SEQUENCE` deja huecos por diseño en cada transacción revertida, y prometer una serie completa obligaría a renumerar. `requirements/mv.md` §7.2.1 |
| Sembrar `PUNTOS` como método de pago desde ya | Ofrecería un método con el que no se puede pagar hasta la etapa 3 |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Los cuatro permisos se siembren sin asociarse a `SUPERADMIN` y `ADMIN`** | La migración se aplicaría sin quejarse. Lo cubre una prueba de la siembra, no una lectura del `SQL` |
| 2 | **Colisión del código aleatorio** | Treinta y dos elevado a seis por tipo y día hace la colisión improbable y no imposible. Se resuelve con la unicidad en el esquema y **reintento acotado** en el caso de uso: tres intentos y falla. Sin la unicidad, la colisión produciría dos comprobantes iguales sin que nada avisara |
| 3 | **La venta se registre con el vendedor equivocado** porque la estructura comercial estaba mal | No se mitiga aquí: `MV` copia lo que `SP` dice. Lo que sí se hace es **devolverlo en la respuesta** y guardarlo en la auditoría, para que el error sea visible el mismo día y no el de liquidar |
| 4 | El gestor de auditoría de la aplicación **espere `updated_at`** en `movements` | Es la primera tabla sin esas columnas. Se comprueba al escribir el adaptador, y falla al compilar o en la primera prueba de integración |
| 5 | **Dos ventas simultáneas del mismo upgrade** | Aceptado y declarado en `spec.md` §13. Ninguna concede nada; el conflicto es de `RF-MV-003` |
| 6 | Las interfaces nuevas de `PM` y `SP` **rompan sus suites** | Se añaden métodos, no se modifican. Su definición de terminado exige las dos suites en verde sin cambios |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Alta de una venta, pendiente y con código | Integración | `CA-MV-001` |
| **El vendedor resuelto en cada línea** | Integración | `CA-MV-002`: el que el actor no envió |
| **La autoventa de quien no cuelga de nadie** | Integración | `CA-MV-017`: cada línea lleva como vendedor a quien compra |
| **La copia sobrevive a corregir el producto** | Integración | `CA-MV-003`: se corrige el precio **después** y la venta no cambia. Comparar al registrar no probaría nada |
| Totales y descuento cero | Integración | `CA-MV-004` |
| Varias líneas, con y sin upgrade | Integración | `CA-MV-005` |
| Formato del código | Integración | `CA-MV-006`: prefijo, día del hecho y alfabeto sin `I`, `L`, `O`, `U` |
| **La venta no cambia el nivel de nadie** | Integración | `CA-MV-007`: la membresía del cliente es la misma después |
| Las diez negativas | Integración | `CA-MV-008` a `CA-MV-017`, cada una con su código y su distinción |
| Auditoría con el vendedor dentro | Integración | `CA-MV-018` |
| El agregado, sin base de datos | Unitaria | El total como suma de líneas, la composición del código, y que un `Movement` no se puede construir sin líneas |
| **La siembra de permisos** | Integración | Los cuatro existen **y están asociados a `SUPERADMIN` y `ADMIN`** — riesgo 1 |
| Reintento del código | Unitaria | Tres intentos y falla, con el generador forzado a colisionar |

**No hay prueba concurrente en este requerimiento, y su ausencia es una afirmación**: ninguna regla de las que aquí se comprueban puede burlarse con dos peticiones simultáneas, porque **ninguna venta registrada produce efecto alguno**. Las que sí lo pueden ser se prueban en `RF-MV-003`.
