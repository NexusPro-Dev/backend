# TASKS — `RF-MV-005` Anular una venta pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-005` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 17-09-2026 |
| Estado | **En revisión** — `T-01` a `T-07` `Hecha` el 17-09-2026 |
| Issue | [#69](https://github.com/NexusPro-Dev/backend/issues/69) |
| Rama | `feature/venta-de-productos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V17`**: `voided_at`, `void_reason` y `ck_movements_voided` | — | Una anulada sin motivo la rechaza el esquema | **Hecha** — 17-09-2026 |
| `T-02` | `VoidReason`, `VoidSaleRequest`; `SaleResponse` gana `voidedAt` y `voidReason` con `types` | — | Motivo vacío → `VAL-002`; largo → `VAL-003` | **Hecha** — 17-09-2026 |
| `T-03` | `MovementRepository.voidIfPending`; el detalle proyecta las columnas | `T-01` | Cero filas sobre una no pendiente | **Hecha** — 17-09-2026 |
| `T-04` | `VoidSaleService`: motivo primero, transición, auditoría, respuesta | `T-02`, `T-03` | El motivo se valida antes de leer la venta | **Hecha** — 17-09-2026 |
| `T-05` | `MovementController`: `POST /{id}/voiding` con `@PreAuthorize('movements:void')` | `T-04` | Documentado con los códigos de §4 | **Hecha** — 17-09-2026 |
| `T-06` | `VoidSaleIT`: `CA-MV-110` a `CA-MV-118` | `T-05` | `CA-MV-115` con `movements:confirm` puesto | **Hecha** — 17-09-2026 |
| `T-07` | Contrato regenerado con la prosa; `requirements/mv.md`, `modelo-datos.md`, `requirements.md` | `T-06` | | **Hecha** — 17-09-2026 |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-110` a `CA-MV-113` | `T-03`, `T-04`, `T-06` |
| `CA-MV-114` | `T-02`, `T-04`, `T-06` |
| `CA-MV-115` | `T-05`, `T-06` |
| `CA-MV-116`, `CA-MV-117`, `CA-MV-118` | `T-04`, `T-06` |

---

## 3. Desviaciones respecto del plan

**Ninguna.** Los fixtures de `ConfirmSaleIT`, `MovementsIT` y `MyProductsIT` que siembran ventas `ANULADA` ganan fecha y motivo, porque `ck_movements_voided` los exige desde `V17`.

**`DevelopmentSeedIT` sigue fuera de la corrida** por el cambio ajeno y sin confirmar de `semilla-productos.sql`, declarado en `RF-MV-006` · `tasks.md` §3.

---

## 4. Definición de terminado

- [x] `./mvnw clean verify` en verde (con `DevelopmentSeedIT` excluida, ver §3).
- [x] Los nueve criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements/mv.md`, `modelo-datos.md` y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
