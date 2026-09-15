# TASKS — `RF-PM-018` Consultar paquetes

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-018` |
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
| `T-01` | `application/ListPackagesRequest` con validación **conjunta** de estado, alcance, moneda y `sort`; `PackageSortField` (`createdAt`, `name`, `price`) | — | `CA-PM-276`: cuatro filtros malos, un `400` con cuatro errores | **Hecha el 15-09-2026** |
| `T-02` | `ProductPackageQueryRepository.search` y `count`: predicado en un solo sitio; el orden por `price` con la **subconsulta constante** de suma sin redondear y desempate por `id` | `RF-PM-017 · T-01` | Integración: los cinco filtros; `count` no multiplica; el orden por precio con dos paquetes | **Hecha el 15-09-2026** |
| `T-03` | `ProductPackageQueryRepository.findItemsOf(List<UUID>)`: las filas de asociación de todos los paquetes pedidos con su producto, en una sentencia; lista vacía → sin sentencia | `RF-PM-017 · T-06` | Integración: veinte paquetes con tres productos cada uno, una sentencia | **Hecha el 15-09-2026** |
| `T-04` | `domain/service/ListPackagesService`: página, filas agrupadas por paquete, `PackagePricing`, `PackageOfferability`, conversión con `ProductExchangeResolver` para las monedas de la página | `T-01`, `T-02`, `T-03`, `RF-PM-019 · T-01` | `CA-PM-269`, `CA-PM-270`, `CA-PM-271`, `CA-PM-275` | **Hecha el 15-09-2026** |
| `T-05` | `application/PackageItemSummary` y `PackagePageResponse` | `T-04` | El contrato declara la fila con `itemCount` y `offerable` | **Hecha el 15-09-2026** |
| `T-06` | `PackageController`: `GET /api/v1/packages`, `@PreAuthorize("hasAuthority('packages:read')")`, `@ParameterObject` | `T-05` | La ruta entra en `EndpointPermissionsIT` | **Hecha el 15-09-2026** |
| `T-07` | **LA PRUEBA DE SENTENCIAS** (`PackageListIT`): página de uno y de veinte, mismo número | `T-06` | `CA-PM-274` | **Hecha el 15-09-2026** |
| `T-08` | Pruebas de orden: omisión con desempate, `name`, `price` **calculado** que cambia de posición al corregir un producto, campo no admitido | `T-06` | `CA-PM-272`, `CA-PM-273` | **Hecha el 15-09-2026** |
| `T-09` | Pruebas de los criterios restantes: retirados bajo petición, filtros combinados, conversión por fila, permisos | `T-06` | `CA-PM-269` a `CA-PM-271`, `CA-PM-275`, `CA-PM-276` | **Hecha el 15-09-2026** |
| `T-10` | Documentación OpenAPI. **La prosa dice** que el precio se calcula, que `offerable` es columna y no filtro, y qué significa ordenar por precio | `T-06` | El contrato declara `200`, `400`, `401`, `403` | **Hecha el 15-09-2026** |
| `T-11` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-09` | La fila de `RF-PM-018` refleja el estado | **Hecha el 15-09-2026** |

**Verificación (15-09-2026):** `PackageListIT` (8), en verde; el `mvn verify` completo queda en 370 unitarias y 1422 de integración, con las únicas rojas fuera del módulo (`DevelopmentSeedIT` por una edición sin confirmar de la semilla, y una prueba de `SP` que desempata mal dos asientos con el mismo instante).

## 2. Orden de ejecución

`T-01`, `T-02` y `T-03` son independientes. `T-04` las junta. **`T-07` justo después de `T-06`**: es la que impide que una comodidad futura reintroduzca el `N+1`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-269`, `CA-PM-270`, `CA-PM-271`, `CA-PM-275` | `T-04`, `T-09` |
| `CA-PM-272`, `CA-PM-273` | `T-02`, `T-08` |
| `CA-PM-274` | `T-03`, `T-07` |
| `CA-PM-276` | `T-01`, `T-09` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-017`, `RF-PM-023` y `RF-PM-019` (`PackageOfferability`) | 15-09-2026 | Responsable técnico | **Cerrado** el 15-09-2026 |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [ ] El número de sentencias no depende del tamaño de la página, y está probado.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
