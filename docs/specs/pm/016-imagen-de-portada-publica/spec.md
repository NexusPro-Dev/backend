# SPEC — `RF-PM-016` Obtener la imagen de una portada, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-016` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que la dirección que las cuatro lecturas del producto devuelven en `coverImageUrl` **se pueda poner en un `<img>` y funcione**, con token o sin él, y que el navegador no vuelva a pedirla nunca.

## 2. Contexto

**Es la primera ruta del sistema que sirve bytes y no JSON**, y la tercera pública del módulo, después del hotlink (`RF-PM-008`) y de las reseñas (`RF-PM-012`). Es pública **porque una de las lecturas que devuelven su dirección lo es**: la pantalla del hotlink pinta la portada y no tiene con qué autenticarse, y un `<img>` no lleva cabecera `Authorization`. Con token o sin él, responde lo mismo.

**Y no mira el producto.** Sirve una imagen por su identificador, sin comprobar si el producto está activo, vivo o publicado en ese canal. La decisión y su motivo están en [`requirements/pm.md` §5.2.9](../../../requirements/pm.md): lo que se sirve no identifica a nadie ni es un costo, el identificador es un UUID que no se lista sin token, y comprobar el producto costaría una sentencia por cada `<img>` de cada pantalla. Lo que la seguridad de esta ruta decide se resume en tres cosas: **solo tres tipos** —y `SVG` fuera—, **`nosniff`**, y la cota de tasa.

**La dirección señala una imagen concreta, no «la portada de un producto».** Cada subida estrena identificador y la reemplazada se borra (`RN-PM-033`), de modo que una dirección **nunca cambia de contenido**: o sirve la misma imagen para siempre, o responde `404`. Eso es lo que permite decirle al navegador que la guarde un año.

## 3. Actores

| Actor | Papel |
|---|---|
| **Cualquiera, sin autenticar** | Obtiene la imagen por su identificador |

**El mismo cuerpo con token que sin él.** Como el hotlink y las reseñas: una ruta pública responde lo mismo a todo el mundo.

## 4. Alcance

### 4.1 Incluye

- Devolver **los bytes** de la imagen con su `Content-Type` real, el detectado al subirla.
- Responder con **caché inmutable**: `Cache-Control: public, max-age=31536000, immutable`.
- Responder `404` a un identificador que no existe — o que existió y se reemplazó o se quitó.
- Declarar la ruta pública en `SecurityConfig`, **solo en `GET`**, y entrar en la cota de tasa de los catálogos, por la familia.

### 4.2 No incluye

- **Mirar el producto.** Ni estado, ni alcance, ni retiro (`requirements/pm.md` §5.2.9).
- **Redimensionar según un parámetro** (`?w=200`), **negociar formato** (`Accept: image/avif`) ni **recortar**: el sistema no trata la imagen. Se sirve lo que se subió.
- **Rangos** (`Range: bytes=…`). Una portada de cinco megas se sirve entera; los rangos son de video y de descargas grandes.
- **Listar** imágenes, ni por producto ni en general. El identificador se obtiene de las lecturas del producto y de ningún otro sitio.
- **Un `HEAD`** propio. Spring lo responde con las mismas cabeceras del `GET`, y con eso basta.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-033` | **La portada se publica por su identificador**, sin token y con caché inmutable | `requirements/pm.md` §5.1 |
| `RNF-SEG-002` | Todo endpoint no declarado como público exige autenticación — **esta es la tercera declaración del módulo** | `requirements/pm.md` §7, `security.md` §6 |
| `security.md` §5.5 | Limitación de tasa por origen para lo público que consulta la base — **la octava cota, y la primera que sirve bytes** | `security.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador de la imagen | Sí | Cuál | Va en la ruta; UUID |

Sin parámetros de consulta. Ninguno se admite y ninguno se rechaza: se ignoran, como en cualquier `GET`.

### 6.2 Salida

**Los bytes**, con estas cabeceras:

| Cabecera | Valor | Por qué |
|---|---|---|
| `Content-Type` | `image/jpeg`, `image/png` o `image/webp` — **el guardado**, detectado al subir | Es la única fuente de verdad sobre qué son los bytes |
| `Content-Length` | El tamaño real | El navegador lo usa para la barra de progreso y para no esperar de más |
| `Cache-Control` | `public, max-age=31536000, immutable` | La dirección nunca cambia de contenido (§2). `public` porque no depende de quién pregunta y un proxy puede guardarla |
| `X-Content-Type-Options` | `nosniff` | Que el navegador **no adivine** un tipo distinto del declarado: es la segunda mitad de dejar `SVG` fuera |
| `Content-Disposition` | `inline` | Se pinta, no se descarga |

**Sin `ETag` ni `Last-Modified`.** No hacen falta: con `immutable` el navegador no revalida, y un identificador que cambió de contenido no existe.

## 7. Precondiciones y postcondiciones

**Precondiciones:**

- La imagen existe.

**Postcondiciones:**

- Ninguna: es una lectura. **No se registra nada** —ni en `request_log` más allá de lo que toda petición deja, ni en auditoría—.

## 8. Flujo principal

1. Llega un `GET` con el identificador.
2. El sistema resuelve la imagen por su identificador. Si no existe: `EX-001`.
3. Devuelve `200` con los bytes y las cabeceras de §6.2.

**Dos sentencias como máximo, y ninguna sobre `products`.** Una si la fila se lee entera; es la única lectura del sistema que carga `content`, y lo hace a propósito.

