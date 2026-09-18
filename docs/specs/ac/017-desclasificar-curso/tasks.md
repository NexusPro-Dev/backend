# TASKS — `RF-AC-017` Desclasificar un curso de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-017` |
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
| `T-01` | `domain/service/DeclassifyCourseService`: curso vivo con `FOR UPDATE`, fila por clave, `DELETE`, `ASSOCIATION` sin motivo, relectura | `RF-AC-016` · `T-05` | `CA-AC-131`, `CA-AC-132`, `CA-AC-133` | Pendiente |
| `T-02` | `interfaces/CourseController`: `DELETE /api/v1/courses/{courseId}/categories/{categoryId}`, `@PreAuthorize("hasAuthority('courses:update')")`, `200` | `T-01` | La ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-03` | Pruebas de API en `CourseClassificationIT` y la carrera en `CourseConcurrencyIT` | `T-02` | `CA-AC-131` a `CA-AC-134` | Pendiente |
| `T-04` | Documentación OpenAPI. **La prosa dice** que borra la fila sin motivo, que devuelve el curso, y que una categoría retirada se desclasifica igual | `T-02` | El contrato declara `200`, `400`, `401`, `403`, `404` | Pendiente |
| `T-05` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-03` | La fila de `RF-AC-017` refleja el estado | Pendiente |

## 2. Orden de ejecución

Lineal, tras `RF-AC-016`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-131`, `CA-AC-132`, `CA-AC-133` | `T-01`, `T-03` |
| `CA-AC-134` | `T-01`, `T-03` |

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
