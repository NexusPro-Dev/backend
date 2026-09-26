# TASKS — `RF-AC-034` Consultar el detalle de un curso como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-034` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 y reescrito el 26-09-2026 |
| Estado | **En desarrollo** |
| Issue | Pendiente de crear |
| Rama | `feature/aula-academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V47__ac_permisos_del_aula.sql`: `courses:read-available` y `lessons:learn`, en todo rol que porte `courses:learn`, con guarda de conteo | — | Las suites de catálogo de permisos cuentan 136 | Hecha |
| `T-02` | `application/ClassroomCourseResponse` con `RecommendedCourseItem`, `ClassroomModuleItem`, `ClassroomLessonItem` | — | El contrato declara la forma; sin estados ni `offerable` | Hecha |
| `T-03` | `GetClassroomCourseService`: `404` por `CourseOfferability`, árbol filtrado por `ModuleOfferability` y `LessonOfferability`, sumas sobre lo ofrecido, `StudentKeys`, `StudentAccess` | `RF-AC-033` · `T-02`, `T-06` | `CA-AC-195` a `CA-AC-199` | Hecha |
| `T-04` | `ClassroomController`: `GET /api/v1/courses/available/{id}` con `courses:read-available` | `T-03` | `CA-AC-201`; la ruta entra en `PERMISO_DE_CADA_OPERACION` | Hecha |
| `T-05` | Pruebas de API (`ClassroomCourseDetailIT`), con el contador de sentencias | `T-04` | `CA-AC-195` a `CA-AC-201` | Hecha |
| `T-06` | Documentación OpenAPI: solo lo ofrecido, sin estados ni contenido, las dos listas de llaves, `accessible` y `openLessonCount`, `404` igual para todo | `T-04` | El contrato declara `200`, `400`, `401`, `403`, `404` | Hecha |
| `T-07` | Matriz de `docs/requirements.md` y `docs/api/index.md` | `T-05` | La fila de `RF-AC-034` refleja el estado | Hecha |

## 2. Orden de ejecución

`T-01` y `T-02` sueltas; `T-03` sobre el catálogo de `RF-AC-033`; `T-04` y `T-05` detrás.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-195`, `CA-AC-197`, `CA-AC-198` | `T-02`, `T-03`, `T-05` |
| `CA-AC-196` | `T-03`, `T-05` |
| `CA-AC-199` | `T-03` (vacía hasta `RF-AC-018`) |
| `CA-AC-200` | `T-03`, `T-05` |
| `CA-AC-201` | `T-01`, `T-04`, `T-05` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `CA-AC-199` solo puede probarse con recomendaciones: la relación nace en `RF-AC-018` | 26-09-2026 | `RF-AC-018` | Abierto |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde, salvo el bloqueo 1.
- [x] `mvn verify` en verde en local.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
