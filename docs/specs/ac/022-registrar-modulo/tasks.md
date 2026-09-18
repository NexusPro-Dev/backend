# TASKS — `RF-AC-022` Registrar módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-022` |
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
| `T-01` | Migración **`V23__ac_modulos.sql`**: `course_modules` con `fk_course_modules_course`, los tres `CHECK`, `cover_image_id` nulable sin FK, `uq_course_modules_title` compuesto y parcial, `ix_course_modules_course` | `RF-AC-008` · `T-01` | Integración: aplica; dos títulos iguales vivos en el mismo curso se rechazan, en cursos distintos no, con uno retirado no | Pendiente |
| `T-02` | `domain/models/CourseModule` sobre `CourseStatus` y `VideoUrl`; **`ModuleOfferability`**; `CourseOfferability` recibe módulos ofrecibles | `RF-AC-008` · `T-05`, `T-06` | Unitaria: los casos de `plan.md` §11; las pruebas de `CourseOfferability` siguen en verde con la firma nueva | Pendiente |
| `T-03` | `CourseModuleRepository` + `Jpa…` con traducción de `uq_course_modules_title` | `T-01`, `T-02` | Integración: el `INSERT` duplicado llega como `BusinessRuleException` | Pendiente |
| `T-04` | `CourseModuleQueryRepository.findDetail(moduleId)` y `findLessonsOf` —vacío hasta `RF-AC-028`, con la nota— | `T-01` | Integración: el módulo vuelve con su `course_id`; el inexistente, vacío | Pendiente |
| `T-05` | **Enmienda de `CourseQueryRepository`**: la subconsulta de `moduleCount`, `findModulesOf` en orden, `countActiveModulesOf`, `findAliveModulesForUpdate` — sustituyen los cuatro literales del bloque 2 | `T-01` | Integración: un curso con dos módulos vivos y uno retirado cuenta dos, los devuelve en orden con el retirado marcado, y cuenta uno activo | Pendiente |
| `T-06` | `application/RegisterCourseModuleRequest` y `CourseModuleDetailResponse`; `domain/service/CourseModuleDetailReader` | `T-02`, `T-04` | El contrato declara la forma; `lessons`, `coverImageUrl`, `offerable` y `offerableReason` siempre presentes | Pendiente |
| `T-07` | `domain/service/RegisterCourseModuleService`: validación conjunta, curso vivo con `FOR UPDATE`, título dentro del curso, inserción en `INACTIVO`, auditoría, relectura | `T-03`, `T-06` | `CA-AC-076`, `CA-AC-077`, `CA-AC-078`, `CA-AC-080` | Pendiente |
| `T-08` | **`CourseDetailReader` y `CourseTreeRetirement`, enmendados**: el árbol de módulos con su ofrecibilidad, y el arrastre de módulos con una baja por fila | `T-02`, `T-05` | `CA-AC-082`, `CA-AC-083`; `CA-AC-064` de `RF-AC-012` queda a un paso —falta la lección activa de `RF-AC-024`— | Pendiente |
| `T-09` | `interfaces/CourseModuleController`: `POST /api/v1/courses/{courseId}/modules`, `@PreAuthorize("hasAuthority('courses:update')")`, `201` con `Location` | `T-07` | `CA-AC-079`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-10` | Pruebas de API (`CourseModulesIT`, `CourseModuleConcurrencyIT`) y los casos nuevos en `CourseListIT`, `CourseDetailIT` y `CourseDeletionIT` | `T-08`, `T-09` | `CA-AC-076` a `CA-AC-084` | Pendiente |
| `T-11` | Documentación OpenAPI. **La prosa dice** que nace dentro del curso e inactivo, que el título es único en el curso, que devuelve el módulo y no el curso, y que las lecciones y la portada llegan después; **las `@Operation` de `RF-AC-009`, `RF-AC-010`, `RF-AC-012` y `RF-AC-013` dejan de decir que los módulos no existen** | `T-09` | El contrato declara `201`, `400`, `401`, `403`, `404`, `409`; las cuatro operaciones enmendadas releídas | Pendiente |
| `T-12` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md`, y las specs de `RF-AC-009`, `RF-AC-010`, `RF-AC-012` y `RF-AC-013` con su fila de enmienda construida | `T-10` | Las cinco filas reflejan el estado | Pendiente |

## 2. Orden de ejecución

`T-01` y `T-02` primero; `T-05` antes que `T-08`; `T-10` al final, con las tres suites del bloque 2 en verde con sus casos nuevos.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-076`, `CA-AC-078`, `CA-AC-080` | `T-02`, `T-07`, `T-10` |
| `CA-AC-077`, `CA-AC-084` | `T-01`, `T-03`, `T-07`, `T-10` |
| `CA-AC-079` | `T-06`, `T-09` |
| `CA-AC-081`, `CA-AC-082`, `CA-AC-083` | `T-05`, `T-08`, `T-10` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Los números de migración se asignan al construir: `V23` si nadie se adelanta | 18-09-2026 | Responsable técnico | Abierto |
| 2 | `lessons` y `durationMinutes` del módulo son literales hasta `RF-AC-028`, y `ModuleOfferability` no puede decir «ofrecible» hasta entonces | 18-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **con las tres suites enmendadas del bloque 2**.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida, **también en las cuatro operaciones enmendadas**.
- [ ] Matriz de trazabilidad, `docs/api/index.md` y las cuatro specs enmendadas actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
