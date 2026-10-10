# PLAN — `RF-MV-001` Registrar una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-001` |
| Especificación | [`spec.md`](spec.md) v0.11.0 |
| `spec.md` aprobada el | 02-09-2026 |
| Versión | 0.9.0 |
| Estado | **Aprobado** |
| Enmendado el | 09-10-2026 — **la oficina de cada línea** (`RN-MV-078`): el puerto `SellerTeamLookup` que publica `teams`, `movement_details.team_id` y `V99`, que sirven a todo el módulo (§2.8) |
| Enmendado el | 05-10-2026 — **la venta del alta gratuita nace confirmada sin entregar** (`RN-MV-075`) y `V68` migra las que esperaban (§2.7) |
| Enmendado el | 05-10-2026 — `V65`: **los importes en centésimas** (`bigint`), con un convertidor JPA compartido; `MV` es la tripleta que construye el convertidor y la migración entera ([`ADR-006`](../../../architecture/ADR-006-importes-en-unidades-minimas.md); §2.6) |
| Enmendado el | 03-10-2026 — `RN-MV-006` gana la mitad del **salto**: `SaleRules.verificarQueSube` rechaza también un destino más de un nivel por encima del vigente, con el mismo `EX-005` y otro mensaje (§3.2). Sin esquema |
| Enmendado el | 19-09-2026 — `RN-MV-007` enmendada: `RegisterSaleService` recibe **el canal** (`SaleChannel`) desde la entrada —tienda para el funcionario y la compra propia, hotlink para el registro por enlace— y valida la oferta contra lo que ese canal publica: `ProductCatalog.offeredTo` o `ProductCatalog.publishedByHotlink`, la lectura nueva de `PM`. La petición **no** lo lleva |
| Enmendado el | 16-09-2026 — `V12`: `user_id` en la cabecera, `seller_id` en cada línea (§2.4); `V14`: el descuento de la línea, sus rebajas y su paquete (§2.5) |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

Este plan **funda la mecánica del módulo** y los demás la heredan sin repetirla: el esquema, las lecturas cruzadas hacia `SP` y `PM`, la traducción de errores y el generador del código de comprobante rigen para `RF-MV-002` a `RF-MV-009`.

!!! warning "Enmendado el 09-10-2026 — la oficina de cada línea: `SellerTeamLookup`, `movement_details.team_id` y `V99`"

    `spec.md` v0.11.0 (`RN-MV-078`). **Este plan funda la oficina para todo el módulo**, como fundó el vendedor: **el puerto de lectura que publica `teams`, la columna y la migración `V99` se describen aquí, en §2.8**, y [`RF-MV-002`](../002-comprar-producto-uno-mismo/plan.md), [`RF-MV-011`](../011-comprar-por-hotlink/plan.md) a [`RF-MV-013`](../013-comprar-paquete-por-hotlink/plan.md), [`RF-MV-016`](../016-asignar-vendedores-de-venta/plan.md) y [`RF-MV-058`](../058-rellenar-oficina-de-lineas/plan.md) los citan sin repetirlos. **En una frase**: `RegisterSaleService.registrar` pregunta `SellerTeamLookup.teamAt(vendedor, ocurrioEn)` **después de la atribución y una vez por vendedor distinto**, y `MovementLine` lo copia en `team_id` junto a `seller_id`. **Cómo se lee la oficina** en las respuestas, los listados y los filtros es de [`RF-MV-007`](../007-consultar-detalle-movimiento/plan.md) y sus hermanos; aquí solo se escribe.

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

!!! warning "Los tipos de esta sección los cambia `V65` (§2.6)"

    `line_discount` y `discount_value` son `bigint` en centésimas, y `value` también, sea `PORCENTAJE` o `FIJO`. La tabla siguiente se deja como se decidió.

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

### 2.6 `V65`: los importes en centésimas — 05-10-2026

