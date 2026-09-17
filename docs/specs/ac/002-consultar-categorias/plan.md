# PLAN — `RF-AC-002` Consultar categorías

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-002` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 17-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

---

## 1. Enfoque

**El listado del producto (`RF-PM-002`) con una subconsulta de cuenta y otro orden por omisión.**

Dos sentencias fijas —página y total—, y la cuenta de cursos vivos **dentro de la sentencia de página** como subconsulta escalar sobre `course_category_items` unida a `courses` por `deleted_at IS NULL`. No hay `JOIN` que multiplique filas ni segunda pasada: la cuenta es una columna más. **Hasta `RF-AC-016` la subconsulta no existe y la columna es un literal `0`**; ese requerimiento la reemplaza y la prueba `CA-AC-010` deja de ser trivial.

## 2. Cambios de esquema

**Ninguno.** El orden por `display_order` no necesita índice: la tabla son decenas de filas y el planificador la recorre entera más barato que por índice. Queda anotado el disparador de revisión: si el catálogo pasa de unos cientos, `(display_order, id)` parcial sobre `deleted_at IS NULL`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `ListCourseCategoriesRequest` (`q`, `includeDeleted`, `sort`, paginación, con validación conjunta como `ListProductsRequest`), `CourseCategorySortField` (`DISPLAY_ORDER`, `NAME`, `CREATED_AT`), `CourseCategoryItem` (fila), `CourseCategoryPageResponse` | `AC` |
| `domain/repository` | `CourseCategoryQueryRepository.search(filtros, orden, offset, limit)` y `count(filtros)` | `AC` |
| `domain/service` | `ListCourseCategoriesService` | `AC` |
| `interfaces` | `CourseCategoryController` — `GET /api/v1/course-categories` | `AC` |

## 4. Contrato de API

`GET /api/v1/course-categories?q=&includeDeleted=&sort=&page=&size=` — `course-categories:read`.

Envoltura del sistema; cada fila: `id`, `name`, `color`, `icon`, `displayOrder`, `coverImageUrl`, `courseCount`, `createdAt`, `deletedAt` (`NON_NULL`).

- **`sort` por omisión es `displayOrder`**, ascendente, con `id` ascendente de desempate. `name` ordena por `f_unaccent(lower(name))` para que el orden alfabético no separe «Álgebra» de «Algoritmos»; `createdAt` desciende, como en `PM`.
- **`coverImageUrl`** lo construye `AcademyImageUrls` sobre `cover_image_id`, en Java, sin consulta.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-003`, juntos |
| `401` / `403` | Sin sesión / sin `course-categories:read` |

## 5. Autorización

`@PreAuthorize("hasAuthority('course-categories:read')")`; `courses:read` no habilita (`CA-AC-015`).

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Dos sentencias fijas**: página y total. `CA-AC-014` lo cuenta con una página de uno y una de veinte.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Orden por fecha de alta por omisión, como el producto** | La categoría tiene orden propio y esta es la vista para comprobarlo (`spec.md` §14.1) |
| **`courseCount` con un `LEFT JOIN` y `GROUP BY`** | Agrupa toda la página por diez columnas para contar una; la subconsulta escalar cuenta lo mismo y deja la sentencia legible |
| **`courseCount` en una segunda sentencia por identificadores** | Una sentencia más para una cuenta que cabe en la primera |
| **Contar los ofrecidos** | Cuatro tablas por fila (`spec.md` §14.2) |
| **Filtro por «vacía»** | Rompe el total o repite la cuenta; el mismo argumento de `RF-PM-018` §14.1 |
| **Índice sobre `display_order`** | Decenas de filas; el recorrido completo es más barato. Disparador anotado en §2 |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La subconsulta cuenta cursos retirados** cuando llegue `RF-AC-016` | El predicado `c.deleted_at IS NULL` está escrito en este plan y `CA-AC-010` lo cruza con el detalle; el caso límite de §13 lo prueba |
| 2 | **El orden por nombre separa acentos** | `f_unaccent(lower(name))` en el `ORDER BY`, la misma expresión del índice de unicidad |

## 11. Estrategia de prueba

- **Integración de API** (`CourseCategoryListIT`): los seis criterios; **la que define el requerimiento es `CA-AC-011`**, la del orden con desempate por antigüedad.
- **De sentencias**: `CA-AC-014` con el contador de sentencias de las pruebas de `PM`.
- **De coherencia con el detalle**: `courseCount` de la fila es el del detalle (`CA-AC-010`) — trivial hasta `RF-AC-016`, y ese requerimiento la enmienda con cursos vivos, inactivos y retirados.
