# SPEC — `RF-PM-013` Consultar la reseña propia sobre un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-013` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`products:read-own-comments`** y no `products:comment` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `products:comment` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `products:comment`. Las menciones de `products:comment` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Objetivo

Que el front sepa, antes de pintar la pantalla de un producto, **si quien mira ya opinó y qué escribió**: para ofrecerle corregir en lugar de escribir, y para prellenar el formulario.

## 2. Contexto

Existe porque `RF-PM-012` **no puede** decirlo: la lista es pública y responde lo mismo a todo el mundo, de modo que no marca cuál reseña es la del actor. Y porque `RN-PM-026` fija una por persona y producto, de modo que «la mía» es **una** y la pregunta tiene respuesta única.

Es la lectura más pequeña del módulo, y está separada a propósito: meter «la mía» en la lista rompería la uniformidad que hace pública a la lista, y meterla en el `409` del alta sería una lectura escondida en un error.

## 3. Actores

| Actor | Papel |
|---|---|
| Cualquier persona con `products:read-own-comments` | Lee **su** reseña sobre un producto |

**No admite parámetro de persona.** Responde sobre quien llama, como `RF-PM-007` y `RF-SP-039`: el actor sale del token y no existe por dónde preguntar por un tercero.

## 4. Alcance

### 4.1 Incluye

- Devolver la reseña **viva** del actor sobre un producto, con su identificador.
- Responder `404` cuando no la tiene.
- Responder **aunque el producto ya no se venda**.

### 4.2 No incluye

