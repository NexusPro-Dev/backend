# TASKS — `RF-AC-036` Consultar el detalle de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-036` |
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
| `T-01` | `LessonQueryRepository.findDetail(courseId, moduleId, lessonId)` en cualquier estado, con la pertenencia en el `WHERE` | `RF-AC-028` · `T-03` | Integración: una lección de otro módulo y un módulo de otro curso devuelven vacío | **Hecha el 19-09-2026** |
| `T-02` | `application/LessonResponse`: `deletedAt` y `deletionReason` `NON_NULL` | `RF-AC-028` · `T-04` | Las suites de las escrituras de la lección siguen en verde sin cambiar sus aserciones | **Hecha el 19-09-2026** |
| `T-03` | `LessonDetailReader`, enmendado con la rama de la retirada por `DeletionReasonReader`; `GetLessonService` | `T-01`, `T-02` | `CA-AC-209`, `CA-AC-210`, `CA-AC-211` | **Hecha el 19-09-2026** |
| `T-04` | `interfaces/LessonController`: `GET /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}`, `@PreAuthorize("hasAuthority('courses:read')")` | `T-03` | `CA-AC-213`; la ruta entra en `EndpointPermissionsIT` | **Hecha el 19-09-2026** |
| `T-05` | Pruebas de API (`LessonDetailIT`) de los cinco criterios, con el contador de sentencias y la comparación de forma con el alta; **la arrastrada** montada con `RF-AC-013` | `T-04` | `CA-AC-209` a `CA-AC-213` | **Hecha el 19-09-2026** |
| `T-06` | Documentación OpenAPI. **La prosa dice** que es la lectura de administración y devuelve también la retirada con su motivo, que el contenido viaja entero, y que la ruta afirma la pertenencia | `T-04` | El contrato declara `200`, `400`, `401`, `403`, `404` | **Hecha el 19-09-2026** |
| `T-07` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md`; **cerrar el bloqueo 1 de `RF-AC-029`** en su `tasks.md` | `T-05` | La fila de `RF-AC-036` refleja el estado | **Hecha el 19-09-2026** |

## 2. Orden de ejecución

`T-01` y `T-02` son independientes; `T-03` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-209` | `T-02`, `T-03`, `T-05` |
| `CA-AC-210` | `T-03`, `T-05` |
| `CA-AC-211` | `T-01`, `T-03`, `T-05` |
| `CA-AC-212` | `T-03`, `T-05` |
| `CA-AC-213` | `T-04`, `T-05` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
