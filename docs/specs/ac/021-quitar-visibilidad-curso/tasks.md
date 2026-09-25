# TASKS — `RF-AC-021` Quitar la visibilidad de un curso a una membresía

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-021` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 25-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/ajustes-academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `domain/service/RevokeCourseVisibilityService` | `RF-AC-020` · `T-04` | `CA-AC-141`, `CA-AC-142`, `CA-AC-143` | Hecha |
| `T-02` | `interfaces/CourseController`: `DELETE /api/v1/courses/{courseId}/memberships/{membershipId}`, `@PreAuthorize("hasAuthority('courses:update')")`, `200` | `T-01` | La ruta entra en `EndpointPermissionsIT` | Hecha |
| `T-03` | Pruebas de API en `CourseVisibilityIT` y la carrera en `CourseConcurrencyIT` | `T-02` | `CA-AC-141` a `CA-AC-144`; `CA-AC-143` sobre el detalle hoy y sobre el aula desde `RF-AC-033` | Hecha |
| `T-04` | Documentación OpenAPI. **La prosa dice** que quitar la última nunca se rechaza y esconde el curso sin desactivarlo | `T-02` | El contrato declara `200`, `400`, `401`, `403`, `404` | Hecha |
| `T-05` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-03` | La fila de `RF-AC-021` refleja el estado | Hecha |

## 2. Orden de ejecución

Lineal, tras `RF-AC-020`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-141` a `CA-AC-144` | `T-01`, `T-03` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | La mitad del aula de `CA-AC-143` espera a `RF-AC-033` | 18-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
