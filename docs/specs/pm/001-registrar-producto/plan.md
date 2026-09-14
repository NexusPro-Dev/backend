# PLAN — `RF-PM-001` Registrar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-001` |
| Especificación | [`spec.md`](spec.md) v0.5.0 |
| `spec.md` aprobada el | 26-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Enmendado el | 27-08-2026 — `RN-PM-015`; 02-09-2026 — la membresía de **origen** (`RN-PM-017`, `RN-PM-018`); 07-09-2026 — **el alcance y la implementación** (`RN-PM-019`, `RN-PM-020`), §2.4, y **la renovación** —el origen puede ser el destino (`RN-PM-017`)—, §2.5; 08-09-2026 — **el segundo precio, el público** (`RN-PM-023`) y **`RN-PM-006` relajada**, §2.6; 12-09-2026 — **el segundo precio pasa a ser el de COMPRA** (`RN-PM-023`, `RN-PM-024`), §2.7; 14-09-2026 — **el enlace de un video** (`RN-PM-032`), §2.8 y §4 |
| Fecha de aprobación | 26-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye** lo que `spec.md` describe: esquema, componentes, contrato, transacciones y pruebas. Nada de aquí puede contradecir la especificación; si al escribirlo aparece un defecto en ella, se devuelve a su compuerta (Art. I.7).

---

## 1. Enfoque

Es el requerimiento que **funda el módulo**: crea `products`, siembra sus cuatro permisos y estrena las interfaces que `SP` publica al cerrarse **D-25**. Todo lo demás de `PM` se apoyará en lo que entre aquí.

El caso de uso es un alta con validación condicional por tipo, y su forma ya existe en el sistema: es la de `RF-SP-024`, que aplica reglas condicionales en los dos sentidos dentro de una sola operación. Lo específico de este requerimiento es que **la mitad de sus validaciones se resuelve contra otro módulo**, y esa es la parte que hay que construir con cuidado.

**El producto nace `INACTIVO`** (`RN-PM-012`), lo que simplifica el alta más de lo que parece: no hay que comprobar `RN-PM-004` —«un solo upgrade activo por pareja origen→destino»—, porque nada de lo que entre por aquí queda activo. Esa comprobación vive entera en `RF-PM-005`.

## 2. Cambios de esquema

**Dos migraciones, cada una con un trabajo.** Es la misma separación que `SP` hizo entre `V2__create_permissions` y `V3__seed_permissions`: crear una tabla y poblar un catálogo son cosas distintas, y mezclarlas hace que un fallo de siembra parezca un fallo de esquema.

### 2.1 `V39__create_products.sql`

| Tabla | Cambio | Detalle |
|---|---|---|
| `products` | Crea | Campos de [`requirements/pm.md` §10.1](../../../requirements/pm.md) |

```
id                    uuid          PRIMARY KEY
code                  varchar(50)   NOT NULL
type                  varchar(30)   NOT NULL
name                  varchar(150)  NOT NULL
description           text          NULL
target_membership_id  uuid          NULL  → memberships(id)
price                 numeric(14,4) NOT NULL
validity_days         integer       NULL
currency_id           uuid          NOT NULL → currencies(id)
status                varchar(20)   NOT NULL DEFAULT 'INACTIVO'
created_at            timestamptz   NOT NULL DEFAULT now()
updated_at            timestamptz   NOT NULL DEFAULT now()
deleted_at            timestamptz   NULL
```

Restricciones e índices:

| Nombre | Definición | Por qué |
|---|---|---|
| `ck_products_code_format` | `code ~ '^[A-Z][A-Z0-9_]*$'` | `RN-PM-013`, `VAL-010`. Mismo formato que `roles` y `memberships` |
| `uq_products_code` | `UNIQUE (code)` — **restricción de tabla, total** | `RN-PM-013`. **No es un índice parcial**, al revés que el nombre: el código no se libera al eliminar |
| `uq_products_name` | `CREATE UNIQUE INDEX … ON products (f_unaccent(lower(name))) WHERE deleted_at IS NULL` | `RN-PM-005`. Índice **funcional y parcial**: una restricción de tabla no admite expresión ni condición. `f_unaccent` existe desde `V1` y está declarada `IMMUTABLE` precisamente para poder indexarse |
| `ck_products_type` | `type IN ('UPGRADE_MEMBRESIA','BOT')` | `RN-PM-001` |
| `ck_products_status` | `status IN ('ACTIVO','INACTIVO')` | `RN-PM-009` |
| `ck_products_type_target` | `(type = 'UPGRADE_MEMBRESIA' AND target_membership_id IS NOT NULL) OR (type = 'BOT' AND target_membership_id IS NULL)` | `RN-PM-002`, en los dos sentidos. **`V53` la reescribe** para que cubra también el origen — ver §2.3 |
| `ck_products_price_positive` | `price > 0` | `RN-PM-006`. **`V67` la retira y la sustituye** por `ck_products_price_no_negativo` con `price >= 0` — ver §2.6 |
| `ck_products_validity_positive` | `validity_days IS NULL OR validity_days > 0` | `RN-PM-015`. La rama `IS NULL` va **explícita**: la comparación sola también admitiría el nulo —un `CHECK` que evalúa a `NULL` acepta la fila—, y se escribe para que ese permiso sea deliberado |
| `ck_products_name_length` | `length(name) <= 150` implícito en el tipo; `description` con `CHECK` de 1000 | Sin cota, el listado de `RF-PM-002` devolvería respuestas de tamaño impredecible |
| `uq_products_upgrade_target` | `CREATE UNIQUE INDEX … ON products (target_membership_id) WHERE type = 'UPGRADE_MEMBRESIA' AND status = 'ACTIVO' AND deleted_at IS NULL` | `RN-PM-004`. Se declara **aquí**, con la tabla, aunque **solo `RF-PM-005` pueda violarla**: el esquema es de quien crea la tabla. **`V53` lo rehace sobre la pareja `(origen, destino)`** — ver §2.3 |
| `fk_products_target_membership` | `target_membership_id → memberships(id)` | `RN-PM-003` |
| `fk_products_currency` | `currency_id → currencies(id)` | `RN-PM-008` |

