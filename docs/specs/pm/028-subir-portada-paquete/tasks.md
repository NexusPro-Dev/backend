# TASKS — `RF-PM-028` Subir o reemplazar la portada de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-028` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 16-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 16-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V11__pm_portada_paquete.sql`**: `product_packages.cover_image_id` con `fk_product_packages_cover_image` (sin `ON DELETE`) y `uq_product_packages_cover_image`; `COMMENT ON` de la columna y de la tabla `product_images` —que desde hoy guarda las portadas del catálogo— | — | La migración aplica sobre una base con paquetes; ningún `icon` ni `color` | **Hecha el 16-09-2026** |
| `T-02` | `domain/models/CambioDePortada`: sale de `Product` como tipo propio; `Product.asignarPortada` y `quitarPortada` lo devuelven; `UploadProductCoverService`, `RemoveProductCoverService` y `ProductTest` lo importan | — | `ProductTest` y `ProductCoverIT` siguen en verde | **Hecha el 16-09-2026** |
| `T-03` | `ProductPackage`: columna `coverImageId`; `asignarPortada(UUID, ahora)` devuelve el anterior y el diff; `quitarPortada(ahora)` **sin regla** —vacío y sin diff si no había—; `getCoverImageId()`; `instantanea()` gana `cover_image_id` | `T-02` | Unitaria en `ProductPackageTest`: anterior nulo y no nulo, el diff, quitar sin y con portada, la instantánea | **Hecha el 16-09-2026** |
| `T-04` | `ProductPackageQueryRepository.PackageRow` gana `coverImageId`; `COLUMNAS_PAQUETE` y `SELECT_PUBLICADO` seleccionan `k.cover_image_id` y **nada de `product_images`** | `T-01` | Los techos de sentencias de las cuatro suites de lecturas no suben | **Hecha el 16-09-2026** |
| `T-05` | `coverImageUrl` en `PackageDetailResponse`, `PackageItemSummary`, `OfferPackageItem` y `PackageHotlinkResponse.PackageRef`, con `ProductImageUrls.de`, **siempre presente** | `T-04` | Las cuatro formas del contrato declaran el campo; nulo cuando no hay | **Hecha el 16-09-2026** |
| `T-06` | `domain/service/UploadPackageCoverService`: `ProductImage.de` **antes** de tocar la base; `paqueteVivo` (`EX-001`, el de `AssociatePackageProductService`); `save` de la nueva; `asignarPortada`; `flush`; **`deleteById` de la anterior después**; `ChangeEvent` `UPDATE` de `product_packages` con solo `cover_image_id`; `PackageDetailReader.leer` | `T-03`, `T-05` | `CA-PM-354`, `CA-PM-355`, `CA-PM-356` | **Hecha el 16-09-2026** |
| `T-07` | `PackageController`: `PUT /api/v1/packages/{id}/cover`, `consumes = multipart/form-data`, `@RequestPart(value = "file", required = false)`, `@PreAuthorize("hasAuthority('packages:update')")`, `200` | `T-06` | La ruta entra en `EndpointPermissionsIT` con su permiso | **Hecha el 16-09-2026** |
| `T-08` | **LA PRUEBA DE LAS CUATRO LECTURAS** (`PackageCoverIT`): tras subir, listado, detalle, oferta y hotlink devuelven la dirección **del paquete** y no la de sus productos; sin portada, presente y nula | `T-05`, `T-07` | `CA-PM-360`. **Es la prueba que define el requerimiento** | **Hecha el 16-09-2026** |
| `T-09` | Prueba del reemplazo: otra dirección, la fila anterior no existe, **una** fila por paquete, la vieja `404` y la nueva `200` sin token, auditoría con los dos identificadores | `T-07` | `CA-PM-355`, `CA-PM-356` | **Hecha el 16-09-2026** |
| `T-10` | Pruebas de los rechazos: `GIF` como `image/png` → `VAL-003`; 5 242 881 → `VAL-004`; sin parte y vacía → `VAL-002`; JSON → `EX-003`; nada queda escrito | `T-07` | `CA-PM-357` | **Hecha el 16-09-2026** |
| `T-11` | Pruebas de paquete: inexistente y retirado → `404` con el mismo cuerpo; `products:update` sin `packages:set-cover` → `403`; inactivo, vacío y sin descripción → `200` | `T-07` | `CA-PM-358`, `CA-PM-359` | **Hecha el 16-09-2026** |
| `T-12` | Pruebas de sentencias: los techos de `PackageListIT`, `PackageDetailIT`, `PackageOfferIT` y `PackageHotlinkIT` no suben | `T-04` | `CA-PM-361` | **Hecha el 16-09-2026** |
| `T-13` | Pruebas de enmienda, **en `PackageCoverIT`** y no repartidas en cinco suites: `coverImageUrl` presente y nula en el alta, el listado, el detalle —también retirado—, la oferta y el hotlink, en una sola prueba que recorre las cinco | `T-05` | `CA-PM-367` a `CA-PM-371` | **Hecha el 16-09-2026** |
| `T-14` | Documentación OpenAPI. **La prosa dice** que es la misma imagen, con las mismas condiciones y la misma ruta que la del producto; que reemplaza y la dirección cambia; y **que el paquete no declara icono ni color**. Las cuatro lecturas describen `coverImageUrl` | `T-07` | `OpenApiContractIT` regenera; el diff del `json` trae `coverImageUrl` en las cuatro formas | **Hecha el 16-09-2026** |
| `T-15` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md`, `docs/modelo-datos.md` y `docs/security.md` | `T-13` | Las filas reflejan el estado | **Hecha el 16-09-2026** |

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06` → `T-07`, y **`T-08` inmediatamente después**: es la que dice que la portada del paquete es del paquete y no de sus productos. `T-09` a `T-13` cierran los criterios; `T-14` y `T-15` cierran la documentación.

**`T-02` va antes que el agregado** aunque no cambie ningún comportamiento: `ProductPackage` no debe devolver un tipo llamado `Product.CambioDePortada`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-354` | `T-06`, `T-07` |
| `CA-PM-355`, `CA-PM-356` | `T-06`, `T-09` |
| `CA-PM-357` | `T-10` |
| `CA-PM-358`, `CA-PM-359` | `T-11` |
| `CA-PM-360` | `T-04`, `T-05`, `T-08` |
| `CA-PM-361` | `T-04`, `T-12` |
| `CA-PM-367` a `CA-PM-371` (enmiendas) | `T-05`, `T-13` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Ninguno. `V11` es el siguiente número libre tras `V10`; si otra migración se lo lleva antes, se renumera (`modelo-datos.md` §5.4) | 16-09-2026 | Responsable técnico | **Cerrado el 16-09-2026** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde, **incluidos los de las enmiendas** a las cuatro lecturas y al alta.
- [ ] `mvn verify` en verde en local, **con los techos de sentencias de las cuatro lecturas sin subir**.
- [ ] **Ningún archivo de imagen entra al repositorio**: los bytes de las pruebas se generan.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad, `docs/api/index.md`, `docs/modelo-datos.md` y `docs/security.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
