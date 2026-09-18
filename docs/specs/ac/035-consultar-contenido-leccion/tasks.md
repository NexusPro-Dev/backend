# TASKS — `RF-AC-035` Consultar el contenido de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-035` |
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
| `T-01` | `shared/error/ForbiddenException`: constructor con extensiones | — | Unitaria: las extensiones llegan al `ProblemDetail` por el manejador existente | Pendiente |
| `T-02` | `LessonQueryRepository.findOfferedLesson(courseId, lessonId)`: la lección con contenido y las entradas de los tres niveles, con `c.id = :courseId` en el `JOIN` | `RF-AC-034` · `T-01`, `RF-AC-033` · `T-01` | Integración: una lección de otro curso no se devuelve; las entradas de cada nivel llegan | Pendiente |
| `T-03` | `application/ClassroomLessonResponse` | — | El contrato declara `content` y `description` presente y nula | Pendiente |
| `T-04` | `ClassroomLessonReader` y `GetClassroomLessonService`: los tres objetos en orden y el `404` único; la abierta sin puerto; puerto, `findMembershipsOf` y `StudentAccess` para la cerrada; `EX-002` con `memberships` | `T-01`, `T-02`, `T-03` | `CA-AC-202` a `CA-AC-206` | Pendiente |
| `T-05` | `ClassroomController`: `GET /api/v1/courses/available/{courseId}/lessons/{lessonId}`, `@PreAuthorize("hasAuthority('courses:learn')")` | `T-04`, `RF-AC-033` · `T-06` | `CA-AC-208`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-06` | Pruebas de API (`ClassroomLessonIT`) de los siete criterios, con el contador de sentencias y el Markdown con `<script>` comparado byte a byte | `T-05` | `CA-AC-202` a `CA-AC-208` | Pendiente |
| `T-07` | Documentación OpenAPI. **La prosa dice** el orden —primero si se ofrece, después si se abre—, que la abierta no exime de ofrecerse, que el `403` de membresía lleva `memberships` y el de permiso no, y que el contenido viaja sin interpretar con la obligación del frontend de `ac.md` §5.2.4 | `T-05` | El contrato declara `200`, `400`, `401`, `403` (los dos), `404` | Pendiente |
| `T-08` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-AC-035` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01`, `T-02` y `T-03` son independientes; `T-04` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-202`, `CA-AC-203` | `T-02`, `T-04`, `T-06` |
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