Enmienda del Art. I.7 sobre un requerimiento construido, por decisión del responsable del proyecto ([`ADR-006`](../../../architecture/ADR-006-importes-en-unidades-minimas.md); [`requirements/mv.md`](../../../requirements/mv.md) v0.73.0). **Este plan construye la pieza común y la migración entera**, aunque convierta columnas de `PM` y `CM`: una migración que cambia la unidad de la mitad del esquema **no se parte por módulos**, porque cada versión intermedia tendría importes en dos unidades y alguna consulta que los cruce (el precio del catálogo contra el de la línea, el lote contra el abono) daría un resultado cien veces equivocado. `PM` y `CM` ponen en sus entidades la anotación y revisan su SQL nativo, cada uno en su tripleta.

| Cambio | Cómo | Por qué así |
|---|---|---|
| El convertidor | `MinorUnitsConverter implements AttributeConverter<BigDecimal, Long>` en `shared`, **no** `autoApply`: cada columna lo declara con `@Convert`. Al leer, `BigDecimal.valueOf(centésimas, 2)`; al escribir, `setScale(2, HALF_UP)` y `movePointRight(2).longValueExact()` | `autoApply` alcanzaría también los porcentajes y las tasas, que son `BigDecimal` y **no** se convierten. Declararlo en cada columna hace que la lista de columnas convertidas se lea en el código. El `HALF_UP` es una red, no la regla (ADR-006 §4) |
| El ayudante | `MinorUnits.toMinor(BigDecimal)` y `MinorUnits.fromMinor(long)`, que el convertidor usa por dentro | **El SQL nativo los necesita**: lo que lee lo convierte al mapearlo con `fromMinor`, y lo que compara lo vincula con `toMinor`. Un solo sitio que multiplica y divide, y no dos |
| `V65` | `ALTER COLUMN … TYPE bigint USING round(col * 100)` sobre las columnas de las trece tablas de ADR-006 §1. **Antes**, `DROP CONSTRAINT` de los `CHECK` que comparan un importe con una constante distinta de cero; **después**, se vuelven a crear con la constante en centésimas | `ALTER … TYPE` revalida los `CHECK` al terminar, y `value <= 100` sobre un `1250` recién convertido haría fallar la migración. Los que comparan con cero o son igualdades lineales (`payable_amount = total_amount - discount_amount`, `line_amount = quantity * unit_price - line_discount`) siguen valiendo multiplicados por cien y no se tocan |
| Los `CHECK` que se reescriben | `ck_movement_detail_discounts_value` (`value <= 100` → `<= 10000`), `ck_product_package_items_percentage` (`discount_value <= 100` → `<= 10000`) y `ck_currencies_decimal_places` (`<= 4` → `<= 2`) | Son los únicos tres que comparan con una constante distinta de cero. `ck_commission_rates_percentage`, `ck_user_commission_rates_percentage`, `ck_commissions_percentage` y la mitad porcentual de `ck_commission_rates_direct_rangos` (`V64`) **no se tocan**: miran columnas de porcentaje, que no se convierten |
| El cuadre del libro | `f_movement_entries_cuadre` (`V49`) declara `suma numeric` y compara con `balance`: **no se reescribe** | Sumar `bigint` en un `numeric` es exacto, y la comparación sigue siendo entre la misma unidad. Es un disparador de fila diferido, de modo que `ALTER … TYPE` no lo dispara |
| Los comentarios | `COMMENT ON COLUMN` de cada columna convertida dice **«en centésimas»** | Quien lea la base sin leer el código tiene que saber que `1250` es `12,50` |

**La migración redondea con `round` de PostgreSQL**, que sobre `numeric` deshace el empate alejándose de cero, exactamente como `RoundingMode.HALF_UP`. En las columnas de `MV` no hay nada que redondear, porque todas eran `numeric(14,2)`, salvo `movement_detail_discounts.value`, que era `numeric(14,4)`. **Va detrás de `V64`** (la comisión directa por rol), que añade `commission_rates.direct_fixed_amount` y retira `products.direct_commission_*`: `V65` convierte la primera y no menciona las segundas.

