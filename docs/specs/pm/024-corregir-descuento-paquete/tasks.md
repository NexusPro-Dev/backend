# TASKS — `RF-PM-024` Corregir el descuento de un producto del paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-024` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 15-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `application/UpdatePackageItemRequest`, con forma y valor obligatorios | `RF-PM-023 · T-04` | `CA-PM-317`: uno ausente es `400` | Pendiente |
| `T-02` | `PackageItem.corregir(DiscountValue, ahora)`: mapa de cambios de forma y valor, `updatedAt` solo si cambió | `RF-PM-023 · T-02` | Unitaria: forma, valor, los dos, nada; el mismo número en otra forma es cambio | Pendiente |
| `T-03` | `PackageItemRepository.findForUpdate(packageId, productId)` | `RF-PM-023 · T-03` | Integración: bloquea la fila; vacío si la pareja no existe | Pendiente |
| `T-04` | `domain/service/UpdatePackageItemService`: paquete vivo bloqueado (`EX-001`), fila (`EX-002`), precio de hoy y cota (`EX-003`), corregir, auditar si cambió, relectura | `T-01`, `T-02`, `T-03`, `RF-PM-019 · T-03` | `CA-PM-315`, `CA-PM-316`, `CA-PM-318`, `CA-PM-319` | Pendiente |
| `T-05` | `PackageController`: `PATCH /api/v1/packages/{id}/products/{productId}`, `@PreAuthorize("hasAuthority('packages:update')")` | `T-04` | La ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-06` | Pruebas de API de los seis criterios (`PackageProductsIT`), con **`CA-PM-316`** —bajar el precio del producto después de asociar y corregir por encima— como la que define el requerimiento | `T-05` | `CA-PM-315` a `CA-PM-320` | Pendiente |
| `T-07` | Documentación OpenAPI. **La prosa dice** que forma y valor van juntos y obligatorios, que la cota es contra el precio de hoy, y que el producto inactivo se corrige igual | `T-05` | El contrato declara `200`, `400`, `403`, `404`, `409` | Pendiente |
| `T-08` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-PM-024` refleja el estado | Pendiente |

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
| 1 | Depende de `RF-PM-023` y `RF-PM-019` | 15-09-2026 | Responsable técnico | **Abierto** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
