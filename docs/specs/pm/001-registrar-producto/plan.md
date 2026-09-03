# PLAN — `RF-PM-001` Registrar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-001` |
| Especificación | [`spec.md`](spec.md) v0.5.0 |
| `spec.md` aprobada el | 26-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Enmendado el | 27-08-2026 — `RN-PM-015`; 02-09-2026 — la membresía de **origen** (`RN-PM-017`, `RN-PM-018`) |
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
| `ck_products_price_positive` | `price > 0` | `RN-PM-006` |
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
| Relleno | `UPDATE … SET source_membership_id = (SELECT id FROM memberships WHERE code = 'FREE')` sobre los upgrades existentes | **Es una decisión, no una deducción**: bajo el modelo anterior esos productos **no tenían** origen. Se elige el suelo de la cadena porque conserva la oferta de quien está en `FREE`, que es el caso más común |
| `ck_products_type_target` | Se **reescribe entera** para exigir las dos membresías en el upgrade y prohibirlas las dos en el bot | Añadir un segundo `CHECK` al lado se leería como dos reglas, y `RN-PM-002` es **una** |
| `fk_products_source_membership` | `source_membership_id → memberships(id)` | Lo mismo que su gemela del destino, y por el mismo motivo |
| `ck_products_origen_distinto` | `source_membership_id IS NULL OR source_membership_id <> target_membership_id` | La mitad de `RN-PM-017` que el motor **sí** puede sostener: un upgrade de `FREE` a `FREE` vende nada |
| `uq_products_upgrade_target` | Se rehace sobre **`(source_membership_id, target_membership_id)`** | La versión anterior prohibía **exactamente lo que el origen existe para permitir**: dos upgrades activos hacia `ORO`, uno desde `FREE` y otro desde `PLATINO` |

!!! danger "El coste del relleno, escrito para que nadie lo descubra en producción"

    Dar `FREE` a todos los upgrades existentes significa que **quien esté en `VIP` deja de verlos**. No recibe ningún error: simplemente dejan de ofrecerse, y el catálogo se ve perfectamente bien desde administración. La alternativa considerada —el nivel inmediatamente inferior al destino, derivable de la cadena— estrecha igual y además deja a `FREE` sin nada.

    **Y la migración puede detenerse**: si existe un upgrade cuyo destino es `FREE`, el relleno lo deja apuntando a sí mismo y `ck_products_origen_distinto` la aborta. Es lo correcto — ese producto vendía un descenso llamándolo upgrade, y qué hacer con él es una decisión del negocio, no de una migración.

