# TASKS — `RF-AC-055` Repetir una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-055` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 13-10-2026 |
| Estado | **En revisión** — `T-01` a `T-08` `Hecha` el 13-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V102`: `recurrence`, `repeat_until`, `series_ends_at` con sus `CHECK` y el índice | — | La migración aplica | Hecha |
| `T-02` | `LiveSessionSeries` | — | `LiveSessionSeriesTest` | Hecha |
| `T-03` | `MeetingSpec.Repeat` y `ZoomApiClient`: tipo 8 con `recurrence` | — | `ZoomApiClientTest` | Hecha |
| `T-04` | Repositorios: las tres columnas y `series_ends_at` en los filtros | `T-01` | Integración por `T-07` | Hecha |
| `T-05` | Programar y corregir: leer, comprobar, Zoom, guardar, auditar | `T-02` a `T-04` | — | Hecha |
| `T-06` | Lecturas, vitrina, entrar e iniciar: la fecha siguiente y la última | `T-02`, `T-04` | — | Hecha |
| `T-07` | `LiveSessionAdminIT` y `LiveClassroomIT`: `CA-AC-306` a `CA-AC-311` | `T-05`, `T-06` | Suite completa en verde | Hecha |
| `T-08` | La prosa de las `@Operation`, el contrato y `docs/api/index.md` | `T-07` | El contrato declara los campos nuevos | Hecha |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-306`, `CA-AC-307` | `T-02`, `T-03`, `T-05`, `T-07` |
| `CA-AC-308` | `T-03`, `T-05`, `T-07` |
| `CA-AC-309` a `CA-AC-311` | `T-04`, `T-06`, `T-07` |

## 3. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Probar contra Zoom de verdad una serie corregida ya empezada (riesgo 1 del plan). **No bloquea la construcción** | 13-10-2026 | Responsable del proyecto | Abierto |

## 4. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.

## Desviaciones respecto del plan

- **Una suite propia**, `LiveSessionRecurrenceIT`, en lugar de repartir los criterios en `LiveSessionAdminIT` y `LiveClassroomIT`: las series empezadas se escriben directo en la tabla (`LiveSessionTestSupport.serieDiaria`) y no se mezclan con las clases sueltas de las otras.
- **Cambiar solo la frecuencia conserva `repeatUntil`**: de semanal a diaria, la serie va hasta la misma fecha.
