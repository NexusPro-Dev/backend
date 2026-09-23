# TASKS — `RF-MV-016` Asignar los vendedores de una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-016` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 23-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/estados-de-comision` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V36`**: `movement_type_statuses` y su siembra, `movements.type_status_id` rellena y `NOT NULL`, la clave compuesta, el índice, y `movements:assign-sellers` con sus guardas | — | Un movimiento con el estado de otro tipo lo rechaza el esquema; las guardas cuadran 134 / 134 / 128 | Pendiente |
| `T-02` | Los fixtures que insertan ventas a mano ganan `type_status_id`; las cuatro suites que cuentan el catálogo pasan a 134 | `T-01` | La suite de `MV` sigue en verde sin tocar una aserción de negocio | Pendiente |
| `T-03` | `ClientCatalog.sellersOf` y su implementación en `PublishedUserCatalog` | — | Devuelve todos los vínculos, de registro y de hotlink | Pendiente |
| `T-04` | `SaleTypeStatus`; `MovementLine` admite vendedor nulo; `Movement` lleva el estado del tipo y lo pone en la instantánea | — | Unitarias de línea y de venta | Pendiente |
| `T-05` | `SaleAttribution` (`RN-MV-034`) y su uso en `RegisterSaleService` y `BuyPackageService`; el `INSERT` escribe `type_status_id` | `T-01`, `T-03`, `T-04` | `SaleAttributionTest`: uno, varios, ninguno, enlace | Pendiente |
| `T-06` | `typeStatus` en `SaleResponse`, `PurchaseResponse` y `MovementResponse`; las lecturas lo proyectan; `seller` de la línea corrige su prosa | `T-05` | El registro y el detalle lo traen | Pendiente |
| `T-07` | El filtro `typeStatus` en `RF-MV-006` (`VAL-006`) y `RF-MV-015` (`VAL-005`); las uniones con `seller_id` revisadas | `T-06` | Filtra, y un código inexistente es `400` | Pendiente |
| `T-08` | `AssignSellersRequest` y `AssignSellersService`: forma, bloqueo, comprobaciones, escritura, transición, auditoría | `T-05` | Todo rechazo deja la venta intacta | Pendiente |
| `T-09` | `MovementController`: `POST /{id}/seller-assignments` con `@PreAuthorize('movements:assign-sellers')`, documentado; entra en `PERMISO_DE_CADA_OPERACION` | `T-08` | `EndpointPermissionsIT` en verde | Pendiente |
| `T-10` | `SellerAssignmentIT` y las ampliaciones de §2 | `T-07`, `T-09` | `CA-MV-143` a `CA-MV-162` | Pendiente |
| `T-11` | `mvn verify` completo; enmiendas de `RF-MV-006` y `RF-MV-015` en sus `spec.md` | `T-10` | Verde | Pendiente |
| `T-12` | Contrato regenerado **al integrar**, sobre la rama que tenga también las rutas de Equipos (PR #97) | `T-11` | Las rutas de Equipos siguen en el contrato | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-143`, `CA-MV-144`, `CA-MV-146` | `T-05`, `T-10` |
| `CA-MV-145` | `T-05`, `T-10` |
| `CA-MV-147` | `T-05`, `T-10` |
| `CA-MV-148` | `T-01`, `T-10` |
| `CA-MV-149` a `CA-MV-157` | `T-08`, `T-10` |
| `CA-MV-158` | `T-09`, `T-10` |
| `CA-MV-159` | `T-08`, `T-10` |
| `CA-MV-160` | `T-05`, `T-10` |
| `CA-MV-161`, `CA-MV-162` | `T-06`, `T-07`, `T-10` |

---

## 3. Desviaciones respecto del plan

Ninguna todavía.

---

## 4. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los veinte criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado **al integrar**, con la prosa releída.
- [x] `requirements/mv.md`, `modelo-datos.md`, `security.md` y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