## 9. Flujos alternativos

### FA-001 — El producto de la imagen está inactivo, retirado o es de alcance `TIENDA`

**Comportamiento:** **se sirve igual.** Lo que se publica es una imagen que administración subió para que se viera, y el identificador no se publica sin token para lo que no se publica (`requirements/pm.md` §5.2.9). Es la misma decisión que las reseñas de un producto de alcance `TIENDA` (§5.2.7).

### FA-002 — Con token

**Comportamiento:** lo mismo. La ruta no lee el token y no cambia nada con él.

### FA-003 — El navegador ya la tiene

**Comportamiento:** no llega ninguna petición: `immutable` le dice que no revalide. Es el caso normal a partir de la segunda pantalla, y es lo que hace que la cota de tasa baste.

## 10. Excepciones

### EX-001 — La imagen no existe

**Respuesta del sistema:** `404` — *«La imagen no existe.»* **El mismo cuerpo** para un identificador que nunca existió, uno reemplazado y uno quitado: para quien pregunta son lo mismo, y distinguirlos diría que hubo una portada.

**Es un `404` con el sobre de errores JSON del sistema**, aunque la ruta sirva imágenes: un `<img>` que recibe `404` enseña su `alt` y no lee el cuerpo, y un cliente que sí lo lea encuentra lo de siempre.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-255` | El sistema devuelve **sin token** los bytes exactos que se subieron, con `Content-Type` igual al detectado —un `JPEG` subido como `image/png` vuelve como `image/jpeg`—, `Content-Length` igual al tamaño, `Cache-Control: public, max-age=31536000, immutable`, `X-Content-Type-Options: nosniff` y `Content-Disposition: inline` |
| `CA-PM-256` | El sistema responde **lo mismo con token**, cabeceras incluidas |
| `CA-PM-257` | El sistema responde `404` con el mismo cuerpo a un identificador inexistente, a uno **reemplazado** y a uno **quitado** |
| `CA-PM-258` | El sistema sirve la portada de un producto **inactivo**, de uno **retirado** y de uno de alcance **`TIENDA`** |
| `CA-PM-259` | La ruta está declarada pública **solo en `GET`** —`EndpointPermissionsIT` la lista con «público»— y **no lee `products`**: la prueba de sentencias cuenta una |
| `CA-PM-260` | El sistema responde `429` a la petición ciento veintiuna de un mismo origen en un minuto sobre **identificadores distintos**, y el registro del rechazo lleva el prefijo `/api/v1/product-images/` y no la ruta |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El identificador tiene formato válido y la fila se borra entre el `SELECT` y la respuesta | No pasa: los bytes ya están en memoria cuando se responde. Y si pasara, el cliente recibiría la imagen que existía cuando preguntó |
| Un proxy intermedio guarda la imagen y después se quita la portada | El proxy la sigue sirviendo hasta que caduque: hasta un año. Se acepta, y es lo que `public` declara — la imagen se subió para verse, y una quitada que un proxy siga enseñando un tiempo no expone nada que no estuviera publicado |
| `If-None-Match` o `If-Modified-Since` en la petición | Se ignoran: sin `ETag` ni `Last-Modified` no hay con qué compararlos, y la respuesta es `200` completa |
| `Accept: application/json` | Se sirve la imagen igual. La ruta tiene un solo tipo de respuesta y no negocia |
| Una imagen de cinco megas y un cliente lento | Se sirve entera desde memoria; con el tope de 5 MB y la cota de 120 por minuto por origen el peor caso de un origen es de seiscientos megas por minuto, y está escrito en `security.md` §5.5.1 |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Por qué la ruta es `/product-images/{id}` y no `/products/{productId}/cover`? | **Porque la dirección tiene que señalar una imagen que no cambia**, y así se puede cachear un año (`requirements/pm.md` §9). Y porque no revela el identificador del producto, que el hotlink se cuida de no publicar |
| 2 | ¿No debería el `GET` comprobar el producto, como el hotlink? | **No**, y §5.2.9 dice por qué: no identifica a nadie, no es un costo, y costaría una sentencia por `<img>`. La salida está escrita: si un día se decide, es un `JOIN` a `products` y un `404` más, no otra ruta |
| 3 | ¿Se sirve con `Content-Security-Policy`? | **No hace falta**: `nosniff` y la lista de tres tipos bastan para que lo servido sea siempre una imagen de mapa de bits, que no ejecuta nada. Una `CSP` es cosa de las páginas que la incrustan, y esas no son de este sistema |
| 4 | ¿Y cuando haya miniaturas? | Será **otra columna o tabla y otra ruta** (`RF-PM-014` §14.4), no un parámetro aquí: esta ruta sirve lo que se subió y no interpreta nada |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 14-09-2026 | Redacción inicial. **Es la primera ruta del sistema que sirve bytes y no JSON**, y la tercera pública del módulo, pública porque el hotlink lo es y un `<img>` no lleva token. **No mira el producto** (`requirements/pm.md` §5.2.9), y lo que la protege son tres cosas: solo tres tipos con `SVG` fuera, `nosniff`, y la octava cota de tasa por la familia. **Caché inmutable de un año**, posible porque cada subida estrena identificador y la reemplazada se borra: una dirección sirve siempre lo mismo o `404`. Seis criterios, `CA-PM-255` a `CA-PM-260`. | Responsable técnico |
