# PLAN — `RF-PM-028` Subir o reemplazar la portada de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-028` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 16-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 16-09-2026 |

---

## 1. Enfoque

**Una columna en `product_packages`, y todo lo demás ya existe.**

El plan de `RF-PM-014` resolvió las tres cosas que no tenían precedente —leer un archivo de la petición, saber qué es, guardar y borrar bytes— en un sitio cada una: el controlador, `ProductImage` con `ImageSignature`, y `ProductImageRepository`. **Las tres se reutilizan tal cual**: esta operación no añade ningún conocimiento sobre imágenes, ninguna tabla y ninguna ruta pública. Lo que añade es **el hueco** —`product_packages.cover_image_id`— y **las dos operaciones del agregado** que lo llenan y lo vacían, calcadas de `Product.asignarPortada` y `Product.quitarPortada` **sin la regla**: el paquete no tiene `RN-PM-034` que aplicar (`RN-PM-045`).

El caso de uso es `UploadProductCoverService` con el paquete en lugar del producto: construir la imagen antes de tocar la base, encontrar el paquete vivo bloqueando, insertar la nueva, repuntar, volcar, borrar la anterior, auditar, devolver el detalle por `PackageDetailReader`. **Y el registro `CambioDePortada` sale de `Product` a un tipo propio**, porque desde hoy lo devuelven dos agregados.

## 2. Cambios de esquema

### `V11__pm_portada_paquete.sql`

```sql
ALTER TABLE product_packages
    ADD COLUMN cover_image_id uuid NULL,
    ADD CONSTRAINT fk_product_packages_cover_image
        FOREIGN KEY (cover_image_id) REFERENCES product_images (id),
    ADD CONSTRAINT uq_product_packages_cover_image UNIQUE (cover_image_id);
```

Con los `COMMENT ON` de rigor —el de la columna, y el de la tabla `product_images`, que desde hoy dice que guarda las portadas del catálogo y no solo las del producto—. **Sin `ON DELETE`** por lo mismo que `fk_products_cover_image`: el orden de las tres escrituras lo garantiza el servicio. **`UNIQUE` total**: los nulos no colisionan.

**Es `V11` y no una reescritura de `V5`**: una migración aplicada no se toca (`modelo-datos.md` §5.4). Y **no hay ninguna columna `icon` ni `color`** al lado, que es la decisión de [`requirements/pm.md` §5.2.12](../../../requirements/pm.md).

### Lo que ningún `UNIQUE` puede decir

Que una imagen no sea a la vez portada de un producto y de un paquete no cabe en una restricción: son dos tablas. No hace falta declararlo, porque ninguna operación puede producirlo — una fila de `product_images` solo nace por una subida, que la señala desde una entidad y solo una (§5.2.12).

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | **`CambioDePortada`** — sale de `Product` como tipo propio: el identificador anterior y el diff `cover_image_id`; `Product` y `ProductPackage` lo devuelven | `PM` |
| `domain/models` | `ProductPackage`: columna `coverImageId`; `asignarPortada(UUID, ahora)` y `quitarPortada(ahora)` **sin regla**; `getCoverImageId()`; `instantanea()` gana `cover_image_id` | `PM` |
| `domain/repository` | `ProductPackageQueryRepository.PackageRow` gana `coverImageId`; **las dos sentencias del paquete** —`COLUMNAS_PAQUETE` y `SELECT_PUBLICADO`— seleccionan `k.cover_image_id` y **nada de `product_images`** | `PM` |
| `domain/service` | **`UploadPackageCoverService`**. Sin un `PackageCoverSupport` como el del producto: el `404` del paquete vivo ya lo da `AssociatePackageProductService.paqueteVivo`, y la respuesta, `PackageDetailReader` | `PM` |
| `application` | `coverImageUrl` en `PackageDetailResponse`, `PackageItemSummary`, `OfferPackageItem` y `PackageHotlinkResponse.PackageRef`, con `ProductImageUrls.de`, **siempre presente** | `PM` |
| `interfaces` | `PackageController` — `PUT /api/v1/packages/{id}/cover`, `consumes = multipart/form-data`, `@RequestPart(value = "file", required = false)` | `PM` |

