# SPEC — `RF-PM-019` Consultar el detalle de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-019` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |
| Enmendada el | 16-09-2026 — **el paquete trae `coverImageUrl`, la dirección de su portada**, también retirado (`RN-PM-045`, `RF-PM-028`). Ver §15 |

---

## 1. Objetivo

Ver **todo lo que hay en un paquete**: cada producto con su descuento y su precio dentro del paquete, los tres totales, la conversión, y —si no se puede ofrecer— **por qué**.

## 2. Contexto

**Aquí nace la cuenta de `RN-PM-036`**, y todo lo demás la reutiliza: la lista (`RF-PM-018`), el alta y las tres operaciones sobre productos —que devuelven esta misma forma—, la oferta (`RF-PM-007`) y el hotlink (`RF-PM-026`). Es también la única lectura que dice **qué detiene** a un paquete que no se ofrece (`RN-PM-039`, `RN-PM-040`): la oferta simplemente lo oculta, y sin este detalle nadie sabría por qué.

Es la lectura **de administración**, y por eso —al revés que la oferta y el hotlink— **sí** trae el `purchasePrice` de cada producto (`RN-PM-024`) y el motivo del retiro del paquete, como `RF-PM-003` con el producto.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador · fuerza comercial | Consulta el paquete |

## 4. Alcance

### 4.1 Incluye

- Devolver un paquete por su identificador, **retirado incluido**, con su moneda y su alcance resueltos.
- Sus productos: código, nombre, tipo, estado, si está retirado, precio de catálogo, **precio de compra**, descuento (forma y valor) y **precio dentro del paquete**.
- `price`, `listPrice`, `savings` y la conversión de `price` a la moneda por omisión.
- `offerable` y, cuando es falso, `offerableReason`: el primer motivo en orden fijo.
- El motivo del retiro, si está retirado.

### 4.2 No incluye

