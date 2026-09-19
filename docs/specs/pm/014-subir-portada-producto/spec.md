# SPEC — `RF-PM-014` Subir o reemplazar la portada de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-014` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`products:set-cover`** y no `products:update` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `products:update` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `products:update`. Las menciones de `products:update` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Objetivo

Que un producto tenga **una foto con la que presentarse**, subida por administración, y que se pueda **cambiar** sin que quede rastro de la anterior ni una dirección vieja que enseñe otra cosa.

## 2. Contexto

**Es la primera vez que el sistema guarda un archivo.** Hasta hoy la frontera estaba escrita dos veces: el icono es un nombre y no una imagen (`RN-PM-016`), y el video es una dirección y no un video (`RN-PM-032`, §5.2.8). Con la portada la frontera **se mueve a propósito** y las seis decisiones que dicen hasta dónde están en [`requirements/pm.md` §5.2.9](../../../requirements/pm.md): se guarda **la imagen** y no su dirección, **en PostgreSQL**, **por un endpoint propio después del alta**, y se sirve **sin token por identificador de imagen**. No se repiten aquí; esta spec es la **subida**, que es la que crea la tabla, la columna y la comprobación de los bytes.

**Y es la que construye `RN-PM-034` en el alta y en la corrección.** «Un upgrade siempre tiene portada o icono» es una regla que solo tiene sentido cuando existe una portada con la que no tener icono, de modo que **nace aquí** y se aplica como enmienda a `RF-PM-001` y `RF-PM-004` (Art. I.7), como `RN-PM-031` nació con `RF-PM-009` y se aplicó a cuatro lecturas.

**Lo que esta operación no puede hacer nunca es dejar a un producto peor de lo que estaba.** Subir una portada añade algo con lo que pintarse; por eso no tiene ninguna condición de tipo ni de estado más allá de que el producto exista — al revés que quitarla (`RF-PM-015`), que es la única de las tres que tiene algo que rechazar.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador, con `products:set-cover` | Sube o reemplaza la portada de un producto |

**Es `products:update` y no un permiso propio**, porque la portada es **el valor de un campo del producto**, como la descripción o el icono: quien puede corregir el producto puede ponerle foto. Un permiso `products:cover` separaría una capacidad que nadie ha pedido separar, y el catálogo de permisos crece solo cuando alguien necesita conceder una cosa sin la otra.

## 4. Alcance

### 4.1 Incluye

- Recibir **un archivo** por `multipart/form-data` y convertirlo en la portada del producto.
- Comprobar, **por sus primeros bytes**, que es `JPEG`, `PNG` o `WebP`, y que **no pasa de 5 MB**.
- **Reemplazar** la portada anterior si la había: la nueva estrena identificador y la vieja **se borra**, en la misma transacción.
- Registrar el cambio en la auditoría de cambios de `products`, con el antes y el después del identificador.
- Responder con el producto, con `coverImageUrl` señalando la imagen nueva.
- **Crear `product_images` y `products.cover_image_id`** (`V90`).
- **Enmendar `RF-PM-001` y `RF-PM-004`** con `RN-PM-034`: el icono obligatorio en un upgrade sin portada.
- **Enmendar las cuatro lecturas** —`RF-PM-002`, `RF-PM-003`, `RF-PM-007`, `RF-PM-008`— con `coverImageUrl`.

### 4.2 No incluye

