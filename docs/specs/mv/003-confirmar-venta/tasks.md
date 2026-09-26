# TASKS — `RF-MV-003` Confirmar una venta pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-003` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 17-09-2026 |
| Estado | **En revisión** — `T-01` a `T-12` `Hecha` el 17-09-2026 |
| Issue | [#67](https://github.com/NexusPro-Dev/backend/issues/67) |
| Rama | `feature/venta-de-productos` |

!!! warning "Enmendado el 23-09-2026 — toda línea entregada escribe su posesión"

    `plan.md`, enmienda del 23-09-2026 (`RN-MV-036`, `RN-SP-056`). Dos tareas nuevas:

    | ID | Tarea | Depende de | Verificación | Estado |
    |---|---|---|---|---|
    | `T-13` | `GrantOrder` gana `productId` y `movementDetailId`, y admite `membershipId` nulo; `ConfirmSaleService` invoca la escritura publicada para **cada** línea entregada y no solo para las de upgrade | `T-70` de `RF-SP-024` | `CA-MV-182`: confirmar una venta de **un bot** deja una fila en `user_products` con su producto, su línea, `membership_id` nulo y `ends_at` a los días de la línea desde la confirmación. Y una línea **retenida** no deja ninguna | **Pendiente** |
    | `T-14` | La entrega es **idempotente** por esquema, no por comprobación previa | `T-13` | `CA-MV-183`: una segunda confirmación de la misma venta no duplica posesiones — `uq_user_products_linea` la rechaza, y el caso de uso no la comprueba antes | **Pendiente** |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V16`**: `implementation` (rellena desde `products`, luego `NOT NULL` + `CHECK`), `delivery_status` (`DEFAULT 'PENDIENTE'` + `CHECK`), `delivered_at`, `delivery_note`, `ck_movement_details_delivery` | — | Una línea existente queda `AUTOMATICA`/`MANUAL` según su producto y `PENDIENTE`; `ENTREGADA` sin fecha y `RETENIDA` sin motivo son rechazadas por el esquema | **Hecha** — 17-09-2026 |
| `T-02` | `ProductCatalog.SaleView` gana `implementation`; `PublishedProductCatalog` la proyecta | — | Las dos sentencias de venta (suelto y paquete) la traen | **Hecha** — 17-09-2026 |
| `T-03` | `MovementLine` gana `implementation` y la entrega; `DeliveryStatus`; `copiarDe` exige la implementación; el `INSERT` de líneas la escribe | `T-01`, `T-02` | `copiarDe` sin implementación lanza (unitaria); una venta nueva queda con la copia | **Hecha** — 17-09-2026 |
| `T-04` | `RegisterSaleService` y `BuyPackageService` copian `implementation` en cada línea | `T-03` | `RegisterSaleIT` y `BuyPackageIT` siguen en verde y la línea trae la copia | **Hecha** — 17-09-2026 |
| `T-05` | **`SP`**: `MembershipGrant` (interfaz en `application`) y `PublishedMembershipGrant` con `MANDATORY`, bloqueo de la persona, cierre e inserción, auditoría | — | Unitaria: sin transacción falla; integración: cierra la vigente e inserta la nueva con `ends_at` desde el instante indicado | **Hecha** — 17-09-2026 |
| `T-06` | `MovementRepository`: `confirmIfPending`, `findLinesForDelivery` (con destino, nivel y si es upgrade desde `products`), `markDelivered`, `markRetained`; las lecturas de detalle proyectan las columnas nuevas | `T-01` | `confirmIfPending` devuelve `false` sobre una venta no pendiente sin tocarla | **Hecha** — 17-09-2026 |
| `T-07` | `ConfirmSaleService`: transición, bucle por líneas con `RN-MV-029` sobre `CurrentMembershipLookup`, `MembershipGrant`, auditoría con las líneas | `T-05`, `T-06` | Lee las líneas **después** de la transición | **Hecha** — 17-09-2026 |
| `T-08` | `SaleResponse`/`SaleLineResponse`, `PurchaseResponse`/`PurchaseLineResponse` y `MyMovementResponse`: `confirmedAt` y la entrega por línea, nulables con `types` | `T-06` | El contrato declara los nulables con `types` y no con `nullable` | **Hecha** — 17-09-2026 |
| `T-09` | `MovementController`: `POST /{id}/confirmation` con `@PreAuthorize('movements:confirm')`, documentado con los códigos de §4.2 | `T-07`, `T-08` | El `409` lleva el estado actual en el mensaje | **Hecha** — 17-09-2026 |
| `T-10` | `ConfirmSaleIT`: `CA-MV-083` a `CA-MV-096`, `CA-MV-098` | `T-09` | La membresía se comprueba en `user_products`; la auditoría en `audit_change_log` | **Hecha** — 17-09-2026 |
| `T-11` | `ConfirmSaleConcurrencyIT`: `CA-MV-097` con el arnés concurrente del proyecto | `T-09` | Una `200`, una `409`, una fila nueva en `user_products` | **Hecha** — 17-09-2026 |
| `T-12` | Contrato OpenAPI regenerado y prosa releída; `requirements.md` (fila, indicadores, control de cambios) | `T-10` | `openapi.json` declara `confirmedAt`, `deliveryStatus`, `deliveredAt` y `deliveryNote` | **Hecha** — 17-09-2026 |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-083` a `CA-MV-087` | `T-06`, `T-09`, `T-10` |
| `CA-MV-088` a `CA-MV-091` | `T-05`, `T-07`, `T-10` |
| `CA-MV-092` | `T-07`, `T-10` |
| `CA-MV-093`, `CA-MV-094`, `CA-MV-095` | `T-03`, `T-04`, `T-07`, `T-10` |
| `CA-MV-096` | `T-05`, `T-07`, `T-10` |
| `CA-MV-097` | `T-06`, `T-11` |
| `CA-MV-098` | `T-08`, `T-10` |

---

## 3. Desviaciones respecto del plan

**Ninguna.** Los dieciséis criterios tienen prueba, incluida la concurrente; `MANDATORY` se fija con una prueba de integración y no unitaria, porque lo que se comprueba es el proxy transaccional de Spring y no la clase.

**`DevelopmentSeedIT` sigue fuera de la corrida** por el cambio ajeno y sin confirmar de `semilla-productos.sql`, declarado en `RF-MV-006` · `tasks.md` §3.

---

## 4. Definición de terminado

- [x] `./mvnw clean verify` en verde — 1874 de integración, 0 fallos (con `DevelopmentSeedIT` excluida, ver §3).
- [x] Los dieciséis criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements/mv.md`, `requirements/sp.md`, `architecture.md`, `modelo-datos.md` y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
