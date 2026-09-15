# PLAN — `RF-PM-024` Corregir el descuento de un producto del paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-024` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 15-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 15-09-2026 |

---

## 1. Enfoque

**La cota de `RF-PM-023` sobre una fila que ya existe.** El mismo `DiscountValue`, la misma comprobación contra el precio de hoy, el mismo bloqueo del paquete; lo único nuevo es que la fila se busca por su clave compuesta y se **actualiza**, con la auditoría de antes y después de las dos columnas.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `UpdatePackageItemRequest` — `discountType`, `discountValue`, los dos obligatorios | `PM` |
| `domain/models` | `PackageItem.corregir(DiscountValue, ahora)` que devuelve el mapa de cambios | `PM` |
| `domain/repository` | `PackageItemRepository.findForUpdate(packageId, productId)` | `PM` |
| `domain/service` | `UpdatePackageItemService` | `PM` |
| `interfaces` | `PackageController` — `PATCH /api/v1/packages/{id}/products/{productId}` | `PM` |

## 4. Contrato de API

`PATCH /api/v1/packages/{id}/products/{productId}` — `packages:update`. `{ "discountType": "FIJO", "discountValue": 20.00 }` → `200` con `PackageDetailResponse`.

## 5. Autorización

`@PreAuthorize("hasAuthority('packages:update')")`.

## 6. Auditoría

`UPDATE` de `product_package_items` con `{discount_type: {before, after}, discount_value: {before, after}}` de lo que cambió; nada si no cambió.

## 7. Transaccionalidad

`@Transactional`. El paquete con `FOR UPDATE`, la fila, el producto —para el precio de hoy—, el `UPDATE`, la auditoría, la relectura del detalle.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`PATCH` parcial del valor** | Asumir la forma que había invita al despiste (`spec.md` §14.1) |
| **`PUT`** | Un verbo distinto para la única corrección no parcial del módulo |
| **No comprobar la cota si solo cambia la forma** | Cambiar la forma cambia lo que el valor significa; siempre se comprueba |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Se comprueba contra el precio del momento de asociar** guardado en algún sitio | No hay ningún sitio: el precio se lee de `products`; `CA-PM-316` baja el precio después de asociar |

## 11. Estrategia de prueba

- **Unitaria**: `PackageItem.corregir` — forma, valor, los dos, nada.
- **Integración de API** (`PackageProductsIT`): los seis criterios; la que define el requerimiento es `CA-PM-316`.
