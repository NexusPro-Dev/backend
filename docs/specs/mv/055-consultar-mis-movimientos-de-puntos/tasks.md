# TASKS — `RF-MV-055` Consultar mis movimientos de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-055` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-08` `Hecha` |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V77`: tabla del comprobante, los dos índices, renombrar los dos permisos y sembrar los cinco nuevos, guardas | — | `mvn verify` aplica la migración | **Hecha** — 06-10-2026 |
| `T-02` | `PointsMovementQuery` y `JpaPointsMovementQuery`: lista, conteo acotado, uno, datos y archivo del comprobante | `T-01` | | **Hecha** — 06-10-2026 |
| `T-03` | `PointsMovementReader`: validación conjunta, alcance, fila, detalle y descarga | `T-02` | | **Hecha** — 06-10-2026 |
| `T-04` | `PointsMovementsController`: las tres rutas propias, documentadas | `T-03` | Contrato | **Hecha** — 06-10-2026 |
| `T-05` | Retirar `GET /mine/points-purchases`, `listMine` y su consulta | `T-04` | | **Hecha** — 06-10-2026 |
| `T-06` | `OwnPointsMovementsIT`: `CA-MV-662` a `CA-MV-673` | `T-04`, `RF-MV-057` `T-04` | | **Hecha** — 06-10-2026 |
| `T-08` | **Los gastos** (`spec.md` v0.2.0): la rama de `movement_entries`, el tipo `GASTO_PUNTOS` en la validación, las líneas en el detalle; `CA-MV-696` a `CA-MV-698` | `T-07` | Suite en verde | **Hecha** — 06-10-2026, en `PayWithPointsIT` |
| `T-07` | Recuentos del catálogo (202), `EndpointPermissionsIT`, listas de alcance propio de `IntegrationTestBase`, contrato | `T-06` | Suite en verde | **Hecha** — 06-10-2026 |

---

## 1.1 Desviaciones respecto del plan

| # | Plan | Construido | Por qué |
|---|---|---|---|
| 1 | `points_adjustment_receipts.movement_id` sin `ON DELETE` | **`ON DELETE CASCADE`** | En producción un movimiento no se borra (`RN-MV-001`); las suites sí, y una FK sin `ON DELETE` las rompería lejos de aquí. `modelo-datos.md` lo dice |
| 2 | `OwnPointsPurchasesIT` | Los criterios de `RF-MV-031` estaban en `PointsPurchaseIT`: se retiraron de ahí, y la tasa congelada se comprueba ahora en el detalle | — |
| 3 | La ruta retirada «ya no existe» | `GET /mine/points-purchases` cae en `GET /mine/{id}` (`RF-MV-008`) y responde `400`; `GET /points-adjustments`, `405` | Lo que importa a `CA-MV-673` y `CA-MV-684` es que ya no listan |

## 2. Definición de terminado

- [x] `./mvnw verify` en verde.
- [x] Los doce criterios con prueba.
- [x] Contrato OpenAPI regenerado, con la prosa releída.
- [x] **`tasks.md` aprobadas por el responsable del proyecto** (06-10-2026).

---

## 3. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| — | 06-10-2026 | Primera versión. | Responsable técnico |
| — | 06-10-2026 | Aprobadas y construidas el mismo día; suite completa en verde (567 unitarias, 2574 de integración). | Responsable técnico |
| — | 06-10-2026 | `T-08`, los gastos, aprobada por el responsable del proyecto («agrégalo»). | Responsable técnico |
| — | 06-10-2026 | `T-08` `Hecha`: los gastos, `CA-MV-696` a `CA-MV-698` en `PayWithPointsIT` (ya monta una venta pagada con puntos). Suite completa en verde (567 unitarias, 2576 de integración). | Responsable técnico |
