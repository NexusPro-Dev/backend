# TASKS — `RF-PM-013` Consultar la reseña propia sobre un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-013` |
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
| `T-01` | `ProductCommentRepository.findLiveByProductAndUser(productId, userId)`: `Optional`, sin bloqueo, con `deleted_at IS NULL` | `RF-PM-009 · T-05` | Integración: devuelve la viva y no la retirada; vacío si no hay | Pendiente |
| `T-02` | `domain/service/GetOwnProductCommentService`: actor del token, una consulta, `EX-001` si vacío. **No consulta el producto** | `T-01` | `CA-PM-213`, `CA-PM-214`, `CA-PM-218` | Pendiente |
| `T-03` | `ProductCommentController`: `GET /api/v1/products/{id}/comments/mine`, `@PathVariable UUID id`, `@PreAuthorize("hasAuthority('products:comment')")`, **declarado antes que `/{commentId}`** | `T-02` | `CA-PM-212`, `CA-PM-216`; la ruta entra en `EndpointPermissionsIT` con su permiso | Pendiente |
| `T-04` | **La prueba de la ruta literal**: `GET /comments/mine` responde `200` o `404` de negocio, nunca `405` ni `400` por identificador inválido | `T-03` | `CA-PM-217`, junto a la de `/products/available` en `ProductOfferIT` | Pendiente |
| `T-05` | **La prueba de los dos autores** (`ProductCommentMineIT`): dos personas, el mismo producto, cada una recibe la suya | `T-03` | `CA-PM-215`. Es la que define el requerimiento | Pendiente |
| `T-06` | Pruebas de los criterios restantes: sin reseña, retirada, producto inexistente —**mismo cuerpo**—, producto inactivo y retirado con reseña | `T-03` | `CA-PM-213`, `CA-PM-214` | Pendiente |
| `T-07` | Prueba de **número de sentencias**: una | `T-02` | `CA-PM-218` | Pendiente |
| `T-08` | Documentación OpenAPI. **La prosa dice** que responde sobre quien llama, que el `404` no dice nada del producto, y que sí responde sobre productos que ya no se venden | `T-03` | El contrato declara `200`, `400`, `403`, `404` y **ningún parámetro de consulta** | Pendiente |
| `T-09` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-PM-013` refleja el estado | Pendiente |

## 2. Orden de ejecución

Lineal: `T-01` → `T-02` → `T-03`, y las pruebas después. **`T-04` va junto a `T-03`**: si el patrón de la variable se come el literal, se ve en la primera petición y no al final.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-212` | `T-02`, `T-03` |
| `CA-PM-213`, `CA-PM-214` | `T-02`, `T-06` |
| `CA-PM-215` | `T-05` |
| `CA-PM-216` | `T-03` |
| `CA-PM-217` | `T-04` |
| `CA-PM-218` | `T-07` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-009 · T-05` (repositorio) y `T-07` (forma de respuesta) | 14-09-2026 | Responsable técnico | **Abierto** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
