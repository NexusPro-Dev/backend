# TASKS — `RF-MV-018` Volver a pagar una venta pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-018` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 26-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/pagos-y-saldos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V48__mv_pagos.sql`** (o la siguiente libre): `payments` con sus restricciones e índices; traslado; `DO $$` de comprobación; retirar `movements.payment_method_id`; sembrar `movements:retry-payment` | — | Aplicada sobre una copia de la base de desarrollo: cada movimiento con un pago, estados casados | Pendiente |
| `T-02` | `Payment`, `PaymentStatus`, `IdempotencyKey` | — | Unitarias: clave corta, larga, con espacios → `VAL-002` | Pendiente |
| `T-03` | `PaymentRepository` y `JpaPaymentRepository` | `T-01`, `T-02` | La violación llega con el nombre de la restricción | Pendiente |
| `T-04` | `Movement` pierde `paymentMethodId`; `JpaMovementRepository` inserta la venta con su primer pago | `T-03` | Compila sin la columna | Pendiente |
| `T-05` | Las cinco entradas de compra y `PublishedRegistrationSaleRegistrar` leen `Idempotency-Key` opcional y crean el primer pago | `T-04` | Repetir con la misma clave devuelve la misma venta | Pendiente |
| `T-06` | Listados (`RF-MV-006`, `RF-MV-008`, `RF-MV-015`, `RF-MV-017`) y detalle: método por `LATERAL` sobre el último pago; filtros por método sobre él | `T-04` | Estadísticas de Hibernate: el número de sentencias no crece con las filas | Pendiente |
| `T-07` | `PaymentResponse`; `SaleResponse` gana `payments` | `T-03` | Sin la clave de idempotencia | Pendiente |
| `T-08` | `ConfirmSaleService` y `VoidSaleService` sobre el pago pendiente | `T-03` | Los criterios nuevos de `RF-MV-003` y `RF-MV-005` | Pendiente |
| `T-09` | Fixtures de `src/test` que escriben `movements.payment_method_id` pasan a `payments` | `T-04` | `grep` limpio | Pendiente |
| `T-10` | `RetryPaymentService` y `RetryPaymentRequest`: clave primero, venta propia, estado, método, `INSERT`, traducción, auditoría | `T-03`, `T-07` | La relectura tras la violación, en transacción nueva | Pendiente |
| `T-11` | `MovementController`: `POST /mine/{id}/payments` con `@PreAuthorize('movements:retry-payment')` | `T-10` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-12` | `RetryPaymentIT`: `CA-MV-206` a `CA-MV-215` | `T-11` | `CA-MV-210` con dos hilos | Pendiente |
| `T-13` | `PaymentsOnRegistrationIT`: `CA-MV-216`; `CA-MV-217` en las suites de los listados | `T-05`, `T-06` | | Pendiente |
| `T-14` | `PermissionIT` y `EndpointPermissionsIT` con el permiso nuevo | `T-01`, `T-11` | El catálogo cuenta uno más | Pendiente |
| `T-15` | Contrato regenerado, **con la prosa de las `@Operation` releída**; `requirements.md` y `security.md` (el permiso pasa a sembrado) | `T-12`, `T-13` | El `diff` del json enseña `payments` y la cabecera | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → (`T-05`, `T-06`, `T-07`, `T-08`, `T-09`) → `T-10` → `T-11` → (`T-12`, `T-13`, `T-14`) → `T-15`.

**`RF-MV-004` depende de `T-01` a `T-03` y `T-07`**: se construye después de ellas, en la misma rama.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-206` a `CA-MV-209` | `T-10`, `T-12` |
| `CA-MV-210` | `T-01`, `T-10`, `T-12` |
| `CA-MV-211` a `CA-MV-215` | `T-10`, `T-11`, `T-12` |
| `CA-MV-216` | `T-05`, `T-13` |
| `CA-MV-217` | `T-06`, `T-13` |

---

## 4. Bloqueos

**Ninguno.** El árbol de trabajo lo comparte otra sesión (el aula de `AC`): antes de correr `mvn` o de cambiar de rama, avisarla.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los doce criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
