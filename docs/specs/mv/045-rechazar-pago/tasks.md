# TASKS — `RF-MV-045` Rechazar un pago pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-045` |
| Especificación | [`spec.md`](spec.md) v0.1.1 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 01-10-2026 |
| Estado | **En revisión** — tareas `Hecha` el 01-10-2026 |
| Issue | [#163](https://github.com/NexusPro-Dev/backend/issues/163) |
| Rama | `feature/confirmar-por-pago` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PaymentResolutionService.reject`; `RejectPaymentService.rejectPayment` y `PointsPurchaseService.rejectPayment`, de paquete; se retiran `RejectPaymentService.reject` y `PointsPurchaseService.reject` | `RF-MV-044` · `T-01` a `T-03` | El motivo se valida antes del bloqueo | **Hecha** — 01-10-2026 |
| `T-02` | `POST /movements/payments/{paymentId}/rejection`; se retiran las rutas de `PaymentController` y `PointsController` | `T-01` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 01-10-2026 |
| `T-03` | `RejectPaymentIT` a la ruta nueva: `CA-MV-507` a `CA-MV-515` | `T-02` | Cada criterio afirmado en el cuerpo de la prueba | **Hecha** — 01-10-2026 |
| `T-04` | Las suites que rechazaban por las rutas viejas pasan a la nueva; `EndpointPermissionsIT`; contrato con la prosa releída | `T-03` | `./mvnw clean verify` en verde | **Hecha** — 01-10-2026 |

---

## 2. Orden de ejecución

Después de `RF-MV-044` · `T-03`, en la misma rama. `T-01` → `T-02` → `T-03` → `T-04`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-507` a `CA-MV-509`, `CA-MV-515` | `T-01`, `T-03` |
| `CA-MV-510` a `CA-MV-513` | `T-01`, `T-03` |
| `CA-MV-514` | `T-02`, `T-03` |

---

## 3.1 Desviaciones respecto del plan

Las de `RF-MV-044` · `tasks.md` §3.1. **El cuerpo es `RejectPaymentRequest`**, el de `RF-MV-004`. **El motivo responde con los códigos de `RejectionReason`**: `VAL-002` vacío y `VAL-003` demasiado largo, como en `RF-MV-004` (`spec.md` v0.1.1). **`CA-MV-512` siembra el retiro por SQL** con un pago pendiente, que en la vida real no existe —el pago de un retiro nace confirmado—: lo que se prueba es que el tipo se comprueba **antes** que el estado.

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [x] Los nueve criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