!!! important "Dos advertencias que este proyecto ya pagó"

    **`ck_products_type_target` no puede evaluar a `NULL`.** Sus dos ramas son predicados `IS NULL` / `IS NOT NULL`, que devuelven siempre verdadero o falso. La precaución no es teórica: `ck_deletion_reason` se escribió con un `OR` cuyo lado nulo evaluaba a `NULL`, y **un `CHECK` que devuelve `NULL` acepta la fila** — la restricción existía y no restringía nada.

    **Los dos índices únicos son parciales, y un índice parcial no admite `DEFERRABLE`**, que es propiedad de una *restricción* y no de un índice. Morderán en la sentencia que los viole y no en el `COMMIT`; el adaptador los traduce ahí.

!!! warning "Las dos claves foráneas cruzan a `SP`, y no contradicen a D-25"

    `products` referencia `memberships` y `currencies`, que son tablas de otro módulo. La frontera que `modules.md` §7 defiende es la del **código**: `PM` no lee esas tablas ni sus repositorios, y todo dato que necesite entra por las interfaces que `SP` publica. La clave foránea es integridad declarada en el motor (Art. V.6), y hace un trabajo que el puerto no puede hacer: impedir que una fila quede apuntando a una membresía que se borró **por debajo de la aplicación**.

    Su consecuencia práctica: la validación **con mensaje útil** la hace el caso de uso contra el puerto (`EX-002`), y la clave foránea es la red por si acaso. Si saltara ella, sería un `500`, y eso significa que el puerto y la base dejaron de estar de acuerdo — un defecto, no una validación.

### 2.2 `V40__seed_products_permissions.sql`

Siembra los cuatro permisos `products:create`, `products:read`, `products:update` y `products:delete`, con identificadores **UUID v7 literales**, como exige el Art. V.11 y por el mismo motivo que `V3`: deben ser iguales en todos los entornos para que las pruebas los referencien por constante.

