# SPEC — `RF-PM-018` Consultar paquetes

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-018` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |
| Enmendada el | 16-09-2026 — **cada fila trae `coverImageUrl`, la dirección de la portada del paquete** (`RN-PM-045`, `RF-PM-028`). Ver §15 |

---

## 1. Objetivo

Ver y encontrar los paquetes, **incluidos los que no se ofrecen**, con su precio calculado y con la señal de si hoy se pueden ofrecer.

## 2. Contexto

Es `RF-PM-002` para paquetes: el catálogo de administración, paginado, con filtros de lista cerrada y orden total. Hereda de aquel el orden por defecto, el desempate por identificador —UUID v7— y la decisión de que los retirados se excluyen salvo que se pidan; y hereda del detalle (`RF-PM-019`) la cuenta y la ofrecibilidad, que aquí se resuelven **para toda la página en una sentencia**.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador · fuerza comercial | Consulta el catálogo de paquetes |

## 4. Alcance

### 4.1 Incluye

- Devolver los paquetes **paginados**, con filtros por **estado**, **alcance**, **moneda** y búsqueda por **nombre**, e `includeDeleted`.
- Cada fila con: identificador, código, nombre, moneda, alcance, estado, **cuántos productos**, `price`, `listPrice`, `savings`, `offerable` y `deletedAt` si aplica.
- Orden por fecha de alta descendente por omisión, o por nombre o por precio, de una lista cerrada, con el identificador como desempate.

### 4.2 No incluye

- **Los productos de cada paquete.** Es el detalle. La fila trae cuántos son, no cuáles.
- **`offerableReason`.** Uno a uno es una consulta; en bloque sería otra cosa, y el detalle lo dice.
- **Filtrar por `offerable`.** Se publica por fila y no se filtra; el motivo está en §14.1.
- **El motivo del retiro** de los retirados: vive en la auditoría y lo trae el detalle.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-036` | El precio se calcula — para toda la página, en una sentencia de filas | `requirements/pm.md` §5.1 |
| `RN-PM-039` | `offerable` por fila | `requirements/pm.md` §5.1 |
| `RN-PM-041` | Los retirados existen y se listan si se piden | `requirements/pm.md` §5.1 |
| `RN-PM-024` | La conversión sale en toda lectura | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `status` | No | `ACTIVO` o `INACTIVO` | Fuera del dominio, `400` |
| `scope` | No | `TIENDA`, `HOTLINK`, `AMBOS` o `NINGUNO` | Ídem |
| `currencyId` | No | Solo los de esa moneda | UUID |
| `q` | No | Búsqueda por nombre | Sin distinguir mayúsculas ni acentos, por contenido |
| `includeDeleted` | No | Incluir retirados | Por omisión `false` |
| `sort`, `page`, `size` | No | `createdAt` (omisión), `name`, `price`; paginación del sistema | Lista cerrada |

**`offerable` no es filtro**: se queda como columna de la fila. Ver §14.1.

### 6.2 Salida

La envoltura de página del sistema, y en `content` cada paquete con: `id`, `code`, `name`, `currency`, `scope`, `status`, **`itemCount`**, `listPrice`, `price`, `savings`, `exchange`, `offerable`, **`coverImageUrl`** (desde el 16-09-2026), `createdAt`, `deletedAt` (`NON_NULL`).

**`coverImageUrl` es la dirección de la portada del paquete** (`RN-PM-045`): `/api/v1/product-images/{imageId}`, la misma ruta que la del producto, construida sobre `cover_image_id` **sin ninguna consulta más** —los bytes no se seleccionan nunca en un listado— y **presente y nula** cuando el paquete no tiene. No es un filtro.

**La conversión se resuelve como en `RF-PM-002`**: la moneda por omisión una vez por página y las tasas de todas las monedas presentes en una sentencia. **El precio de la página se resuelve en una sentencia de filas** sobre `product_package_items` para todos los identificadores de la página, y `PackagePricing` la agrupa en Java. Ni la cuenta ni la conversión cuestan una consulta por paquete.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `packages:read`. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llega la petición con sus filtros.
2. El sistema valida filtros y paginación **juntos** (§11).
3. El sistema trae la página de paquetes y su total.
4. El sistema trae **en una sentencia** las filas de asociación de todos los paquetes de la página, con el producto de cada una.
5. El sistema calcula por paquete, en `PackagePricing`, los tres totales, y en `PackageOfferability` la señal.
6. El sistema resuelve la conversión para todas las monedas de la página.
7. Devuelve `200`.

## 9. Flujos alternativos

### FA-001 — Página sin paquetes

**Comportamiento:** `content` vacío y total cero.

### FA-002 — Orden por precio

**Comportamiento:** el precio **se calcula**, de modo que no se puede ordenar en la sentencia de paquetes. Ver §14.2: **se ordena en la sentencia de filas** con la suma sin redondear como criterio de la base, y el desempate por identificador. Es el único orden que necesita la segunda sentencia antes que la primera.

