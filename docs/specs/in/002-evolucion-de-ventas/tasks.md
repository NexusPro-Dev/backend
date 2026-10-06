# TASKS — `RF-IN-002` Consultar la evolución de las ventas

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-002` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `SalesFigures.confirmedByBucket` y `Granularity`; `JpaSalesFigures` con `date_trunc` sobre la hora de la zona recibida | `RF-IN-001` · `T-03` | `SalesFiguresIT`: tramos de día, semana de lunes y mes; la venta de las 20:00 de Bogotá en su día | Pendiente |
| `T-02` | `SalesCalendar`: los inicios de tramo del periodo | — | `SalesCalendarTest`: domingo, 31-01 a 01-03, día único, bisiesto | Pendiente |
| `T-03` | `SalesSeriesResponse` con `@Schema` propios; `GetSalesSeriesService`: `VAL-005` junto a los demás, corte a ceros con calendario completo, cruce y relleno de monedas | `T-01`, `T-02` | Monedas en el orden de `currencies` en todos los tramos | Pendiente |
| `T-04` | `SalesIndicatorsController`: `GET /api/v1/indicators/sales/series`, documentado | `T-03` | La ruta en `PERMISO_DE_CADA_OPERACION` | Pendiente |
| `T-05` | `SalesSeriesIT`: `CA-IN-015` a `CA-IN-022`, con la suma cruzada contra el resumen | `T-04` | Coste: una sentencia con 7 y con 90 tramos | Pendiente |
| `T-06` | Contrato regenerado y prosa releída; `docs/api/index.md`; ficha y matriz | `T-05` | `openapi.json` con la ruta y su permiso | Pendiente |

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

Ninguna todavía.

---

## 4. Bloqueos declarados

1. **Se construye después de `RF-IN-001`**, que siembra el permiso y publica `SalesFigures`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements/in.md`, `requirements.md` y `api/index.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