**Y los asocia a `SUPERADMIN` y a `ADMIN` en la misma migración.** No es opcional: [`security.md` §4.4](../../../security.md#44-catalogo-de-permisos) lo declara obligación de toda migración que siembre permisos, y **`V7` no puede hacerlo por ella** —asocia el catálogo existente en su momento, y estos permisos aún no existían—. El síntoma de olvidarlo no se parece a la causa: `ADMIN` quedaría incapaz de crear un rol que declare `products:create`, y `RN-SEG-003` lo rechazaría sin decir que lo que falta es una siembra.

Los cuatro van a **ambos roles**: a diferencia de `audit:read-security` y `currencies:update`, que `V7` excluyó de `ADMIN`, aquí no hay ninguno que deba quedar reservado al superadministrador.

Esta migración **no emite auditoría**, igual que `V3`: un permiso no tiene línea de tiempo que reconstruir.

### 2.3 `V53__products_source_membership.sql` — enmienda del 02-09-2026

**El upgrade declara de dónde sale, y no solo a dónde lleva.** Hasta esta migración el origen **se deducía**: podía comprarlo cualquiera por debajo del destino. Esa deducción hacía **imposible el salto** —«subir a `ORO`» era el mismo producto y el mismo precio para quien sube un escalón y para quien sube tres—, y con el origen declarado **cada salto es un producto** con su precio.

| Cambio | Definición | Por qué |
|---|---|---|
| `source_membership_id` | `uuid NULL` | **Sin `NOT NULL` a propósito**: en un bot tiene que estar vacía. Quien exige su presencia es el `CHECK`, que es el único capaz de decir «obligatoria aquí y prohibida allá» |
| Relleno | `UPDATE … SET source_membership_id = (SELECT id FROM memberships WHERE code = 'BECA')` sobre los upgrades existentes | **Es una decisión, no una deducción**: bajo el modelo anterior esos productos **no tenían** origen. Se elige el suelo de la cadena porque conserva la oferta de quien está en `BECA`, que es el caso más común |
| `ck_products_type_target` | Se **reescribe entera** para exigir las dos membresías en el upgrade y prohibirlas las dos en el bot | Añadir un segundo `CHECK` al lado se leería como dos reglas, y `RN-PM-002` es **una** |
| `fk_products_source_membership` | `source_membership_id → memberships(id)` | Lo mismo que su gemela del destino, y por el mismo motivo |
| `ck_products_origen_distinto` | `source_membership_id IS NULL OR source_membership_id <> target_membership_id` | La mitad de `RN-PM-017` que el motor **sí** puede sostener: un upgrade de `BECA` a `BECA` vende nada |
| `uq_products_upgrade_target` | Se rehace sobre **`(source_membership_id, target_membership_id)`** | La versión anterior prohibía **exactamente lo que el origen existe para permitir**: dos upgrades activos hacia `ORO`, uno desde `BECA` y otro desde `PLATINO` |

!!! danger "El coste del relleno, escrito para que nadie lo descubra en producción"

    Dar `BECA` a todos los upgrades existentes significa que **quien esté en `VIP` deja de verlos**. No recibe ningún error: simplemente dejan de ofrecerse, y el catálogo se ve perfectamente bien desde administración. La alternativa considerada —el nivel inmediatamente inferior al destino, derivable de la cadena— estrecha igual y además deja a `BECA` sin nada.

    **Y la migración puede detenerse**: si existe un upgrade cuyo destino es `BECA`, el relleno lo deja apuntando a sí mismo y `ck_products_origen_distinto` la aborta. Es lo correcto — ese producto vendía un descenso llamándolo upgrade, y qué hacer con él es una decisión del negocio, no de una migración.

!!! important "La otra mitad de `RN-PM-017` no cabe en el esquema"

    «El origen está por debajo del destino» obliga a leer el `level` de **dos filas de `memberships`**, y un `CHECK` no consulta otra tabla. Vive en el caso de uso, por el mismo motivo exacto que los decimales de la moneda en `RN-PM-007`.

    Y va con el reparto de `V47` delante: **`level` numera desde la cima** —`ORO` es el 1 y `BECA` el 4—, de modo que «por debajo» es **número mayor**. Escribir la comparación al revés produce un sistema que acepta descensos y rechaza ascensos, y las dos mitades fallan calladas.

### 2.4 `V59__products_scope_and_implementation.sql` — enmienda del 07-09-2026

**Un producto declara hasta dónde se muestra y quién aplica lo que otorga** (`RN-PM-019`, `RN-PM-020`). Dos columnas obligatorias, en los **dos** tipos, y **sin valor por omisión**.

| Cambio | Definición | Por qué |
|---|---|---|
| `scope` | `varchar(20) NOT NULL`, en **tres pasos**: se añade nula, se rellena, y solo entonces se marca `NOT NULL` | Una columna `NOT NULL` no se puede añadir de golpe a una tabla con filas sin darle un `DEFAULT`, y **el `DEFAULT` es justo lo que no queremos** — ver la fila del relleno |
| `implementation` | `varchar(20) NOT NULL`, con la misma secuencia | Lo mismo |
| Relleno | `TIENDA` y `MANUAL` sobre todo lo existente | **Es una decisión, no una deducción**, como el `BECA` de `V53`: bajo el modelo anterior estos productos **no tenían** ni alcance ni implementación. `TIENDA` es el alcance **más corto** y conserva **exactamente** la oferta de hoy; `MANUAL` es la implementación que **no entrega sola** |
| `ck_products_scope` | `scope IN ('TIENDA','HOTLINKS')` | `RN-PM-019` |
| `ck_products_implementation` | `implementation IN ('AUTOMATICA','MANUAL')` | `RN-PM-020` |

!!! danger "Por qué el relleno de la implementación es `MANUAL` y no `AUTOMATICA`"

    El valor por omisión de una migración **es una decisión de negocio disfrazada de detalle técnico**, y aquí las dos opciones no cuestan lo mismo.

    Con `AUTOMATICA`, el día que `RF-MV-003` se construya **todo producto que existía antes de esta migración entregaría solo** — membresías concedidas por productos que nadie revisó, con el cobro hecho y sin que ninguna decisión lo hubiera dicho. El defecto **no falla: entrega**.

    Con `MANUAL`, lo peor que pasa es que alguien tenga que autorizar una entrega que podría haberse aplicado sola. Eso se nota, se corrige con `RF-PM-004` y no deja nada mal concedido detrás.

!!! important "Ninguna de las dos lleva `DEFAULT`, y no es lo mismo que el relleno"

    El relleno lo escribe **la migración, una vez**, sobre lo que ya existe. Un `DEFAULT` lo escribiría **la columna, siempre**, sobre todo lo que venga — y con él, un alta que olvidara declarar el alcance se guardaría sin error y sin que nadie pudiera distinguirla de una que lo declaró. `status` sí lleva `DEFAULT` porque una regla lo exige (`RN-PM-012`); aquí ninguna regla dice cuál es el valor natural, y ese es precisamente el motivo por el que se declara.

    Es la misma forma que `V53` usó con `source_membership_id`: rellenar y no suponer.

### 2.5 `V61__products_admite_renovacion.sql` — enmienda del 07-09-2026

**Cae `ck_products_origen_distinto`**, y es una migración de una sola sentencia con una consecuencia que merece más líneas que el `SQL`.

| Cambio | Definición | Por qué |
|---|---|---|
| `ck_products_origen_distinto` | `DROP CONSTRAINT` | Prohibía `source_membership_id = target_membership_id`, que es **exactamente** lo que la renovación admite (`requirements/pm.md` §5.2.3) |

!!! danger "De `RN-PM-017` no queda NADA en el esquema"

    Esa restricción era **la única mitad de la regla que el motor podía sostener**. La que sobrevive —«el origen no está por encima del destino»— obliga a leer el `level` de **dos filas de `memberships`**, y un `CHECK` no consulta otra tabla: nunca cupo aquí y no va a caber.

    De modo que a partir de esta migración **una regla crítica de este módulo vive entera en `RegisterProductService`**, sin red. Es el mismo reparto que `RN-PM-007` tiene con los decimales de la moneda, con una diferencia que conviene no olvidar: aquel **nunca** tuvo una restricción detrás, y este la pierde. Un `INSERT` directo —una migración, una corrección a mano— puede meter hoy un descenso vendido como upgrade, y nada lo impedirá.

!!! important "No se toca `uq_products_upgrade_target`"

    `(BECA, BECA)` es una pareja como cualquier otra. La unicidad sigue siendo **un producto activo por pareja origen→destino**, de modo que no pueden coexistir dos renovaciones activas de la misma membresía — que es justo lo que `RN-PM-004` existe para evitar: dos precios simultáneos para lo mismo.

**Y el agregado pierde una comprobación sin ganarla en otro sitio.** `Product.verificarTipoYMembresias` rechazaba `origen.equals(destino)` con `VAL-014`; esa comparación **dejó de decir nada**. Quien decide es el caso de uso, que es el único que conoce los dos `level`, y su comparación pasa de `origen.level() <= destino.level()` a `origen.level() < destino.level()`.

### 2.6 `V67__products_precio_publico.sql` — enmienda del 08-09-2026

**Una columna, y dos restricciones donde había una.**

| Cambio | Definición | Por qué |
|---|---|---|
| `public_price` | `numeric(14,4) NULL` | El precio con el que se anuncia (`RN-PM-023`). **La misma forma que `price` porque es el mismo dinero en la misma moneda**; nulo porque «no declara precio público» es un estado distinto de «vale cero» |
| ~~`ck_products_price_positive`~~ | `DROP CONSTRAINT` | Decía `price > 0`, y `RN-PM-006` dejó de exigirlo |
| `ck_products_price_no_negativo` | `CHECK (price >= 0)` | La misma regla con el umbral nuevo. **Cambia de nombre a propósito**: con el viejo, quien lo leyera creería que el cero sigue prohibido |
| `ck_products_public_price_no_negativo` | `CHECK (public_price IS NULL OR public_price >= 0)` | La rama `IS NULL` va **delante y explícita**, por lo mismo que en `ck_products_validity_positive`: un `CHECK` que evalúa a `NULL` **acepta** la fila, y el permiso debe ser deliberado |

!!! warning "Y el número volvió a moverse: se escribió `V65` y acabó en `V67`, el mismo día"

    Esta migración se planificó como `V65` con `V64` aplicada. Antes de escribir una línea de `SQL`, las tasas de cambio (`RF-SP-047`) fijaron `V65` para su tabla y `V66` para sus permisos, y este cambio se corrió al **`V67`**.

    Es **la cuarta vez** que le pasa a este proyecto, y [`modelo-datos.md` v0.23.0](../../../modelo-datos.md) ya lo tenía escrito con todas las letras: **una migración reservada no está reservada**. Quien tenga el `SQL` escrito antes se lleva el hueco, y Flyway deja fuera **sin error y sin aviso** una migración con número por debajo del último aplicado.

!!! important "No hay relleno, y esa es la diferencia con `V53` y `V59`"

    Aquellas migraciones tuvieron que **decidir un valor** para las filas existentes —`BECA` como origen, `TIENDA` y `MANUAL`—, porque las columnas quedaban obligatorias. Aquí no hace falta ninguna decisión: **el nulo ya significa lo correcto** para todo producto ya registrado —«se anuncia con el precio del sistema»—, que es exactamente lo que hacían ayer.

    Es el argumento que sostiene que la columna sea opcional, visto desde la migración.

!!! danger "Relajar un `CHECK` rompe una división en OTRO módulo, y estaba escrito"

    `ProductCommissionCapGuard` de `CM` calcula `fixed_amount ÷ precio` y su Javadoc decía que «el precio nunca es cero — `ck_products_price_positive` lo garantiza desde `V39`». **Esta migración retira esa garantía**, y con un producto gratuito esa división es una excepción aritmética: un `500` en una comprobación de negocio.

    Se corrige en el mismo pase (`RN-CM-019` llevada a su límite: sobre precio cero, cualquier valor fijo mayor que cero pasa del 100 %). Queda anotado aquí porque **el `SQL` es donde el defecto nace**, y quien lea esta migración dentro de un año tiene que poder llegar hasta él.

### 2.7 `V86__products_precio_de_compra.sql` — enmienda del 12-09-2026

**Un renombrado de columna y de su `CHECK`, y nada más — porque lo que cambia es el significado, y eso no se migra.**

| Cambio | Definición | Por qué |
|---|---|---|
| `public_price` → `purchase_price` | `ALTER TABLE … RENAME COLUMN` | El número deja de ser lo que se anuncia y pasa a ser **lo que NEXUS paga por el producto** (`RN-PM-023`, `requirements/pm.md` §5.2.6). Conserva tipo, escala y opcionalidad: la forma era correcta y sigue siéndolo |
| `ck_products_public_price_no_negativo` → `ck_products_purchase_price_no_negativo` | `ALTER TABLE … RENAME CONSTRAINT` | Por lo mismo que `ck_products_price_positive` se renombró con su umbral: un nombre que dice «público» sobre un costo **miente**, y el que lo lea en un error del motor buscará una columna que ya no existe |
| `COMMENT ON COLUMN` | Reescrito | El comentario decía «se anuncia»; ahora dice qué es y qué significa su nulo |

**No es una migración nueva sobre `V67`, y `V67` no se toca.** Reescribir una migración aplicada es el error que Flyway existe para impedir; el renombrado va en su propia migración, con el número que le toque el día que se escriba — que este plan **no reserva**, porque una migración reservada no está reservada (§2.6).

!!! important "No hay relleno, otra vez, y esta vez por un motivo distinto"

    `V67` no rellenó nada porque el nulo ya significaba «se anuncia con el precio del sistema» para todo producto existente. Hoy tampoco se rellena, pero el argumento es otro: **ningún producto de hoy tiene un costo declarado**, porque hasta ayer la columna guardaba otra cosa. Lo que hubiera en ella —un precio anunciado— **no es un costo**, y dejarlo ahí con el nombre nuevo sería mentir con datos. La migración, por tanto, **vacía la columna** (`UPDATE products SET purchase_price = NULL`) antes de que nadie la lea como costo. Es la única sentencia que no es un renombrado, y es la que hace que la columna cambie de significado **vacía del anterior**.

### 2.8 `V89__products_video_url.sql` — enmienda del 14-09-2026

**Una columna opcional, un `CHECK` de forma y un comentario. Sin relleno.**

| Cambio | Definición | Por qué |
|---|---|---|
| `video_url` | `varchar(500) NULL` | **La dirección de un video que presenta el producto, no el video** (`RN-PM-032`). `varchar(500)` y no `text`: un enlace que no cabe en quinientos caracteres no es uno que nadie vaya a escribir a mano, y ensanchar un `varchar` es un `ALTER` de solo metadatos en PostgreSQL. Nulo cuando no hay, **sin `DEFAULT`**: la cadena vacía no es un estado |
| `ck_products_video_url_format` | `CHECK (video_url IS NULL OR video_url ~ '^https?://[^[:space:]]+$')` | Comprueba **la forma y nada más**: esquema `http` o `https`, y ningún espacio. La rama `IS NULL` va **delante y explícita**, como en el icono y en la vigencia. Que el enlace resuelva a algo no es cosa del esquema ni del dominio (`pm.md` §5.2.8) |
| `COMMENT ON COLUMN` | Qué es, qué significa su nulo y **dónde sí se ve** | Al revés que `purchase_price`, este sale en las cuatro lecturas, hotlink sin token incluido — y conviene que quien lea el esquema lo sepa sin abrir el código |

**No hay relleno**, por el motivo más simple de los tres que este plan ya ha dado: ningún producto de hoy tiene video, y el nulo lo dice. **El número `V89` cuenta con que `V87` y `V88` —las reseñas, en construcción el mismo día— se queden como están**; si aquel trabajo cambia de número, este lo sigue. Es la única reserva de número que este plan hace, y la hace porque las dos migraciones se escriben el mismo día en el mismo árbol.

## 3. Componentes afectados

### 3.1 En `PM` — `modules/products`

| Capa | Componente | Responsabilidad |
|---|---|---|
| `domain/models` | `Product` | Agregado y modelo persistente. Normaliza el código y el nombre, y valida su formato |
| `domain/models` | `ProductType`, `ProductStatus` | Dominios cerrados |
| `domain/repository` | `ProductRepository` | Puerto: existencia de código y de nombre, y persistencia |
| `domain/repository` | `JpaProductRepository` | Adaptador. **Traduce las violaciones por nombre de restricción**, nunca por el texto del driver |
| `domain/service` | `RegisterProductService` | Caso de uso, con el orden de verificación de §4 |
| `application` | `RegisterProductRequest`, `RegisterProductCommand`, `ProductResponse` | Entrada y salida |
| `interfaces` | `ProductController` | `POST /api/v1/products` |

### 3.2 En `SP` — las tres interfaces que publica (D-25)

Las escribe este requerimiento, en paquetes de `SP` (`architecture.md` §15.2). `RF-PM-001` necesita dos de las tres; la tercera la trae `RF-PM-007`.

| Paquete | Componente | Devuelve |
|---|---|---|
| `modules/system/memberships/application` | `MembershipCatalog` + adaptador | `Optional<MembershipView>` con `id`, `code`, `name`, `level` |
| `modules/system/currencies/application` | `CurrencyCatalog` + adaptador | `Optional<CurrencyView>` con `id`, `code`, `decimalPlaces`, `active` |

**Son registros planos, no entidades.** Devolver `Membership` o `Currency` filtraría JPA a otro módulo y le daría con qué escribir. **La ausencia es `Optional.empty()`**, no una excepción: qué `4xx` produce lo decide `PM`, que es quien tiene el contrato.

### 3.3 La regla de ArchUnit

Se añade a `LayerRulesTest`: **ninguna clase de `..modules.products..` depende de `..modules.system..domain..`**. Sin ella, D-25 es una convención, y las convenciones se saltan sin que nada falle — el mismo mecanismo con el que se sujeta `RN-SEG-010`.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/products` | Registra un producto, siempre inactivo |

**Petición**

```json
{
  "code": "UPGRADE_ORO",
  "type": "UPGRADE_MEMBRESIA",
  "name": "Ascenso a Oro",
  "description": "Acceso a los contenidos de nivel oro.",
  "sourceMembershipId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d24",
  "targetMembershipId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d20",
  "price": 49.99,
  "purchasePrice": 30.00,
  "videoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "validityDays": 30,
  "currencyId": "01a03336-6d00-7001-9c4f-5e7ad3000001"
}
```

- **No existe campo `status`**, y el DTO se deserializa con `FAIL_ON_UNKNOWN_PROPERTIES` activo: enviarlo devuelve `400` y no se ignora en silencio (`CA-PM-068`). Es lo mismo que `RF-SP-001` hizo con `status` e `isSystem`, y es lo que hace verificable que **el estado inicial no se pueda forzar desde fuera**.
- `code` se **recorta y se pasa a mayúsculas** antes de validar; `name` y `description` se recortan. Sin el recorte, `"Ascenso "` y `"Ascenso"` serían dos nombres distintos para `uq_products_name`.
- `price` llega como **número**. La escala admisible **no la fija el DTO**, la fija la moneda (`RN-PM-007`), y por eso se valida en el caso de uso y no con una anotación.
- **`purchasePrice` es opcional, y ausente y nulo significan lo mismo**: no se conoce el costo todavía (`RN-PM-023`). Aquí **sí** se aparta de `scope` e `implementation` —que son obligatorias y donde ausente y nulo significan «falta»—, y el motivo es que **su omisión no deja ninguna decisión sin tomar**: un producto se registra antes de comprarse, y el costo se declara cuando se conoce (`RF-PM-004`). **Se llamó `publicPrice` hasta el 12-09-2026** y significaba otra cosa; el nombre viejo es una propiedad desconocida y devuelve `400`.
- **El DTO acota de los dos importes lo que es cierto para cualquier moneda** —no negativo y hasta cuatro decimales, que es lo que la columna admite— y **nada más**. Los decimales de verdad los decide el caso de uso contra `currencies.decimal_places`, para los **dos**.
- **`videoUrl` es opcional en los dos tipos**, y ausente y nulo significan lo mismo: no tiene video (`RN-PM-032`). Se **recorta** antes de validar —`" "` es un enlace ausente, no uno inválido— y **no se normaliza nada más**: ni mayúsculas, ni barra final, ni parámetros; lo que se guarda es lo que se escribió, porque un enlace que el sistema «arregla» puede dejar de resolver. **La forma se comprueba en el dominio con `VAL-017`** —URL absoluta `http` o `https`, sin espacios, hasta 500 caracteres— y no con una anotación: `@URL` de Hibernate Validator admite cualquier esquema y no distingue una relativa, y `@Pattern` no puede decir «hasta 500» sin repetir el tope; el dominio lo comprueba en un sitio y con un mensaje, como hace con el icono. **Y nada sigue el enlace**: comprobar que resuelve obligaría a salir a Internet en cada alta (`pm.md` §5.2.8).
- **`validityDays` es opcional en los dos tipos.** Ausente o `null` significa lo mismo: el producto no caduca. Se valida en el DTO —entero mayor que cero— porque su regla no depende de ningún otro campo, al revés que el precio.
- `sourceMembershipId` y `targetMembershipId` son **obligatorios los dos o prohibidos los dos** según `type`, y **la condición se comprueba en el caso de uso y no con validación declarativa**: una anotación de Bean Validation no puede expresar «obligatorio si otro campo vale X» sin un validador de clase, y el mensaje que produce no distingue cuál de las cuatro mitades se incumplió. Con dos campos el mensaje **dice cuál**: `VAL-007` y `VAL-008` viajan con el `field` que falta o que sobra, porque uno que no distinga obliga a probar los dos.
- **El orden de las comprobaciones importa y está fijado**: moneda → destino → **origen** → unicidad. Que el origen no exista (`EX-002`) y que el origen no esté por debajo del destino (`EX-006`, con `VAL-014`) son dos respuestas distintas, y la segunda no se puede dar sin haber resuelto la primera.

- **`scope` e `implementation` son obligatorios y sin valor por omisión**, en los **dos** tipos. Se validan **con anotación** —`@NotNull` sobre el enumerado— y no en el caso de uso, al revés que las membresías: su obligatoriedad **no depende de ningún otro campo**, de modo que no hay nada que un validador de clase pudiera decir que la anotación no diga. Un valor fuera del dominio lo rechaza Jackson al deserializar el enumerado, con `400`.
- **Ausente y nulo significan lo mismo aquí: falta.** No se admite el valor por omisión ni en el DTO ni en la columna, y la razón es que el defecto **no se vería**: un producto que se guardó con el alcance supuesto se ve exactamente igual que uno declarado, y nadie descubriría nunca que nadie decidió dónde se publica.
**Respuesta `201`**, con cabecera `Location: /api/v1/products/{id}`:

```json
{
  "id": "01a03340-1200-7001-9c4f-5e7ad4000001",
  "code": "UPGRADE_ORO",
  "type": "UPGRADE_MEMBRESIA",
  "name": "Ascenso a Oro",
  "description": "Acceso a los contenidos de nivel oro.",
  "sourceMembership": { "id": "018f3a2b-…", "code": "BECA", "name": "Beca", "level": 4 },
  "targetMembership": { "id": "018f3a2b-…", "code": "ORO", "name": "Oro", "level": 1 },
  "price": 49.99,
  "purchasePrice": 30.00,
  "videoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "validityDays": 30,
  "currency": { "id": "01a03336-…", "code": "USD", "decimalPlaces": 2 },
  "status": "INACTIVO",
  "createdAt": "2026-08-26T14:32:11Z",
  "updatedAt": "2026-08-26T14:32:11Z"
}
```

- **Las dos membresías llegan resueltas** y no como identificadores sueltos, con los datos que el puerto ya devolvió: resolverlas cuesta cero consultas extra porque la validación ya las trajo. En el ejemplo, `level` 4 → 1 es un **salto de tres escalones**, y es legítimo (`RN-PM-018`).
- **`sourceMembership` y `targetMembership` viajan como `null` presentes** en los bots, no ausentes: un campo que falta es indistinguible de uno que el cliente no conoce.
- **El precio se serializa con los decimales de su moneda** y no con la escala de la columna (`CA-PM-082`): `49.99`, no `49.9900`. **Vale para los dos importes, con la misma función y en el mismo sitio** (`ProductPrice`): escrita dos veces, el mismo producto acabaría enseñando sus dos precios con escalas distintas.
- **`videoUrl` viaja como `null` presente** cuando el producto no lo declara (`CA-PM-220`), por lo mismo. Y al revés que el precio de compra, **esta no es una respuesta de administración por llevarlo**: el enlace sale en las cuatro lecturas (`RN-PM-032`).
- **`purchasePrice` viaja como `null` presente** cuando el producto no lo declara, no ausente (`CA-PM-146`). Su nulo **significa** «no se conoce el costo», y un campo que desaparece no puede decir eso.
- **Esta respuesta lleva los DOS precios porque exige `products:create`**, que solo tiene quien administra el catálogo. `RN-PM-024` acota el precio de compra a administración: `RF-PM-007` y `RF-PM-008`, que no piden ningún permiso de administración, **no lo devuelven** (12-09-2026).

## 5. Autorización

`@PreAuthorize("hasAuthority('products:create')")` sobre el método, como el resto del sistema. El permiso se siembra en `V40` y se resuelve contra la base en cada petición (`security.md` §4.5), de modo que retirárselo a un rol tiene efecto inmediato.

## 6. Auditoría

Un evento `CREATE` en `audit_change_log`, en la misma transacción, con el **estado inicial completo**: código, tipo, nombre, descripción, **origen**, destino, **los dos precios**, moneda y estado (`CA-PM-011`, `CA-PM-150`).

**El precio de compra entra en la instantánea aunque no se cobre**, y no por simetría: es el único sitio donde queda escrito **cuánto costó** un producto cuyo costo después se corrige, y sin él una revisión de márgenes no tendría contra qué contrastarse. Va como texto y nulo cuando no se declaró, igual que el resto — `Map.of` rechaza los nulos, de modo que la instantánea usa un mapa que sí los admite. La clave es `purchase_price` desde el 12-09-2026; los eventos anteriores conservan `public_price`, porque una instantánea es lo que era, no lo que es.

**Sin evento de seguridad**, y no es una omisión: `spec.md` §14 resolución 5 lo decidió. Un producto no concede privilegios sobre el sistema y el catálogo de `security.md` §8.1 es cerrado. Quién puso un precio lo responde este mismo evento.

## 7. Transaccionalidad

Una sola transacción para el `INSERT` y su evento de auditoría. Las lecturas contra los puertos de `SP` ocurren **dentro** de ella y son de solo lectura.

**El `flush` es explícito** antes de salir del adaptador, por el mismo motivo que en `RF-SP-016`: sin él, la violación de `uq_products_code` o de `uq_products_name` saltaría al confirmar, fuera del método que sabe traducirla, y llegaría al manejador global como fallo no controlado.

**No hay bloqueo pesimista.** No se lee ningún agregado para modificarlo: el alta inserta, y la unicidad la resuelven las restricciones. Dos altas simultáneas con el mismo código se serializan en el índice único, y la perdedora recibe su `409` traducido.

## 8. Impacto sobre otros módulos

**`SP` gana dos interfaces publicadas** y ninguna otra cosa: no cambia ninguna tabla suya, ningún endpoint ni ninguna regla. `requirements/sp.md` anota que quedan publicadas, sin abrir un requerimiento nuevo (D-25).

**`CM` cambia, y no porque este requerimiento se lo pida** (08-09-2026). `ProductCommissionCapGuard` convierte un valor fijo a porcentaje con `fixed_amount ÷ precio`, y dependía **por escrito** de `ck_products_price_positive` para que ese divisor no fuera cero. `V67` retira esa garantía, de modo que la corrección viaja en el mismo pase: sobre un producto de precio cero, cualquier valor fijo mayor que cero **paga más del 100 % de lo que el producto cobra** y se rechaza con el mensaje de `RN-CM-019`; uno de cero ocupa cero. No hace falta ninguna regla nueva — es lo que aquella ya decía, llevado al límite.

**`MV` no cambia.** Sigue copiando `price` en `movement_details.unit_price`, y el precio de compra **no entra en el puerto de venta**: `ProductCatalog.saleViewOf` no lo lleva, y eso es lo único que impide que empiece a cobrarse.

**El contrato OpenAPI crece** con el endpoint y sus esquemas, y `OpenApiContractIT` lo regenera en `docs/api/`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Deducir el origen de la cadena** en lugar de declararlo: el nivel inmediatamente inferior al destino | Es lo que había, escrito de otra forma, y arrastra su defecto: **hace imposible el salto**. Un producto por salto es la razón de la enmienda, no un efecto colateral (`RN-PM-018`) |
| Validar el tipo y el destino con un validador de clase de Bean Validation | Expresa la condición, pero el `400` que produce no distingue si sobró el destino o si faltó, y `VAL-007` y `VAL-008` son dos mensajes distintos |
| Un endpoint por tipo (`/products/upgrades`, `/products/services`) | Duplicaría el contrato y la mitad del caso de uso para una diferencia de un campo. La spec ya resolvió que es **un** requerimiento |
| Guardar el precio como `numeric(12,2)` | Fijaría en dos los decimales de toda moneda, cuando `currencies.decimal_places` existe justamente para no asumirlo |
| Que `PM` consultara `memberships` con su propio repositorio | Es lo que D-25 prohíbe: ataría `PM` al esquema de `SP` y un cambio allí lo rompería en silencio |
| **Guardar solo el costo y calcular el precio con un margen** —o al revés— | No hay ninguna operación que relacione los dos importes: **la relación es una decisión comercial que se toma producto a producto** y puede ser cualquiera. Lo único que puede guardarla es una segunda columna (`requirements/pm.md` §5.2.4) |
| **`purchase_price NOT NULL DEFAULT 0`**, o rellenar con el precio del sistema | Los dos borran una distinción que significa algo: «no se conoce el costo» **no es** «costó cero», y rellenar con el precio de venta inventaría un margen de cero que nadie declaró |
| **Un `CHECK` de `price >= purchase_price`** | Cierra un caso legítimo: vender por debajo del costo es una decisión comercial, y el sistema la registra en vez de impedirla. Se descarta **hoy**, no para siempre — es una migración de tres líneas el día que se decida (`requirements/pm.md` §5.2.6) |
| **Una segunda moneda para el precio de compra** | Si NEXUS paga en otra moneda, quien registra el costo lo convierte al declararlo. Una compra con su moneda, su tasa y su fecha es una **tabla de compras**, no una columna de esta |
| **Una columna nueva en vez de renombrar `public_price`** (12-09-2026) | Dejaría una columna huérfana que nadie lee y que seguiría llamándose «público», y obligaría a decidir qué hacer con ella. El renombrado con vaciado deja **una** columna con **un** significado |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La escala de `price` no se puede acotar por moneda en el esquema**: un `CHECK` no consulta otra tabla | Se verifica en el dominio con prueba unitaria propia sobre una moneda de dos decimales y otra de cero (`requirements/pm.md` §10.3) |
| 2 | **Los puertos de `SP` son código nuevo en un módulo ya implementado** | Son de solo lectura y no tocan ningún caso de uso existente. La suite de `SP` debe seguir en verde sin cambios |
| 3 | **Una moneda desactivada después del alta deja productos con moneda inactiva** | Es deliberado (`RN-PM-008`): desactivar no invalida lo registrado. Lo que no puede es usarse en un alta nueva |
| 4 | **Dos altas simultáneas del mismo código** | Las serializa `uq_products_code`; la prueba concurrente es obligatoria y no basta con la verificación previa |
| 5 | **El precio de compra acaba cobrándose sin que nada falle.** Basta con añadirlo al puerto que `PM` publica para vender (`ProductCatalog.saleViewOf`) o con leerlo desde `MV`: no hay restricción, tipo ni prueba que lo impida, porque «este número no se cobra» no es expresable en el esquema | La única defensa es **dónde no está**: `SaleView` no lleva el campo, y `RN-PM-023` lo declara. Se prueba **por el efecto**: corregir el precio de compra **no cambia** el importe de una venta registrada después (§11) |
| 6 | **El precio de compra acaba publicándose sin que nada falle** (12-09-2026). Basta con añadir el campo a `OfferItem` o a la respuesta del hotlink «por simetría» con las de administración, y el margen de NEXUS sale sin token | La misma defensa, en el otro sentido: **las dos proyecciones públicas no tienen el campo y sus consultas no lo seleccionan**. Se prueba por la **ausencia** en el cuerpo (`CA-PM-160`, `CA-PM-163`), con un producto que sí lo tiene declarado |
| 6 | **Un producto de precio cero rompía la conversión de un valor fijo en `CM`** | Se corrige `ProductCommissionCapGuard` en el mismo pase, con `RN-CM-019` llevada a su límite. Ver §2.6 y §8 |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| Normalización y formato del código, y del nombre | Unitaria | Sobre `Product`, sin Spring |
| Decimales del precio según la moneda | Unitaria | Dos monedas: una de dos decimales y otra de **cero**. **Contra los dos importes**, y con el caso que solo aparece con dos: el del sistema cabe y el público no |
| El precio de compra, en sus cuatro estados | API | Informado, **ausente**, **nulo explícito** y negativo. Los dos primeros terminan en la misma fila; el tercero tiene que llegar **presente y nulo** en la respuesta, y el cuarto nombrar `purchasePrice` |
| El precio **cero** | API | Se admite en los dos importes (`CA-PM-149`). Es la renovación de una membresía gratuita, y hasta hoy era un `400` |
| El enlace del video, en sus cuatro estados | API | Informado en un **bot** —que es donde el icono no cabe y el video sí—, **ausente**, **nulo explícito** y **con forma inválida** en sus cinco variantes: relativo, sin esquema, `ftp://`, con espacio, y de 501 caracteres. El rechazo nombra `videoUrl` (`CA-PM-219` a `CA-PM-221`) |
| Los once criterios de `spec.md` §12 | API | `MockMvc` con permiso concedido |
| La condición cruzada de `RN-PM-002` | API | **En los cuatro sentidos**: upgrade sin origen, upgrade sin destino, bot con destino y bot con origen — y el `field` de cada rechazo, porque un mensaje que no distinga obliga a probar los dos |
| `RN-PM-017` — el origen por debajo del destino | API | Origen **igual** al destino (`400`, lo ve el agregado) y origen **por encima** (`422`, hace falta el `level` de las dos filas). Un descenso vendido como upgrade |
| `RN-PM-018` — saltar niveles es legítimo | API | `BECA → ORO` con **dos eslabones de por medio**, y la premisa comprobada: sin afirmar que la cadena los tiene, el salto lo sería solo de nombre |
| El producto nace `INACTIVO` | API | Y enviar `status` devuelve `400`, no se ignora |
| Código único **incluso contra eliminados** | Integración | Se retira un producto y se intenta reutilizar su código |
| Traducción por nombre de restricción | Integración | El duplicado produce `409` con el campo correcto, distinguiendo código de nombre |
| Dos altas simultáneas con el mismo código | Concurrencia | Una queda, la otra recibe `409`. **No basta la verificación previa** |
| La frontera entre módulos | ArchUnit | `..modules.products..` no depende de `..modules.system..domain..` |
| El contrato publicado coincide | Integración | `OpenApiContractIT` |
