# SPEC — `RF-PM-028` Subir o reemplazar la portada de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-028` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que un paquete tenga **una foto con la que presentarse**, subida por administración, y que se pueda **cambiar** sin que quede rastro de la anterior ni una dirección vieja que enseñe otra cosa — exactamente lo que `RF-PM-014` le dio al producto.

## 2. Contexto

**Es `RF-PM-014` aplicado a otra entidad, y casi todo se hereda.** Las seis decisiones de [`requirements/pm.md` §5.2.9](../../../requirements/pm.md) —se guarda la imagen y no su dirección, en PostgreSQL, por un endpoint propio después del alta, sin tratar, el tipo por los bytes, servida sin token por identificador de imagen— valen aquí sin volver a preguntarse, y las tres que **sí** se preguntaron están en [§5.2.12](../../../requirements/pm.md): el paquete **no declara icono ni color**, los bytes van a **la misma tabla**, y se sirven por **la misma ruta**. Esta spec no las repite; describe la subida del paquete y lo que la distingue de la del producto, que es poco y está escrito abajo.

**Lo que la distingue es lo que no tiene.** `RF-PM-014` nació construyendo `RN-PM-034` en el alta y en la corrección del producto —el icono obligatorio sin portada— porque un upgrade sin ninguno de los dos no tenía con qué pintarse. **El paquete siempre tiene con qué**: sin portada, el frontend pinta **su icono de promoción y el color por omisión del sistema** (`RN-PM-045`), los mismos para todos, y por eso esta operación **no enmienda ninguna regla del alta ni de la corrección** y `RF-PM-029` no tiene nada que rechazar. Es la mitad del bot de `RN-PM-034`, entera y para toda la entidad.

**Y sigue siendo una operación que nunca deja al paquete peor de lo que estaba.** Subir una portada añade algo con lo que pintarse; por eso no tiene ninguna condición de estado, de descripción ni de contenido más allá de que el paquete exista y esté vivo: un paquete inactivo, vacío o sin descripción la admite igual.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador, con `packages:update` | Sube o reemplaza la portada de un paquete |

**Es `packages:update` y no un permiso propio**, por lo mismo que la del producto es `products:update` (`RF-PM-014` §3): la portada es **el valor de un campo del paquete**, como la descripción, y quien puede corregir el paquete puede ponerle foto.

## 4. Alcance

### 4.1 Incluye

- Recibir **un archivo** por `multipart/form-data` y convertirlo en la portada del paquete.
- Comprobar, **por sus primeros bytes**, que es `JPEG`, `PNG` o `WebP`, y que **no pasa de 5 MB** — **con el mismo objeto de dominio** que la portada del producto.
- **Reemplazar** la portada anterior si la había: la nueva estrena identificador y la vieja **se borra**, en la misma transacción.
- Registrar el cambio en la auditoría de cambios de `product_packages`, con el antes y el después del identificador.
- Responder con el paquete —la respuesta de las ocho operaciones, con su cuenta hecha— con `coverImageUrl` señalando la imagen nueva.
- **Añadir `product_packages.cover_image_id`** (`V11`), con su clave foránea y su único.
- **Enmendar las cuatro lecturas del paquete** —`RF-PM-018`, `RF-PM-019`, `RF-PM-026` y la colección `packages` de `RF-PM-007`— con `coverImageUrl`, y el alta `RF-PM-017`, que la devuelve nula.

### 4.2 No incluye

