# SPEC — `RF-PM-012` Consultar las reseñas de un producto, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-012` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que la pantalla de un producto —**dentro o fuera**, con token o sin él— enseñe **qué dicen de él quienes lo compraron**: cada reseña con su puntuación, su texto, cuándo se escribió y quién la escribió, por su nombre.

## 2. Contexto

**Es la segunda ruta pública del módulo y la segunda del sistema que publica el nombre de una persona.** La primera fue el hotlink (`RF-PM-008`), y de él hereda las dos decisiones que gobiernan el diseño: **de la persona solo el nombre y el apellido** (`RN-PM-030`) y **`404` uniforme** para todo lo que no procede (`RN-PM-028`). Lo que la hace distinta es quién es la persona: allí un vendedor que reparte su nombre a propósito; aquí un cliente que solo quiso opinar, y por eso `RN-PM-030` es crítica.

Es pública porque la pantalla del hotlink la necesita y no tiene con qué autenticarse. La alternativa —solo autenticada— dejaba al canal de enlaces sin reseñas, que es donde más venden ([`requirements/pm.md` §5.2.7](../../../requirements/pm.md)).

## 3. Actores

| Actor | Papel |
|---|---|
| **Cualquiera, sin autenticar** | Lee las reseñas de un producto |

**Llevar un token no cambia la respuesta.** Como en el hotlink: no hay versión enriquecida para quien está dentro, y por eso la lista **no marca cuál reseña es la del actor** — eso es `RF-PM-013`.

## 4. Alcance

### 4.1 Incluye

- Devolver las reseñas **vivas** de un producto, **paginadas**, de la más reciente a la más antigua.
- De cada una: identificador, puntuación, texto, fecha de escritura, fecha de última corrección, y **nombre y apellido** del autor.
- Responder **solo sobre productos activos y no retirados**, y `404` uniforme a lo demás.
- Acotar por origen, con la cota de los catálogos públicos y **la llave del hotlink**.

### 4.2 No incluye

