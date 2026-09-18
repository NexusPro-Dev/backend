# PLAN — `RF-AC-016` Clasificar un curso en una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-016` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**La asociación del paquete (`RF-PM-023`) sin descuento ni moneda ni cuenta, y seis literales de los bloques 1 y 2 convertidos en sentencias.** La fila es una entidad JPA con clave compuesta —`CourseCategoryItem` con `CourseCategoryItemId`, como `PackageItem`—; el caso de uso bloquea el curso vivo, resuelve la categoría viva por el repositorio de categorías del propio módulo —es `AC`, no cruza nada—, comprueba la pareja y escribe. **Lo que este plan trae de verdad es el cierre de las enmiendas**: la subconsulta de `courseCount` (`RF-AC-002`), `findAliveCoursesOf` y `findAliveCourseIdsOf` (`RF-AC-003`, `RF-AC-005`), `findCategoriesOf` y el predicado de `categoryId` (`RF-AC-009`), las categorías del detalle (`RF-AC-010`) y los identificadores de la instantánea (`RF-AC-013`).

## 2. Cambios de esquema

**Una migración.** `V25` salvo que otra tanda se adelante (tras `V23`/`V24` del bloque 3).

### `V25__ac_clasificacion.sql`

```sql
CREATE TABLE course_category_items (
    course_id   uuid        NOT NULL,
    category_id uuid        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_course_category_items PRIMARY KEY (course_id, category_id),
    CONSTRAINT fk_course_category_items_course   FOREIGN KEY (course_id)   REFERENCES courses (id),
    CONSTRAINT fk_course_category_items_category FOREIGN KEY (category_id) REFERENCES course_categories (id)
);
CREATE INDEX ix_course_category_items_category ON course_category_items (category_id);
```

- **Sin `id`, sin `deleted_at`**: no es una entidad, es una relación, y desclasificar borra (`ac.md` §8.3).
- **`ix_course_category_items_category`** sostiene el filtro por categoría de `RF-AC-009` y `RF-AC-033`, y los cursos de la categoría (`RF-AC-003`); la clave primaria ya sirve para «categorías de un curso».
- **Sin `ON DELETE`**: nada se borra físicamente.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `CourseCategoryItem` (clave compuesta `CourseCategoryItemId`) | `AC` |
| `domain/repository` | `CourseCategoryItemRepository` + `Jpa…`: `save` con traducción de `pk_course_category_items` al `409`, `exists`, `find`, `delete` | `AC` |
| `domain/repository` | **Enmendados**: `CourseCategoryQueryRepository` —la subconsulta de `courseCount`, `findAliveCoursesOf`, `findAliveCourseIdsOf`— y `CourseQueryRepository` —`findCategoriesOf`, el predicado `EXISTS` de `categoryId`, `findCategoryIdsOf`— | `AC` |
| `domain/service` | `ClassifyCourseService` | `AC` |
| `application` | `ClassifyCourseRequest` — `categoryId` | `AC` |
| `interfaces` | `CourseController` — `POST /api/v1/courses/{courseId}/categories` | `AC` |

**Los cursos de la categoría (`RF-AC-003`) traen `offerable`**, y eso obliga a que `findAliveCoursesOf` lea lo que `CourseOfferability` necesita: estado, retiro, cuántas membresías y cuántos módulos ofrecibles. Es la misma cuenta que el listado de cursos hace por fila, y se hace en Java con las mismas subconsultas.

## 4. Contrato de API

`POST /api/v1/courses/{courseId}/categories` — `courses:update`. `{ "categoryId": "…" }` → `201` con `CourseDetailResponse` y `Location` del curso.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-003` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |
| `409` | `EX-003` |
| `422` | `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`ChangeEvent` `CREATE` de `course_category_items`, **`entity_id` el del curso** —la fila es suya y no tiene identificador—, instantánea `{ course_id, category_id, category_name }`.

## 7. Transaccionalidad

`@Transactional`. El curso con `FOR UPDATE`, la categoría, la pareja, `INSERT`, auditoría, relectura del detalle.

## 8. Impacto sobre otros módulos

**Ninguno.** Dentro de `AC`, **seis requerimientos construidos se enmiendan** (Art. I.7), con su fila de control de cambios al construir.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`UPDATE` del curso con la lista antes y después** | `spec.md` §14.2 |
| **Bloquear la categoría** | `spec.md` §14.1 |
| **Clasificar en varias categorías por petición** | Un `400` de cinco causas con rollback parcial; una pareja por petición es la forma de toda relación del sistema |
| **`PUT /courses/{id}/categories` con la lista completa** | Reemplazar la lista obliga al cliente a reenviar lo que no toca, y «quité una» y «olvidé una» se confunden |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Se sustituye un literal y se olvida otro** de los seis | Los seis listados en §1 y §3, cada uno con su criterio (`CA-AC-127` a `CA-AC-129`) |
| 2 | **`findCategoriesOf` trae las retiradas** | El `JOIN` a `course_categories` lleva `deleted_at IS NULL`; `CA-AC-128` lo prueba |

## 11. Estrategia de prueba

- **Integración de API** (`CourseClassificationIT`): `CA-AC-124` a `CA-AC-126`; la carrera en `CourseConcurrencyIT` (`CA-AC-130`).
- **De las enmiendas**: `CA-AC-127` en `CourseCategoryListIT`, `CourseCategoryDetailIT` y `CourseCategoryDeletionIT`; `CA-AC-128` en `CourseListIT` —con el contador de sentencias— y `CourseDetailIT`; `CA-AC-129` en `CourseDeletionIT`. Las seis suites ganan casos y siguen en verde.
