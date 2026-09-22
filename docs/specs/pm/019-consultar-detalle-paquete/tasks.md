# TASKS — `RF-PM-019` Consultar el detalle de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-019` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 15-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 15-09-2026; **reabierta el 16-09-2026** por la vigencia (`T-13`); queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`domain/models/PackageOfferability`**: `(boolean, motivo)` en el orden fijo de la spec —menos de dos, sin descripción, inactivo, retirado, producto no ofrecible nombrado— | `RF-PM-017 · T-03` | Unitaria: un caso por motivo, y uno con **dos** motivos a la vez que devuelve el primero del orden | **Hecha el 15-09-2026** |
| `T-02` | `ProductPackageQueryRepository.findDetail(id)` (de `RF-PM-017 · T-06`) devuelve además `source_membership_id`, `status` y `deleted_at` del producto, que la ofrecibilidad y `RN-PM-044` necesitan | `RF-PM-017 · T-06` | Integración: un paquete con un producto retirado dentro lo devuelve marcado | **Hecha el 15-09-2026** |
| `T-03` | `domain/service/GetPackageService`: detalle, `PackagePricing`, conversión sobre `price` con `ProductExchangeResolver`, `PackageOfferability`, y el motivo del retiro por la lectura estrecha de `shared/audit` | `T-01`, `T-02` | `CA-PM-277`, `CA-PM-280`, `CA-PM-281`, `CA-PM-283` | **Hecha el 15-09-2026** |
| `T-04` | `PackageDetailResponse` gana `deletedAt`/`deletionReason` con `NON_NULL`, y `PackageItemResponse` lleva `product.purchasePrice` presente y nulo | `RF-PM-017 · T-07` | El contrato los declara | **Hecha el 15-09-2026** |
| `T-05` | `PackageController`: `GET /api/v1/packages/{id}`, `@PreAuthorize("hasAuthority('packages:read')")` | `T-03`, `T-04` | La ruta entra en `EndpointPermissionsIT` con su permiso | **Hecha el 15-09-2026** |
| `T-06` | **LA PRUEBA DE QUE EL PRECIO SE CALCULA** (`PackageDetailIT`): corregir el precio de un producto por `RF-PM-004` cambia el detalle del paquete | `T-05` | `CA-PM-278` | **Hecha el 15-09-2026** |
| `T-07` | Pruebas de redondeo y del fijo que supera el precio: tres porcentajes que dejan medio céntimo cuadran línea a línea; el fijo alto cuenta cero | `T-05` | `CA-PM-277`, `CA-PM-279` | **Hecha el 15-09-2026** |
| `T-08` | Pruebas de ofrecibilidad y de administración: inactivo y retirado dentro, retirado el paquete con su motivo, inexistente `404`, número de sentencias | `T-05` | `CA-PM-282`, `CA-PM-284` | **Hecha el 15-09-2026** |
| `T-09` | Documentación OpenAPI. **La prosa dice** que el precio se calcula en cada lectura, qué significa `offerable` y su orden de motivos, que `priceInPackage` es lo que una venta copiará, y que el `purchasePrice` viaja porque es administración | `T-05` | El contrato declara `200`, `400`, `401`, `403`, `404` | **Hecha el 15-09-2026** |
| `T-10` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-08` | La fila de `RF-PM-019` refleja el estado | **Hecha el 15-09-2026** |
| `T-11` | **Enmienda a `RF-PM-007`** (`plan.md` §8.1): `ProductPackageQueryRepository.findOfferable()` en dos sentencias; en `GetOfferService`, filtro por `PackageOfferability` y por **origen** (`RN-PM-044`), cuenta con `PackagePricing`, y las monedas de los paquetes **en la misma sentencia de tasas**; `OfferResponse.packages` envuelta, con el producto en la forma de `OfferItem`, `discount` y `priceInPackage` | `T-01`, `T-03`, `RF-PM-018 · T-02` | `CA-PM-335`, `CA-PM-337` | **Hecha el 15-09-2026** |
| `T-12` | Pruebas de la oferta con paquetes (`ProductOfferIT`): la cuenta y la ausencia de `purchasePrice`; el paquete que desaparece con un producto inactivo dentro y vuelve al reactivarlo; el origen con el actor en cada membresía y sin ninguna; el número de sentencias, que sube **en dos** | `T-11` | `CA-PM-335` a `CA-PM-338` | **Hecha el 15-09-2026** |
| `T-13` | **Enmienda del 16-09-2026** (`spec.md` v0.4.0, `RN-PM-047`): `PackageOfferability.decidir` recibe `hoy`, `validFrom` y `validTo` y estrena los dos motivos con fecha, en su sitio del orden; `findDetail` selecciona las dos columnas y `PackageRow` las lleva; `GetPackageService` pasa hoy con un `Clock` UTC; `PackageDetailResponse` publica `validFrom` y `validTo`; la prosa de la `@Operation` añade el motivo al orden | `T-01`, `T-03`, `RF-PM-017 · T-13` | `PackageOfferabilityTest` con hoy fijo; `PackageDetailIT`: `CA-PM-377` | **Hecha el 16-09-2026** |

**Verificación (15-09-2026):** `PackageDetailIT` (10), `PackageOfferabilityTest` (7) y `PackageOfferIT` (4, la enmienda de `RF-PM-007`), en verde; el `mvn verify` completo queda en 370 unitarias y 1422 de integración, con las únicas rojas fuera del módulo (`DevelopmentSeedIT` por una edición sin confirmar de la semilla, y una prueba de `SP` que desempata mal dos asientos con el mismo instante).

**Verificación de la enmienda (16-09-2026):** `PackageOfferabilityTest` (8) y `PackageDetailIT` (11), en verde.

## 2. Orden de ejecución

`T-01` primero, con su unitaria: el orden de los motivos es una regla y se prueba solo. `T-02` y `T-01` son independientes; `T-03` las junta. **`T-06` inmediatamente después de `T-05`**: es la prueba que define `RN-PM-036`. `T-11` y `T-12` cierran la tripleta: la oferta es el primer consumidor de `PackagePricing` y `PackageOfferability` fuera del detalle, y construirla aquí evita una tripleta de una sola tarea.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-277` | `T-03`, `T-07` |
| `CA-PM-278` | `T-06` |
| `CA-PM-279` | `T-07` |
| `CA-PM-280`, `CA-PM-281` | `T-01`, `T-03` |
| `CA-PM-282` | `T-02`, `T-08` |
| `CA-PM-283` | `T-03`, `T-04` |
| `CA-PM-284` | `T-08` |
| `CA-PM-335` a `CA-PM-338` (enmienda a `RF-PM-007`) | `T-11`, `T-12` |
| `CA-PM-377` | `T-13` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-017` y de `RF-PM-023` para tener paquetes con productos que leer | 15-09-2026 | Responsable técnico | **Cerrado** el 15-09-2026 |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [ ] `PackageOfferability` y `PackagePricing` son los únicos sitios que deciden lo suyo, y las seis lecturas los usan.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
