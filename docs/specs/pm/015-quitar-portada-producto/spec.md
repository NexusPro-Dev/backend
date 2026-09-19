# SPEC — `RF-PM-015` Quitar la portada de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-015` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`products:remove-cover`** y no `products:update` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `products:update` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `products:update`. Las menciones de `products:update` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Objetivo

Que un producto **vuelva a pintarse con su icono** —o con el que el frontend le pone por omisión, si es un bot— sin dejar la imagen huérfana en la base ni una dirección que siga sirviéndola.

## 2. Contexto

**Es la única de las tres operaciones de la portada que tiene algo que rechazar.** Subirla nunca deja al producto peor (`RF-PM-014`); servirla no cambia nada (`RF-PM-016`); quitarla puede dejar a un upgrade **sin nada con qué pintarse**, y eso es exactamente lo que `RN-PM-034` prohíbe. De modo que esta operación es donde la tercera cara de la regla se construye: **sin icono, la portada no se quita**. Las otras dos —el alta exige el icono, y con portada el icono se vacía— nacieron con `RF-PM-014` como enmiendas.

**Y es un `DELETE` que devuelve `200` con el producto**, no `204`. No se retira ninguna entidad —la imagen no lo es, [`requirements/pm.md` §10.5](../../../requirements/pm.md)—: se **vacía un campo** del producto, y el cliente repinta la ficha con lo que vuelve, como tras `RF-PM-004`. El verbo dice lo que hace con el hueco; la respuesta dice cómo queda el producto.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador, con `products:remove-cover` | Quita la portada de un producto |

El mismo permiso y por lo mismo que en `RF-PM-014`: la portada es el valor de un campo del producto.

## 4. Alcance

### 4.1 Incluye

- Vaciar `cover_image_id` y **borrar** la fila de la imagen, en la misma transacción.
- Rechazar el retiro en un **upgrade sin icono** (`RN-PM-034`).
- Registrar el cambio en la auditoría de cambios de `products`.
- **No escribir nada** cuando el producto no tiene portada: responder igual.
- Responder con el producto, con `coverImageUrl` nulo.

### 4.2 No incluye

