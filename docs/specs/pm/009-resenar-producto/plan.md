# PLAN — `RF-PM-009` Reseñar un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-009` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 14-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 14-09-2026 |

---

## 1. Enfoque

**Una tabla nueva, una regla de unicidad que vive dos veces, y una enmienda a cinco formas de respuesta que no añade ninguna consulta.**

La reseña es la segunda tabla de `PM` y la primera cuya fila pertenece a una persona. El alta es la operación más sencilla del submódulo —validar, comprobar que no hay otra, insertar—, y lo que la hace de este plan y no de una línea es lo que trae alrededor: la migración con su índice parcial, la siembra del permiso con su guarda, y `rating` en las cuatro lecturas del producto sin que ninguna suba de número de sentencias.

**El precedente que se hereda es el del alta del producto** (`RF-PM-001`): la unicidad se comprueba antes para dar un mensaje útil y la sostiene un índice para cerrar la carrera, y el nombre de la restricción se traduce en el repositorio a la misma excepción que la comprobación previa. Aquí el índice es parcial —solo las vivas— y **por parcial no admite `DEFERRABLE`**, de modo que muerde en el `INSERT` y se traduce ahí.

## 2. Cambios de esquema

**Dos migraciones**, por el mismo reparto que `V39` y `V40`: la tabla en una, el permiso en otra.

### `V87__create_product_comments.sql`

```sql
CREATE TABLE product_comments (
    id          uuid        PRIMARY KEY,
    product_id  uuid        NOT NULL,
    user_id     uuid        NOT NULL,
    rating      smallint    NOT NULL,
    comment     text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz NULL,

    CONSTRAINT fk_product_comments_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_comments_user    FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT ck_product_comments_rating  CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_product_comments_comment_length
        CHECK (char_length(btrim(comment)) BETWEEN 1 AND 1000)
);

-- RN-PM-026: una por persona y producto ENTRE LAS VIVAS. Parcial, y por eso
-- no DEFERRABLE: muerde en el INSERT y el repositorio la traduce ahí.
CREATE UNIQUE INDEX uq_product_comments_autor
    ON product_comments (product_id, user_id)
    WHERE deleted_at IS NULL;

-- Sostiene la lista (RF-PM-012) en su orden y el agregado (RN-PM-031).
CREATE INDEX ix_product_comments_product
    ON product_comments (product_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;
```

- **Sin `ON DELETE` en ninguna de las dos claves foráneas**: ni `products` ni `users` se borran físicamente, y declararlo sería documentar un caso que no ocurre.
- **`ck_product_comments_rating` sin rama `IS NULL`**, al revés que la vigencia y el icono del producto: la columna es `NOT NULL`, y un `CHECK` sobre una columna obligatoria no puede evaluar a `NULL`. La advertencia de `ck_deletion_reason` no aplica aquí, y se dice para que nadie añada la rama «por si acaso».
- **`btrim` dentro del `CHECK`** porque el valor se guarda ya recortado (`spec.md` `FA-002`); el `CHECK` es una red, no la regla.

### `V88__seed_products_comment_permission.sql`

Siembra `products:comment` y lo asocia a `SUPERADMIN` y `ADMIN`, **con la guarda de `V60`**: un `DO $$` que cuenta las filas insertadas en `role_permissions` y lanza si no son dos. Olvidar la asociación no falla al aplicar la migración; deja a `ADMIN` incapaz de conceder lo que no tiene ([`security.md` §4.4](../../../security.md)). **A `CLIENTE` no**, por lo mismo que `V48` y `V60`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `ProductComment` — entidad: autor, producto, puntuación, texto, marcas de tiempo, `instantanea()` | `PM` |
| `domain/models` | `RatingSummary` — `average` (`BigDecimal`, dos decimales, nulo) y `count`; `vacio()` para el producto sin reseñas | `PM` |
| `domain/repository` | `ProductCommentRepository` + `JpaProductCommentRepository`: `save`, `existsLiveByProductAndUser`, y la **traducción de `uq_product_comments_autor`** al `409` de `EX-002` | `PM` |
| `domain/repository` | `ProductQueryRepository.ProductRow` gana `ratingAverage` y `ratingCount`; las **cuatro** sentencias —`findPage`, `findById`, `findOffer`, `findPublishedByCode`— ganan el agregado (§4.1) | `PM` |
| `domain/service` | `CreateProductCommentService` | `PM` |
| `application` | `CreateProductCommentRequest`, `ProductCommentResponse` | `PM` |
| `application` | `ProductResponse`, `ProductDetailResponse`, `ProductItem`, `OfferItem`, `HotlinkResponse.ProductRef` ganan `rating` | `PM` |
| `interfaces` | `ProductCommentController` — `POST /api/v1/products/{id}/comments` | `PM` |
| `shared/security` | La ruta en `EndpointPermissionsIT` con su permiso | `shared` |

