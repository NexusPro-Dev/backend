# SPEC — `RF-PM-026` Consultar el hotlink de un paquete, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-026` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que **un enlace repartido por un vendedor abra la pantalla de un paquete**: qué productos trae, cuánto vale cada uno dentro de él, cuánto vale el paquete —en su moneda y en la de casa—, cuánto se ahorra y quién lo ofrece. Sin token.

## 2. Contexto

Es `RF-PM-008` aplicado al paquete, y **hereda sus decisiones enteras**: el `404` uniforme, el vendedor reducido a nombre y apellido, la conversión informativa, la ausencia de token y de auditoría, la cota por origen. Lo que añade es lo que un paquete tiene y un producto no: **la cuenta** de `RN-PM-036` —cada producto con su precio dentro del paquete, `price`, `listPrice`, `savings`— y **la ofrecibilidad** de `RN-PM-039`: un paquete no se resuelve mientras cualquiera de sus productos no esté activo y vivo, porque el enlace prometería algo que no se puede entregar.

**Es el segundo sitio donde el alcance filtra.** `RN-PM-021` se aplica por extensión: el hotlink del paquete publica solo lo **activo y de alcance `HOTLINK` o `AMBOS`** —del paquete, no de sus productos: el alcance de un producto dice hasta dónde se muestra **ese producto por sí solo**, y dentro de un paquete quien decide el canal es el paquete (§13).

**Vive bajo la misma familia de rutas.** `/api/v1/hotlinks/{username}/packages/{code}` no compite con `/api/v1/hotlinks/{username}/{code}`: tiene tres segmentos tras la familia y aquel dos, de modo que ningún código de producto puede confundirse con la palabra `packages`. La cota de tasa se aplica **por prefijo de familia** y por eso no estrena política (`CA-PM-333`).

## 3. Actores

| Actor | Papel |
|---|---|
| **Cualquiera, sin autenticar** | Abre el enlace |

**Llevar un token no cambia la respuesta**, como en `RF-PM-008`.

## 4. Alcance

### 4.1 Incluye

- Resolver **un nombre de usuario y un código de paquete** en una sola llamada.
- Devolver del vendedor **nombre y apellido**, y nada más.
- Devolver el paquete con **sus productos**, cada uno con lo que el hotlink del producto publica y con su **precio dentro del paquete**; los tres totales; y la **conversión de `price`** a la moneda por omisión con la tasa vigente hoy.
- Responder **`404` uniforme** a todo lo que no procede.

### 4.2 No incluye

