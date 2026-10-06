# TASKS — `RF-IN-006` Consultar el resumen de líneas de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-006` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V78`**: `indicators:read-sale-lines-summary` (`…-5e7ad8000006`) a `SUPERADMIN` y `ADMIN`, explícitos; guardas 203 / 203 / 201 / ningún otro rol / contención | — | Recuentos del catálogo a 203 y 201 | Pendiente |
| `T-02` | `SalesFigures.unassigned` y `unassignedByBucket`; `JpaSalesFigures` con la sentencia de `plan.md` §4.3 | — | Sin alcance en la firma | Pendiente |
| `T-03` | `SaleLinesSummaryResponse`; `GetSaleLinesSummaryService` sin alcance, con periodo y tramos | `T-02` | Los nulos presentes | Pendiente |
| `T-04` | `SalesIndicatorsController`: `GET /api/v1/indicators/sales/lines/summary`, documentado —sin alcance, lo ve entero quien porte el permiso— | `T-03` | La ruta en `PERMISO_DE_CADA_OPERACION` | Pendiente |
| `T-05` | `SaleLinesSummaryIT`: `CA-IN-059` a `CA-IN-066` | `T-01`, `T-04` | Cuadra con el resumen de ventas | Pendiente |
| `T-06` | Contrato regenerado; `api/index.md`, `requirements/mv.md` §3, `security.md` (sembrado), `requirements/in.md` y matriz | `T-05` | Solo altas | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-059` a `CA-IN-061` | `T-03`, `T-05` |
| `CA-IN-062`, `CA-IN-063` | `T-02`, `T-05` |
| `CA-IN-064`, `CA-IN-065` | `T-03`, `T-05` |
| `CA-IN-066` | `T-01`, `T-04`, `T-05` |

---

## 3. Desviaciones respecto del plan

Ninguna todavía.

---

## 4. Bloqueos declarados

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements/in.md`, `requirements/mv.md`, `security.md`, `requirements.md` y `api/index.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