**Nada en `shared`, nada en `application.yml`, nada en `SecurityConfig`.** El manejador ya traduce `MaxUploadSizeExceededException` y `MultipartException`, el tope del contenedor ya está por encima del de negocio, y la ruta pública ya existe.

## 4. Contrato de API

`PUT /api/v1/packages/{id}/cover` — `packages:update`. `Content-Type: multipart/form-data`, una parte `file`. `200` con `PackageDetailResponse`.

- **La misma forma que `PUT /products/{id}/cover`**, verbo a verbo: `consumes`, `@RequestPart` con `required = false` para que la ausencia sea `VAL-002` con el sobre del sistema, y los mismos códigos de respuesta.
- **Cuatro `record` ganan `coverImageUrl`**, siempre presente y nulo cuando no hay (`@JsonInclude(ALWAYS)`), con la misma función `ProductImageUrls.de` que las cinco formas del producto: la ruta y no una URL absoluta.
- **La prosa de la `@Operation` dice tres cosas**: que es la misma imagen, con las mismas condiciones y la misma ruta que la del producto; que reemplaza y la dirección cambia; y **que el paquete no declara icono ni color** — sin portada, el cliente pinta los suyos por omisión, y por eso `DELETE` nunca responde `400`.
- **El contrato se regenera** (`OpenApiContractIT`) y `docs/api/index.md` gana su párrafo.

## 5. Autorización

`@PreAuthorize("hasAuthority('packages:update')")`. El permiso ya existe (`V8`): **ningún cambio en el catálogo ni en `SecurityConfig`**. `EndpointPermissionsIT` la incorpora con su permiso, y la prueba comprueba que `products:update` **no** basta (`CA-PM-358`).

## 6. Auditoría

`ChangeEvent` `UPDATE`, `module = "PM"`, `entity = "product_packages"`, y **solo `cover_image_id`** en `changes`: `{"before": <uuid o vacío>, "after": <uuid>}`. Siempre hay cambio, porque cada subida estrena identificador. **Los bytes no van a la auditoría**, como en el producto.

## 7. Transaccionalidad

`@Transactional`. **Cinco sentencias en el caso feliz con reemplazo**: el paquete con `FOR UPDATE`, el `INSERT` de la imagen, el `UPDATE` de `product_packages`, el `DELETE` de la imagen anterior, la auditoría. **`UPDATE` → `flush()` → `DELETE`**, por la clave foránea sin `ON DELETE`. El archivo se valida antes de la primera sentencia. El bloqueo del paquete serializa dos subidas simultáneas.

**La respuesta cuesta lo que `PackageDetailReader` cuesta** (`RF-PM-019` `CA-PM-284`): el paquete con sus filas, la moneda de casa, y la tasa solo si hay algo que convertir. La portada **no añade ninguna**: `cover_image_id` viaja en la sentencia del paquete.

## 8. Impacto sobre otros módulos

**Ninguno en código de otro módulo.** Dentro de `PM`: **`Product` cambia de tipo de retorno** en `asignarPortada` y `quitarPortada` —de `Product.CambioDePortada` a `CambioDePortada`—, y con él `UploadProductCoverService`, `RemoveProductCoverService` y `ProductTest`, sin cambiar de comportamiento. **Las cuatro lecturas del paquete ganan un campo** y el alta lo devuelve nulo: son enmiendas Art. I.7 con su fila en cada tripleta (`RF-PM-007`, `017`, `018`, `019`, `026`).