- **Quitar** la portada (`RF-PM-015`) ni **servirla** (`RF-PM-016`).
- **Tratar la imagen.** Ni recorte, ni redimensión, ni conversión de formato, ni miniaturas, ni comprobar sus dimensiones: los bytes se guardan **tal cual** y se devuelven tal cual. Cómo se ve una portada es del frontend.
- **Más de una imagen** por producto. Es la portada, no una galería.
- **Subir la imagen en el alta.** `RF-PM-001` sigue siendo JSON; la portada llega después (`requirements/pm.md` §5.2.9).
- **Conservar la portada reemplazada.** Se borra, y la auditoría conserva su identificador, no sus bytes.
- **Comprobar que la imagen sea razonable** —una portada apaisada, un tamaño mínimo—: el sistema no interpreta el contenido.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-033` | **La portada es un archivo —el primero que el sistema guarda— y se publica por su identificador** | `requirements/pm.md` §5.1 |
| `RN-PM-034` | **Un upgrade siempre tiene con qué pintarse: portada o icono** — nace aquí y se aplica en el alta y en la corrección | `requirements/pm.md` §5.1 |
| `RN-PM-016` | El icono solo existe en el upgrade — **enmendada**: obligatorio mientras no haya portada | `requirements/pm.md` §5.1 |
| `RN-PM-010` | El producto no desaparece — la portada de un retirado sigue existiendo y sirviéndose | `requirements/pm.md` §5.1 |
| Art. V.7 | Quién subió la portada vive en la auditoría, no en la tabla | `constitution.md` |
| Art. V.14 | La auditoría se escribe en la misma transacción | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del producto | Sí | A qué producto se le pone la portada | Va en la ruta |
| `file` | Sí | El archivo de imagen, una sola parte del `multipart/form-data` | **`JPEG`, `PNG` o `WebP` por sus primeros bytes**; de **1 byte a 5 242 880 bytes** (5 MB). La cabecera `Content-Type` de la parte y el nombre del archivo **se ignoran**: ni se validan ni se guardan |

**Una sola parte, y se llama `file`.** Una segunda parte se ignora. No hay campos de texto que acompañen a la imagen —ni descripción, ni posición, ni texto alternativo—: todo lo que el producto declara sobre sí mismo va por `RF-PM-004`.

**El tipo lo deciden los bytes, y es la decisión que más pesa de esta tabla.** La cabecera la escribe el cliente y se puede equivocar o mentir; la firma de un `PNG` —ocho bytes fijos— no. Lo que se guarda como `content_type` es **lo detectado**, y es lo que `RF-PM-016` devolverá al servirla.

### 6.2 Salida

El producto, en la misma forma que `RF-PM-004` devuelve (`ProductDetailResponse`), con `coverImageUrl` señalando la imagen recién subida: `/api/v1/product-images/{imageId}`.

**Responde con el producto y no con la imagen** porque lo que cambió es un campo del producto, y el cliente repinta la ficha con lo que vuelve — como tras cualquier corrección.

## 7. Precondiciones y postcondiciones

**Precondiciones:**

- El actor está autenticado y porta `products:set-cover`.
- El producto existe y **está vivo** (no retirado). Su estado —activo o inactivo— **no importa**.
- La petición trae una parte `file` con contenido.

**Postcondiciones:**

- `product_images` tiene **una fila nueva** con los bytes tal cual, el tipo detectado y `created_at`.
- `products.cover_image_id` señala esa fila, y `updated_at` avanza.
- Si había portada, **su fila ya no existe**: la dirección vieja responde `404` en `RF-PM-016`.
- `audit_change_log` tiene una fila `UPDATE` de `products` con `cover_image_id` —antes y después— y **nada más**: subir la portada no cambia ningún otro campo.
- Las cuatro lecturas devuelven la dirección nueva.

## 8. Flujo principal

1. Llega una petición `multipart/form-data` con el identificador en la ruta y la parte `file`.
2. El sistema comprueba que la parte existe y no está vacía. Si no: `VAL-002`.
3. El sistema comprueba el tamaño: más de 5 MB, `VAL-004`. **Se comprueba antes que el tipo** — ver §13.
4. El sistema **lee los primeros bytes** y detecta el tipo: `JPEG`, `PNG` o `WebP`. Si no es ninguno: `VAL-003`.
5. El sistema resuelve el producto **vivo** por su identificador, bloqueándolo. Si no: `EX-001`.
6. El sistema inserta la imagen nueva con identificador propio.
7. El sistema hace que el producto la señale, y se queda con el identificador anterior si lo había.
8. Si lo había, el sistema **borra** la fila anterior.
9. El sistema registra el cambio en la auditoría, en la misma transacción.
10. Devuelve `200` con el producto.

**Los pasos 2 a 4 van antes que el 5, y es a propósito.** Un archivo malo se rechaza sin tocar la base ni bloquear ninguna fila: la comprobación de los bytes es la más barata y la que más veces va a fallar, y rechazar antes de bloquear es lo que hace que un cliente que insiste con un archivo malo no le cueste nada al catálogo.

**El paso 8 va después del 7, y no es un detalle.** Con `uq_products_cover_image` y `fk_products_cover_image` sin `ON DELETE`, borrar la imagen **antes** de que el producto deje de señalarla violaría la clave foránea. El orden es: apuntar a la nueva, soltar la vieja, borrar la vieja.

## 9. Flujos alternativos

### FA-001 — El producto ya tenía portada

**Comportamiento:** **se reemplaza.** La nueva es otra fila con otro identificador; la vieja se borra; la auditoría lleva los dos identificadores. La dirección que las lecturas devuelven **cambia**, y eso es lo que permite que `RF-PM-016` sirva con caché inmutable sin que ningún navegador enseñe la portada anterior.

### FA-002 — El mismo archivo dos veces

**Comportamiento:** **son dos subidas**, dos filas —una tras otra—, dos identificadores y dos registros de auditoría. El sistema **no compara bytes**: detectar que la imagen es la misma que ya estaba costaría leer los cinco megas viejos en cada subida, para ahorrar una escritura que nadie hace por error.

### FA-003 — Un `BOT`

**Comportamiento:** **se sube igual.** La portada vale en los dos tipos y `RN-PM-034` no le exige nada al bot, que sigue sin declarar icono y se pinta con el suyo por omisión cuando no tiene foto (`requirements/pm.md` §5.2.9).

### FA-004 — Un producto inactivo o con portada y sin icono

**Comportamiento:** **se sube igual.** Subir nunca deja al producto peor; las condiciones de `RN-PM-034` las tiene `RF-PM-015`.

## 10. Excepciones

### EX-001 — Producto inexistente o retirado

**Respuesta del sistema:** `404` — *«No existe un producto vivo con ese identificador.»* El mismo cuerpo que `RF-PM-004` `EX-001`, por lo mismo: un retirado no se corrige, y ponerle foto es corregirlo.

### EX-002 — Sin permiso

**Respuesta del sistema:** `403`. Sin `products:set-cover` la ruta no se alcanza.

### EX-003 — La petición no es `multipart/form-data`

**Respuesta del sistema:** `400` — *«La petición debe enviar el archivo como multipart/form-data.»* Es la única forma de que el archivo llegue, y un `PUT` con JSON aquí es un cliente equivocado de ruta.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | La parte `file` es obligatoria y no puede estar vacía | La imagen de portada es obligatoria. |
| `VAL-003` | Los primeros bytes son los de un `JPEG`, un `PNG` o un `WebP` | La portada debe ser una imagen JPEG, PNG o WebP. |
| `VAL-004` | El archivo no pasa de 5 MB | La portada no puede pesar más de 5 MB. |

**Las tres del archivo nombran el campo `file`.** Y **ninguna mira la extensión ni la cabecera**: un `foto.png` que por dentro es un `GIF` es `VAL-003`, y un `foto.txt` que por dentro es un `JPEG` se admite y se guarda como `image/jpeg`.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-239` | El sistema sube una portada `PNG` a un producto sin ella con `200`, la respuesta trae `coverImageUrl` con la forma `/api/v1/product-images/{uuid}`, y `product_images` tiene una fila con los **mismos bytes** y `content_type = image/png` |
| `CA-PM-240` | El sistema **reemplaza** la portada: la respuesta trae **otra** dirección, la fila anterior **ya no existe**, y `product_images` tiene **una** fila para ese producto |
| `CA-PM-241` | El sistema registra en `audit_change_log` un `UPDATE` de `products` con `cover_image_id` —antes nulo y después el nuevo; y en el reemplazo, los dos identificadores— y **sin ningún otro campo** |
| `CA-PM-242` | El sistema detecta el tipo **por los bytes**: un archivo `JPEG` enviado con `Content-Type: image/png` y nombre `foto.png` se guarda como `image/jpeg`; un `GIF`, un `SVG` y un texto enviados como `image/png` se rechazan con `VAL-003` nombrando `file` |
| `CA-PM-243` | El sistema rechaza un archivo de **5 242 881 bytes** con `VAL-004`, y admite uno de **5 242 880** |
| `CA-PM-244` | El sistema rechaza la petición **sin parte `file`** y la que la trae **vacía** con `VAL-002`, y una petición que no es `multipart` con `400` |
| `CA-PM-245` | El sistema responde `404` sobre un producto inexistente y sobre uno **retirado**, con el mismo cuerpo; y **sube igual** sobre uno inactivo |
| `CA-PM-246` | El sistema sube la portada a un **`BOT`** y a un upgrade **sin icono**: ninguna condición de tipo ni de estado |
| `CA-PM-247` | Un archivo rechazado por `VAL-002`, `VAL-003` o `VAL-004` **no deja ninguna fila** en `product_images` ni en la auditoría, y el producto no cambia |
| `CA-PM-248` | Las cuatro lecturas —catálogo, detalle, oferta y hotlink— devuelven la dirección nueva tras subir, y **`coverImageUrl` presente y nulo** en un producto sin portada |

