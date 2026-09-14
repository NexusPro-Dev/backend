# PLAN — `RF-PM-015` Quitar la portada de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-015` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 14-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 14-09-2026 |

---

## 1. Enfoque

**La subida al revés, con la regla delante y un atajo antes de la regla.**

Los pasos son los de `UploadProductCoverService` sin el archivo: encontrar bloqueando, cambiar `cover_image_id`, borrar la imagen, auditar. Lo que se añade es `RN-PM-034` **en el agregado**: `Product.quitarPortada()` es quien sabe si el producto es un upgrade y si tiene icono, y quien lanza `VAL-002`; el servicio solo ordena los pasos y borra los bytes. Y el atajo: **sin portada no hay nada que hacer**, y se comprueba antes que la regla, en el mismo método del agregado, que devuelve un diff vacío.

## 2. Cambios de esquema

**Ninguno.** `V90` (`RF-PM-014`) ya tiene todo lo que hace falta: la clave foránea sin `ON DELETE` es precisamente lo que obliga a este servicio a soltar la imagen **antes** de borrarla.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `Product.quitarPortada()` → `Optional<UUID>` de la imagen que había, y el diff `cover_image_id`; **vacío y sin diff** si no había; `VAL-002` si es upgrade sin icono | `PM` |
| `domain/service` | **`RemoveProductCoverService`** | `PM` |
| `domain/repository` | Reutiliza `ProductImageRepository.deleteById` de `RF-PM-014` | `PM` |
| `interfaces` | `ProductController` — `DELETE /api/v1/products/{id}/cover` → `200` con `ProductDetailResponse` | `PM` |

## 4. Contrato de API

`DELETE /api/v1/products/{id}/cover` — `products:update`. Sin cuerpo. `200` con `ProductDetailResponse`.

- **Sin `@RequestBody`**: un cuerpo, si llega, ni se lee.
- **`200` y no `204`**, con el producto: `spec.md` §14.1.
- **La prosa de la `@Operation` dice tres cosas**: que un upgrade sin icono no puede quedarse sin portada y en qué orden se arregla; que sin portada responde igual y no escribe; y que la imagen se borra y su dirección deja de servir.

## 5. Autorización

`@PreAuthorize("hasAuthority('products:update')")`. Sin cambios en el catálogo ni en `SecurityConfig`. `EndpointPermissionsIT` la incorpora.

## 6. Auditoría

`ChangeEvent` `UPDATE` con solo `cover_image_id`: `{"before": <uuid>, "after": null}`. **Ninguna fila cuando no había portada** (`FA-001`), por el mismo criterio de `RF-PM-004` `CA-PM-038`.

## 7. Transaccionalidad

`@Transactional`. **Cuatro sentencias** cuando hay portada: el producto con `FOR UPDATE`, el `UPDATE` de `products`, el `DELETE` de la imagen, la auditoría. **Una** cuando no la hay. El `UPDATE` va antes que el `DELETE` por la clave foránea, como en la subida y al revés que en ninguna parte.

**Y el `UPDATE` se vuelca antes del `DELETE`**: con JPA, la entidad `Product` modificada y el `deleteById` del repositorio de imágenes son dos operaciones que Hibernate podría reordenar al vaciar la sesión —**los `DELETE` van al final del `flush` por defecto**, después de los `UPDATE`, de modo que el orden natural es el correcto—; el servicio hace `flush()` explícito entre las dos igualmente, para que el orden no dependa de una regla de Hibernate que nadie va a recordar.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`204` sin cuerpo, como `RF-PM-011`** | Allí desaparece una entidad; aquí queda un producto que cambió y el cliente lo repinta (`spec.md` §14.1) |
| **`409` para el upgrade sin icono** | El precedente del módulo para «el estado del producto no admite la operación» es `VAL` y `400` (`RF-PM-005` `VAL-003`); un `409` habla de concurrencia |
| **`404` cuando no hay portada** | «Quítala» sobre un producto sin portada ya está hecho; un `404` diría que el producto no existe, y existe |
| **Quitar la portada y vaciar el icono en una sola petición** | Serían dos campos en un `DELETE`, y `RN-PM-034` prohibiría exactamente esa combinación. El orden correcto son dos peticiones |
| **Conservar la fila de la imagen «por si acaso»** | Descartado en `requirements/pm.md` §5.2.9: no hay papelera |
| **Comprobar `RN-PM-034` en el servicio y no en el agregado** | El agregado es el único que ve tipo, icono y portada juntos, y es donde viven las otras dos caras de la regla (`create`, `update`). Tres sitios para una regla es la copia que se queda atrás |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Se comprueba la regla antes que «no hay portada»** y un upgrade viejo sin icono ni portada recibe `VAL-002` sin poder arreglarlo por aquí | El orden está en `spec.md` §8 y `CA-PM-253` lo prueba con ese producto exacto |
| 2 | **El `DELETE` de la imagen se vuelca antes que el `UPDATE`** y la clave foránea falla con un `500` | `flush()` explícito entre los dos (§7) y `CA-PM-249` comprueba que la fila desaparece |
| 3 | **Alguien relaja la regla para el bot «por simetría»** y le exige icono | `CA-PM-252` quita la portada de un bot y espera `200` |

## 11. Estrategia de prueba

- **Unitaria** (`ProductTest`): `quitarPortada` en un upgrade con icono, en uno sin icono (`VAL-002`, nombra `icon`), en un bot, y sin portada (vacío, sin diff, sin excepción — también en el upgrade sin icono).
- **Integración de API** (`ProductCoverIT`, la misma suite que la subida): los seis criterios de `spec.md` §12. **La que define el requerimiento**: `CA-PM-251` — el upgrade sin icono conserva la portada y no deja auditoría.
- **De efecto**: tras quitar, la dirección vieja responde `404` en `RF-PM-016` y las cuatro lecturas devuelven nulo.
