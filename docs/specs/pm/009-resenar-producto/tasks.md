# TASKS — `RF-PM-009` Reseñar un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-009` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 14-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración **`V87__create_product_comments.sql`**: la tabla, las dos claves foráneas **sin `ON DELETE`**, los dos `CHECK`, el índice único **parcial** `uq_product_comments_autor` y el índice `ix_product_comments_product` | — | Integración: la migración aplica sobre el esquema de `V86`; un `rating` de `0` y de `6` se rechazan; un `comment` de mil espacios se rechaza; dos vivas del mismo par se rechazan y una viva más una retirada **no** | Pendiente |
| `T-02` | Migración **`V88__seed_products_comment_permission.sql`**: siembra `products:comment`, lo asocia a `SUPERADMIN` y `ADMIN`, **con la guarda de `V60`** que cuenta las dos filas y lanza si faltan | — | `ProductsPermissionsSeedIT` pasa de seis a **siete** siembras y comprueba las dos asociaciones; `CLIENTE` no lo tiene | Pendiente |
| `T-03` | `domain/models/ProductComment`: entidad con `productId`, `userId`, `rating`, `comment`, marcas de tiempo; el constructor **recorta y valida** (`RN-PM-025`); `esDe(actor)`, `instantanea()` | `T-01` | Unitaria: puntuación fuera de rango y texto vacío o de mil y uno lanzan `ValidationException` con los códigos de `VAL-003` a `VAL-005`; el texto se guarda recortado | Pendiente |
| `T-04` | `domain/models/RatingSummary`: `average` a **dos decimales `HALF_UP`** y `count`; `vacio()`; `de(BigDecimal avgBruto, long count)` | — | Unitaria: `4.335` → `4.34`; `vacio()` es `(null, 0)`; nunca se construye con `average` no nulo y `count` cero | Pendiente |
| `T-05` | `domain/repository/ProductCommentRepository` + `JpaProductCommentRepository`: `save`, `existsLiveByProductAndUser`, y la **traducción de `uq_product_comments_autor`** a la `BusinessRuleException` de `EX-002`, con el mismo mensaje que la comprobación previa | `T-01`, `T-03` | Integración: un `INSERT` duplicado del par vivo llega al servicio como `EX-002` y no como `DataIntegrityViolationException` | Pendiente |
| `T-06` | `ProductQueryRepository.isPurchasable(productId)`: existe, `ACTIVO`, `deleted_at IS NULL`. Una lectura por clave, sin `JOIN` | — | Integración: verdadero solo para el activo no retirado; falso para inactivo, retirado e inexistente | Pendiente |
| `T-07` | `application/CreateProductCommentRequest` (`@NotNull @Min(1) @Max(5) Integer rating`, `@NotBlank String comment`) y `application/ProductCommentResponse` — **sin autor** | — | El contrato declara los dos obligatorios; un `rating` decimal o cadena responde `400` por `VAL-003` con el mensaje de la spec | Pendiente |
| `T-08` | `domain/service/CreateProductCommentService`: actor del token, producto comprable (`EX-001`, **un solo mensaje para los tres casos**), sin reseña viva (`EX-002`), inserción, auditoría `CREATE` en la misma transacción | `T-03`, `T-05`, `T-06`, `T-07` | `CA-PM-170`, `CA-PM-173`, `CA-PM-175`, `CA-PM-178` | Pendiente |
| `T-09` | `interfaces/ProductCommentController`: `POST /api/v1/products/{id}/comments`, `@PreAuthorize("hasAuthority('products:comment')")`, `201` | `T-08` | `CA-PM-176`, `CA-PM-177`, `CA-PM-183`. La ruta entra en `EndpointPermissionsIT` con su permiso | Pendiente |
| `T-10` | **`rating` en `ProductRow` y en las CUATRO sentencias** de `JpaProductQueryRepository` —`findPage`, `findById`, `findOffer`, `findPublishedByCode`— por el `LEFT JOIN LATERAL` de `plan.md` §4.1 | `T-01`, `T-04` | Las pruebas de número de sentencias de `ProductListIT` y `ProductOfferIT` **no cambian de número** (`CA-PM-182`) | Pendiente |
| `T-11` | **`rating` en las cinco formas de respuesta**: `ProductResponse` —con `RatingSummary.vacio()` desde `from(Product, …)` en el alta—, `ProductDetailResponse`, `ProductItem`, `OfferItem`, `HotlinkResponse.ProductRef` | `T-04`, `T-10` | `CA-PM-180` y `CA-PM-181` en `ProductListIT`, `ProductDetailIT`, `ProductOfferIT` y `HotlinkIT` — este último **sin token**. El alta del producto (`ProductsIT`) devuelve `rating` presente con `average` nulo y `count` cero | Pendiente |
| `T-12` | **La prueba de la carrera** (`ProductCommentConcurrencyIT`): dos hilos, el mismo actor, el mismo producto | `T-09` | `CA-PM-179`: una fila, un `201`, un `409` — y **no** un `500` | Pendiente |
| `T-13` | Pruebas de API de los criterios restantes (`ProductCommentCreateIT`): validaciones, el `404` uniforme **comparando el cuerpo**, la reseña nueva tras retirar, el administrador | `T-09` | `CA-PM-171`, `CA-PM-172`, `CA-PM-174`, `CA-PM-175`, `CA-PM-183` | Pendiente |
| `T-14` | Documentación OpenAPI. **La prosa dice** que el autor sale del token, que el `404` no distingue los tres casos, que `rating` cuenta solo las vivas y que **el texto no se sanea** | `T-09`, `T-11` | `OpenApiContractIT` regenera `docs/api/openapi.json` y `.yaml` con `rating` en las cinco formas; la prosa de las `@Operation` de las cuatro lecturas nombra `rating` | Pendiente |
| `T-15` | **Enmiendas Art. I.7** por `rating`: fila en el control de cambios de las specs de `RF-PM-001` a `RF-PM-004`, `RF-PM-007` y `RF-PM-008`, citando esta tripleta | — | Las seis specs nombran `rating` en su §6.2 y en su §15 | **Hecha el 14-09-2026** — se aplicó con el plan y no con el código: una enmienda que un plan declara se aplica en el mismo pase |
| `T-16` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-13` | La fila de `RF-PM-009` refleja el estado; `api/index.md` documenta el endpoint y `rating` | Pendiente |

## 2. Orden de ejecución

**`T-01` y `T-02` primero**, y `T-02` con su guarda antes de escribir una línea de Java: si la asociación a `ADMIN` falta, que falle la migración y no una prueba tres días después.

**`T-10` y `T-11` van antes que el servicio de alta**, aunque parezca al revés: son la enmienda a lo ya construido, y hacerla primero deja las cuatro suites de lecturas en verde con `rating` vacío **antes** de que exista ninguna reseña. Si algo rompe el número de sentencias, se ve ahí, sin el ruido del alta.

**`T-12` no se deja para el final.** El índice parcial existe para la carrera, y la carrera solo se ve con dos hilos.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-170` | `T-08`, `T-09` |
| `CA-PM-171`, `CA-PM-172` | `T-03`, `T-07`, `T-13` |
| `CA-PM-173` | `T-05`, `T-08` |
| `CA-PM-174` | `T-01`, `T-13` |
| `CA-PM-175` | `T-06`, `T-08`, `T-13` |
| `CA-PM-176`, `CA-PM-177`, `CA-PM-183` | `T-09`, `T-13` |
| `CA-PM-178` | `T-08` |
| `CA-PM-179` | `T-05`, `T-12` |
| `CA-PM-180`, `CA-PM-181` | `T-10`, `T-11` |
| `CA-PM-182` | `T-10` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-10` y `T-11` tocan **cuatro requerimientos construidos** y sus suites. Cualquier regresión allí es responsabilidad de este requerimiento | 14-09-2026 | Responsable técnico | **Abierto** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **incluidas las cuatro suites de lecturas sin cambio en su número de sentencias**.
- [ ] `V88` aplica con su guarda y `ProductsPermissionsSeedIT` cuenta siete.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**, y `rating` figura en las cinco formas.
- [ ] Las seis tripletas enmendadas por `rating` citan esta.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
