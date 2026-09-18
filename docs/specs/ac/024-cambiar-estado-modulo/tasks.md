# TASKS — `RF-AC-024` Cambiar el estado de un módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-024` |
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
| `T-01` | `application/ChangeCourseModuleStatusRequest`; `CourseModule.activate`/`deactivate` | `RF-AC-022` · `T-02` | Unitaria: sin cambio devuelve falso | Pendiente |
| `T-02` | `domain/service/ChangeCourseModuleStatusService`: módulo vivo del curso con `FOR UPDATE`, mismo estado sin escribir, `409` al activar sin lección activa, auditoría, relectura | `T-01`, `RF-AC-028` · `T-05` | `CA-AC-105`, `CA-AC-106`, `CA-AC-107` | Pendiente |
| `T-03` | `interfaces/CourseModuleController`: `PATCH …/modules/{moduleId}/status`, `@PreAuthorize("hasAuthority('courses:update')")` | `T-02` | `CA-AC-108`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-04` | Pruebas de API (`CourseModuleStatusIT`) de `CA-AC-105` a `CA-AC-108`, con la lectura del detalle del curso tras desactivar el último módulo | `T-03` | Los cuatro en verde | Pendiente |
| `T-05` | **Habilitar `CA-AC-064` en `CourseStatusIT`** (`RF-AC-012`), con un módulo activado por esta operación | `T-03` | `CA-AC-109`; la fila de `RF-AC-012` deja de estar bloqueada | Pendiente |
| `T-06` | Documentación OpenAPI. **La prosa dice** que activar exige una lección activa y nada más, que desactivar el último no toca el curso, y que el curso puede activarse desde que tiene un módulo activo | `T-03` | El contrato declara `200`, `400`, `401`, `403`, `404`, `409` | Pendiente |
| `T-07` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md` y la spec de `RF-AC-012` (`CA-AC-064` habilitado) | `T-05` | Las dos filas reflejan el estado | Pendiente |

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04`/`T-05`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-105`, `CA-AC-106`, `CA-AC-107` | `T-01`, `T-02`, `T-04` |
| `CA-AC-108` | `T-03`, `T-04` |
| `CA-AC-109` | `T-05` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno: `RF-AC-028` va antes en el orden de construcción | | | |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`, **`T-05` incluida**.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad, `docs/api/index.md` y la spec de `RF-AC-012` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
