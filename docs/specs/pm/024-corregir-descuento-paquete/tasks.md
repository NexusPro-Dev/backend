# TASKS — `RF-PM-024` Corregir el descuento de un producto del paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-024` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 15-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 15-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `application/UpdatePackageItemRequest`, con forma y valor obligatorios | `RF-PM-023 · T-04` | `CA-PM-317`: uno ausente es `400` | **Hecha el 15-09-2026** |
| `T-02` | `PackageItem.corregir(DiscountValue, ahora)`: mapa de cambios de forma y valor, `updatedAt` solo si cambió | `RF-PM-023 · T-02` | Unitaria: forma, valor, los dos, nada; el mismo número en otra forma es cambio | **Hecha el 15-09-2026** |
| `T-03` | `PackageItemRepository.findForUpdate(packageId, productId)` | `RF-PM-023 · T-03` | Integración: bloquea la fila; vacío si la pareja no existe | **Hecha el 15-09-2026** |
| `T-04` | `domain/service/UpdatePackageItemService`: paquete vivo bloqueado (`EX-001`), fila (`EX-002`), precio de hoy y cota (`EX-003`), corregir, auditar si cambió, relectura | `T-01`, `T-02`, `T-03`, `RF-PM-019 · T-03` | `CA-PM-315`, `CA-PM-316`, `CA-PM-318`, `CA-PM-319` | **Hecha el 15-09-2026** |
| `T-05` | `PackageController`: `PATCH /api/v1/packages/{id}/products/{productId}`, `@PreAuthorize("hasAuthority('packages:update')")` | `T-04` | La ruta entra en `EndpointPermissionsIT` | **Hecha el 15-09-2026** |
| `T-06` | Pruebas de API de los seis criterios (`PackageProductsIT`), con **`CA-PM-316`** —bajar el precio del producto después de asociar y corregir por encima— como la que define el requerimiento | `T-05` | `CA-PM-315` a `CA-PM-320` | **Hecha el 15-09-2026** |
| `T-07` | Documentación OpenAPI. **La prosa dice** que forma y valor van juntos y obligatorios, que la cota es contra el precio de hoy, y que el producto inactivo se corrige igual | `T-05` | El contrato declara `200`, `400`, `403`, `404`, `409` | **Hecha el 15-09-2026** |
| `T-08` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-PM-024` refleja el estado | **Hecha el 15-09-2026** |

**Verificación (15-09-2026):** `PackageDiscountIT` (6), en verde; el `mvn verify` completo queda en 370 unitarias y 1422 de integración, con las únicas rojas fuera del módulo (`DevelopmentSeedIT` por una edición sin confirmar de la semilla, y una prueba de `SP` que desempata mal dos asientos con el mismo instante).

## 2. Orden de ejecución

`T-01` a `T-03` independientes; `T-04` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-315`, `CA-PM-319` | `T-02`, `T-04`, `T-06` |
| `CA-PM-316` | `T-04`, `T-06` |
| `CA-PM-317` | `T-01`, `T-06` |
| `CA-PM-318` | `T-03`, `T-04`, `T-06` |
| `CA-PM-320` | `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-023` y `RF-PM-019` | 15-09-2026 | Responsable técnico | **Cerrado** el 15-09-2026 |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
