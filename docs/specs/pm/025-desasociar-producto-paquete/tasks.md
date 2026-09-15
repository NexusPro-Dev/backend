# TASKS — `RF-PM-025` Desasociar un producto de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-025` |
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
| `T-01` | `PackageItemRepository.delete(item)` — borrado físico de la fila | `RF-PM-023 · T-03` | Integración: la fila deja de existir; volver a asociar la misma pareja pasa | **Hecha el 15-09-2026** |
| `T-02` | `domain/service/DissociatePackageProductService`: paquete vivo bloqueado (`EX-001`), fila (`EX-002`), instantánea con precio de hoy, borrado, `DeletionEvent` `ASSOCIATION` sin motivo, relectura | `T-01`, `RF-PM-024 · T-03`, `RF-PM-019 · T-03` | `CA-PM-321`, `CA-PM-322`, `CA-PM-323` | **Hecha el 15-09-2026** |
| `T-03` | `PackageController`: `DELETE /api/v1/packages/{id}/products/{productId}`, sin `@RequestBody`, `@PreAuthorize("hasAuthority('packages:update')")` | `T-02` | La ruta entra en `EndpointPermissionsIT` | **Hecha el 15-09-2026** |
| `T-04` | Pruebas de API de los seis criterios (`PackageProductsIT`), con **`CA-PM-322`** —la instantánea lleva forma, valor y precio— como la que define el requerimiento | `T-03` | `CA-PM-321` a `CA-PM-326` | **Hecha el 15-09-2026** |
| `T-05` | Prueba de concurrencia (`PackageConcurrencyIT`): activar y desasociar a la vez sobre un paquete de dos | `T-03`, `RF-PM-021 · T-05` | Nunca activo con uno sin `offerable: false` | **Hecha el 15-09-2026** |
| `T-06` | Documentación OpenAPI. **La prosa dice** que no lleva cuerpo ni motivo, que responde `200` con el paquete porque su precio cambió, que el `404` no distingue «nunca estuvo» de «ya se quitó», y que dejar el paquete con menos de dos no lo desactiva | `T-03` | El contrato declara `200`, `403`, `404` y ningún esquema de petición | **Hecha el 15-09-2026** |
| `T-07` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-04` | La fila de `RF-PM-025` refleja el estado | **Hecha el 15-09-2026** |

**Verificación (15-09-2026):** `PackageDissociationIT` (5), en verde; el `mvn verify` completo queda en 370 unitarias y 1422 de integración, con las únicas rojas fuera del módulo (`DevelopmentSeedIT` por una edición sin confirmar de la semilla, y una prueba de `SP` que desempata mal dos asientos con el mismo instante).

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04`, `T-05` y `T-06` en paralelo → `T-07`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-321`, `CA-PM-322`, `CA-PM-323` | `T-01`, `T-02`, `T-04` |
| `CA-PM-324` | `T-02`, `T-04`, `T-05` |
| `CA-PM-325`, `CA-PM-326` | `T-04` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-023`, `RF-PM-024`, `RF-PM-021` y `RF-PM-019` | 15-09-2026 | Responsable técnico | **Cerrado** el 15-09-2026 |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
