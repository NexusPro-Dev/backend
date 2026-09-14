# TASKS — `RF-PM-014` Subir o reemplazar la portada de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-014` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 14-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 14-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V90__product_images.sql`**: tabla `product_images` con `ck_product_images_content_type` y `ck_product_images_size`; `products.cover_image_id` con `fk_products_cover_image` (sin `ON DELETE`) y `uq_products_cover_image`; `COMMENT ON` | — | La migración aplica sobre una base con productos; un `INSERT` de cero bytes y uno de `image/gif` se rechazan | **Hecha el 14-09-2026** |
| `T-02` | `application.yml`: `spring.servlet.multipart.max-file-size: 6MB`, `max-request-size: 7MB`, con el comentario de **por qué van por encima** del tope de negocio | — | Un archivo de 5 242 880 bytes llega al controlador | **Hecha el 14-09-2026** |
| `T-03` | `domain/models/ImageSignature`: detecta `JPEG`, `PNG` y `WebP` por sus primeros bytes y devuelve el `content_type`; vacío para todo lo demás | — | Unitaria: las tres firmas, `GIF`, `SVG`, texto, vacío, un byte | **Hecha el 14-09-2026** |
| `T-04` | `domain/models/ProductImage`: entidad inmutable (`id` v7, `contentType`, `content`, `createdAt`); `ProductImage.de(bytes, ahora)` lanza `VAL-002` (vacío), `VAL-004` (más de 5 242 880) y `VAL-003` (firma desconocida), **en ese orden** y nombrando `file`. **Javadoc: nunca en una lectura del catálogo** | `T-03` | Unitaria: los tres rechazos, el orden, y 5 242 880 bytes que caben | **Hecha el 14-09-2026** |
| `T-05` | `Product`: columna `coverImageId`; `asignarPortada(UUID)` devuelve el anterior y el diff `cover_image_id`; `getCoverImageId()`; `instantanea()` gana `cover_image_id` | — | Unitaria en `ProductTest`: anterior nulo y no nulo, el diff con los dos, la instantánea | **Hecha el 14-09-2026** |
| `T-06` | **`RN-PM-034` en `Product.create`**: un upgrade sin icono se rechaza con `VAL-018` nombrando `icon`; el bot sin icono se registra (enmienda a `RF-PM-001`, `T-40` de aquella) | `T-05` | `CA-PM-230`, `CA-PM-231` | **Hecha el 14-09-2026** |
| `T-07` | **`RN-PM-034` en `Product.update`**: vaciar el icono de un upgrade **sin portada** se rechaza con `VAL-010` nombrando `icon` y **sin aplicar nada**; con portada se vacía; en un bot `icon: null` sigue siendo un vaciado sin cambio (enmienda a `RF-PM-004`, `T-24` de aquella) | `T-05` | `CA-PM-234`, `CA-PM-235` | **Hecha el 14-09-2026** |
| `T-08` | `domain/repository/ProductImageRepository` y `JpaProductImageRepository`: `save`, `findById`, `deleteById`. **Sin métodos de listado** | `T-04` | Compila; la regla de ArchUnit del módulo lo admite | **Hecha el 14-09-2026** |
| `T-09` | `ProductQueryRepository.ProductRow` gana `coverImageId`; las **cuatro** sentencias de `JpaProductQueryRepository` seleccionan `p.cover_image_id` y **nada de `product_images`** | `T-01` | La prueba de sentencias de `RF-PM-002` no sube | **Hecha el 14-09-2026** |
| `T-10` | `application/ProductImageUrls.de(UUID)` → `/api/v1/product-images/{id}` o nulo; `coverImageUrl` en `ProductResponse`, `ProductItem`, `ProductDetailResponse`, `OfferItem` y `ProductRef`, **siempre presente** | `T-09` | Las cinco formas del contrato declaran el campo; nulo cuando no hay | **Hecha el 14-09-2026** |
| `T-11` | `domain/service/UploadProductCoverService`: construir `ProductImage.de` **antes** de tocar la base; `findAliveByIdForUpdate` (`EX-001`); `save` de la nueva; `asignarPortada`; **`deleteById` de la anterior después**; `ChangeEvent` `UPDATE` con solo `cover_image_id`; devolver el detalle | `T-04`, `T-05`, `T-08` | `CA-PM-239`, `CA-PM-240`, `CA-PM-241`, `CA-PM-247` | **Hecha el 14-09-2026** |
| `T-12` | `ProductController`: `PUT /api/v1/products/{id}/cover`, `consumes = multipart/form-data`, `@RequestPart(value = "file", required = false)`, `@PreAuthorize("hasAuthority('products:update')")`, `200` | `T-11` | La ruta entra en `EndpointPermissionsIT` con su permiso | **Hecha el 14-09-2026** |
| `T-13` | `GlobalExceptionHandler`: `MaxUploadSizeExceededException` → `400` `VAL-004` nombrando `file`; `MultipartException` / `HttpMediaTypeNotSupportedException` en esa ruta → `400` `EX-003` | `T-12` | `CA-PM-244` (no `multipart`) y el caso límite de 50 MB de `spec.md` §13 | **Hecha el 14-09-2026** |
| `T-14` | **LA PRUEBA DE LOS BYTES** (`ProductCoverIT`): `JPEG` enviado como `image/png` y `foto.png` se guarda como `image/jpeg`; `GIF`, `SVG` y texto como `image/png` → `VAL-003` | `T-12` | `CA-PM-242`. **Es la prueba que define el requerimiento** | **Hecha el 14-09-2026** |
| `T-15` | Prueba del reemplazo: otra dirección, la fila anterior no existe, **una** fila por producto, auditoría con los dos identificadores | `T-12` | `CA-PM-240`, `CA-PM-241` | **Hecha el 14-09-2026** |
| `T-16` | Pruebas de los topes: 5 242 880 → `200`; 5 242 881 → `VAL-004`; sin parte y parte vacía → `VAL-002`; nada queda escrito tras un rechazo | `T-12`, `T-13` | `CA-PM-243`, `CA-PM-244`, `CA-PM-247` | **Hecha el 14-09-2026** |
| `T-17` | Pruebas de producto: inexistente y retirado → `404` con el mismo cuerpo; inactivo, `BOT` y upgrade sin icono → `200` | `T-12` | `CA-PM-245`, `CA-PM-246` | **Hecha el 14-09-2026** |
| `T-18` | Prueba de las cuatro lecturas tras subir, y `coverImageUrl` presente y nulo sin portada — la propia (`CA-PM-248`) y una en cada suite de lecturas (enmiendas: `CA-PM-232`, `233`, `237`, `238`) | `T-10`, `T-12` | Las cuatro suites en verde con el campo | **Hecha el 14-09-2026** |
| `T-19` | Documentación OpenAPI. **La prosa dice** que el tipo lo deciden los bytes y cuáles son, el tope de 5 MB, que reemplaza y la dirección cambia, y que el alta no admite la imagen. El contrato declara el `requestBody` `multipart/form-data` con la parte `file` | `T-12` | `OpenApiContractIT` regenera; el primer `multipart` del contrato | **Hecha el 14-09-2026** |
| `T-20` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md` y `docs/modelo-datos.md` (la tabla pasa de diseñada a escrita) | `T-18` | Las tres filas reflejan el estado | **Hecha el 14-09-2026** |

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06` → `T-07` → `T-08` → `T-09` → `T-10` → `T-11` → `T-12` → `T-13`, y **`T-14` y `T-15` inmediatamente después**: son las dos que definen el requerimiento — qué es el archivo, y qué queda cuando se reemplaza.