**Las entidades de `MV` cambian una anotación por columna y nada más**: `movements`, `movement_details`, `movement_detail_discounts`, `payments`, `accounts` y `movement_entries`, `points_amount` incluido. El dominio sigue en `BigDecimal`, y `Movement`, `MovementLine`, `LineDiscount` y `LedgerMovements` siguen redondeando a los decimales de la moneda **antes** de que el valor llegue al convertidor.

**Lo que cuesta de verdad es el SQL nativo.** Cada consulta de `MV` que lee o compara un importe sin pasar por la entidad —mis compras, ventas de mi alcance, el detalle, los pagos, los saldos y el libro— se revisa una a una (`tasks.md` `T-43`), y con ella las siembras de las suites que insertan importes con `JdbcTemplate`, que pasan a escribir centésimas. **Olvidar una no falla**: devuelve un `200` con una cifra cien veces distinta. Por eso `CA-MV-543` compara la lectura nativa con la de la entidad, y no con una constante.

**Alternativa descartada: dividir en SQL** (`total_amount / 100.0`). Ahorra el mapeo, pero PostgreSQL devuelve un `numeric` de escala arbitraria, y la conversión quedaría repartida entre dos lenguajes. ADR-006 §5 lo prohíbe por eso.

### 2.7 La venta del alta gratuita nace confirmada, y `V68` — 05-10-2026

Enmienda del Art. I.7 por `RN-MV-075` ([`requirements/mv.md`](../../../requirements/mv.md) v0.76.0) y `RN-SP-057` ([`requirements/sp.md`](../../../requirements/sp.md) v1.91.0).

**Dónde se decide.** En `RegisterSaleService.registrarAltaDeCliente`, que solo alcanza el adaptador del registro (`PublishedRegistrationSaleRegistrar`). Después de guardar la venta, **si el sujeto está en `FTD_PENDIENTE`**, se confirma en la misma transacción con `MovementRepository.confirmIfPending`, que ya hace la transición condicionada de la cabecera y del pago. **Se usa la transición y no el caso de uso**: `ConfirmSaleService.confirmar` entrega las líneas y publica `CommissionableLinesEvent`, y esas son justo las dos cosas que aquí no deben ocurrir. **No se toca `Movement.registrar`**, que sigue construyendo siempre `PENDIENTE`: el agregado no sabe nada del alta, y la excepción se queda en el único sitio por el que entra.

**Se pregunta por el estado de la cuenta y no por el importe.** Lo que la regla protege es la espera del depósito, y eso lo dice `FTD_PENDIENTE`. Que hoy coincida con el importe cero —el `BECA → BECA` gratuito— es una consecuencia de `RN-SP-044`, no la condición: si mañana un `BECA → BECA` costara algo (`RN-CM-036` lo prevé), la cuenta seguiría naciendo `FTD_PENDIENTE` y su venta tendría un pago que esperar. **Ese caso no existe hoy**, porque el alta gratuita exige importe cero (`RN-MV-022`), y si llegara se replantearía la regla.

**La entrega la hace `PublishedFirstDepositActivation`** (`RF-MV-010` §12), cuando `SP` saca a la cuenta de `FTD_PENDIENTE`. Es otro puerto de `SP` que `MV` implementa, por la misma razón que la venta del registro: `MV` ya depende de `SP`, y la dirección inversa cerraría el ciclo.

**`V68` — datos, sin esquema.** Para cada usuario en `FTD_PENDIENTE`, su venta del alta —la de `client_sellers.first_movement_id`— pasa a `CONFIRMADA` y su pago a `CONFIRMADO`, **solo si** sigue `PENDIENTE` con el pago `GRATIS` `PENDIENTE`. Las líneas no se tocan: siguen `PENDIENTE`, que es lo que la regla nueva deja. **`confirmed_at` es el `created_at` de la venta y no `now()`**: la regla dice que esa venta nace confirmada, y fecharla el día de la migración inventaría un hueco de días entre el alta y una confirmación que, con la regla vigente, habría sido el mismo instante. Además, `now()` haría aparecer esas ventas como confirmadas hoy en cualquier listado por fecha de confirmación. **Las cuentas `ACTIVO` no se tocan**: ya salieron de la espera sin que nada se entregara, y entregar ahora fecharía un FTD (`delivered_at`) que no se sabe cuándo ocurrió. **No hay auditoría por fila**: es una migración, como `V65`, y su encabezado lo dice.

