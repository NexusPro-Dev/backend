# TASKS — `RF-MV-058` Rellenar la oficina de las líneas de venta que no la tienen

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-058` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 09-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `MovementRepository` y `JpaMovementRepository`: `lockSalesWithLinesWithoutTeam`, `findSellersOfLinesWithoutTeam` y `fillLineTeam` con `RETURNING` (`plan.md` §1) | `RF-MV-001` · `T-49`, `T-50` | | **Hecha** — 10-10-2026 |
| `T-02` | `FillLineTeamsService`: bloqueo, vendedores, `currentTeamsOf`, escritura por equipo y un `ChangeEvent` por venta tocada | `T-01` | Todo o nada | **Hecha** — 10-10-2026 |
| `T-03` | `LineTeamFillResponse`; `POST /api/v1/movements/sales/lines/team-fill` en `MovementController` con `movements:fill-line-teams`, documentado; entra en `PERMISO_DE_CADA_OPERACION` | `T-02` | `EndpointPermissionsIT` en verde | **Hecha** — 10-10-2026 |
| `T-04` | `FillLineTeamsIT`: `CA-MV-729` a `CA-MV-739` | `T-03` | | **Hecha** — 10-10-2026 |
| `T-05` | `CA-MV-740` en la prueba de la siembra de permisos; contrato regenerado con la prosa releída; `api/index.md`, `requirements/mv.md` y la matriz | `T-04` | Diff del `json` | **Hecha** — 10-10-2026 |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-MV-729` a `CA-MV-738` | `T-01`, `T-02`, `T-04` |
| `CA-MV-739` | `T-03`, `T-04` |
| `CA-MV-740` | `RF-MV-001` · `T-50`, `T-05` |

---

## 3. Definición de terminado

- [ ] `./mvnw verify` en verde.
- [ ] Los doce criterios con prueba.
- [ ] Contrato OpenAPI regenerado, con la prosa releída.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 4. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| — | 09-10-2026 | Primera versión. | Responsable técnico |
