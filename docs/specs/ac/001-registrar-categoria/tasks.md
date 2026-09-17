# TASKS — `RF-AC-001` Registrar categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-001` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 17-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración **`V18__ac_categorias.sql`**: `course_categories` con `cover_image_id` nulable y sin clave foránea, los tres `CHECK` —color en mayúsculas, icono, orden— y el índice parcial `uq_course_categories_name` | — | Integración: aplica sobre `V17`; dos nombres iguales vivos se rechazan y con uno retirado no; un color en minúsculas se rechaza por el `CHECK`; un orden `-1` se rechaza | Pendiente |
| `T-02` | Migración **`V19__ac_semilla_permisos_categorias.sql`**: los cuatro `course-categories:` con identificador literal —serie propia de `AC`, `…000001` a `…000004`—, asociados a `SUPERADMIN` y `ADMIN`, con la guarda que cuenta ocho | — | `CourseCategoriesPermissionsSeedIT`: cuatro sembrados, identificadores estables, ocho asociaciones, ninguna a `CLIENTE`; `PermissionsSeedIT` y sus tres hermanas pasan de 50 a **54** | Pendiente |
| `T-03` | **El paquete `modules/academy`** con sus capas vacías y su fila en la regla de ArchUnit que aísla los módulos | — | La prueba de arquitectura en verde con `academy` incluido; una clase de prueba temporal que importe `modules.products.domain` la hace fallar y se retira | Pendiente |
| `T-04` | `domain/models/CourseCategory` y `CategoryColor`: recorta nombre y descripción, descripción vacía → nula, color normalizado a mayúsculas, y rechazo de color, icono y orden mal formados **con el mensaje de cada `VAL-`** | `T-03` | Unitaria: los seis casos de `plan.md` §11 | Pendiente |
| `T-05` | `CourseCategoryRepository` + `JpaCourseCategoryRepository`: `save` con traducción de `uq_course_categories_name` al `409` de `EX-001`, `existsAliveName`, `findAliveByIdForUpdate`, `findByIdForUpdate` | `T-01`, `T-04` | Integración: un `INSERT` duplicado llega como `BusinessRuleException` y no como `DataIntegrityViolationException` | Pendiente |
| `T-06` | `CourseCategoryQueryRepository.findDetail(id)`: la categoría con `courseCount` en una sentencia —cuenta literal `0` hasta `RF-AC-016`, con la nota en el código de dónde se completa— | `T-01` | Integración: una categoría devuelve su fila con `courseCount = 0`; la inexistente, vacío | Pendiente |
| `T-07` | `application/RegisterCourseCategoryRequest`, `CourseCategoryDetailResponse` (con `courseCount`, `courses` vacío, `coverImageUrl`) y `AcademyImageUrls` | `T-03` | Unitaria de `AcademyImageUrls`; el contrato declara los campos, `coverImageUrl` y `courses` **siempre presentes** | Pendiente |
| `T-08` | `domain/service/RegisterCourseCategoryService`: validación conjunta, nombre, inserción, auditoría `CREATE`, relectura del detalle | `T-05`, `T-06`, `T-07` | `CA-AC-001`, `CA-AC-002`, `CA-AC-003`, `CA-AC-006`, `CA-AC-007` | Pendiente |
| `T-09` | `interfaces/CourseCategoryController`: `POST /api/v1/course-categories`, `@PreAuthorize("hasAuthority('course-categories:create')")`, `201` con `Location` | `T-08` | `CA-AC-004`, `CA-AC-005`, `CA-AC-008`; la ruta entra en `EndpointPermissionsIT` con su permiso | Pendiente |
| `T-10` | Pruebas de API (`CourseCategoriesIT`) de los nueve criterios, incluida la carrera de dos altas con el mismo nombre en `CourseCategoryConcurrencyIT` | `T-09` | `CA-AC-001` a `CA-AC-009`; la carrera deja una fila y un `409` | Pendiente |
| `T-11` | Documentación OpenAPI. **La prosa dice** que nace sin portada, sin cursos y sin estado, que el color va sin `#` y sale en mayúsculas, que no es único, y que los `courses:` no habilitan | `T-09` | El contrato declara `201`, `400`, `401`, `403`, `409` | Pendiente |
| `T-12` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-10` | La fila de `RF-AC-001` refleja el estado; `api/index.md` documenta el recurso `/course-categories` y los cuatro permisos | Pendiente |

## 2. Orden de ejecución

**`T-03` primero**, antes que cualquier clase de negocio: el módulo tiene que existir aislado por ArchUnit antes de que alguien importe algo de `PM` «porque la forma es la misma». `T-01` y `T-02` son independientes entre sí y de `T-03`. `T-05` y `T-06` son independientes; `T-08` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-001`, `CA-AC-006`, `CA-AC-007` | `T-04`, `T-08`, `T-10` |
| `CA-AC-002`, `CA-AC-009` | `T-01`, `T-05`, `T-08`, `T-10` |
| `CA-AC-003` | `T-01`, `T-08` |
| `CA-AC-004`, `CA-AC-005` | `T-04`, `T-07`, `T-09` |
| `CA-AC-008` | `T-02`, `T-09` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Los números de migración se asignan al construir: `V18`/`V19` si nadie se adelanta. `MV` sigue construyendo en paralelo sobre el mismo árbol | 17-09-2026 | Responsable técnico | Abierto |
| 2 | `T-02` cambia el recuento del catálogo de permisos (50 → 54) en cuatro suites de `SP`; es la fricción deliberada de `security.md` §4.4 | 17-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **incluidas las cuatro suites del catálogo de permisos con el número nuevo y la de arquitectura con `academy`**.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
