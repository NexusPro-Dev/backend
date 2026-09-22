# TASKS — `RF-AC-007` Quitar la portada de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-007` |
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
| `T-01` | `CourseCategory.quitarPortada(ahora)` | `RF-AC-006` · `T-03` | Unitaria: sin anterior devuelve `ninguno()` y no mueve `updatedAt`; con anterior, el diff | Pendiente |
| `T-02` | `domain/service/RemoveCourseCategoryCoverService` | `T-01` | `CA-AC-168`, `CA-AC-169`, `CA-AC-170` | Pendiente |
| `T-03` | `interfaces/CourseCategoryController`: `DELETE /api/v1/course-categories/{id}/cover`, `@PreAuthorize("hasAuthority('course-categories:update')")`, `200` | `T-02` | `CA-AC-171`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-04` | Pruebas de API en `CourseCategoryCoverIT` | `T-03` | `CA-AC-168` a `CA-AC-171` | Pendiente |
| `T-05` | Documentación OpenAPI. **La prosa dice** que nunca se rechaza, que borra la imagen, y que sin portada no escribe | `T-03` | El contrato declara `200`, `400`, `401`, `403`, `404` | Pendiente |
| `T-06` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md` y **`ac.md` (la ficha de `RF-AC-007` decía `204`)** | `T-04` | Las filas reflejan el estado | Pendiente |

## 2. Orden de ejecución

Lineal, tras `RF-AC-006`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-168` a `CA-AC-170` | `T-01`, `T-02`, `T-04` |
| `CA-AC-171` | `T-03`, `T-04` |

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
- [ ] Matriz de trazabilidad, `docs/api/index.md` y `ac.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
