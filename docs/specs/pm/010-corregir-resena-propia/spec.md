# SPEC — `RF-PM-010` Corregir la reseña propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-010` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |
| Enmendada el | 14-09-2026 — **el cuerpo vacío responde `400`, como en `RF-PM-004`**, y no `200`. Ver §15 |

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`products:update-comment`** y no `products:comment` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `products:comment` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `products:comment`. Las menciones de `products:comment` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Objetivo

Que quien escribió una reseña **cambie de opinión sin escribir dos veces**: corrige la puntuación, el texto o los dos, y la reseña sigue siendo la misma.

## 2. Contexto

`RN-PM-026` fija **una** reseña por persona y producto, y esa decisión es la que hace necesaria esta operación: sin corrección, cambiar de opinión obligaría a retirar y volver a escribir, y el promedio y la lista verían dos filas donde hubo una opinión.

**Es la primera operación del módulo cuya autorización no la decide solo el permiso.** `products:update-comment` habilita corregir; **ser el autor** autoriza (`RN-PM-027`). Es la «verificación de propiedad del dato» que [`security.md` §6](../../../security.md) deja a la capa de aplicación, y `PM` la ejerce aquí por primera vez.

## 3. Actores

| Actor | Papel |
|---|---|
| El **autor** de la reseña, con `products:update-comment` | Corrige la suya |

**Nadie más.** Ni otro cliente, ni la administración: un administrador con el permiso corrige **las suyas** y recibe `403` en las ajenas, exactamente como cualquiera. No existe moderación (`requirements/pm.md` §5.2.7).

## 4. Alcance

### 4.1 Incluye

- Corregir la **puntuación**, el **texto**, o los dos, de una reseña viva del actor.
- Responder `403` a quien no es el autor, **aunque porte el permiso**.
- Admitir la corrección **aunque el producto ya no se venda**.

### 4.2 No incluye

