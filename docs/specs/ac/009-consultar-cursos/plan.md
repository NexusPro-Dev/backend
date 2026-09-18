# PLAN — `RF-AC-009` Consultar cursos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-009` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**El listado de categorías (`RF-AC-002`) con un `JOIN` de lectura, dos subconsultas y una segunda sentencia por página.**

La página se lee en una sentencia sobre `courses` con `JOIN users` para el instructor —las tres columnas de `RF-AC-008`— y **dos subconsultas escalares** para `moduleCount` y `lessonCount`, que hoy son literales cero y `RF-AC-022`/`RF-AC-028` sustituyen. Las categorías de todos los cursos de la página llegan en **una segunda sentencia fija** desde `RF-AC-016` —`course_category_items` unida a `course_categories` vivas, `WHERE course_id IN (:ids)`— y se agrupan en Java, como `findItemsOf` en `RF-PM-018`. `offerable` lo decide **`CourseOfferability`** por fila con las cuentas que ya vinieron: **el número de sentencias no depende de cuántos cursos tenga la página**.

## 2. Cambios de esquema

**Ninguno.** `ix_courses_instructor` (`V21`) sostiene el filtro por instructor; el de categoría lo sostendrá `ix_course_category_items_category` cuando exista.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `ListCoursesRequest` (`q`, `categoryId`, `instructorId`, `difficulty`, `status`, `includeDeleted`, `sort`, paginación, con validación conjunta), `CourseSortField` (`DISPLAY_ORDER`, `TITLE`, `CREATED_AT`), `CourseItem` (fila) con `CategoryRef`, `CoursePageResponse` | `AC` |
| `domain/repository` | `CourseQueryRepository.search(filtros, orden, offset, limit)`, `count(filtros)`, y **`findCategoriesOf(List<UUID> courseIds)`** —vacío hasta `RF-AC-016`— | `AC` |
| `domain/service` | `ListCoursesService` | `AC` |
| `interfaces` | `CourseController` — `GET /api/v1/courses` | `AC` |

## 4. Contrato de API

`GET /api/v1/courses?q=&categoryId=&instructorId=&difficulty=&status=&includeDeleted=&sort=&page=&size=` — `courses:read`.

Envoltura del sistema; cada fila: `id`, `title`, `instructor`, `difficulty`, `shortDescription`, `displayOrder`, `status`, `coverImageUrl`, `categories[]`, `offerable`, `moduleCount`, `lessonCount`, `createdAt`, `deletedAt` (`NON_NULL`).

- **`sort` por omisión `displayOrder`** ascendente con `id`; `title` por `f_unaccent(lower(title))`; `createdAt` desciende.
- **`categoryId` hasta `RF-AC-016`**: el predicado es `EXISTS (SELECT 1 FROM course_category_items …)` y, mientras la tabla no exista, un `false` literal con la nota en el código — devuelve vacío y no todo, porque «ningún curso está en esa categoría» es la verdad.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-005`, juntos |
| `401` / `403` | Sin sesión / sin `courses:read` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:read')")`; `course-categories:read` no habilita (`CA-AC-049`).

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Dos sentencias fijas** hoy —página con instructor y cuentas, y total—; **tres** desde `RF-AC-016`, con las categorías de la página, que se cortocircuita cuando la página está vacía. `CA-AC-048` lo cuenta.

## 8. Impacto sobre otros módulos

**Ninguno en código.** El `JOIN users` es de lectura y tiene el precedente de `RF-PM-012` (`RF-AC-008` §3).

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una sentencia por curso para sus categorías** | `N+1`; la página de veinte costaría veintidós |
| **`categories` como `string_agg` en la sentencia de página** | Devuelve texto que hay que partir en Java, y una categoría con coma en el nombre lo rompe; la segunda sentencia agrupa objetos |
| **Filtro por `offerable`** | Rompe el total o repite la regla (`RF-PM-018` §14.1) |
| **`categoryId` ignorado hasta `RF-AC-016`** | Devolvería todo cuando la verdad es nada; peor que un vacío honesto |
| **Traer `offerableReason` por fila** | Exportación de fallos; el detalle lo dice |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **`findCategoriesOf` con lista vacía** rompe el `IN` | Se cortocircuita: página vacía, cero sentencias de categorías |
| 2 | **El total se calcula con `JOIN`** y multiplica | `count` no une nada: cuenta cursos |
| 3 | **Se olvida sustituir los literales** de cuentas y categoría | Cada uno con su nota, y `CA-AC-044`/`CA-AC-047` quedan como criterios que los tres requerimientos enmiendan |

## 11. Estrategia de prueba

- **Integración de API** (`CourseListIT`): los siete criterios; **la que define el requerimiento es `CA-AC-045`**, la del orden con desempate.
- **De sentencias**: `CA-AC-048` con página de uno y de veinte.
- **Del instructor retirado**: `CA-AC-050`, retirándolo por SQL en `users` y leyendo la lista.
