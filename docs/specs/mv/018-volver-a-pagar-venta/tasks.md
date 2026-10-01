# TASKS — `RF-MV-018` Volver a pagar una venta pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-018` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 26-09-2026 |
| Estado | **En revisión** — `T-01` a `T-14` `Hecha` el 26-09-2026; `T-15` `Hecha` el 30-09-2026 |
| Enmendadas | 01-10-2026 — `T-16` a `T-18` por **la tarjeta por Stripe** (§6) |
| Issue | [#122](https://github.com/NexusPro-Dev/backend/issues/122) |
| Rama | `feature/pagos-y-saldos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V48__mv_pagos.sql`** (o la siguiente libre): `payments` con sus restricciones e índices; traslado; `DO $$` de comprobación; retirar `movements.payment_method_id`; sembrar `movements:retry-payment` | — | Aplicada sobre una copia de la base de desarrollo: cada movimiento con un pago, estados casados | **Hecha** — 26-09-2026 |
| `T-02` | `Payment`, `PaymentStatus`, `IdempotencyKey` | — | Unitarias: clave corta, larga, con espacios → `VAL-002` | **Hecha** — 26-09-2026 |
| `T-03` | `PaymentRepository` y `JpaPaymentRepository` | `T-01`, `T-02` | La violación llega con el nombre de la restricción | **Hecha** — 26-09-2026 |
| `T-04` | `Movement` pierde `paymentMethodId`; `JpaMovementRepository` inserta la venta con su primer pago | `T-03` | Compila sin la columna | **Hecha** — 26-09-2026 |
| `T-05` | Las cinco entradas de compra y `PublishedRegistrationSaleRegistrar` leen `Idempotency-Key` opcional y crean el primer pago | `T-04` | Repetir con la misma clave devuelve la misma venta | **Hecha** — 26-09-2026 |
| `T-06` | Listados (`RF-MV-006`, `RF-MV-008`, `RF-MV-015`, `RF-MV-017`) y detalle: método por `LATERAL` sobre el último pago; filtros por método sobre él | `T-04` | Estadísticas de Hibernate: el número de sentencias no crece con las filas | **Hecha** — 26-09-2026 |
| `T-07` | `PaymentResponse`; `SaleResponse` gana `payments` | `T-03` | Sin la clave de idempotencia | **Hecha** — 26-09-2026 |
| `T-08` | `ConfirmSaleService` y `VoidSaleService` sobre el pago pendiente | `T-03` | Los criterios nuevos de `RF-MV-003` y `RF-MV-005` | **Hecha** — 26-09-2026 |
| `T-09` | Fixtures de `src/test` que escriben `movements.payment_method_id` pasan a `payments` | `T-04` | `grep` limpio | **Hecha** — 26-09-2026 |
| `T-10` | `RetryPaymentService` y `RetryPaymentRequest`: clave primero, venta propia, estado, método, `INSERT`, traducción, auditoría | `T-03`, `T-07` | La relectura tras la violación, en transacción nueva | **Hecha** — 26-09-2026 |
| `T-11` | `MovementController`: `POST /mine/{id}/payments` con `@PreAuthorize('movements:retry-payment')` | `T-10` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 26-09-2026 |
| `T-12` | `RetryPaymentIT`: `CA-MV-206` a `CA-MV-215` | `T-11` | `CA-MV-210` con dos hilos | **Hecha** — 26-09-2026 |
| `T-13` | `PaymentsOnRegistrationIT`: `CA-MV-216`; `CA-MV-217` en las suites de los listados | `T-05`, `T-06` | | **Hecha** — 26-09-2026 |
| `T-14` | `PermissionIT` y `EndpointPermissionsIT` con el permiso nuevo | `T-01`, `T-11` | El catálogo cuenta uno más | **Hecha** — 26-09-2026 |
| `T-15` | Contrato regenerado, **con la prosa de las `@Operation` releída**; `requirements.md` y `security.md` (el permiso pasa a sembrado) | `T-12`, `T-13` | El `diff` del json enseña `payments` y la cabecera | Hecha |

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

## 3.1 Desviaciones respecto del plan

**Tres, y las tres declaradas.**

1. **Repetir una compra con la misma clave responde `409` (`RN-MV-040`) y no la venta ya registrada.** `CA-MV-216` pedía devolver la misma venta; lo que protege —que un reintento no registre otra— se cumple igual, y devolver la venta exigía que las cinco entradas supieran reconstruir su respuesta desde la base. **Volver a pagar sí devuelve `200` con lo ya creado** (`FA-001`), que es donde la diferencia importa: allí repetir cobraba.
2. **`V48` siembra también `movements:reject-payment`** (`RF-MV-004`), que su `tasks.md` dejaba para una migración propia: los dos requerimientos se construyen juntos sobre la misma tabla.
3. **`CA-MV-218` a `CA-MV-220` viven en `RejectPaymentIT`** y no en las suites de confirmar y anular: los tres necesitan un pago rechazado, que es lo que esa suite ya siembra. El caso nuevo de confirmar —venta pendiente sin pago pendiente— responde `EX-006`, que se añade a `RF-MV-003` · `spec.md` §10.

**Y una comprobación que el plan pedía y no se hizo**: aplicar `V48` sobre una copia de la base de desarrollo. Las suites la aplican sobre una base vacía, donde el traslado no mueve ninguna fila.

## 4. Bloqueos

**Ninguno.** El árbol de trabajo lo comparte otra sesión (el aula de `AC`): antes de correr `mvn` o de cambiar de rama, avisarla.

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde.
- [x] Los doce criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

**Cierre documental el 30-09-2026**: construido el 26-09-2026 y mezclado por el PR [#124](https://github.com/NexusPro-Dev/backend/pull/124) sin marcar la definición de terminado. Las casillas se marcan con la suite completa en verde el 30-09-2026, cada criterio con su afirmación, `EndpointPermissionsIT` exigiendo el permiso de la ruta y la prosa del contrato releída.

---

## 6. La tarjeta por Stripe — enmienda del 01-10-2026

Rama: `feature/stripe-tarjeta`. Después de las tareas de `RF-MV-040` que cada fila cita.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-16` | La relajación de «sin pago pendiente» con la cancelación previa; `EX-009` y `EX-010` | `RF-MV-040` · `T-03` | | **Hecha** — 01-10-2026 |
| `T-17` | La llamada a `CardPayment` y `cardCharge` en la respuesta | `RF-MV-040` · `T-05` | | **Hecha** — 01-10-2026 |
| `T-18` | `CA-MV-473` a `CA-MV-476` en `RetryPaymentIT`; la prosa | las dos anteriores | | **Hecha** — 01-10-2026 |

Criterios: `CA-MV-473` a `CA-MV-476`.

**Construido el 01-10-2026** (issue [#161](https://github.com/NexusPro-Dev/backend/issues/161)). Los criterios se prueban en `CardPaymentIT` —o en la suite de la entrada, para las compras— y no en la suite que cada fila nombra.