- **Quitar** la portada (`RF-PM-029`) ni **servirla** (`RF-PM-016`, sin cambios).
- **Un icono o un color del paquete.** No se declaran, no se guardan y no viajan: sin portada, el frontend pinta los suyos por omisión (`RN-PM-045`, [`requirements/pm.md` §5.2.12](../../../requirements/pm.md)).
- **Una tabla ni una ruta pública nuevas.** Los bytes van a `product_images` y se sirven por `/api/v1/product-images/{imageId}`.
- **Tratar la imagen**, **más de una por paquete**, **subirla en el alta** y **conservar la reemplazada**: lo mismo que `RF-PM-014` §4.2 deja fuera, por lo mismo.
- **Ninguna regla nueva en el alta ni en la corrección del paquete.** No hay `RN-PM-034` que construir aquí.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-045` | **El paquete lleva portada, y sin ella se pinta con lo que el frontend pone por omisión** — es la regla de esta operación | `requirements/pm.md` §5.1 |
| `RN-PM-033` | **La portada es un archivo y se publica por su identificador** — por extensión: los mismos formatos, el mismo tope, la misma tabla y la misma ruta | `requirements/pm.md` §5.1 |
| `RN-PM-041` | El paquete es catálogo y hereda la forma del producto — un retirado no se corrige, y ponerle foto es corregirlo | `requirements/pm.md` §5.1 |
| Art. V.7 | Quién subió la portada vive en la auditoría, no en la tabla | `constitution.md` |
| Art. V.14 | La auditoría se escribe en la misma transacción | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del paquete | Sí | A qué paquete se le pone la portada | Va en la ruta |
| `file` | Sí | El archivo de imagen, una sola parte del `multipart/form-data` | **`JPEG`, `PNG` o `WebP` por sus primeros bytes**; de **1 byte a 5 242 880 bytes**. La cabecera `Content-Type` de la parte y el nombre del archivo **se ignoran** |

**La misma parte, con el mismo nombre y las mismas condiciones que `RF-PM-014` §6.1.** No es una coincidencia sino una decisión: el archivo lo comprueba **el mismo objeto de dominio**, de modo que el día que cambie el tope o entre un formato cambia para las dos portadas a la vez.

### 6.2 Salida

El paquete, en la misma forma que todas sus escrituras devuelven (`PackageDetailResponse`, `RF-PM-019`), con `coverImageUrl` señalando la imagen recién subida: `/api/v1/product-images/{imageId}`.

**Responde con el paquete y no con la imagen**, por lo mismo que la del producto: lo que cambió es un campo del paquete, y el cliente repinta la ficha con lo que vuelve — cuenta hecha incluida, porque es la respuesta que las ocho operaciones comparten.

## 7. Precondiciones y postcondiciones

**Precondiciones:**

- El actor está autenticado y porta `packages:update`.
- El paquete existe y **está vivo** (no retirado). Su estado, su descripción y cuántos productos tiene **no importan**.
- La petición trae una parte `file` con contenido.

**Postcondiciones:**

- `product_images` tiene **una fila nueva** con los bytes tal cual, el tipo detectado y `created_at`.
- `product_packages.cover_image_id` señala esa fila, y `updated_at` avanza.
- Si había portada, **su fila ya no existe**: la dirección vieja responde `404` en `RF-PM-016`.
- `audit_change_log` tiene una fila `UPDATE` de `product_packages` con `cover_image_id` —antes y después— y **nada más**.
- Las cuatro lecturas del paquete devuelven la dirección nueva.

## 8. Flujo principal

1. Llega una petición `multipart/form-data` con el identificador en la ruta y la parte `file`.
2. El sistema comprueba que la parte existe y no está vacía. Si no: `VAL-002`.
3. El sistema comprueba el tamaño: más de 5 MB, `VAL-004`.
4. El sistema **lee los primeros bytes** y detecta el tipo. Si no es `JPEG`, `PNG` ni `WebP`: `VAL-003`.
5. El sistema resuelve el paquete **vivo** por su identificador, bloqueándolo. Si no: `EX-001`.
6. El sistema inserta la imagen nueva con identificador propio.
7. El sistema hace que el paquete la señale, y se queda con el identificador anterior si lo había.
8. Si lo había, el sistema **borra** la fila anterior.
9. El sistema registra el cambio en la auditoría, en la misma transacción.
10. Devuelve `200` con el paquete.

**Los pasos 2 a 4 van antes que el 5, y el 8 después del 7**, por lo que `RF-PM-014` §8 explica: rechazar el archivo antes de bloquear es gratis, y la clave foránea sin `ON DELETE` obliga a soltar la imagen vieja antes de borrarla.

## 9. Flujos alternativos

### FA-001 — El paquete ya tenía portada

**Comportamiento:** **se reemplaza.** La nueva es otra fila con otro identificador; la vieja se borra; la auditoría lleva los dos identificadores. La dirección que las lecturas devuelven **cambia**.

### FA-002 — El mismo archivo dos veces

**Comportamiento:** **son dos subidas**, dos filas —una tras otra— y dos registros de auditoría. El sistema no compara bytes (`RF-PM-014` `FA-002`).

### FA-003 — Un paquete inactivo, vacío o sin descripción

**Comportamiento:** **se sube igual.** Subir nunca deja al paquete peor, y la portada no condiciona la activación (`RN-PM-040` sigue pidiendo descripción y dos productos, y nada más). Un paquete que se está armando puede tener foto antes que productos.

### FA-004 — El paquete tiene un producto con portada

**Comportamiento:** **nada que decidir.** La portada del paquete y las de sus productos son columnas de tablas distintas que señalan filas distintas; las lecturas devuelven la del paquete en el paquete y la de cada producto en su línea, y ninguna sustituye a la otra.

## 10. Excepciones

### EX-001 — Paquete inexistente o retirado

**Respuesta del sistema:** `404` — *«No existe un paquete vivo con ese identificador.»* El mismo cuerpo que `RF-PM-020` `EX-001`: un retirado no se corrige, y ponerle foto es corregirlo.

### EX-002 — Sin permiso

**Respuesta del sistema:** `403`. Sin `packages:update` la ruta no se alcanza — aunque el actor porte `products:update`.

### EX-003 — La petición no es `multipart/form-data`

**Respuesta del sistema:** `400` — *«La petición debe enviar el archivo como multipart/form-data.»* La misma traducción que `RF-PM-014` `EX-003`, y **sin código nuevo**: el manejador ya la hace para toda ruta `multipart`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | La parte `file` es obligatoria y no puede estar vacía | La imagen de portada es obligatoria. |
| `VAL-003` | Los primeros bytes son los de un `JPEG`, un `PNG` o un `WebP` | La portada debe ser una imagen JPEG, PNG o WebP. |
| `VAL-004` | El archivo no pasa de 5 MB | La portada no puede pesar más de 5 MB. |

**Son las cuatro de `RF-PM-014` §11, con los mismos códigos, los mismos mensajes y el mismo campo `file`**, porque las lanza el mismo objeto de dominio. Un cliente que ya sabe subir la portada de un producto sabe subir la de un paquete.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-354` | El sistema sube una portada `PNG` a un paquete sin ella con `200`, la respuesta es el paquete **con su cuenta hecha** y trae `coverImageUrl` con la forma `/api/v1/product-images/{uuid}`, y `product_images` tiene una fila con los **mismos bytes** y `content_type = image/png` |
| `CA-PM-355` | El sistema **reemplaza** la portada: la respuesta trae **otra** dirección, la fila anterior **ya no existe** —su dirección responde `404` en `RF-PM-016` y la nueva `200`, sin token— y `product_images` tiene **una** fila para ese paquete |
| `CA-PM-356` | El sistema registra en `audit_change_log` un `UPDATE` de `product_packages` con `cover_image_id` —antes nulo y después el nuevo; y en el reemplazo, los dos identificadores— y **sin ningún otro campo** |
| `CA-PM-357` | El sistema rechaza con `VAL-003` nombrando `file` un `GIF` enviado como `image/png`, con `VAL-004` un archivo de 5 242 881 bytes, y con `VAL-002` la petición sin parte y la que la trae vacía; **nada queda escrito** tras un rechazo, y una petición que no es `multipart` responde `400` con `EX-003` |
| `CA-PM-358` | El sistema responde `404` sobre un paquete inexistente y sobre uno **retirado**, con el mismo cuerpo; y sin `packages:update` responde `403` aunque el actor porte `products:update` |
| `CA-PM-359` | El sistema sube la portada a un paquete **inactivo**, a uno **vacío** y a uno **sin descripción**: ninguna condición de estado ni de contenido, y la activación (`RF-PM-021`) sigue sin mirarla |
| `CA-PM-360` | Las cuatro lecturas del paquete —listado, detalle, oferta y hotlink— devuelven la dirección nueva tras subir, y **`coverImageUrl` presente y nula** en un paquete sin portada; la del paquete **no es** la de ninguno de sus productos |
| `CA-PM-361` | El número de sentencias del listado, del detalle, de la oferta y del hotlink **no sube** por la portada del paquete: `cover_image_id` viaja en la sentencia que ya traía el paquete, y `product_images` no se une nunca |