**En datos**: `product_images` crece también con las portadas de los paquetes. Con decenas de paquetes y cinco megas de tope, la anotación de `RF-PM-014` §8 sigue valiendo.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`icon` y `color` en `product_packages`, con `RN-PM-034` encima** | Decidido en contra por el responsable del proyecto (`requirements/pm.md` §5.2.12): sin portada el frontend pinta un icono de promoción y el color por omisión, los mismos para todos. Dos columnas con un solo valor posible y una regla de tres caras para sostenerlas |
| **Una tabla `package_images`** | Idéntica columna a columna y con el mismo detector delante, para que una imagen supiera de qué entidad es — y no necesita saberlo: quien la señala lo dice. Dos sitios que mantener cuando cambie el tope |
| **Una ruta `/package-images/{imageId}`** | Una declaración más en `SecurityConfig` y una cota más para servir los mismos bytes con las mismas cabeceras. La ruta no mira a la entidad que la señala |
| **Un `discriminator` en `product_images` (`owner_type`)** | Un segundo puntero que podría divergir del único que el catálogo lee, para una pregunta —¿de quién es esta imagen?— que nadie hace: la imagen se lee por su identificador, y se borra desde quien la señala |
| **Reutilizar `UploadProductCoverService` con un parámetro de entidad** | Dos repositorios, dos agregados y dos lectores de detalle detrás de un `if`. Son diez líneas cada uno; una copia legible vale más que una abstracción para dos casos |
| **Dejar `CambioDePortada` anidado en `Product` y que `ProductPackage` lo importe de ahí** | El paquete devolvería un tipo llamado `Product.CambioDePortada`, que dice lo que no es |
| **Un permiso `packages:cover`** | Nadie ha pedido conceder «poner foto» sin «corregir» (`RF-PM-014` §3) |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien une `product_images` en `SELECT_PUBLICADO` o en `COLUMNAS_PAQUETE`** y cada oferta arrastra megas | `PackageRow` lleva solo `cover_image_id`; `CA-PM-361` cuenta sentencias en las cuatro lecturas; el Javadoc de `ProductImage` ya lo dice |
| 2 | **Se borra la imagen vieja antes de repuntar** y la clave foránea falla | `flush()` explícito entre las dos (§7) y `CA-PM-355` reemplaza y comprueba que queda una fila |
| 3 | **Una suite de otra clase hace `DELETE FROM product_images` con un paquete todavía señalándola** y cae con una violación de integridad que no dice de dónde vino | `PackageCoverIT` limpia sus paquetes en `@AfterEach`, como todas las suites de paquetes (`PackageTestSupport`); `limpiarCatalogoYSembrarMembresias` borra los paquetes **antes** que las imágenes |
| 4 | **Alguien añade `RN-PM-034` al paquete «por simetría»** y `DELETE /cover` empieza a rechazar | `CA-PM-362` de `RF-PM-029` quita la portada de un paquete y espera `200` siempre; §5.2.12 dice por qué |
| 5 | **Las cuatro lecturas divergen en la forma de la dirección** | Una sola función, `ProductImageUrls.de`, para las nueve formas de respuesta que la publican |

## 11. Estrategia de prueba

- **Unitaria** (`ProductPackageTest`): `asignarPortada` devuelve el anterior y el diff, `quitarPortada` sin portada devuelve vacío sin diff y con portada devuelve el anterior, y la instantánea lleva `cover_image_id`. `ProductTest` sigue en verde con el tipo nuevo.
- **Integración de API** (`PackageCoverIT`): los ocho criterios de `spec.md` §12 más los cinco de `RF-PM-029`. **La que define el requerimiento**: `CA-PM-360` — las cuatro lecturas devuelven la dirección del paquete y no la de sus productos. La subida con `MockMvc` y `multipart()`, con bytes generados por `ProductImageTest`.
- **De enmienda**: `CA-PM-367` a `CA-PM-371` en la misma `PackageCoverIT`, en una prueba que recorre el alta y las cuatro lecturas: presente y nula sin portada. Van juntas y no una por suite porque las cinco comprueban lo mismo con el mismo fixture.
- **De sentencias**: los techos de `PackageListIT`, `PackageDetailIT`, `PackageOfferIT` y `PackageHotlinkIT` **no suben**.
- **De esquema**: `V11` aplica sobre una base con paquetes existentes sin tocarlos.
