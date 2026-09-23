# TASKS — `RF-MV-016` Asignar los vendedores de una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-016` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 23-09-2026 |
| Estado | **En revisión** — `T-01` a `T-10` `Hecha` el 23-09-2026; `T-11` y `T-12` a medias (ver §3) |
| Issue | [#103](https://github.com/NexusPro-Dev/backend/issues/103) |
| Rama | `feature/estados-de-comision` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V36`**: `movement_type_statuses` y su siembra, `movements.type_status_id` rellena y `NOT NULL`, la clave compuesta, el índice, y `movements:assign-sellers` con sus guardas | — | Un movimiento con el estado de otro tipo lo rechaza el esquema; las guardas cuadran 134 / 134 / 128 | **Hecha** — 23-09-2026 |
| `T-02` | Los fixtures que insertan ventas a mano ganan `type_status_id`; las cuatro suites que cuentan el catálogo pasan a 134 | `T-01` | La suite de `MV` sigue en verde sin tocar una aserción de negocio | **Hecha** — 23-09-2026 |
| `T-03` | `ClientCatalog.sellersOf` y su implementación en `PublishedUserCatalog` | — | Devuelve todos los vínculos, de registro y de hotlink | **Hecha** — 23-09-2026 |
| `T-04` | `SaleTypeStatus`; `MovementLine` admite vendedor nulo; `Movement` lleva el estado del tipo y lo pone en la instantánea | — | Unitarias de línea y de venta | **Hecha** — 23-09-2026 |
| `T-05` | `SaleAttribution` (`RN-MV-034`) y su uso en `RegisterSaleService` y `BuyPackageService`; el `INSERT` escribe `type_status_id` | `T-01`, `T-03`, `T-04` | `SaleAttributionTest`: uno, varios, ninguno, enlace | **Hecha** — 23-09-2026 |
| `T-06` | `typeStatus` en `SaleResponse`, `PurchaseResponse` y `MovementResponse`; las lecturas lo proyectan; `seller` de la línea corrige su prosa | `T-05` | El registro y el detalle lo traen | **Hecha** — 23-09-2026 |
| `T-07` | El filtro `typeStatus` en `RF-MV-006` (`VAL-006`) y `RF-MV-015` (`VAL-005`); las uniones con `seller_id` revisadas | `T-06` | Filtra, y un código inexistente es `400` | **Hecha** — 23-09-2026 |
| `T-08` | `AssignSellersRequest` y `AssignSellersService`: forma, bloqueo, comprobaciones, escritura, transición, auditoría | `T-05` | Todo rechazo deja la venta intacta | **Hecha** — 23-09-2026 |
| `T-09` | `MovementController`: `POST /{id}/seller-assignments` con `@PreAuthorize('movements:assign-sellers')`, documentado; entra en `PERMISO_DE_CADA_OPERACION` | `T-08` | `EndpointPermissionsIT` en verde | **Hecha** — 23-09-2026 |
| `T-10` | `SellerAssignmentIT` y las ampliaciones de §2 | `T-07`, `T-09` | `CA-MV-143` a `CA-MV-162` | **Hecha** — 23-09-2026 |
| `T-11` | `mvn verify` completo; enmiendas de `RF-MV-006` y `RF-MV-015` en sus `spec.md` | `T-10` | Verde | **En curso** — ver §3 |
| `T-12` | Contrato regenerado **al integrar**, sobre la rama que tenga también las rutas de Equipos (PR #97) | `T-11` | Las rutas de Equipos siguen en el contrato | **En curso** — ver §3 |

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

**La respuesta del registro fallaba con varios vendedores**, y lo encontró `SellerAssignmentIT`: `SaleResponse.de` buscaba el vendedor de la línea en un `Map.of`, que no admite la clave nula, y la venta por validar respondía `500`. Se corrigió antes de subir.

**`T-11`: se corrieron las 454 unitarias y las 21 suites de integración afectadas** —ventas, permisos, endpoints, alta por enlace y contrato—, todas en verde. **La suite completa no se ha corrido**: la primera corrida se cortó por falta de memoria del equipo, con otra sesión compilando a la vez. Queda para CI.

**`T-12`: el contrato se regeneró sobre esta rama** (102 → 103 rutas, sin perder ninguna), a petición del responsable del proyecto. Esta rama no tiene las rutas de Equipos del PR #97, de modo que **hay que regenerarlo otra vez al integrar**, sobre la rama que tenga todo el código.

**`V36` va antes que la `V37` de `RF-MV-017`** (sesión backend-57): aquella espera el catálogo en 134, que es donde lo deja esta.

---

## 4. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Contrato OpenAPI regenerado **al integrar**, con la prosa releída.
- [x] Los veinte criterios de aceptación con prueba.
- [x] `requirements/mv.md`, `modelo-datos.md`, `security.md` y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