**Alternativa descartada: confirmar al depósito y no al alta.** Dejaría la venta pendiente hasta el depósito y entregaría al confirmarla, sin ninguna excepción a `RN-MV-020`. Se descarta porque es lo contrario de lo que el responsable pidió: la venta **es** una compra hecha, y lo que espera es su activación. Un pendiente que no espera ningún pago tampoco tiene quién lo rechace ni lo anule.

### 2.8 La oficina de cada línea: `SellerTeamLookup`, `team_id` y `V99` — 09-10-2026

Enmienda del Art. I.7 por `RN-MV-078` ([`requirements/mv.md`](../../../requirements/mv.md) v0.97.0 §4.13 y §7.3) y por `RN-SP-051`, `RN-SP-052` y `RN-SP-055` enmendadas ([`requirements/sp.md`](../../../requirements/sp.md) v1.119.0).

**El puerto.** `teams` publica una lectura, **`com.factech.nexus.modules.system.teams.application.SellerTeamLookup`**, con dos métodos:

| Método | Qué responde | Quién lo usa |
|---|---|---|
| `Optional<UUID> teamAt(UUID sellerId, OffsetDateTime instant)` | El equipo del **primero** de la cadena del vendedor —él incluido— que tiene una pertenencia vigente en ese instante; vacío si nadie de la cadena la tiene, y vacío con `sellerId` o `instant` nulos | El registro (`RF-MV-001`, `RF-MV-002`, `RF-MV-011` y el alta por enlace), la compra de paquetes (`RF-MV-012`, `RF-MV-013`) y la asignación (`RF-MV-016`) |
| `Map<UUID, UUID> currentTeamsOf(Collection<UUID> sellerIds)` | Lo mismo con el instante **de ahora** y en lote: vendedor → equipo; **sin entrada** el que no tiene | El relleno (`RF-MV-058`) |

**Una sola sentencia por pregunta.** Es la `WITH RECURSIVE` de `JpaSupervisorChain` —las filas de `user_supervisors` vigentes en el instante, la ruta recorrida en un arreglo como guarda de ciclos (`RN-SP-020` los prohíbe y el esquema no los impide) y el tope de **64** niveles—, y sobre ella `JOIN team_members tm ON tm.user_id = c.user_id AND tm.started_at <= :instante AND (tm.ended_at IS NULL OR tm.ended_at > :instante)`, `ORDER BY c.nivel LIMIT 1`. **En lote**, la semilla lleva **a cada vendedor como origen** —`(origen, user_id, nivel, ruta)`— y la salida es `SELECT DISTINCT ON (c.origen) c.origen, tm.team_id … ORDER BY c.origen, c.nivel`: una ida a la base para todos. El instante de `currentTeamsOf` es el del `Clock` inyectado y no el `now()` del motor, para que una prueba lo pueda fijar. La implementación, **`JpaSellerTeamLookup`**, vive en `teams/domain/repository`, junto a `JpaTeamMemberRepository`; `MV` solo conoce la interfaz, que es lo que `LayerRulesTest` (D-25) deja pasar.

**No se pregunta por el rol ni por el estado del equipo.** Quién puede tener fila lo decide `RN-SP-051` al asignar (`RF-SP-069`), y la venta lee lo que hay (`requirements/mv.md` §4.13: «no se pregunta por el código del rol»). Por lo mismo, un equipo `INACTIVO` con su director vigente sigue siendo su oficina: la regla habla de pertenencia.

