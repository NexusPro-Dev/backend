# TASKS — `RF-MV-056` Consultar los movimientos de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-056` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | El alcance de administración en `PointsMovementQuery`: persona por parámetro, quién lo hizo, búsqueda por nombre, usuario y correo | `RF-MV-055` `T-02` | | Pendiente |
| `T-02` | Las tres rutas de administración en `PointsMovementsController` | `T-01` | Contrato | Pendiente |
| `T-03` | Retirar `GET /points-adjustments`, `PointsAdjustmentService.list` y lo que solo él usaba | `T-02` | | Pendiente |
| `T-04` | `PointsMovementsIT`: `CA-MV-674` a `CA-MV-684`; retirar `PointsAdjustmentListIT` | `T-02` | | Pendiente |

---

## 2. Definición de terminado

- [ ] `./mvnw verify` en verde.
- [ ] Los once criterios con prueba.
- [ ] Contrato OpenAPI regenerado, con la prosa releída.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 3. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| — | 06-10-2026 | Primera versión. | Responsable técnico |
