# PLAN — `RF-PM-019` Consultar el detalle de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-019` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 15-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 15-09-2026 |

---

## 1. Enfoque

**Una sentencia que trae el paquete y sus filas, y un componente que las convierte en dinero.**

El detalle del producto (`RF-PM-003`) resuelve sus membresías con `JOIN`; el del paquete resuelve **sus productos** igual, en una sola sentencia con `LEFT JOIN` a `product_package_items` y a `products`, y le pasa las filas a `PackagePricing`. Todo lo que este plan decide gira alrededor de que **la cuenta viva en un sitio y la sentencia no la haga**: la lista, la oferta y el hotlink reutilizan los dos.

## 2. Cambios de esquema

**Ninguno.** Las dos tablas son de `RF-PM-017`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `ProductPackageQueryRepository.findDetail(id)` (de `RF-PM-017`): devuelve `PackageRow` + `List<PackageItemRow>`, con el producto de cada fila —`code`, `name`, `type`, `status`, `deleted_at`, `price`, `purchase_price`, `currency_id`, `source_membership_id`— en **una sentencia** | `PM` |
| `domain/models` | `PackagePricing` (de `RF-PM-017`) y **`PackageOfferability`** — la decisión de `offerable` con su motivo en orden fijo, sin dependencias | `PM` |
| `domain/service` | `GetPackageService`: detalle → precio → conversión → ofrecibilidad → motivo de retiro | `PM` |
| `application` | `PackageDetailResponse` (de `RF-PM-017`) con `PackageItemResponse`, `DiscountRef`, `offerable`, `offerableReason`, `deletionReason` | `PM` |
| `interfaces` | `PackageController` — `GET /api/v1/packages/{id}` | `PM` |
| `shared/audit` | La lectura estrecha del motivo de eliminación que `RF-PM-003` estrenó (`architecture.md` §15.3), para `product_packages` | `shared` |

**`PackageOfferability` es un objeto de dominio y no un `if` en el servicio**, porque **tres** lecturas lo necesitan —el detalle lo publica, la oferta y el hotlink filtran por él— y la regla tiene un orden. Recibe el paquete y sus filas y devuelve `(boolean, motivo)`; la oferta y el hotlink solo miran el booleano.

## 4. Contrato de API

`GET /api/v1/packages/{id}` — `packages:read`.

```json
{
  "id": "…", "code": "COMBO_ORO", "name": "Combo Oro", "description": "…",
  "currency": { "id": "…", "code": "USD", "decimalPlaces": 2 },
  "scope": "HOTLINKS", "status": "ACTIVO",
  "items": [
    {
      "product": { "id": "…", "code": "UPGRADE_BECA_ORO", "name": "Ascenso a Oro", "type": "UPGRADE_MEMBRESIA",
                   "status": "ACTIVO", "deleted": false, "price": 299.00, "purchasePrice": null },
      "discount": { "type": "PORCENTAJE", "value": 10 },
      "priceInPackage": 269.10
    },
    {
      "product": { "id": "…", "code": "BOT_SENALES", "name": "Bot de señales", "type": "BOT",
                   "status": "ACTIVO", "deleted": false, "price": 39.00, "purchasePrice": null },
      "discount": { "type": "FIJO", "value": 39.00 },
      "priceInPackage": 0.00
    }
  ],
  "listPrice": 338.00, "price": 269.10, "savings": 68.90,
  "exchange": null,
  "offerable": true, "offerableReason": null,
  "createdAt": "…", "updatedAt": "…"
}
```

- **`deletedAt` y `deletionReason`** con `NON_NULL`, como en `RF-PM-003`: solo cuando el paquete está retirado.
- **`purchasePrice` presente y nulo** cuando no se conoce: es la lectura de administración.
- **`discount.value` como número** con la escala de su forma: dos decimales el porcentaje, los de la moneda el fijo.

## 5. Autorización

`@PreAuthorize("hasAuthority('packages:read')")`.

## 6. Auditoría

**No audita.**

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Tres sentencias** —el paquete con sus filas, la moneda de casa, la tasa solo si hay algo que convertir—, **cuatro** con conversión y **cinco** cuando está retirado y hay que leer el motivo. `CA-PM-284` las cuenta.

## 8. Impacto sobre otros módulos

**Ninguno fuera de `PM`.** Dentro, **una enmienda de Art. I.7 que este plan declara y sus tareas construyen**:

### 8.1 `RF-PM-007` — la oferta gana la colección `packages`

`requirements/pm.md` §5.2.10 decidió que el paquete **se publica donde se publican los productos**, y la oferta es el primer sitio. La enmienda ya está escrita en [`007-consultar-oferta-propia/spec.md`](../007-consultar-oferta-propia/spec.md) v0.13.0 (`CA-PM-335` a `CA-PM-338`) y se construye **aquí**, con `T-11` y `T-12`, porque las dos piezas que necesita —`PackagePricing` y `PackageOfferability`— nacen en esta tripleta y en la del alta, y la oferta es su primer consumidor fuera del detalle.

**Lo que se construye**: `ProductPackageQueryRepository.findOfferable()` —los paquetes activos y vivos con sus líneas en **dos** sentencias (la segunda es `findItemsOf`, de `RF-PM-018`)—; en `GetOfferService`, el filtro por `PackageOfferability` y por **origen** (`RN-PM-044`: todos los upgrades del paquete con `source_membership_id` igual a la membresía del actor, o ninguno), la cuenta con `PackagePricing`, y **las monedas de los paquetes añadidas al conjunto** que ya se pasa a `ExchangeRateLookup` para que la sentencia de tasas siga siendo una; y `OfferResponse.packages`, envuelta, con `OfferPackageItem{product: OfferItem, discount, priceInPackage}`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Sumar en la sentencia** (`SUM(CASE …)`) | Dos cuentas para el mismo precio; y el redondeo por producto a la moneda no se expresa bien en SQL sin repetir la escala en cada sitio |
| **Una sentencia por producto** | Es el `N+1`; el `JOIN` trae todo de una vez |
| **`offerable` decidido en el servicio con `if`s** | Tres lecturas lo necesitan con el mismo orden; un objeto con prueba unitaria lo fija |
| **Ocultar los productos inactivos del detalle** | Es la única pantalla desde la que se arregla; ocultarlos dejaría el paquete «oferable: false» sin nada visible que lo explique |
| **Redondear el total y no las líneas** | El total dejaría de cuadrar con la suma de las líneas que el front pinta |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El total no cuadra con las líneas** por redondeo | Se redondea **por producto** y el total es la suma; `CA-PM-277` lo comprueba con tres porcentajes que dejan medio céntimo |
| 2 | **`offerable` dice cosas distintas** en el detalle y en la oferta | Un solo `PackageOfferability`; la prueba de la oferta usa los mismos casos que `CA-PM-281` |
| 3 | **Alguien cachea el precio** «porque el detalle es lento» | `CA-PM-278` corrige un producto y espera el cambio en la lectura siguiente |

## 11. Estrategia de prueba

- **Unitaria**: `PackageOfferability` —cada motivo y su orden— y `PackagePricing` (ya de `RF-PM-017`).
- **Integración de API** (`PackageDetailIT`): los ocho criterios de `spec.md` §12; **la que define el requerimiento es `CA-PM-278`**: el precio se calcula.
- **De número de sentencias**: tres, cuatro y cinco.
