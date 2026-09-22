# TASKS — `RF-AC-023` Editar módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-023` |
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
| `T-01` | `application/UpdateCourseModuleRequest` con los cinco `Patchable` e `informaAlgo()` | `RF-AC-022` · `T-06` | Unitaria: ausente, nulo y valor se distinguen | **Hecha el 19-09-2026** |
| `T-02` | `CourseModule.update(...)`: aplica, valida con el mensaje de cada `VAL-`, devuelve el mapa de cambios | `RF-AC-022` · `T-02` | Unitaria: los casos de `plan.md` §11 | **Hecha el 19-09-2026** |
| `T-03` | `CourseModuleRepository.existsAliveTitleInCourseForOther` y `findAliveByIdInCourseForUpdate` | `RF-AC-022` · `T-03` | Integración: el propio no choca; otro vivo del curso sí; el de otro curso no; el módulo de otro curso devuelve vacío | **Hecha el 19-09-2026** |
| `T-04` | `domain/service/UpdateCourseModuleService` | `T-01` a `T-03` | `CA-AC-094`, `CA-AC-097`, `CA-AC-098` | **Hecha el 19-09-2026** |
| `T-05` | `interfaces/CourseModuleController`: `PATCH /api/v1/courses/{courseId}/modules/{moduleId}`, `@PreAuthorize("hasAuthority('courses:update')")` | `T-04` | `CA-AC-095`, `CA-AC-096`; la ruta entra en `EndpointPermissionsIT` | **Hecha el 19-09-2026** |
| `T-06` | Pruebas de API (`CourseModuleUpdateIT`) de los cinco criterios, incluida la lectura del detalle del curso tras reordenar | `T-05` | `CA-AC-094` a `CA-AC-098` | **Hecha el 19-09-2026** |
| `T-07` | Documentación OpenAPI. **La prosa dice** que es parcial, qué se vacía y que vaciar no desactiva, que el curso no se corrige, y que el módulo de otro curso es `404` | `T-05` | El contrato declara `200`, `400`, `401`, `403`, `404`, `409` | **Hecha el 19-09-2026** |
| `T-08` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-AC-023` refleja el estado | **Hecha el 19-09-2026** |

## 2. Orden de ejecución

`T-01` a `T-03` independientes; `T-04` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-094`, `CA-AC-098` | `T-02`, `T-04`, `T-06` |
| `CA-AC-095`, `CA-AC-096` | `T-01`, `T-05`, `T-06` |
| `CA-AC-097` | `T-03`, `T-04`, `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
