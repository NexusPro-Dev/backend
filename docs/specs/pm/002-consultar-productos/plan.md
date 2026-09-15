# PLAN — `RF-PM-002` Consultar productos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-002` |
| Especificación | [`spec.md`](spec.md) v0.4.0 |
| `spec.md` aprobada el | 26-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Enmendado el | 12-09-2026 — **el segundo precio es el de COMPRA** (`purchasePrice`, `RN-PM-023`, `RN-PM-024`), §4; 08-09-2026 — **los dos precios en cada fila** (`RN-PM-023`, `RN-PM-024`), §4; 27-08-2026 — `RN-PM-015`; 02-09-2026 — la membresía de **origen** (`RN-PM-017`, `RN-PM-018`); 07-09-2026 — los filtros de **alcance** e **implementación** (`RN-PM-019`, `RN-PM-020`); 14-09-2026 — **`videoUrl` en cada fila** (`RN-PM-032`), §4; 14-09-2026 — **`coverImageUrl` en cada fila** (`RN-PM-033`), §4; 15-09-2026 — **el alcance de cuatro valores** en el filtro (`RN-PM-019`) |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Enfoque

Una consulta de lectura sobre `products`, **paginada, en una sola sentencia**, con cinco filtros y un orden configurable. No toca dominio: es un modelo de lectura que va del repositorio al controlador sin pasar por el agregado.

Reutiliza entera la infraestructura de paginación de `shared/pagination`, que `RF-SP-025` estrenó: una petición que excede el tamaño máximo se **rechaza y no se recorta**, porque recortarla en silencio haría que quien pide doscientos elementos reciba cien y crea que solo hay cien.

## 2. Cambios de esquema

**Ninguna tabla nueva.** Una migración de índices, `V41__create_products_search_index.sql`:

| Índice | Sobre | Por qué |
|---|---|---|
| `ix_products_busqueda` | Trigramas sobre `f_unaccent(lower(name))` | La búsqueda parcial insensible a mayúsculas y acentos no puede usar el índice único —que es de igualdad—, y sin él cada búsqueda recorre la tabla entera |
| `ix_products_listado` | `(created_at DESC, id DESC)` | Es el orden por omisión, y el que más se pide |

**No se indexa `type` ni `status`.** Son dominios de dos y tres valores: un índice sobre una columna con dos valores distintos no aporta selectividad y el planificador lo ignorará. Se anota para que nadie lo añada creyendo que falta.

## 3. Componentes afectados

| Capa | Componente | Responsabilidad |
|---|---|---|
| `application` | `ListProductsRequest` | Los cinco filtros, la paginación y el orden |
| `application` | `ProductItem`, `ProductPageResponse` | Modelo de lectura y su envoltura |
| `application` | `ProductSortField` | **Dominio cerrado** del ordenamiento |
| `domain/repository` | `ProductQueryRepository` + adaptador | Una sentencia, con predicado dinámico |
| `domain/service` | `ListProductsService` | `@Transactional(readOnly = true)` |
| `interfaces` | `ProductController` | `GET /api/v1/products` |

## 4. Contrato de API

`GET /api/v1/products?type=&status=&scope=&implementation=&sourceMembershipId=&targetMembershipId=&search=&includeDeleted=&sort=&page=&size=`

