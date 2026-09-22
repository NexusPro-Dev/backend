# TASKS — `RF-PM-022` Eliminar paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-022` |
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
| `T-01` | `ProductPackage.delete(ahora)`, `estaRetirado()` e `instantanea(items)` con los productos anidados —producto, código, forma, valor y precio en ese instante— | `RF-PM-017 · T-03`, `RF-PM-023 · T-02` | Unitaria: la instantánea lleva las filas; `delete` no toca `status` | **Hecha el 15-09-2026** |
| `T-02` | `application/DeletePackageRequest` y `DeletionReason` reutilizado | — | El motivo vacío lanza `VAL-002` antes de cualquier consulta | **Hecha el 15-09-2026** |
| `T-03` | `domain/service/DeletePackageService`: motivo, paquete en cualquier estado bloqueado (`EX-001`/`EX-002`), filas, instantánea, marca, `DeletionEvent` `LOGICAL` | `T-01`, `T-02` | `CA-PM-298`, `CA-PM-300`, `CA-PM-301` | **Hecha el 15-09-2026** |
| `T-04` | `PackageController`: `POST /api/v1/packages/{id}/deletion`, `@PreAuthorize("hasAuthority('packages:delete')")`, `204` | `T-03` | La ruta entra en `EndpointPermissionsIT` | **Hecha el 15-09-2026** |
| `T-05` | Pruebas de API (`PackageDeletionIT`) de los seis criterios, con `CA-PM-301` como la que define el requerimiento; concurrencia de dos retiros en `PackageConcurrencyIT` | `T-04`, `RF-PM-018 · T-06`, `RF-PM-007` enmendado | `CA-PM-298` a `CA-PM-303` | **Hecha el 15-09-2026** |
| `T-06` | Documentación OpenAPI. **La prosa dice** que exige motivo, que no exige desactivar, que las filas de asociación permanecen y que retirar dos veces es `409` | `T-04` | El contrato declara `204`, `400`, `403`, `404`, `409` | **Hecha el 15-09-2026** |
| `T-07` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-05` | La fila de `RF-PM-022` refleja el estado | **Hecha el 15-09-2026** |

**Verificación (15-09-2026):** `PackageDeletionIT` (6), en verde; el `mvn verify` completo queda en 370 unitarias y 1422 de integración, con las únicas rojas fuera del módulo (`DevelopmentSeedIT` por una edición sin confirmar de la semilla, y una prueba de `SP` que desempata mal dos asientos con el mismo instante).

## 2. Orden de ejecución

`T-01` y `T-02` independientes; `T-03` las junta. `T-05` necesita la lista y la oferta para `CA-PM-302`, y por eso este requerimiento va después de ellas en el orden del módulo.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-298`, `CA-PM-300`, `CA-PM-301` | `T-01`, `T-03`, `T-05` |
| `CA-PM-299` | `T-02`, `T-05` |
| `CA-PM-302`, `CA-PM-303` | `T-05` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-017`, `RF-PM-023`, `RF-PM-018` y la oferta enmendada | 15-09-2026 | Responsable técnico | **Cerrado** el 15-09-2026 |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
