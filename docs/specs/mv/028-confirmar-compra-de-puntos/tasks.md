# TASKS — `RF-MV-028` Confirmar el pago de una compra de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-028` |
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
| `T-01` | `JpaMovementRepository.confirmPointsPurchaseIfPending`; `confirmPending` con referencia | `RF-MV-027` `T-02` | Filtra el tipo en la sentencia | **Hecha** — 30-09-2026 |
| `T-02` | `ConfirmPointsPurchaseService`, `ConfirmPointsPurchaseRequest` | `T-01` | Solo quien gana la transición abona | **Hecha** — 30-09-2026 |
| `T-03` | `PointsPurchaseController`: `POST /movements/{id}/points-purchase-confirmation` | `T-02` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 30-09-2026 |
| `T-04` | `ConfirmPointsPurchaseIT`: `CA-MV-318` a `CA-MV-325` | `T-03`, `RF-MV-029` `T-02` | `CA-MV-323` con dos hilos | **Hecha** — 30-09-2026 |
| `T-05` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-04` | | **Hecha** — 30-09-2026 |

---

## 2. Orden de ejecución

**Después de `RF-MV-027`**: `T-01` → `T-02` → `T-03` → `T-04` → `T-05`. `CA-MV-323` necesita el rechazo de `RF-MV-029`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-318` a `CA-MV-321` | `T-01`, `T-02`, `T-04` |
| `CA-MV-322` a `CA-MV-324` | `T-01`, `T-04` |
| `CA-MV-325` | `T-03`, `T-04` |

---

## 3.1 Desviaciones respecto del plan

**Nombres.** `PointsPurchaseService.confirm`, con `PointsRequests.Confirmation`, y el pago lo confirma `MovementRepository.confirmPendingPayment`; no hay `ConfirmPointsPurchaseService`. Suite: `PointsPurchaseIT`.

## 4. Bloqueos

**`RF-MV-027`**, que crea las compras.

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde.
- [x] Los ocho criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. La tarjeta por Stripe — enmienda del 01-10-2026

Rama: `feature/stripe-tarjeta`. Después de las tareas de `RF-MV-040` que cada fila cita.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-06` | La comprobación en la ruta manual; `CA-MV-478` en `PointsPurchaseIT`; la prosa | `RF-MV-040` · `T-01` | | **Hecha** — 01-10-2026 |

Criterios: `CA-MV-478`.

**Construido el 01-10-2026** (issue [#161](https://github.com/NexusPro-Dev/backend/issues/161)). Los criterios se prueban en `CardPaymentIT` —o en la suite de la entrada, para las compras— y no en la suite que cada fila nombra.

## 7. Sin ruta propia — enmienda del 01-10-2026

Rama: `feature/confirmar-por-pago`. Junto con `RF-MV-044`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-07` | `PointsPurchaseService.confirm` y su ruta se retiran; nace `confirmPayment`, que invoca `RF-MV-044`; `PointsPurchaseIT` pasa a la ruta nueva; `CA-MV-518` en `PointsPurchaseIT` | `RF-MV-044` · `T-03` | Ninguna prueba llama a la ruta retirada | Pendiente |

Criterios: `CA-MV-518`.
