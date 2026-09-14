# TASKS — `RF-PM-010` Corregir la reseña propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-010` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 14-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `application/UpdateProductCommentRequest`: `Patchable<Integer> rating`, `Patchable<String> comment`, con `PatchableDeserializer` | `RF-PM-009 · T-07` | Unitaria de deserialización: ausente, nulo y con valor llegan como tres estados distintos | Pendiente |
| `T-02` | `ProductComment.corregir(Patchable rating, Patchable comment, ahora)`: **rechaza el nulo explícito** de cualquiera (`VAL-002`, `VAL-003`), aplica lo presente, recorta el texto, avanza `updatedAt` **solo si algo cambió**, y **devuelve** el mapa de cambios `{campo: {before, after}}` | `RF-PM-009 · T-03` | Unitaria: cambia uno, cambia los dos, no cambia nada —mapa vacío y `updatedAt` intacto—, nulo explícito lanza | Pendiente |
| `T-03` | `ProductCommentRepository.findLiveByIdAndProductForUpdate(commentId, productId)`: `SELECT … FOR UPDATE` con `deleted_at IS NULL` **y `product_id = :producto`** | `RF-PM-009 · T-05` | Integración: no encuentra la retirada ni la de otro producto; bloquea la fila | Pendiente |
| `T-04` | `domain/service/UpdateProductCommentService`: resolver (`EX-001`), **propiedad** (`EX-002`, `ForbiddenException`), corregir, escribir y auditar `UPDATE` **solo si el mapa no está vacío** | `T-01`, `T-02`, `T-03` | `CA-PM-184`, `CA-PM-189`, `CA-PM-190` | Pendiente |
| `T-05` | `ProductCommentController`: `PATCH /api/v1/products/{id}/comments/{commentId}`, `@PreAuthorize("hasAuthority('products:comment')")`, `200` | `T-04` | La ruta entra en `EndpointPermissionsIT` con su permiso | Pendiente |
| `T-06` | **LA PRUEBA DE LA PROPIEDAD** (`ProductCommentUpdateIT`): otro cliente con el permiso, y **un administrador con el permiso**, sobre una reseña ajena | `T-05` | `CA-PM-185`, `CA-PM-186`: `403`, reseña intacta, evento de seguridad registrado. **Es la prueba que define el requerimiento** | Pendiente |
| `T-07` | Prueba del `404` uniforme: inexistente, **retirada por su propio autor**, de otro producto — comparando el cuerpo | `T-05` | `CA-PM-187` | Pendiente |
| `T-08` | Pruebas de los criterios restantes: nulo explícito, sin cambios, `rating` del producto, producto inactivo y retirado | `T-05` | `CA-PM-188`, `CA-PM-189`, `CA-PM-191`, `CA-PM-192` | Pendiente |
| `T-09` | Concurrencia: dos correcciones simultáneas del mismo autor, en `ProductCommentConcurrencyIT` | `T-05` | Las dos responden `200`; gana la última; dos filas de auditoría | Pendiente |
| `T-10` | Documentación OpenAPI. **La prosa dice** que solo el autor puede y que el permiso no basta, que ningún campo admite nulo, y que un cuerpo vacío responde `200` sin cambiar nada | `T-05` | El contrato declara `200`, `400`, `403`, `404` | Pendiente |
| `T-11` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-08` | La fila de `RF-PM-010` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01` a `T-03` son independientes entre sí. `T-04` las junta.

**`T-06` se escribe inmediatamente después de `T-05`**, mientras se recuerda por qué el permiso no basta. Es la prueba que impide que una simplificación futura convierta `RN-PM-027` en letra muerta sin que nada falle.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-184` | `T-02`, `T-04` |
| `CA-PM-185`, `CA-PM-186` | `T-04`, `T-06` |
| `CA-PM-187` | `T-03`, `T-07` |
| `CA-PM-188` | `T-01`, `T-02`, `T-08` |
| `CA-PM-189` | `T-02`, `T-04`, `T-08` |
| `CA-PM-190` | `T-04` |
| `CA-PM-191`, `CA-PM-192` | `T-08` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-009` construido: entidad, repositorio, forma de respuesta y tabla | 14-09-2026 | Responsable técnico | **Abierto** hasta `RF-PM-009 · T-09` |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] `CA-PM-186` prueba con un **administrador** y espera `403`.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