!!! important "La otra mitad de `RN-PM-017` no cabe en el esquema"

    «El origen está por debajo del destino» obliga a leer el `level` de **dos filas de `memberships`**, y un `CHECK` no consulta otra tabla. Vive en el caso de uso, por el mismo motivo exacto que los decimales de la moneda en `RN-PM-007`.

    Y va con el reparto de `V47` delante: **`level` numera desde la cima** —`ORO` es el 1 y `FREE` el 4—, de modo que «por debajo» es **número mayor**. Escribir la comparación al revés produce un sistema que acepta descensos y rechaza ascensos, y las dos mitades fallan calladas.

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
  "validityDays": 30,
  "currencyId": "01a03336-6d00-7001-9c4f-5e7ad3000001"
}
```

- **No existe campo `status`**, y el DTO se deserializa con `FAIL_ON_UNKNOWN_PROPERTIES` activo: enviarlo devuelve `400` y no se ignora en silencio (`CA-PM-068`). Es lo mismo que `RF-SP-001` hizo con `status` e `isSystem`, y es lo que hace verificable que **el estado inicial no se pueda forzar desde fuera**.
- `code` se **recorta y se pasa a mayúsculas** antes de validar; `name` y `description` se recortan. Sin el recorte, `"Ascenso "` y `"Ascenso"` serían dos nombres distintos para `uq_products_name`.
- `price` llega como **número**. La escala admisible **no la fija el DTO**, la fija la moneda (`RN-PM-007`), y por eso se valida en el caso de uso y no con una anotación.
- **`validityDays` es opcional en los dos tipos.** Ausente o `null` significa lo mismo: el producto no caduca. Se valida en el DTO —entero mayor que cero— porque su regla no depende de ningún otro campo, al revés que el precio.
- `sourceMembershipId` y `targetMembershipId` son **obligatorios los dos o prohibidos los dos** según `type`, y **la condición se comprueba en el caso de uso y no con validación declarativa**: una anotación de Bean Validation no puede expresar «obligatorio si otro campo vale X» sin un validador de clase, y el mensaje que produce no distingue cuál de las cuatro mitades se incumplió. Con dos campos el mensaje **dice cuál**: `VAL-007` y `VAL-008` viajan con el `field` que falta o que sobra, porque uno que no distinga obliga a probar los dos.
- **El orden de las comprobaciones importa y está fijado**: moneda → destino → **origen** → unicidad. Que el origen no exista (`EX-002`) y que el origen no esté por debajo del destino (`EX-006`, con `VAL-014`) son dos respuestas distintas, y la segunda no se puede dar sin haber resuelto la primera.

**Respuesta `201`**, con cabecera `Location: /api/v1/products/{id}`:

```json
{
  "id": "01a03340-1200-7001-9c4f-5e7ad4000001",
  "code": "UPGRADE_ORO",
  "type": "UPGRADE_MEMBRESIA",
  "name": "Ascenso a Oro",
  "description": "Acceso a los contenidos de nivel oro.",
  "sourceMembership": { "id": "018f3a2b-…", "code": "FREE", "name": "Free", "level": 4 },
  "targetMembership": { "id": "018f3a2b-…", "code": "ORO", "name": "Oro", "level": 1 },
  "price": 49.99,
  "validityDays": 30,
  "currency": { "id": "01a03336-…", "code": "USD", "decimalPlaces": 2 },
  "status": "INACTIVO",
  "createdAt": "2026-08-26T14:32:11Z",
  "updatedAt": "2026-08-26T14:32:11Z"
}
```

- **Las dos membresías llegan resueltas** y no como identificadores sueltos, con los datos que el puerto ya devolvió: resolverlas cuesta cero consultas extra porque la validación ya las trajo. En el ejemplo, `level` 4 → 1 es un **salto de tres escalones**, y es legítimo (`RN-PM-018`).
- **`sourceMembership` y `targetMembership` viajan como `null` presentes** en los bots, no ausentes: un campo que falta es indistinguible de uno que el cliente no conoce.
- **El precio se serializa con los decimales de su moneda** y no con la escala de la columna (`CA-PM-082`): `49.99`, no `49.9900`.

## 5. Autorización

`@PreAuthorize("hasAuthority('products:create')")` sobre el método, como el resto del sistema. El permiso se siembra en `V40` y se resuelve contra la base en cada petición (`security.md` §4.5), de modo que retirárselo a un rol tiene efecto inmediato.

## 6. Auditoría

Un evento `CREATE` en `audit_change_log`, en la misma transacción, con el **estado inicial completo**: código, tipo, nombre, descripción, **origen**, destino, precio, moneda y estado (`CA-PM-011`).

**Sin evento de seguridad**, y no es una omisión: `spec.md` §14 resolución 5 lo decidió. Un producto no concede privilegios sobre el sistema y el catálogo de `security.md` §8.1 es cerrado. Quién puso un precio lo responde este mismo evento.

## 7. Transaccionalidad

Una sola transacción para el `INSERT` y su evento de auditoría. Las lecturas contra los puertos de `SP` ocurren **dentro** de ella y son de solo lectura.

**El `flush` es explícito** antes de salir del adaptador, por el mismo motivo que en `RF-SP-016`: sin él, la violación de `uq_products_code` o de `uq_products_name` saltaría al confirmar, fuera del método que sabe traducirla, y llegaría al manejador global como fallo no controlado.

**No hay bloqueo pesimista.** No se lee ningún agregado para modificarlo: el alta inserta, y la unicidad la resuelven las restricciones. Dos altas simultáneas con el mismo código se serializan en el índice único, y la perdedora recibe su `409` traducido.

## 8. Impacto sobre otros módulos

**`SP` gana dos interfaces publicadas** y ninguna otra cosa: no cambia ninguna tabla suya, ningún endpoint ni ninguna regla. `requirements/sp.md` anota que quedan publicadas, sin abrir un requerimiento nuevo (D-25).

**El contrato OpenAPI crece** con el endpoint y sus esquemas, y `OpenApiContractIT` lo regenera en `docs/api/`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Deducir el origen de la cadena** en lugar de declararlo: el nivel inmediatamente inferior al destino | Es lo que había, escrito de otra forma, y arrastra su defecto: **hace imposible el salto**. Un producto por salto es la razón de la enmienda, no un efecto colateral (`RN-PM-018`) |
| Validar el tipo y el destino con un validador de clase de Bean Validation | Expresa la condición, pero el `400` que produce no distingue si sobró el destino o si faltó, y `VAL-007` y `VAL-008` son dos mensajes distintos |
| Un endpoint por tipo (`/products/upgrades`, `/products/services`) | Duplicaría el contrato y la mitad del caso de uso para una diferencia de un campo. La spec ya resolvió que es **un** requerimiento |
| Guardar el precio como `numeric(12,2)` | Fijaría en dos los decimales de toda moneda, cuando `currencies.decimal_places` existe justamente para no asumirlo |
| Que `PM` consultara `memberships` con su propio repositorio | Es lo que D-25 prohíbe: ataría `PM` al esquema de `SP` y un cambio allí lo rompería en silencio |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La escala de `price` no se puede acotar por moneda en el esquema**: un `CHECK` no consulta otra tabla | Se verifica en el dominio con prueba unitaria propia sobre una moneda de dos decimales y otra de cero (`requirements/pm.md` §10.3) |
| 2 | **Los puertos de `SP` son código nuevo en un módulo ya implementado** | Son de solo lectura y no tocan ningún caso de uso existente. La suite de `SP` debe seguir en verde sin cambios |
| 3 | **Una moneda desactivada después del alta deja productos con moneda inactiva** | Es deliberado (`RN-PM-008`): desactivar no invalida lo registrado. Lo que no puede es usarse en un alta nueva |
| 4 | **Dos altas simultáneas del mismo código** | Las serializa `uq_products_code`; la prueba concurrente es obligatoria y no basta con la verificación previa |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| Normalización y formato del código, y del nombre | Unitaria | Sobre `Product`, sin Spring |
| Decimales del precio según la moneda | Unitaria | Dos monedas: una de dos decimales y otra de **cero** |
| Los once criterios de `spec.md` §12 | API | `MockMvc` con permiso concedido |
| La condición cruzada de `RN-PM-002` | API | **En los cuatro sentidos**: upgrade sin origen, upgrade sin destino, bot con destino y bot con origen — y el `field` de cada rechazo, porque un mensaje que no distinga obliga a probar los dos |
| `RN-PM-017` — el origen por debajo del destino | API | Origen **igual** al destino (`400`, lo ve el agregado) y origen **por encima** (`422`, hace falta el `level` de las dos filas). Un descenso vendido como upgrade |
| `RN-PM-018` — saltar niveles es legítimo | API | `FREE → ORO` con **dos eslabones de por medio**, y la premisa comprobada: sin afirmar que la cadena los tiene, el salto lo sería solo de nombre |
| El producto nace `INACTIVO` | API | Y enviar `status` devuelve `400`, no se ignora |
| Código único **incluso contra eliminados** | Integración | Se retira un producto y se intenta reutilizar su código |
| Traducción por nombre de restricción | Integración | El duplicado produce `409` con el campo correcto, distinguiendo código de nombre |
| Dos altas simultáneas con el mismo código | Concurrencia | Una queda, la otra recibe `409`. **No basta la verificación previa** |
| La frontera entre módulos | ArchUnit | `..modules.products..` no depende de `..modules.system..domain..` |
| El contrato publicado coincide | Integración | `OpenApiContractIT` |
