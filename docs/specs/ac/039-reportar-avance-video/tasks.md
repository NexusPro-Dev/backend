# TASKS — `RF-AC-039` Reportar el avance de un video

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-039` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 09-10-2026 |
| Estado | **En desarrollo** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V92`: `lesson_progress` y los tres permisos, con su comprobación | — | La migración aplica; catálogo 219 | Hecha |
| `T-02` | `domain/models/LessonProgress` | — | `LessonProgressTest` | Hecha |
| `T-03` | `LessonProgressRepository` y su implementación: `open` y `report` con `ON CONFLICT … RETURNING` | `T-01`, `T-02` | Integración por `T-06` | Hecha |
| `T-04` | `ClassroomLessonGate`, sacada de `GetClassroomLessonService` | — | `ClassroomLessonIT` sigue en verde | Hecha |
| `T-05` | `ReportLessonProgressService`, la petición, la respuesta y la ruta `PUT` | `T-03`, `T-04` | La ruta entra en `PERMISO_DE_CADA_OPERACION` | Hecha |
| `T-06` | `LessonProgressReportIT` | `T-05` | `CA-AC-243` a `CA-AC-249` | Hecha |
| `T-07` | Las suites que cuentan el catálogo pasan a `219L` | `T-01` | Suite completa en verde | Hecha |
| `T-08` | OpenAPI con la prosa, `docs/api/index.md` y la matriz | `T-06` | El contrato declara `200`, `400`, `401`, `403`, `404`, `422` | Hecha |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-243`, `CA-AC-244`, `CA-AC-245` | `T-02`, `T-03`, `T-06` |
| `CA-AC-246`, `CA-AC-248` | `T-05`, `T-06` |
| `CA-AC-247` | `T-04`, `T-06` |
| `CA-AC-249` | `T-03`, `T-06` |

## 3. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 4. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
