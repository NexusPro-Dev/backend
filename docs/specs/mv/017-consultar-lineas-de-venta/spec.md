# SPEC — `RF-MV-017` Consultar las líneas de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-017` |
| Módulo | `MV` — Movimientos |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 23-09-2026 |

---

!!! warning "Enmendado el 24-09-2026 — solo las ventas CONFIRMADAS"

    `RN-MV-038` ([`requirements/mv.md`](../../../requirements/mv.md) v0.42.0), por decisión del responsable del proyecto. El listado deja de traer las líneas de **toda** venta y pasa a traer **solo las de las confirmadas**: desaparecen las de `PENDIENTE`, `ANULADA` y `RECHAZADA`.

    **Lo decide la consulta y no un filtro.** El predicado va fijo, junto a `mt.code = 'VENTA'`, de modo que ningún parámetro puede ensancharlo — un filtro con valor por omisión lo dejaría a merced de quien llama.

    **Y por eso el filtro `status` se retira del contrato** (`CA-MV-188`): solo podía tomar un valor útil. Un parámetro cuyo resultado está predeterminado invita a que alguien construya sobre él una condición que nunca se cumple, que es el argumento que `OfferItem` ya tenía escrito para no publicar `status` en la oferta.

    **`movementStatus` se conserva en la respuesta**, por decisión expresa del responsable: dirá siempre `CONFIRMADA`, y se acepta el dato constante a cambio de no romper a quien ya lo lee.

    **La entrega no se toca, y conviene no confundirlas**: `deliveryStatus` es de la **línea** y sigue teniendo sus tres valores dentro de una venta confirmada —`ENTREGADA`, `PENDIENTE` de autorización y `RETENIDA`—, de modo que su filtro sigue sirviendo para todo lo que servía.

!!! warning "Enmendado el 24-09-2026 — el filtro `code` busca por FRAGMENTO"

    `RN-MV-037` ([`requirements/mv.md`](../../../requirements/mv.md) v0.41.0), a petición del responsable del proyecto: «por si solo me sé una parte». El filtro `code` de las líneas de venta **deja de exigir el comprobante entero** y pasa a devolver todas las de cualquier venta que lo **contenga**, sin distinguir mayúsculas.

    **Es una ampliación y no un cambio de contrato**: el código completo sigue encontrando lo que encontraba. Lo que cambia es que la respuesta puede traer líneas de **varias ventas** donde antes traía las de una.

    **`type` y `typeStatus` siguen siendo exactos** —se eligen de un conjunto cerrado y no se teclean—, los comodines `%` y `_` del usuario se **escapan**, y el alcance de esta consulta no se mueve. Se indexa con trigramas (`ix_movements_codigo_busqueda`, `V39`), porque `uq_movements_code` no responde por un fragmento del medio.

## 1. Objetivo

Responder **«qué se ha vendido»**, y no «qué ventas hubo»: una fila por **línea** de venta, paginada, para administración.

## 2. Contexto

**Lo pidió el responsable del proyecto el 23-09-2026**: «un endpoint para traer todas las líneas de las ventas, con su propio permiso, paginado».

**Ningún listado de hoy contesta esa pregunta.** `RF-MV-006` (`GET /movements`) devuelve **una fila por venta**, con sus importes agregados: para saber qué productos se vendieron hay que abrir cada venta, y una tabla de mil ventas son mil peticiones. `RF-MV-015` (`GET /movements/sales`) tiene la misma forma con otro alcance. Y `RF-MV-014` (`GET /movements/mine/products`) sí publica **una fila por línea** —es exactamente la forma que hace falta— pero **acotada a lo propio**: lo que compró quien pregunta. Este requerimiento es **su contraparte de administración**.

**Nada del esquema cambia.** La línea existe desde `V7` (`movement_details`) y ha ido ganando todo lo que esta consulta publica: el **vendedor por línea** con `V12` —la comisión se calcula por línea, no por venta—, el **nombre del producto congelado** y el **descuento de línea** con `V14`, y el **estado de entrega** con `V16`. Lo único que nace aquí es la operación y su permiso.

