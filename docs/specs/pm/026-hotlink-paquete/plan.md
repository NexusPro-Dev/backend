# PLAN — `RF-PM-026` Consultar el hotlink de un paquete, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-026` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 15-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 15-09-2026 |
| Reabierto el | 16-09-2026 — **`validFrom` y `validTo` en la respuesta, y hoy en la ofrecibilidad** (`RN-PM-047`), ver §11 (Art. I.7) |
| Reaprobado el | 16-09-2026 — Responsable del proyecto |

---

## 1. Enfoque

**Componer lo que ya existe, y no volver a decidir nada.** El vendedor sale de `PublicSellerLookup`; la conversión, de `ProductExchangeResolver`; la cuenta, de `PackagePricing`; la ofrecibilidad, de `PackageOfferability`; y cada producto, de **la misma fila** (`ProductRow`) y **la misma forma** (`HotlinkResponse.ProductRef`) que el hotlink del producto. Lo único que este requerimiento escribe de nuevo es **una sentencia** —el paquete publicado con sus productos— y **un servicio** que ordena las piezas.

**Desde el 22-09-2026 eso incluye los enlaces** (`RN-PM-048` a `RN-PM-050`): `p.video_url` deja de estar en el `SELECT` —la columna ya no existe— y los enlaces de los productos del paquete se leen **en una sentencia más, con el tipo en el predicado**, exactamente como en `RF-PM-008`. **Que sean dos consultas distintas es justo el motivo de que `CA-PM-398` exista**: el filtro que impide publicar el cupón está escrito **dos veces**, una por sentencia, y una copia que se olvide no falla — publica.

**La sentencia trae de cada producto lo que trae `findPublishedByCode`**: el `SELECT` de productos que ya tiene cinco copias —catálogo, detalle, oferta, hotlink, disponibles— gana una **sexta**, con un `JOIN product_package_items` y las dos columnas del descuento. Es la forma de que `ProductRef.from(ProductRow)` se reutilice sin tocarlo, y de que `rating`, video, portada y membresía viajen **sin sentencias propias**.

## 2. Cambios de esquema

**Ninguno.** `uq_product_packages_code` cubre la búsqueda por código; `ix_product_package_items_product` no hace falta aquí — se entra por el paquete.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `ProductPackageQueryRepository.findPublishedByCode(String)` — el paquete **activo, vivo y de alcance `HOTLINK` o `AMBOS`** con sus productos como `ProductRow` + descuento, en una sentencia | `PM` |
| `domain/service` | `GetPackageHotlinkService` | `PM` |
| `application` | `PackageHotlinkResponse` — `seller` (`HotlinkResponse.SellerRef`), `package` con `items[{product: HotlinkResponse.ProductRef, discount, priceInPackage}]`, `listPrice`, `price`, `savings`, `exchange` (`HotlinkResponse.ExchangeRef`) | `PM` |
| `interfaces` | `HotlinkController` — `GET /api/v1/hotlinks/{username}/packages/{code}`, `@SecurityRequirements` | `PM` |
| `shared/security` | `RUTAS_PUBLICAS` gana **`/api/v1/hotlinks/*/packages/*`** | `shared` |

**Nada nuevo en `SP`.** Las dos lecturas que `RF-PM-008` estrenó bastan.

## 4. Contrato de API

`GET /api/v1/hotlinks/{username}/packages/{code}` — **público**.

```json
{
  "seller": { "firstName": "Ana", "lastName": "Ruiz" },
  "package": {
    "code": "PACK_ORO_BOTS",
    "name": "Oro con señales",
    "description": "…",
    "currency": { "code": "USD", "decimalPlaces": 2 },
    "items": [
      {
        "product": { "id": "…", "code": "UPGRADE_PLATINO_ORO", "type": "UPGRADE_MEMBRESIA", "…": "lo del hotlink del producto", "price": 500.00, "rating": { "average": 4.50, "count": 2 } },
        "discount": { "type": "PORCENTAJE", "value": 10.00 },
        "priceInPackage": 450.00
      },
      {
        "product": { "code": "BOT_SENALES", "type": "BOT", "price": 100.00, "…": "…" },
        "discount": { "type": "FIJO", "value": 20.00 },
        "priceInPackage": 80.00
      }
    ],
    "listPrice": 600.00,
    "price": 530.00,
    "savings": 70.00,
    "exchange": { "currency": { "code": "COP", "decimalPlaces": 0 }, "rate": 4150.000000, "amount": 2199500 }
  }
}
```

`404` con **el cuerpo de `RF-PM-008` `EX-001`** en todos los casos que no proceden — el mismo `ResourceNotFoundException` con el mismo mensaje, para que ni el texto distinga.

