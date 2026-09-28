# TASKS — `RF-MV-010` Activar un producto comprado de implementación manual

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-010` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 28-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/activar-producto-comprado` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V50`: `movements:activate-own-product` por tipo de rol | — | El catálogo cuenta 145 | Pendiente |
| `T-02` | `LineDelivery`, extraído de `ConfirmSaleService` | — | `ConfirmSaleIT` en verde sin tocarse | Pendiente |
| `T-03` | `findOwnLineForActivation` (actor + `FOR UPDATE OF d`) y `findMyProduct`; `MyProductRow.lineId` | — | Cero filas para la línea de otro | Pendiente |
| `T-04` | `PurchasedProductState.PENDIENTE_ACTIVACION`, `MyProductResponse.lineId`, `ListMyProductsService.get` | `T-03` | `MyProductsIT` con el nombre nuevo | Pendiente |
| `T-05` | `ActivateMyProductService`: buscar, comprobar, entregar, auditar | `T-02`, `T-03`, `T-04` | Los `409` antes de escribir nada | Pendiente |
| `T-06` | `MovementController`: `POST /mine/products/{lineId}/activation`, antes de `/mine/{id}`; la prosa de `GET /mine/products` | `T-05` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-07` | `ActivateMyProductIT`: `CA-MV-275` a `CA-MV-283` | `T-06` | `CA-MV-278` con el superadministrador | Pendiente |
| `T-08` | `PermissionIT`, `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md`, `security.md` | `T-07` | | Pendiente |

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

---

## 4. Bloqueos

Ninguno. **Se apila sobre `feature/pagos-y-saldos`**, que cambió la confirmación (el pago como intento) y aún no está en `develop`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los nueve criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