**Es de administración y no tiene alcance por estructura**, y conviene decirlo antes de que alguien lo lea como una incoherencia: `RN-MV-031` —el vendedor ve su red, el consumidor lo suyo— gobierna `movements:list-sales` y **solo** eso. Aquí el permiso es la puerta y con él se ve todo, como en `GET /movements`. Darle alcance a este permiso lo convertiría en dos cosas a la vez, y un vendedor con él vería las líneas de la empresa entera; quien quiera ver lo suyo tiene `RF-MV-014`, y quien quiera ver su red, `RF-MV-015`.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador con `movements:list-sale-lines` | Consulta las líneas de venta del libro |

## 4. Alcance

### 4.1 Incluye

- Listar **todas** las líneas de las ventas, paginadas y de la más reciente a la más antigua.
- Publicar en cada fila **lo de la línea** y **lo de la venta que la explica**.
- Filtrar por venta, persona, vendedor, producto, estado de la venta, estado de entrega, **estado del tipo** y rango de fechas, **combinables**.
- Un total **acotado** por el techo de conteo de `RF-SP-011`.

### 4.2 No incluye

- **Los demás tipos de movimiento.** Se acota a las **ventas** (`movement_types.code = 'VENTA'`), como `RF-MV-015`. El día que haya depósitos, su línea —si la tiene— declarará su consulta.
- **Alcance por estructura.** §2 y `RN-MV-031`.
- **Cambiar nada.** Es de lectura; no confirma, no entrega, no anula.
- **El cupón del bot.** `RF-MV-014` lo publica a quien compró y **solo** si está entregado, porque publicarlo antes sería entregar sin autorización. Aquí no se publica: administración tiene el catálogo de productos para eso, y un listado de mil filas no es el sitio para repartir accesos.
- **Un resumen ni totales agregados.** La pregunta es qué líneas hay; sumar importes es otra consulta y tendrá la suya.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-MV-002` | Lo vendido se **congela** en la línea: el nombre y el precio son los del día de la venta, y no se releen | `requirements/mv.md` §5.1 |
| `RN-MV-003` | El vendedor es **de la línea**, no de la venta | `requirements/mv.md` §5.1 |
| `RN-MV-033` | Cada tipo de movimiento declara **sus** estados, un eje aparte del pago y de la entrega (23-09-2026, `RF-MV-016`) | `requirements/mv.md` §5.1 |
| `RN-SEG-014` | Un permiso gobierna una operación o ninguna | `security.md` §4.3 |
| `RN-SEG-015` | Autenticarse no autoriza nada: la ruta exige permiso | `security.md` §4.3 |
| `RF-SP-011` | El total se cuenta **hasta un techo** y se declara si es exacto | `specs/sp/011-*` |

## 6. Datos

### 6.1 Entrada

Todo por *query string*, todo opcional, y **se combinan**:

| Dato | Descripción | Restricción |
|---|---|---|
| `page`, `size` | Paginación | La del sistema; `size` acotado |
| `movementId` | Las líneas de **una** venta | `uuid`; inexistente → página vacía |
| `userId` | Qué compró **esta persona** (el sujeto de la venta) | `uuid`; inexistente → página vacía |
| `sellerId` | Qué vendió **esta persona**, como vendedora **de la línea** | `uuid`; inexistente → página vacía |
| `productId` | Las líneas de **un producto** del catálogo | `uuid`; inexistente → página vacía |
| `status` | El estado de la **venta** | Del catálogo cerrado; otro valor es `400` |
| `deliveryStatus` | El estado de **entrega de la línea** | Del catálogo cerrado; otro valor es `400` |
| `typeStatus` | El **estado del tipo** de la venta —`VALIDAR_COMISIONES` o `VALIDADO`— (23-09-2026, `RF-MV-016`) | Del catálogo de estados por tipo; otro valor es `400`. **No se publica en la fila**: §14.7 |
| `code` | El comprobante exacto de la venta, sin distinguir caja | — |
| `from`, `to` | **Cuándo ocurrió la venta**; instantes con zona, rango **semiabierto** | `from` posterior a `to` es `400` |

**Los identificadores inexistentes dan página vacía y los estados inexistentes dan `400`**, y la asimetría es la que `RF-MV-006` ya fijó: el catálogo de estados es **cerrado** y equivocarse es un error del cliente, mientras que preguntar por una persona que no existe es una pregunta legítima con respuesta vacía — y responder `404` convertiría el filtro en un oráculo de quién existe.

### 6.2 Salida

`200` con la página. Cada fila es **la línea y su venta**:

| Campo | De dónde | Nota |
|---|---|---|
| `lineId` | `movement_details.id` | Identifica la fila; no se publica en ninguna otra consulta |
| `movementId`, `movementCode`, `movementStatus` | la venta | Para abrir el detalle con `RF-MV-007` |
| `occurredAt` | la venta | Cuándo se vendió; es el orden |
| `client` | `movements.user_id` | `id` y `username`: el **sujeto**, a nombre de quién es |
| `seller` | `movement_details.seller_id` | `id` y `username`. **Presente y nulo** cuando la línea no lo tiene |
| `product` | la línea | `id` y el **nombre congelado** (`RN-MV-002`), no el de hoy |
| `quantity`, `unitPrice`, `lineDiscount`, `lineAmount` | la línea | Los cuatro, porque sin el descuento los importes no cuadran |
| `validityDays` | la línea | Presente y nulo si lo comprado no caduca |
| `currency` | la venta | El código; el importe sin su moneda no dice nada |
| `implementation`, `deliveryStatus`, `deliveredAt`, `deliveryNote` | la línea | El estado de entrega **tal como está en la línea** |

**El estado de entrega va crudo y no derivado**, al contrario que en `RF-MV-014`, que calcula un estado único a partir de la venta, la entrega y la vigencia. Allí lo pide quien compró —que necesita una respuesta, no cuatro campos— y aquí lo pide administración, que necesita saber por qué algo está donde está. Derivarlo obligaría además a repetir aquí la máquina de estados de `RF-MV-014`, y dos copias divergen.

**`totalIsExact`** viaja con la página: por encima del techo el total **es** el techo (`RF-SP-011`). Aquí importa más que en cualquier otro listado, porque `movement_details` es la tabla que crece más rápido del sistema — una venta de cinco productos son cinco filas.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `movements:list-sale-lines`.

**Postcondiciones:** ninguna. Es una lectura y **no escribe nada**, ni auditoría: consultar no es un hecho que se registre (el sistema no audita lecturas, `security.md` §7).

## 8. Flujo principal

1. Llega la petición con sus filtros, o sin ninguno.
2. El sistema valida la forma de todos **juntos** (§11).
3. El sistema consulta las líneas de las ventas que cumplen los filtros, ordenadas por fecha de la venta descendente.
4. El sistema cuenta el total **hasta el techo**.
5. Devuelve `200` con la página, aunque esté vacía.

**Dos sentencias y no más**, con o sin filtros y con una fila o con veinte: la página y el conteo acotado. Todo lo que la fila publica viaja en la misma sentencia de la página, con sus uniones; una consulta por línea para resolver el producto o el vendedor sería el `N+1` que `CA-MV-178` existe para impedir.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | Ningún filtro | Todas las líneas de todas las ventas, paginadas |
| `FA-002` | La venta tiene **varias líneas** | Salen **todas**, una fila cada una, con los mismos datos de venta repetidos. No se agrupa: la fila es la línea |
| `FA-003` | La línea **no tiene vendedor** | Sale igual, con `seller` **presente y nulo**. Ocurre en los tipos de movimiento que no venden y en las ventas que otro requerimiento genere sin vendedor de línea |
| `FA-004` | La venta está **anulada o rechazada** | Sus líneas salen, con el `movementStatus` que tengan. Administración necesita ver lo que se deshizo; filtrarlo es para lo que está `status` |
| `FA-005` | El producto **se retiró del catálogo** después de la venta | La línea sale con el **nombre congelado** (`RN-MV-002`): lo que se vendió no cambia porque el catálogo cambie |
| `FA-006` | La persona que compró o vendió **se eliminó** | La línea sale igual, con su `username`: la baja es lógica y el libro no se reescribe |
| `FA-007` | Filtro por alguien inexistente | Página **vacía**, no error (§6.1) |

## 10. Excepciones

Ninguna propia. Sin el permiso, `403` (`AUTH-002`); sin token, `401` (`AUTH-001`). No hay `404`: es un listado, y su recurso es la colección.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Los identificadores son `uuid` bien formados | El identificador indicado no es válido. |
| `VAL-002` | `status` pertenece al catálogo de estados de movimiento | El estado indicado no es válido. |
| `VAL-003` | `deliveryStatus` pertenece al catálogo de estados de entrega | El estado de entrega indicado no es válido. |
| `VAL-004` | `from` no es posterior a `to` | El rango de fechas es inválido: `from` no puede ser posterior a `to`. |
| `VAL-005` | La paginación es válida | La del sistema |
| `VAL-006` | `typeStatus` pertenece al catálogo de estados por tipo (23-09-2026) | El estado del tipo indicado no existe. |

**Los seis se devuelven juntos**, como en `RF-MV-006` y `RF-MV-015`: quien se equivocó en dos filtros corrige una vez.

**El `VAL-006` viaja en el cuerpo con el código `VAL-005`**, que es el que `RF-MV-015` devuelve para este mismo error. El número de esta tabla es la etiqueta de la especificación y el del cuerpo es lo que lee el cliente: para él importa que el mismo filtro mal escrito se llame igual en los dos listados. Aquí ya ocurría con la paginación, que viaja con el `VAL-003` del sistema.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-163` | Sin filtros, el sistema devuelve **una fila por línea** de todas las ventas, paginadas y de la más reciente a la más antigua; una venta de tres líneas aporta **tres filas** |
| `CA-MV-164` | Cada fila trae lo de la línea y lo de su venta, según §6.2, con el **nombre del producto congelado** y no el del catálogo de hoy |
| `CA-MV-165` | `seller` viaja **presente y nulo** cuando la línea no tiene vendedor, y la fila **no desaparece** |
| `CA-MV-166` | `movementId` acota a las líneas de una venta; un identificador inexistente da **página vacía** |
| `CA-MV-167` | `userId` acota por el **sujeto** de la venta y `sellerId` por el **vendedor de la línea**: una venta con dos líneas de dos vendedores distintos aparece **una vez por cada uno** al filtrar por él |
| `CA-MV-168` | `productId` acota a las líneas de un producto, aunque su nombre haya cambiado después |
| `CA-MV-169` | `status` acota por el estado de la venta, y las líneas de una venta **anulada** salen cuando no se filtra |
| `CA-MV-170` | `deliveryStatus` acota por el estado de entrega **de la línea**: dos líneas de la misma venta en estados distintos se separan |
| `CA-MV-171` | `code` acota al comprobante exacto, **sin distinguir mayúsculas** |
| `CA-MV-172` | `from`/`to` acotan por **cuándo ocurrió la venta**, con el rango **semiabierto**: una venta en el instante de `from` entra y una en el de `to` no |
| `CA-MV-173` | Los filtros **se combinan**, y la combinación que no encuentra nada devuelve página vacía con `200` |
| `CA-MV-174` | El sistema rechaza con `400` el `status` desconocido, el `deliveryStatus` desconocido, el identificador mal formado, `from` posterior a `to` y la paginación inválida, **y los devuelve juntos** |
| `CA-MV-175` | El total es **acotado**: por encima del techo vale el techo y `totalIsExact` dice `false`; por debajo es exacto y dice `true` |
| `CA-MV-176` | Sin `movements:list-sale-lines` responde `403` **aunque el actor porte `movements:read`, `movements:list-sales` y `movements:read-own-products`**, y `EndpointPermissionsIT` recibe la ruta con su código |
| `CA-MV-177` | `V37` siembra el permiso **solo** para `SUPERADMIN` y `ADMIN`, con la contención de `RN-SEG-003`; el catálogo cuenta **ciento treinta y cinco** |
| `CA-MV-178` | El número de sentencias **no crece** con el tamaño de la página: dos con una fila y dos con veinte |
| `CA-MV-179` | La consulta **no devuelve** líneas de movimientos que no son ventas |
| `CA-MV-180` | `typeStatus` acota por el estado del tipo de la venta: pidiendo `VALIDADO` no salen las líneas de una venta en `VALIDAR_COMISIONES`, y se **combina** con los demás filtros |
| `CA-MV-181` | Un `typeStatus` desconocido responde `400` sobre el campo `typeStatus`, **junto a los demás problemas** de la misma petición |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Una venta con cinco líneas del mismo producto | Imposible: `uq_movement_details_producto` lo impide por venta |
| Dos líneas de la misma venta con vendedores distintos | Dos filas, cada una con el suyo. Es el caso que `RN-MV-003` existe para permitir |
| Mil ventas de cinco líneas | Cinco mil filas paginadas; el techo de conteo dice «más de» y `totalIsExact` lo declara |
| Una línea de un producto eliminado del catálogo | Sale: el nombre está congelado en la línea (`FA-005`) |
| `size` enorme | Lo acota la paginación del sistema, como en todo listado |
| Filtrar por `deliveryStatus` en una venta pendiente de pago | Sale si su línea está en ese estado de entrega: son dos ejes distintos y se combinan |
| Filtrar por un `typeStatus` que existe pero pertenece a otro tipo de movimiento | Página vacía, y no `400`: el código existe en el catálogo —de modo que la pregunta está bien escrita— pero el listado solo mira ventas |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Con alcance por estructura, como `RF-MV-015`? | **No** (23-09-2026, responsable del proyecto). Es de administración: el permiso abre y con él se ve todo. El alcance vive en `movements:list-sales`, y darle alcance a este permiso haría que un vendedor con él viera la empresa entera |
| 2 | ¿Una fila por línea o una por venta con sus líneas dentro? | **Una por línea.** Anidar las líneas dentro de la venta es lo que ya hace el detalle (`RF-MV-007`), y hace la respuesta impaginable: el tamaño de página dejaría de significar filas |
| 3 | ¿El estado de entrega crudo o derivado como en `RF-MV-014`? | **Crudo** (§6.2). Administración necesita saber **por qué** algo está donde está, y derivarlo obligaría a repetir aquí una máquina de estados que ya vive en `RF-MV-014` — dos copias divergen |
| 4 | ¿Se publica el cupón del bot? | **No** (§4.2). Es el medio de la entrega y `RF-MV-014` lo reparte a quien compró, solo si está entregado |
| 5 | ¿Solo ventas, o cualquier movimiento? | **Solo ventas**, como `RF-MV-015`. Hoy es el único tipo que existe, de modo que la diferencia es de intención: el día que haya depósitos, sus líneas no entran aquí por descuido |
| 6 | ¿Hace falta un tipo nuevo en el esquema? | **No.** Todo lo que la fila publica está en `movement_details` y en `movements` desde `V16`. Nace la operación, no el dato |
| 7 | ¿Se **publica** `typeStatus` en cada fila, como en `RF-MV-006` y `RF-MV-015`? | **No** (23-09-2026, responsable del proyecto), y **sí se filtra** por él. Queda escrito porque la asimetría se lee como un olvido: acotar por el estado del tipo es la pregunta de administración —«¿qué falta por validar?»—, y traer la columna en cinco mil filas se paga para responder otra que este listado no hace. Quien necesite verlo lo tiene en `RF-MV-006` y en el detalle |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 23-09-2026 | Redacción inicial, el día que el responsable del proyecto pidió «un endpoint para traer todas las líneas de las ventas, con su propio permiso, paginado». Hereda de `RF-MV-014` la forma de la fila —una por línea, con el nombre congelado— y de `RF-MV-006` la familia de filtros, el `400` conjunto y el techo de conteo. Decide: **administración sin alcance por estructura**, una fila por línea, el estado de entrega **crudo**, sin cupón y solo ventas. Diecisiete criterios, `CA-MV-163` a `CA-MV-179`. | Responsable del proyecto |
| 0.2.0 | 23-09-2026 | **Entra el filtro `typeStatus` y el campo NO se publica** (enmienda del Art. I.7, el día que `RF-MV-016` integró el eje de estados por tipo), por decisión del responsable del proyecto. Los filtros pasan a **ocho** y los criterios a **diecinueve**: nacen `CA-MV-180` y `CA-MV-181`, y `VAL-006` —que viaja en el cuerpo con el código `VAL-005`, el mismo que `RF-MV-015`—. Entra `RN-MV-033` en §5. La fila no cambia, de modo que **ninguna forma publicada se toca**: quien ya consumía el listado no nota la enmienda. | Responsable del proyecto |
