# SPEC — `RF-PM-009` Reseñar un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-009` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que quien puede comprar un producto **deje escrito qué le pareció**: una puntuación de uno a cinco, que se pueda sumar, y un texto, que explique la puntuación. Una por persona y producto.

## 2. Contexto

**Es la primera vez que este módulo guarda algo que escribe un cliente.** Hasta hoy `products` la escribe la administración y la lee todo el mundo; la reseña la escribe quien compra, y eso trae dos cosas que el módulo no tenía: una fila que **pertenece a una persona** y una operación cuya autorización **no la decide solo el permiso** (`RN-PM-027`, que gobierna `RF-PM-010` y `RF-PM-011`).

Las cuatro decisiones que dan forma al submódulo están en [`requirements/pm.md` §5.2.7](../../../requirements/pm.md) y no se repiten aquí. Esta spec es el **alta**: crea la tabla, siembra el permiso y —porque desde la primera reseña escrita el catálogo tiene algo que sumar— **enmienda las cuatro lecturas del producto** para que publiquen su promedio y su cantidad (`RN-PM-031`).

## 3. Actores

| Actor | Papel |
|---|---|
| Cualquier persona con `products:comment` | Escribe **su** reseña sobre un producto |

**El autor sale del token, no del cuerpo.** No existe forma de reseñar en nombre de otro, y por eso el cuerpo no tiene campo de persona: como `RF-PM-007` y `RF-SP-039`, la operación responde sobre quien llama.

**Un administrador con el permiso escribe las suyas, como cualquiera.** `products:comment` no distingue quién lo porta: habilita la operación, y lo que la acota —una por persona— es la misma regla para todos.

## 4. Alcance

### 4.1 Incluye

- Registrar la reseña del actor sobre un producto: **puntuación** entera de uno a cinco y **texto** de uno a mil caracteres, las dos obligatorias.
- Rechazar la **segunda** reseña del mismo actor sobre el mismo producto mientras la primera siga viva.
- Admitir solo productos **activos y no retirados**.
- **Crear la tabla `product_comments`** y **sembrar `products:comment`**, con su asociación a `SUPERADMIN` y `ADMIN` en la misma migración.
- **Enmendar las cuatro lecturas del producto** —`RF-PM-002`, `RF-PM-003`, `RF-PM-007`, `RF-PM-008`— con `rating`: promedio y cantidad de reseñas vivas (`RN-PM-031`).

### 4.2 No incluye

