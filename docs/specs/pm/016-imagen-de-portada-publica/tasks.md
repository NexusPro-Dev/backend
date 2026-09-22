# TASKS — `RF-PM-016` Obtener la imagen de una portada, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-016` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 14-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 14-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `domain/service/GetProductImageService.get(UUID)`: `findById` o `ResourceNotFoundException` `EX-001` «La imagen no existe.»; `@Transactional(readOnly = true)` | `RF-PM-014 · T-08` | Cubierta por `ProductImageIT`: existe (`CA-PM-255`) y no existe (`CA-PM-257`) | **Hecha el 14-09-2026** |
| `T-02` | `interfaces/ProductImageController`: `GET /api/v1/product-images/{imageId}`, **sin `produces`** —la ruta no negocia; los tres tipos los declara el contrato—, `ResponseEntity<byte[]>` con `Content-Type` guardado, `Cache-Control: public, max-age=31536000, immutable`, `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`; **sin `@PreAuthorize`, con el comentario de por qué** | `T-01` | `CA-PM-255` | **Hecha el 14-09-2026** |
| `T-03` | `SecurityConfig`: `GET /api/v1/product-images/*` en la lista **por método**, con el motivo al lado; `EndpointPermissionsIT` la lista como pública solo en `GET` | `T-02` | `CA-PM-259` | **Hecha el 14-09-2026** |
| `T-04` | `RateLimitFilter`: la familia `/api/v1/product-images/` con la política `public-catalog`, llave por la familia | `T-02` | `CA-PM-260`: la 121.ª de un origen → `429`, con identificadores distintos; el registro lleva el prefijo | **Hecha el 14-09-2026** |
| `T-05` | **LA PRUEBA DE LAS CABECERAS** (`ProductImageIT`): subir un `JPEG` disfrazado de `PNG` con `RF-PM-014`, leerlo sin token: bytes iguales, `image/jpeg`, `Content-Length`, y las cuatro cabeceras exactas; y lo mismo con token | `T-02` | `CA-PM-255`, `CA-PM-256`. **Es la prueba que define el requerimiento** | **Hecha el 14-09-2026** |
| `T-06` | Prueba del `404` uniforme: inventado, reemplazado, quitado — mismo cuerpo JSON | `T-02`, `RF-PM-015 · T-03` | `CA-PM-257` | **Hecha el 14-09-2026** |
| `T-07` | Prueba de «no mira el producto»: inactivo, retirado y de alcance `TIENDA` → `200`; y la prueba de sentencias cuenta una, ninguna sobre `products` | `T-02` | `CA-PM-258`, `CA-PM-259` | **Hecha el 14-09-2026** |
| `T-08` | Documentación OpenAPI. **La prosa dice** que la dirección la dan las lecturas y no se lista, que es inmutable y se cachea un año, y que no mira el producto. `security: []`; respuesta binaria con los tres tipos | `T-02` | `OpenApiContractIT`: la primera respuesta no JSON del contrato, y `security: []` como en el hotlink | **Hecha el 14-09-2026** |
| `T-09` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-07` | La fila de `RF-PM-016` refleja el estado | **Hecha el 14-09-2026** |

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04`, y **`T-05` inmediatamente después**. `T-06` necesita `RF-PM-015` para el identificador quitado; se escribe con el reemplazado y el inventado primero, y gana el tercer caso cuando aquel se construya.

Este requerimiento va **segundo** de los tres (`requirements/pm.md` §6.1): sin él, `coverImageUrl` señalaría a una ruta que no existe.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-255`, `CA-PM-256` | `T-01`, `T-02`, `T-05` |
| `CA-PM-257` | `T-06` |
| `CA-PM-258` | `T-07` |
| `CA-PM-259` | `T-03`, `T-07` |
| `CA-PM-260` | `T-04` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-014` (tabla, entidad, repositorio y la subida con la que se prueba) | 14-09-2026 | Responsable técnico | **Cerrado el 14-09-2026** |
| 2 | `T-06` necesita `RF-PM-015` para el caso «quitado» | 14-09-2026 | Responsable técnico | **Cerrado el 14-09-2026** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] `EndpointPermissionsIT` lista la ruta como pública **solo en `GET`**.
- [ ] `CA-PM-255` comprueba las cinco cabeceras **exactas**, `immutable` incluido.
- [ ] El contrato OpenAPI declara `security: []` y la respuesta binaria, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
