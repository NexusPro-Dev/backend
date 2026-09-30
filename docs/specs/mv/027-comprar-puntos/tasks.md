# TASKS — `RF-MV-027` Comprar puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-027` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 30-09-2026 |
| Issue | [#149](https://github.com/NexusPro-Dev/backend/issues/149) |
| Rama | `feature/compra-de-puntos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PointsAmount`, `Movement.compraDePuntos(...)` | `RF-MV-025` `T-03` | Unitarias: redondeo hacia abajo; cero inválido | **Hecha** — 30-09-2026 |
| `T-02` | `JpaMovementRepository`: la cabecera con `points_rate_id` y `points_amount`, y su primer pago | `T-01`, `RF-MV-025` `T-01` | El esquema rechaza tasa sin puntos | **Hecha** — 30-09-2026 |
| `T-03` | `BuyPointsService`, `BuyPointsRequest`, `PointsPurchaseResponse` | `T-02`, `RF-MV-025` `T-04` | La validación va antes de tocar nada | **Hecha** — 30-09-2026 |
| `T-04` | `PointsPurchaseController`: `POST /movements/mine/points-purchases` con `Idempotency-Key` | `T-03` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 30-09-2026 |
| `T-05` | `BuyPointsIT`: `CA-MV-306` a `CA-MV-317` | `T-04` | `CA-MV-310` con dos hilos | **Hecha** — 30-09-2026 |
| `T-06` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-05` | | **Hecha** — 30-09-2026 |

---

## 2. Orden de ejecución

**Después de `RF-MV-025`**: `T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-306` a `CA-MV-309` | `T-01`, `T-02`, `T-03`, `T-05` |
| `CA-MV-310`, `CA-MV-311` | `T-03`, `T-05` |
| `CA-MV-312` a `CA-MV-315` | `T-03`, `T-05` |
| `CA-MV-316`, `CA-MV-317` | `T-04`, `T-05` |

---

## 3.1 Desviaciones respecto del plan

**La repetición se detecta leyendo la clave antes de escribir**, como el bono (`RF-MV-023` · `tasks.md` §3.1), y no traduciendo la violación en una transacción nueva. Dos peticiones **simultáneas** con la misma clave registran una sola compra —`uq_payments_idempotency_key` para la segunda—, pero esa segunda puede responder `500` en lugar de `200` si llega a insertar antes de ver la primera. Se acepta, igual que en el bono: nada se registra dos veces y el cliente reintenta con la misma clave. `CA-MV-310` lo comprueba por lo que queda escrito, no por los códigos.

**Nombres.** `PointsPurchaseService.buy`, con el cuerpo `PointsRequests.Purchase`, en `PointsController`; no hay `BuyPointsService`. La suite es `PointsPurchaseIT`, que cubre `RF-MV-027` a `RF-MV-029` y `RF-MV-031`, y el redondeo tiene sus unitarias en `PointsAmountTest`.

## 4. Bloqueos

**`RF-MV-025`**, que trae la migración de la etapa y la lectura de la vigente.

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde.
- [x] Los doce criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
