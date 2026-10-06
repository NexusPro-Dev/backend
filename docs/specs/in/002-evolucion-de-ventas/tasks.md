# TASKS — `RF-IN-002` Consultar la evolución de las ventas

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-002` |
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
| `T-01` | `SalesFigures.confirmedByBucket` y `Granularity`; `JpaSalesFigures` con `date_trunc` sobre la hora de la zona recibida | `RF-IN-001` · `T-03` | `SalesFiguresIT`: tramos de día, semana de lunes y mes; la venta de las 20:00 de Bogotá en su día | **Hecha** — 06-10-2026 |
| `T-02` | `SalesCalendar`: los inicios de tramo del periodo | — | `SalesCalendarTest`: domingo, 31-01 a 01-03, día único, bisiesto | **Hecha** — 06-10-2026 |
| `T-03` | `SalesSeriesResponse` con `@Schema` propios; `GetSalesSeriesService`: `VAL-005` junto a los demás, corte a ceros con calendario completo, cruce y relleno de monedas | `T-01`, `T-02` | Monedas en el orden de `currencies` en todos los tramos | **Hecha** — 06-10-2026 |
| `T-04` | `SalesIndicatorsController`: `GET /api/v1/indicators/sales/series`, documentado | `T-03` | La ruta en `PERMISO_DE_CADA_OPERACION` | **Hecha** — 06-10-2026 |
| `T-05` | `SalesSeriesIT`: `CA-IN-015` a `CA-IN-022`, con la suma cruzada contra el resumen | `T-04` | Coste: una sentencia con 7 y con 90 tramos | **Hecha** — 06-10-2026 |
| `T-06` | Contrato regenerado y prosa releída; `docs/api/index.md`; ficha y matriz | `T-05` | `openapi.json` con la ruta y su permiso | **Hecha** — 06-10-2026 |

### 1.1 Sin fechas, desde la primera venta — 06-10-2026 (`RN-IN-010`)

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-07` | `GetSalesSeriesService` sobre el periodo abierto; `SalesSeriesIT`: `CA-IN-055` y `CA-IN-056`, y `CA-IN-020`/`CA-IN-022` reescritos | `RF-IN-001` · `T-15` | | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-015` | `T-01`, `T-05` |
| `CA-IN-016`, `CA-IN-017` | `T-02`, `T-03`, `T-05` |
| `CA-IN-018`, `CA-IN-019` | `T-01`, `T-02`, `T-05` |
| `CA-IN-020`, `CA-IN-021` | `T-03`, `T-05` |
| `CA-IN-022` | `T-03`, `T-04`, `T-05` |

---

## 3. Desviaciones respecto del plan

**La agrupación por tramo se prueba por HTTP**, como la suma de `RF-IN-001`: `SalesSeriesIT` cruza la semana de PostgreSQL con la de `SalesCalendar` (`CA-IN-018`) y la suma de los tramos con el resumen en los tres tamaños (`CA-IN-015`).

**`JpaSalesFigures` reúne el predicado en un solo método** (`donde`) que comparten el resumen y la serie: la serie tiene que sumar exactamente lo confirmado del resumen, y dos copias del filtro divergirían sin fallar.

**`granularity` se recibe como texto y se valida en el servicio**, sin distinguir mayúsculas, para devolver su `VAL-005` junto a los del periodo; un enumerado en el controlador lo rechazaría antes, solo y como `VAL-001`.

**Se construyó en un worktree aparte** mientras la sesión del segundo factor ocupaba el árbol, y se integró sobre su `RF-SP-071` (`cadd275f`).

---

## 4. Bloqueos declarados

1. **Se construye después de `RF-IN-001`**, que siembra el permiso y publica `SalesFigures`.

---

## 5. Definición de terminado

- [x] Las suites del módulo en verde: `SalesSeriesIT` (8), `SalesSummaryIT` (12), `SalesCalendarTest` (4), `LayerRulesTest`, `EndpointPermissionsIT` y `OpenApiContractIT`. **La suite completa no se corrió**: el cambio no toca permisos, esquema ni código compartido fuera de `SalesFigures`.
- [x] Los ocho criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**: solo altas.
- [x] `requirements/in.md`, `requirements.md` y `api/index.md` actualizados.
- [x] **`tasks.md` aprobadas por el responsable del proyecto** (06-10-2026).