- **Comprar.** El enlace enseña; la venta del paquete es de otra tanda (`requirements/pm.md` §5.2.10).
- **Decir por qué no se resuelve.** El detalle administrativo (`RF-PM-019`) lo dice; aquí toda ausencia es el mismo `404`.
- **Contar visitas, elegir moneda, publicar nada más de la persona.** Lo que `RF-PM-008` §4.2 dejó fuera sigue fuera.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-021` | **Por extensión**: el hotlink solo publica el paquete activo y de alcance `HOTLINK` o `AMBOS` | `requirements/pm.md` §5.1 |
| `RN-PM-022` | **Por extensión**: de la persona solo el nombre, y solo si es fuerza comercial | `requirements/pm.md` §5.1 |
| `RN-PM-036` | **El precio se calcula siempre**: la cuenta se hace en esta lectura | `requirements/pm.md` §5.1 |
| `RN-PM-039` | **El paquete no se ofrece si algo suyo dejó de poderse comprar** — aquí, **no se resuelve** | `requirements/pm.md` §5.1 |
| `RN-PM-040` | Un paquete son al menos dos productos — el que quedó con menos después de activarse tampoco se resuelve | `requirements/pm.md` §5.1 |
| `RN-PM-043` | **La cuenta viaja hecha y sin el costo de nadie**: `purchasePrice` no se selecciona | `requirements/pm.md` §5.1 |
| `RN-PM-031`, `RN-PM-032`, `RN-PM-033` | De cada producto salen `rating`, `videoUrl` y `coverImageUrl`, como en el hotlink del producto | `requirements/pm.md` §5.1 |
| `RN-SP-032` | Dos tasas vigentes del mismo par no se solapan | `requirements/sp.md` §5.2 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Nombre de usuario | Sí | De quién es el enlace | Va en la ruta |
| Código del paquete | Sí | Qué se ofrece | Va en la ruta. Se compara **sin distinguir mayúsculas**, como el del producto |

**Ningún parámetro de consulta**, por lo mismo que en `RF-PM-008`.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Vendedor | **Nombre y apellido**, y nada más |
| Paquete | Código, nombre, descripción, moneda (`code`, `decimalPlaces`) |
| `items` | Un elemento por producto, en el orden en que se asociaron: `product` —**exactamente lo que el hotlink del producto publica**: identificador, código, tipo, nombre, descripción, icono, `videoUrl`, `coverImageUrl`, vigencia, membresía destino recortada a código, nombre y color, `price`, moneda y `rating`—, `discount` (`type`, `value`) y **`priceInPackage`** |
| `listPrice` | Σ `product.price` |
| `price` | Σ `priceInPackage` — **lo que costaría el paquete** |
| `savings` | `listPrice − price` |
| `exchange` | La conversión **de `price`** a la moneda por omisión, con la tasa aplicada; **vacía y presente** cuando no hay nada que convertir |

**El producto de cada `item` es la misma forma que el hotlink del producto, y no una segunda.** Lo que se publica de un producto sin token ya se decidió en `RF-PM-008` —enmienda a enmienda: color sin nivel, `price` sin costo, `rating`, video, portada—, y este endpoint **no vuelve a decidirlo**: reutiliza la forma tal cual. El día que aquella cambie, esta cambia con ella.

**Ni `purchasePrice` ni `status`.** El costo no se selecciona (`RN-PM-043`), y el estado no viaja porque **aquí todos los productos están activos**: si alguno no lo estuviera, la respuesta no sería esta sino `404`.

!!! danger "El importe convertido es informativo, y el `price` del paquete también lo es hasta que exista la venta"

    La advertencia de `RF-PM-008` §6.2 vale entera, y aquí hay una segunda: **el paquete no se vende todavía**. `price` es lo que costaría hoy con los precios y descuentos de hoy; el día que la venta multilínea exista, será ella la que congele estos números (`requirements/pm.md` §5.2.10), y hasta entonces la pantalla tiene que decir que es una cuenta y no una reserva.

## 7. Precondiciones y postcondiciones

**Precondiciones:** ninguna. No hay actor que autenticar.

**Postcondiciones:** ninguna. Es una lectura, **no audita** y **no reserva nada**.

## 8. Flujo principal

1. Llega una petición con el nombre de usuario y el código.
2. El sistema resuelve **el vendedor** por `PublicSellerLookup`. Vacío → `404`.
3. El sistema resuelve **el paquete con sus productos** por su código, exigiendo **activo, no retirado y de alcance `HOTLINK` o `AMBOS`**, en una sentencia que trae de cada producto lo que el hotlink del producto trae — `rating` incluido. Vacío → `404`.
4. El sistema decide la **ofrecibilidad** con `PackageOfferability`: si el paquete no es ofrecible hoy —un producto inactivo o retirado, menos de dos productos, sin descripción— → **el mismo `404`**.
5. El sistema calcula, en `PackagePricing`, el precio dentro del paquete de cada producto y los tres totales.
6. El sistema pide la conversión de `price` a la moneda por omisión.
7. Devuelve el vendedor, el paquete, los productos y la conversión.

**El orden es el del coste**, como en `RF-PM-008`: la ofrecibilidad se decide sobre las filas que ya vinieron, sin sentencia propia, y la tasa solo se pide si todo lo anterior salió.

## 9. Flujos alternativos

### FA-001 — No hay tasa vigente, o el paquete ya está en la moneda de casa

**Comportamiento:** el paquete se devuelve igual, con `exchange` **vacía y presente**. Lo mismo que `RF-PM-008` FA-001 y FA-002.

### FA-002 — Llega con un token válido

**Comportamiento:** **la misma respuesta.**

### FA-003 — El fijo de un producto supera hoy su precio

**Comportamiento:** su `priceInPackage` es **cero** (`RN-PM-037`), y la pantalla lo enseña gratis dentro del paquete. Es el hueco temporal declarado, y el hotlink no lo esconde: lo que se publica es la cuenta de hoy.

## 10. Excepciones

### EX-001 — El enlace no lleva a ninguna parte

**Condición:** cualquiera de estos casos — nombre de usuario inexistente; persona que no es fuerza comercial; código inexistente; paquete inactivo; paquete retirado; paquete de alcance `TIENDA`; **paquete no ofrecible hoy** — un producto suyo inactivo o retirado, menos de dos productos, sin descripción —.
**Respuesta del sistema:** `404` con **el mismo cuerpo** en todos, **y el mismo que devuelve el hotlink del producto**. **No dice cuál falló.**

**Los tres últimos casos son nuevos respecto de `RF-PM-008` y responden lo mismo por la misma razón**: distinguir «el paquete no existe» de «el paquete existe y hoy tiene un producto retirado» publicaría, sin token, que el paquete existe y qué le pasa. Quien tiene que saberlo es administración, y lo sabe por `RF-PM-019`, con permiso.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Nombre de usuario con forma admisible | El enlace solicitado no existe. |
| `VAL-002` | Código con forma admisible | El enlace solicitado no existe. |

**Las dos responden `404` y no `400`**, por lo que `RF-PM-008` §11 dejó escrito: en una ruta pública, la forma también es información.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-327` | El sistema devuelve, **sin token**, el vendedor y el paquete en una sola llamada: cada producto con su `priceInPackage`, y `price`, `listPrice` y `savings` que **cuadran** con la suma de las líneas |
| `CA-PM-328` | El sistema responde `404` **con el mismo cuerpo que el hotlink del producto** a: nombre de usuario inexistente, persona que no es fuerza comercial, código inexistente, paquete inactivo, retirado y de alcance `TIENDA` |
| `CA-PM-329` | El sistema responde **el mismo `404`** cuando un producto del paquete está **inactivo** o **retirado** (`RN-PM-039`), y **vuelve a resolver** el enlace cuando ese producto se reactiva |
| `CA-PM-330` | El sistema responde **el mismo `404`** cuando el paquete, activo, quedó con **menos de dos** productos por una desasociación posterior |
| `CA-PM-331` | La respuesta **no lleva** `purchasePrice` en ningún nivel, ni `status` de los productos, ni correo, identificador, estado o roles del vendedor |
| `CA-PM-332` | Cada producto del paquete lleva **lo mismo que el hotlink del producto**: `rating`, `videoUrl`, `coverImageUrl`, y en un upgrade la membresía destino con **código, nombre y color** y sin nivel |
| `CA-PM-333` | La ruta comparte la **cota por origen** del hotlink del producto: agotarla con un enlace de producto deja en `429` el enlace de un paquete desde el mismo origen, y al revés |
| `CA-PM-334` | El sistema devuelve la **tasa aplicada** y el **importe convertido de `price`** cuando hay tasa vigente; `exchange` **vacía y presente** cuando no la hay o el paquete ya está en la moneda de casa; y **lo mismo** con un token válido que sin él |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Un producto del paquete es de alcance **`TIENDA`** | **Se publica dentro del paquete.** El alcance de un producto dice hasta dónde se muestra **por sí solo**; dentro del paquete el canal lo decide el paquete. Un producto que administración no quiere en hotlinks **por sí solo** puede querer venderlo **en paquete** — es, de hecho, para lo que sirve un paquete. Si no lo quiere ni así, el paquete no se pone en `HOTLINKS` |
| El paquete se **desactiva** después de repartir sus enlaces | Dejan de funcionar, con el mismo `404` que uno inventado. Como el producto en `RF-PM-008` |
| Un producto del paquete **cambia de precio** entre dos visitas | La segunda visita muestra otra cuenta. Es `RN-PM-036`: el precio no se guarda |
| El vendedor **deja de serlo** | Los enlaces caducan (`RF-PM-008` §13) |
| El nombre de usuario o el código llevan mayúsculas | Se comparan **sin distinguirlas** |
| Alguien pide un paquete de otro vendedor | **Se devuelve igual**: nada asocia un paquete con una persona, como con los productos (`RF-PM-008` §14.3) |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El paquete no ofrecible responde `404` o se devuelve marcado, como en el detalle? | **`404`, el mismo.** El detalle es administración y dice qué lo detiene; esto es público y decir «existe pero un producto suyo está retirado» publica dos cosas sin token. `RN-PM-039` lo escribe así: **deja de resolverse por hotlink** |
| 2 | ¿El alcance de los productos filtra dentro del paquete? | **No.** Ver §13, primera fila. Filtrarlos dejaría paquetes que no se pueden publicar sin que nada lo dijera —el detalle no lo nombra como motivo—, y el paquete existe precisamente para vender junto lo que no se vende igual por separado |
| 3 | ¿Se reutiliza la forma del producto del hotlink o se define una propia? | **Se reutiliza tal cual.** Todo lo que se decidió publicar de un producto sin token se decidió en `RF-PM-008`, enmienda a enmienda; una segunda forma obligaría a repetir cada enmienda futura dos veces, y la que se olvidara sería la pública |
| 4 | ¿La ruta necesita declararse pública, si `requirements/pm.md` §7 dice que hereda la del producto? | **Sí, y `pm.md` se corrige.** El patrón declarado para el producto es de **dos** segmentos tras la familia y este tiene **tres**; no lo cubre. Lo que sí hereda sin tocar nada es la **cota**, que se aplica por prefijo. Ver `plan.md` §5 |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. Hereda `RF-PM-008` entero y añade lo que un paquete tiene y un producto no: la **cuenta** de `RN-PM-036` y la **ofrecibilidad** de `RN-PM-039`, que aquí es un **`404` uniforme** y no un campo — lo público no dice qué le pasa a lo que no se publica. **El producto de cada línea es la forma del hotlink del producto, reutilizada tal cual**, para que ninguna enmienda futura tenga que hacerse dos veces. **El alcance de los productos no filtra dentro del paquete**: el canal lo decide el paquete. Y una corrección a `pm.md` §7: la ruta **sí estrena declaración pública** —tres segmentos, el patrón del producto cubre dos—, aunque no cota. | Responsable técnico |
| 0.2.0 | 15-09-2026 | **Construida** (`PackageHotlinkIT`, `RateLimitIT`, `EndpointPermissionsIT`). Enmienda de Art. I.7 al construir: **el alcance publicable es `HOTLINK` o `AMBOS`** ([`requirements/pm.md`](../../../requirements/pm.md) v0.35.0 §5.2.11; `TIENDA` y `NINGUNO` responden el `404` uniforme). Lo que §14.4 anunció se confirmó al escribir la prueba sin token: **`SecurityConfig.RUTAS_PUBLICAS` gana `/api/v1/hotlinks/*/packages/*`**, al lado del patrón de dos segmentos y no en su lugar. El cuerpo del `404` se compara con el del hotlink del producto ignorando `instance` y `correlationId`, que son por petición. | Responsable técnico |
