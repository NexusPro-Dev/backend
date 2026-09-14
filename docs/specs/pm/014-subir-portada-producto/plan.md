# PLAN — `RF-PM-014` Subir o reemplazar la portada de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-014` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 14-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 14-09-2026 |

---

## 1. Enfoque

**Una tabla nueva para los bytes, una columna en `products` que la señala, y la corrección de siempre por debajo.**

El caso de uso es `UpdateProductService` con un solo campo y un archivo delante: encontrar el producto vivo bloqueando, cambiar `cover_image_id`, registrar el diff en la misma transacción, devolver el detalle. Lo que no tiene precedente son **tres cosas**, y las tres se resuelven en un sitio cada una:

1. **Leer un archivo de la petición.** Es el primer `multipart/form-data` del sistema. Spring lo trae como `MultipartFile`; el controlador lo convierte en bytes y **el resto del módulo no sabe que existió una petición HTTP**.
2. **Saber qué es el archivo.** Un detector de firma **propio y pequeño** —tres firmas, unos veinte bytes de conocimiento— en el dominio, y **ninguna biblioteca de imágenes**: no hay nada que decodificar porque no se trata la imagen.
3. **Guardar y borrar bytes.** Una entidad `ProductImage` inmutable con un `bytea`, insertada y borrada por su repositorio, **nunca cargada en ninguna lectura del catálogo**.

Y `RN-PM-034` se construye **dentro de `Product`**, que es el único que ve el icono y la portada a la vez, como enmienda a `create` y `update` — el detalle está en los planes de `RF-PM-001` y `RF-PM-004`.

## 2. Cambios de esquema

### `V90__product_images.sql`

```sql
CREATE TABLE product_images (
    id           uuid PRIMARY KEY,
    content_type varchar(30) NOT NULL,
    content      bytea NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_product_images_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_product_images_size
        CHECK (octet_length(content) BETWEEN 1 AND 5242880)
);

ALTER TABLE products ADD COLUMN cover_image_id uuid;
ALTER TABLE products ADD CONSTRAINT fk_products_cover_image
    FOREIGN KEY (cover_image_id) REFERENCES product_images (id);
ALTER TABLE products ADD CONSTRAINT uq_products_cover_image UNIQUE (cover_image_id);
```

Con los `COMMENT ON` de rigor. **Sin `updated_at` ni `deleted_at`** en la tabla nueva (`requirements/pm.md` §10.5): una fila no se modifica, se reemplaza, y la vieja se borra. **Sin `ON DELETE`** en la clave foránea: el orden de las tres escrituras lo garantiza el servicio, y un `ON DELETE SET NULL` dejaría que borrar una imagen quitara una portada sin pasar por `RN-PM-034`. **`UNIQUE` total**: en PostgreSQL los nulos no colisionan, de modo que mil productos sin portada caben.

**`bytea` y no un tipo de objeto grande (`lo`).** Cinco megas caben en `bytea` sin despeinarse, viajan en una sola sentencia y se borran con la fila; los objetos grandes son otra API, otra tabla del sistema y una limpieza aparte (`vacuumlo`) que nadie ejecutaría. **Y TOAST hace el resto**: PostgreSQL saca la columna fuera de la fila en cuanto pasa de dos kilobytes, de modo que un `SELECT` de `products` que no nombre `content` **no la lee del disco** — que es lo que permite que el catálogo no pague por las portadas.

**No hay `CHECK` sobre la firma de los bytes**, y §10.5 dice por qué: sería la única línea de SQL del sistema que sabe lo que es una imagen.

### `RN-PM-034` no entra en el esquema

Ver [`requirements/pm.md` §10.3](../../../requirements/pm.md): hay upgrades anteriores sin icono, y un `CHECK NOT VALID` mordería en su próximo `UPDATE` —activar, retirar— con un `500` donde la regla dice que no pasa nada.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | **`ProductImage`** — entidad inmutable: `id`, `contentType`, `content`, `createdAt`; `ProductImage.de(bytes, ahora)` detecta el tipo y rechaza (`VAL-003`, `VAL-004`, `VAL-002`) | `PM` |
| `domain/models` | **`ImageSignature`** — el detector: `JPEG` (`FF D8 FF`), `PNG` (`89 50 4E 47 0D 0A 1A 0A`), `WebP` (`RIFF` + cuatro bytes + `WEBP`); devuelve el `content_type` o vacío | `PM` |
| `domain/models` | `Product.asignarPortada(UUID nueva)` → devuelve el identificador anterior (o vacío) y el diff; `Product.getCoverImageId()`; `instantanea()` gana `cover_image_id`; **`create` y `update` ganan `RN-PM-034`** (planes de `RF-PM-001` y `RF-PM-004`) | `PM` |
| `domain/repository` | **`ProductImageRepository`** (`save`, `deleteById`, `findById`) sobre JPA; **nunca** se une a `products` en una consulta del catálogo | `PM` |
| `domain/repository` | `ProductQueryRepository.ProductRow` gana `coverImageId`; las **cuatro** sentencias seleccionan `p.cover_image_id` | `PM` |
| `domain/service` | **`UploadProductCoverService`** | `PM` |
| `application` | **`ProductImageUrls.de(UUID)`** — la única función que sabe la forma `/api/v1/product-images/{id}`; los cinco `record` de respuesta ganan `coverImageUrl` | `PM` |
| `interfaces` | `ProductController` — `PUT /api/v1/products/{id}/cover`, `consumes = multipart/form-data`, `@RequestPart("file") MultipartFile` | `PM` |
| `shared/error` | `GlobalExceptionHandler` traduce `MaxUploadSizeExceededException` y `MultipartException` (`VAL-004`, `EX-003`) | `SP` (transversal) |
| `application.yml` | `spring.servlet.multipart.max-file-size: 6MB`, `max-request-size: 7MB` — **por encima** del tope de negocio | — |

