# SPEC — `RF-AC-006` Subir o reemplazar la portada de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-006` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que la categoría tenga una **imagen** con la que presentarse, y que se pueda cambiar sin dejar rastro de la anterior.

## 2. Contexto

Es `RF-PM-014` aplicado a la categoría, y hereda casi todo sin volver a decidirlo (`ac.md` §5.2.3, `RN-AC-004`): un archivo `multipart/form-data` con una sola parte `file`, **el tipo por los primeros bytes** y no por la cabecera —`JPEG`, `PNG` o `WebP`—, **hasta 5 MB**, guardado tal cual, **cada subida estrena identificador** y la reemplazada **se borra** en la misma transacción. Lo que este requerimiento decide es **dónde viven los bytes**: en **`academy_images`**, tabla de `AC` y no `product_images` de `PM`, porque `modules.md` §7 prohíbe que un módulo escriba la tabla de otro. Y **lo que sí se comparte**, que es el detector de firma y el tope, que pasan de `PM` a `shared/` para que las dos tablas no dupliquen el código aunque dupliquen el esquema.

**Sin regla cruzada.** La categoría se pinta siempre con color e icono (`RN-AC-003`), de modo que la portada es un adorno: se sube en cualquier momento y a cualquier categoría viva, y quitarla nunca se rechaza (`RF-AC-007`). Es el primer requerimiento del bloque 5 y el que **crea la tabla, mueve el detector y añade las restricciones** que las tres columnas `cover_image_id` —categoría, curso, módulo— esperaban desde sus migraciones.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Sube o reemplaza la portada |

## 4. Alcance

### 4.1 Incluye

- Recibir un archivo y convertirlo en la portada de una categoría viva: comprobar tipo y tamaño, guardar los bytes, señalarlos, borrar la anterior.
- Crear `academy_images` y las claves foráneas y unicidades de las tres columnas que la señalan.
- Mover `ImageSignature` y `CambioDePortada` de `PM` a `shared/`.
- Devolver la categoría en la forma de su detalle, con la dirección nueva.

### 4.2 No incluye

