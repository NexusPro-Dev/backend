# TASKS — `RF-PM-015` Quitar la portada de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-015` |
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
| `T-01` | `Product.quitarPortada()`: **primero** «¿hay portada?» —si no, vacío y sin diff—; **después** `RN-PM-034` —upgrade sin icono, `VAL-002` nombrando `icon`—; devuelve el identificador que había y el diff `cover_image_id` | `RF-PM-014 · T-05` | Unitaria: los cuatro casos de `plan.md` §11, incluido el upgrade viejo sin icono ni portada, que **no** lanza | **Hecha el 14-09-2026** |
| `T-02` | `domain/service/RemoveProductCoverService`: `findAliveByIdForUpdate` (`EX-001`), `quitarPortada`, **`flush()`**, `deleteById` de la imagen, `ChangeEvent` `UPDATE` con solo `cover_image_id` — **nada de esto cuando no había portada**; devolver el detalle | `T-01`, `RF-PM-014 · T-08` | `CA-PM-249`, `CA-PM-250`, `CA-PM-253` | **Hecha el 14-09-2026** |
| `T-03` | `ProductController`: `DELETE /api/v1/products/{id}/cover`, **sin `@RequestBody`**, `@PreAuthorize("hasAuthority('products:update')")`, `200` con `ProductDetailResponse` | `T-02` | La ruta entra en `EndpointPermissionsIT` con su permiso | **Hecha el 14-09-2026** |
| `T-04` | **LA PRUEBA DEL UPGRADE SIN ICONO** (`ProductCoverIT`): con portada y sin icono, `DELETE` → `VAL-002` nombrando `icon`; la portada sigue, la fila sigue, sin auditoría | `T-03` | `CA-PM-251`. **Es la prueba que define el requerimiento** | **Hecha el 14-09-2026** |
| `T-05` | Prueba del caso feliz: upgrade con icono → `200`, `coverImageUrl` nulo y presente, `cover_image_id` nulo, la fila no existe, la dirección vieja responde `404` en `RF-PM-016`, auditoría con antes y después | `T-03`, `RF-PM-016 · T-02` | `CA-PM-249`, `CA-PM-250` | **Hecha el 14-09-2026** |
| `T-06` | Prueba del bot: se quita con `200` | `T-03` | `CA-PM-252` | **Hecha el 14-09-2026** |
| `T-07` | Prueba de «sin portada»: producto sin portada y upgrade viejo sin icono ni portada → `200` sin escribir: `updated_at` igual y `audit_change_log` sin crecer | `T-03` | `CA-PM-253` | **Hecha el 14-09-2026** |
| `T-08` | Prueba de producto: inexistente y retirado → `404`; inactivo → `200` | `T-03` | `CA-PM-254` | **Hecha el 14-09-2026** |
| `T-09` | Documentación OpenAPI. **La prosa dice** que un upgrade sin icono no se queda sin portada y en qué orden se arregla, que sin portada responde igual sin escribir, y que la imagen se borra | `T-03` | El contrato declara `200` con `ProductDetailResponse`, `400`, `403`, `404` y ningún cuerpo de petición | **Hecha el 14-09-2026** |
| `T-10` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-08` | La fila de `RF-PM-015` refleja el estado | **Hecha el 14-09-2026** |

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03`, y **`T-04` inmediatamente después**: es la única prueba de las tres operaciones de la portada en la que el sistema dice que no.

`T-05` necesita `RF-PM-016` construido para comprobar el `404` de la dirección vieja; por eso este requerimiento va **el último** de los tres (`requirements/pm.md` §6.1).

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-249`, `CA-PM-250` | `T-01`, `T-02`, `T-05` |
| `CA-PM-251` | `T-01`, `T-04` |
| `CA-PM-252` | `T-06` |
| `CA-PM-253` | `T-01`, `T-02`, `T-07` |
| `CA-PM-254` | `T-08` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-014` (tabla, entidad, repositorio, `asignarPortada`) y de `RF-PM-016` para `T-05` | 14-09-2026 | Responsable técnico | **Cerrado el 14-09-2026** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] `CA-PM-253` prueba con un upgrade **sin icono ni portada** y espera `200` sin escritura.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