## 4. Contrato de API

`PUT /api/v1/products/{id}/cover` — `products:update`. `Content-Type: multipart/form-data`, una parte `file`. `200` con `ProductDetailResponse`.

- **`consumes = MULTIPART_FORM_DATA_VALUE`**, de modo que un `PUT` con JSON responde `415` de Spring — y **se traduce a `400` `EX-003`** en el manejador, porque el sistema no publica `415` en ninguna otra ruta y un cliente tiene que poder leer el cuerpo del error.
- **`@RequestPart("file")` con `required = false`**, para que la ausencia de la parte sea `VAL-002` **con el sobre de errores del sistema** y no el `400` genérico de Spring sin cuerpo útil.
- **`ProductDetailResponse` gana `coverImageUrl`**, y con él `ProductResponse`, `ProductItem`, `OfferItem` y `ProductRef`: **siempre presente**, nulo cuando no hay (`@JsonInclude(ALWAYS)` como en `videoUrl`). La forma es **la ruta y no una URL absoluta** —`/api/v1/product-images/{uuid}`—: el backend no sabe bajo qué dominio lo sirven, y el cliente ya conoce la base con la que llama a todo lo demás.
- **La prosa de la `@Operation` dice cuatro cosas**: que el tipo lo deciden los bytes y cuáles son; el tope de 5 MB; que reemplaza y la vieja se borra —la dirección cambia—; y que el alta no admite la imagen, se sube después.
- **El contrato se regenera** (`OpenApiContractIT`) y `docs/api/index.md` gana su fila: es el primer endpoint `multipart` del sistema y el primer `requestBody` que no es JSON.

## 5. Autorización

`@PreAuthorize("hasAuthority('products:update')")`, y nada más. El permiso ya existe: **ningún cambio en el catálogo de permisos ni en `SecurityConfig`** para esta ruta. `EndpointPermissionsIT` la incorpora con su permiso.

## 6. Auditoría

`ChangeEvent` con `ChangeAction.UPDATE`, `module = "PM"`, `entity = "products"`, y **solo `cover_image_id`** en `changes`: `{"before": <uuid o null>, "after": <uuid>}`. Es lo que `UpdateProductService` hace con cualquier campo, y por lo mismo **no se escribe nada cuando no cambia nada** — aquí siempre cambia, porque cada subida estrena identificador (`FA-002`).

**Los bytes no van a la auditoría.** Ni antes ni después: la instantánea de `products` lleva el identificador, y `product_images` **no tiene instantánea** porque no es una entidad que se retire — es un valor que se reemplaza (`requirements/pm.md` §5.2.9).

## 7. Transaccionalidad

`@Transactional`. **Cinco sentencias en el caso feliz con reemplazo**: el producto con `FOR UPDATE`, el `INSERT` de la imagen, el `UPDATE` de `products`, el `DELETE` de la imagen anterior, la auditoría. **El orden `UPDATE` → `DELETE` no es negociable**: la clave foránea sin `ON DELETE` rechaza borrar una imagen que todavía se señala. El `INSERT` va antes que el `UPDATE` por lo mismo, al revés.

**El archivo se valida antes de abrir la transacción**, o al menos antes de la primera sentencia (`spec.md` §8): `ProductImage.de(bytes, ahora)` se construye primero y lanza sus tres `VAL` sin haber tocado la base.

**El bloqueo del producto serializa dos subidas simultáneas**: la segunda espera, ve la portada de la primera y la reemplaza. Queda la última, y `product_images` tiene una fila.

## 8. Impacto sobre otros módulos

**Ninguno en código de otro módulo.** El manejador de excepciones es transversal y gana dos traducciones. **`RF-PM-001` y `RF-PM-004` cambian de comportamiento** —el icono obligatorio en un upgrade sin portada— y **las cuatro lecturas ganan un campo**: son enmiendas Art. I.7 con su fila en cada tripleta.