- **Cambiar de producto** la reseña. No es un campo corregible: una reseña es de un producto, y opinar de otro es otra reseña.
- **Retirar** la reseña: `RF-PM-011`.
- **Historial de versiones** visible: lo que la reseña decía antes vive en `audit_change_log`, y no se publica. La lista muestra la versión vigente y la fecha de la última corrección, nada más.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-025` | **La puntuación es un entero de uno a cinco, y va siempre con texto** | `requirements/pm.md` §5.1 |
| `RN-PM-027` | **Solo el autor corrige y retira su reseña — nadie más, ni administración** | `requirements/pm.md` §5.1 |
| `RN-PM-028` | **Solo se reseña lo que se puede comprar** — y la reseña **sobrevive** al retiro del producto | `requirements/pm.md` §5.1 |
| `RN-PM-031` | **El producto publica el promedio** — y una corrección de puntuación lo mueve en el acto | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del producto | Sí | De qué producto es la reseña | Va en la ruta. La reseña **debe ser de ese producto** |
| Identificador de la reseña | Sí | Cuál se corrige | Va en la ruta. Lo devolvió `RF-PM-009` o `RF-PM-013` |
| Puntuación (`rating`) | No | Puntuación nueva | Entero de `1` a `5`. **No admite vaciarse**: el nulo explícito se rechaza |
| Texto (`comment`) | No | Texto nuevo | De uno a mil caracteres tras recortar. **No admite vaciarse** |

**Semántica de `PATCH`: lo que no viene no cambia.** Es la misma forma que `RF-PM-004`, con una diferencia que la simplifica: aquí **ningún campo admite el nulo explícito**, porque los dos son obligatorios en la columna (`RN-PM-025`). Los tres estados de un campo —ausente, nulo, con valor— se reducen a dos, y el nulo es un error de validación y no una orden.

**Un cuerpo sin ningún campo responde `400`** (`VAL-005`), como la edición del producto: el módulo entero corrige con «al menos uno», y una petición que no informa nada es una petición mal formada, no una corrección. **Lo que sí responde `200` sin escribir** es el cuerpo cuyos valores **coinciden con los guardados**: eso es una corrección que no corrige nada, y el sistema no distingue «no toqué nada» de «toqué y volví a dejar lo mismo» — ni tiene por qué. (Decía «`200`» para el cuerpo vacío hasta el 14-09-2026; se alineó con `RF-PM-004` al construirlo.)

### 6.2 Salida

`200` con la reseña corregida, **la misma forma que `RF-PM-009`**: identificador, producto, puntuación, texto, fecha de escritura y **fecha de última corrección, que avanza**.

## 7. Precondiciones y postcondiciones

**Precondiciones:**

- El actor está autenticado y porta `products:update-comment`.
- La reseña existe, está **viva**, es **de ese producto** y **su autor es el actor**.

**Postcondiciones:**

- La fila tiene los valores nuevos y `updated_at` avanzó — **solo si algo cambió**.
- `audit_change_log` tiene una fila `UPDATE` con el antes y el después de cada campo tocado.
- `rating.average` del producto refleja la puntuación nueva en la misma transacción.
- `created_at`, `product_id` y `user_id` **no cambiaron**.

## 8. Flujo principal

1. Llega una petición con los dos identificadores y el cuerpo.
2. El sistema valida la forma de lo que venga (§11).
3. El sistema toma el actor del token.
4. El sistema resuelve la reseña **viva** por su identificador **y el del producto**, bloqueándola. Si no existe, no está viva o no es de ese producto: `EX-001`.
5. El sistema comprueba que **el autor de la fila es el actor**. Si no: `EX-002`.
6. El sistema aplica los campos presentes. Si ninguno cambia el valor guardado, devuelve `200` sin escribir.
7. El sistema escribe la fila y registra el cambio en la auditoría, en la misma transacción.
8. Devuelve `200` con la reseña.

**El orden de los pasos 4 y 5 importa, y es el que es a propósito.** Primero «existe» y después «es tuya»: una reseña inexistente responde `404` a todo el mundo, y una existente y ajena responde `403`. Que el `403` confirme que la reseña existe **no publica nada**: la lista pública ya la enseña, con su identificador.

## 9. Flujos alternativos

### FA-001 — Solo cambia la puntuación

**Comportamiento:** el texto se queda. `rating.average` del producto cambia; `count` no.

### FA-002 — El producto ya no se vende

**Condición:** el producto está inactivo o retirado.
**Comportamiento:** **se corrige igual.** La reseña sobrevive al producto (`RN-PM-028`) y lo escrito sigue siendo del autor. Es la asimetría con el alta, y es deliberada: no se puede opinar de lo que no se vende, pero lo que ya se opinó no se congela porque el catálogo cambie.

### FA-003 — Nada cambia de valor

**Condición:** los campos presentes traen lo mismo que hay guardado.
**Comportamiento:** `200` con la reseña, **sin escribir** y sin fila de auditoría. `updated_at` **no avanza**: una corrección que no corrige nada no es una corrección.

## 10. Excepciones

### EX-001 — La reseña no existe, está retirada o no es de ese producto

**Respuesta del sistema:** `404` — *«La reseña no existe.»* **El mismo cuerpo en los tres casos.**

**Una reseña retirada responde «no existe» también a su autor.** Se consideró distinguirlo —«ya la retiraste»— y se descartó: la reseña retirada no se puede corregir ni revivir, de modo que el mensaje no le da al autor nada que hacer, y a un tercero le confirmaría que existió. Quien quiera volver a opinar escribe otra (`RF-PM-009`, `FA-001`).

### EX-002 — La reseña no es del actor

**Condición:** la reseña existe, está viva, y su autor **no es** quien llama.
**Respuesta del sistema:** `403` — *«Solo el autor puede corregir su reseña.»* Se registra en la **auditoría de seguridad** como toda denegación, con severidad alta.

!!! danger "Es la excepción que define el requerimiento, y la prueba que la sostiene no puede faltar"

    `RN-PM-027` no cabe en el esquema: un `CHECK` no sabe quién ejecuta la sentencia. Lo único que la defiende es la comparación del paso 5 y la prueba que intenta corregir una ajena **con el permiso puesto** — y en particular **con un administrador**, que es quien está acostumbrado a que el permiso baste. Sin esa prueba, el día que alguien «simplifique» el servicio quitando la comparación, nada fallaría: **silenciaría**.

### EX-003 — Sin permiso

**Respuesta del sistema:** `403`. Sin `products:update-comment` no se llega al paso 4: la ruta lo exige antes de mirar la reseña.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Puntuación, si viene, entera entre uno y cinco y **no nula** | La puntuación debe ser un entero entre 1 y 5. |
| `VAL-003` | Texto, si viene, de uno a mil caracteres tras recortar y **no nulo** | El texto de la reseña es obligatorio y no puede superar los 1000 caracteres. |
| `VAL-004` | Ningún campo desconocido — en particular, **ni `productId` ni `userId`** | El cuerpo de la petición contiene campos no admitidos. |
| `VAL-005` | Al menos un campo corregible en el cuerpo | Debe informar al menos uno de los campos corregibles. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-184` | El autor corrige la puntuación, el texto o los dos con `200`, y `updatedAt` **avanza** mientras `createdAt` no cambia |
| `CA-PM-185` | El sistema responde `403` a otro cliente **con `products:update-comment`** que intenta corregir una reseña ajena, y la reseña no cambia |
| `CA-PM-186` | El sistema responde `403` a un **administrador** con `products:update-comment` sobre una reseña ajena: el permiso habilita, no autoriza |
| `CA-PM-187` | El sistema responde `404` a una reseña inexistente, a una **retirada** —también a su autor— y a una que **no es del producto de la ruta**, con el mismo cuerpo |
| `CA-PM-188` | El sistema rechaza con `400` el nulo explícito en `rating` y en `comment`: ninguno admite vaciarse |
| `CA-PM-189` | Un cuerpo **vacío** responde `400`; uno **sin cambios de valor** responde `200` **sin** avanzar `updatedAt` y **sin** fila de auditoría |
| `CA-PM-190` | El sistema registra una fila `UPDATE` en `audit_change_log` con el antes y el después de cada campo tocado |
| `CA-PM-191` | Corregir la puntuación mueve `rating.average` del producto en el acto, y `count` no cambia |
| `CA-PM-192` | El autor corrige su reseña sobre un producto **inactivo** y sobre uno **retirado** |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El autor corrige a **la misma puntuación** y otro texto | Cambia el texto; `average` no se mueve; `updatedAt` avanza porque algo cambió |
| Dos correcciones simultáneas del mismo autor | La fila se bloquea en el paso 4; la segunda espera y aplica sobre el resultado de la primera. Gana la última, que es lo que el autor espera |
| La reseña se **retira** entre que el autor abre el formulario y envía | `EX-001`. No se revive corrigiendo |
| El identificador de reseña existe pero **bajo otro producto** | `EX-001`, aunque sea del actor. La ruta dice de qué producto es, y una petición que se contradice no se «arregla» |
| El autor fue **eliminado** y conserva sesión | No llega: `RF-SP-029` revoca el acceso al eliminar |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La lista debería marcar «editada»? | **Ya lo hace sin campo nuevo**: `updatedAt` distinto de `createdAt` es exactamente eso, y el front lo pinta como quiera. Un booleano sería una segunda forma del mismo dato |
| 2 | ¿Se limita cuántas veces se corrige, o hasta cuándo? | **No.** Ninguna regla lo pide, y un límite sería una decisión de producto que nadie ha tomado. Se anota que añadirlo después es una validación más en el paso 5 |
| 3 | ¿Por qué `403` y no `404` para la ajena, si el hotlink y `RF-SP-055` usan `404` para no revelar? | **Porque aquí no hay nada que revelar.** Aquellos ocultan la existencia de una **persona**; la reseña la publica la lista con su identificador, de modo que el `403` no confirma nada que no fuera público. Y `security.md` §6 fija `403` como la forma por omisión: el `404` es la excepción que hay que justificar, no al revés |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 14-09-2026 | Redacción inicial. **La excepción que define el requerimiento es `EX-002`**: el permiso habilita y ser el autor autoriza, y como `RN-PM-027` no cabe en el esquema lo único que la defiende es la prueba que intenta corregir una ajena con el permiso puesto — en particular con un administrador—. Primero «existe» (`404`) y después «es tuya» (`403`), y el `403` no revela nada porque la lista pública ya enseña la reseña con su identificador. **Ningún campo admite el nulo explícito**, al revés que en `RF-PM-004`, porque los dos son obligatorios en la columna; un cuerpo vacío es una corrección vacía y responde `200` sin escribir. **La retirada responde «no existe» también a su autor**: no se puede revivir, y distinguirlo no le daría nada que hacer. **Y se corrige aunque el producto ya no se venda**, que es la asimetría deliberada con el alta. | Responsable técnico |
| 0.2.0 | 14-09-2026 | **El cuerpo vacío responde `400` y no `200`.** Al construir se vio que `RF-PM-004` rechaza la edición sin ningún campo con «Debe informar al menos uno de los campos corregibles», y esta spec decía lo contrario citándolo como precedente. Se alinea: el módulo entero corrige con «al menos uno». Lo que sigue respondiendo `200` sin escribir es el cuerpo **sin cambios de valor**, que es la corrección que no corrige nada. Nace `VAL-005`; `FA-003` y `CA-PM-189` se reescriben. | Responsable técnico |
| 0.3.0 | 19-09-2026 | **Cambia el permiso: `products:update-comment` y no `products:comment`** (`RF-SP-060`, `RN-SEG-014`, un permiso por operación; [`security.md`](../../../security.md) v0.63.0). Enmienda de Art. I.7 sin cambio de comportamiento: la misma operación, el mismo actor, un código propio sembrado por `V28` y dado a todo rol que portara `products:comment`. | Responsable del proyecto |
