# PLAN — `RF-PM-013` Consultar la reseña propia sobre un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-013` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 14-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 14-09-2026 |

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`products:read-own-comments`** y no `products:comment` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `products:comment` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `products:comment`. Las menciones de `products:comment` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Enfoque

**Una consulta por el índice único, y nada más.**

`uq_product_comments_autor` garantiza que la reseña viva de una persona sobre un producto es a lo sumo una, de modo que «la mía» se resuelve con una lectura de índice que devuelve cero o una fila. El servicio no consulta el producto, no bloquea, no audita. Lo único que hay que decidir es la ruta —`/comments/mine` frente a `/comments/{commentId}`— y eso ya lo decidió `/products/available`.

## 2. Cambios de esquema

**Ninguno.** El índice parcial de `V87` es la consulta.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `ProductCommentRepository.findLiveByProductAndUser(productId, userId)` — `Optional`, sin bloqueo | `PM` |
| `domain/service` | `GetOwnProductCommentService` | `PM` |
| `interfaces` | `ProductCommentController` — `GET /api/v1/products/{id}/comments/mine` | `PM` |

`ProductCommentResponse` ya existe (`RF-PM-009`); esta lectura la reutiliza sin cambios.

## 4. Contrato de API

`GET /api/v1/products/{id}/comments/mine` — `products:read-own-comments`.

`200` con `ProductCommentResponse` —la forma del alta— o `404` con `EX-001`.

- **El identificador de la ruta es `UUID`**, con el convertidor del sistema: un malformado responde `400` (`VAL-001`). Aquí hay token, y la forma no es información para quien ya está dentro — es la asimetría deliberada con la lista pública.
- **`/mine` se declara antes que `/{commentId}` en el controlador** y, aunque Spring resuelve por especificidad y no por orden, la prueba `CA-PM-217` fija que `GET /comments/mine` no cae en el patrón de la variable: no hay `GET /comments/{commentId}`, de modo que el síntoma de un error aquí sería un `405` o un `400` por identificador inválido.

## 5. Autorización

`@PreAuthorize("hasAuthority('products:comment')")`. El actor sale de `CurrentActor`; **no hay parámetro de persona**, y por eso no hay comprobación de propiedad que hacer: la consulta ya lleva `user_id = :actor` **y aquí sí es correcto** — al revés que en la corrección y el retiro, donde filtrar por actor convertiría el `403` en `404`. La diferencia es que aquí no hay recurso ajeno que negar: la pregunta es sobre uno mismo.

## 6. Auditoría

**No audita.** Lectura propia.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Una sentencia**, siempre. `CA-PM-218` la cuenta.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Marcar `mine` en la lista pública** | La respuesta dejaría de ser la misma para todos (`RF-PM-012` §3) |
| **Devolver la existente en el `409` del alta** | Una lectura escondida en un error, y el front la necesita **antes** de intentar escribir, no después de fallar |
| **`200` con cuerpo nulo cuando no hay** | Dos formas de éxito para el front; el sistema ya dice `404` cuando el recurso no existe |
| **Consultar el producto para distinguir «no existe» de «no has opinado»** | Una sentencia más para darle a un cliente un dato que la oferta le oculta (`spec.md` `EX-001`) |
| **`GET /comments/{commentId}` genérico, y que el front lo busque en la lista** | Obliga a paginar la lista pública para encontrar la propia, y publicaría por el camino la relación reseña → actor |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien añade `?userId=`** «para soporte» | La spec lo excluye (§3); `EndpointPermissionsIT` y el contrato OpenAPI no declaran parámetros, y `fail-on-unknown` no aplica a la consulta — de modo que la única defensa es la revisión. Queda escrito |
| 2 | **La ruta literal cae en el patrón de la variable** | `CA-PM-217` |
| 3 | **Se devuelve una retirada** por olvidar el predicado | `findLiveByProductAndUser` lleva `deleted_at IS NULL` en el nombre y en la consulta; `CA-PM-213` la prueba con una retirada |

## 11. Estrategia de prueba

- **Integración de API** (`ProductCommentMineIT`): los siete criterios de `spec.md` §12. La que define el requerimiento es **`CA-PM-215`**: dos autores, el mismo producto, cada uno recibe la suya.
- **De número de sentencias**: una.
- **De ruta**: `CA-PM-217`, junto a la prueba que `RF-PM-007` ya tiene para `/available`.
