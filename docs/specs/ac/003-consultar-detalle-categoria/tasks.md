# TASKS — `RF-AC-003` Consultar el detalle de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-003` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 17-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `CourseCategoryQueryRepository.findAliveCoursesOf(categoryId)`: identificador, título, estado y orden de los cursos vivos, por `display_order, id` — **devuelve vacío hasta `RF-AC-016`**, con la nota en el código de qué sentencia lo sustituye | `RF-AC-001` · `T-06` | Integración: devuelve lista vacía; la firma y el DTO de fila existen | Pendiente |
| `T-02` | `application/CategoryCourseItem` y `CourseCategoryDetailResponse` con `deletedAt` y `deletionReason` (`NON_NULL`) | `RF-AC-001` · `T-07` | El contrato declara los dos como opcionales y `courses` como lista siempre presente | Pendiente |
| `T-03` | `domain/service/GetCourseCategoryService`: detalle en cualquier estado, cursos si `courseCount > 0`, motivo por `DeletionReasonReader` si retirada | `T-01`, `T-02` | `CA-AC-016`, `CA-AC-018` | Pendiente |
| `T-04` | `interfaces/CourseCategoryController`: `GET /api/v1/course-categories/{id}`, `@PreAuthorize("hasAuthority('course-categories:read')")` | `T-03` | `CA-AC-020`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-05` | Pruebas de API (`CourseCategoryDetailIT`) de los cinco criterios, con el contador de sentencias para `CA-AC-019` y la retirada sin registro de eliminación | `T-04` | `CA-AC-016` a `CA-AC-020` | Pendiente |
| `T-06` | Documentación OpenAPI. **La prosa dice** que se devuelve también una retirada con su motivo, que `courses` son los vivos en su orden, y que `offerable` es del curso | `T-04` | El contrato declara `200`, `400`, `401`, `403`, `404` | Pendiente |
| `T-07` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-05` | La fila de `RF-AC-003` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01` y `T-02` son independientes; `T-03` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-016`, `CA-AC-018` | `T-01`, `T-03`, `T-05` |
| `CA-AC-017` | `T-01`, `T-05` — **real desde `RF-AC-016`** |
| `CA-AC-019` | `T-03`, `T-05` |
| `CA-AC-020` | `T-04`, `T-05` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `courses` vacío hasta `RF-AC-008` y `RF-AC-016`; `offerable` literal hasta que el bloque 3 construya `CourseOfferability` (`RN-AC-015`). Las tres enmiendas están declaradas en `spec.md` §15 y en `ac.md` §6.1 | 17-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde, **con `CA-AC-017` marcada como trivial hasta `RF-AC-016`**.
- [ ] `mvn verify` en verde en local.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
