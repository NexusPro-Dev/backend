# TASKS — `RF-AC-040` Consultar el progreso de los alumnos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-040` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 09-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `LessonProgressRepository.search` y `count` con el predicado del alcance | `RF-AC-041` · `T-02` | Integración por `T-04` | Pendiente |
| `T-02` | `ListStudentProgressService`, petición, fila y página | `T-01` | — | Pendiente |
| `T-03` | `CourseProgressController`: `GET /api/v1/courses/progress` con `courses:list-progress` | `T-02` | La ruta entra en `PERMISO_DE_CADA_OPERACION` | Pendiente |
| `T-04` | `StudentProgressListIT` | `T-03` | `CA-AC-258` a `CA-AC-262` | Pendiente |
| `T-05` | OpenAPI, `docs/api/index.md` y la matriz | `T-04` | El contrato declara `200`, `400`, `401`, `403` | Pendiente |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-258`, `CA-AC-261` | `T-01`, `T-04` |
| `CA-AC-259`, `CA-AC-260` | `T-01`, `T-02`, `T-04` |
| `CA-AC-262` | `T-02`, `T-03`, `T-04` |

## 3. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 4. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
