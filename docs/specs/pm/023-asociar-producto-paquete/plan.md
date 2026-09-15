# PLAN — `RF-PM-023` Asociar un producto a un paquete, con su descuento

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-023` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 15-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 15-09-2026 |

---

## 1. Enfoque

**La asociación de `RF-CM-007` con datos dentro de la fila, y con el paquete bloqueado.**

Se hereda la forma de la asociación de tasas —producto por el puerto, `422` para el inexistente, la fila de asociación auditada como `CREATE`, la respuesta con todas las asociaciones— y se le añaden tres cosas: **el descuento** validado contra el precio de hoy, **la moneda** comparada con la del paquete, y **el origen común de los upgrades**, que exige leer las filas hermanas y por eso obliga a bloquear el paquete.

## 2. Cambios de esquema

**Ninguno propio.** `product_package_items` la crea `RF-PM-017` (`V91`) con su clave primaria, sus tres `CHECK` y su índice por producto.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `PackageItem.create(packageId, productId, DiscountValue)` y **`DiscountValue`** — forma y valor, con la validación de forma (`VAL-003`, `VAL-004`) y la de cota contra un precio (`RN-PM-037`) | `PM` |
| `domain/repository` | `PackageItemRepository` + `Jpa…`: `save` con **traducción de `pk_product_package_items`** al `409` de `EX-005`, `exists(packageId, productId)`, `findByPackage(packageId)` | `PM` |
| `domain/repository` | `ProductPackageRepository.findAliveByIdForUpdate` (de `RF-PM-017`) | `PM` |
| `application` | `AssociatePackageProductRequest` — `productId`, `discountType`, `discountValue` | `PM` |
| `domain/service` | `AssociatePackageProductService` | `PM` |
| `domain/repository` | `ProductRepository` y `ProductQueryRepository` **del propio módulo** para leer el producto: es `PM`, no hace falta puerto | `PM` |
| `interfaces` | `PackageController` — `POST /api/v1/packages/{id}/products` | `PM` |

**`DiscountValue` es un objeto de valor y no dos campos sueltos**, como `CommissionValue` en `CM`: es lo que impide que un `PORCENTAJE` viaje con un importe de catorce dígitos o un `FIJO` con tres decimales en una moneda de dos. Su método `precioDentroDe(precio, decimales)` es el que **`PackagePricing` llama** para cada fila: la cuenta vive en un sitio y la cota en otro, pero las dos hablan el mismo tipo.

## 4. Contrato de API

`POST /api/v1/packages/{id}/products` — `packages:update`.

```json
{ "productId": "…", "discountType": "PORCENTAJE", "discountValue": 15 }
```

`201` con `PackageDetailResponse` **entero** (`RF-PM-019`), y `Location` apuntando al detalle del paquete.

- **`discountValue` es número**, con la misma advertencia de coma flotante que todo importe del módulo. El servidor lo compara con `compareTo`, nunca con `equals`.
- **Los códigos `409` son cinco y se distinguen** (`EX-003` a `EX-007`): quien llama es un administrador y cada uno le dice qué corregir.

## 5. Autorización

`@PreAuthorize("hasAuthority('packages:update')")`. La asociación es parte de **armar** el paquete, no una escritura sobre el producto: `products:update` no habilita, y `CA-PM-314` lo cubre por el lado del paquete retirado y `PackagesIT` por el del permiso.

## 6. Auditoría

`ChangeEvent` `CREATE` de `product_package_items`, con la instantánea de la fila —paquete, producto, forma, valor— y **el precio del producto en ese instante**, que es contra lo que se validó el descuento y lo que una revisión posterior necesitará para entender por qué se admitió un fijo que hoy supera el precio.

## 7. Transaccionalidad

`@Transactional`. **Seis sentencias**: el paquete con `FOR UPDATE`, el producto, las filas hermanas —para la unicidad y el origen, **una sola lectura**—, el `INSERT`, la auditoría, y la relectura del detalle. La comprobación de unicidad se hace sobre las hermanas ya leídas y no con una sentencia aparte.

**El bloqueo es del paquete** (`SELECT … FOR UPDATE` sobre `product_packages`): dos asociaciones simultáneas al mismo paquete se ordenan, y `RN-PM-044` se comprueba sobre el estado que dejó la primera. El producto no se bloquea: no se escribe. Y la clave primaria es la red de la unicidad si dos peticiones leyeran las hermanas antes de que ninguna escribiera — no ocurre con el bloqueo, y se traduce igual por si alguien lo quita.

## 8. Impacto sobre otros módulos

**Ninguno.** El producto se lee del propio módulo.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Asociar una lista de productos en una petición** | Cada uno falla por un motivo distinto y habría que decidir si se aplica a medias |
| **Dos campos sueltos en vez de `DiscountValue`** | Un porcentaje con catorce dígitos o un fijo con tres decimales pasarían hasta el `CHECK`, y el `CHECK` no conoce la moneda |
| **Comprobar el origen de los upgrades en la oferta** | Produciría paquetes que nadie puede comprar y que la oferta oculta sin decirlo (`spec.md` §10, `EX-007`) |
| **Bloquear el producto** | No se escribe; bloquearlo serializaría a todos los paquetes que lo contienen |
| **Un `404` uniforme para inactivo, retirado e inexistente** | Quien llama ve el catálogo entero; la uniformidad protege a un anónimo, no a un administrador |
| **Devolver solo la fila nueva** | Lo que cambió es el precio del paquete, y eso solo se ve entero |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La cota del fijo se compara con `equals`** y `49.00` ≠ `49.0000` | `DiscountValue` usa `compareTo`; `CA-PM-307` prueba el igual al precio |
| 2 | **El redondeo del porcentaje sale distinto** en la asociación y en la lectura | La cuenta vive solo en `PackagePricing`, que la asociación llama para responder; `CA-PM-305` la fija |
| 3 | **Alguien quita el bloqueo del paquete** y dos upgrades de orígenes distintos entran a la vez | `PackageConcurrencyIT` asocia dos upgrades de orígenes distintos en paralelo y espera un `201` y un `409` |
| 4 | **Se asocia a un paquete retirado** «porque la fila es de asociación» | `findAliveByIdForUpdate` no lo encuentra; `CA-PM-314` |

## 11. Estrategia de prueba

- **Unitaria**: `DiscountValue` — las dos formas, las cotas, los decimales, el `compareTo`, y `precioDentroDe` en sus tres casos.
- **Integración de API** (`PackageProductsIT`): los once criterios de `spec.md` §12. **Las que definen el requerimiento**: `CA-PM-307` (el céntimo) y `CA-PM-313` (el origen).
- **Concurrencia** (`PackageConcurrencyIT`): el mismo producto dos veces → una fila; dos upgrades de orígenes distintos → uno entra.
- **Auditoría**: la instantánea lleva el precio del producto en ese instante.