- **Tratar la imagen.** Ni recorte, ni redimensión, ni conversión, ni miniaturas (`RN-AC-004`).
- **Servirla.** `RF-AC-032`, que va segundo en el bloque.
- **Una galería.** Una imagen por categoría.
- **Rechazar por estado o contenido.** No hay nada que comprobar de la categoría salvo que esté viva.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-004` | La portada es un archivo en tabla propia; las condiciones de `RN-PM-033`; cada subida estrena identificador y la reemplazada se borra; opcional sin condición | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Una categoría retirada no recibe portada | `requirements/ac.md` §5.1 |
| `RN-PM-033` | Por extensión: `JPEG`, `PNG` o `WebP` por los bytes, hasta 5 MB, sin tratar | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Qué categoría | Ruta. Categoría **viva** |
| `file` | Sí | La imagen | Una sola parte `multipart/form-data`; de 1 byte a 5 242 880; primeros bytes de `JPEG`, `PNG` o `WebP`. **El nombre y el `Content-Type` de la parte se ignoran** |

### 6.2 Salida

`200` con la categoría en la forma del detalle (`RF-AC-003`), con `coverImageUrl` = `/api/v1/academy-images/{imageId}` de la imagen **nueva**.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `course-categories:update`; categoría viva; archivo válido.

**Postcondiciones:** existe una fila en `academy_images` con los bytes tal cual y el tipo detectado; `course_categories.cover_image_id` la señala; la fila anterior, si había, **no existe**; `audit_change_log` tiene un `UPDATE` de `course_categories` con `cover_image_id` antes y después.

## 8. Flujo principal

1. Llega la petición `multipart` con la parte `file`.
2. El sistema comprueba **el archivo antes que nada** y sin tocar la base: que hay bytes (`VAL-002`), que no pasan de 5 MB (`VAL-004`), que los primeros son de una imagen admitida (`VAL-003`) — en ese orden.
3. El sistema resuelve la categoría **viva**, bloqueándola (`EX-001`).
4. Inserta la imagen nueva, apunta la categoría a ella, **vuelca**, y borra la anterior si la había.
5. Registra el `UPDATE` y devuelve `200` con el detalle.

**El orden del paso 4 es el de la clave foránea**: apuntar a la nueva, soltar la vieja, borrar la vieja. Al revés, la clave muerde.

## 9. Flujos alternativos

### FA-001 — La categoría ya tenía portada

**Comportamiento:** se reemplaza: la nueva estrena identificador, la vieja se borra, y `coverImageUrl` cambia de dirección. Quien tenía la vieja en caché sigue viéndola hasta que recargue la ficha —y entonces pide otra dirección—.

### FA-002 — La imagen es la misma que ya tenía

**Comportamiento:** se guarda igual, con otro identificador: el sistema no compara bytes. Dos subidas iguales son dos filas en la historia y una en la tabla.

## 10. Excepciones

### EX-001 — La categoría no existe o está retirada

**Respuesta del sistema:** `404` — *«No existe una categoría viva con ese identificador.»*

### EX-002 — La petición no es `multipart/form-data`

**Respuesta del sistema:** `400` — *«La portada se envía como multipart/form-data con una parte llamada file.»* Es el `EX-003` de `RF-PM-014`, con su número.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | La parte `file` es obligatoria y no puede estar vacía | La imagen de portada es obligatoria. |
| `VAL-003` | Los primeros bytes son los de un `JPEG`, un `PNG` o un `WebP` | La portada debe ser una imagen JPEG, PNG o WebP. |
| `VAL-004` | El archivo no pasa de 5 MB | La portada no puede pesar más de 5 MB. |

Las tres del archivo nombran el campo `file` y son **las mismas de `PM`**, código a código: viven desde hoy en un solo sitio.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-155` | El sistema sube una portada `PNG` a una categoría sin ella con `200`; la respuesta trae `coverImageUrl` con la forma `/api/v1/academy-images/{uuid}`, y `academy_images` tiene una fila con los **mismos bytes** y `content_type = image/png` |
| `CA-AC-156` | El sistema **reemplaza** la portada: otra dirección, la fila anterior **ya no existe**, `academy_images` tiene **una** fila para esa categoría |
| `CA-AC-157` | `audit_change_log` tiene un `UPDATE` de `course_categories` con `cover_image_id` antes y después **y ningún otro campo** |
| `CA-AC-158` | El tipo se detecta **por los bytes**: un `JPEG` enviado como `image/png` y `foto.png` se guarda como `image/jpeg`; un `GIF`, un `SVG` y un texto enviados como `image/png` se rechazan con `VAL-003` nombrando `file` |
| `CA-AC-159` | Un archivo de **5 242 881 bytes** se rechaza con `VAL-004` y uno de **5 242 880** se admite; sin parte `file` o vacía, `VAL-002`; una petición que no es `multipart`, `400` |
| `CA-AC-160` | `404` sobre una categoría inexistente y sobre una retirada; un archivo rechazado **no deja fila** en `academy_images` ni en la auditoría |
| `CA-AC-161` | **`ImageSignature` y `CambioDePortada` viven en `shared/`** y `PM` los importa de allí: `ProductCoverIT`, `PackageCoverIT` y `ProductImageIT` siguen en verde sin cambio de comportamiento, y la regla de ArchUnit también |
| `CA-AC-162` | **Las tres columnas `cover_image_id`** —categoría, curso, módulo— tienen desde esta migración clave foránea a `academy_images` y unicidad: una imagen es portada de **una** fila como máximo, y una columna no puede señalar una imagen que no existe |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El archivo supera el tope del contenedor (6 MB) antes de llegar | Lo corta el contenedor y se traduce a `400` con `VAL-004`, como en `PM` |
| Dos subidas simultáneas a la misma categoría | La fila se bloquea; la segunda espera, ve la portada de la primera y la reemplaza. Queda la última, una fila en la tabla, dos registros de auditoría |
| Un `WebP` animado, un `JPEG` con EXIF | Se admiten tal cual; el sistema no interpreta ni rota (`RN-AC-004`) |
| Subir una portada de producto a una categoría, o al revés | Son dos tablas: la imagen nace señalada desde una entidad y solo una. **Una imagen de `product_images` nunca puede ser portada de una categoría**, porque la clave foránea es hacia `academy_images` |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se reutiliza `product_images`? | **No** (`ac.md` §5.2.3): `modules.md` §7. Misma tabla columna a columna, en el módulo que la escribe |
| 2 | ¿Qué se comparte entonces? | **El detector y el tope**, promovidos a `shared/`: `ImageSignature` —con la validación de los tres `VAL`— y `CambioDePortada`. Dos tablas, un solo sitio que sabe qué es una imagen admitida |
| 3 | ¿La categoría responde con su detalle o con `204`? | **Con su detalle**, como el producto y el paquete: lo que cambió es un campo, y el cliente repinta la ficha con lo que vuelve |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-PM-014` sobre la categoría, sin regla cruzada; **crea `academy_images` con las restricciones de las tres columnas que la señalan** y **mueve `ImageSignature` y `CambioDePortada` a `shared/`**. Los tres `VAL` del archivo son los de `PM`, en un solo sitio. | Responsable técnico |