- **El orden por omisión es `createdAt` descendente, con `id` como desempate.** El desempate no es cosmético: sin un orden **total**, dos productos con el mismo instante de alta pueden repetirse o saltarse entre páginas, y eso se descubre como «faltan productos» sin ningún error de por medio. Sale gratis: el identificador es un UUID v7 y su orden **es** el cronológico.
- **`sort` es un dominio cerrado** —`name`, `price`, `createdAt`— y un valor fuera de él devuelve `400` (`VAL-005`). Se rechaza y no se ignora: ignorarlo devolvería un orden distinto del pedido sin decirlo. **`purchasePrice` NO se añade al dominio** (08-09-2026, confirmado el 12-09-2026 con el nombre nuevo): **ordenar por una columna que admite nulos abriría una decisión que nadie ha tomado** — dónde van los productos sin costo conocido, al principio o al final. Se amplía el día que alguien lo pida, y entonces con esa decisión escrita.
- **`exchange` viaja en cada fila desde el 08-09-2026** (`RN-PM-024` reescrita), **presente y nulo** cuando el producto ya está en la moneda por omisión o cuando no hay tasa vigente. Cómo se resuelve sin una consulta por fila está en §4.1, y es la parte de este cambio que puede salir mal en silencio.
- **`coverImageUrl` viaja en cada fila** (14-09-2026, `RN-PM-033`, enmienda de `RF-PM-014`): `ProductRow` gana `coverImageId`, el `SELECT` la columna `p.cover_image_id` —**y nada de `product_images`**: ni un `JOIN`, ni el tipo, ni los bytes—, y `ProductItem` la convierte con `ProductImageUrls.de(...)` en la ruta `/api/v1/product-images/{id}`, **presente y nula** cuando no hay. Es una ruta y no una URL absoluta porque el backend no sabe bajo qué dominio lo sirven y el cliente ya conoce la base. **Tampoco es un filtro.**
- **`videoUrl` viaja en cada fila** (14-09-2026, `RN-PM-032`) y **tampoco es un filtro**: `ProductRow` gana el campo, el `SELECT` la columna, y `ProductItem` lo copia tal cual —**presente y nulo** cuando no hay—. Nada que convertir ni redondear: es texto.
- **`purchasePrice` viaja en cada fila** y **no es un filtro**: se selecciona en la misma sentencia, junto a `price`, y se serializa con los decimales de la misma moneda. La proyección crece en un campo; la consulta no gana ni un `JOIN` ni una condición. **Es el precio de compra desde el 12-09-2026** —lo que NEXUS paga—, y este listado lo devuelve porque exige `products:read`; las dos lecturas sin permiso de administración **no lo seleccionan** (`RN-PM-024`).
- **La conversión se calcula sobre `price`**, siempre (12-09-2026). Hasta esa fecha se calculaba sobre «el importe que se muestra» —el público si existía—; con el segundo importe convertido en costo no hay nada que elegir, y `ProductExchangeResolver.importeMostrado` desaparece.
- **Los cuatro `400` se devuelven juntos**, como en `RF-SP-002`: quien se equivocó en cuatro parámetros no tiene que corregir la dirección cuatro veces.
- `includeDeleted` por omisión es `false`.
- **`scope` e `implementation` entran como filtros el 07-09-2026**, y se validan **exactamente como `type` y `status`**: llegan al mandato como **texto y no como enumerado** —enlazarlos como enumerado dejaría que Spring rechazara el valor fuera de dominio **antes** del caso de uso, y el rechazo saldría solo en lugar de junto a los demás, que es lo que `CA-PM-020` no admite—, se comprueban contra su dominio y se **normalizan a su forma canónica**. Lo segundo no es un adorno: validar sin normalizar deja pasar `scope=tienda`, que después no coincide con ninguna fila, y el actor recibe `200` con la colección vacía en vez de sus productos. Reutilizan el mismo ayudante que ya sirve a los otros dos, de modo que el filtro nuevo no trae lógica nueva.
- **No se indexan**, por lo mismo que `type` y `status`: dos valores no dan selectividad, y un índice sobre ellos costaría escritura sin ahorrar una sola lectura.

La respuesta es un `PageResponse<ProductItem>` con `totalIsExact` en `true`.

**El conteo es exacto y no acotado**, al revés que en los cuatro listados de auditoría. La diferencia es el tamaño esperado de la tabla: un catálogo comercial tiene decenas o cientos de filas, no millones, y `BoundedCount` existe para tablas que crecen sin límite con el uso. Queda anotado el disparador de cambiarlo: **si `products` llegara a decenas de miles**, este conteo pasa a `BoundedCount` como el de la auditoría.

!!! warning "El atajo del conteo NO se aplica aquí"

    `RF-SP-002` dejó escrito un defecto que conviene no repetir: «omitir el conteo cuando la página no se llena» es correcto **salvo en la página vacía más allá de la última**, donde deducir el total del desplazamiento da un número inventado —`1980` para la página 99 de un catálogo de doce— con la colección vacía y sin error que lo delate. Aquí se cuenta siempre.

## 4.1 La conversión de una página, y por qué se resuelve fuera de la consulta — 08-09-2026

**`RN-PM-024` reescrita obliga a que cada fila lleve `exchange`**, y esa es la parte del cambio que puede salir mal sin que nada falle: un listado de veinte productos que pregunte por fila **la moneda de casa** y **la tasa** son **cuarenta consultas** donde había una, y la respuesta sería idéntica. Es el `N+1` que este módulo lleva seis requerimientos evitando, y aquí no lo destapa ninguna prueba de cuerpo.

**Se resuelve en tres pasos y DOS consultas como mucho, sean veinte filas o cien:**

1. La página se lee como hasta hoy — **una sentencia**, con sus `JOIN`.
2. **La moneda por omisión, una vez**: `CurrencyCatalog.findDefault()`.
3. **Las tasas de todas las monedas presentes en la página, en una sentencia**: `ExchangeRateLookup.ratesOn(monedasDistintas, monedaDeCasa, hoy)` devuelve un mapa `moneda → tasa`, y cada fila busca la suya en memoria. **Este paso se salta entero** cuando todos los productos ya están en la moneda de casa —el caso normal de un catálogo de una sola moneda—, porque no hay nada que convertir y `RN-SP-029` impide que exista una tasa de una moneda a sí misma.

