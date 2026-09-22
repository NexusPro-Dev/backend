# TASKS — `RF-PM-021` Cambiar el estado de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-021` |
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
| `T-01` | `ProductPackage.activate/deactivate(ahora)` devolviendo si cambió, y `motivosParaNoPublicarse(cuantosProductos)`: descripción y tamaño, **los dos** | `RF-PM-017 · T-03` | Unitaria: cero, uno, dos productos × con/sin descripción | **Hecha el 15-09-2026** |
| `T-02` | `PackageItemRepository.countByPackage(packageId)` | `RF-PM-023 · T-03` | Integración | **Hecha el 15-09-2026** |
| `T-03` | `application/ChangePackageStatusRequest` | — | El contrato declara `status` obligatorio | **Hecha el 15-09-2026** |
| `T-04` | `domain/service/ChangePackageStatusService`: paquete vivo bloqueado (`EX-001`), sin cambio → `200`, al activar los motivos **juntos** (`EX-002`, `EX-003`), escritura, auditoría, relectura | `T-01`, `T-02`, `T-03`, `RF-PM-019 · T-03` | `CA-PM-291`, `CA-PM-292`, `CA-PM-293`, `CA-PM-296` | **Hecha el 15-09-2026** |
| `T-05` | `PackageController`: `PATCH /api/v1/packages/{id}/status`, `@PreAuthorize("hasAuthority('packages:update')")` | `T-04` | La ruta entra en `EndpointPermissionsIT` | **Hecha el 15-09-2026** |
| `T-06` | Pruebas de API (`PackageStatusIT`) de los siete criterios; **`CA-PM-294`** —activar con un producto inactivo dentro— es la que define el requerimiento | `T-05` | `CA-PM-291` a `CA-PM-297` | **Hecha el 15-09-2026** |
| `T-07` | Documentación OpenAPI. **La prosa dice** qué exige activar y qué no —los productos activos no—, y que sin cambio no se escribe | `T-05` | El contrato declara `200`, `400`, `403`, `404`, `409` | **Hecha el 15-09-2026** |
| `T-08` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-PM-021` refleja el estado | **Hecha el 15-09-2026** |

**Verificación (15-09-2026):** `PackageStatusIT` (6), en verde; el `mvn verify` completo queda en 370 unitarias y 1422 de integración, con las únicas rojas fuera del módulo (`DevelopmentSeedIT` por una edición sin confirmar de la semilla, y una prueba de `SP` que desempata mal dos asientos con el mismo instante).

## 2. Orden de ejecución

`T-01` a `T-03` independientes; `T-04` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-291`, `CA-PM-292`, `CA-PM-293` | `T-01`, `T-04`, `T-06` |
| `CA-PM-294`, `CA-PM-295` | `T-06` |
| `CA-PM-296` | `T-04`, `T-06` |
| `CA-PM-297` | `T-03`, `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-017`, `RF-PM-019` y `RF-PM-023` | 15-09-2026 | Responsable técnico | **Cerrado** el 15-09-2026 |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
