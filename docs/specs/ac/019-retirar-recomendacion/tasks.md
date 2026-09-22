# TASKS — `RF-AC-019` Retirar una recomendación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-019` |
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
| `T-01` | `domain/service/WithdrawRecommendationService` | `RF-AC-018` · `T-04` | `CA-AC-151`, `CA-AC-152`, `CA-AC-153` | Pendiente |
| `T-02` | `interfaces/CourseController`: `DELETE /api/v1/courses/{courseId}/recommendations/{recommendedCourseId}`, `@PreAuthorize("hasAuthority('courses:update')")`, `200` | `T-01` | La ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-03` | Pruebas de API en `CourseRecommendationIT` y la carrera en `CourseConcurrencyIT` | `T-02` | `CA-AC-151` a `CA-AC-154` | Pendiente |
| `T-04` | Documentación OpenAPI. **La prosa dice** que borra la fila sin motivo, que el recomendado retirado se retira igual, y que la inversa permanece | `T-02` | El contrato declara `200`, `400`, `401`, `403`, `404` | Pendiente |
| `T-05` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-03` | La fila de `RF-AC-019` refleja el estado | Pendiente |

## 2. Orden de ejecución

Lineal, tras `RF-AC-018`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-151` a `CA-AC-154` | `T-01`, `T-03` |

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
