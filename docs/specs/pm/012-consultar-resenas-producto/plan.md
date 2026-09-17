# PLAN — `RF-PM-012` Consultar las reseñas de un producto, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-012` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 14-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 14-09-2026 |

---

## 1. Enfoque

**Un listado del sistema con la disciplina del hotlink.**

La paginación, la envoltura y el conteo son los de `shared/pagination`, como en todo listado. Lo que no es de un listado corriente es todo lo que viene de ser público: **un solo punto de salida para el `404`**, el identificador de la ruta leído como texto para que su forma no responda `400`, la proyección **sin ningún campo de persona** salvo dos cadenas, y una cota de tasa contada **por familia**. Cada una de esas cuatro decisiones tiene su prueba y ninguna es opcional.

## 2. Cambios de esquema

**Ninguno propio.** `ix_product_comments_product` (`V87`) está construido para esta consulta: `(product_id, created_at DESC, id DESC) WHERE deleted_at IS NULL` es exactamente el predicado y el orden de la página.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `ProductCommentQueryRepository` + `JpaProductCommentQueryRepository`: `findPageByProduct(productId, slice)` y `countLiveByProduct(productId)` — **nativas**, con `JOIN users` | `PM` |
| `domain/repository` | `ProductQueryRepository.isPurchasable(productId)` — existe, `ACTIVO`, no retirado. Una sola lectura de `products` por su clave | `PM` |
| `application` | `ProductCommentPublicItem` — `id`, `rating`, `comment`, `author { firstName, lastName }`, `createdAt`, `updatedAt` | `PM` |
| `domain/service` | `GetProductCommentsService` | `PM` |
| `interfaces` | `ProductCommentController` — `GET /api/v1/products/{id}/comments`, **sin `@PreAuthorize`** | `PM` |
| `shared/security` | La ruta en la lista **por método** de `SecurityConfig` (`GET`, con el patrón de un segmento), en `EndpointPermissionsIT` como pública a propósito, y la **familia** en `RateLimitFilter` | `shared` |

## 4. Contrato de API

`GET /api/v1/products/{id}/comments?page=0&size=20` — **público**.

```json
{
  "content": [
    {
      "id": "0192…",
      "rating": 5,
      "comment": "Subí a Oro y las señales llegan a tiempo.",
      "author": { "firstName": "Ana", "lastName": "Ruiz" },
      "createdAt": "2026-09-14T15:02:11Z",
      "updatedAt": "2026-09-14T15:02:11Z"
    }
  ],
  "totalElements": 1, "totalPages": 1, "page": 0, "size": 20, "totalIsExact": true
}
```

- **`author` es un registro propio de dos cadenas**, declarado en `ProductCommentPublicItem` y en ningún otro sitio. No se reutiliza ninguna proyección de persona de `SP` —ni `SellerRef` del hotlink, aunque tenga la misma forma— para que ampliar aquella no amplíe esta sin que nadie lo decida.
- **El identificador de la ruta se declara `String`**, como en el hotlink, y se convierte en el servicio: un UUID malformado responde `404` con **el mismo cuerpo** que el producto inexistente (`VAL-001`), y nunca el `400` que daría el convertidor de `shared/error`.
- **`page` y `size`** los valida `Pagination.resolver` con sus mensajes; ahí sí `400`.

### 4.1 La sentencia

```sql
SELECT c.id, c.rating, c.comment, c.created_at, c.updated_at,
       u.first_name, u.last_name
  FROM product_comments c
  JOIN users u ON u.id = c.user_id
 WHERE c.product_id = :producto AND c.deleted_at IS NULL
 ORDER BY c.created_at DESC, c.id DESC
 LIMIT :size OFFSET :offset
```

**El `JOIN` a `users` es SQL nativo en un repositorio de `PM`, y es el precedente de `RF-PM-002` con `memberships`**, firme desde `requirements/pm.md` v0.10.0: la regla de ArchUnit prohíbe importar repositorios y entidades de `SP`, no nombrar sus tablas en una sentencia. **Ninguna regla se decide con él** —quién es el autor lo dice `c.user_id`; el `JOIN` le pone nombre—, y la alternativa de un puerto de `SP` «nombre por identificador» llamado por fila sería el `N+1` de siempre, o un puerto por lote que existiría solo para esto.

**Y se seleccionan exactamente esas dos columnas de `users`.** No `u.*`, no el correo «por si acaso»: lo que no se selecciona no puede filtrarse a la respuesta (`RN-PM-030`), que es la misma disciplina con la que las consultas públicas no seleccionan `purchase_price`.

## 5. Autorización

**Ninguna en la ruta, y declarada en tres sitios**:

