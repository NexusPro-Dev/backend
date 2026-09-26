# TASKS — `RF-AC-035` Consultar el contenido de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-035` |
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
| `T-01` | `shared/error/ForbiddenException`: constructor con extensiones | — | El `403` de `EX-002` lleva `memberships` y `products` | Pendiente |
| `T-02` | `LessonQueryRepository.findClassroomLesson(courseId, lessonId)` | — | Integración a través de `ClassroomLessonIT` | Pendiente |
| `T-03` | `application/ClassroomLessonResponse` | — | El contrato declara `content` | Pendiente |
| `T-04` | `GetClassroomLessonService`: tres objetos, abierta y gratuita sin puertos, `StudentKeys`, listas, `StudentAccess`, `403` con extensiones | `T-01`..`T-03`, `RF-AC-033` · `T-02`, `T-06` | `CA-AC-202` a `CA-AC-207`, `CA-AC-239` | Pendiente |
| `T-05` | `ClassroomController`: `GET /api/v1/courses/available/{courseId}/lessons/{lessonId}` con `lessons:learn` | `T-04` | `CA-AC-208`; la ruta entra en `PERMISO_DE_CADA_OPERACION` | Pendiente |
| `T-06` | Pruebas de API (`ClassroomLessonIT`), con el contador de sentencias | `T-05` | `CA-AC-202` a `CA-AC-208`, `CA-AC-239` | Pendiente |
| `T-07` | Documentación OpenAPI: el orden `404` antes que `403`, las extensiones del `403`, el contenido tal cual | `T-05` | El contrato declara `200`, `400`, `401`, `403`, `404` | Pendiente |
| `T-08` | Matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-AC-035` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01` a `T-03` sueltas; `T-04` las junta con el catálogo; `T-05` y `T-06` detrás.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-202`, `CA-AC-203`, `CA-AC-239` | `T-02`, `T-04`, `T-06` |
| `CA-AC-204` | `T-01`, `T-04`, `T-06` |
| `CA-AC-205`, `CA-AC-206` | `T-02`, `T-04`, `T-06` |
| `CA-AC-207` | `T-04`, `T-06` |
| `CA-AC-208` | `T-05`, `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
