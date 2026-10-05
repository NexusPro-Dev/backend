# TASKS — `RF-MV-053` Consultar los ajustes de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-053` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 05-10-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 05-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V73`: `recorded_by`, índice, dos permisos | — | | **Hecha** — 05-10-2026 |
| `T-02` | `RF-MV-052` escribe `recorded_by` | `T-01` | `CA-MV-647` | **Hecha** — 05-10-2026 |
| `T-03` | Repositorio, servicio, orden y filtros | `T-01` | | **Hecha** — 05-10-2026 |
| `T-04` | `GET /points-adjustments` | `T-03` | Documentado | **Hecha** — 05-10-2026 |
| `T-05` | `PointsAdjustmentListIT`: `CA-MV-647` a `CA-MV-655` | `T-04` | | **Hecha** — 05-10-2026 |
| `T-06` | Recuentos del catálogo (185, `ADMIN` 183), `EndpointPermissionsIT`, contrato | `T-05` | Suite en verde | **Hecha** — 05-10-2026 |

---

## 2. Definición de terminado

- [x] `./mvnw verify` en verde.
- [x] Los nueve criterios con prueba.
- [x] Contrato OpenAPI regenerado, con la prosa releída.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