**En datos**: la primera tabla con `bytea` del sistema. La copia de seguridad de la base **crece con las portadas**; con decenas de productos y cinco megas de tope son centenares de megas como máximo, y se anota para el día que alguien pregunte por qué el volcado pesa.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Guardar la dirección de la imagen, como el video** | Decidido en contra por el responsable del proyecto (`requirements/pm.md` §5.2.9): el video tiene quien lo aloje y la portada no |
| **Disco o S3** | Ídem: un segundo lugar que respaldar y que se desincroniza, o un servicio más que operar, para decenas de imágenes |
| **Una biblioteca de imágenes (ImageIO, Thumbnailator) para validar** | Decodificar cinco megas para saber si son un `PNG` es lo que un detector de firma resuelve leyendo doce bytes; y una biblioteca que decodifica es una superficie de ataque —una imagen malformada que agote memoria— para una decisión que no se toma (no se trata la imagen) |
| **Validar por la cabecera `Content-Type` de la parte** | La escribe el cliente. `CA-PM-242` envía un `JPEG` como `image/png` y espera que se guarde como lo que es |
| **`product_id` en `product_images` en lugar de `cover_image_id` en `products`** | Dos punteros divergen; y el agregado `Product` tiene que ver la portada para aplicar `RN-PM-034` en `update` sin una consulta más |
| **Conservar las imágenes reemplazadas** | Cinco megas por cada intento de acertar con la foto, para no leerlas nunca (`requirements/pm.md` §5.2.9) |
| **`POST /cover` que responde `201` con la imagen** | La portada es un solo hueco: `PUT`. Y lo que cambió es el producto, que es lo que el cliente repinta (`spec.md` §14.1) |
| **Un permiso `products:cover`** | Nadie ha pedido conceder «poner foto» sin «corregir» (`spec.md` §3) |
| **La imagen dentro del alta (`multipart` en `RF-PM-001`)** | Rompe el contrato JSON del alta y mezcla dos validaciones (`requirements/pm.md` §5.2.9) |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien selecciona `content` en una consulta del catálogo** —un `JOIN` a `product_images` para «traer el tipo»— y cada listado arrastra megas | La proyección `ProductRow` lleva **solo `cover_image_id`**; `ProductImageRepository` no tiene ningún método de listado; y la prueba de sentencias de `RF-PM-002` no sube. Se anota en el Javadoc de la entidad: **nunca en una lectura del catálogo** |
| 2 | **El tope del contenedor queda por debajo del de negocio** y un archivo de 4 MB recibe un `500` | `max-file-size` en `6MB`, con el comentario de por qué está por encima; `CA-PM-243` sube 5 242 880 bytes y espera `200` |
| 3 | **Se borra la imagen vieja antes de repuntar** y la clave foránea falla | El orden está escrito en §7 y `CA-PM-240` reemplaza y comprueba que queda una fila |
| 4 | **El detector acepta un `SVG` como «imagen»** porque alguien amplía la lista «para que se vea el logo» | `CA-PM-242` envía un `SVG` y espera `VAL-003`; `security.md` §6 dice por qué queda fuera |
| 5 | **Un `WebP` mal formado pasa la firma y el navegador no lo pinta** | Se acepta: el sistema no interpreta el contenido, y la portada rota la ve administración en el detalle antes que nadie |
| 6 | **La prueba de integración carga fixtures binarios pesados** | Los bytes se **generan** en la prueba: una firma válida más relleno hasta el tamaño deseado. No entran archivos de imagen al repositorio |

## 11. Estrategia de prueba

- **Unitaria** (`ProductImageTest`, `ImageSignatureTest`): las tres firmas válidas —con cabecera falsa y nombre falso, que no existen aquí—, `GIF`, `SVG`, texto, vacío, un byte, 5 242 880 y 5 242 881 bytes. Y `ProductTest`: `asignarPortada` devuelve el anterior y el diff, y la instantánea lleva `cover_image_id`.
- **Integración de API** (`ProductCoverIT`): los diez criterios de `spec.md` §12. **Las que definen el requerimiento**: `CA-PM-240` —reemplazar deja **una** fila y la dirección vieja responde `404`— y `CA-PM-242` —el tipo por los bytes—. La subida se hace con `MockMvc` y `multipart()`, con bytes generados.
- **De efecto**: `CA-PM-248` recorre las cuatro lecturas tras subir; y las cuatro suites de lecturas ganan su prueba de `coverImageUrl` presente y nulo (enmiendas).
- **De enmienda**: `CA-PM-230`/`231` en `ProductsIT` y `CA-PM-234`/`235` en `ProductUpdateIT`, con `ProductTest` para la regla en el agregado.
- **De sentencias**: la prueba de `RF-PM-002` que cuenta consultas del listado **no sube**.
- **De esquema**: `V90` aplica sobre una base con productos existentes sin tocarlos; `ck_product_images_size` rechaza un `INSERT` de cero bytes.
