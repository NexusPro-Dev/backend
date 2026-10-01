# TASKS — `RF-MV-029` Rechazar el pago de una compra de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-029` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 30-09-2026 |
| Enmendadas | 01-10-2026 — `T-06` por **la tarjeta por Stripe** (§6) |
| Issue | [#149](https://github.com/NexusPro-Dev/backend/issues/149) |
| Rama | `feature/compra-de-puntos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `JpaMovementRepository.rejectPointsPurchaseIfPending` | `RF-MV-027` `T-02` | Filtra el tipo; escribe instante y motivo | **Hecha** — 30-09-2026 |
| `T-02` | `RejectPointsPurchaseService`, `RejectPointsPurchaseRequest` | `T-01` | La validación va antes de tocar nada | **Hecha** — 30-09-2026 |
| `T-03` | `PointsPurchaseController`: `POST /movements/{id}/points-purchase-rejection` | `T-02` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 30-09-2026 |
| `T-04` | `RejectPointsPurchaseIT`: `CA-MV-326` a `CA-MV-331` | `T-03` | | **Hecha** — 30-09-2026 |
| `T-05` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-04` | | **Hecha** — 30-09-2026 |

---

## 2. Orden de ejecución

**Después de `RF-MV-027`**: `T-01` → `T-02` → `T-03` → `T-04` → `T-05`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-326` a `CA-MV-328`, `CA-MV-330` | `T-01`, `T-04` |
| `CA-MV-329` | `T-02`, `T-04` |
| `CA-MV-331` | `T-03`, `T-04` |

---

## 3.1 Desviaciones respecto del plan

**Nombres.** `PointsPurchaseService.reject`, con `PointsRequests.Rejection`, y el pago lo rechaza `MovementRepository.rejectPendingPayment`; no hay `RejectPointsPurchaseService`. Suite: `PointsPurchaseIT`.

## 4. Bloqueos

**`RF-MV-027`**, que crea las compras.

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde.
- [x] Los seis criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. La tarjeta por Stripe — enmienda del 01-10-2026

Rama: `feature/stripe-tarjeta`. Después de las tareas de `RF-MV-040` que cada fila cita.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-06` | La comprobación en la ruta manual; `CA-MV-479` en `PointsPurchaseIT`; la prosa | `RF-MV-040` · `T-01` | | **Hecha** — 01-10-2026 |

Criterios: `CA-MV-479`.

**Construido el 01-10-2026** (issue [#161](https://github.com/NexusPro-Dev/backend/issues/161)). Los criterios se prueban en `CardPaymentIT` —o en la suite de la entrada, para las compras— y no en la suite que cada fila nombra.