- **Corregir, retirar y leer** la reseña: `RF-PM-010`, `RF-PM-011`, `RF-PM-012` y `RF-PM-013`.
- **Exigir haber comprado.** Quien opina es quien porta el permiso (`requirements/pm.md` §1.3, §5.2.7).
- **Moderar.** No hay revisión previa, ni denuncia, ni ocultación: lo escrito se publica en el acto y solo su autor lo retira.
- **Responder** a una reseña, ni votarla, ni adjuntarle nada.
- **Notificar** a nadie de que se escribió.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-025` | **La puntuación es un entero de uno a cinco, y va siempre con texto** | `requirements/pm.md` §5.1 |
| `RN-PM-026` | **Una reseña por persona y producto, entre las vivas** | `requirements/pm.md` §5.1 |
| `RN-PM-028` | **Solo se reseña lo que se puede comprar** | `requirements/pm.md` §5.1 |
| `RN-PM-031` | **El producto publica el promedio y la cantidad de sus reseñas vivas, en toda lectura** | `requirements/pm.md` §5.1 |
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |
| `RN-SEG-003` | Los permisos se conceden por rol; ningún rol concede lo que su padre no tiene | `security.md` §4 |

**`RN-PM-026` es la que define la operación.** Sin ella, esto sería un hilo: quien más escribe más pesa en el promedio, y corregir no significaría nada. Con ella, la reseña es una **opinión**, y cambiar de opinión es `RF-PM-010`.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del producto | Sí | Sobre qué se opina | Va en la ruta. UUID |
| Puntuación (`rating`) | **Sí** | De uno a cinco | Entero. `1` a `5`, los dos incluidos. **No admite decimales**: `4.5` se rechaza, no se redondea |
| Texto (`comment`) | **Sí** | Qué le pareció | De **uno a mil** caracteres **sin contar los espacios de los extremos**. Se guarda recortado |

**Ningún campo de persona.** El autor es quien porta el token. Un campo `userId` en el cuerpo es un campo desconocido y se rechaza como tal (`fail-on-unknown-properties`), que es lo que impide que la ambigüedad exista siquiera.

**Las dos son obligatorias, y es deliberado.** Una puntuación sin texto es un número que nadie explica; un texto sin puntuación no se puede sumar. La alternativa —admitir cualquiera de las dos sola— habría producido dos clases de reseña que el promedio y la lista tendrían que tratar distinto.

### 6.2 Salida

`201` con la reseña recién escrita:

| Dato | Descripción |
|---|---|
| Identificador | El de la reseña. Es lo que `RF-PM-010` y `RF-PM-011` reciben en la ruta |
| Producto | Su identificador |
| Puntuación y texto | Como quedaron guardados — el texto **ya recortado** |
| Fechas | Escrita y última corrección, **iguales** al nacer |

**No devuelve al autor.** Quien la escribió es quien la está leyendo, y publicar su nombre en su propia respuesta no le dice nada. La forma es la misma que devuelve `RF-PM-013`, que es lo que permite al front tratar «acabo de escribirla» y «ya la tenía» igual.

### 6.3 Lo que este requerimiento añade a las cuatro lecturas del producto

Desde la primera reseña escrita, `RF-PM-002`, `RF-PM-003`, `RF-PM-007` y `RF-PM-008` devuelven en cada producto:

```json
"rating": { "average": 4.33, "count": 3 }
```

- **`average`** con **dos decimales**, redondeo a la mitad hacia arriba, sobre las reseñas **vivas**. **Nulo** cuando no hay ninguna — nulo y no cero: cero sería «todos la puntuaron pésimo», y no es lo mismo que «nadie la puntuó».
- **`count`** entero, **cero** cuando no hay ninguna. Presente siempre.
- **`rating` viaja presente en las cuatro**, incluido el hotlink **sin token**: no es un dato de la persona ni del costo, es del producto, y una pantalla pública de venta sin estrellas es exactamente lo que este submódulo existe para evitar.

!!! important "Es una enmienda a cuatro requerimientos construidos, y se hace aquí y no en la lista"

    Art. I.7: las cuatro tripletas se enmiendan en su control de cambios citando esta. Se hace **con el alta** y no con `RF-PM-012` porque en cuanto exista una reseña el catálogo tiene un número que enseñar, y dejarlo para después dejaría al front pintando estrellas con una segunda llamada por producto durante el intervalo.

    **Y no sube el número de sentencias de ningún listado.** El agregado se calcula **en la misma sentencia** que trae el producto —una subconsulta correlacionada sobre el índice parcial de `product_comments`—, y la prueba que cuenta sentencias en `RF-PM-002` y `RF-PM-007` **no cambia de número**. Es la única forma de que `RN-PM-031` no reintroduzca el `N+1` que esos dos existen para evitar.

## 7. Precondiciones y postcondiciones

**Precondiciones:**

- El actor está autenticado y porta `products:comment`.
- El producto existe, está **activo** y **no retirado**.
- El actor **no tiene** una reseña viva sobre ese producto.

**Postcondiciones:**

- Existe una fila en `product_comments` con el actor como autor, `deleted_at` nulo y las dos marcas de tiempo iguales.
- `audit_change_log` tiene una fila `CREATE` de `product_comments` con el actor y la reseña completa.
- Las cuatro lecturas del producto reflejan la reseña en `rating` **en la misma transacción que la escribió**: no hay caché ni columna que actualizar.
- **Nada más cambia**: ni el producto, ni la persona, ni su membresía. Una reseña no concede nada, y por eso **no emite evento de seguridad**.

## 8. Flujo principal

1. Llega una petición con el identificador del producto, la puntuación y el texto.
2. El sistema valida la forma: puntuación entera entre uno y cinco, texto recortado de uno a mil caracteres (§11).
3. El sistema toma el actor del token.
4. El sistema resuelve el producto exigiendo **activo y no retirado**. Si no lo encuentra, `EX-001`.
5. El sistema comprueba que el actor **no tiene reseña viva** sobre ese producto. Si la tiene, `EX-002`.
6. El sistema inserta la reseña con el actor como autor.
7. El sistema registra la creación en la auditoría de cambios, en la misma transacción.
8. Devuelve `201` con la reseña.

**El paso 5 y el índice único son la misma regla dos veces, y las dos hacen falta.** La consulta previa da un mensaje útil; el índice parcial cierra la carrera entre dos peticiones simultáneas del mismo actor, que la consulta no puede ver. Cuando muerde el índice, la respuesta es **la misma `EX-002`** — la carrera no es un `500`.

## 9. Flujos alternativos

### FA-001 — El actor ya reseñó este producto y retiró su reseña

**Condición:** existe una fila del actor sobre ese producto con `deleted_at` no nulo.
**Comportamiento:** **se admite.** `RN-PM-026` cuenta las **vivas**, y el índice único es parcial por eso. La reseña nueva es otra fila, con otro identificador; la retirada permanece, retirada.

### FA-002 — El texto llega con espacios en los extremos

**Comportamiento:** se recorta antes de validar y de guardar. `«   Bueno   »` es `«Bueno»`, y mil espacios y una letra son una letra. Es lo que hace que el `CHECK` de la columna pueda escribirse sobre el valor guardado.

### FA-003 — Un administrador escribe la suya

**Comportamiento:** **igual que cualquiera.** Con `products:comment`, escribe una y solo una por producto. Sin el permiso, `403` como cualquiera: `products:read` y los demás de administración **no habilitan** esto.

## 10. Excepciones

### EX-001 — El producto no existe, está inactivo o está retirado

**Condición:** cualquiera de los tres.
**Respuesta del sistema:** `404` con **el mismo cuerpo** en los tres: *«El producto no existe o no está a la venta.»*

**Los tres responden lo mismo por lo mismo que en la lista pública** (`RN-PM-028`, `RF-PM-012`): quien porta `products:comment` es un cliente, y un cliente ve **la oferta**, que no distingue un producto inactivo de uno que no existe. Distinguirlos aquí le diría lo que la oferta le oculta — que hay un producto preparándose—. El coste es que quien intenta reseñar un producto que **acaba** de desactivarse recibe «no existe», y se acepta: es el mismo `404` que recibiría al intentar comprarlo.

### EX-002 — El actor ya tiene una reseña viva sobre este producto

**Condición:** existe una fila viva del actor sobre ese producto — detectada en el paso 5 o por el índice único en el paso 6.
**Respuesta del sistema:** `409` — *«Ya reseñaste este producto. Puedes corregir tu reseña.»*

**No devuelve el identificador de la existente.** El front la obtiene con `RF-PM-013`, que existe para eso; meterla aquí sería una segunda forma de la misma lectura.

### EX-003 — Sin permiso

**Respuesta del sistema:** `403`, registrado en la auditoría de seguridad como toda denegación.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador de producto con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Puntuación presente | La puntuación es obligatoria. |
| `VAL-003` | Puntuación entera entre uno y cinco | La puntuación debe ser un entero entre 1 y 5. |
| `VAL-004` | Texto presente y no vacío tras recortar | El texto de la reseña es obligatorio. |
| `VAL-005` | Texto de mil caracteres como máximo, tras recortar | El texto de la reseña no puede superar los 1000 caracteres. |
| `VAL-006` | Ningún campo desconocido en el cuerpo | El cuerpo de la petición contiene campos no admitidos. |

**`VAL-003` rechaza el decimal en lugar de redondearlo.** `4.5` no es una puntuación de esta escala; convertirlo en `5` —o en `4`— sería decidir por el autor lo que el autor no dijo. El error dice qué se admite.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-170` | El sistema registra la reseña con `201` y devuelve su identificador, la puntuación, el texto recortado y las dos fechas iguales |
| `CA-PM-171` | El sistema rechaza con `400` una puntuación fuera de `1..5` o ausente **con el mensaje que dice qué se admite**, y una decimal o entre comillas **con el `400` de cuerpo ilegible**: Jackson convertiría `4.5` en `4` por omisión, y el deserializador estricto de la puntuación es lo que lo impide |
| `CA-PM-172` | El sistema rechaza con `400` un texto ausente, vacío, de solo espacios o de más de mil caracteres tras recortar |
| `CA-PM-173` | El sistema rechaza con `409` la **segunda** reseña del mismo actor sobre el mismo producto mientras la primera esté viva |
| `CA-PM-174` | El sistema **admite** una reseña nueva del mismo actor cuando la anterior está **retirada**, y es otra fila con otro identificador |
| `CA-PM-175` | El sistema responde `404` a un producto **inactivo**, a uno **retirado** y a uno **inexistente**, **con el mismo cuerpo** en los tres |
| `CA-PM-176` | El sistema responde `403` a quien no porta `products:comment`, aunque porte los cuatro permisos de administración del módulo |
| `CA-PM-177` | El autor es quien porta el token: un cuerpo con `userId` se rechaza con `400` como campo desconocido, y la reseña queda a nombre del actor |
| `CA-PM-178` | El sistema registra una fila `CREATE` en `audit_change_log` con el actor, en la misma transacción que la reseña |
| `CA-PM-179` | Dos peticiones simultáneas del mismo actor sobre el mismo producto dejan **una** reseña, y la otra recibe **`409`** y no `500` |
| `CA-PM-180` | Un producto sin reseñas devuelve `rating` **presente**, con `average` **nulo** y `count` **cero**, en las cuatro lecturas |
| `CA-PM-181` | Con reseñas vivas, las cuatro lecturas devuelven `average` con **dos decimales** y `count` con cuántas son — y el hotlink lo devuelve **sin token** |
| `CA-PM-182` | El número de sentencias de `RF-PM-002` y `RF-PM-007` **no sube** al incorporar `rating`: el agregado viaja en la misma consulta |
| `CA-PM-183` | Un administrador con `products:comment` escribe la suya como cualquiera, y `products:read` solo **no** habilita la operación |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El producto **se desactiva** entre que el actor abre el formulario y envía | `EX-001`. La comprobación es en el momento de escribir, y es lo mismo que le pasaría al comprar |
| El producto se desactiva **después** de escrita la reseña | La reseña **sobrevive** (`RN-PM-028`): sigue viva, su autor la ve, la corrige y la retira, y sigue contando en el `rating` que ven las lecturas de administración |
| El actor **pierde el permiso** después de escribir | La reseña se queda. Perder el permiso quita la capacidad de escribir más, no borra lo escrito; retirarla es decisión del autor, y sin permiso ya no puede — queda anotado como consecuencia |
| El actor **es eliminado** (`RF-SP-029`) | La fila permanece y su `user_id` sigue apuntando a la persona retirada: la clave foránea no lleva `ON DELETE` porque `users` no se borra físicamente. La lista sigue mostrando su nombre; ocultar las reseñas de las personas retiradas es una decisión que este requerimiento no toma |
| Texto de exactamente mil caracteres | Se admite. Mil y uno, no |
| Texto con saltos de línea | Se admite y se guarda tal cual: la reseña es texto libre. El front decide cómo lo pinta |
| Puntuación enviada como cadena `"5"` | Se rechaza: el contrato dice entero, y aceptar cadenas abriría `"cinco"` |
| El mismo actor reseña **dos productos distintos** | Dos reseñas. La unicidad es por pareja |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Debe exigirse haber comprado el producto? | **No, y no puede.** Decidido en `requirements/pm.md` §5.2.7: `PM` no puede leer las ventas de `MV` sin cerrar el ciclo. Quien opina es quien porta el permiso. El día que se quiera, la reseña se muda de módulo o nace un puerto de `MV` hacia arriba — ninguna de las dos es una enmienda a esta spec |
| 2 | ¿Se reseña un producto de alcance `TIENDA` y otro de `HOTLINKS` igual? | **Sí.** El alcance dice dónde se muestra el producto, no quién puede opinar. La única cota es la de `RN-PM-028`: activo y no retirado |
| 3 | ¿El texto admite formato —negritas, enlaces—? | **Se guarda tal cual y no se interpreta.** El backend no sanea HTML porque no lo renderiza; **el front debe escaparlo** al pintarlo, y queda escrito aquí porque la lista es pública y una reseña con `<script>` la lee cualquiera. Es la misma condición que la descripción del producto ya impone |
| 4 | ¿`rating` también en la respuesta del alta del producto (`RF-PM-001`) y de la edición (`RF-PM-004`)? | **Sí, por consecuencia**: las dos devuelven `ProductResponse`, que es la misma forma que el detalle. Un producto recién creado trae `average` nulo y `count` cero. No hay decisión que tomar; se anota para que la enmienda de Art. I.7 alcance a las seis tripletas que comparten la forma y no solo a las cuatro lecturas |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 14-09-2026 | Redacción inicial. **La operación la define `RN-PM-026`**: una por persona y producto entre las vivas, comprobada dos veces —consulta previa para el mensaje, índice parcial para la carrera— y las dos responden el mismo `409`. **Las dos entradas son obligatorias** para que no existan dos clases de reseña. **El `404` del producto es uniforme** —inexistente, inactivo, retirado— por lo mismo que en la lista pública: quien reseña es un cliente y ve la oferta, que tampoco distingue. **Y es el requerimiento que enmienda las cuatro lecturas con `rating`** (`RN-PM-031`): promedio con dos decimales y nulo sin reseñas, cantidad con cero, calculados en la misma sentencia para que el número de consultas de los listados no suba. Queda anotado que el front **debe escapar** el texto al pintarlo, porque la lista es pública. | Responsable técnico |
| 0.2.0 | 14-09-2026 | **Construida.** Una precisión que dejó la construcción: `CA-PM-171` distingue el `400` de rango —con el mensaje de `VAL-003`— del `400` de forma para el decimal y la cadena, porque Jackson convierte `4.5` en `4` y `"5"` en `5` **por omisión** (`ACCEPT_FLOAT_AS_INT`, coerción de escalares) y hubo que escribir un deserializador estricto para que la spec se cumpliera. Las pruebas de `rating` en las cuatro lecturas viven en `ProductRatingIT`, una clase propia, y no repartidas en las cuatro suites; las de número de sentencias siguen siendo las de aquellas. | Responsable técnico |
