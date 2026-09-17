# TASKS — `RF-PM-001` Registrar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-001` |
| Plan | [`plan.md`](plan.md), aprobado el 26-08-2026 |
| Estado | **Aprobadas** |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |
| Enmendadas | 28-08-2026 — `T-19` a `T-22` por el renombrado a `BOT` y el icono del upgrade; 02-09-2026 — `T-23` a `T-26` por la membresía de **origen**; 07-09-2026 — `T-27` a `T-30` por el **alcance** y la **implementación**, y `T-31` por la **renovación**; 08-09-2026 — `T-33` a `T-37` por el **precio público** y la relajación de `RN-PM-006`; 12-09-2026 — `T-38` porque el segundo precio pasa a ser el **de compra**; 14-09-2026 — `T-39` por el **enlace del video**; 14-09-2026 — `T-40` por el **icono obligatorio en el upgrade** (`RN-PM-034`); 15-09-2026 — `T-41` por el **alcance de cuatro valores** (`RN-PM-019`) |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V39__create_products.sql`: la tabla con sus doce columnas, los cinco `CHECK`, las dos claves foráneas y los tres índices únicos de `plan.md` §2.1 | — | `mvn flyway:info` la lista aplicada; una prueba comprueba que `ck_products_type_target` **rechaza las dos mitades** —upgrade sin destino y bot con destino— y que `status` toma `INACTIVO` por omisión | Hecha |
| `T-02` | Migración `V40__seed_products_permissions.sql`: los cuatro permisos con UUID v7 literales, **y su asociación a `SUPERADMIN` y `ADMIN` en la misma migración** | `T-01` | Prueba de integración: los cuatro permisos existen y **los dos roles los tienen**. Sin esta segunda mitad, `ADMIN` no podría conceder `products:create` y el rechazo no diría que falta una siembra | Hecha |
| `T-03` | `domain/models/Product`: agregado y modelo persistente. Normaliza el código a mayúsculas, recorta nombre y descripción, y valida el formato del código en el dominio | `T-01` | Unitaria sin Spring: `upgrade_oro` se guarda `UPGRADE_ORO`; `1ORO`, `ORO-PLUS` y el vacío se rechazan con `VAL-010` | Hecha |
| `T-04` | `domain/models/ProductType` y `ProductStatus`: dominios cerrados | — | Compila; un valor fuera del dominio no es representable | Hecha |
| `T-05` | **En `SP`**: `MembershipCatalog` con su adaptador, en `modules/system/memberships/application`. Devuelve `Optional<MembershipView>` con `id`, `code`, `name` y `level` | — | Integración: devuelve la membresía sembrada y **vacío** —no excepción— ante un identificador inexistente | Hecha |
| `T-06` | **En `SP`**: `CurrencyCatalog` con su adaptador, en `modules/system/currencies/application`. Devuelve `id`, `code`, `decimalPlaces` y `active` | — | Integración: devuelve `USD` con dos decimales y **activa**; una moneda desactivada llega con `active` en falso, no ausente | Hecha |
| `T-07` | Regla de **ArchUnit**: ninguna clase de `..modules.products..` depende de `..modules.system..domain..` | `T-05`, `T-06` | La regla falla si alguien importa un repositorio o una entidad de `SP`. **Sin ella D-25 es una convención** | Hecha |
| `T-08` | `application`: `RegisterProductRequest` —**sin campo `status`**— y `RegisterProductCommand` | `T-04` | Enviar `status` devuelve `400` por `FAIL_ON_UNKNOWN_PROPERTIES`, no se ignora | Hecha |
| `T-09` | `domain/repository`: puerto `ProductRepository` y adaptador `JpaProductRepository`, con **traducción por nombre de restricción** y `flush` explícito | `T-03` | Integración: el código duplicado produce `409` sobre el campo `code`, y el nombre duplicado sobre `name`. **Nunca por el texto del driver** | Hecha |
| `T-10` | `domain/service/RegisterProductService` con el orden de verificación de `plan.md` §4 | `T-05`, `T-06`, `T-08`, `T-09` | Integración: cada rechazo llega con su código y **no se registra nada** | Hecha |
| `T-11` | Auditoría: evento `CREATE` con el estado inicial completo, en la misma transacción | `T-10` | `audit_change_log` contiene el evento con las ocho claves, precio incluido (`CA-PM-011`) | Hecha |
| `T-12` | Serialización del precio **con los decimales de su moneda**, en un componente compartido por las cuatro respuestas del módulo | `T-06` | Unitaria: `49.9900` sobre una moneda de dos decimales sale `49.99`; sobre una de cero, `50` | Hecha |
| `T-13` | `interfaces/ProductController`: `POST /api/v1/products` con `products:create` declarado sobre el método, y `Location` en la respuesta | `T-10`, `T-12` | Prueba de API: `201` con cabecera, y `403` sin el permiso | Hecha |
| `T-14` | Pruebas de API de los doce criterios de `spec.md` §12 | `T-13` | La suite cubre `CA-PM-001` a `CA-PM-012` y `CA-PM-068` a `CA-PM-071`, con sus estados y sus `error_code` | Hecha |
| `T-15` | Prueba concurrente: **dos altas simultáneas con el mismo código** | `T-13` | Una queda y la otra recibe `409`. **No basta la verificación previa**: la garantía es el índice único | Hecha |
| `T-16` | Documentación OpenAPI del endpoint, con los ocho campos, el `201` y los estados `400`, `403`, `409` y `422` | `T-14` | `OpenApiContractIT` regenera el contrato y la prueba comprueba que declara el endpoint | Hecha |
| `T-17` | Actualizar la matriz de `docs/requirements.md` y anotar en `requirements/sp.md` que `SP` publica las dos interfaces | `T-14` | La fila de `RF-PM-001` refleja el estado y `SP` deja constancia de lo que publica | Hecha |
| `T-18` | **Vigencia**: la columna en `T-01`, el campo opcional en el DTO, en el agregado y en la respuesta | `T-08`, `T-13` | Registrar **sin** vigencia deja un producto que no caduca (`CA-PM-092`); cero, negativa o no entera se rechazan con `VAL-011` (`CA-PM-093`) | Hecha |
| `T-19` | Migración `V43__rename_bot_type_and_add_product_icon.sql`: los dos `CHECK` que nombran `SERVICIO` caen, se traducen las filas a `BOT`, entra `icon varchar(50)` y se reponen las restricciones —incluidas `ck_products_icon_solo_upgrade` y `ck_products_icon_format` | `T-01` | Flyway aplica sobre una base con productos de los dos tipos y ninguno queda con `type = 'SERVICIO'`; un `INSERT` directo de un bot con icono lo rechaza el `CHECK` | **Hecha el 28-08-2026** |
| `T-20` | `ProductType.BOT`, y el icono en el agregado, el DTO de alta, el mandato y las tres respuestas. `Product.verificarTipoEIcono` junto a su hermana, y la normalización a minúsculas en un solo sitio | `T-19` | `ProductTest` cubre las dos mitades: el bot con icono da `VAL-013` y el upgrade sin icono se construye sin queja. El icono se guarda recortado y en minúsculas | **Hecha el 28-08-2026** |
| `T-21` | Pruebas de API del icono en el alta: normalización, opcionalidad, forma inválida y el rechazo en el bot | `T-20` | `CA-PM-096` a `CA-PM-098` y `VAL-012` en `ProductsIT` | **Hecha el 28-08-2026** |
| `T-22` | Contrato y documentación: `openapi.json` y `openapi.yaml` regenerados, `requirements/pm.md` §5.1 y §10, y esta tripleta | `T-21` | El contrato publicado dice `BOT` y declara `icon`; no queda ningún `SERVICIO` en el contrato | **Hecha el 28-08-2026** |
| `T-23` | Migración `V53__products_source_membership.sql`: la columna, el relleno con `BECA`, `ck_products_type_target` reescrita, `fk_products_source_membership`, `ck_products_origen_distinto` y `uq_products_upgrade_target` sobre la **pareja** (`plan.md` §2.3) | `T-01` | Flyway la aplica sobre una base con upgrades existentes y todos quedan con origen; un `INSERT` directo de un upgrade sin origen lo rechaza el `CHECK`, y dos upgrades activos hacia `ORO` desde orígenes distintos **conviven** | **Hecha el 02-09-2026** |
| `T-24` | El origen en el agregado, el DTO de alta, el mandato y las tres respuestas. `Product.verificarTipoYDestino` pasa a `verificarTipoYMembresias` y comprueba **las cuatro** mitades de `RN-PM-002` más la igualdad de `RN-PM-017` | `T-23` | `ProductTest` cubre las cuatro: upgrade sin origen y sin destino con `VAL-007` sobre el campo que falta, bot con cualquiera de las dos con `VAL-008`, y origen igual a destino con `VAL-014` | **Hecha el 02-09-2026** |
| `T-25` | La mitad de `RN-PM-017` que necesita el `level` de dos filas, en `RegisterProductService.verificarOrigen`, con el orden moneda → destino → **origen** → unicidad | `T-24` | Integración: origen por encima del destino da `422` con `EX-006` y `VAL-014`; el origen inexistente da `422` con `EX-002` **sobre `sourceMembershipId`**, distinguible del destino inexistente | **Hecha el 02-09-2026** |
| `T-26` | El origen en la lectura: filtro del listado, proyección de `ProductRow`, el segundo `LEFT JOIN` y el detalle. Y las pruebas de API de `CA-PM-101` a `CA-PM-105` | `T-25` | La suite de `PM` en verde con los criterios nuevos, incluida la premisa de `CA-PM-102` —la cadena tiene dos eslabones entre origen y destino—, y el listado sigue costando **dos** sentencias con los seis filtros puestos | **Hecha el 02-09-2026** |
| `T-27` | Migración `V59__products_scope_and_implementation.sql`: las dos columnas en **tres pasos** —añadir nulas, rellenar con `TIENDA` y `MANUAL`, marcar `NOT NULL`—, **sin `DEFAULT`**, y los dos `CHECK` (`plan.md` §2.4) | `T-01` | Flyway la aplica sobre una base con productos de los dos tipos y **ninguno queda nulo**; un `INSERT` directo sin alcance falla por `NOT NULL` y otro con `scope = 'TIENDAS'` falla por `ck_products_scope` | **Hecha el 07-09-2026** |
| `T-28` | `ProductScope` y `ProductImplementation` como dominios cerrados, las dos columnas en el agregado, en `RegisterProductRequest` con `@NotNull` y en `RegisterProductCommand` | `T-27` | Un valor fuera del dominio devuelve `400` al deserializar, y la ausencia devuelve `400` **nombrando el campo** (`VAL-015`, `VAL-016`) | **Hecha el 07-09-2026** |
| `T-29` | Las dos en la **instantánea** del agregado y en las cuatro respuestas del módulo —alta, listado, detalle y oferta— | `T-28` | El evento de creación de `audit_change_log` las lleva (`CA-PM-114`), y las cuatro respuestas las devuelven **siempre presentes** | **Hecha el 07-09-2026** |
| `T-30` | Pruebas de API de `CA-PM-110` a `CA-PM-114`, incluida la del **bot con `HOTLINKS` y `MANUAL`**, que es la que verifica que ninguna de las dos depende del tipo | `T-29` | La suite de `PM` en verde con los cinco criterios nuevos | **Hecha el 07-09-2026** |
| `T-31` | Migración `V61__products_admite_renovacion.sql`: **retira `ck_products_origen_distinto`** (`plan.md` §2.5). En el agregado, `Product.verificarTipoYMembresias` deja de rechazar `origen == destino`; en el caso de uso, la comparación pasa de `<=` a `<` | `T-23` | Un `INSERT` directo de `BECA → BECA` **entra**; el alta por API admite la renovación (`CA-PM-125`) y sigue rechazando el descenso con `EX-006` y `VAL-014` (`CA-PM-104`) | **Hecha el 07-09-2026** |
| `T-32` | El **color** en `MembershipView` —el puerto de `SP`— y en `ProductResponse.MembershipRef`, que comparten las cinco respuestas del módulo | `T-05` | `CA-PM-141`. **Aditivo**: quien ya consume el puerto sigue leyendo los cuatro campos que leía | **Hecha el 07-09-2026** |
| `T-33` | Migración `V67__products_precio_publico.sql`: la columna `public_price`, **el `DROP` de `ck_products_price_positive`** y los dos `CHECK` nuevos de `plan.md` §2.6 | — | `flyway:info` la lista aplicada. Un `INSERT` con `price = 0` **entra**; uno con `public_price` negativo se rechaza, y **con nulo entra** — que es la rama `IS NULL` explícita haciendo su trabajo | **Hecha el 08-09-2026** |
| `T-34` | El campo en el agregado y en el contrato: `Product.publicPrice` con su mutador en `update`, `RegisterProductRequest`/`Command`, y las **tres** respuestas de administración —`ProductResponse`, `ProductItem`, `ProductDetailResponse`— con la escala de la moneda | `T-33` | `CA-PM-145`, `CA-PM-146`: informado sale con los decimales de su moneda; **ausente y nulo terminan los dos en la misma fila** y salen `null` **presentes** | **Hecha el 08-09-2026** |
| `T-35` | La validación de los **dos** importes en un solo sitio: no negativo en el DTO y en el caso de uso, y `ProductPrice.cabeEn` contra la moneda **para cada uno**, con el `field` del que incumple | `T-34` | `CA-PM-147`, `CA-PM-148`, `CA-PM-149`. El error de un público con decimales de más **nombra `publicPrice`**, no `price`, aunque el del sistema sí quepa | **Hecha el 08-09-2026** |
| `T-36` | El precio público en la **instantánea de auditoría** (`Product.instantanea`), como texto y nulo cuando no se declaró | `T-34` | `CA-PM-150`. El evento `CREATE` trae la clave `public_price`, y la trae **también** cuando vale nulo — sin ella no habría con qué contrastar una reclamación por «lo vi a otro precio» | **Hecha el 08-09-2026** |
| `T-37` | **En `CM`**: `ProductCommissionCapGuard` deja de dividir a ciegas. Sobre precio cero, un valor fijo **mayor que cero** ocupa más del 100 % y se rechaza con el mensaje de `RN-CM-019`; uno de cero ocupa cero | `T-33` | `CA-CM-115` y `CA-CM-116`. **Sin esta tarea, asociar una tasa fija a un producto gratuito es un `500`** — la clase lo daba por imposible **por escrito**, citando la restricción que `T-33` relaja | **Hecha el 08-09-2026** |
| `T-38` | **El segundo precio pasa a ser el de compra** (`plan.md` §2.7): migración `V86__products_precio_de_compra.sql` —renombra `public_price` a `purchase_price` y su `CHECK`, y **vacía la columna** porque lo que había no era un costo—; `purchasePrice` sustituye a `publicPrice` en `Product`, `RegisterProductRequest`/`Command`, las tres respuestas de administración, la instantánea (`purchase_price`) y los mensajes de `VAL-004`/`VAL-005`; y la prosa de las `@Operation` deja de decir «se anuncia» | `T-36` | `CA-PM-146` a `CA-PM-148` y `CA-PM-150` con el nombre nuevo. Enviar `publicPrice` es `400` por propiedad desconocida. `flyway:info` lista `V86` aplicada y `\d products` no tiene `public_price`. **El contrato regenerado no declara `publicPrice` en ningún esquema** | **Hecha el 12-09-2026** |
| `T-39` | **El enlace del video** (`plan.md` §2.8): migración `V89__products_video_url.sql` —`video_url varchar(500) NULL`, `ck_products_video_url_format` con la rama `IS NULL` delante, y el comentario—; `videoUrl` en `Product` —campo, fábrica, instantánea `video_url`, `normalizarEnlaceDeVideo` con `VAL-017`—, en `RegisterProductRequest`/`Command` y en `ProductResponse`; y la prosa de la `@Operation` del alta dice qué es y que no se sigue | `T-38` | `CA-PM-219` a `CA-PM-222` en `ProductsIT`, y la unitaria de la forma en `ProductTest`. `\d products` muestra la columna y el `CHECK`. **El contrato regenerado declara `videoUrl` en el cuerpo del alta y en `ProductResponse`** | **Hecha el 14-09-2026** |
| `T-40` | **El icono obligatorio en el upgrade** (`plan.md` §2.9, enmienda de `RF-PM-014`): `Product.create` rechaza un upgrade sin icono con `VAL-018` nombrando `icon`; `ProductResponse` gana `coverImageUrl` —siempre nulo aquí— y la instantánea `cover_image_id`; `CA-PM-098` se retira de `ProductsIT` y entran `CA-PM-230` y `CA-PM-231`; la prosa de la `@Operation` del alta dice que el icono es obligatorio en el upgrade mientras no haya portada, y que la portada se sube después | `T-39`, `RF-PM-014 · T-05` | `CA-PM-230`, `CA-PM-231` en `ProductsIT`; la unitaria en `ProductTest`: upgrade sin icono lanza, bot sin icono no | **Hecha el 14-09-2026** |
| `T-41` | **El alcance de cuatro valores** (`RN-PM-019` reescrita): migración `V92__products_scope_cuatro_valores.sql` —`UPDATE` `HOTLINKS` → `AMBOS`, y `ck_products_scope` reemplazada—; `ProductScope` con `TIENDA`, `HOTLINK`, `AMBOS`, `NINGUNO`; la prosa de la `@Operation` del alta dice qué significa cada uno | `T-40` | `CA-PM-348` en `ProductsIT`; `CA-PM-113` con `AMBOS` | **Hecha el 15-09-2026** |

## 2. Orden de ejecución

`T-01` y `T-02` primero: sin tabla ni permisos no hay nada que probar de extremo a extremo. `T-05`, `T-06` y `T-07` son independientes del resto y conviene escribirlas pronto, porque **son la parte que toca otro módulo** y la que más riesgo tiene de descubrir algo.

`T-03`, `T-04`, `T-08` y `T-09` no dependen entre sí. `T-10` es la que las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-001`, `CA-PM-002` | `T-10`, `T-13`, `T-14` |
| `CA-PM-003`, `CA-PM-004` | `T-10`, `T-14` |
| `CA-PM-005`, `CA-PM-006` | `T-06`, `T-10`, `T-14` |
| `CA-PM-007` | `T-06`, `T-10` |
| `CA-PM-008` | `T-09`, `T-14` |
| `CA-PM-010` | `T-05`, `T-10` |
| `CA-PM-011` | `T-11` |
| `CA-PM-012` | `T-13` |
| `CA-PM-068` | `T-01`, `T-08` |
| `CA-PM-069` | `T-01`, `T-09` |
| `CA-PM-070` | `T-03` |
| `CA-PM-071` | `T-10` |
| `CA-PM-101`, `CA-PM-102` | `T-23`, `T-24`, `T-26` |
| `CA-PM-103`, `CA-PM-105` | `T-24`, `T-26` |
| `CA-PM-104` | `T-24`, `T-25`, `T-26`, `T-31` |
| `CA-PM-125` | `T-31` |
| `CA-PM-141` | `T-32` |
| `CA-PM-145`, `CA-PM-146` | `T-33`, `T-34`, `T-38` |
| `CA-PM-147`, `CA-PM-148` | `T-35`, `T-38` |
| `CA-PM-149` | `T-33`, `T-35` |
| `CA-PM-150` | `T-36`, `T-38` |
| `CA-PM-110`, `CA-PM-111` | `T-28`, `T-30` |
| `CA-PM-112` | `T-28`, `T-30` |
| `CA-PM-113` | `T-29`, `T-30` |
| `CA-PM-114` | `T-29`, `T-30` |
| `CA-PM-219` a `CA-PM-222` | `T-39` |
| `CA-PM-230`, `CA-PM-231` | `T-40` |
| `CA-PM-348` | `T-41` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-05` y `T-06` escriben código **en paquetes de `SP`**, un módulo que ya tiene su suite en verde. Cualquier regresión allí es responsabilidad de este requerimiento | 26-08-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

El requerimiento no está terminado hasta cumplir **todas** las condiciones de la constitución §16:

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **incluida la suite de `SP` sin cambios**.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] Los endpoints nuevos declaran su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
