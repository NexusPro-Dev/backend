# TASKS — `RF-AC-031` Eliminar lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-031` |
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
| `T-01` | `application/DeleteLessonRequest`; `Lesson.delete`, `estaRetirada`, `instantaneaCompleta` | `RF-AC-028` · `T-02` | Unitaria: la completa lleva `content`; la normal, `content_length` | Pendiente |
| `T-02` | `LessonRepository.findByIdInModuleForUpdate` en cualquier estado | `RF-AC-028` · `T-03` | Integración: la retirada vuelve; la de otro módulo, vacío | Pendiente |
| `T-03` | `domain/service/DeleteLessonService` | `T-01`, `T-02` | `CA-AC-119`, `CA-AC-120`, `CA-AC-121`, `CA-AC-122` | Pendiente |
| `T-04` | `interfaces/LessonController`: `POST …/lessons/{lessonId}/deletion`, `@PreAuthorize("hasAuthority('courses:update')")`, `204` | `T-03` | La ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-05` | Pruebas de API (`LessonDeletionIT`) y el retiro doble en `LessonConcurrencyIT` | `T-04` | `CA-AC-119` a `CA-AC-123` | Pendiente |
| `T-06` | Documentación OpenAPI. **La prosa dice** que no arrastra ni toca el módulo, que la instantánea lleva el contenido entero, y que el título queda libre | `T-04` | El contrato declara `204`, `400`, `401`, `403`, `404`, `409` | Pendiente |
| `T-07` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-05` | La fila de `RF-AC-031` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01` y `T-02` independientes; `T-03` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-119`, `CA-AC-121`, `CA-AC-122` | `T-01`, `T-03`, `T-05` |
| `CA-AC-120` | `T-02`, `T-03`, `T-05` |
| `CA-AC-123` | `T-03`, `T-05` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