- **Paginar los productos.** Un paquete tiene pocos; el día que tenga cien, la pregunta es otra.
- **Las reseñas ni el `rating`** de los productos dentro: es el detalle del paquete, no de cada producto.
- **La oferta del paquete a una persona concreta**: `RF-PM-007`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-036` | **El precio se calcula siempre** | `requirements/pm.md` §5.1 |
| `RN-PM-037` | El fijo que hoy supera el precio **cuenta cero** | `requirements/pm.md` §5.1 |
| `RN-PM-039` | El paquete no se ofrece si algo suyo dejó de poderse comprar — **y el detalle dice qué** | `requirements/pm.md` §5.1 |
| `RN-PM-040` | Dos productos y descripción para publicarse — y el detalle lo dice | `requirements/pm.md` §5.1 |
| `RN-PM-024` | El precio de compra **sí** sale en administración; la conversión sale siempre | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del paquete | Sí | Cuál | Va en la ruta. UUID |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Paquete | Identificador, código, nombre, descripción, **`coverImageUrl`** (desde el 16-09-2026: la dirección de la portada, `/api/v1/product-images/{imageId}`, **presente y nula** cuando no hay, también en un retirado — `RN-PM-045`), moneda resuelta (`id`, `code`, `decimalPlaces`), alcance, estado, fechas, `deletedAt` y `deletionReason` cuando está retirado |
| `items` | Un elemento por producto, en el orden en que se asociaron: `product` (`id`, `code`, `name`, `type`, `status`, `deleted`, `price`, `purchasePrice`), `discount` (`type`, `value`) y **`priceInPackage`** |
| `listPrice` | Σ `product.price` |
| `price` | Σ `priceInPackage` — **lo que costaría el paquete** |
| `savings` | `listPrice − price` |
| `exchange` | La conversión de `price` a la moneda por omisión, con la tasa vigente; **nula y presente** si no hay nada que convertir |
| `offerable` | Si hoy la oferta y el hotlink lo enseñarían |
| `offerableReason` | **Nulo** cuando `offerable` es verdadero; si no, el **primer** motivo en este orden: menos de dos productos → sin descripción → paquete inactivo → paquete retirado → un producto no ofrecible, **nombrándolo por su código** |

**El orden de los motivos es fijo y está escrito** para que dos lecturas del mismo paquete no digan cosas distintas. Se elige del más estructural al más circunstancial: primero lo que falta al paquete, después lo que le pasa a un producto suyo.

**`priceInPackage` es lo que una línea de venta copiará** el día que el paquete se venda (`requirements/pm.md` §5.2.10). Se publica por producto y no solo el total por eso.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `packages:read`; el paquete existe, retirado o no.

**Postcondiciones:** ninguna. No audita.

## 8. Flujo principal

1. Llega la petición con el identificador.
2. El sistema trae el paquete **con sus productos** en una sentencia, retirado incluido (`EX-001` si no existe).
3. El sistema calcula, en `PackagePricing`, el precio dentro del paquete de cada producto y los tres totales, redondeando a la moneda del paquete.
4. El sistema pide la conversión de `price` a la moneda por omisión (dos sentencias más, la segunda solo si hay algo que convertir).
5. El sistema decide `offerable` y su motivo.
6. Si el paquete está retirado, el sistema lee el motivo del retiro de la auditoría (una sentencia más).
7. Devuelve `200`.

## 9. Flujos alternativos

### FA-001 — Paquete vacío

**Comportamiento:** `items` vacío, los tres totales en cero, `exchange` nulo, `offerable: false` por «menos de dos productos».

### FA-002 — Un producto del paquete está inactivo o retirado

**Comportamiento:** el producto **se devuelve igual**, con su `status` y su `deleted`, y su precio dentro del paquete **sigue sumando** — el paquete todavía dice cuánto costaría—; `offerable: false` nombrándolo. Es lo que permite arreglarlo desde administración.

### FA-003 — El paquete está retirado

**Comportamiento:** se devuelve con `deletedAt`, `deletionReason` y `offerable: false`, como el producto retirado en `RF-PM-003`.

### FA-004 — El fijo de un producto supera hoy su precio

**Comportamiento:** su `priceInPackage` es **cero** (`RN-PM-037`), no negativo, y nada avisa: es el hueco temporal declarado.

## 10. Excepciones

### EX-001 — El paquete no existe

**Respuesta del sistema:** `404` — *«No existe un paquete con ese identificador.»* Un retirado **no** es un `404`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-277` | El sistema devuelve el paquete con sus productos, cada uno con su descuento y su **`priceInPackage`**, y `price`, `listPrice` y `savings` **cuadran** con la suma |
| `CA-PM-278` | Corregir el **precio de catálogo** de un producto (`RF-PM-004`) **cambia** el detalle del paquete en la siguiente lectura, sin tocar el paquete: el precio se calcula |
| `CA-PM-279` | Un producto cuyo fijo **hoy supera** su precio cuenta **cero**, y el total no baja de cero |
| `CA-PM-280` | El paquete devuelve `offerable: true` con `offerableReason` **nulo** cuando está activo, con descripción, dos o más productos y todos activos y vivos |
| `CA-PM-281` | `offerable: false` con el motivo en su **orden**: menos de dos, sin descripción, inactivo, retirado, y un producto no ofrecible **nombrado por su código** |
| `CA-PM-282` | Un producto **inactivo** o **retirado** dentro del paquete se devuelve con su estado y **sigue sumando**, y `offerable` es falso por él |
| `CA-PM-283` | El detalle devuelve `purchasePrice` de cada producto —presente y nulo cuando no se conoce—, y `exchange` calculado sobre `price`, nulo si el paquete ya está en la moneda de casa |
| `CA-PM-284` | El paquete **retirado** se devuelve con su motivo; el **inexistente** responde `404`; y la lectura cuesta **dos** sentencias —el paquete con sus filas y la moneda de casa—, **tres** con conversión y **una más** con motivo de retiro; el paquete vacío cuesta **una** |
| `CA-PM-368` | El detalle devuelve **`coverImageUrl`** con la forma `/api/v1/product-images/{uuid}` cuando el paquete tiene portada, y **presente y nula** cuando no — también en un paquete **retirado**—, sin que el número de sentencias suba (16-09-2026) |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Producto en el paquete cuya moneda **ya no es** la del paquete | Puede ocurrir: `RF-PM-004` deja corregir la moneda de un producto y nadie mira los paquetes que lo contienen. Se acepta como el hueco temporal que es: el detalle suma el importe tal cual, y `offerable` no lo mira. Queda anotado en §14 |
| Redondeo: tres productos con porcentaje que dejan `x.005` | Cada `priceInPackage` se redondea **por producto** y el total es la suma de los redondeados, para que el total cuadre con las líneas |
| El paquete tiene un solo producto | `offerable: false` por «menos de dos», aunque esté activo |
| Un producto entra dos veces en la respuesta | No puede: la clave primaria lo impide |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se recalcula o se rechaza cuando un producto **cambia de moneda** después de asociado? | **Ninguna de las dos hoy**: el detalle suma lo que hay, en la moneda del paquete, y el importe del producto se toma tal cual. Es un hueco temporal como el del fijo, y **más raro** —cambiar de moneda un producto que se vendió es ya una operación excepcional—. Queda anotado para que quien lo necesite lo cierre en `RF-PM-004` y no aquí |
| 2 | ¿El orden de los `items` es configurable? | **No**: el de asociación, que es el que el administrador construyó. Un orden por precio o por nombre es cosa de la pantalla |
| 3 | ¿Debe el detalle traer el `rating` de cada producto? | **No.** Es el detalle del paquete; quien quiera las estrellas de un producto tiene su detalle |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. **Aquí nace la cuenta de `RN-PM-036`** —`PackagePricing`, un solo sitio— y **`offerable` con su motivo en orden fijo**, que es la única señal de que un paquete activo no se está ofreciendo. `priceInPackage` se publica por producto porque es lo que una línea de venta copiará. **El redondeo es por producto** y el total es la suma de los redondeados, para que cuadre con las líneas. Queda anotado el hueco del producto que cambia de moneda después de asociado. | Responsable técnico |
| 0.2.0 | 15-09-2026 | **Construida** (`PackageDetailIT`, `PackageOfferabilityTest`), y con ella la enmienda de `RF-PM-007` (`PackageOfferIT`). Enmienda de Art. I.7 al construir: **`CA-PM-284` cuenta lo que la lectura cuesta de verdad** —dos sentencias en la moneda de casa, tres con conversión, una más con motivo, y una sola para el paquete vacío, que no pide conversión sobre cero—; la redacción anterior («tres, cuatro, cinco») contaba la tasa dos veces. `PackageOfferability` nombra el producto por su código en el motivo. | Responsable técnico |
| 0.3.0 | 16-09-2026 | **El paquete trae `coverImageUrl`, la dirección de su portada** (`RN-PM-045`, [`requirements/pm.md`](../../../requirements/pm.md) v0.37.0 §5.2.12), presente y nula cuando no hay, también retirado. Es la respuesta de las ocho operaciones del paquete —y desde hoy de diez: la subida y el retiro de la portada la devuelven—. `CA-PM-368`. Enmienda que construye `RF-PM-028` (Art. I.7). | Responsable del proyecto |
