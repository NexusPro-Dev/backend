# TASKS — `RF-PM-029` Quitar la portada de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-029` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 16-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 16-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `ProductPackage.quitarPortada(ahora)`: «¿hay portada?» —si no, vacío y sin diff—; **sin regla**; devuelve el identificador que había y el diff `cover_image_id` | `RF-PM-028 · T-03` | Unitaria: con y sin portada; **nunca lanza** | **Hecha el 16-09-2026** |
| `T-02` | `domain/service/RemovePackageCoverService`: `findAliveByIdForUpdate` (`EX-001`), `quitarPortada`, **`flush()`**, `deleteById` de la imagen, `ChangeEvent` `UPDATE` de `product_packages` con solo `cover_image_id` — **nada de esto cuando no había portada**; `PackageDetailReader.leer` | `T-01`, `RF-PM-028 · T-06` | `CA-PM-362`, `CA-PM-363`, `CA-PM-364` | **Hecha el 16-09-2026** |
| `T-03` | `PackageController`: `DELETE /api/v1/packages/{id}/cover`, **sin `@RequestBody`**, `@PreAuthorize("hasAuthority('packages:update')")`, `200` con `PackageDetailResponse` | `T-02` | La ruta entra en `EndpointPermissionsIT` con su permiso | **Hecha el 16-09-2026** |
| `T-04` | **LA PRUEBA DE QUE SIEMPRE SE QUITA** (`PackageCoverIT`): `200`, `coverImageUrl` nulo y presente, `cover_image_id` nulo, la fila no existe, la dirección vieja responde `404`, auditoría con antes y después | `T-03` | `CA-PM-362`, `CA-PM-363`. **Es la prueba que define el requerimiento** | **Hecha el 16-09-2026** |
| `T-05` | Prueba de «sin portada»: `200` sin escribir — `updated_at` igual y `audit_change_log` sin crecer | `T-03` | `CA-PM-364` | **Hecha el 16-09-2026** |
| `T-06` | Prueba de efecto: un paquete activo y ofrecible sigue en la oferta y en el hotlink tras quitar, con `coverImageUrl` nulo | `T-03` | `CA-PM-365` | **Hecha el 16-09-2026** |
| `T-07` | Prueba de paquete: inexistente y retirado → `404`; inactivo → `200`; sin `packages:remove-cover` → `403` | `T-03` | `CA-PM-366` | **Hecha el 16-09-2026** |
| `T-08` | Documentación OpenAPI. **La prosa dice** que nunca responde `400` y por qué, que sin portada responde igual sin escribir, y que la imagen se borra | `T-03` | El contrato declara `200` con `PackageDetailResponse`, `400` solo por `VAL-001`, `403`, `404` y ningún cuerpo de petición | **Hecha el 16-09-2026** |
| `T-09` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-07` | La fila de `RF-PM-029` refleja el estado | **Hecha el 16-09-2026** |

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03`, y **`T-04` inmediatamente después**. `T-05` a `T-07` cierran los criterios; `T-08` y `T-09` la documentación.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-362`, `CA-PM-363` | `T-01`, `T-02`, `T-04` |
| `CA-PM-364` | `T-01`, `T-02`, `T-05` |
| `CA-PM-365` | `T-06` |
| `CA-PM-366` | `T-07` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-028` (columna, agregado, `CambioDePortada`) | 16-09-2026 | Responsable técnico | **Cerrado el 16-09-2026** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] `CA-PM-362` comprueba que **ningún** estado del paquete rechaza la operación.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