**Por qué no se reutiliza `SupervisorChain` y se filtra en `MV`.** Haría falta la cadena entera y después preguntar por la pertenencia de cada eslabón: o una llamada por eslabón, o una lectura de `team_members` desde `MV`, que D-25 prohíbe. La sentencia que **para en el primer miembro** es una sola ida por vendedor, y vive en el módulo dueño de las dos tablas.

**La escritura.** **`MovementLine` gana `teamId`**: los dos `copiarDe` lo reciben justo detrás de `sellerId`, y **una línea con oficina y sin vendedor se rechaza** con `IllegalArgumentException` al construirla —«sin vendedor, sin oficina» es de la línea, como que una copia vacía no es una copia—. **La instantánea de auditoría escribe `team_id` nulo y presente**, por lo mismo que `seller_id`: la clave ausente se leería como «esta versión no lo registraba». **`JpaMovementRepository.insertarLineas`** escribe la columna. **`RegisterSaleService.registrar`** la resuelve **después de la atribución y antes de copiar**: `vendedor == null ? null : equipos.teamAt(vendedor.id(), ocurrioEn).orElse(null)`, y `copiar` la pone en cada línea. **Una vez por vendedor distinto**: hoy la venta tiene uno solo (`SaleAttribution`), y el día que un carrito mezcle vendedores será un mapa vendedor → oficina, nunca una pregunta por línea. **`ocurrioEn` y no `ahora`**: la regla es la estructura del día de la venta, y es lo que hace verdadero `CA-MV-709`. La compra de paquetes hace lo mismo en `BuyPackageService.registrar` ([`RF-MV-012`](../012-comprar-paquete/plan.md)).

**La respuesta.** `SaleResponse.de` recibe además las oficinas resueltas y `SaleLineResponse.de` pone en cada línea **`team`** —`{id, name}`, la forma `LineTeam` que publica [`RF-MV-007`](../007-consultar-detalle-movimiento/plan.md), con su `@Schema(name = "LineTeam")` propio— o nulo. **El nombre no lo da el puerto**, que responde identificadores: lo lee `MovementRepository.findTeamNames(Collection<UUID>)`, un `SELECT id, name FROM teams WHERE id IN (:equipos)` nativo, la misma lectura de nombres que los listados hacen con `LEFT JOIN teams` (`requirements/mv.md` §7.3). Las respuestas que ya salen de `SaleDetailMapper` —la venta pagada con puntos, la del alta gratuita confirmada— traen `team` por la lectura de `RF-MV-007`, sin nada aquí.

**`V97__mv_oficina_de_la_venta.sql`**, en este orden:

1. **La columna.** `ALTER TABLE movement_details ADD COLUMN team_id uuid NULL`, con `fk_movement_details_team` a `teams (id)` **`ON DELETE SET NULL`** —un equipo se elimina lógicamente (`RN-SP-054`), y la acción existe para que las suites que vacían `teams` no tengan que limpiar antes las ventas: la lección de `product_links`— e `ix_movement_details_team` sobre `(team_id, movement_id) WHERE team_id IS NOT NULL`, **parcial** como el del vendedor. **No rellena nada**: a la fecha de cada venta anterior ningún director tenía equipo, y lo hace `RF-MV-058` cuando administración los haya asignado.
2. **Cierra las pertenencias vigentes de los managers**: `UPDATE team_members SET ended_at = now(), updated_at = now() WHERE ended_at IS NULL AND` el miembro porta el rol vendedor de **la cúspide** —el vendedor cuyo rol padre no es vendedor (`RN-SP-019`), hoy `MANAGER`—, por la forma de la jerarquía y no por el código, como `RN-SP-051`. **Con fecha de fin y no borrando**: es historial (`RN-SP-052`). Hasta `V99` la cúspide era lo único que se podía asignar, de modo que en la práctica cierra todas las vigentes. **Va antes del índice siguiente**, que con dos managers en un equipo no se podría crear.
3. **`uq_team_members_equipo_vigente`**: `CREATE UNIQUE INDEX … ON team_members (team_id) WHERE ended_at IS NULL` —**un director vigente por equipo** (`RN-SP-052`)—, con la construcción de `uq_team_members_vigente`. Respalda la carrera de dos asignaciones simultáneas; la violación la traduce a `409` [`RF-SP-069`](../../sp/069-asignar-miembros-a-equipo/plan.md).
4. **Los cinco equipos** —Principal, Legendary, Elite, Prime y Master—, `ACTIVO`, con identificadores fijos, **sin pisar** uno no eliminado cuyo nombre normalizado coincida (`INSERT … SELECT … WHERE NOT EXISTS` con la expresión de `uq_teams_name`): un entorno donde administración ya creó «Elite» conserva el suyo.
5. **`movements:fill-line-teams`** (`01a10e82-9000-7206-9c4f-5e7ad700006a`, `RF-MV-058`) a `SUPERADMIN` y `ADMIN`, explícito. **No es sensible** (`requires_recent_mfa` en falso): la lista la confirmó el responsable el 06-10-2026, y rellenar oficinas no mueve dinero. El catálogo pasa a **234** y `ADMIN` a **232**, contando la `V96` de cuentas de broker.

