# PLAN — `RF-AC-037` Dar visibilidad de un curso a un servicio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-037` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 25-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 25-09-2026 |

---

## 1. Enfoque

**`RF-AC-016` con el producto en lugar de la categoría, y el puerto de `PM` en lugar del repositorio propio** —la forma que `RF-AC-020` ya fijó para la membresía—. La fila, el bloqueo del curso, el `409` traducido y la auditoría son los de la clasificación; la diferencia es que el producto se resuelve por **`ProductCatalog.findKind`**, una lectura nueva de `PM`, y que en las lecturas se resuelve por un `JOIN products` de tres columnas. **Y la ofrecibilidad gana una cuenta**: `CourseOfferability` recibe cuántos servicios tiene el curso, y el cuarto motivo pasa a ser «sin membresías **ni servicios**».

## 2. Cambios de esquema

**Una migración**, con el número libre al construir. Hoy la siguiente es `V43`, **anotada en `cm.md` v0.17.0 para la liquidación**, que no tiene código: quien construya primero la toma y el otro sube uno. No se reserva nada.

### `V<n>__ac_visibilidad_por_servicio.sql`

```sql
CREATE TABLE course_products (
    course_id  uuid        NOT NULL,
    product_id uuid        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_course_products PRIMARY KEY (course_id, product_id),
    CONSTRAINT fk_course_products_course  FOREIGN KEY (course_id)  REFERENCES courses (id),
    CONSTRAINT fk_course_products_product FOREIGN KEY (product_id) REFERENCES products (id)
);
CREATE INDEX ix_course_products_product ON course_products (product_id);
```

- **`fk_course_products_product` cruza hacia `PM`** y se declara (`modelo-datos.md` §5.3): la primera clave foránea de `AC` hacia `products`.
- **Sin `ON DELETE`**, y aquí importa por las pruebas: **toda suite que limpie con `DELETE FROM products`** tendrá que borrar antes `course_products`, como con `product_links` (memoria del proyecto). Se revisan en `T-08`.
- **Sin `CHECK` de tipo**: el tipo vive en `products` (`ac.md` §8.5.1).

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | **`ProductCatalog.findKind(UUID)`** y el registro `KindView(id, code, name, bot, retired)`; su implementación sobre `products` | `PM` |
| `domain/models` | `CourseProduct` (clave compuesta `CourseProductId`); **`CourseOfferability` recibe la cuenta de servicios**, y su cuarto motivo cambia de texto | `AC` |
| `domain/repository` | `CourseProductRepository` + `Jpa…`: `save` con traducción de `pk_course_products` al `409`, `find`, `delete` | `AC` |
| `domain/repository` | **Enmendado** `CourseQueryRepository`: `CUENTA_DE_SERVICIOS` como columna en las tres sentencias que deciden la ofrecibilidad —detalle, listado, cursos de la categoría—; `findProductsOf(courseId)` con `JOIN products` (id, code, name); `CourseRow.productCount` | `AC` |
| `domain/service` | `GrantCourseProductService` | `AC` |
| `domain/service` | **Enmendados** `CourseDetailReader` (los servicios) y `DeleteCourseService` (`product_ids`) | `AC` |
| `application` | `GrantCourseProductRequest` — `productId`; `CourseDetailResponse.products` con `CourseProductRef` | `AC` |
| `interfaces` | `CourseController` — `POST /api/v1/courses/{courseId}/products` | `AC` |

**`CourseProductRef` lleva `@Schema(name = "CourseProductRef")`**: `ProductRef` ya existe en otro módulo, y springdoc funde los registros con el mismo nombre simple (memoria del proyecto).

## 4. Contrato de API

`POST /api/v1/courses/{courseId}/products` — `courses:update`. `{ "productId": "…" }` → `201` con `CourseDetailResponse` y `Location` del curso.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-003` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |
| `409` | `EX-004` |
| `422` | `EX-002`, `EX-003` |

**Lo que cambia en lo ya publicado**: `CourseDetailResponse` gana `products` —siempre presente, vacío si no hay—, y el texto del cuarto `offerableReason` cambia. Los dos van en `docs/api/index.md`.

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`. El permiso propio es del tramo 3 de `RF-SP-060` (`spec.md` §14.3).

## 6. Auditoría

`ChangeEvent` `CREATE` de `course_products`, `entity_id` del curso, instantánea `{ course_id, product_id, product_code }`.

## 7. Transaccionalidad

`@Transactional`. El curso con `FOR UPDATE`, el producto por el puerto —sin bloquear: nada de él cambia—, la pareja, `INSERT`, auditoría, relectura del detalle.

## 8. Impacto sobre otros módulos

**`PM` gana una lectura publicada** (`findKind`), sin tocar las que consumen `CM` y `MV`. **`SP` no cambia en este requerimiento**: la interfaz de productos vigentes la pide el aula. Dentro de `AC` se enmiendan `RF-AC-008`, `RF-AC-009`, `RF-AC-010` y `RF-AC-013` (Art. I.7), con su fila de control de cambios al construir; y **se declaran**, sin construir, las enmiendas del aula —`RF-AC-033` a `RF-AC-035`— y de `RF-AC-020`, cuyo cuarto motivo cambia de texto.

**Una regla de ArchUnit a revisar**: si alguna prohíbe que `AC` importe de `PM`, se amplía para permitir **solo** `modules.products.application` —el puerto—, como ya se permite hacia `SP`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una sola tabla de «llaves» con membresía o producto** | Dos claves foráneas nulables y un `CHECK` de «exactamente una», para ahorrar una tabla. Las dos listas tienen otro lado distinto, otra lectura y otra regla de tipo |
| **Que el producto declare el curso** (`PM` → `AC`) | Es lo que `ac.md` §1.4 preveía el 17-09-2026; el responsable del proyecto pidió que sea **el curso** quien declare quién lo ve (§5.2.8) |
| **Añadir el tipo a `ProductView`** | `spec.md` §14.1 |
| **Rechazar el servicio `INACTIVO`** | La lista se arma antes de poner el servicio a la venta, como el curso se arma antes de activarse |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Una lectura decide la ofrecibilidad sin la cuenta de servicios** | La cuenta entra en el bloque de columnas compartido —`COLUMNAS` y la sentencia de cursos de la categoría— y `CA-AC-219` lo prueba en las tres |
| 2 | **Una suite ajena borra `products` y choca con la clave foránea** | `T-08` busca todo `DELETE FROM products` en `src/test` y antepone el borrado de `course_products` |
| 3 | **springdoc funde `CourseProductRef` con otro `ProductRef`** | `@Schema(name=…)` y mirar el diff de `openapi.json` |

## 11. Estrategia de prueba

- **Unitaria**: `CourseOfferability` con servicios y sin membresías, y el texto del cuarto motivo.
- **Integración de API** (`CourseProductVisibilityIT`): `CA-AC-216` a `CA-AC-222`, la carrera incluida. **La que define el requerimiento es `CA-AC-219`**: un curso sin membresías que se ofrece por su servicio.
- **De `PM`**: `findKind` distingue `BOT`, upgrade, retirado e inexistente.
