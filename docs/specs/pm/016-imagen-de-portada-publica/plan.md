# PLAN — `RF-PM-016` Obtener la imagen de una portada, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-016` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 14-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 14-09-2026 |

---

## 1. Enfoque

**Un `SELECT` por identificador, un `ResponseEntity<byte[]>` con cinco cabeceras, y tres líneas de configuración transversal.**

No hay caso de uso que decidir: la ruta lee una fila y la devuelve. Lo que este plan fija es **dónde se escribe cada una de las tres cosas que hacen a la ruta segura y barata** —la lista pública, la cota de tasa y las cabeceras— porque las tres viven fuera del módulo o en el borde, y son las que alguien tocaría «para arreglar algo» sin saber lo que sostienen. El controlador es propio (`ProductImageController`) y no cuelga de `ProductController`: **es la única ruta del sistema que no devuelve JSON**, y conviene que se vea sola.

## 2. Cambios de esquema

**Ninguno.** `V90` (`RF-PM-014`) creó la tabla, y esta ruta la lee por clave primaria.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/service` | **`GetProductImageService.get(UUID)`** → `ProductImage` o `EX-001`. Lectura sin transacción de escritura | `PM` |
| `domain/repository` | `ProductImageRepository.findById` (de `RF-PM-014`) — **la única lectura del sistema que carga `content`** | `PM` |
| `interfaces` | **`ProductImageController`** — `GET /api/v1/product-images/{imageId}` → `ResponseEntity<byte[]>` con las cabeceras de `spec.md` §6.2 | `PM` |
| `shared/security` | `SecurityConfig`: `GET /api/v1/product-images/*` en la lista por método, con el motivo escrito al lado; **`RateLimitFilter`**: la familia `/api/v1/product-images/` con la política `public-catalog` | `SP` (transversal) |
| `docs` | `security.md` §6 y §5.5.1 ya lo declaran (v0.53.0); `EndpointPermissionsIT` gana la ruta como pública | — |

## 4. Contrato de API

`GET /api/v1/product-images/{imageId}` — **público**. `200` con `image/jpeg`, `image/png` o `image/webp`; `404` con el sobre de errores JSON.

- **`produces = {image/jpeg, image/png, image/webp}`** en la anotación, para que el contrato OpenAPI declare **por primera vez** una respuesta que no es JSON: `content` con los tres tipos y `schema: {type: string, format: binary}`.
- **`security: []`** en la operación, como el hotlink y la lista de reseñas: el esquema global de seguridad no aplica, y `OpenApiContractIT` lo comprueba con las otras dos.
- **Las cabeceras se escriben a mano** en el `ResponseEntity`: `CacheControl.maxAge(365, DAYS).cachePublic().immutable()`, `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`, `Content-Length` lo pone Spring con el `byte[]`.
- **La prosa de la `@Operation` dice tres cosas**: que la dirección la dan las lecturas del producto y no se lista en ningún sitio; que es inmutable y por eso se cachea un año —y que reemplazar la portada es otra dirección—; y que no mira el producto.

## 5. Autorización

**Pública, y declarada donde se declara lo público.** `SecurityConfig` la incorpora **en la lista por método** —`GET` y solo `GET`— y no en `RUTAS_PUBLICAS`, por lo mismo que las reseñas (`RF-PM-012`, `requirements/pm.md` v0.26.0): `/product-images/` no tiene hoy ninguna escritura, pero el día que la tenga no debe nacer abierta por un patrón demasiado ancho. **Sin `@PreAuthorize`**: la ausencia es deliberada y el comentario del controlador lo dice.

## 6. Auditoría

**Ninguna.** Es una lectura pública, como el hotlink: no hay actor que registrar ni cambio que anotar. `request_log` la registra como a toda petición.

## 7. Transaccionalidad

`@Transactional(readOnly = true)` en el servicio, **una sentencia**: `SELECT id, content_type, content FROM product_images WHERE id = ?`. **Nada sobre `products`** (`CA-PM-259`).

**Los bytes se cargan enteros en memoria** —hasta cinco megas— y se escriben en la respuesta. No se hace *streaming* desde la base: `bytea` no lo admite sin objetos grandes, y cinco megas por petición con ciento veinte por minuto por origen es un tope que la máquina aguanta sin pensar.

## 8. Impacto sobre otros módulos

**Dos líneas en `SP`**: `SecurityConfig` y `RateLimitFilter`. Las dos con su prueba en `EndpointPermissionsIT` y en la suite de la cota.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Colgar la ruta de `ProductController`** | Es la única que no devuelve JSON; sola se ve, mezclada se pierde entre veinte operaciones que sí |
| **Comprobar el producto antes de servir** | Decidido en contra (`requirements/pm.md` §5.2.9): una sentencia más por `<img>` para proteger algo que no identifica a nadie. La salida está escrita en `spec.md` §14.2 |
| **`ETag` y `304`** | Redundante con `immutable`: el navegador no revalida. Y un `ETag` invitaría a alguien a quitar `immutable` «porque ya hay `ETag`», que es peor |
| **Servir con `Content-Type` de la cabecera de la subida** | Se descartó en `RF-PM-014`: el tipo es el detectado, y esta ruta lo devuelve tal cual |
| **Una `CSP` en la respuesta** | No es una página: es una imagen de mapa de bits que no ejecuta nada. `nosniff` y la lista de tres tipos bastan (`spec.md` §14.3) |
| **Servir desde el sistema de archivos con `spring.web.resources`** | Los bytes están en la base por decisión (`requirements/pm.md` §5.2.9) |
| **`StreamingResponseBody`** | Cinco megas no lo justifican, y `bytea` no se lee a trozos |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien amplía `RUTAS_PUBLICAS` con `/api/v1/product-images/**`** y una futura escritura nace abierta | La declaración es por método (`GET`), y `EndpointPermissionsIT` la lista como pública **solo en `GET`** |
| 2 | **Se quita `immutable` «para que se vea la portada nueva»** sin entender que la nueva es otra dirección | La prosa de la `@Operation` y `CA-PM-255` comprueban la cabecera exacta |
| 3 | **Se añade `image/svg+xml` a `produces`** porque «así se ve el logo» | `ck_product_images_content_type` no lo admite, `ImageSignature` no lo detecta, y `security.md` §6 dice por qué |
| 4 | **La cota se cuenta por URI** y un recorrido de identificadores no topa nunca | La regla del filtro usa la **familia** como llave, y `CA-PM-260` la prueba con identificadores distintos |
| 5 | **Un volcado de `request_log` con miles de `GET` de imágenes** ensucia las lecturas de auditoría | Es lo mismo que el hotlink; se anota, y la caché de un año es lo que lo acota |

## 11. Estrategia de prueba

- **Integración de API** (`ProductImageIT`): los seis criterios de `spec.md` §12. **La que define el requerimiento**: `CA-PM-255` —bytes exactos, tipo detectado y las cinco cabeceras—, subiendo primero con `RF-PM-014` y leyendo después sin token.
- **De uniformidad**: `CA-PM-257` con un identificador inventado, uno reemplazado (subir dos veces, pedir el primero) y uno quitado (`RF-PM-015`).
- **De seguridad**: `EndpointPermissionsIT` con la ruta como pública en `GET`; la suite de la cota con la familia.
- **De sentencias**: la prueba cuenta **una** y ninguna sobre `products` (`CA-PM-259`).
- **De contrato**: `OpenApiContractIT` comprueba `security: []` y la respuesta binaria con los tres tipos.
