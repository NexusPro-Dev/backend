# TASKS — `RF-MV-042` Pagar con tarjeta un pago pendiente propio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-042` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 01-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/stripe-tarjeta` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PaymentRepository.lockPendingOwn`; `CardGateway.retrieve` y su implementación | `RF-MV-040` · `T-03` | | Pendiente |
| `T-02` | `CardPayment.retomar`: retoma o abre, con `EX-002` sobre el cobro cancelado | `T-01`, `RF-MV-040` · `T-05` | El bloqueo va antes de leer la referencia | Pendiente |
| `T-03` | `POST /movements/mine/{id}/card-charge` | `T-02` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-04` | `PayPendingByCardIT`: `CA-MV-453` a `CA-MV-461` | `T-03` | Cada criterio afirmado en el cuerpo de la prueba | Pendiente |
| `T-05` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-04` | | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05`. Después de `RF-MV-040`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-453` a `CA-MV-455`, `CA-MV-459` | `T-02`, `T-04` |
| `CA-MV-456` a `CA-MV-458` | `T-01`, `T-02`, `T-04` |
| `CA-MV-460`, `CA-MV-461` | `T-03`, `T-04` |

---

## 4. Bloqueos

Ninguno propio.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los nueve criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
