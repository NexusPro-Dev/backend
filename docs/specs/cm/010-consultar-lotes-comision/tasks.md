# TASKS — `RF-CM-010` Consultar los lotes de comisión, y el detalle de uno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-010` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 28-09-2026 |
| Estado | **En revisión** — tareas `Hecha` el 28-09-2026, salvo `T-02`, retirada |
| Enmendadas | 29-09-2026 — `T-07` por **la clase de cada comisión** (`RN-CM-044`) |
| Issue | Pendiente de crear |
| Rama | `feature/devengo-de-comisiones` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `ix_commission_batches_periodo` en `V51` | `RF-CM-013` `T-01` | Migración desde cero | **Hecha** — 28-09-2026 |
| `T-02` | ~~`CommissionableLines.describe` en `MV`~~ | `RF-CM-013` `T-07` | — | **Retirada** — 28-09-2026, ver §3.1 |
| `T-03` | `CommissionBatchQueryRepository` y adaptador | `T-01` | — | **Hecha** — 28-09-2026 |
| `T-04` | Los dos servicios, los `record`s con `@Schema(name)` y las dos rutas en `PERMISO_DE_CADA_OPERACION` | `T-02`, `T-03` | `EndpointPermissionsIT` | **Hecha** — 28-09-2026 |
| `T-05` | `ListCommissionBatchesIT`, `GetCommissionBatchIT`: `CA-CM-181` a `CA-CM-188` | `T-04`, `RF-CM-009` `T-03` | Número de sentencias en `CA-CM-188` | **Hecha** — 28-09-2026 |
| `T-06` | Contrato OpenAPI y `requirements.md` | `T-05` | | **Hecha** — 28-09-2026 |

---

## 2. Orden de ejecución

**Después de `RF-CM-013` y `RF-CM-009`**: `T-01` → `T-02` y `T-03` → `T-04` → `T-05` → `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-181` a `CA-CM-187` | `T-04`, `T-05` |
| `CA-CM-188` | `T-02`, `T-05` |

---

## 3.1 Desviaciones respecto del plan

- **`T-07`**: `CA-CM-262` se prueba en `AfftrackSettlementIT`, que siembra la comisión afftrack; `CommissionBatchesIT` sigue en verde sin cambios. `CommissionLine.chainLevel` pasa a `Integer`.

- **Los datos de otros módulos se leen con `JOIN` en la misma sentencia**, y no por las interfaces de cada módulo como decía `plan.md` §1: es el precedente de `JpaUserCommissionRateQueryRepository`, que ya une `users`, `products` y `currencies` para leer. Una sentencia por lectura, sin idas por módulo y página, y con la prueba de número de sentencias igual. Por eso `CommissionableLines` **no gana** los métodos que el plan le añadía.
- **`ix_commission_batches_periodo` ya estaba en `V51`**, sin tarea aparte.
- **Las pruebas son una suite, `CommissionBatchesIT`**, que cubre también `RF-CM-012`, y no `ListCommissionBatchesIT` y `GetCommissionBatchIT`.
- **Un solo servicio para `RF-CM-010` y `RF-CM-012`** (`CommissionBatchQueryService`), con la persona como parámetro.
- **Cada lote publica `paidAmount`**, lo abonado en la billetera, leído del movimiento del abono: es lo que `RF-CM-011` devuelve, y en el listado evita ir a buscarlo.

## 4. Bloqueos

**`RF-CM-013`** y, para las pruebas, **`RF-CM-009`**.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

## 6. La clase de cada comisión — enmienda del 29-09-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-07` | `LEFT JOIN` a la línea y a la liquidación, `commissionKind` y los campos nulables en `CommissionLineResponse`, prosa de la `@Operation`; `CA-CM-262` | `RF-CM-020` `T-07` | `CommissionBatchesIT`; el diff del `openapi.json` | **Hecha** — 29-09-2026 |

Rama: `feature/comision-afftrack`.
