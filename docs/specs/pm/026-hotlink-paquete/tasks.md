# TASKS — `RF-PM-026` Consultar el hotlink de un paquete, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-026` |
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
| `T-01` | `ProductPackageQueryRepository.findPublishedByCode(code)`: el paquete **activo, vivo y `HOTLINKS`**, comparado sin distinguir mayúsculas, con sus productos como **`ProductRow`** —la sexta copia del `SELECT` de productos, con `rating` lateral, membresía y portada— más forma y valor del descuento, **en una sentencia** | `RF-PM-017 · T-06`, `RF-PM-019 · T-02` | Integración: vacío para inactivo, retirado y `TIENDA`; un paquete de tres devuelve tres filas con su `rating` | Pendiente |
| `T-02` | `application/PackageHotlinkResponse`: `seller` y `exchange` con los records de `HotlinkResponse`, `items` con **`HotlinkResponse.ProductRef`** tal cual, `discount`, `priceInPackage`, y los tres totales | `RF-PM-017 · T-07` | El contrato declara la forma; ningún campo `purchasePrice` ni `status` en el esquema | Pendiente |
| `T-03` | `domain/service/GetPackageHotlinkService`: vendedor (`PublicSellerLookup`), paquete (`T-01`), **`PackageOfferability`** → el mismo `404`, `PackagePricing`, `ProductExchangeResolver` sobre `price` | `T-01`, `T-02`, `RF-PM-019 · T-01`, `RF-PM-017 · T-04` | `CA-PM-327`, `CA-PM-329`, `CA-PM-330`, `CA-PM-334`; un solo `ResourceNotFoundException` con el mensaje de `RF-PM-008` | Pendiente |
| `T-04` | `HotlinkController`: `GET /{username}/packages/{code}` con `@SecurityRequirements`; **`SecurityConfig.RUTAS_PUBLICAS` gana `/api/v1/hotlinks/*/packages/*`** con el motivo escrito al lado del patrón de dos segmentos | `T-03` | Sin token responde `200`; `EndpointPermissionsIT` la registra entre las públicas | Pendiente |
| `T-05` | Pruebas de API (`PackageHotlinkIT`): los ocho criterios, con **`CA-PM-329`** —desactivar y reactivar un producto del paquete apaga y enciende el enlace— como la que define el requerimiento y **`CA-PM-328`** comparando el cuerpo del `404` con el del hotlink del producto; número de sentencias (cuatro, tres, una) | `T-04` | `CA-PM-327` a `CA-PM-332`, `CA-PM-334` | Pendiente |
| `T-06` | `RateLimitIT`: la cota compartida por familia — dos de producto y la tercera de paquete recibe `429`, y al revés | `T-04` | `CA-PM-333` | Pendiente |
| `T-07` | Documentación OpenAPI. **La prosa dice** que es público, que el `404` es uniforme y el mismo del hotlink del producto —también para el paquete que hoy no se puede ofrecer—, que el producto de cada línea es la forma del hotlink del producto, que `priceInPackage` es la cuenta de hoy y no una reserva, y que el alcance de los productos no filtra dentro del paquete | `T-04` | El contrato declara `200`, `404`, `429`, y `security: []` | Pendiente |
| `T-08` | Corregir `requirements/pm.md` §7 (`RNF-SEG-002`): la ruta **sí estrena declaración pública** y no cota; y actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-05` | La fila de `RF-PM-026` refleja el estado; `security.md` cuenta la ruta entre las públicas | Pendiente |

## 2. Orden de ejecución

`T-01` y `T-02` independientes; `T-03` las junta; **`T-04` inmediatamente después, y su prueba sin token antes que cualquier otra**: es la que descubre si la declaración pública falta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-327`, `CA-PM-334` | `T-03`, `T-05` |
| `CA-PM-328` | `T-01`, `T-03`, `T-05` |
| `CA-PM-329`, `CA-PM-330` | `T-03`, `T-05` |
| `CA-PM-331`, `CA-PM-332` | `T-01`, `T-02`, `T-05` |
| `CA-PM-333` | `T-04`, `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-017` y `RF-PM-019`; va la última de las diez por decisión de `pm.md` §6.1 | 15-09-2026 | Responsable técnico | **Abierto** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
