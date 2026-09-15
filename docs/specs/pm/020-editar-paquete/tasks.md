# TASKS — `RF-PM-020` Editar paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-020` |
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
| `T-01` | `application/UpdatePackageRequest` con `Patchable` en los tres corregibles y `Patchable<Object>` en `code` y `currencyId`; `informaAlgo()`, `traeInmutables()` | `RF-PM-017 · T-07` | Unitaria de deserialización: los tres estados por campo | Pendiente |
| `T-02` | `ProductPackage.update(name, description, scope, ahora)`: recorta, valida, rechaza el nulo en nombre y alcance, vacía la descripción, devuelve el mapa de cambios y avanza `updatedAt` solo si no está vacío | `RF-PM-017 · T-03` | Unitaria: cada campo, nulo de descripción, sin cambios | Pendiente |
| `T-03` | `ProductPackageRepository.existsAliveNameForOther(name, id)` | `RF-PM-017 · T-05` | Integración: el propio nombre no choca; el de otro vivo sí; el de un retirado no | Pendiente |
| `T-04` | `domain/service/UpdatePackageService`: inmutables (`VAL-004`) y «al menos uno» (`VAL-005`) antes de leer; paquete vivo con bloqueo (`EX-001`); nombre libre (`EX-002`); aplicar; auditar solo si cambió; relectura del detalle | `T-01`, `T-02`, `T-03`, `RF-PM-019 · T-03` | `CA-PM-285`, `CA-PM-288`, `CA-PM-290` | Pendiente |
| `T-05` | `PackageController`: `PATCH /api/v1/packages/{id}`, `@PreAuthorize("hasAuthority('packages:update')")` | `T-04` | La ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-06` | Pruebas de API (`PackageUpdateIT`) de los seis criterios, con `CA-PM-289` —vaciar la descripción de un activo lo deja `offerable: false`— como la que define el requerimiento | `T-05` | `CA-PM-285` a `CA-PM-290` | Pendiente |
| `T-07` | Documentación OpenAPI. **La prosa dice** qué se corrige y qué no, que el nulo vacía solo la descripción, y que vaciar la descripción de un activo lo deja sin poder ofrecerse | `T-05` | El contrato declara `200`, `400`, `403`, `404`, `409` | Pendiente |
| `T-08` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-PM-020` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01`, `T-02`, `T-03` independientes; `T-04` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-285`, `CA-PM-290` | `T-02`, `T-04`, `T-06` |
| `CA-PM-286`, `CA-PM-287` | `T-01`, `T-06` |
| `CA-PM-288` | `T-03`, `T-04`, `T-06` |
| `CA-PM-289` | `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-017` y `RF-PM-019` | 15-09-2026 | Responsable técnico | **Abierto** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