**Lo que `V99` no hace**: tocar `RN-SP-051` en el caso de uso de asignar, ni `RN-SP-055` al retirar roles. Eso es de [`RF-SP-069`](../../sp/069-asignar-miembros-a-equipo/plan.md), [`RF-SP-031`](../../sp/031-retirar-roles-usuario/plan.md) y [`RF-SP-029`](../../sp/029-eliminar-usuario/plan.md).

**Alternativa descartada: calcular la oficina al leer**, con el recorrido de hoy. Es la respuesta equivocada después de un traslado, que es justo el caso que pidió el responsable (`requirements/mv.md` §4.13). **Y descartada: guardar la oficina en la cabecera.** La oficina es del vendedor, y el vendedor es de la línea (`RN-MV-003`): el día que una venta lleve dos vendedores, llevará dos oficinas.

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
| `modules/system/teams/application` | `SellerTeamLookup` | **Nuevo, dentro de `SP`** (09-10-2026) | La oficina de un vendedor en un instante, y la de varios ahora. Ver §2.8 |
| `modules/system/teams/domain/repository` | `JpaSellerTeamLookup` | **Nuevo, dentro de `SP`** (09-10-2026) | Una `WITH RECURSIVE` por pregunta |
| `domain/models` | `MovementLine` | **Modificado** (09-10-2026) | Gana `teamId`; sin vendedor, sin oficina; `team_id` en la instantánea |
| `domain/service` | `RegisterSaleService` | **Modificado** (09-10-2026) | `teamAt(vendedor, ocurrioEn)` tras la atribución |
| `domain/repository` | `JpaMovementRepository` | **Modificado** (09-10-2026) | `insertarLineas` escribe `team_id`; `findTeamNames` para la respuesta |

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
| El estado del cliente y **quién es su vendedor** | `SP` — `ClientCatalog`, nuevo | `users`, `client_sellers` y `user_supervisors` son suyas; el vendedor de un cliente es su principal —la fila `REGISTRO`— desde el 18-09-2026, y hasta entonces fue su superior comercial con la rama de consumidor de `RN-SP-020` |
| El nivel de membresía vigente del cliente | `SP` — **`CurrentMembershipLookup`, que YA EXISTE** | `user_products` —`user_memberships` hasta el 23-09-2026— es suya, y desde `RF-PM-007` · `T-01` ya publica esta lectura con su borde fijado por prueba. **Enmendado el 04-09-2026** (`tasks.md` §2.2): declararla otra vez en `ClientCatalog` habría creado la segunda definición de «vigente», que es el defecto que aquel puerto existe para evitar |

**Las dos consultas a `PM` se hacen por lote y no por línea.** Una venta de cinco productos que preguntara cinco veces cruzaría la frontera cinco veces para lo mismo: es una `N+1` que no se ve porque cada llamada es un método Java, y que aparece entera en el registro de sentencias.