**El puerto de `SP` gana el método por lotes** y no se llama al de una en un bucle: `rateOn` sigue existiendo para el detalle y el hotlink, que leen **una** fila. Poner el bucle en `PM` dejaría la decisión de cuántas sentencias cuesta una página **fuera** del módulo que las paga.

!!! danger "La prueba de este apartado NO mira el cuerpo"

    `CA-PM-165` y `CA-PM-168` se miden **contando sentencias** con las estadísticas de Hibernate, como ya hacen `ListProductsServiceIT` y `GetProductServiceIT`. Es la única forma de verlo: veinte productos con la conversión correcta se ven exactamente igual con dos consultas que con cuarenta, y el día que alguien «simplifique» el resolutor a un bucle, ninguna prueba de API se enteraría.

**Lo que la conversión NO hace es fallar la lectura.** Si no hay moneda por omisión, o no hay tasa vigente para una moneda, esa fila lleva `exchange` **presente y nulo**. Un catálogo que devolviera `500` porque nadie declaró una tasa sería un catálogo rehén de otro módulo.

## 5. Autorización

`products:read` sobre el método. **Ver los retirados no exige permiso propio** (`spec.md` §14, resolución 3): basta el de lectura.

## 6. Auditoría

Ninguna. Una consulta de catálogo no es un evento de seguridad; el único listado que se audita a sí mismo es el de seguridad de `RF-SP-014`, donde mirar **es** información.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. Una sola sentencia para la página y otra para el conteo.

## 8. Impacto sobre otros módulos

**Ninguno.** Las **dos** membresías de cada upgrade —origen y destino— se resuelven con sendos `LEFT JOIN` a `memberships` **dentro de la misma sentencia**, y no llamando al puerto de `SP` una vez por fila. Son dos uniones y no dos consultas: el coste de la página sigue siendo **una** sentencia, y el del listado entero **dos** con el conteo.

!!! important "Por qué aquí sí hay `JOIN` y no puerto, y no contradice a D-25"

    D-25 gobierna el **código**: `PM` no importa repositorios ni entidades de `SP`. Este `JOIN` es SQL de una consulta de lectura, y la alternativa —llamar al puerto por cada producto de la página— es el problema de las N+1 consultas con otro nombre: cien productos, cien llamadas.

    La frontera se mantiene donde importa: **ninguna regla se decide con esos `JOIN`**. Lo que valida que las dos membresías existen, y que el origen está por debajo del destino, sigue siendo el puerto, en `RF-PM-001`; aquí solo se pinta un nombre junto a un identificador que ya está en la fila.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Orden solo por `createdAt`, sin desempate | Paginación inestable: filas repetidas u omitidas entre páginas, sin ningún error |
| `sort` como campo libre | Deja ordenar por lo que nadie revisó. `RF-SP-025` lo prohibió por un motivo peor: ordenar por la marca de cambio obligatorio producía la lista de quién no ha cambiado su contraseña |
| Resolver las dos membresías con el puerto de `SP`, fila a fila | N+1 consultas por página, y con el origen serían **2N+1** |
| Excluir siempre los eliminados | Impediría entender por qué un producto dejó de venderse, que es media razón de existir de este listado |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | El índice de trigramas no se usa con pocas filas y la prueba de `EXPLAIN` no lo demuestra | Se siembra volumen a propósito en la prueba, como `RF-SP-021` · `T-11`. Es el mismo hueco que `SP` lleva abierto: **si no se escribe, el síntoma será lentitud y no un fallo** |
| 2 | Un término de búsqueda con comodines amplía la consulta a todo el catálogo | Escape explícito de `\`, `%` y `_`, y `ESCAPE` declarado en la sentencia — no heredado de la configuración del motor |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| Los diez criterios de `spec.md` §12 | API | Con filtros combinados |
| Orden por omisión y configurable | API | Y el campo fuera de la lista devuelve `400` |
| **Paginación estable** | Integración | Se recorren todas las páginas con varios productos del mismo instante y se comprueba que no falta ni se repite ninguno (`CA-PM-076`) |
| Búsqueda insensible a mayúsculas y acentos | API | Incluido el término con comodines |
| Los retirados fuera salvo petición expresa | API | Y sin motivo del retiro en el listado |
| El enlace del video en cada fila | API | Un producto con enlace y otro sin él en la misma página: el primero lo trae tal cual, el segundo **presente y nulo** (`CA-PM-223`) |
| Una sola sentencia por consulta | Integración | Con y sin filtros |
| Uso efectivo del índice | Integración | `EXPLAIN` con volumen sembrado |
