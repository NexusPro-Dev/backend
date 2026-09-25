# TASKS — `RF-AC-016` Clasificar un curso en una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-016` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 25-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/ajustes-academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración **`V42__ac_clasificacion.sql`**: `course_category_items` con clave compuesta, dos claves foráneas e `ix_course_category_items_category` | `RF-AC-008` · `T-01` | Integración: aplica; la pareja repetida se rechaza | Hecha |
| `T-02` | `domain/models/CourseCategoryItem` y `CourseCategoryItemId`; `CourseCategoryItemRepository` + `Jpa…` con traducción de la clave primaria al `409` | `T-01` | Integración: el `INSERT` duplicado llega como `BusinessRuleException` | Hecha |
| `T-03` | **Enmienda de `CourseCategoryQueryRepository`**: la subconsulta de `courseCount` sustituye el literal; `findAliveCoursesOf` con lo que `CourseOfferability` necesita; `findAliveCourseIdsOf` | `T-01` | Integración: vivos cuentan, inactivos cuentan, retirados no; los cursos vuelven en orden | Hecha |
| `T-04` | **Enmienda de `CourseQueryRepository`**: `findCategoriesOf` en una sentencia por página, el predicado `EXISTS` de `categoryId`, `findCategoryIdsOf` | `T-01` | Integración: una sentencia para varias categorías de varios cursos; las retiradas no vuelven; el filtro acota | Hecha |
| `T-05` | `application/ClassifyCourseRequest`; `domain/service/ClassifyCourseService`: curso vivo con `FOR UPDATE`, categoría viva (`422`), pareja (`409` nombrando), inserción, auditoría, relectura | `T-02` | `CA-AC-124`, `CA-AC-125`, `CA-AC-126` | Hecha |
| `T-06` | **Lectores enmendados**: `CourseCategoryDetailReader` con los cursos y su `offerable`, `CourseDetailReader` con `categories`, y las instantáneas de `DeleteCourseCategoryService` y `DeleteCourseService` con los identificadores | `T-03`, `T-04` | `CA-AC-127`, `CA-AC-128`, `CA-AC-129` | Hecha |
| `T-07` | `interfaces/CourseController`: `POST /api/v1/courses/{courseId}/categories`, `@PreAuthorize("hasAuthority('courses:update')")`, `201` | `T-05` | La ruta entra en `EndpointPermissionsIT` | Hecha |
| `T-08` | Pruebas de API (`CourseClassificationIT`, la carrera en `CourseConcurrencyIT`) y los casos nuevos en las seis suites enmendadas | `T-06`, `T-07` | `CA-AC-124` a `CA-AC-130` | Hecha |
| `T-09` | Documentación OpenAPI. **La prosa dice** que es libre y sin repetir, que no toca el estado, que el `422` es de la categoría y el `404` del curso, y que el `409` la nombra; **las `@Operation` de las seis operaciones enmendadas dejan de decir que la clasificación no existe** | `T-07` | El contrato declara `201`, `400`, `401`, `403`, `404`, `409`, `422` | Hecha |
| `T-10` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md`, las seis specs enmendadas y **`ac.md` (`RN-AC-010` precisada: la auditoría es `CREATE`/`ASSOCIATION` de la fila)** | `T-08` | Las filas reflejan el estado; `ac.md` sube de versión | Hecha |

## 2. Orden de ejecución

`T-01` primero; `T-02`, `T-03` y `T-04` independientes; `T-05` y `T-06` en paralelo; `T-08` al final con las seis suites en verde.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-124`, `CA-AC-125`, `CA-AC-126` | `T-02`, `T-05`, `T-08` |
| `CA-AC-127` | `T-03`, `T-06`, `T-08` |
| `CA-AC-128` | `T-04`, `T-06`, `T-08` |
| `CA-AC-129` | `T-04`, `T-06`, `T-08` |
| `CA-AC-130` | `T-02`, `T-08` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Los números de migración se asignan al construir: `V25` si nadie se adelanta | 18-09-2026 | Responsable técnico | **Cerrado** el 25-09-2026: `V42`, la siguiente libre en `develop` |
| 2 | `offerable` de los cursos de la categoría (`CA-AC-127`) necesita los bloques 3 y 4 completos para ser verdadero en algún caso | 18-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local, **con las seis suites enmendadas**.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad, `docs/api/index.md`, las seis specs y `ac.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