**Las enmiendas que esta spec dispara llevan sus criterios en su sitio**: `CA-PM-367` en `RF-PM-018`, `CA-PM-368` en `RF-PM-019`, `CA-PM-369` en `RF-PM-026`, `CA-PM-370` en `RF-PM-007` y `CA-PM-371` en `RF-PM-017`.

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Los casos del archivo de `RF-PM-014` §13 —el tope del contenedor, el tamaño antes que el tipo, un `WebP` animado, EXIF, un nombre con ruta— | **Se heredan tal cual**: es el mismo objeto de dominio, el mismo manejador y el mismo contenedor |
| Dos subidas simultáneas al mismo paquete | La fila del paquete se bloquea en el paso 5; la segunda espera, ve la portada de la primera y **la reemplaza**. Queda la última, y `product_images` tiene una fila. Dos registros de auditoría, encadenados |
| Subir la portada de un paquete y la de un producto suyo con el mismo archivo | Dos filas distintas en `product_images`, cada una señalada por su columna. La tabla no deduplica bytes, y **ninguna fila es señalada desde dos sitios**: una imagen nace por una subida, que la señala desde una entidad y solo una (`requirements/pm.md` §5.2.12) |
| Retirar el paquete con portada | La portada **se queda**, como en el producto (`RF-PM-015` §14.2): el detalle de un retirado la sigue enseñando, y borrarla sería perder la única copia sin que nadie lo pidiera |
| Un cliente envía además `icon` o `color` como partes de texto | **Se ignoran**: no hay campos de texto en esta operación, y el paquete no declara ninguno de los dos |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Por qué no un `icon` y un `color` en el paquete, como el upgrade tiene icono? | **Decidido por el responsable del proyecto** ([`requirements/pm.md` §5.2.12](../../../requirements/pm.md)): sin portada, el frontend pinta **un icono de promoción y el color por omisión del sistema**, los mismos para todos los paquetes. Un upgrade tiene que distinguirse de otro; una promoción se reconoce por serlo. Dos columnas cuyo valor iba a ser siempre el mismo, y una regla de tres caras para sostenerlas |
| 2 | ¿Por qué la misma tabla y no `package_images`? | **Porque la tabla no es una entidad y no necesita saber de quién es**: quien la señala lo dice. Dos tablas iguales serían dos sitios que mantener cuando cambie el tope o entre un formato |
| 3 | ¿Y la misma ruta pública? | **Sí, sin cambios**: §5.2.9 ya dejó escrito que la dirección no dice de qué producto es; desde hoy tampoco dice si es de un producto o de un paquete. Nada de lo que la ruta hace mira a la entidad que la señala |
| 4 | ¿`PUT` o `POST`? | **`PUT`**, por lo mismo que `RF-PM-014` §14.1: la portada es un solo hueco por paquete |
| 5 | ¿Se comprueba que el paquete tenga productos, o esté activo, para admitir foto? | **No.** Subir nunca deja al paquete peor, y la portada es parte de armarlo — puede llegar antes que los productos (`FA-003`) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 16-09-2026 | Redacción inicial. **Es `RF-PM-014` aplicado al paquete, con el mismo archivo, las mismas cuatro validaciones y la misma tabla y ruta**, y lo que la distingue es lo que no tiene: **el paquete no declara icono ni color** (`RN-PM-045`), sin portada el frontend pinta los suyos por omisión, y por eso esta operación no enmienda ninguna regla del alta ni de la corrección. Añade `product_packages.cover_image_id` (`V11`) y enmienda las cuatro lecturas del paquete y el alta con `coverImageUrl`. Ocho criterios, `CA-PM-354` a `CA-PM-361`. | Responsable técnico |
