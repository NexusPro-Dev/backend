# TASKS — `RF-AC-018` Recomendar un curso previo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-018` |
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
| `T-01` | Migración **`V27__ac_recomendaciones.sql`**: `course_recommendations` con clave compuesta, las dos claves foráneas y `ck_course_recommendations_not_self` | `RF-AC-008` · `T-01` | Integración: aplica; la pareja repetida y `A → A` se rechazan | Pendiente |
| `T-02` | `domain/models/CourseRecommendation` y su clave; `CourseRecommendationRepository` + `Jpa…` con traducción de la clave primaria; `CourseRepository.findAliveById` sin bloqueo | `T-01` | Integración: el `INSERT` duplicado llega como `BusinessRuleException` | Pendiente |
| `T-03` | **Enmienda de `CourseQueryRepository`**: `findRecommendedOf` con `JOIN courses` y la ofrecibilidad, en el orden global; `findRecommendedIdsOf` | `T-01` | Integración: los recomendados vuelven en orden con estado, retiro y cuentas | Pendiente |
| `T-04` | `application/RecommendCourseRequest` con `VAL-003`; `domain/service/RecommendCourseService` | `T-02` | `CA-AC-145`, `CA-AC-146`, `CA-AC-147`, `CA-AC-148` | Pendiente |
| `T-05` | **Lectores enmendados**: `recommendedCourses` con `offerable` en `CourseDetailReader`; `recommended_course_ids` en la instantánea de `DeleteCourseService` | `T-03` | `CA-AC-149` | Pendiente |
| `T-06` | `interfaces/CourseController`: `POST /api/v1/courses/{courseId}/recommendations`, `@PreAuthorize("hasAuthority('courses:update')")`, `201` | `T-04` | La ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-07` | Pruebas de API (`CourseRecommendationIT`, las dos carreras en `CourseConcurrencyIT`) y los casos nuevos en `CourseDetailIT` y `CourseDeletionIT` | `T-05`, `T-06` | `CA-AC-145` a `CA-AC-150` | Pendiente |
| `T-08` | Documentación OpenAPI. **La prosa dice** que es una sugerencia y no un candado, que no se comprueban ciclos, que el recomendado puede no ofrecerse y el aula lo filtra, y que el `422` es del recomendado | `T-06` | El contrato declara `201`, `400`, `401`, `403`, `404`, `409`, `422` | Pendiente |
| `T-09` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md` y las specs de `RF-AC-010` y `RF-AC-013` | `T-07` | Las filas reflejan el estado | Pendiente |

## 2. Orden de ejecución

`T-01` → `T-02`/`T-03` → `T-04`/`T-05` → `T-06` → `T-07`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-145` a `CA-AC-148` | `T-02`, `T-04`, `T-07` |
| `CA-AC-149` | `T-03`, `T-05`, `T-07` — la mitad del aula, desde `RF-AC-034` |
| `CA-AC-150` | `T-02`, `T-07` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Los números de migración se asignan al construir: `V27` si nadie se adelanta | 18-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad, `docs/api/index.md` y las dos specs actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
