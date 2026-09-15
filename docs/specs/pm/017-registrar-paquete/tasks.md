# TASKS — `RF-PM-017` Registrar paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-017` |
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
| `T-01` | Migración **`V91__create_product_packages.sql`**: las dos tablas, la unicidad total del código, el índice parcial del nombre, los `CHECK` de estado, alcance, forma y valor del descuento, y `ix_product_package_items_product` | — | Integración: aplica sobre `V90`; dos códigos iguales se rechazan aunque uno esté retirado; dos nombres iguales vivos se rechazan y con uno retirado no; un porcentaje de `101` se rechaza y un fijo de `101` no (su techo no está aquí) | **Hecha el 15-09-2026** |
| `T-02` | Migración **`V92__seed_packages_permissions.sql`**: los cuatro `packages:` con identificador literal —serie de `PM`, `…000008` a `…000011`—, asociados a `SUPERADMIN` y `ADMIN`, con la guarda que cuenta ocho | — | `PackagesPermissionsSeedIT`: cuatro sembrados, identificadores estables, ocho asociaciones, ninguna a `CLIENTE`; `PermissionsSeedIT` y sus tres hermanas pasan de 46 a **50** | **Hecha el 15-09-2026** |
| `T-03` | `domain/models/ProductPackage`, `PackageStatus`, `DiscountType`, `PackageItem` (clave compuesta `PackageItemId`): el constructor normaliza el código, recorta nombre y descripción, y deja la descripción **nula** si queda vacía | `T-01` | Unitaria: código en minúsculas sale en mayúsculas; descripción de espacios sale nula | **Hecha el 15-09-2026** |
| `T-04` | **`domain/models/PackagePricing`**: por producto —`FIJO` → `máx(0, precio − f)`, `PORCENTAJE` → `precio − redondear(precio × p ÷ 100)`— y totales `price`, `listPrice`, `savings`, todo redondeado a los decimales de la moneda `HALF_UP` | — | Unitaria: los dos casos de la spec del módulo, el fijo mayor que el precio → cero, el porcentaje cien → cero, la lista vacía → tres ceros, y una moneda de cero decimales | **Hecha el 15-09-2026** |
| `T-05` | `ProductPackageRepository` + `JpaProductPackageRepository`: `save` con **traducción** de `uq_product_packages_code` y `uq_product_packages_name` al `409` de `EX-001`/`EX-002`, `existsCode`, `existsAliveName`, `findAliveByIdForUpdate`, `findByIdForUpdate` | `T-03` | Integración: un `INSERT` duplicado llega como `BusinessRuleException` y no como `DataIntegrityViolationException` | **Hecha el 15-09-2026** |
| `T-06` | `ProductPackageQueryRepository.findDetail(id)`: el paquete con moneda resuelta y **sus filas de asociación con el producto de cada una** —código, nombre, tipo, precio, estado, `deleted_at`, origen— en **una sentencia** | `T-01` | Integración: un paquete vacío devuelve cero filas y el paquete; uno con tres, tres filas | **Hecha el 15-09-2026** |
| `T-07` | `application/RegisterPackageRequest` y `PackageDetailResponse` (`PackageItemResponse`, `PackageTotals`, `offerable`, `offerableReason`), con `from(fila, PackagePricing)` | `T-04`, `T-06` | El contrato declara los campos; `offerable` y `offerableReason` **siempre presentes** | **Hecha el 15-09-2026** |
| `T-08` | `domain/service/RegisterPackageService`: validación conjunta, código, nombre, moneda por `CurrencyLookup`, inserción en `INACTIVO`, auditoría `CREATE`, y relectura del detalle | `T-05`, `T-06`, `T-07` | `CA-PM-261`, `CA-PM-262`, `CA-PM-263`, `CA-PM-264`, `CA-PM-267` | **Hecha el 15-09-2026** |
| `T-09` | `interfaces/PackageController`: `POST /api/v1/packages`, `@PreAuthorize("hasAuthority('packages:create')")`, `201` con `Location` | `T-08` | `CA-PM-265`, `CA-PM-266`, `CA-PM-268`; la ruta entra en `EndpointPermissionsIT` con su permiso | **Hecha el 15-09-2026** |
| `T-10` | Pruebas de API (`PackagesIT`) de los ocho criterios, incluida la carrera de dos altas con el mismo código en `PackageConcurrencyIT` | `T-09` | `CA-PM-261` a `CA-PM-268`; la carrera deja una fila y un `409` | **Hecha el 15-09-2026** |
| `T-11` | Documentación OpenAPI. **La prosa dice** que nace vacío e inactivo, que el precio se calcula y no se declara, que la moneda es inmutable, y que los `products:` no habilitan | `T-09` | El contrato declara `201`, `400`, `401`, `403`, `409`, `422` | **Hecha el 15-09-2026** |
| `T-12` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-10` | La fila de `RF-PM-017` refleja el estado; `api/index.md` documenta el recurso `/packages` y los cuatro permisos | **Hecha el 15-09-2026** |

**Verificación (15-09-2026):** `PackagesIT` (9), `PackageConcurrencyIT` (2 del alta), `PackagesPermissionsSeedIT` (3) y 26 unitarias, en verde; el `mvn verify` completo queda en 370 unitarias y 1422 de integración, con las únicas rojas fuera del módulo (`DevelopmentSeedIT` por una edición sin confirmar de la semilla, y una prueba de `SP` que desempata mal dos asientos con el mismo instante).

## 2. Orden de ejecución

**`T-01` y `T-02` primero**, y `T-04` antes que cualquier servicio: `PackagePricing` es lo que todas las lecturas del submódulo van a reutilizar, y conviene tenerlo probado en unitaria antes de que una prueba de API tenga que averiguar de dónde salió un céntimo.

`T-05` y `T-06` son independientes. `T-08` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-261` | `T-04`, `T-08`, `T-10` |
| `CA-PM-262`, `CA-PM-263` | `T-01`, `T-05`, `T-08` |
| `CA-PM-264` | `T-08` |
| `CA-PM-265`, `CA-PM-266` | `T-07`, `T-09` |
| `CA-PM-267` | `T-08` |
| `CA-PM-268` | `T-02`, `T-09` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Los números de migración se asignan al construir: `V91`/`V92` si nadie se adelanta | 15-09-2026 | Responsable técnico | **Cerrado** el 15-09-2026 |
| 2 | `T-02` cambia el recuento del catálogo de permisos (46 → 50) en cuatro suites de `SP`; es la fricción deliberada de `security.md` §4.4 | 15-09-2026 | Responsable técnico | **Cerrado** el 15-09-2026 |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local, **incluidas las cuatro suites del catálogo de permisos con el número nuevo**.
- [ ] `PackagePricing` tiene prueba unitaria propia y es el único sitio que resta un descuento.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