- **Las reseñas del actor sobre todos los productos** —«mis reseñas»—: es otra lectura, con paginación, y nadie la pide hoy.
- **La reseña retirada.** Retirada no hay reseña propia: el `404` es el mismo que si nunca hubiera escrito.
- **La de otra persona**, por ninguna vía.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-026` | **Una reseña por persona y producto, entre las vivas** — es lo que hace que «la mía» sea una | `requirements/pm.md` §5.1 |
| `RN-PM-027` | **Solo el autor** — esta lectura devuelve el identificador que las dos escrituras necesitan, y solo al autor | `requirements/pm.md` §5.1 |
| `RN-PM-028` | La reseña **sobrevive** al retiro del producto — y por eso esta lectura responde sobre productos inactivos y retirados | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del producto | Sí | Sobre qué producto | Va en la ruta. UUID |

**Ningún parámetro de consulta y ningún cuerpo.**

### 6.2 Salida

`200` con la reseña, **la misma forma que devuelven `RF-PM-009` y `RF-PM-010`**: identificador, producto, puntuación, texto, fecha de escritura y fecha de última corrección. **Sin el autor**: es quien la está leyendo.

## 7. Precondiciones y postcondiciones

**Precondiciones:** el actor está autenticado y porta `products:read-own-comments`.

**Postcondiciones:** ninguna. Es una lectura y no audita.

## 8. Flujo principal

1. Llega una petición con el identificador del producto.
2. El sistema toma el actor del token.
3. El sistema busca la reseña **viva** del actor sobre ese producto — **una consulta**, sobre el índice único parcial, que garantiza que es a lo sumo una.
4. Si no hay, `EX-001`. Si hay, `200` con ella.

**No consulta el producto.** No hace falta: la pregunta es «¿tengo reseña sobre este identificador?», y la respuesta es la misma exista el producto o no, esté activo o no. Es lo que hace que esta lectura cueste **una** sentencia y que su `404` **no diga nada del producto**.

## 9. Flujos alternativos

### FA-001 — El producto está inactivo o retirado y el actor tiene reseña

**Comportamiento:** `200` con la reseña. El autor tiene que poder llegar a la suya para corregirla o retirarla (`RN-PM-028`), y esta es la única lectura que se la da cuando la lista pública ya no responde.

### FA-002 — El actor retiró la suya

**Comportamiento:** `404`, como si nunca hubiera escrito. Retirada no hay reseña propia, y el front ofrece **escribir** — que es lo que `RF-PM-009` `FA-001` admite.

## 10. Excepciones

### EX-001 — El actor no tiene reseña viva sobre ese producto

**Condición:** no hay fila viva del actor con ese producto — **incluido** el caso en que el producto no existe.
**Respuesta del sistema:** `404` — *«No has reseñado este producto.»*

**El mismo `404` si el producto no existe, y es deliberado.** A quien pregunta «¿ya opiné?» la respuesta sobre un producto inexistente es «no», y distinguirlo costaría una consulta más para darle a un cliente un dato que la oferta le oculta —que hay un identificador que no es de ningún producto vendible—.

### EX-002 — Sin permiso

**Respuesta del sistema:** `403`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador de producto con formato válido | El identificador indicado no tiene un formato válido. |

**Aquí sí `400` y no `404`**, al revés que en la lista pública: la ruta exige token y permiso, y la forma del identificador no es información para quien ya está dentro.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-212` | El sistema devuelve con `200` la reseña viva del actor sobre el producto, con su identificador y la misma forma que el alta |
| `CA-PM-213` | El sistema responde `404` cuando el actor **no ha reseñado** el producto, cuando **retiró** la suya y cuando el producto **no existe**, con el mismo cuerpo |
| `CA-PM-214` | El sistema devuelve la reseña del actor sobre un producto **inactivo** y sobre uno **retirado** |
| `CA-PM-215` | El sistema **nunca devuelve la de otra persona**: con dos autores sobre el mismo producto, cada uno recibe la suya |
| `CA-PM-216` | El sistema responde `403` a quien no porta `products:read-own-comments` |
| `CA-PM-217` | `GET /comments/mine` responde `200` o `404` de negocio, **nunca `400` por identificador inválido ni `405`**: el segmento literal gana a `/comments/{commentId}` |
| `CA-PM-218` | La lectura cuesta **una** sentencia |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El actor tiene una **retirada** y una **viva** sobre el mismo producto | Devuelve la viva. Es el caso normal después de retirar y volver a escribir |
| El actor **acaba de escribir** la reseña en otra pestaña | La ve: no hay caché |
| El actor tiene el permiso y **nunca ha escrito nada** | `404` en todos los productos. No es un error de configuración: es la respuesta |
| Un administrador con el permiso | Ve **la suya**, si la tiene. No hay parámetro con el que ver la de otro |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Por qué `404` y no `200` con cuerpo vacío o nulo? | **Porque el recurso «mi reseña sobre este producto» no existe**, y el sistema ya usa `404` para eso en todas partes. Un `200` con nulo obligaría al front a distinguir dos formas de éxito, y un `204` no puede llevar cuerpo cuando sí la hay |
| 2 | ¿Debería existir «mis reseñas», sobre todos los productos? | **Hoy no.** Nadie la necesita para la pantalla del producto, y una lista paginada de lo que una persona escribió es un requerimiento propio —con la pregunta de si lo ve alguien más—. Se anota |
| 3 | ¿Se comprueba el producto para distinguir «no existe» de «no has opinado»? | **No** (`EX-001`). Costaría una consulta y le diría a un cliente algo que la oferta le oculta. Quien necesita saber si el producto existe tiene la oferta y el detalle |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 14-09-2026 | Redacción inicial. Existe porque la lista pública **no puede** marcar la del actor sin dejar de ser pública, y porque `RN-PM-026` hace que «la mía» sea una. **Una sola sentencia y sin consultar el producto**: la respuesta es la misma exista o no, y por eso el `404` no dice nada del producto y la lectura responde sobre productos inactivos y retirados — la única que le da al autor el camino a la suya cuando la lista ya no responde. **`VAL-001` responde `400` y no `404`**, al revés que en la lista: aquí hay token. Queda anotado el criterio de la ruta literal frente a `/{commentId}`, que es el mismo de `/products/available`. | Responsable técnico |
| 0.2.0 | 19-09-2026 | **Cambia el permiso: `products:read-own-comments` y no `products:comment`** (`RF-SP-060`, `RN-SEG-014`, un permiso por operación; [`security.md`](../../../security.md) v0.63.0). Enmienda de Art. I.7 sin cambio de comportamiento: la misma operación, el mismo actor, un código propio sembrado por `V28` y dado a todo rol que portara `products:comment`. | Responsable del proyecto |
