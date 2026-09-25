# TASKS — `RF-AC-038` Quitar la visibilidad de un curso a un servicio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-038` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 25-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 25-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/ajustes-academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `domain/service/RevokeCourseProductService`: curso vivo con `FOR UPDATE`, fila por clave, instantánea con el código, `DELETE`, `ASSOCIATION` sin motivo, relectura | `RF-AC-037` · `T-06` | `CA-AC-223`, `CA-AC-224`, `CA-AC-225` | Hecha |
| `T-02` | `interfaces/CourseController`: `DELETE /api/v1/courses/{courseId}/products/{productId}`, `@PreAuthorize("hasAuthority('courses:update')")`, `200` | `T-01` | La ruta entra en `EndpointPermissionsIT` | Hecha |
| `T-03` | Pruebas de API en `CourseProductVisibilityIT`, con la carrera | `T-02` | `CA-AC-223` a `CA-AC-226` | Hecha |
| `T-04` | Documentación OpenAPI. **La prosa dice** que borra la fila sin motivo, que devuelve el curso, que un servicio retirado se quita igual, y que quitar el último nunca se rechaza | `T-02` | El contrato declara `200`, `400`, `401`, `403`, `404` | Hecha |
| `T-05` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-03` | La fila de `RF-AC-038` refleja el estado | Hecha |

## 2. Orden de ejecución

Lineal, tras `RF-AC-037`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-223`, `CA-AC-224`, `CA-AC-225` | `T-01`, `T-03` |
| `CA-AC-226` | `T-01`, `T-03` |

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
