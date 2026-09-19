# PLAN — `RF-PM-010` Corregir la reseña propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-010` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 14-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 14-09-2026 |

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`products:update-comment`** y no `products:comment` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `products:comment` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `products:comment`. Las menciones de `products:comment` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Enfoque

**Una corrección con `Patchable`, como `RF-PM-004`, y una comparación que el permiso no hace.**

La forma se hereda entera del `PATCH` del producto —los campos que no vienen no cambian, la auditoría registra el antes y el después, la fila se bloquea antes de leerla—, con una simplificación: **ningún campo admite el nulo explícito**, porque los dos son obligatorios en la columna. Lo que este plan añade y aquel no tenía es el **paso de propiedad**: después de encontrar la reseña y antes de tocarla, `user_id` contra el actor del token. Es una línea, y es la línea que sostiene `RN-PM-027`.

## 2. Cambios de esquema

**Ninguno.** `product_comments` la crea `RF-PM-009` (`V87`) con todo lo que esta operación necesita.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `UpdateProductCommentRequest` — `Patchable<Integer> rating`, `Patchable<String> comment`, con el deserializador de `RF-SP-027` | `PM` |
| `domain/models` | `ProductComment.corregir(rating, comment, ahora)` — aplica lo presente y **devuelve si algo cambió** | `PM` |
| `domain/repository` | `ProductCommentRepository.findLiveByIdAndProductForUpdate(commentId, productId)` — `SELECT … FOR UPDATE` sobre la viva | `PM` |
| `domain/service` | `UpdateProductCommentService` | `PM` |
| `interfaces` | `ProductCommentController` — `PATCH /api/v1/products/{id}/comments/{commentId}` | `PM` |

## 4. Contrato de API

`PATCH /api/v1/products/{id}/comments/{commentId}` — `products:update-comment` **y ser el autor**.

```json
{ "rating": 4 }
```

`200` con `ProductCommentResponse`, la misma forma que el alta, con `updatedAt` avanzado.

- **Los tres estados de cada campo se reducen a dos.** `Patchable` distingue ausente, nulo y con valor; aquí el nulo explícito de cualquiera de los dos es `VAL-002` o `VAL-003` con `400`. Se conserva `Patchable` en lugar de `Integer`/`String` a secas **para que el nulo se rechace con el mensaje de la spec** y no se confunda con ausente — que es exactamente el fallo silencioso que `RF-SP-027` pagó con `Optional`.
- **Cuerpo vacío**: `400` (`VAL-005`), como en `RF-PM-004` — el módulo entero corrige con «al menos uno»; el cuerpo **sin cambios de valor** es el que responde `200` sin escribir. **Campos desconocidos**: `400`, por `fail-on-unknown-properties` — y con eso `productId` y `userId` en el cuerpo quedan rechazados sin una línea propia.
- **La puntuación se deserializa con un entero estricto** (`PatchableRatingDeserializer`): Jackson convierte `4.5` en `4` por omisión, y la spec dice que se rechaza.

## 5. Autorización

**Dos capas, y la segunda es la que importa.**

1. `@PreAuthorize("hasAuthority('products:comment')")` — habilita.
2. En el servicio, después de resolver la fila: `if (!resena.esDe(actor)) throw new ForbiddenException("EX-002", …)` — autoriza.

`ForbiddenException` responde `403` y el manejador global lo registra en la **auditoría de seguridad con severidad alta**, como toda denegación. Es la «verificación de propiedad del dato» de [`security.md` §6](../../../security.md): un permiso concede la capacidad de ejecutar una acción, no el derecho sobre un registro concreto.

!!! danger "El orden existe → es tuya no se invierte, y la prueba lo fija"

    Comprobar la propiedad **antes** de saber si la reseña existe no se puede —no hay fila que comparar—, y comprobar «existe **y** es mía» en una sola consulta con `AND user_id = :actor` **haría que la ajena respondiera `404`**. Sería un cambio de contrato silencioso: `CA-PM-185` y `CA-PM-186` esperan `403`, y una consulta que filtre por actor las rompería con el mensaje equivocado.

## 6. Auditoría

`ChangeEvent` con `action = UPDATE` y en `changes` **solo los campos que cambiaron**, cada uno con `{ before, after }` — la forma de `RF-PM-004`. **Si nada cambió, no hay evento**: una fila de auditoría que dice «de 4 a 4» es ruido, y `FA-003` lo declara.

## 7. Transaccionalidad

`@Transactional`. **Tres sentencias cuando algo cambia** —la reseña con `FOR UPDATE`, el `UPDATE`, la auditoría— y **una** cuando no. El bloqueo de fila es lo que hace que dos correcciones simultáneas del autor se apliquen en orden y gane la última, en lugar de perderse una.

**No se consulta el producto.** La reseña ya lo referencia y la operación no depende de su estado (`FA-002`): consultarlo sería una sentencia para no decidir nada.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`PUT` con los dos campos obligatorios** | Obliga al front a reenviar el texto entero para cambiar una estrella, y rompe la forma del módulo, que corrige con `PATCH` |
| **Resolver la fila con `AND user_id = :actor`** | La ajena respondería `404` en lugar de `403`; ver §5 |
| **Consulta propia en la URL: `PATCH /comments/mine`** | Elimina el `commentId` de la ruta y con él la comprobación de propiedad… y también la posibilidad de que un cliente cachee o comparta la referencia. Se descartó por coherencia con el retiro, que necesita el identificador para que la lista pública y las dos escrituras hablen de lo mismo |
| **Aceptar el cuerpo vacío con `200`** | Era lo que el plan decía al aprobarse; se descartó al construir porque `RF-PM-004` lo rechaza con «al menos uno» y el módulo debe corregir igual en las dos operaciones. Lo que sí se acepta con `200` es el cuerpo sin cambios de valor |
| **Auditar también las correcciones sin cambio** | Ruido en `audit_change_log`, sin ninguna pregunta que responda |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien «simplifica» el servicio quitando la comparación de propiedad**, y el permiso pasa a bastar | `CA-PM-185` y **`CA-PM-186` con un administrador**: las dos esperan `403` y la reseña intacta. Sin ellas, el cambio no fallaría — silenciaría |
| 2 | **El nulo explícito se trata como ausente** y una petición con `"rating": null` responde `200` sin hacer nada | `Patchable` con su deserializador, y `CA-PM-188` envía el nulo y espera `400` |
| 3 | **`updatedAt` avanza sin cambio de valor** | `corregir()` devuelve si algo cambió y el servicio no escribe si no; `CA-PM-189` lo comprueba con el mismo cuerpo dos veces |
| 4 | **Pérdida de una corrección concurrente** | `FOR UPDATE` en la lectura; el caso límite de dos correcciones simultáneas lo declara y la prueba de concurrencia de `RF-PM-009` se amplía con una corrección cruzada |

## 11. Estrategia de prueba

- **Unitaria**: `ProductComment.corregir` — cambia uno, cambia los dos, no cambia nada; `esDe(actor)`.
- **Integración de API** (`ProductCommentUpdateIT`): los nueve criterios de `spec.md` §12. **La que define el requerimiento es `CA-PM-186`**: un administrador con `products:update-comment` sobre una reseña ajena → `403`, fila intacta, evento de seguridad registrado.
- **Del `404` uniforme**: inexistente, retirada —por su propio autor— y de otro producto, comparando el cuerpo.
- **De auditoría**: `UPDATE` con `before`/`after` solo de lo tocado; ausencia de fila cuando no cambió nada.
- **De `rating` del producto**: corregir la puntuación mueve `average` en el detalle en la misma transacción (`CA-PM-191`).
