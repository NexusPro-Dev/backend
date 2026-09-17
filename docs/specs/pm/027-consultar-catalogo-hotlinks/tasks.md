# TASKS — `RF-PM-027` Consultar el catálogo de hotlinks

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-027` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 15-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 15-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |
| Enmendadas | 15-09-2026 — `T-11` por el **alcance de cuatro valores** |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `ProductQueryRepository.findHotlinkCatalog()` y su sentencia en `JpaProductQueryRepository`: el `SELECT` de la oferta —membresías, moneda, `rating`, **sin `purchase_price`**— con `WHERE deleted_at IS NULL AND status = 'ACTIVO' AND scope = 'HOTLINKS'` y el mismo `ORDER BY` | — | `CA-PM-340`, `CA-PM-343` | **Hecha el 15-09-2026** |
| `T-02` | `application/HotlinkCatalogResponse(upgrades, services)` con `OfferResponse.Offered`; **sin `currentMembership`** | — | El contrato declara la forma sin ese campo | **Hecha el 15-09-2026** |
| `T-03` | `domain/service/GetHotlinkCatalogService.catalog()`: sentencia, conversión en bloque, separación por tipo sin reordenar; `readOnly` | `T-01`, `T-02` | `CA-PM-341`, `CA-PM-344`, `CA-PM-347` | **Hecha el 15-09-2026** |
| `T-04` | `ProductController`: `GET /api/v1/products/hotlinks`, `@PreAuthorize("hasAuthority('products:hotlink')")`, con la prosa de la `@Operation` (no mira la membresía; el enlace se compone con `username` y `code`; los paquetes con `RF-PM-026`) | `T-03` | `EndpointPermissionsIT` reconoce la ruta con su permiso; `OpenApiContractIT` regenera | **Hecha el 15-09-2026** |
| `T-05` | **LA PRUEBA DEL CONJUNTO** (`HotlinkCatalogIT`): `TIENDA` y `HOTLINKS`, activo e inactivo, un retirado — solo los activos de `HOTLINKS`, en las dos listas y en el orden de la oferta | `T-04` | `CA-PM-340`, `CA-PM-343` | **Hecha el 15-09-2026** |
| `T-06` | Prueba de «sin membresía»: un actor en `ORO` ve el `BECA → ORO`; la respuesta no trae `currentMembership` | `T-04` | `CA-PM-341` | **Hecha el 15-09-2026** |
| `T-07` | Prueba de la forma: con un costo declarado, el cuerpo no trae `purchasePrice` ni el importe; sí `price`, `exchange`, `videoUrl`, `coverImageUrl` y `rating` | `T-04` | `CA-PM-342` | **Hecha el 15-09-2026** |
| `T-08` | Pruebas de permiso y ruta: vacío → `200` con listas vacías; sin permiso, con `products:sale` y con `products:read` → `403`; sin token → `401`; `/hotlinks` no cae en `/{id}` | `T-04` | `CA-PM-344`, `CA-PM-345`, `CA-PM-346` | **Hecha el 15-09-2026** |
| `T-09` | Prueba de sentencias: con tres y con ocho productos, el mismo número | `T-04` | `CA-PM-347` | **Hecha el 15-09-2026** |
| `T-10` | Actualizar `requirements/pm.md` (§4, §6.1, ficha, §9), la matriz de `docs/requirements.md`, `security.md` y `docs/api/index.md` | `T-08` | Las filas reflejan el estado | **Hecha el 15-09-2026** |
| `T-11` | **`HOTLINK` o `AMBOS`** (`RN-PM-021`): `findHotlinkCatalog` con `p.scope IN ('HOTLINK','AMBOS')`; la prosa de la `@Operation` lo dice | `T-04`, `RF-PM-001` `T-41` | `CA-PM-353` en `HotlinkCatalogIT` | **Hecha el 15-09-2026** |

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04`, y **`T-05` y `T-06` inmediatamente después**: el conjunto, y que la membresía de quien llama no lo recorta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-340`, `CA-PM-343` | `T-01`, `T-05` |
| `CA-PM-341` | `T-03`, `T-06` |
| `CA-PM-342` | `T-07` |
| `CA-PM-344`, `CA-PM-345`, `CA-PM-346` | `T-08` |
| `CA-PM-347` | `T-03`, `T-09` |
| `CA-PM-353` | `T-11` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno: sin migración, sin permiso nuevo, sin dependencia de otro módulo | — | — | — |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] `CA-PM-341` prueba con un actor cuya membresía **no** coincide con el origen de un upgrade publicable, y lo ve.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