## 10. Excepciones

Ninguna de negocio: filtros y paginación inválidos son `400`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Paginación dentro de rango | Los de `shared/pagination` |
| `VAL-002` | `status` dentro del dominio | El estado debe ser ACTIVO o INACTIVO. |
| `VAL-003` | `scope` dentro del dominio | El alcance debe ser TIENDA, HOTLINK, AMBOS o NINGUNO. |
| `VAL-004` | `currencyId` con formato válido | El identificador de la moneda no tiene un formato válido. |
| `VAL-005` | `sort` en la lista cerrada | El campo de ordenamiento no es admitido. |

Las cuatro primeras se devuelven **juntas**.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-269` | El sistema devuelve la página con `itemCount`, `price`, `listPrice`, `savings` y `offerable` por fila, y **cuadran** con el detalle de cada paquete |
| `CA-PM-270` | El sistema **excluye** los retirados salvo `includeDeleted=true`, y entonces los trae con `deletedAt` |
| `CA-PM-271` | Los filtros por estado, alcance, moneda y nombre acotan, y se combinan |
| `CA-PM-272` | El orden por omisión es por fecha de alta descendente con el identificador de desempate; `name` y `price` se admiten; otro campo es `400` |
| `CA-PM-273` | El orden por **precio** ordena por el precio **calculado**, y un paquete cuyo producto cambió de precio **cambia de posición** en la siguiente lectura |
| `CA-PM-274` | El número de sentencias **no crece** con el tamaño de la página: página de uno y de veinte cuestan lo mismo |
| `CA-PM-275` | `exchange` llega resuelto por fila, en una sentencia por página |
| `CA-PM-276` | Los filtros inválidos se devuelven **juntos** con `400`, y sin `packages:read` responde `403` aunque el actor porte `products:read` |
| `CA-PM-367` | Cada fila devuelve **`coverImageUrl`** con la forma `/api/v1/product-images/{uuid}` cuando el paquete tiene portada, y **presente y nula** cuando no, **sin que el número de sentencias suba** (16-09-2026) |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Paquete vacío en la lista | `itemCount` cero, totales cero, `offerable` falso |
| Dos paquetes con el mismo precio | Desempata el identificador |
| `q` con acentos | Se busca sin acentos, como el nombre del producto |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se filtra por `offerable`? | **No.** `offerable` depende del estado de otras filas y se decide en Java; filtrar después de paginar rompería `totalElements`, y filtrar en SQL duplicaría `PackageOfferability`. Se queda como **columna**, y quien necesite «los que no se ofrecen» los ve de un vistazo en la página. Si algún día hace falta, es una vista materializada o un `EXISTS` que repita la regla — y habrá que decidirlo sabiendo que la repite |
| 2 | ¿Cómo se ordena por un precio que no existe en la tabla? | **Con la suma sin redondear en la sentencia de filas**, como criterio de orden y **solo** de orden: el importe que se publica sigue saliendo de `PackagePricing`. La diferencia entre las dos cuentas es el redondeo por producto, que puede alterar el orden entre dos paquetes que difieren en menos de un céntimo. Se acepta y se anota |
| 3 | ¿`offerableReason` en la lista? | **No**: en bloque sería una exportación de por qué falla cada paquete, y el detalle lo dice uno a uno |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. Hereda de `RF-PM-002` el orden total, los retirados bajo petición y la conversión por página; y del detalle, la cuenta y la ofrecibilidad, resueltas **para toda la página en una sentencia de filas**. Dos decisiones propias: **`offerable` es columna y no filtro** —filtrar después de paginar rompería el total, y filtrar en SQL repetiría la regla— y **el orden por precio usa la suma sin redondear como criterio de la base**, con la diferencia de redondeo aceptada y escrita. | Responsable técnico |
| 0.2.0 | 15-09-2026 | **Construida** (`PackageListIT`). Enmiendas de Art. I.7 al construir: **el alcance filtra por los cuatro valores** de [`requirements/pm.md`](../../../requirements/pm.md) v0.35.0; y **el orden por precio se resuelve en la sentencia de paquetes con una subconsulta** —la suma sin redondear, como el plan §4 ya decía— y no invirtiendo el orden de las dos sentencias como sugería `FA-002`: la página cuesta **cuatro** sentencias en cualquier orden (la página, sus filas, el total y la moneda de casa; la tasa solo si hay otra moneda), y `CA-PM-274` las cuenta con una y con veinte. | Responsable técnico |
| 0.3.0 | 16-09-2026 | **Cada fila trae `coverImageUrl`, la dirección de la portada del paquete** (`RN-PM-045`, [`requirements/pm.md`](../../../requirements/pm.md) v0.37.0 §5.2.12): la misma ruta pública que la del producto, sobre `cover_image_id` y sin consulta más; presente y nula cuando no hay. `CA-PM-367`. Enmienda que construye `RF-PM-028` (Art. I.7). | Responsable del proyecto |
