# TASKS — `RF-AC-025` Eliminar módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-025` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 19-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `application/DeleteCourseModuleRequest`; `CourseModule.delete`, `estaRetirado`, `instantanea(lessonIds)` | `RF-AC-022` · `T-02` | Unitaria: la instantánea lleva `status`, `course_id`, `lesson_ids` y `deleted_at` nulo | **Hecha el 19-09-2026** |
| `T-02` | `CourseModuleRepository.findByIdInCourseForUpdate` en cualquier estado | `RF-AC-022` · `T-03` | Integración: el retirado vuelve; el de otro curso, vacío | **Hecha el 19-09-2026** |
| `T-03` | `domain/service/DeleteCourseModuleService` sobre `CourseTreeRetirement` | `T-01`, `T-02`, `RF-AC-028` · `T-08` | `CA-AC-114`, `CA-AC-115`, `CA-AC-116`, `CA-AC-117` | **Hecha el 19-09-2026** |
| `T-04` | `interfaces/CourseModuleController`: `POST …/modules/{moduleId}/deletion`, `@PreAuthorize("hasAuthority('courses:update')")`, `204` | `T-03` | La ruta entra en `EndpointPermissionsIT` | **Hecha el 19-09-2026** |
| `T-05` | Pruebas de API (`CourseModuleDeletionIT`) y las dos carreras en `CourseModuleConcurrencyIT` | `T-04` | `CA-AC-114` a `CA-AC-118` | **Hecha el 19-09-2026** |
| `T-06` | Documentación OpenAPI. **La prosa dice** que arrastra las lecciones con un registro cada una, que no toca el curso, y que es `courses:update` | `T-04` | El contrato declara `204`, `400`, `401`, `403`, `404`, `409` | **Hecha el 19-09-2026** |
| `T-07` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-05` | La fila de `RF-AC-025` refleja el estado | **Hecha el 19-09-2026** |

## 2. Orden de ejecución

`T-01` y `T-02` independientes; `T-03` sobre el arrastre ya construido por `RF-AC-028`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-114`, `CA-AC-116`, `CA-AC-117` | `T-01`, `T-03`, `T-05` |
| `CA-AC-115` | `T-02`, `T-03`, `T-05` |
| `CA-AC-118` | `T-03`, `T-05` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno: `RF-AC-028` va antes en el orden de construcción | | | |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
