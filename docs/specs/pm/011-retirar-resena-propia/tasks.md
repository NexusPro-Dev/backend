# TASKS — `RF-PM-011` Retirar la reseña propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-011` |
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
| `T-01` | `ProductComment.retirar(ahora)`: marca `deleted_at` y **no toca nada más**; `instantanea()` devuelve la fila completa | `RF-PM-009 · T-03` | Unitaria: tras retirar, puntuación y texto intactos; la instantánea tomada antes tiene `deletedAt` nulo | **Hecha el 14-09-2026** |
| `T-02` | `domain/service/DeleteProductCommentService`: resolver con `findLiveByIdAndProductForUpdate` (`EX-001`), **propiedad** (`EX-002`), **instantánea antes de marcar**, marcar, `DeletionEvent` `LOGICAL` con la constante **`MOTIVO_CONTENIDO_PROPIO = "Retirada por su autor"`**, todo en una transacción | `T-01`, `RF-PM-010 · T-03` | `CA-PM-193`, `CA-PM-194` | **Hecha el 14-09-2026** |
| `T-03` | `ProductCommentController`: `DELETE /api/v1/products/{id}/comments/{commentId}`, **sin `@RequestBody`**, `@PreAuthorize("hasAuthority('products:comment')")`, `204` | `T-02` | La ruta entra en `EndpointPermissionsIT` con su permiso; `CA-PM-201` envía un cuerpo y responde igual | **Hecha el 14-09-2026** |
| `T-04` | **LA PRUEBA DEL SUPERADMINISTRADOR** (`ProductCommentDeleteIT`): con `products:delete-comment`, sobre una reseña ajena | `T-03` | `CA-PM-196`: `403`, reseña viva, evento de seguridad. Y `CA-PM-195` con otro cliente. **Es la prueba que define el requerimiento**, y la que impide que la moderación llegue aflojando esta comparación | **Hecha el 14-09-2026** |
| `T-05` | Prueba de la fila de auditoría: `deletion_type = LOGICAL`, `reason` igual al literal de la spec, `snapshot` completo con `deletedAt` nulo, actor = autor | `T-03` | `CA-PM-194` | **Hecha el 14-09-2026** |
| `T-06` | Prueba del `404` uniforme: inexistente, **ya retirada** —segundo `DELETE` del autor—, de otro producto | `T-03` | `CA-PM-197` | **Hecha el 14-09-2026** |
| `T-07` | Pruebas de efecto: la retirada no está en la lista ni en la propia; `rating` del producto la descuenta —**nulo** si era la única—; el autor escribe otra con `201` | `T-03`, `RF-PM-012 · T-05`, `RF-PM-013 · T-03` | `CA-PM-198`, `CA-PM-199` | **Hecha el 14-09-2026** |
| `T-08` | Producto inactivo y retirado: el autor retira igual | `T-03` | `CA-PM-200` | **Hecha el 14-09-2026** |
| `T-09` | Concurrencia: dos `DELETE` simultáneos del autor, en `ProductCommentConcurrencyIT` | `T-03` | Un `204`, un `404`, **una** fila de auditoría | **Hecha el 14-09-2026** |
| `T-10` | Documentación OpenAPI. **La prosa dice** que no pide motivo y por qué (Art. V.13, contenido propio), que solo el autor puede, y que retirar dos veces responde `404` | `T-03` | El contrato declara `204`, `403`, `404` y **ningún cuerpo de petición** | **Hecha el 14-09-2026** |
| `T-11` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-08` | La fila de `RF-PM-011` refleja el estado | **Hecha el 14-09-2026** |

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03`, y **`T-04` y `T-05` inmediatamente después**: son las dos que definen el requerimiento — quién no puede, y qué queda escrito cuando el autor sí puede.

`T-07` depende de la lista y de la propia, y por eso este requerimiento va **el último** de los cinco (`requirements/pm.md` §6.1).

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-193` | `T-02`, `T-03` |
| `CA-PM-194` | `T-01`, `T-02`, `T-05` |
| `CA-PM-195`, `CA-PM-196` | `T-02`, `T-04` |
| `CA-PM-197` | `T-06` |
| `CA-PM-198`, `CA-PM-199` | `T-07` |
| `CA-PM-200` | `T-08` |
| `CA-PM-201` | `T-03` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-009` (tabla, entidad, repositorio) y de `RF-PM-010 · T-03` (la lectura con bloqueo) | 14-09-2026 | Responsable técnico | **Cerrado el 14-09-2026** |
| 2 | `T-07` necesita la lista (`RF-PM-012`) y la propia (`RF-PM-013`) construidas | 14-09-2026 | Responsable técnico | **Cerrado el 14-09-2026** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **incluida la prueba de `V4` sobre `ck_deletion_reason` sin cambios**.
- [ ] `CA-PM-196` prueba con un **superadministrador** y espera `403`.
- [ ] La fila de auditoría lleva el literal de `spec.md` §14.2.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