**`T-06` y `T-07` van antes que el servicio de subida** aunque sean enmiendas a otros requerimientos: `RN-PM-034` tiene que existir en el agregado antes de que exista una portada con la que no tener icono, porque `RF-PM-015` la necesita entera.

`T-18` cierra la construcción de `RN-PM-033` en las lecturas; **`RF-PM-016` va después de este requerimiento** y antes de que nadie pinte la dirección.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-239` | `T-11`, `T-12` |
| `CA-PM-240`, `CA-PM-241` | `T-11`, `T-15` |
| `CA-PM-242` | `T-03`, `T-04`, `T-14` |
| `CA-PM-243`, `CA-PM-244` | `T-02`, `T-13`, `T-16` |
| `CA-PM-245`, `CA-PM-246` | `T-17` |
| `CA-PM-247` | `T-11`, `T-16` |
| `CA-PM-248` | `T-09`, `T-10`, `T-18` |
| `CA-PM-230`, `CA-PM-231` (`RF-PM-001`) | `T-06` |
| `CA-PM-234`, `CA-PM-235` (`RF-PM-004`) | `T-07` |
| `CA-PM-232`, `CA-PM-233`, `CA-PM-237`, `CA-PM-238` (lecturas) | `T-09`, `T-10`, `T-18` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Ninguno. `V90` es el siguiente número libre; si otra migración se lo lleva antes, se renumera (`modelo-datos.md` §5.4) | 14-09-2026 | Responsable técnico | **Cerrado el 14-09-2026** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde, **incluidos los de las enmiendas** a `RF-PM-001`, `RF-PM-004` y las cuatro lecturas.
- [ ] `mvn verify` en verde en local, **con la prueba de sentencias de `RF-PM-002` sin subir**.
- [ ] `CA-PM-242` envía un `JPEG` disfrazado de `PNG` y un `SVG`, y el sistema hace lo que la spec dice con cada uno.
- [ ] **Ningún archivo de imagen entra al repositorio**: los bytes de las pruebas se generan.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad, `docs/api/index.md` y `docs/modelo-datos.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
