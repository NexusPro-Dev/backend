# TASKS — `RF-AC-033` Consultar el catálogo de cursos como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-033` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 y reescrito el 26-09-2026 |
| Estado | **En desarrollo** |
| Issue | Pendiente de crear |
| Rama | `feature/aula-academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | ~~Extraer `LECCION_OFRECIBLE` y `MODULO_OFRECIBLE`~~ | — | Ya extraídos en el bloque 3 (`JpaCourseModuleQueryRepository`) | **No aplica** |
| `T-02` | `domain/models/StudentAccess`: `courseAccessible` con las dos llaves y la gratuidad, `lessonAccessible` | — | `StudentAccessTest`: los casos de `plan.md` §11 | Hecha |
| `T-03` | **`SP`**: `CurrentProductsLookup` y `JpaCurrentProductsLookup` | — | `CurrentProductsLookupIT`: vigente, vencido, cerrado, futuro, nivel sin producto | Hecha |
| `T-04` | `CourseQueryRepository`: `findClassroomCandidates(categoryId, difficulty)` con las tres cuentas del alumno; `findKeysOfCourses(ids)`; `CourseCategoryQueryRepository.findAlive()` | — | Integración a través de `ClassroomCatalogIT` | Hecha |
| `T-05` | `application/ClassroomCatalogRequest`, `ClassroomCatalogResponse` con `CurrentMembershipRef`, `ClassroomCategoryItem`, `ClassroomCourseItem` | — | El contrato declara las formas; `currentMembership` y `coverImageUrl` presentes y nulos | Hecha |
| `T-06` | `StudentKeys` y `GetClassroomCatalogService`: puertos, candidatos, `CourseOfferability`, llaves, `StudentAccess`, `onlyAccessible`, categorías | `T-02`..`T-05` | `CA-AC-186` a `CA-AC-191`, `CA-AC-237`, `CA-AC-238` | Hecha |
| `T-07` | `interfaces/ClassroomController`: `GET /api/v1/courses/available`, `@PreAuthorize("hasAuthority('courses:learn')")`, `@ParameterObject` | `T-06` | `CA-AC-193`; la ruta entra en `PERMISO_DE_CADA_OPERACION` | Hecha |
| `T-08` | Pruebas de API (`ClassroomCatalogIT`), con el contador de sentencias | `T-07` | `CA-AC-186` a `CA-AC-193`, `CA-AC-237`, `CA-AC-238` | Hecha |
| `T-09` | ~~Prueba de concordancia exhaustiva~~ | — | Retirada en `spec.md` 1.0.0: mismo objeto y mismas cuentas; `CA-AC-186` | **No aplica** |
| `T-10` | `CA-AC-032` de `RF-AC-005` se hace real: la categoría retirada no aparece en el aula y su curso sí | `T-08` | `CA-AC-189` | Hecha |
| `T-11` | Documentación OpenAPI. **La prosa dice** que el actor sale del token, que exige `courses:learn` y no `courses:read`, que se enseña todo lo ofrecido con `accessible` como marca salvo `onlyAccessible`, qué cuenta `openLessonCount`, que las categorías vienen sin filtro y que no se pagina | `T-07` | El contrato declara `200`, `400`, `401`, `403` | Hecha |
| `T-12` | Matriz de `docs/requirements.md` y `docs/api/index.md` | `T-08` | La fila de `RF-AC-033` refleja el estado | Hecha |

## 2. Orden de ejecución

`T-02`, `T-03` y `T-05` no dependen de nada; `T-04` tampoco; `T-06` las junta; `T-07` y `T-08` detrás.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-186`, `CA-AC-191` | `T-04`, `T-06`, `T-08` |
| `CA-AC-187`, `CA-AC-188`, `CA-AC-237` | `T-02`, `T-03`, `T-06`, `T-08` |
| `CA-AC-189`, `CA-AC-190` | `T-04`, `T-06`, `T-08` |
| `CA-AC-192` | `T-06`, `T-08` |
| `CA-AC-193` | `T-07`, `T-08` |
| `CA-AC-238` | `T-04`, `T-06`, `T-08` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha` o `No aplica`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
