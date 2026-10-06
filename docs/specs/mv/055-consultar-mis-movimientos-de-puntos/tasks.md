# TASKS — `RF-MV-055` Consultar mis movimientos de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-055` |
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
| `T-01` | `V77`: tabla del comprobante, los dos índices, renombrar los dos permisos y sembrar los cinco nuevos, guardas | — | `mvn verify` aplica la migración | Pendiente |
| `T-02` | `PointsMovementQuery` y `JpaPointsMovementQuery`: lista, conteo acotado, uno, datos y archivo del comprobante | `T-01` | | Pendiente |
| `T-03` | `PointsMovementReader`: validación conjunta, alcance, fila, detalle y descarga | `T-02` | | Pendiente |
| `T-04` | `PointsMovementsController`: las tres rutas propias, documentadas | `T-03` | Contrato | Pendiente |
| `T-05` | Retirar `GET /mine/points-purchases`, `listMine` y su consulta | `T-04` | | Pendiente |
| `T-06` | `OwnPointsMovementsIT`: `CA-MV-662` a `CA-MV-673` | `T-04`, `RF-MV-057` `T-04` | | Pendiente |
| `T-07` | Recuentos del catálogo (202), `EndpointPermissionsIT`, listas de alcance propio de `IntegrationTestBase`, contrato | `T-06` | Suite en verde | Pendiente |

---

## 2. Definición de terminado

- [ ] `./mvnw verify` en verde.
- [ ] Los doce criterios con prueba.
- [ ] Contrato OpenAPI regenerado, con la prosa releída.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 3. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| — | 06-10-2026 | Primera versión. | Responsable técnico |
