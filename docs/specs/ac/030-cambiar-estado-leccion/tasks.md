# TASKS — `RF-AC-030` Cambiar el estado de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-030` |
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
| `T-01` | `application/ChangeLessonStatusRequest`; `Lesson.activate` con el `409` sin contenido, `deactivate` | `RF-AC-028` · `T-02` | Unitaria: activar sin contenido lanza; sin cambio devuelve falso | **Hecha el 19-09-2026** |
| `T-02` | `domain/service/ChangeLessonStatusService` | `T-01` | `CA-AC-110`, `CA-AC-111`, `CA-AC-112` | **Hecha el 19-09-2026** |
| `T-03` | `interfaces/LessonController`: `PATCH …/lessons/{lessonId}/status`, `@PreAuthorize("hasAuthority('courses:update')")` | `T-02` | `CA-AC-113`; la ruta entra en `EndpointPermissionsIT` | **Hecha el 19-09-2026** |
| `T-04` | Pruebas de API (`LessonStatusIT`) de los cuatro criterios, con la lectura del módulo y del curso tras desactivar la última | `T-03` | `CA-AC-110` a `CA-AC-113` | **Hecha el 19-09-2026** |
| `T-05` | Documentación OpenAPI. **La prosa dice** que activar exige contenido, que desactivar la última no toca el módulo, y que la duración solo suma activas | `T-03` | El contrato declara `200`, `400`, `401`, `403`, `404`, `409` | **Hecha el 19-09-2026** |
| `T-06` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-04` | La fila de `RF-AC-030` refleja el estado | **Hecha el 19-09-2026** |

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

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
