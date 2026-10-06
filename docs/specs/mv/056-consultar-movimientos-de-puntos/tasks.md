# TASKS — `RF-MV-056` Consultar los movimientos de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-056` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-04` `Hecha` |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | El alcance de administración en `PointsMovementQuery`: persona por parámetro, quién lo hizo, búsqueda por nombre, usuario y correo | `RF-MV-055` `T-02` | | **Hecha** — 06-10-2026 |
| `T-02` | Las tres rutas de administración en `PointsMovementsController` | `T-01` | Contrato | **Hecha** — 06-10-2026 |
| `T-03` | Retirar `GET /points-adjustments`, `PointsAdjustmentService.list` y lo que solo él usaba | `T-02` | | **Hecha** — 06-10-2026 |
| `T-04` | `PointsMovementsIT`: `CA-MV-674` a `CA-MV-684`; retirar `PointsAdjustmentListIT` | `T-02` | | **Hecha** — 06-10-2026 |

---

## 1.1 Desviaciones respecto del plan

| # | Plan | Construido | Por qué |
|---|---|---|---|
| 1 | Retirar `PointsAdjustmentListIT` | **Renombrado** a `PointsMovementsIT`: guardaba también las pruebas de `RF-MV-054`, que siguen ahí | — |
| 2 | `userId` en los parámetros comunes | **Parámetro aparte** de la ruta de administración | Para que el contrato de la lista propia no lo ofrezca |

## 2. Definición de terminado

- [x] `./mvnw verify` en verde.
- [x] Los once criterios con prueba.
- [x] Contrato OpenAPI regenerado, con la prosa releída.
- [x] **`tasks.md` aprobadas por el responsable del proyecto** (06-10-2026).

---

## 3. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| — | 06-10-2026 | Primera versión. | Responsable técnico |
| — | 06-10-2026 | Aprobadas y construidas el mismo día; suite completa en verde (567 unitarias, 2574 de integración). | Responsable técnico |
