# PLAN — `RF-PM-011` Retirar la reseña propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-011` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 14-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 14-09-2026 |

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`products:delete-comment`** y no `products:comment` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `products:comment` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `products:comment`. Las menciones de `products:comment` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Enfoque

**El retiro del producto sin el motivo, con la comparación de propiedad delante, y un `DELETE` de verdad.**

Los cinco pasos son los de `DeleteProductService` —encontrar bloqueando, instantánea, marcar, registrar en la misma transacción— menos el primero, que allí es «el motivo, antes que nada». Aquí no hay motivo que leer (`RN-PM-029`) y en su lugar entra el paso de propiedad de `RF-PM-010`. **Lo que se escribe en `audit_deletion_log.reason` es una constante**, y esa constante es una decisión del Art. V.13 enmendado que la spec declara y este plan implementa sin tocar el esquema de la auditoría.

## 2. Cambios de esquema

**Ninguno.** Ni en `product_comments` (`V87`) ni en `audit_deletion_log`: `ck_deletion_reason` sigue exigiendo contenido en toda baja `LOGICAL`, y **lo tiene** ([`architecture.md` §6.6.3](../../../architecture.md)).

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `ProductComment.retirar(ahora)` e `instantanea()` | `PM` |
| `domain/service` | `DeleteProductCommentService`, con la constante `MOTIVO_CONTENIDO_PROPIO = "Retirada por su autor"` | `PM` |
| `domain/repository` | Reutiliza `findLiveByIdAndProductForUpdate` de `RF-PM-010` | `PM` |
| `interfaces` | `ProductCommentController` — `DELETE /api/v1/products/{id}/comments/{commentId}` → `204` | `PM` |

## 4. Contrato de API

`DELETE /api/v1/products/{id}/comments/{commentId}` — `products:delete-comment` **y ser el autor**. Sin cuerpo. `204` sin cuerpo.

- **Es un `DELETE` y no un `POST /deletion`**, y el motivo está en [`requirements/pm.md` §9](../../../requirements/pm.md): el retiro del producto usa `POST` porque su cuerpo lleva el motivo y la RFC 9110 no garantiza que el cuerpo de un `DELETE` llegue; aquí no hay cuerpo, de modo que no hay nada que un intermediario pueda perder. El precedente es `DELETE /api/v1/users/{id}/membership`.
- **Un cuerpo, si llega, se ignora.** El controlador no declara `@RequestBody`, y con eso Spring ni lo lee. `CA-PM-201` lo prueba enviando uno.
- **La prosa de la `@Operation` dice tres cosas**: que no pide motivo y por qué, que solo el autor puede, y que retirar dos veces responde `404`.

## 5. Autorización

Las mismas dos capas de `RF-PM-010`: `@PreAuthorize("hasAuthority('products:comment')")` habilita; `esDe(actor)` en el servicio autoriza, y su `403` va a la auditoría de seguridad con severidad alta. **Sin excepción para nadie**: el superadministrador recibe el mismo `403`, y `CA-PM-196` lo prueba con él.

## 6. Auditoría

`DeletionEvent` con `deletionType = LOGICAL`, `reason = "Retirada por su autor"`, y `snapshot = instantanea()` **tomada antes de marcar**. `module = "PM"`, `entity = "product_comments"`.

!!! important "La constante es una decisión de la especificación, no un texto de conveniencia"

    El Art. V.13 admite suplir el motivo con un valor automático **solo** en las excepciones declaradas en la especificación, y `spec.md` §14.2 declara este texto. Cambiarlo es enmendar la spec. No es configuración —no vive en `application.yml`— porque no hay entorno en el que deba decir otra cosa.

    **Por qué no se deja nulo**: `ck_deletion_reason` lo rechazaría en una baja `LOGICAL`, y relajarlo abriría toda baja lógica a ir sin motivo. **Por qué no un `deletion_type` nuevo**: esa columna dice cómo se eliminó, y esto se eliminó lógicamente. Lo que distingue estas filas es el texto, y el actor de la fila **es** el autor de la instantánea.

## 7. Transaccionalidad

`@Transactional`. **Tres sentencias**: la reseña con `FOR UPDATE`, el `UPDATE` de `deleted_at`, la auditoría (Art. V.14: si la auditoría falla, el retiro falla). El bloqueo es lo que hace que dos `DELETE` simultáneos dejen **una** fila de auditoría: el segundo encuentra la fila ya retirada y responde `404` sin escribir.

**No se consulta el producto** (`FA-001`).

## 8. Impacto sobre otros módulos

**Ninguno en código.** En datos, la primera fila de `audit_deletion_log` cuyo motivo no lo escribió nadie: `RF-SP-012` la lista como cualquier otra, y quien la lea verá el texto fijo.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`POST /comments/{id}/deletion` por simetría con el producto** | Simetría sin causa: el `POST` existe allí por el cuerpo, y aquí no hay cuerpo. Un `POST` sin cuerpo que borra es un `DELETE` mal escrito |
| **Exigir motivo, como en `RF-PM-006`** | Decidido en contra por el responsable del proyecto (`requirements/pm.md` §5.2.7): produce «lo borro» en cada fila |
| **`reason` nulo con un `deletion_type` nuevo** | Ver §6 |
| **Borrado físico** | La lista y el promedio no la necesitan, pero la auditoría sí conserva la instantánea — y aun así el módulo entero retira lógicamente. Borrar físicamente rompería además `FA-002`: el autor no podría demostrar que existió |
| **Responder `409` al retirar dos veces, como el producto** | La reseña retirada no la devuelve nadie; para el autor «retirada» y «no existe» son lo mismo, y distinguirlo se lo confirmaría a un tercero (`spec.md` `EX-001`) |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La instantánea se toma después de marcar** y dice que la reseña ya estaba retirada | El orden de los pasos está escrito y `CA-PM-194` comprueba que `snapshot.deletedAt` es nulo |
| 2 | **Alguien añade una rama para la administración** «mientras llega la moderación» | `CA-PM-196` con un superadministrador espera `403`. La moderación es otra operación con motivo, y la spec lo dice |
| 3 | **El motivo fijo se convierte en cadena mágica repetida** | Una constante, en un solo servicio; la prueba compara contra el literal de la spec |
| 4 | **Un intermediario rechaza el `DELETE` sin cuerpo con `411`** | No ocurre con un `DELETE` sin `Content-Length`; se anota para el día que un proxy nuevo lo haga |

## 11. Estrategia de prueba

- **Integración de API** (`ProductCommentDeleteIT`): los nueve criterios de `spec.md` §12. **Las dos que definen el requerimiento**: `CA-PM-194` —la fila de auditoría con el motivo fijo y la instantánea entera— y `CA-PM-196` —el superadministrador recibe `403`—.
- **De efecto sobre el resto**: tras retirar, la lista pública no la trae, la propia responde `404`, `rating` del producto la descuenta —nulo si era la única— y el autor escribe otra con `201` (`CA-PM-198`, `CA-PM-199`).
- **Concurrencia**: dos `DELETE` simultáneos del autor → un `204`, un `404`, una fila de auditoría; se añade a `ProductCommentConcurrencyIT`.
- **De esquema**: la prueba de `V4` sobre `ck_deletion_reason` no cambia — es la demostración de que la excepción no relajó nada.
