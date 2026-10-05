# TASKS — `RF-MV-010` Activar un producto comprado de implementación manual

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-010` |
| Especificación | [`spec.md`](spec.md) v0.3.0 |
| Plan | [`plan.md`](plan.md) v0.2.0 |
| `plan.md` aprobado el | 28-09-2026 |
| Estado | **En revisión** — `T-01` a `T-08` `Hecha` el 28-09-2026; `T-09` y `T-10` **Pendiente** (05-10-2026, `RN-MV-075`) |
| Issue | [#123](https://github.com/NexusPro-Dev/backend/issues/123) |
| Rama | `feature/activar-producto-comprado` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V50`: `movements:activate-own-product` por tipo de rol | — | El catálogo cuenta 145 | Hecha |
| `T-02` | `LineDelivery`, extraído de `ConfirmSaleService` | — | `ConfirmSaleIT` en verde sin tocarse | Hecha |
| `T-03` | `findOwnLineForActivation` (actor + `FOR UPDATE OF d`) y `findMyProduct`; `MyProductRow.lineId` | — | Cero filas para la línea de otro | Hecha |
| `T-04` | `PurchasedProductState.PENDIENTE_ACTIVACION`, `MyProductResponse.lineId`, `ListMyProductsService.get` | `T-03` | `MyProductsIT` con el nombre nuevo | Hecha |
| `T-05` | `ActivateMyProductService`: buscar, comprobar, entregar, auditar | `T-02`, `T-03`, `T-04` | Los `409` antes de escribir nada | Hecha |
| `T-06` | `MovementController`: `POST /mine/products/{lineId}/activation`, antes de `/mine/{id}`; la prosa de `GET /mine/products` | `T-05` | Documentado con los códigos de `plan.md` §4 | Hecha |
| `T-07` | `ActivateMyProductIT`: `CA-MV-275` a `CA-MV-283` | `T-06` | `CA-MV-278` con el superadministrador | Hecha |
| `T-08` | `PermissionIT`, `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md`, `security.md` | `T-07` | | Hecha |
| `T-09` | **`EX-006`** en `ActivateMyProductService`: el actor en `FTD_PENDIENTE` responde conflicto **antes** de las comprobaciones de la línea; y la prosa de la ruta en `MovementController` (05-10-2026) | — | `CA-MV-583` | **Hecha el 05-10-2026** |
| `T-10` | **Pruebas**: `CA-MV-583` en `ActivateMyProductIT`; `CA-MV-584` sobre el adaptador de `RF-MV-001` `T-46` (05-10-2026). **Se apartó del plan**: vive en `SelfRegistrationIT` (`laActivacionEsIdempotente`) y no en una `FirstDepositActivationIT` nueva, porque necesita todo el montaje del registro —enlace, vendedor y venta del alta— y entra por el camino real, el cambio de estado de `SP`, en lugar de invocar el puerto a pelo | `T-09`, `RF-MV-001` `T-46` | Las dos pasan; la segunda llamada de `CA-MV-584` no escribe ninguna posesión | **Hecha el 05-10-2026** |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06` → `T-07` → `T-08`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-275`, `CA-MV-276`, `CA-MV-277` | `T-02`, `T-05`, `T-07` |
| `CA-MV-278` | `T-03`, `T-07` |
| `CA-MV-279` a `CA-MV-281` | `T-05`, `T-07` |
| `CA-MV-282` | `T-04`, `T-07` |
| `CA-MV-283` | `T-01`, `T-06`, `T-07` |
| `CA-MV-583` | `T-09`, `T-10` |
| `CA-MV-584` | `RF-MV-001` `T-46`, `T-10` |

---

## 4. Bloqueos

Ninguno. **Se apiló sobre `feature/pagos-y-saldos`**, que cambió la confirmación (el pago como intento); las dos llegaron a `develop` el 28-09-2026, esta por el PR [#125](https://github.com/NexusPro-Dev/backend/pull/125).

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde.
- [x] Los nueve criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

**Cierre documental el 30-09-2026**: la construcción se mezcló el 28-09-2026 sin marcar las tareas ni llevar el permiso a `security.md`. Las cuatro casillas se marcan con la suite completa en verde el 30-09-2026 (503 unitarias y 2177 de integración), con `CA-MV-275` a `CA-MV-283` en `ActivateMyProductIT` y con `requirements.md` v0.237.0 y `security.md` v0.84.0.
