# PLAN — `RF-AC-006` Subir o reemplazar la portada de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-006` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`UploadPackageCoverService` con otra entidad, otra tabla y el detector en `shared/`.** La secuencia es la de `PM` paso a paso —el archivo antes que la base, la fila bloqueada, insertar, apuntar, volcar, borrar la vieja, auditar, releer—; lo que este plan trae es **la refactorización previa**: `ImageSignature` gana la validación de los tres `VAL` —que hoy vive en `ProductImage.de`— y se muda a `shared/images` con `CambioDePortada`; `ProductImage.de` pasa a delegar. Así `AcademyImage.de` son cinco líneas, y las dos entidades no pueden divergir en qué es una imagen admitida.

## 2. Cambios de esquema

**Una migración.** `V28` salvo que otra tanda se adelante (tras `V25`–`V27` del bloque 4).

### `V28__ac_imagenes.sql`

```sql
CREATE TABLE academy_images (
    id           uuid        PRIMARY KEY,
    content_type varchar(30) NOT NULL,
    content      bytea       NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_academy_images_content_type CHECK (content_type IN ('image/jpeg','image/png','image/webp')),
    CONSTRAINT ck_academy_images_size         CHECK (octet_length(content) BETWEEN 1 AND 5242880)
);

ALTER TABLE course_categories
    ADD CONSTRAINT fk_course_categories_cover_image FOREIGN KEY (cover_image_id) REFERENCES academy_images (id),
    ADD CONSTRAINT uq_course_categories_cover_image UNIQUE (cover_image_id);
ALTER TABLE courses
    ADD CONSTRAINT fk_courses_cover_image FOREIGN KEY (cover_image_id) REFERENCES academy_images (id),
    ADD CONSTRAINT uq_courses_cover_image UNIQUE (cover_image_id);
ALTER TABLE course_modules
    ADD CONSTRAINT fk_course_modules_cover_image FOREIGN KEY (cover_image_id) REFERENCES academy_images (id),
    ADD CONSTRAINT uq_course_modules_cover_image UNIQUE (cover_image_id);
```

- **`product_images` columna a columna** (`ac.md` §8.8), con las dos restricciones repetidas: son dos tablas, y el tope y la lista de tipos viven en el esquema de cada una **y** en `shared/` — subir el tope es una constante y dos restricciones.
- **Las seis restricciones de las tres columnas llegan aquí**, y no en `V18`, `V21` y `V23`: la tabla no existía, y las columnas nacieron nulables para que la forma de las respuestas fuera la definitiva desde el primer día. Las tres tablas están vacías de portadas al aplicar, de modo que las restricciones se añaden sin relleno.
- **Sin `ON DELETE`**: la imagen se borra **después** de que la columna deje de señalarla, nunca al revés.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| **`shared/images`** | **`ImageSignature`** (movido de `PM`), que gana `TAMANO_MAXIMO`, `CAMPO = "file"` y **`validar(bytes)`** —vacío `VAL-002`, tamaño `VAL-004`, firma `VAL-003`, en ese orden— y **`CambioDePortada`** (movido de `PM`) | `shared` |
| `PM` | `ProductImage.de` delega en `ImageSignature.validar`; `Product`, `ProductPackage` y sus servicios importan `CambioDePortada` de `shared/images`. Sin cambio de comportamiento | `PM` |
| `domain/models` | `AcademyImage` (entidad sobre `academy_images`, inmutable); `CourseCategory.asignarPortada(nueva, ahora)` que devuelve `CambioDePortada` | `AC` |
| `domain/repository` | `AcademyImageRepository` + `Jpa…`: `save`, `deleteById`, `findById` (solo `RF-AC-032` la usa para leer) | `AC` |
| `domain/service` | `UploadCourseCategoryCoverService` | `AC` |
| `interfaces` | `CourseCategoryController` — `PUT /api/v1/course-categories/{id}/cover` (`multipart/form-data`) | `AC` |
| `shared/config` | El tope del contenedor para `multipart` ya está en 6 MB desde `RF-PM-014`; sin cambio | `shared` |

**`AcademyImage` no se selecciona nunca en una lectura del catálogo**, como `ProductImage`: ninguna sentencia de `AC` une `academy_images` ni lee `content`, salvo la que sirve los bytes (`RF-AC-032`).

## 4. Contrato de API

`PUT /api/v1/course-categories/{id}/cover` — `course-categories:update` — `multipart/form-data`, parte `file`. `200` con `CourseCategoryDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-004`, o no es `multipart` (`EX-002`) |
| `401` / `403` | Sin sesión / sin `course-categories:update` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('course-categories:update')")`: la portada es el valor de una columna de la categoría.

## 6. Auditoría

`ChangeEvent` `UPDATE` de `course_categories` con `cover_image_id` antes y después, que es lo que `CambioDePortada` arma. **La instantánea conserva el identificador, no los bytes** (`requirements/pm.md` §5.2.9, que `ac.md` hereda).

## 7. Transaccionalidad

`@Transactional`. El archivo se valida **fuera** de cualquier bloqueo; después la categoría con `FOR UPDATE`, `INSERT` de la imagen, `UPDATE` de la columna con volcado explícito, `DELETE` de la vieja, auditoría, relectura.

## 8. Impacto sobre otros módulos

**`PM` cambia imports y `ProductImage.de` delega**: una refactorización sin cambio de comportamiento, con `ProductCoverIT`, `PackageCoverIT`, `ProductImageIT` y `ProductImageTest` en verde como definición de terminado. Es el segundo código que `AC` promueve a `shared/`, después de `DeletionReason`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Reutilizar `product_images`** | `modules.md` §7 (`ac.md` §5.2.3) |
| **Copiar `ImageSignature` a `AC`** | Dos detectores que un día admiten formatos distintos; `shared/` existe para esto |
| **Una entidad `Image` compartida en `shared/` con dos tablas** | JPA no mapea una clase a dos tablas sin herencia; y la entidad es de quien escribe la tabla |
| **Las restricciones de `cover_image_id` en cada migración de tabla** | La tabla de imágenes no existía; una clave foránea a una tabla futura no se puede declarar |
| **Un componente transversal `images`** con una sola tabla | Decisión de arquitectura con ADR que obliga a migrar `product_images` (`ac.md` §5.2.3); anotada la condición para reabrirlo |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La refactorización cambia el comportamiento de `PM`** | `T-01` en su propio commit con las cuatro suites de imágenes de `PM` en verde antes de escribir una clase de `AC` |
| 2 | **Se borra la imagen vieja antes de volcar el `UPDATE`** y la clave muerde | El orden está escrito y `CA-AC-156` lo ejercita |
| 3 | **Una lectura del catálogo une `academy_images`** | Convención escrita en el Javadoc de `AcademyImage`; las pruebas de sentencias de los listados no crecen |

## 11. Estrategia de prueba

- **Unitarias**: `ImageSignature.validar` (los tres rechazos en su orden, los tres formatos, el `JPEG` disfrazado); `AcademyImage.de`; `CourseCategory.asignarPortada` con y sin anterior.
- **Integración de API** (`CourseCategoryCoverIT`): `CA-AC-155` a `CA-AC-160`, incluida la doble subida simultánea.
- **Refactorización** (`CA-AC-161`): las suites de `PM` y `LayerRulesTest` en verde.
- **Esquema** (`CA-AC-162`): las seis restricciones existen y muerden.
