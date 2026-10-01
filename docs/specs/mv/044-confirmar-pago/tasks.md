# TASKS — `RF-MV-044` Confirmar un pago pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-044` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 01-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/confirmar-por-pago` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V63__mv_conciliar_por_el_pago.sql`**: nace `movements:confirm-payment` (`01a0ef9c-6800-701f-9c4f-5e7ad7000058`); se reparte por posesión de los retirados; `movements:reject-payment` corrige nombre y descripción; se retiran `movements:confirm`, `movements:confirm-points-purchase` y `movements:reject-points-purchase`; bloque de comprobación | — | Catálogo 179; `SUPERADMIN` y `ADMIN` portan los dos | Pendiente |
| `T-02` | `PaymentRepository.lockMovementOf` y `findTarget`; la referencia en `MovementRepository.confirmIfPending` | — | El bloqueo es del movimiento y la lectura del pago va después | Pendiente |
| `T-03` | `PaymentResolutionService.confirm`; `ConfirmSaleService.confirmPayment` y `PointsPurchaseService.confirmPayment`, de paquete; se retiran `ConfirmSaleService.confirm` y `PointsPurchaseService.confirm` | `T-02` | Las comprobaciones en el orden de `spec.md` §10 | Pendiente |
| `T-04` | `POST /movements/payments/{paymentId}/confirmation` en `PaymentController`; se retiran las rutas de `MovementController` y `PointsController` | `T-03` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-05` | `ConfirmPaymentIT`: `CA-MV-495` a `CA-MV-506` | `T-01`, `T-04` | Cada criterio afirmado en el cuerpo de la prueba | Pendiente |
| `T-06` | Las suites que confirmaban por las rutas viejas pasan a la nueva, con un ayudante común que confirma **el pago pendiente de un movimiento** | `T-04` | Ninguna prueba llama a una ruta retirada | Pendiente |
| `T-07` | El catálogo a 179 en todas las suites que lo cuentan; `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-05`, `T-06` | `./mvnw clean verify` en verde | Pendiente |

---

## 2. Orden de ejecución

`T-01` y `T-02` → `T-03` → `T-04` → `T-05` y `T-06` → `T-07`. **Junto con `RF-MV-045`**, que comparte `T-01`, `T-02` y el servicio: se construyen en la misma rama.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-495` a `CA-MV-498`, `CA-MV-506` | `T-03`, `T-05` |
| `CA-MV-499` a `CA-MV-502` | `T-02`, `T-03`, `T-05` |
| `CA-MV-503`, `CA-MV-504` | `T-02`, `T-05` |
| `CA-MV-505` | `T-01`, `T-04`, `T-05` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los doce criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
