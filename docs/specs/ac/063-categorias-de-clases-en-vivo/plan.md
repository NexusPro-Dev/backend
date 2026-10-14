# PLAN — `RF-AC-063` a `RF-AC-066` Las categorías de las clases en vivo

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-063`, `RF-AC-064`, `RF-AC-065`, `RF-AC-066` |
| Especificación | [`spec.md`](spec.md), aprobada el 14-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 14-10-2026 |

---

## 1. Enfoque

**La categoría es un agregado JPA**, como `CourseCategory` sin color, icono ni portada: `LiveSessionCategory` con `create`, `update` —que devuelve el diff— y `delete`, y un repositorio que **traduce la unicidad por el nombre de la restricción** (`uq_live_session_categories_name`) al mismo `409` que la comprobación previa. Los cuatro casos de uso van en **un solo servicio**, `LiveSessionCategoryService`: son pequeños y comparten el no-existe y la instantánea. Un controlador propio, `LiveSessionCategoryController`.

**Las clases siguen en SQL nativo.** `category_id` entra en el `INSERT` y en el `UPDATE` de `JpaLiveSessionRepository`, y **el nombre viaja con la fila**: `COLUMNAS` y la consulta del listado se unen a `live_session_categories`, de modo que **ninguna lectura gana sentencias**. `LiveSessionRow`, `LiveSessionItemRow` y `UpcomingRow` ganan `categoryId` y `categoryName`; el filtro gana `categoryId`. `LiveSessionSupport.categoria(id)` comprueba que la categoría esté viva —`422` `EX-002`— **antes de llamar a Zoom**, al programar y al corregir. La vitrina filtra en memoria, como `onlyAccessible`.

## 2. Cambios de esquema

`V107`:

1. `live_session_categories` (`id`, `name` `varchar(100)`, `display_order`, `created_at`, `updated_at`, `deleted_at`), con `ck_live_session_categories_display_order` y el índice único parcial `uq_live_session_categories_name` sobre `f_unaccent(lower(name))` entre las vivas.
2. Las tres sembradas con identificador literal (Art. V.11).
3. `live_sessions.category_id`: se añade nula, se rellena con «Clases», pasa a `NOT NULL`, con `fk_live_sessions_category` sin `ON DELETE` e índice.
4. Los cuatro permisos (`…7014` a `…7017`), las escrituras a `SUPERADMIN` y `ADMIN`, el listado además donde está `courses:teach` o `live-sessions:learn`.
5. Guardas: catálogo de 244 y ninguna clase sin categoría.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/models | **`LiveSessionCategory`** (nuevo) |
| domain/repository | **`LiveSessionCategoryRepository`** y su implementación (nuevos); `LiveSessionQueryRepository`, `LiveSessionRepository`, `JpaLiveSessionRepository` |
| domain/service | **`LiveSessionCategoryService`** (nuevo); `LiveSessionSupport`, `ScheduleLiveSessionService`, `UpdateLiveSessionService`, `ListLiveSessionsService`, `LiveClassroomService` |
| application | **`LiveSessionCategoryRequests`**, **`LiveSessionCategoryResponse`** (nuevos); `LiveSessionRequests`, `LiveSessionResponses`, `LiveSessionDetailResponse` |
| interfaces | **`LiveSessionCategoryController`** (nuevo); la prosa de `LiveSessionController` y `LiveClassroomController` |

## 4. Contrato de API

| Método | Ruta | Permiso | Respuesta |
|---|---|---|---|
| `GET` | `/api/v1/live-session-categories` | `live-session-categories:list` | `200`, lista |
| `POST` | `/api/v1/live-session-categories` | `live-session-categories:create` | `201` |
| `PATCH` | `/api/v1/live-session-categories/{id}` | `live-session-categories:update` | `200` |
| `POST` | `/api/v1/live-session-categories/{id}/deletion` | `live-session-categories:delete` | `204` |

**Cambio incompatible** en `POST /live-sessions` y `POST /live-sessions/mine`: `categoryId` obligatorio. Compatible en lo demás: `category` nuevo en las respuestas y `categoryId` nuevo en los filtros y al corregir.

## 5. Autorización

Una ruta, un permiso (`RN-SEG-014`, `RN-SEG-015`). Las de las clases, sin cambios.

## 6. Auditoría

Entidad `live_session_categories`: `CREATE` con `name` y `display_order`; `UPDATE` con antes y después de lo que cambió; eliminación lógica con el motivo y la instantánea. Las clases añaden `categoryId` a su instantánea, de modo que cambiarla sale en el diff del `UPDATE`.

## 7. Transaccionalidad

La de cada caso de uso. Al programar, la categoría se comprueba **fuera y antes** de la llamada a Zoom, como las demás referencias; si se retirara entre la comprobación y la escritura, la clase quedaría con ella, lo mismo que si se retirara un segundo después —se acepta—.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Reutilizar `course_categories`** | Llevan color, icono, portada y clasificación de muchos a muchos; mezclarlas pondría «Señales» entre los cajones de cursos |
| **Categoría opcional** | Obliga a decidir cómo vaciarla al corregir —el `Update` de la clase no distingue ausente de nulo— y deja clases sin rótulo en la agenda |
| **Rechazar retirar una categoría con clases** | La de cursos no lo hace (`RN-AC-018`), y las clases terminadas la tendrían para siempre: nunca se podría retirar |
| **Un enum en el código** | El responsable pidió crearlas, corregirlas y eliminarlas |

## 9. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El frontend programa sin `categoryId`** | Cambio incompatible avisado con el contrato |
| 2 | **Las suites escriben clases directo en la tabla** | `LiveSessionTestSupport` las pone en «Clases»; las suites no borran las sembradas |
| 3 | **Las suites que cuentan el catálogo** | Se pasan a 244 (y `ADMIN` a 242) en el mismo cambio |

## 10. Estrategia de prueba

Integración de API: `LiveSessionCategoriesIT` (`CA-AC-330` a `CA-AC-334`, `CA-AC-338`) y `LiveSessionCategoryAssignmentIT` (`CA-AC-335` a `CA-AC-337`); las suites de las clases, con `categoryId`; las del catálogo, con la cifra nueva.