1. `SecurityConfig`: en la lista **por método**, `GET` sobre `/api/v1/products/*/comments`, junto a los catálogos públicos. **No en `RUTAS_PUBLICAS`**: la misma ruta responde a un `POST` que exige `products:comment`, y meterla entera dejaría al anónimo recibiendo `403` donde debe recibir `401`. El patrón de un segmento **no alcanza** a `/comments/mine` ni a `/comments/{commentId}`, y `EndpointPermissionsIT` lo comprueba con los tres.
2. `EndpointPermissionsIT`: en la lista de rutas públicas a propósito.
3. `security.md` §6: ya está.

## 6. Auditoría

**No audita.** Lectura pública y anónima, como el hotlink. La huella queda en `request_log`.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Tres sentencias**: el producto, la página, el total — y **una sola** cuando el producto no procede, que es lo que cuesta un recorrido de identificadores al azar. **El número no crece con el tamaño de la página**: el nombre viene en la misma sentencia (`CA-PM-209`).

## 8. Límite de tasa

**La familia `/api/v1/products/*/comments`, con la política `public-catalog`.** En `RateLimitFilter.reglaDe`, para un `GET` cuya ruta empieza por `/api/v1/products/` y termina en `/comments`, se devuelve `Regla("/api/v1/products/*/comments", ajustes.publicCatalog())`: **el ámbito es la familia** —un cubo para todos los productos, como el hotlink— y **el número es el de los catálogos** —120 por minuto—. No hace falta política nueva en `RateLimitSettings`: la naturaleza es la de un catálogo y lo único distinto es la llave.

**Por qué la familia y no la ruta**: la ruta lleva el identificador del producto. Contar por URI daría un cubo por producto, y quien recorriera identificadores buscando `200` no repetiría ninguno y no toparía jamás. Es exactamente el razonamiento del hotlink con los nombres de usuario ([`security.md` §5.5](../../../security.md)).

## 9. Impacto sobre otros módulos

**Ninguno en código.** Se lee `users` por `JOIN`, no por repositorio.

## 10. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Puerto de `SP` «nombre por identificador»**, por fila | `N+1`. Por lote, un puerto que existiría solo para esta lista; el `JOIN` tiene precedente firme |
| **Reutilizar `SellerRef` del hotlink para el autor** | Misma forma hoy, distinta persona: ampliar una ampliaría la otra sin decisión |
| **`UUID` en la ruta, con el convertidor del sistema** | Responde `400` al malformado, y en una ruta pública la forma es información (`spec.md` §11) |
| **`RUTAS_PUBLICAS` en lugar de la lista por método** | Abriría el `POST` a nivel de filtro: el anónimo recibiría `403` en vez de `401` |
| **Contar la cota por ruta, como los catálogos** | Un cubo por producto no corta un recorrido de identificadores |
| **Política de tasa propia** | Sería `public-catalog` con otro nombre; lo distinto es la llave, no el número |
| **Incluir `rating.average` y `count` en la envoltura** | Segunda forma del mismo dato; viajan con el producto |
| **Marcar la reseña del actor cuando hay token** | La respuesta dejaría de ser la misma para todos y de ser cacheable; `RF-PM-013` existe para eso |

## 11. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La proyección gana un campo de persona** «para que el front pinte el avatar» | `CA-PM-203` compara **el cuerpo entero** de cada elemento contra la forma esperada; un campo nuevo la rompe y obliga a decidirlo |
| 2 | **Los tres `404` se distinguen** para depurar | Un solo punto de salida en el servicio; `CA-PM-205` compara el cuerpo de los **cuatro** casos, malformado incluido |
| 3 | **El patrón público alcanza a `/comments/mine`** y la lectura de la propia se vuelve anónima | `EndpointPermissionsIT` exige `401` sin token en `/comments/mine` y en `PATCH`/`DELETE` de `/comments/{id}` |
| 4 | **La cota se cuenta por ruta** por copiar el bloque de los catálogos | `CA-PM-208`: tres identificadores distintos topan con la misma cota |
| 5 | **Una reseña con HTML se pinta sin escapar** en el hotlink | Fuera del backend; la prosa de la `@Operation` lo advierte al front, y `spec.md` de `RF-PM-009` §14.3 lo declara |

## 12. Estrategia de prueba

- **Integración de API** (`ProductCommentListIT`): los diez criterios de `spec.md` §12. **Las dos que definen el requerimiento**: `CA-PM-203` —el cuerpo entero del elemento— y `CA-PM-205` —los cuatro `404` iguales—.
- **De seguridad** (`EndpointPermissionsIT`): la ruta pública a propósito en `GET`; `401` en `POST` sin token, en `/comments/mine` y en `PATCH`/`DELETE` de `/comments/{id}`.
- **De límite de tasa** (`RateLimitIT`): el exceso desde un origen recibe `429`, y tres productos distintos comparten el cubo.
- **De número de sentencias**: tres, y una cuando el producto no procede; el número no cambia entre una página de uno y una de veinte.
- **Contrato**: la prosa de la `@Operation` dice que es público, que del autor solo viaja el nombre, que el `404` no distingue, y que **el texto no está saneado**.