**`ProductComment` no lleva `@ManyToOne` a `Product` ni a nada de `SP`.** Guarda los dos identificadores, como `Product` guarda los de sus membresías: la regla de ArchUnit que impide a `modules/products` importar entidades de `modules/system` alcanza a esta entidad, y un `@ManyToOne` a `User` la rompería en la primera línea.

## 4. Contrato de API

`POST /api/v1/products/{id}/comments` — `products:comment`.

```json
{ "rating": 5, "comment": "Subí a Oro y las señales llegan a tiempo." }
```

`201`:

```json
{
  "id": "0192…",
  "productId": "0191…",
  "rating": 5,
  "comment": "Subí a Oro y las señales llegan a tiempo.",
  "createdAt": "2026-09-14T15:02:11Z",
  "updatedAt": "2026-09-14T15:02:11Z"
}
```

- **`ProductCommentResponse` es la forma que comparten el alta, la corrección y la lectura de la propia.** Sin autor: quien la recibe es quien la escribió.
- **`rating` entero.** Se declara `Integer` en la petición y `@Min(1) @Max(5)`; un decimal o una cadena no pasan la deserialización y responden `400` por `VAL-003` con el mensaje de la spec, no con el genérico de Jackson — el manejador global ya traduce el fallo de forma por campo.
- **`comment` se recorta antes de validar** y la longitud se mide sobre el recortado: es lo que hace que `VAL-004` y el `CHECK` digan lo mismo.

### 4.1 `rating` en las lecturas del producto

Todas las formas de respuesta del producto ganan:

```json
"rating": { "average": 4.33, "count": 3 }
```

**El agregado entra en las cuatro sentencias por un `LEFT JOIN LATERAL`**, y no por una consulta aparte:

```sql
LEFT JOIN LATERAL (
    SELECT avg(c.rating) AS rating_avg, count(*) AS rating_count
      FROM product_comments c
     WHERE c.product_id = p.id AND c.deleted_at IS NULL
) r ON true
```

- `count(*)` sobre cero filas es **cero** y `avg` es **nulo**: exactamente los dos valores que `RN-PM-031` pide para el producto sin reseñas, sin `COALESCE` y sin caso especial en Java.
- **El redondeo se hace en Java** —`setScale(2, HALF_UP)` en `RatingSummary`— y no en SQL: una sola regla de redondeo, en un solo sitio, y la misma para el `ProductResponse` del alta, que no pasa por ninguna sentencia.
- **El `LATERAL` corre sobre `ix_product_comments_product`**, cuyo primer componente es `product_id` y cuyo predicado es el mismo `deleted_at IS NULL`: es una lectura de índice por producto, no un recorrido.
- **En el alta del producto** (`RF-PM-001`), `ProductResponse.from(Product, …)` pone `RatingSummary.vacio()`: un producto recién creado no tiene reseñas por definición y consultarlo sería pagar una sentencia por un cero.

## 5. Autorización

`@PreAuthorize("hasAuthority('products:comment')")`. El actor se toma de `CurrentActor`, como en `RF-PM-007`: **no hay campo de persona** y la ambigüedad no existe. La ruta entra en `EndpointPermissionsIT` con su permiso, y `ProductsPermissionsSeedIT` pasa de seis a **siete** siembras.

## 6. Auditoría

`ChangeEvent` con `action = CREATE`, `module = "PM"`, `entity = "product_comments"`, y la reseña completa en `changes` — la misma forma que el alta del producto. **Sin evento de seguridad**: una reseña no concede nada.

## 7. Transaccionalidad

`@Transactional`. **Cuatro sentencias**: el producto —activo y no retirado, sin bloqueo: no se escribe sobre él—, la existencia de reseña viva del actor, el `INSERT`, y la auditoría. La carrera entre dos altas del mismo actor la cierra el índice, no un bloqueo: bloquear el producto para escribir una reseña serializaría a todos los que opinan sobre el mismo producto.

