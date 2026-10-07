# TASKS — `RF-IN-007` Consultar el resumen de lotes de comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-007` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 07-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 07-10-2026. `T-01` a `T-06` `Hecha` el mismo día |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V79`**: `indicators:read-commission-batches-summary` (`…-5e7ad8000007`) a `SUPERADMIN` y `ADMIN`, explícitos; guardas 204 / 204 / 202 / ningún otro rol / contención. Los recuentos del catálogo en las pruebas que los fijan | — | Recuentos a 204 y 202 | **Hecha** — 07-10-2026 |
| `T-02` | `CommissionBatchFigures` en `commissions/application`; `JpaCommissionBatchFigures` con la sentencia de `plan.md` §4.3 | — | Sin alcance ni intervalo en la firma | **Hecha** — 07-10-2026 |
| `T-03` | `CommissionBatchesSummaryResponse`; `GetCommissionBatchesSummaryService` —los tres estados siempre, el total por moneda, un estado desconocido al total y al log—; `GetCommissionBatchesSummaryServiceTest` | `T-02` | Los tres bloques presentes con ceros | **Hecha** — 07-10-2026 |
| `T-04` | `CommissionIndicatorsController`: `GET /api/v1/indicators/commissions/batches/summary`, documentado —foto de hoy, sin alcance, las fechas se ignoran— | `T-03` | La ruta en `PERMISO_DE_CADA_OPERACION` | **Hecha** — 07-10-2026 |
| `T-05` | `CommissionBatchesSummaryIT`: `CA-IN-072` a `CA-IN-079`, limpiando lo que siembra | `T-01`, `T-04` | Una sentencia por lectura | **Hecha** — 07-10-2026 |
| `T-06` | Contrato regenerado; `api/index.md`, `requirements/cm.md` §3, `security.md` (sembrado), `requirements/in.md` y matriz | `T-05` | Solo altas | **Hecha** — 07-10-2026 |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-072` a `CA-IN-075` | `T-02`, `T-03`, `T-05` |
| `CA-IN-076`, `CA-IN-077` | `T-02`, `T-05` |
| `CA-IN-078` | `T-03`, `T-05` |
| `CA-IN-079` | `T-01`, `T-04`, `T-05` |

---

## 3. Desviaciones respecto del plan

**`CA-IN-076` y `CA-IN-077` cambian los lotes por SQL y no por las rutas de `CM`** (`T-05`), al revés de lo que decía `plan.md` §11: el indicador solo lee el estado y el total de cada lote, y el cierre, el pago y la retirada —que son quienes los cambian— tienen sus propias suites. Pasar por ellas habría obligado a sembrar ventas confirmadas y devengos para probar una suma.

**El coste se prueba comparando** (`RNF-PERF-*`): las mismas sentencias con seis lotes que con dieciséis, como `SalesSeriesIT`, y no un número absoluto, que depende de lo que la seguridad lea en cada petición.

---

## 4. Bloqueos declarados

Ninguno.

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde — 569 unitarias y 2590 de integración (07-10-2026).
- [x] Los ocho criterios de aceptación con prueba (`CommissionBatchesSummaryIT`, 8).
- [x] Contrato OpenAPI regenerado, **con la prosa releída**: solo altas.
- [x] `requirements/in.md`, `requirements/cm.md`, `security.md`, `requirements.md` y `api/index.md` actualizados.
- [x] **`tasks.md` aprobadas por el responsable del proyecto** (07-10-2026).
