# TASKS — `RF-IN-004` Consultar las ventas por vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-004` |
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
| `T-01` | `SalesFigures.confirmedBySeller` y `confirmedUnassigned`, con `SellerRanking`, `SellerFigure` y `Totals`; `JpaSalesFigures` con las sentencias de `plan.md` §4.3 | `RF-IN-003` · `T-01` | `SalesFiguresIT`: sin vendedor nunca en el ranking; lo sin asignar solo con `seller_id` nulo | Pendiente |
| `T-02` | `SalesBySellerResponse` con `@Schema` propios; `GetSalesBySellerService`: `VAL-005`, corte, identidades por `UserCatalog.findAll`, desempate por nombre de usuario, lo sin asignar solo con `everything()` sin `sellerId` | `T-01` | `unassigned` nulo para un vendedor | Pendiente |
| `T-03` | `SalesIndicatorsController`: `GET /api/v1/indicators/sales/by-seller`, documentado —cada uno con lo suyo, lo sin asignar, el empate en la frontera— | `T-02` | La ruta en `PERMISO_DE_CADA_OPERACION` | Pendiente |
| `T-04` | `SalesBySellerIT`: `CA-IN-030` a `CA-IN-037` | `T-03` | Coste: sentencias fijas con `limit` 2 y 100 | Pendiente |
| `T-05` | Contrato regenerado y prosa releída; `docs/api/index.md`; ficha y matriz | `T-04` | `openapi.json` con la ruta y su permiso | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-030`, `CA-IN-031` | `T-01`, `T-04` |
| `CA-IN-032`, `CA-IN-033` | `T-01`, `T-02`, `T-04` |
| `CA-IN-034`, `CA-IN-035` | `T-01`, `T-02`, `T-04` |
| `CA-IN-036` | `T-02`, `T-04` |
| `CA-IN-037` | `T-02`, `T-03`, `T-04` |

---

## 3. Desviaciones respecto del plan

Ninguna todavía.

---

## 4. Bloqueos declarados

1. **Se construye después de `RF-IN-003`**, cuyas sentencias reutiliza.
2. **Los vendedores sin ventas** (`spec.md` §14) no bloquean; se decide con el responsable si se pide.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements/in.md`, `requirements.md` y `api/index.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
