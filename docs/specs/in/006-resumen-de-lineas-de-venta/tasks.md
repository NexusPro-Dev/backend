# TASKS — `RF-IN-006` Consultar el resumen de líneas de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-006` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| Plan | [`plan.md`](plan.md) v0.2.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-06` `Hecha` el mismo día |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V78`**: `indicators:read-sale-lines-summary` (`…-5e7ad8000006`) a `SUPERADMIN` y `ADMIN`, explícitos; guardas 203 / 203 / 201 / ningún otro rol / contención | — | Recuentos del catálogo a 203 y 201 | **Hecha** — 06-10-2026 |
| `T-02` | `SalesFigures.unassigned` y `unassignedByBucket`; `JpaSalesFigures` con la sentencia de `plan.md` §4.3 | — | Sin alcance en la firma | **Hecha** — 06-10-2026 |
| `T-03` | `SaleLinesSummaryResponse`; `GetSaleLinesSummaryService` sin alcance, con periodo y tramos | `T-02` | Los nulos presentes | **Hecha** — 06-10-2026 |
| `T-04` | `SalesIndicatorsController`: `GET /api/v1/indicators/sales/lines/summary`, documentado —sin alcance, lo ve entero quien porte el permiso— | `T-03` | La ruta en `PERMISO_DE_CADA_OPERACION` | **Hecha** — 06-10-2026 |
| `T-05` | `SaleLinesSummaryIT`: `CA-IN-059` a `CA-IN-066` | `T-01`, `T-04` | Cuadra con el resumen de ventas | **Hecha** — 06-10-2026 |
| `T-06` | Contrato regenerado; `api/index.md`, `requirements/mv.md` §3, `security.md` (sembrado), `requirements/in.md` y matriz | `T-05` | Solo altas | **Hecha** — 06-10-2026 |

### 1.1 Por tipo de producto — 07-10-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-07` | `SalesFigures.byProductType` y `byProductTypeAndBucket`, con `Lines`; `JpaSalesFigures` con el cruce a `products` | — | El mismo mapeo que el resumen | **Hecha** — 07-10-2026 |
| `T-08` | `SaleLinesSummaryResponse` con `sold` y `unassigned` (`total` y `byType`); `GetSaleLinesSummaryService`; la descripción de la ruta | `T-07` | Sin pendientes ni anuladas | **Hecha** — 07-10-2026 |
| `T-09` | `SaleLinesSummaryIT`: `CA-IN-067` a `CA-IN-070` y los casos de antes reescritos; contrato y documentos | `T-08` | | **Hecha** — 07-10-2026 |

### 1.2 Filtros por vendedor, cliente, producto y comprobante — 07-10-2026

Enmienda de hecho (Art. I.7), `spec.md` 0.3.0 y `plan.md` 0.3.0 **antes** del código.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-10` | `SalesFigures.LineFilter`; las cuatro lecturas de lo sin vendedor y por tipo lo reciben; `summary` y `summaryByBucket` con sobrecarga; `JpaSalesFigures` lo aplica en `donde` y `enlazar` | — | `RF-IN-001`, `RF-IN-002` y `RF-IN-004` sin cambios | **Hecha** — 07-10-2026 |
| `T-11` | `GetSaleLinesSummaryService` y `SalesIndicatorsController`: los cuatro parámetros, documentados | `T-10` | Vacío es sin filtro | **Hecha** — 07-10-2026 |
| `T-12` | `SaleLinesSummaryIT`: `CA-IN-080` a `CA-IN-085`; contrato regenerado; `api/index.md`, `requirements/in.md` y matriz | `T-11` | Solo altas | **Hecha** — 07-10-2026 |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-059` a `CA-IN-061` | `T-03`, `T-05` |
| `CA-IN-062`, `CA-IN-063` | `T-02`, `T-05` |
| `CA-IN-064`, `CA-IN-065` | `T-03`, `T-05` |
| `CA-IN-066` | `T-01`, `T-04`, `T-05` |
| `CA-IN-067` a `CA-IN-070` | `T-07` a `T-09` — 07-10-2026 |
| `CA-IN-080` a `CA-IN-085` | `T-10` a `T-12` — 07-10-2026 |

---

## 3. Desviaciones respecto del plan

**Lo sin vendedor reutiliza el mapeo del resumen** (`T-02`): la sentencia lleva un estado fijo delante para pasar por el mismo `sumar` que el resumen, de modo que ventas, líneas, unidades e importes se cuentan exactamente igual. El servicio recibe el periodo, la moneda y el tramo sueltos, sin `SalesIndicatorRequest`, porque no hay vendedor por el que acotar.

---

**La enmienda por tipo (07-10-2026) no corrió la suite completa**: no cambia permisos, esquema ni código compartido fuera de `SalesFigures`; se verificaron `SaleLinesSummaryIT` (6), `SalesSummaryIT`, `SalesSeriesIT`, `LayerRulesTest` y `OpenApiContractIT`.

## 4. Bloqueos declarados

Ninguno.

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde — 567 unitarias y 2581 de integración (06-10-2026).
- [x] Los ocho criterios de aceptación con prueba (`SaleLinesSummaryIT`, 5).
- [x] Contrato OpenAPI regenerado, **con la prosa releída**: solo altas.
- [x] `requirements/in.md`, `requirements/mv.md`, `security.md`, `requirements.md` y `api/index.md` actualizados.
- [x] **`tasks.md` aprobadas por el responsable del proyecto** (06-10-2026).