**Las enmiendas que esta spec dispara llevan sus criterios en su sitio**: `RN-PM-034` en el alta es `CA-PM-230` y `CA-PM-231` de `RF-PM-001`; en la corrección, `CA-PM-234` y `CA-PM-235` de `RF-PM-004`; y `coverImageUrl` en cada lectura, `CA-PM-232`, `CA-PM-233`, `CA-PM-237` y `CA-PM-238`.

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El archivo supera el tope del servidor de aplicaciones antes de llegar al controlador | El límite del contenedor se declara **por encima** del de negocio —6 MB frente a 5—, de modo que un archivo de 5,5 MB llega y se rechaza con `VAL-004`; uno de 50 MB lo corta el contenedor y **se traduce al mismo `VAL-004`**, no a un `500` ni a un `413` sin cuerpo. El cliente recibe siempre la misma respuesta por «demasiado grande» |
| El tamaño se comprueba antes que el tipo | A propósito: leer los primeros bytes de un archivo de 50 MB para decir que no es una imagen es trabajo tirado, y el mensaje de tamaño es el más útil de los dos para quien lo envió |
| Dos subidas simultáneas al mismo producto | La fila del producto se bloquea en el paso 5; la segunda espera, ve la portada de la primera y **la reemplaza**. Queda la última, y `product_images` tiene una fila. Dos registros de auditoría, encadenados |
| Un `WebP` con animación | Se admite: la firma es la de `WebP` y el sistema no interpreta el contenido. Es el único camino por el que puede entrar una portada animada, y se anota sin cerrarlo |
| Un `JPEG` progresivo, un `PNG` con transparencia, un `JPEG` con EXIF y orientación | Se admiten y se guardan tal cual. **El sistema no rota según EXIF**: cómo se ve es del frontend |
| Un archivo de exactamente 1 byte con firma inválida | `VAL-003`. El de cero bytes es `VAL-002` |
| El nombre del archivo lleva una ruta (`../../x.png`) | Se ignora: el nombre no se guarda ni se usa para nada |
| La imagen es la misma que el icono «pinta» | Nada que decidir: el icono es un nombre y la portada una imagen; conviven, y `RN-PM-034` solo pide que haya uno |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Por qué `PUT` y no `POST`? | **Porque la portada es un solo hueco por producto**, y subir es **poner** lo que hay en él: repetir la petición deja el mismo resultado —una portada, la última—, que es lo que `PUT` promete y `POST` no. `POST /cover` sugeriría que se puede añadir más de una |
| 2 | ¿Se comprueba que el actor sea quien registró el producto? | **No.** `products:set-cover` no distingue autores, como en `RF-PM-004`. La propiedad del dato es cosa de las reseñas (`RN-PM-027`), no del catálogo |
| 3 | ¿Por qué se rechaza el archivo antes de comprobar que el producto existe? | **Porque rechazar antes de bloquear es gratis y el orden contrario no.** Un cliente que insiste con un archivo malo no debe bloquear filas del catálogo en cada intento. El precio es que un archivo malo sobre un producto inexistente recibe `400` y no `404`, y se acepta |
| 4 | ¿Debería el sistema generar una miniatura para el listado? | **Hoy no** (`requirements/pm.md` §1.3). El listado devuelve la misma dirección que el detalle, y el navegador la cachea una vez. El día que el catálogo tenga cientos de productos por página se decidirá, y será otra columna y otra tabla, no una condición aquí |
| 5 | ¿Y si el frontend sube una imagen de 20 000 × 20 000 píxeles que pesa 4 MB? | **Se admite.** El sistema no interpreta el contenido y no tiene forma de saber las dimensiones sin decodificar la imagen, que es exactamente lo que se decidió no hacer. Cinco megas acotan lo que se guarda y lo que se sirve; lo que el navegador haga al pintarlos es del frontend |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 14-09-2026 | Redacción inicial. **Es la primera vez que el sistema guarda un archivo**, y la spec dice hasta dónde: `JPEG`, `PNG` o `WebP` **por sus primeros bytes** —la cabecera y el nombre se ignoran—, hasta 5 MB, y **sin tratar**. `PUT` porque la portada es un solo hueco; **el archivo se comprueba antes de bloquear el producto**, y el tamaño antes que el tipo; y **la reemplazada se borra** después de que el producto deje de señalarla, en ese orden y por la clave foránea. Es la spec que **construye `RN-PM-034` en el alta y en la corrección** como enmienda a `RF-PM-001` y `RF-PM-004`, y la que crea `product_images` y `cover_image_id`. Doce criterios, `CA-PM-239` a `CA-PM-252`. | Responsable técnico |
| 0.2.0 | 19-09-2026 | **Cambia el permiso: `products:set-cover` y no `products:update`** (`RF-SP-060`, `RN-SEG-014`, un permiso por operación; [`security.md`](../../../security.md) v0.63.0). Enmienda de Art. I.7 sin cambio de comportamiento: la misma operación, el mismo actor, un código propio sembrado por `V28` y dado a todo rol que portara `products:update`. | Responsable del proyecto |
