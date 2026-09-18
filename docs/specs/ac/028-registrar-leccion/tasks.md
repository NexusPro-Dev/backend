# TASKS — `RF-AC-028` Registrar lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-028` |
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
| `T-01` | Migración **`V24__ac_lecciones.sql`**: `lessons` con `fk_lessons_module`, los cinco `CHECK`, `uq_lessons_title` compuesto y parcial, `ix_lessons_module` | `RF-AC-022` · `T-01` | Integración: aplica; `VIDEO` con texto sin esquema se rechaza, `TEXTO` con cualquier texto no; duración cero se rechaza | Pendiente |
| `T-02` | `domain/models/Lesson`, `LessonType`, **`LessonContent`**; `instantanea()` con `content_length`; `ModuleOfferability` y `CourseOfferability` con sus cuentas reales —**la de lecciones cuenta solo las que tienen contenido**— | `RF-AC-022` · `T-02` | Unitaria: los casos de `plan.md` §11 | Pendiente |
| `T-03` | `LessonRepository` + `Jpa…` con traducción de `uq_lessons_title` | `T-01`, `T-02` | Integración: el `INSERT` duplicado llega como `BusinessRuleException` | Pendiente |
| `T-04` | `LessonQueryRepository.findDetail(lessonId)` con módulo y curso | `T-01` | Integración: la lección vuelve con `module_id` y `course_id` | Pendiente |
| `T-05` | **Enmienda de `CourseModuleQueryRepository` y `CourseQueryRepository`**: `findLessonsOf`, `findLessonsOfModules`, la subconsulta de `lessonCount`, `countActiveLessonsOf`, las lecciones en `findAliveModulesForUpdate` | `T-01` | Integración: un módulo con dos lecciones vivas y una retirada cuenta dos y las devuelve en orden; una sentencia para las lecciones de varios módulos | Pendiente |
| `T-06` | `application/RegisterLessonRequest` y `LessonResponse`; `domain/service/LessonDetailReader` | `T-02`, `T-04` | El contrato declara la forma con `content` | Pendiente |
| `T-07` | `domain/service/RegisterLessonService`: validación conjunta con el contenido contra el tipo, módulo vivo **del curso de la ruta** con `FOR UPDATE`, título dentro del módulo, inserción, auditoría, relectura | `T-03`, `T-06` | `CA-AC-085`, `CA-AC-086`, `CA-AC-087`, `CA-AC-089` | Pendiente |
| `T-08` | **`CourseModuleDetailReader`, `CourseDetailReader` y `CourseTreeRetirement`, enmendados**: lecciones en los dos árboles con duración sumada sobre activas, ofrecibilidad real, y arrastre de lecciones con una baja por fila | `T-02`, `T-05` | `CA-AC-090`, `CA-AC-091`, `CA-AC-092` | Pendiente |
| `T-09` | `interfaces/LessonController`: `POST /api/v1/courses/{courseId}/modules/{moduleId}/lessons`, `@PreAuthorize("hasAuthority('courses:update')")`, `201` con `Location` | `T-07` | `CA-AC-088`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-10` | Pruebas de API (`LessonsIT`, `LessonConcurrencyIT` con la carrera y el interbloqueo alta-lección/retiro-curso) y los casos nuevos en `CourseListIT`, `CourseDetailIT`, `CourseDeletionIT` y `CourseModulesIT` | `T-08`, `T-09` | `CA-AC-085` a `CA-AC-093` | Pendiente |
| `T-11` | Documentación OpenAPI. **La prosa dice** que el tipo manda sobre el contenido, que el Markdown no se interpreta y que el frontend debe pintarlo sin ejecutarlo, que la duración es obligatoria en los dos tipos, y qué es `open`; **las `@Operation` enmendadas dejan de decir que las lecciones no existen** | `T-09` | El contrato declara `201`, `400`, `401`, `403`, `404`, `409` | Pendiente |
| `T-12` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md`, y las specs de `RF-AC-009`, `RF-AC-010`, `RF-AC-013` y `RF-AC-022` con su fila de enmienda construida | `T-10` | Las cinco filas reflejan el estado | Pendiente |

## 2. Orden de ejecución

`T-01` y `T-02` primero; `T-05` antes que `T-08`; `T-10` al final con las cuatro suites enmendadas en verde.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-085`, `CA-AC-087`, `CA-AC-089` | `T-02`, `T-07`, `T-10` |
| `CA-AC-086`, `CA-AC-093` | `T-01`, `T-03`, `T-07`, `T-10` |
| `CA-AC-088` | `T-02`, `T-06`, `T-09` |
| `CA-AC-090`, `CA-AC-091`, `CA-AC-092` | `T-05`, `T-08`, `T-10` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Los números de migración se asignan al construir: `V24` si nadie se adelanta | 18-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **con las cuatro suites enmendadas**.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad, `docs/api/index.md` y las cuatro specs enmendadas actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
