# TASKS — `RF-MV-057` Adjuntar el comprobante de un ajuste de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-057` |
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
| `T-01` | `PointsReceipt` y `PointsReceiptTest` | — | Unitaria | Pendiente |
| `T-02` | `PointsReceiptRepository` y su implementación | `RF-MV-055` `T-01` | | Pendiente |
| `T-03` | `PointsAdjustmentService.adjust` con comprobante y repetición por resumen; `receipt` en la respuesta | `T-01`, `T-02` | | Pendiente |
| `T-04` | `AttachPointsReceiptService`, y las dos rutas multipart en `PointsController` | `T-03` | Contrato | Pendiente |
| `T-05` | `PointsReceiptIT`: `CA-MV-685` a `CA-MV-695` | `T-04` | | Pendiente |

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
