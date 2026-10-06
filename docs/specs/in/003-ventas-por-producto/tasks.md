# TASKS — `RF-IN-003` Consultar las ventas por producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-003` |
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
| `T-01` | `SalesFigures.confirmedByProduct`, `ProductRanking` y `ProductFigure`; `JpaSalesFigures` con las dos sentencias de `plan.md` §4.3 | `RF-IN-001` · `T-03` | `SalesFiguresIT`: orden, nombre más reciente, total, importes solo del corte | Pendiente |
| `T-02` | `SalesByProductResponse` con `@Schema` propios (`IndicatorProduct`); `GetSalesByProductService` con `VAL-005` y el corte | `T-01` | Sin esquemas fundidos en el contrato | Pendiente |
| `T-03` | `SalesIndicatorsController`: `GET /api/v1/indicators/sales/by-product`, documentado —el orden según la moneda, el nombre congelado, los paquetes— | `T-02` | La ruta en `PERMISO_DE_CADA_OPERACION` | Pendiente |
| `T-04` | `SalesByProductIT`: `CA-IN-023` a `CA-IN-029` | `T-03` | Coste: dos sentencias con `limit` 2 y 50 | Pendiente |
| `T-05` | Contrato regenerado y prosa releída; `docs/api/index.md`; ficha y matriz | `T-04` | `openapi.json` con la ruta y su permiso | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-023`, `CA-IN-024` | `T-01`, `T-04` |
| `CA-IN-025`, `CA-IN-026` | `T-01`, `T-04` |
| `CA-IN-027` | `T-01`, `T-02`, `T-04` |
| `CA-IN-028` | `T-02`, `T-04` |
| `CA-IN-029` | `T-02`, `T-03`, `T-04` |

---

## 3. Desviaciones respecto del plan

Ninguna todavía.

---

## 4. Bloqueos declarados

1. **Se construye después de `RF-IN-001`**.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los siete criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements/in.md`, `requirements.md` y `api/index.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