!!! warning "La comprobación de nivel se escribe aunque hoy la oferta ya la garantice"

    `RF-PM-007` ofrece solo lo que está por encima del nivel actual, de modo que `RN-MV-007` —el producto está en la oferta— **hoy implica** `RN-MV-006` —el upgrade sube—. Comprobar las dos parece redundante, y lo es.

    **Se escriben las dos igualmente**, y el motivo es de quién manda sobre qué. La oferta es una decisión de `PM` y puede ampliarse —el día que se vendan renovaciones del mismo nivel, por ejemplo—; que **una venta no baje a nadie de nivel** es una regla de `MV`, y no puede depender de que otro módulo siga tomando la misma decisión que hoy.

    Es lo que mantiene `EX-005` alcanzable: hoy no se llega por la oferta, y se llegaría el día siguiente a que `PM` la ampliara.

!!! success "Y el 07-09-2026 se amplió, exactamente como este aviso previó"

    `PM` abrió el catálogo a la **renovación** —`X → X`, `requirements/pm.md` §5.2.3— y la oferta pasó a coincidir por **origen**, de modo que un producto de la misma membresía **sí llega** ahora a `RN-MV-006`.

    La comprobación no se borra: **estrecha**. Pasa de rechazar `destino >= nivelActual` a rechazar `destino > nivelActual` — el mismo nivel se admite y el inferior sigue sin admitirse. Lo que este aviso defendía era justo eso: que la regla siguiera viva y en `MV` para poder cambiarla **aquí** el día que `PM` moviera su oferta, sin que nadie tuviera que ir a buscarla.

!!! warning "El 03-10-2026 la regla gana una mitad, y vuelve a cambiarse aquí"

    `PM` deja de admitir el salto ([`requirements/pm.md`](../../../requirements/pm.md) §5.2.17) y `RN-MV-006` lo rechaza también al vender. La comprobación vive donde vivía —`SaleRules.verificarQueSube`, que desde el 17-09-2026 comparten esta entrada y la compra de paquetes— y gana **una condición**: además de `destino > nivelActual` —el descenso—, rechaza `destino < nivelActual - 1` —el salto—. Entre las dos queda exactamente lo que se admite: el mismo nivel y el inmediatamente superior. Se escribe `- 1` y no «el siguiente de la cadena» porque la cadena no tiene huecos (`RN-SP-007`, `RN-SP-008`): no hace falta leer ninguna fila más.

    **Mismo código, otro mensaje.** Las dos mitades son `RN-MV-006`, y el que llama reacciona igual —no se vende—, de modo que un `EX` nuevo obligaría a cada entrada y al frontend a reconocer un segundo código para la misma decisión. El mensaje sí distingue, porque quien lo lee necesita saber si lo que compra baja o salta.

    **Se compara con la membresía vigente, no con el origen del producto.** Por la tienda coinciden; por el hotlink no, y la regla es de quien compra. **Sin membresía vigente sigue sin rechazarse**, y **al confirmar no se repite**: `RN-MV-029` mira solo el descenso.

    **Y otra vez la oferta lo garantiza antes**: `RF-PM-007` deja de publicar los saltos ya registrados, de modo que por esta entrada el salto se ve como `EX-004`. Es el mismo caso que el primer aviso de esta sección, y se resuelve igual: la prueba que alcanza `EX-005` es unitaria, con la oferta simulada (`tasks.md` §1.5).

## 4. Contrato de API

