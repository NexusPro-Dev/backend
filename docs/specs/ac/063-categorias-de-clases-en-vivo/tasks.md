# TASKS — `RF-AC-063` a `RF-AC-066` Las categorías de las clases en vivo

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-063`, `RF-AC-064`, `RF-AC-065`, `RF-AC-066` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 14-10-2026 |
| Estado | **En revisión** — `T-01` a `T-06` `Hecha` el 14-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V107`: la tabla, las tres sembradas, `live_sessions.category_id` y los cuatro permisos | — | La migración aplica | Hecha |
| `T-02` | `LiveSessionCategory`, su repositorio y `LiveSessionCategoryService` | `T-01` | — | Hecha |
| `T-03` | `LiveSessionCategoryController` con las cuatro rutas | `T-02` | — | Hecha |
| `T-04` | Las clases: `categoryId` al programar y corregir, `category` en las lecturas, el filtro | `T-01` | — | Hecha |
| `T-05` | `LiveSessionCategoriesIT` y `LiveSessionCategoryAssignmentIT`; las suites de las clases y del catálogo | `T-03`, `T-04` | `CA-AC-330` a `CA-AC-338`; suite completa en verde | Hecha |
| `T-06` | Contrato, `api/index.md` | `T-05` | — | Hecha |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-330`, `CA-AC-338` | `T-01`, `T-05` |
| `CA-AC-331` a `CA-AC-334` | `T-02`, `T-03`, `T-05` |
| `CA-AC-335` a `CA-AC-337` | `T-04`, `T-05` |

## 3. Bloqueos

Ninguno.

## Desviaciones respecto del plan

- **La suite completa del 14-10-2026 dio 122 errores en `system.auth`**, todos al limpiar `users` por una fila de `user_supervisors` que otra suite deja (`fk_user_supervisors_supervisor`): el mismo fallo, con la misma cifra, que dio la suite del 13-10-2026 antes de este cambio, que no toca `user_supervisors`. Las 598 unitarias y el resto de las de integración, en verde.
