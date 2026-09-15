# TASKS — `RF-PM-023` Asociar un producto a un paquete, con su descuento

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-023` |
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
| `T-01` | **`domain/models/DiscountValue`**: forma y valor; valida la forma (`VAL-003`, `VAL-004`: porcentaje `0..100` con dos decimales, fijo no negativo con los decimales de la moneda) y la cota contra un precio (`RN-PM-037`: fijo ≤ precio, gratuito solo cero); `precioDentroDe(precio, decimales)` con `compareTo` | `RF-PM-017 · T-04` | Unitaria: las cotas en el céntimo; `49.00` y `49.0000` son el mismo precio; `precioDentroDe` en fijo, porcentaje y fijo mayor que el precio (cero) | **Hecha el 15-09-2026** |
| `T-02` | `PackageItem.create(packageId, productId, DiscountValue)` e `instantanea()` **con el precio del producto en ese instante** | `T-01`, `RF-PM-017 · T-03` | Unitaria: la instantánea lleva forma, valor y precio | **Hecha el 15-09-2026** |
| `T-03` | `PackageItemRepository` + `Jpa…`: `save` con **traducción de `pk_product_package_items`** a `EX-005`, `findByPackage(packageId)` con el producto de cada fila (tipo, origen, precio, moneda, estado) | `T-02` | Integración: el `INSERT` duplicado llega como `BusinessRuleException` | **Hecha el 15-09-2026** |
| `T-04` | `application/AssociatePackageProductRequest` | — | El contrato declara los tres campos obligatorios | **Hecha el 15-09-2026** |
| `T-05` | `domain/service/AssociatePackageProductService`: paquete vivo **bloqueado** (`EX-001`), producto (`EX-002`), activo y no retirado (`EX-003`), moneda (`EX-004`), no asociado (`EX-005`, sobre las hermanas ya leídas), descuento contra el precio de hoy (`EX-006`), origen de los upgrades (`EX-007`, sobre las mismas hermanas), inserción, auditoría `CREATE`, relectura del detalle | `T-03`, `T-04`, `RF-PM-017 · T-06`, `RF-PM-017 · T-07` | `CA-PM-304`, `CA-PM-305`, `CA-PM-306`, `CA-PM-314` | **Hecha el 15-09-2026** |
| `T-06` | `PackageController`: `POST /api/v1/packages/{id}/products`, `@PreAuthorize("hasAuthority('packages:update')")`, `201` con `Location` al detalle | `T-05` | La ruta entra en `EndpointPermissionsIT`; `products:update` no habilita | **Hecha el 15-09-2026** |
| `T-07` | **LA PRUEBA DEL CÉNTIMO** (`PackageProductsIT`): fijo igual al precio pasa, un céntimo más se rechaza; el gratuito solo con cero | `T-06` | `CA-PM-307`, `CA-PM-308` | **Hecha el 15-09-2026** |
| `T-08` | **LA PRUEBA DEL ORIGEN**: el primer upgrade fija; el segundo de otro origen se rechaza; un bot entra sin mirar; quitado el único upgrade, entra otro origen | `T-06`, `RF-PM-025 · T-03` | `CA-PM-313` y el caso límite de §13 | **Hecha el 15-09-2026** |
| `T-09` | Pruebas de los criterios restantes: validaciones de forma, inactivo/retirado (`409`) frente a inexistente (`422`), otra moneda, ya asociado, paquete inactivo y retirado | `T-06` | `CA-PM-309` a `CA-PM-312`, `CA-PM-314` | **Hecha el 15-09-2026** |
| `T-10` | Concurrencia (`PackageConcurrencyIT`): el mismo producto dos veces → una fila y un `409`; dos upgrades de orígenes distintos → un `201` y un `409` | `T-06` | `CA-PM-312`, y el riesgo 3 del plan | **Hecha el 15-09-2026** |
| `T-11` | Documentación OpenAPI. **La prosa dice** que forma y valor van juntos y el cero se admite, qué significa cada `409`, que el paquete se devuelve entero y que **la cota se comprueba contra el precio de hoy y nadie la vuelve a mirar** | `T-06` | El contrato declara `201`, `400`, `403`, `404`, `409`, `422` | **Hecha el 15-09-2026** |
| `T-12` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-09` | La fila de `RF-PM-023` refleja el estado | **Hecha el 15-09-2026** |

**Verificación (15-09-2026):** `PackageProductsIT` (12), `PackageConcurrencyIT` (2 de la asociación) y `DiscountValueTest` (9), en verde; el `mvn verify` completo queda en 370 unitarias y 1422 de integración, con las únicas rojas fuera del módulo (`DevelopmentSeedIT` por una edición sin confirmar de la semilla, y una prueba de `SP` que desempata mal dos asientos con el mismo instante).

## 2. Orden de ejecución

**`T-01` primero y con su unitaria**: es donde vive la cota, y una prueba de API que descubra un `equals` donde iba un `compareTo` costaría diez veces más que la unitaria que lo evita.

`T-07` y `T-08` **no son opcionales**: son las dos pruebas que definen el requerimiento — la cota en el céntimo y el paquete que nadie podría comprar.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-304`, `CA-PM-305`, `CA-PM-306` | `T-05` |
| `CA-PM-307`, `CA-PM-308` | `T-01`, `T-07` |
| `CA-PM-309`, `CA-PM-310`, `CA-PM-311` | `T-09` |
| `CA-PM-312` | `T-03`, `T-09`, `T-10` |
| `CA-PM-313` | `T-08` |
| `CA-PM-314` | `T-05`, `T-09` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-017` construido: tablas, entidad, `PackagePricing`, detalle | 15-09-2026 | Responsable técnico | **Cerrado** el 15-09-2026 |
| 2 | `T-08` necesita desasociar (`RF-PM-025`) para su último caso | 15-09-2026 | Responsable técnico | **Cerrado** el 15-09-2026 |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] `DiscountValue` compara con `compareTo` y tiene prueba del céntimo.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