- **El promedio y la cantidad.** Viajan con el producto en sus cuatro lecturas (`RN-PM-031`); repetirlos aquí sería una segunda forma del mismo dato, y una página de reseñas no necesita saber el promedio de las que no trae.
- **Filtrar ni ordenar de otro modo.** Ni por puntuación, ni por antigüedad ascendente, ni por «útiles»: cada parámetro público es superficie que vigilar, y ninguno lo pide nadie hoy.
- **Buscar en el texto.** Es analítica, no lectura.
- **Marcar la reseña del actor.** `RF-PM-013`.
- **Las reseñas retiradas**, para nadie. Ni las de productos inactivos o retirados, para nadie sin `products:read` — y quien lo tiene tampoco las ve aquí: no hay versión administrativa de esta lista.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-028` | **Solo se reseña lo que se puede comprar, y solo eso se lee sin token** | `requirements/pm.md` §5.1 |
| `RN-PM-030` | **Del autor solo se publica su nombre y apellido** | `requirements/pm.md` §5.1 |
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |
| `RN-PM-022` | Del vendedor solo el nombre — **el precedente** del que esta lista toma la forma | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del producto | Sí | De qué producto | Va en la ruta. UUID |
| `page` | No | Página, desde cero | Entero no negativo. Por omisión `0` |
| `size` | No | Tamaño de página | De `1` al máximo del sistema. Por omisión el del sistema |

**Ningún otro parámetro.** Los dos de paginación son los mismos de todo listado del sistema (`shared/pagination`), con las mismas validaciones y los mismos mensajes.

### 6.2 Salida

La envoltura de página del sistema —`content`, `totalElements`, `totalPages`, `page`, `size`, `totalIsExact`— y en `content`:

| Dato | Descripción |
|---|---|
| Identificador | El de la reseña. Es lo que `RF-PM-010` y `RF-PM-011` reciben en la ruta, y lo que hace que el `403` de esas dos **no revele nada** |
| Puntuación | Entero de uno a cinco |
| Texto | Tal como se guardó, recortado |
| Autor | **Nombre y apellido**, y nada más |
| Fechas | Escrita y última corrección. Distintas cuando se corrigió — eso es «editada», sin campo aparte |

!!! danger "El autor viaja como dos cadenas, y la proyección no tiene dónde poner nada más"

    Ni identificador, ni nombre de usuario, ni correo, ni estado, ni roles, ni membresía. **Lo que sostiene `RN-PM-030` no es un filtro: es que el registro de salida no tenga el campo**, igual que `OfferItem` no tiene el costo. La prueba compara **el cuerpo entero** de cada elemento contra la forma esperada, y no solo los campos que espera encontrar.

    **La lista tampoco dice cuál es la del actor.** Un campo `mine` obligaría a que la respuesta cambiara con el token, y una ruta pública responde lo mismo a todo el mundo (`RF-PM-008` §3). Es el precio de que la pantalla se pueda cachear y compartir.

**El orden es de la más reciente a la más antigua, con el identificador como desempate.** El identificador es un UUID v7, de modo que su orden **es** el cronológico y el desempate sale gratis; sin él, dos reseñas escritas en el mismo instante podrían repetirse o saltarse entre páginas, que es el defecto que `RF-PM-002` ya describió y que se ve como «faltan reseñas» sin ningún error.

## 7. Precondiciones y postcondiciones

**Precondiciones:** el producto existe, está **activo** y **no retirado**. No hay actor que autenticar.

**Postcondiciones:** ninguna. Es una lectura, **no audita** y no cuenta visitas.

## 8. Flujo principal

1. Llega una petición con el identificador del producto y, si vienen, `page` y `size`.
2. El sistema valida la paginación (§11).
3. El sistema resuelve el producto exigiendo **activo y no retirado**. Si no lo encuentra, `EX-001`.
4. El sistema trae la página de reseñas **vivas** de ese producto, **con el nombre y apellido del autor en la misma sentencia**, y su total.
5. Devuelve la página.

**Son dos consultas más la del total, y la del autor no es una por fila.** El nombre sale de un `JOIN` a `users` en la sentencia de la página —el precedente es el `JOIN` a `memberships` de `RF-PM-002`—, y **ninguna regla se decide con él**: quién es el autor lo dice `product_comments.user_id`; el `JOIN` solo le pone nombre.

## 9. Flujos alternativos

### FA-001 — El producto no tiene reseñas

**Comportamiento:** `200` con `content` vacío y `totalElements` cero. No es un `404`: el producto existe y se vende; simplemente nadie ha opinado.

### FA-002 — Llega con un token válido

**Comportamiento:** **la misma respuesta.** Ni más campos, ni la propia marcada, ni las retiradas.

### FA-003 — Se pide una página más allá de la última

**Comportamiento:** `200` con `content` vacío, como en todo listado del sistema.

## 10. Excepciones

### EX-001 — El producto no existe, está inactivo o está retirado

**Condición:** cualquiera de los tres.
**Respuesta del sistema:** `404` con **el mismo cuerpo** en los tres: *«El producto no existe o no está a la venta.»*

!!! danger "Por qué los tres responden lo mismo, sin token"

    Un anónimo con un identificador **no debe poder saber si corresponde a un producto en preparación**. Con tres mensajes distintos, recorrer identificadores diría cuáles existen y cuáles están inactivos, que es el catálogo administrativo leído desde fuera. Es la misma decisión que el hotlink, con menos en juego —aquí no hay personas que sondear— y el mismo remedio: **un solo punto de salida** con el mismo código y el mismo mensaje.

    Lo que la uniformidad no resuelve es el recorrido a ciegas de identificadores buscando `200`, y eso lo acota la cota de tasa **por familia** (§4.1): contar por URI daría un cubo por producto y no cortaría jamás.

### EX-002 — Paginación inválida

**Respuesta del sistema:** `400`, con los mensajes de `shared/pagination`. **Aquí sí es `400` y no `404`**, al revés que en las validaciones del hotlink: la forma de `page` y `size` no es información sobre nadie.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador de producto con forma admisible | El producto no existe o no está a la venta. |
| `VAL-002` | `page` no negativa; `size` entre uno y el máximo | Los de `shared/pagination` |

**`VAL-001` responde `404` y no `400`**, como en el hotlink: en una ruta pública la forma del identificador también es información, y el mensaje es **el mismo** que el de `EX-001`.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-202` | El sistema devuelve, **sin token**, las reseñas vivas del producto paginadas, de la más reciente a la más antigua |
| `CA-PM-203` | Cada elemento trae identificador, puntuación, texto, fechas y **autor con nombre y apellido**, y **el cuerpo entero** de cada elemento no trae nada más — ni identificador de persona, ni nombre de usuario, ni correo |
| `CA-PM-204` | El sistema **no devuelve** las reseñas retiradas, y sí las de autores cuya cuenta está inactiva o retirada |
| `CA-PM-205` | El sistema responde `404` a un producto **inactivo**, a uno **retirado**, a uno **inexistente** y a un **identificador malformado**, **con el mismo cuerpo** en los cuatro |
| `CA-PM-206` | El sistema responde `200` con página vacía a un producto activo **sin reseñas**, y no `404` |
| `CA-PM-207` | El sistema responde **lo mismo** con un token válido que sin él, incluido cuando el token es del autor de una de las reseñas |
| `CA-PM-208` | El sistema **acota por origen** las peticiones a esta familia de rutas, y el exceso recibe `429`; identificadores distintos topan con **la misma** cota |
| `CA-PM-209` | El sistema resuelve el nombre del autor **en la misma sentencia** que la página: el número de consultas no crece con el tamaño de la página |
| `CA-PM-210` | El sistema rechaza con `400` una paginación inválida, con los mensajes del sistema |
| `CA-PM-211` | Dos reseñas escritas en el mismo instante no se repiten ni se saltan entre páginas: el identificador desempata |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El producto es de alcance **`TIENDA`** | **Se devuelve igual.** La lista no distingue el alcance, y las reseñas de un producto que solo se vende dentro se leen desde fuera si alguien tiene su identificador — que el hotlink no publica. Aceptado en [`requirements/pm.md` §5.2.7](../../../requirements/pm.md) con la salida escrita: acotar la lista anónima a `HOTLINKS` es un predicado más |
| El autor **corrige** su nombre en su perfil | La lista muestra el nombre **de hoy**: el `JOIN` lee `users` en cada consulta, y no hay copia que envejezca |
| El autor es **eliminado** (`RF-SP-029`) | Su reseña sigue en la lista con su nombre. Ocultar lo escrito por personas retiradas es una decisión de privacidad sin dueño, y no se toma aquí de rebote |
| Dos autores con el mismo nombre y apellido | Dos reseñas con el mismo rótulo. La lista **no** los distingue, y es a propósito: distinguirlos exigiría publicar algo más de la persona |
| El producto **se desactiva** entre dos páginas | La segunda página responde `404`. Es correcto: la lista publica lo que se vende **hoy** |
| Se pide `size` máximo sobre un producto con miles de reseñas | Funciona; el índice parcial por producto y fecha lo sostiene. La cota de tasa acota cuántas veces, no cuántas por vez |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La lista debería traer también `rating.average` y `count`? | **No.** Viajan con el producto en sus cuatro lecturas (`RN-PM-031`), y la pantalla que pinta la lista ya tiene el producto. Traerlos aquí sería una segunda forma del mismo dato, y el total de la envoltura ya dice cuántas hay |
| 2 | ¿Por qué el `404` cubre también el producto **inactivo**, si un cliente autenticado con `products:read` podría verlo? | **Porque la ruta es pública y no mira el token** (§3). Una respuesta que dependiera del permiso dejaría de ser cacheable y compartible, y el administrador tiene el catálogo para ver el producto; sus reseñas mientras está inactivo las ve su autor por `RF-PM-013`, y nadie más — no hay hoy quien las necesite |
| 3 | ¿Se cuenta por familia o por ruta en la cota? | **Por familia**, `/api/v1/products/*/comments`, y con el número de los catálogos: 120 por minuto. La naturaleza es la de un catálogo —una lectura pública que consulta la base—, pero la ruta lleva un identificador y contar por URI no cortaría un recorrido ([`security.md` §5.5](../../../security.md)) |
| 4 | ¿Debe el `404` de identificador malformado ser `400`? | **No, por lo mismo que el hotlink**: en una ruta pública la forma también es información, y un `400` diría que la forma importa. Cuesta un mensaje menos preciso a quien teclea mal, y se acepta |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 14-09-2026 | Redacción inicial. **Hereda del hotlink las dos decisiones que la gobiernan**: del autor solo nombre y apellido —sostenido por que la proyección no tenga el campo, y probado comparando el cuerpo entero— y `404` uniforme para el producto inexistente, inactivo, retirado o malformado, con un solo punto de salida. **La misma respuesta con token que sin él**, y por eso no marca la del actor. **El nombre del autor sale de un `JOIN` a `users` en la misma sentencia**, con el precedente de `RF-PM-002`, y ninguna regla se decide con él. **Orden por fecha con el identificador como desempate**, porque el UUID v7 lo da gratis. Queda anotado que las reseñas de un producto `TIENDA` se leen sin token, y que la cota se cuenta por familia con el número de los catálogos. | Responsable técnico |
