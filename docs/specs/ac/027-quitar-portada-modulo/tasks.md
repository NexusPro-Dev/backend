# TASKS — `RF-AC-027` Quitar la portada de un módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-027` |
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
| `T-01` | `CourseModule.quitarPortada` | `RF-AC-026` · `T-01` | Unitaria | Pendiente |
| `T-02` | `domain/service/RemoveCourseModuleCoverService` sobre `CoverRemover` | `T-01`, `RF-AC-015` · `T-01` | `CA-AC-183`, `CA-AC-184` | Pendiente |
| `T-03` | `interfaces/CourseModuleController`: `DELETE …/modules/{moduleId}/cover`, `@PreAuthorize("hasAuthority('courses:update')")`, `200` | `T-02` | `CA-AC-185`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-04` | Pruebas de API en `CourseModuleCoverIT` | `T-03` | `CA-AC-183` a `CA-AC-185` | Pendiente |
| `T-05` | Documentación OpenAPI, con la prosa de `RF-AC-007` sobre el módulo | `T-03` | El contrato declara `200`, `400`, `401`, `403`, `404` | Pendiente |
| `T-06` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-04` | La fila de `RF-AC-027` refleja el estado | Pendiente |

## 2. Orden de ejecución

Lineal, tras `RF-AC-026` y `RF-AC-015`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-183`, `CA-AC-184` | `T-01`, `T-02`, `T-04` |
| `CA-AC-185` | `T-03`, `T-04` |

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
