# PLAN — `RF-PM-002` Consultar productos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-002` |
| Especificación | [`spec.md`](spec.md) v0.3.0 |
| `spec.md` aprobada el | 26-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
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

`GET /api/v1/products?type=&status=&targetMembershipId=&search=&includeDeleted=&sort=&page=&size=`

- **El orden por omisión es `createdAt` descendente, con `id` como desempate.** El desempate no es cosmético: sin un orden **total**, dos productos con el mismo instante de alta pueden repetirse o saltarse entre páginas, y eso se descubre como «faltan productos» sin ningún error de por medio. Sale gratis: el identificador es un UUID v7 y su orden **es** el cronológico.
- **`sort` es un dominio cerrado** —`name`, `price`, `createdAt`— y un valor fuera de él devuelve `400` (`VAL-005`). Se rechaza y no se ignora: ignorarlo devolvería un orden distinto del pedido sin decirlo.
- **Los cuatro `400` se devuelven juntos**, como en `RF-SP-002`: quien se equivocó en cuatro parámetros no tiene que corregir la dirección cuatro veces.
- `includeDeleted` por omisión es `false`.

La respuesta es un `PageResponse<ProductItem>` con `totalIsExact` en `true`.

**El conteo es exacto y no acotado**, al revés que en los cuatro listados de auditoría. La diferencia es el tamaño esperado de la tabla: un catálogo comercial tiene decenas o cientos de filas, no millones, y `BoundedCount` existe para tablas que crecen sin límite con el uso. Queda anotado el disparador de cambiarlo: **si `products` llegara a decenas de miles**, este conteo pasa a `BoundedCount` como el de la auditoría.

!!! warning "El atajo del conteo NO se aplica aquí"

    `RF-SP-002` dejó escrito un defecto que conviene no repetir: «omitir el conteo cuando la página no se llena» es correcto **salvo en la página vacía más allá de la última**, donde deducir el total del desplazamiento da un número inventado —`1980` para la página 99 de un catálogo de doce— con la colección vacía y sin error que lo delate. Aquí se cuenta siempre.

## 5. Autorización

`products:read` sobre el método. **Ver los retirados no exige permiso propio** (`spec.md` §14, resolución 3): basta el de lectura.

## 6. Auditoría

Ninguna. Una consulta de catálogo no es un evento de seguridad; el único listado que se audita a sí mismo es el de seguridad de `RF-SP-014`, donde mirar **es** información.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. Una sola sentencia para la página y otra para el conteo.

## 8. Impacto sobre otros módulos

**Ninguno.** El destino de cada upgrade se resuelve con un `LEFT JOIN` a `memberships` **dentro de la misma sentencia**, y no llamando al puerto de `SP` una vez por fila.

!!! important "Por qué aquí sí hay `JOIN` y no puerto, y no contradice a D-25"

    D-25 gobierna el **código**: `PM` no importa repositorios ni entidades de `SP`. Este `JOIN` es SQL de una consulta de lectura, y la alternativa —llamar al puerto por cada producto de la página— es el problema de las N+1 consultas con otro nombre: cien productos, cien llamadas.

    La frontera se mantiene donde importa: **ninguna regla se decide con ese `JOIN`**. Lo que valida que el destino existe sigue siendo el puerto, en `RF-PM-001`; aquí solo se pinta un nombre junto a un identificador que ya está en la fila.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Orden solo por `createdAt`, sin desempate | Paginación inestable: filas repetidas u omitidas entre páginas, sin ningún error |
| `sort` como campo libre | Deja ordenar por lo que nadie revisó. `RF-SP-025` lo prohibió por un motivo peor: ordenar por la marca de cambio obligatorio producía la lista de quién no ha cambiado su contraseña |
| Resolver el destino con el puerto de `SP`, fila a fila | N+1 consultas por página |
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
| Una sola sentencia por consulta | Integración | Con y sin filtros |
| Uso efectivo del índice | Integración | `EXPLAIN` con volumen sembrado |