- **Motivo.** No se retira una entidad; se vacía un campo, y ningún campo pide motivo para vaciarse.
- **Conservar la imagen** para volver a ponerla. Quien quiera la misma foto la sube otra vez.
- **Vaciar el icono a la vez.** Son dos peticiones —`RF-PM-004` y esta—, y el orden lo decide `RN-PM-034`: primero se sube o se pone lo otro, después se quita esto.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-034` | **Un upgrade siempre tiene con qué pintarse: portada o icono** — sin icono, la portada no se quita | `requirements/pm.md` §5.1 |
| `RN-PM-033` | **La portada es un archivo** — la reemplazada, y aquí la quitada, **se borra** | `requirements/pm.md` §5.1 |
| `RN-PM-016` | El icono solo existe en el upgrade — el bot no lo declara, y por eso la regla no le alcanza | `requirements/pm.md` §5.1 |
| Art. V.7 | Quién quitó la portada vive en la auditoría | `constitution.md` |
| Art. V.14 | La auditoría se escribe en la misma transacción | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del producto | Sí | A qué producto se le quita la portada | Va en la ruta |

**Sin cuerpo.** Un `DELETE` con cuerpo se ignora, como en `RF-PM-011`.

### 6.2 Salida

`200` con el producto (`ProductDetailResponse`), con `coverImageUrl` **presente y nulo**.

## 7. Precondiciones y postcondiciones

**Precondiciones:**

- El actor está autenticado y porta `products:remove-cover`.
- El producto existe y está **vivo**.
- Si es un `UPGRADE_MEMBRESIA` **con portada**, tiene icono.

**Postcondiciones:**

- `products.cover_image_id` es nulo y `updated_at` avanza — **solo si había portada**.
- La fila de `product_images` **ya no existe**: su dirección responde `404` en `RF-PM-016`.
- `audit_change_log` tiene una fila `UPDATE` con `cover_image_id` —antes el identificador, después nulo— **solo si había portada**.
- Las cuatro lecturas devuelven `coverImageUrl` nulo.

## 8. Flujo principal

1. Llega una petición con el identificador del producto.
2. El sistema resuelve el producto **vivo** por su identificador, bloqueándolo. Si no: `EX-001`.
3. Si el producto **no tiene portada**: devuelve `200` con el producto, **sin escribir nada** (`FA-001`).
4. Si es un upgrade **sin icono**: `VAL-002`.
5. El sistema se queda con el identificador de la imagen y deja de señalarla.
6. El sistema **borra** la fila de la imagen.
7. El sistema registra el cambio en la auditoría, en la misma transacción.
8. Devuelve `200` con el producto.

**El paso 3 va antes que el 4.** Un upgrade viejo sin icono y sin portada —los hay, `requirements/pm.md` §5.2.9— recibe un `200` sin cambios y no un `VAL-002` por una regla que no puede cumplir con esta operación: no hay portada que quitar, y la regla habla de quitarla.

## 9. Flujos alternativos

### FA-001 — El producto no tiene portada

**Comportamiento:** `200` con el producto tal cual, **sin fila de auditoría ni cambio de `updated_at`**. «Quítala» sobre un producto sin portada ya ha conseguido lo que quería, y registrar un cambio que no cambió nada es el ruido que `RF-PM-004` `CA-PM-038` decidió no escribir.

### FA-002 — Un `BOT`

**Comportamiento:** **se quita siempre.** El bot no declara icono (`RN-PM-016`) y se pinta con el suyo por omisión, de modo que `RN-PM-034` no tiene nada que exigirle. Es la mitad del bot que §5.2.9 explica: la portada le es opcional **sin condición**.

### FA-003 — Un upgrade con portada y con icono

**Comportamiento:** se quita, y el producto vuelve a pintarse con el icono. Es el caso para el que existe la operación.

### FA-004 — Un producto inactivo o retirado

**Comportamiento:** inactivo, **se quita igual** — el estado no importa. Retirado, `EX-001`: un retirado no se corrige.

## 10. Excepciones

### EX-001 — Producto inexistente o retirado

**Respuesta del sistema:** `404` — *«No existe un producto vivo con ese identificador.»* El mismo cuerpo que `RF-PM-004` y `RF-PM-014`.

### EX-002 — Sin permiso

**Respuesta del sistema:** `403`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Un upgrade **sin icono** no puede quedarse sin portada | Un upgrade sin icono no puede quedarse sin portada: declare primero el icono. |

**`VAL-002` nombra el campo `icon`**, que es lo que falta, y no `cover`, que es lo que se pide. El mensaje dice qué hacer, porque el camino tiene un orden: `RF-PM-004` con el icono, y después esta operación.

**Es un `400` y no un `409`**, y sigue el precedente de `RF-PM-005` `VAL-003`: activar sin descripción también es un estado del producto que no admite la operación, y el módulo lo numera como validación. Un `409` diría «alguien más cambió algo», y aquí nadie cambió nada — el producto está como está.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-249` | El sistema quita la portada de un upgrade **con icono** con `200`, `coverImageUrl` nulo y presente, `cover_image_id` nulo, y la fila de `product_images` **ya no existe**: su dirección responde `404` |
| `CA-PM-250` | El sistema registra en `audit_change_log` un `UPDATE` con `cover_image_id` —antes el identificador, después nulo— y sin ningún otro campo |
| `CA-PM-251` | El sistema **rechaza** quitar la portada de un upgrade **sin icono** con `VAL-002` nombrando `icon`: la portada sigue, la fila de la imagen sigue, y no hay auditoría |
| `CA-PM-252` | El sistema quita la portada de un **`BOT`** —que nunca tiene icono— con `200` |
| `CA-PM-253` | Sobre un producto **sin portada** —incluido un upgrade viejo sin icono ni portada— responde `200` con el producto **sin escribir nada**: `updated_at` no avanza y `audit_change_log` no crece |
| `CA-PM-254` | El sistema responde `404` sobre un producto inexistente y sobre uno retirado; y quita la portada de uno **inactivo** |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Vaciar el icono y quitar la portada «a la vez» desde dos pestañas | Dos peticiones, dos bloqueos del producto en serie. La que llegue segunda encuentra el estado que dejó la primera y `RN-PM-034` la rechaza: **nunca quedan las dos** |
| Quitar la portada y subir otra de inmediato | Dos operaciones; la subida no tiene condición. Entre las dos, el producto se pinta con el icono |
| Dos `DELETE` simultáneos | El bloqueo los serializa; el segundo no encuentra portada y responde `200` sin escribir. Una fila de auditoría |
| El upgrade tiene icono y la portada; alguien quita la portada y **después** intenta vaciar el icono | `RF-PM-004` lo rechaza con `VAL-010`. Es la misma regla vista desde la otra operación |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Por qué `200` con el producto y no `204`? | **Porque no se retira una entidad, se vacía un campo**, y lo que el cliente necesita después es el producto tal como queda — la ficha con el icono en lugar de la foto. `RF-PM-011` responde `204` porque la reseña deja de existir; aquí el producto sigue, y cambió |
| 2 | ¿Debería quitarse la portada automáticamente al retirar el producto? | **No.** `RN-PM-010` dice que el producto no desaparece, y su portada es parte de lo que era: el detalle de un retirado la sigue enseñando (`RF-PM-003`). Borrarla al retirar sería perder, sin que nadie lo pidiera, la única copia de la imagen |
| 3 | ¿Y si administración quiere «volver a la anterior»? | **La sube otra vez.** No hay papelera: conservar imágenes por si acaso es lo que §5.2.9 descartó |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 14-09-2026 | Redacción inicial. **Es la única de las tres operaciones de la portada con algo que rechazar**: un upgrade sin icono no se queda sin portada (`RN-PM-034`, `VAL-002`, `400` como `RF-PM-005` `VAL-003` y no `409`). **Sin portada responde `200` sin escribir nada**, y esa comprobación va **antes** que la regla, para que un upgrade viejo sin icono ni portada no reciba un rechazo por algo que esta operación no puede arreglar. **`DELETE` que responde `200` con el producto**: se vacía un campo, no se retira una entidad. La imagen quitada **se borra**. Seis criterios, `CA-PM-249` a `CA-PM-254`. | Responsable técnico |
| 0.2.0 | 19-09-2026 | **Cambia el permiso: `products:remove-cover` y no `products:update`** (`RF-SP-060`, `RN-SEG-014`, un permiso por operación; [`security.md`](../../../security.md) v0.63.0). Enmienda de Art. I.7 sin cambio de comportamiento: la misma operación, el mismo actor, un código propio sembrado por `V28` y dado a todo rol que portara `products:update`. | Responsable del proyecto |
