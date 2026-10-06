# TASKS — `RF-MV-057` Adjuntar el comprobante de un ajuste de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-057` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-05` `Hecha` |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PointsReceipt` y `PointsReceiptTest` | — | Unitaria | **Hecha** — 06-10-2026 |
| `T-02` | `PointsReceiptRepository` y su implementación | `RF-MV-055` `T-01` | | **Hecha** — 06-10-2026 |
| `T-03` | `PointsAdjustmentService.adjust` con comprobante y repetición por resumen; `receipt` en la respuesta | `T-01`, `T-02` | | **Hecha** — 06-10-2026 |
| `T-04` | `AttachPointsReceiptService`, y las dos rutas multipart en `PointsController` | `T-03` | Contrato | **Hecha** — 06-10-2026 |
| `T-05` | `PointsReceiptIT`: `CA-MV-685` a `CA-MV-695` | `T-04` | | **Hecha** — 06-10-2026 |

---

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
