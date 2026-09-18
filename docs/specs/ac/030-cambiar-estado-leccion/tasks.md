# TASKS — `RF-AC-030` Cambiar el estado de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-030` |
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
| `T-01` | `application/ChangeLessonStatusRequest`; `Lesson.activate` con el `409` sin contenido, `deactivate` | `RF-AC-028` · `T-02` | Unitaria: activar sin contenido lanza; sin cambio devuelve falso | Pendiente |
| `T-02` | `domain/service/ChangeLessonStatusService` | `T-01` | `CA-AC-110`, `CA-AC-111`, `CA-AC-112` | Pendiente |
| `T-03` | `interfaces/LessonController`: `PATCH …/lessons/{lessonId}/status`, `@PreAuthorize("hasAuthority('courses:update')")` | `T-02` | `CA-AC-113`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-04` | Pruebas de API (`LessonStatusIT`) de los cuatro criterios, con la lectura del módulo y del curso tras desactivar la última | `T-03` | `CA-AC-110` a `CA-AC-113` | Pendiente |
| `T-05` | Documentación OpenAPI. **La prosa dice** que activar exige contenido, que desactivar la última no toca el módulo, y que la duración solo suma activas | `T-03` | El contrato declara `200`, `400`, `401`, `403`, `404`, `409` | Pendiente |
| `T-06` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-04` | La fila de `RF-AC-030` refleja el estado | Pendiente |

## 2. Orden de ejecución

Lineal.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-110`, `CA-AC-111`, `CA-AC-112` | `T-01`, `T-02`, `T-04` |
| `CA-AC-113` | `T-03`, `T-04` |

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