`POST /api/v1/movements` · `201 Created`, con `Location`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-007`: lo que se ve **mirando la petición** — falta el comprador, no hay líneas, cantidad no positiva, producto repetido, fecha futura |
| `403` | Sin el permiso `movements:create` |
| `409` | Lo que solo se sabe **después de resolver**: cuenta en `FTD_PENDIENTE` (`EX-002`), producto fuera de la oferta (`EX-004`), upgrade que baja o salta de nivel (`EX-005`), dos upgrades (`EX-006`), monedas distintas (`EX-008`), cantidad en un upgrade (`EX-009`), método inactivo (`EX-010`) |
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

**Y desde el 09-10-2026, su oficina** (`RN-MV-078`): `team_id` en cada línea, **nulo y presente** cuando no la hay. Por el mismo argumento que el vendedor: es un dato que el actor no envió, sale de una estructura que mañana será otra, y sin él «¿por qué esta venta cuenta en esta oficina?» solo se respondería reconstruyendo `user_supervisors` y `team_members` de aquel día.

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

**Desde el 09-10-2026 `SP` gana otra interfaz publicada**, `SellerTeamLookup` en `teams` (§2.8), con la misma regla: se añade y no se modifica nada, y la suite de `teams` sigue en verde sin cambios **salvo** lo que `V99` siembra —cinco equipos que las pruebas que cuentan equipos o vacían la tabla tienen que tener en cuenta—.

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
| 7 | **La oficina se calcule con el reloj y no con la fecha del hecho** (09-10-2026) | La venta registrada con fecha anterior a un traslado iría a la oficina de hoy, sin error. Lo cubre `CA-MV-709`, que registra con una fecha anterior al traslado |
| 8 | **Una venta con fecha del hecho anterior a `V99`** encuentre la pertenencia **de un manager**, que estaba vigente en ese instante y `V99` cierra con fecha de hoy (09-10-2026) | La regla, aplicada al pie de la letra, le daría el equipo de ese manager. **No se resuelve en este plan**: está planteado al responsable del proyecto. Mientras tanto la regla se aplica tal cual está escrita |

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
| **El escalón** | Unitaria e integración | `CA-MV-526` en `RegisterSaleServiceTest`, con la oferta simulada dejando pasar el salto —por HTTP la oferta lo excluye y se ve `EX-004`, el argumento de `tasks.md` §3 para `CA-MV-011`—; `CA-MV-527` por HTTP, con un producto de un escalón |
| Auditoría con el vendedor dentro | Integración | `CA-MV-018` |
| **La oficina de cada línea** (09-10-2026) | Integración | `CA-MV-704` a `CA-MV-707` en `RegisterSaleIT`, con la estructura sembrada —agente, director con equipo, manager, vendedor sin director con equipo—, mirando la respuesta, `movement_details.team_id` y la instantánea a la vez |
| **El traslado posterior** (09-10-2026) | Integración | `CA-MV-708`: se registra, **después** se traslada al agente y al director, y la línea no cambia. Sin el traslado posterior la prueba no probaría nada |
| **La fecha del hecho** (09-10-2026) | Integración | `CA-MV-709`: el director cambia de equipo en `T` y se registra hoy una venta con fecha anterior a `T` |
| Sin vendedor, sin oficina (09-10-2026) | Integración | `CA-MV-710`, sobre la venta por validar de `RF-MV-016` |
| El alta por enlace (09-10-2026) | Integración | `CA-MV-711` en `SelfRegistrationIT` |
| **El puerto** (09-10-2026) | Integración | `SellerTeamLookupIT` en `teams`: agente → su director; director → él; manager → vacío; un instante anterior a un traslado; un ciclo sembrado que no cuelga; `currentTeamsOf` en lote, sin entrada para quien no tiene |
| La línea sin vendedor y con oficina (09-10-2026) | Unitaria | `MovementLineTest`: se rechaza al construirla |
| El agregado, sin base de datos | Unitaria | El total como suma de líneas, la composición del código, y que un `Movement` no se puede construir sin líneas |
| **La siembra de permisos** | Integración | Los cuatro existen **y están asociados a `SUPERADMIN` y `ADMIN`** — riesgo 1 |
| Reintento del código | Unitaria | Tres intentos y falla, con el generador forzado a colisionar |

**No hay prueba concurrente en este requerimiento, y su ausencia es una afirmación**: ninguna regla de las que aquí se comprueban puede burlarse con dos peticiones simultáneas, porque **ninguna venta registrada produce efecto alguno**. Las que sí lo pueden ser se prueban en `RF-MV-003`.