**La traducción de `uq_product_comments_autor` vive en `JpaProductCommentRepository.save`**, con el mismo mecanismo que `JpaProductRepository`: se busca la `ConstraintViolationException` en la cadena de causas, se compara el nombre, y se devuelve la `BusinessRuleException` de `EX-002` con el **mismo mensaje** que la comprobación previa. Es el camino de la carrera; el mensaje accionable lo da la comprobación.

## 8. Impacto sobre otros módulos

**Ninguno en código.** `users` se referencia por clave foránea y se lee por `JOIN` solo en `RF-PM-012`; este requerimiento no toca nada de `SP`. **En datos**, la primera fila de `PM` que apunta a una persona: `modelo-datos.md` §5.3 ya lo registra.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`PUT /comments/mine` que cree o corrija** (upsert) | Junta dos operaciones con dos auditorías distintas —`CREATE` y `UPDATE`— y esconde la unicidad: el cliente nunca vería el `409` que le dice que ya opinó. Y el módulo entero separa alta de corrección |
| **Columnas `rating_avg` y `rating_count` en `products`** | Tres operaciones tendrían que mantenerlas y la que se quedara atrás no fallaría, mentiría (`requirements/pm.md` §5.2.7). El coste del agregado es una lectura de índice por producto |
| **Una consulta aparte para el agregado** | Es el `N+1` en el listado y la oferta. `CA-PM-182` lo prueba contando sentencias |
| **Redondear en SQL con `round(avg(...), 2)`** | Dos reglas de redondeo —la del motor y la de Java para el alta— que un día divergirían. Una sola, en `RatingSummary` |
| **`@ManyToOne` a `Product` y a `User`** | Rompe la regla de ArchUnit con `SP`, y con `Product` cargaría la entidad para escribir un identificador |
| **Bloquear el producto al reseñar** | Serializa a todos los que opinan sobre el mismo producto para proteger una unicidad que el índice ya protege |
| **Exigir compra confirmada** | Cierra el ciclo `MV → PM → MV`. Decidido en `requirements/pm.md` §5.2.7 |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Dos altas simultáneas dejan dos reseñas** | `uq_product_comments_autor`, parcial sobre las vivas. `CA-PM-179` lo prueba con dos hilos y espera una fila y un `409` |
| 2 | **El agregado se resuelve fila a fila** en el listado | El `LATERAL` va dentro de las cuatro sentencias; `CA-PM-182` cuenta sentencias en `RF-PM-002` y `RF-PM-007` y exige el número de hoy |
| 3 | **Se olvida la asociación del permiso a `ADMIN`** | La guarda de `V88` falla al aplicar; `ProductsPermissionsSeedIT` lo comprueba además desde la suite |
| 4 | **Una reseña con `<script>` se pinta en una pantalla pública** | El backend no renderiza y no sanea; `spec.md` §14.3 lo deja escrito para el front. La prosa de la `@Operation` de `RF-PM-012` lo repite |
| 5 | **`average` sale con más de dos decimales** por algún camino | Un solo constructor de `RatingSummary` redondea; las cinco formas de respuesta lo usan. `CA-PM-181` comprueba la escala en las cuatro lecturas |
| 6 | **El `CHECK` de longitud y `VAL-005` miden distinto** | Los dos miden sobre el recortado; la prueba envía mil caracteres con espacios alrededor y espera `201` |

## 11. Estrategia de prueba

- **Unitarias**: `ProductComment` —puntuación fuera de rango, texto vacío, recorte— y `RatingSummary` —redondeo, vacío—.
- **Integración de API** (`ProductCommentCreateIT`): los catorce criterios de `spec.md` §12, incluida la prueba del `404` uniforme comparando el cuerpo de los tres casos.
- **Concurrencia** (`ProductCommentConcurrencyIT`): dos hilos, el mismo actor, el mismo producto → una fila y un `409` (`CA-PM-179`).
- **Siembra** (`ProductsPermissionsSeedIT`): siete permisos y sus asociaciones.
- **`rating` en las lecturas**: en `ProductListIT`, `ProductDetailIT`, `ProductOfferIT` y `HotlinkIT`, una prueba cada uno con reseñas y sin ellas (`CA-PM-180`, `CA-PM-181`), y **las pruebas de número de sentencias existentes no cambian de número** (`CA-PM-182`).
- **Contrato**: `OpenApiContractIT` regenera el esquema; la prosa de la `@Operation` dice que el autor sale del token, que el `404` no distingue, y que `rating` es de las reseñas vivas.