## 5. Autorización

**Ninguna: la ruta es pública.** `@SecurityRequirements` vacío en la operación y **una declaración nueva** en `SecurityConfig.RUTAS_PUBLICAS`: `/api/v1/hotlinks/*/packages/*`.

!!! important "Esto corrige lo que `requirements/pm.md` §7 dijo al diseñar el requerimiento"

    El módulo escribió que el hotlink del paquete «no estrena ni declaración de ruta ni cota». **La mitad es cierta.** La cota de `RateLimitFilter` se decide por **prefijo** (`ruta.startsWith("/api/v1/hotlinks/")`) y cubre cualquier profundidad; la declaración pública es un patrón Ant de **dos** comodines, `/api/v1/hotlinks/*/*`, y un `*` es **un** segmento. Sin la declaración nueva la ruta exigiría token y respondería `401` a todo el mundo. Se añade el patrón de tres segmentos **al lado del de dos**, con el motivo escrito, y no se sustituye por `/**`: un `/**` bajo `hotlinks/` dejaría pública cualquier ruta futura de la familia sin que nadie la declarara.

`EndpointPermissionsIT` la registra entre las públicas —**la cuarta del módulo**—, y `SIN_PERMISO_A_PROPOSITO` no la necesita: no tiene `@PreAuthorize` porque no tiene permiso, no porque se le olvidara.

## 6. Auditoría

**No audita.** `RF-PM-008` §14.5.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Cuatro sentencias como máximo**: vendedor, paquete con productos, moneda de casa, tasa; **tres** cuando no hay nada que convertir; **una** cuando el vendedor no existe. La ofrecibilidad no cuesta sentencia: se decide sobre las filas que ya vinieron (`status`, `deleted_at`, cuántas, y `description` del paquete).

**El `404` por no ofrecible se decide en Java y no en el `WHERE`**, a propósito: si la sentencia filtrara «todos activos» con un `NOT EXISTS`, la regla de `RN-PM-039` viviría en dos sitios —`PackageOfferability` para el detalle y la oferta, y el SQL para el hotlink— y el segundo se quedaría atrás.

## 8. Impacto sobre otros módulos

**Ninguno.** `PublicSellerLookup` y `ExchangeRateLookup` se consumen como están.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una forma propia de producto para el paquete** | Cada enmienda futura del hotlink del producto tendría que hacerse dos veces, y la olvidada sería la pública (`spec.md` §14.3) |
| **Devolver el paquete no ofrecible marcado** | Publica sin token que existe y qué le pasa (`spec.md` §14.1) |
| **`/api/v1/hotlinks/**` en `RUTAS_PUBLICAS`** | Dejaría pública cualquier ruta futura de la familia sin declararla (§5) |
| **Filtrar los productos de alcance `TIENDA` dentro del paquete** | El canal lo decide el paquete; filtrarlos crearía paquetes impublicables sin motivo nombrado (`spec.md` §13) |
| **Ofrecibilidad en el `WHERE`** | La regla viviría en dos sitios (§7) |
| **Una ruta propia `/api/v1/package-hotlinks/…`** | Estrenaría cota y declaración; la familia existe para esto |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Que el `404` del paquete no ofrecible tenga otro texto** que el del inexistente, y el texto distinga | Un solo `ResourceNotFoundException` con el mensaje de `RF-PM-008`; `CA-PM-328` y `CA-PM-329` comparan cuerpos |
| 2 | **Que la sexta copia del `SELECT` de productos se quede atrás** en la próxima enmienda | Es el riesgo que ya corren las otras cinco; `ProductRatingIT` y sus hermanas comprueban la forma en las cuatro lecturas y ganan la quinta |
| 3 | **Olvidar la declaración pública** y que la ruta responda `401` | `T-04` y su prueba sin token son lo primero que se escribe tras el controlador |

## 11. Estrategia de prueba

- **Integración de API** (`PackageHotlinkIT`, junto a `HotlinkIT`): los ocho criterios; la que define el requerimiento es **`CA-PM-329`** —desactivar un producto del paquete apaga el enlace, reactivarlo lo enciende—, y la que más pesa es **`CA-PM-328`**, que compara el cuerpo del `404` con el del hotlink del producto.
- **Cota de tasa** (`RateLimitIT`): `CA-PM-333`, dos peticiones de producto agotan la tercera de paquete desde el mismo origen.
- **Sentencias**: cuatro con conversión, tres sin ella, una con vendedor inexistente.
- **Seguridad** (`EndpointPermissionsIT`): la ruta entre las públicas.
