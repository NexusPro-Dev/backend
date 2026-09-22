# TASKS — `RF-AC-033` Consultar el catálogo de cursos como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-033` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **Extraer `LECCION_OFRECIBLE` y `MODULO_OFRECIBLE`** en `JpaCourseQueryRepository` y hacer que el listado, los cursos de la categoría y el detalle los usen — refactorización sin cambio de resultado | `RF-AC-028` · `T-03`, `RF-AC-020` · `T-05` | `CourseListIT`, `CourseCategoryDetailIT` y `CourseDetailIT` en verde sin tocar sus aserciones | Pendiente |
| `T-02` | `domain/models/StudentAccess`: `courseAccessible`, `lessonAccessible` | — | Unitaria: los cuatro casos de `plan.md` §11 | Pendiente |
| `T-03` | `CourseQueryRepository`: `findOfferedCandidates(categoryId, difficulty)` con instructor, entradas de `CourseOfferability` y duración y cuenta de lo ofrecible; `findMembershipIdsOfCourses(ids)`; `CourseCategoryQueryRepository.findAlive()` | `T-01` | Integración: un candidato inactivo no sale; las cuentas ignoran lo no ofrecible | Pendiente |
| `T-04` | `application/ClassroomCatalogRequest`, `ClassroomCatalogResponse` con `CurrentMembershipRef`, `ClassroomCategoryItem`, `ClassroomCourseItem` | — | El contrato declara las tres formas; `currentMembership` y `coverImageUrl` presentes y nulos | Pendiente |
| `T-05` | `ClassroomCatalogReader` y `GetClassroomCatalogService`: puerto, candidatos, `CourseOfferability` por fila, categorías y membresías, `StudentAccess` | `T-02`, `T-03`, `T-04` | `CA-AC-186`, `CA-AC-187`, `CA-AC-188`, `CA-AC-189`, `CA-AC-190`, `CA-AC-191` | Pendiente |
| `T-06` | `interfaces/ClassroomController`: `GET /api/v1/courses/available`, `@PreAuthorize("hasAuthority('courses:learn')")`; `@ParameterObject` sobre los filtros | `T-05` | `CA-AC-193`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-07` | Pruebas de API (`ClassroomCatalogIT`) de `CA-AC-186` a `CA-AC-193`, con el contador de sentencias | `T-06` | `CA-AC-186` a `CA-AC-193` | Pendiente |
| `T-08` | **Prueba de concordancia** (`ClassroomOfferabilityAgreementIT`): las combinaciones de estado, descripciones, membresías, módulos y lecciones, comparando el aula con `offerable` del detalle | `T-06` | `CA-AC-194` | Pendiente |
| `T-09` | **Enmendar `RF-AC-005`**: `CA-AC-032` deja de ser trivial; su `tasks.md` cierra el bloqueo 1 y `CourseCategoryDeletionIT` gana el caso del aula | `T-07` | La categoría retirada no aparece en el aula y su curso sí | Pendiente |
| `T-10` | Documentación OpenAPI. **La prosa dice** que el actor sale del token, que exige `courses:learn` y no `courses:read`, que se enseña todo lo ofrecido con `accessible` como marca, que las categorías vienen sin filtro y con las vacías, y que no se pagina | `T-06` | El contrato declara `200`, `400`, `401`, `403` | Pendiente |
| `T-11` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-08` | La fila de `RF-AC-033` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01` va primera y sola —es una refactorización con tres suites como red—; `T-02` y `T-04` no dependen de nada; `T-03` sobre `T-01`; `T-05` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-186`, `CA-AC-191` | `T-01`, `T-03`, `T-05`, `T-07` |
| `CA-AC-187`, `CA-AC-188` | `T-02`, `T-05`, `T-07` |
| `CA-AC-189`, `CA-AC-190` | `T-03`, `T-05`, `T-07` |
| `CA-AC-192` | `T-05`, `T-07` |
| `CA-AC-193` | `T-06`, `T-07` |
| `CA-AC-194` | `T-08` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno: todo lo que lee existe desde los bloques 2 a 5 | | | |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **incluidas las tres suites de `T-01` sin cambios en sus aserciones**.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
