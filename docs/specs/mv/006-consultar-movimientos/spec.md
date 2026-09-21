# SPEC — `RF-MV-006` Consultar los movimientos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-006` |
| Módulo | `MV` — Movimientos |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Enmendada el | 21-09-2026 — los movimientos se filtran también **por tipo** (§2.2, §6.1, §11, §12, §13). Ver §15 |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que **quien administra vea todos los movimientos registrados en el sistema** —de quien sean, los haya vendido quien los haya vendido— y pueda acotarlos para responder las preguntas de la operación: qué está pendiente de cobrar, qué compró una persona, qué vendió otra, qué entró por un medio de pago y qué ocurrió en un periodo.

---

## 2. Contexto

**Hoy el libro solo se puede leer desde dentro.** `RF-MV-008` responde «¿en qué movimientos participé yo?», y esa es la única lectura que existe. Nadie puede responder «¿qué se vendió ayer?» ni «¿cuántas ventas hay pendientes de confirmar?», que son las dos preguntas que hacen falta **antes** de que `RF-MV-003` exista: quien va a confirmar un pago tiene que poder encontrar la venta que lo espera, y hoy solo la encuentra su comprador y su vendedor.

**Este requerimiento estaba declarado desde el 02-09-2026** en `requirements/mv.md` §4.1, sin especificar, y §5.3 ya había decidido lo único que lo hace posible sin cerrar **D-22**: **el alcance es global y explícito** para quien tenga el permiso. No se decide aquí si un director ve a su equipo; se decide que quien tenga `movements:read` **lo ve todo**, que es la única respuesta que no cambia cuando D-22 se cierre — cuando se cierre, será una **restricción sobre esta consulta**, no una reinterpretación.

**Y es exactamente lo contrario de `RF-MV-008`, y por eso no es el mismo requerimiento con un parámetro.** Allí el requerimiento es que el alcance **no se escape**; aquí, que el permiso **no falte**. Fundirlos en una sola operación con un «solo lo mío» daría un endpoint con **dos modelos de seguridad**, que es el mismo argumento con el que `requirements/mv.md` §4.1 separó registrar de comprar. Se escribe por diferencias sobre aquel, y las diferencias son tres: **quién puede pedirlo**, **sobre quién responde** y **con qué se acota**.

### 2.1 Se llama «movimientos» y no «ventas», y hay que decirlo

El catálogo lo registró como «Consultar ventas». **Se renombra** a «Consultar los movimientos», por lo mismo que `RF-MV-008` se llama «los movimientos propios» y no «las ventas propias»: el libro es de **todos los hechos económicos** (`requirements/mv.md` §4.2), hoy solo hay ventas, y una consulta que se llamara «ventas» tendría que renombrarse el día que entre el primer depósito — o mentir. Cada fila dice **de qué tipo** es, para que ese día no cambie nada más que el contenido.

### 2.2 Lo que acota la consulta, y por qué eso y no más

Un listado de administración **sin filtros es un listado que nadie puede usar** una vez que el libro pasa de unas decenas de filas. Los filtros que se admiten son los que responden **una pregunta de operación cada uno**:

| Filtro | Pregunta que responde |
|---|---|
| Estado | «¿Qué está pendiente de confirmar?» — la pregunta que `RF-MV-003` necesitará antes que ninguna |
| Sujeto | «¿Qué compró esta persona?» — con la persona indicada, que es lo que `RF-MV-008` no puede decir |
| Vendedor | «¿Qué vendió esta persona?» — como vendedor **de alguna de sus líneas** (`RN-MV-003`) |
| Método de pago | «¿Qué entró por transferencia?» — la pregunta de quien concilia contra un extracto |
| Código | «¿Dónde está este comprobante?» — el que la persona cita cuando llama |
| Periodo | «¿Qué ocurrió entre estas dos fechas?» — sobre **cuándo ocurrió** el hecho, no cuándo se escribió |
| **Tipo** (21-09-2026) | «¿Qué depósitos hubo?» — la pregunta que el segundo tipo de movimiento traerá antes que ninguna, y que el libro puede responder desde hoy porque cada fila **dice su tipo** (§2.1). Es la otra mitad de esa promesa: un libro que dice de qué tipo es cada hecho tiene que poder preguntarse por uno solo |

**Se combinan**: el estado pendiente **de** un vendedor **en** septiembre es una sola pregunta.

**Lo que NO se ofrece, y es decisión:** buscar por texto libre —el nombre del comprador es un dato de `SP`, y buscarlo es `RF-SP-025`—, ordenar por otro criterio que el cronológico, y **sumar**. Un total de lo vendido en un periodo es un **informe**, con sus propias reglas sobre qué cuenta —¿las anuladas? ¿las pendientes?— y este listado no las decide. Ofrecer una suma «de paso» sería decidirlas sin escribirlas.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:read` | Consulta **todos** los movimientos, con los filtros que quiera. Hoy solo lo tiene el superadministrador (`requirements/mv.md` §6.1) |

**Quien no lo tiene no ve nada por aquí**, tenga o no movimientos propios. Para lo propio está `RF-MV-008`, sin permiso.

---

## 4. Alcance

### 4.1 Incluye

- El **listado paginado** de todos los movimientos del sistema, del más reciente al más antiguo.
- Los **siete filtros** de §2.2, combinables — seis desde el 17-09-2026 y el tipo desde el 21-09-2026.
- En cada fila: **el tipo** de movimiento, su estado, **el sujeto y los vendedores de sus líneas** —las mismas dos partes que `RF-MV-008`—, el medio de pago, la moneda, los importes, **cuándo ocurrió y cuándo se confirmó**.

### 4.2 No incluye

- **El detalle de un movimiento**, con sus líneas y su comprobante → `RF-MV-007`. Este listado lleva a él por el identificador; no lo sustituye.
- **Restringir por equipo.** Un director con el permiso vería aquí las ventas de todo el mundo: es **D-22** y sigue abierta.
- **Búsqueda por texto libre**, ordenamiento a elección, y **totales o sumas** (§2.2).
- **Exportar.**
- **Los movimientos propios sin permiso** → `RF-MV-008`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-001` | La venta no se toca. Esta operación **solo lee** |
| `RN-MV-003` | «Vendido por» es cosa de **cada línea**: el filtro por vendedor encuentra los movimientos con **alguna** línea suya, y cada uno aparece **una sola vez** aunque tenga varias |
| `RN-MV-004`, `RN-MV-005` | El estado y la fecha de confirmación viajan para que se vea **qué se cobró y cuándo**: una venta pendiente aparece **sin fecha de confirmación**, y una confirmada la lleva siempre |
| `RN-MV-026` | «Sujeto» es a nombre de quién ocurre el hecho: en una venta, quien compra. El filtro por sujeto se llama así y no «cliente», por lo mismo que la columna |

**Ninguna regla nueva.** Este requerimiento no decide nada sobre los movimientos: los muestra.

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Página, tamaño | No | Como todo listado del sistema |
| Estado | No | Solo los movimientos en ese estado. Uno que no exista es un **error**, no una página vacía |
| Sujeto | No | Solo los movimientos a nombre de esa persona. Una que no exista da una **página vacía**, no un error |
| Vendedor | No | Solo los movimientos con alguna línea atribuida a esa persona. Mismo trato que el sujeto |
| Método de pago | No | Solo los pagados con ese método. Mismo trato |
| Código | No | El comprobante **exacto**, sin distinguir mayúsculas. Como mucho devuelve uno |
| Desde, hasta | No | Instantes con zona horaria sobre **cuándo ocurrió** el movimiento. El rango es **semiabierto** —incluye «desde», excluye «hasta»—, para que dos periodos consecutivos no devuelvan dos veces el de la medianoche. «Desde» posterior a «hasta» es un **error** |
| Tipo (21-09-2026) | No | Solo los movimientos de ese tipo, por su **código** en el catálogo, sin distinguir mayúsculas. Uno que no exista es un **error**, no una página vacía — el mismo trato que el estado, y por el mismo motivo (abajo) |

**El tipo va con el estado y no con las personas.** El catálogo de tipos **no se edita por API y no se borra** (`RN-MV-017`): lo siembra el sistema, y el caso de uso decide según él. Es un conjunto **cerrado que el sistema declara**, aunque viva en una tabla, y pedir un tipo que no existe es la misma pregunta mal escrita que pedir un estado inventado. **Hoy el catálogo tiene un solo código**, y el filtro se define igual: lo que se promete es que discrimina el día que haya dos, no que hoy separe algo.

**Por qué el estado que no existe es un error y la persona que no existe no lo es.** Los estados son un conjunto **cerrado que el sistema declara**: pedir uno inventado es una pregunta mal escrita, y una página vacía sería una respuesta falsa a ella — «no hay ninguno así». Las personas y los métodos son **datos**: preguntar por uno que no está devuelve lo que hay, que es nada, y es el mismo trato que `RF-SP-025` da a un rol o un país inexistentes.

### 6.2 Salida

Cada movimiento devuelve:

| Dato | Descripción |
|---|---|
| Identificador y código | El código es el que la persona ve y cita |
| **Tipo** | Qué clase de hecho es. Hoy, siempre una venta |
| Estado | Pendiente, confirmada, rechazada o anulada |
| Sujeto | A nombre de quién es (`RN-MV-026`) |
| Vendedores | A quién se atribuye **cada línea**, sin repetir. Vacía y presente en los tipos que no venden nada |
| Moneda y método de pago | Con qué se paga |
| Importes | Total, descuento y lo que se paga de verdad |
| Cuándo ocurrió | La fecha del hecho |
| **Cuándo se confirmó** | Presente solo en las confirmadas. Es lo que separa «cuándo se vendió» de «cuándo entró el dinero», y quien concilia necesita las dos |

**No lleva el papel de quien pregunta**, y esa es la diferencia visible con `RF-MV-008`: quien administra no participa en lo que mira, y un papel que valiera siempre «ninguno» sería un campo que miente por omisión.

**Las líneas no viajan**, por lo mismo que allí: multiplican la respuesta por un dato que solo se mira al abrir uno.

**El total puede ser aproximado.** El libro crece sin límite —una fila por cada venta del sistema, y mañana por cada depósito—, y contar exactamente cuántas cumplen un filtro poco selectivo obliga a recorrerlas todas **en cada página**. La respuesta dice cuántos hay hasta un techo, y **declara** cuando el número real está por encima. Es lo que `RF-SP-011` decidió para la auditoría, por lo mismo; `RF-MV-008` no lo necesita porque el conjunto de una persona sí se cuenta entero.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y tiene `movements:read` |
| Postcondición | **Ninguna.** No se escribe nada, no se audita nada |

**No se audita, y conviene decir por qué aquí sí hay que pensarlo.** En `RF-MV-008` no se auditaba porque mirar lo propio no es un acceso a datos ajenos. Aquí **sí lo es**, y aun así no se registra: es el mismo criterio de `RF-SP-025` con el listado de personas y de `RF-SP-011` a `RF-SP-014` con los registros de auditoría — **consultar con permiso no es un evento**, y registrar cada página que alguien pide llenaría el registro de ruido hasta esconder lo que sí importa. Lo que sí se audita es **conceder** el permiso, y eso ya ocurre en `SP`.

---

## 8. Flujo principal

1. El actor pide los movimientos, con los filtros que quiera.
2. El sistema comprueba que tiene el permiso.
3. Selecciona los movimientos que cumplen **todos** los filtros indicados; sin filtros, todos.
4. Ordena del más reciente al más antiguo y devuelve la página pedida, con el total hasta el techo.

---

## 9. Flujos alternativos

### FA-001 — Ningún movimiento cumple los filtros

Página **vacía**, no un error. Vale también para el sujeto, el vendedor, el método o el código que no existen (§6.1).

### FA-002 — Un movimiento con varias líneas del mismo vendedor

Aparece **una sola vez** al filtrar por ese vendedor. Es el caso de toda compra de paquete (`RF-MV-012`), que produce una línea por producto con el mismo vendedor en todas.

### FA-003 — Hay más movimientos que el techo del conteo

La página se devuelve igual; el total **es el techo** y la respuesta declara que no es exacto. Quien pregunta sabe que hay «más de N» y que debe acotar. Pedir una página más allá de la última contada sigue funcionando.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pregunta no tiene `movements:read` | Prohibido. **Aunque tenga movimientos propios**: lo propio se pregunta por `RF-MV-008` |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La página no es negativa y el tamaño está dentro del límite del sistema |
| `VAL-002` | El estado indicado, si viene, es uno de los que existen |
| `VAL-003` | Los identificadores de sujeto, vendedor y método, si vienen, están bien formados |
| `VAL-004` | «Desde» y «hasta», si vienen, son instantes bien formados, y «desde» no es posterior a «hasta» |
| `VAL-005` | El tipo indicado, si viene, es uno del catálogo de tipos de movimiento (21-09-2026) |

**Los problemas de validación se devuelven juntos**, como en los listados de `SP`: quien escribió mal tres parámetros no tiene que corregir la petición tres veces.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-068` | Quien tiene `movements:read` ve movimientos **en los que no participa**: ni es su sujeto ni vendió ninguna línea |
| `CA-MV-069` | Quien **no** tiene el permiso recibe **prohibido**, aunque tenga movimientos propios |
| `CA-MV-070` | **Sin autenticar responde `401`** |
| `CA-MV-071` | El listado va **paginado y envuelto**, del más reciente al más antiguo, y el orden es **estable entre páginas** |
| `CA-MV-072` | El filtro por **estado** devuelve solo los de ese estado; un estado que no existe es un **error** y no una página vacía |
| `CA-MV-073` | El filtro por **sujeto** devuelve solo los movimientos a nombre de esa persona; una persona que no existe da una página vacía |
| `CA-MV-074` | El filtro por **vendedor** devuelve los movimientos con alguna línea suya, **una vez cada uno** aunque tenga varias |
| `CA-MV-075` | El filtro por **método de pago** devuelve solo los pagados con él |
| `CA-MV-076` | El filtro por **código** devuelve ese comprobante, escrito en mayúsculas o en minúsculas |
| `CA-MV-077` | El **periodo** incluye «desde», excluye «hasta», y «desde» posterior a «hasta» es un error |
| `CA-MV-078` | Los filtros **se combinan**: con dos puestos, solo lo que cumple los dos |
| `CA-MV-079` | Cada fila trae **el tipo**, el sujeto, sus vendedores **sin repetir** —la lista vacía y presente cuando no hay ninguno—, la moneda, el método, los tres importes y cuándo ocurrió |
| `CA-MV-080` | **Cuándo se confirmó** viaja en las confirmadas y va **nulo y presente** en las demás |
| `CA-MV-081` | La fila **no lleva el papel** de quien pregunta ni las líneas |
| `CA-MV-082` | Por encima del techo del conteo, el total **es el techo** y la respuesta lo declara **inexacto**; por debajo, es el real y exacto |
| `CA-MV-119` | El filtro por **tipo** devuelve solo los movimientos de ese tipo, escrito en mayúsculas o en minúsculas, y **se combina** con los demás; un tipo que no existe es un **error** y no una página vacía, devuelto **junto** con los demás problemas de la petición (21-09-2026) |

**`CA-MV-068` y `CA-MV-069` son los dos criterios que sostienen el requerimiento**, y son el espejo de `CA-MV-038`: aquel prueba que el permiso **no amplía** lo propio; estos prueban que el permiso **es lo único** que abre lo ajeno.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una venta **anulada** o **rechazada** | Aparece, con su estado. Es la única forma de responder «cuánto se intenta cobrar y no entra», que es el número por el que `requirements/mv.md` §4.1 separó anular de rechazar |
| Un movimiento cuyo **sujeto fue eliminado** | Aparece, con sus datos tal como están. `RF-SP-029` es un borrado lógico y la fila sigue ahí |
| Una venta con **líneas de vendedores distintos** | Aparece **una vez** al filtrar por cualquiera de ellos, y su lista de vendedores los trae a todos. Hoy ninguna entrada la produce |
| Un movimiento **sin vendedor** | Aparece en el listado sin filtrar, con la lista vacía; **no aparece** al filtrar por ningún vendedor. Es la forma que tendrán los tipos que no venden nada |
| Un **periodo** sin «hasta», o sin «desde» | Abierto por ese lado: «desde el 1 de septiembre» son todos los posteriores |
| Dos movimientos **en el mismo instante** | El orden entre ellos es estable, y no depende de la página que se pida |
| El **libro vacío** | Página vacía y total cero, exacto |
| El catálogo con **un solo tipo** (21-09-2026) | Filtrar por `VENTA` devuelve lo mismo que no filtrar. No es un defecto: el filtro existe para el día del segundo tipo, y lo que se comprueba es que **discrimina** — con un segundo tipo que solo existe en la prueba |

---

## 14. Preguntas abiertas

**Quién ve las ventas de quién.** Es **D-22** y sigue abierta. Cuando se cierre, será una restricción sobre esta consulta —«los de mi equipo»— y no un requerimiento distinto; se escribe aquí para que quien la cierre sepa dónde cae.

**El detalle.** `RF-MV-007` lo declara con el comprobante, y el comprobante no existe. Este listado devuelve el identificador de cada movimiento y hoy **no hay a dónde llevarlo** salvo que sea propio: quien administra ve la fila y no puede abrirla. No es de este requerimiento, pero es lo primero que va a faltar.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 17-09-2026 | Primera versión. **El requerimiento estaba declarado desde el 02-09-2026** en `requirements/mv.md` §4.1 como «Consultar ventas», sin especificar, y lo pide el responsable del proyecto —«un endpoint para consultar todos los movimientos registrados»—. **Se renombra a «Consultar los movimientos»** (§2.1), por lo mismo que `RF-MV-008`: el libro es de todos los hechos económicos y cada fila dice su tipo. Se escribe **por diferencias sobre `RF-MV-008`** y las diferencias son tres: el permiso `movements:read` es lo único que lo abre, el alcance es **global y explícito** —lo que §5.3 de `requirements/mv.md` había decidido para no depender de D-22—, y se acota con **seis filtros** que responden una pregunta de operación cada uno (§2.2). La fila gana **el tipo** y **cuándo se confirmó**, y pierde el papel. **El total es acotado**, como en la auditoría, porque el libro crece sin límite. Lo que se deja fuera se deja a propósito: buscar por texto, ordenar a elección, sumar, y el detalle — que es `RF-MV-007` y es lo primero que va a faltar (§14). | Responsable del proyecto |
| 0.2.0 | 21-09-2026 | **Los movimientos se filtran también por tipo** (`requirements/mv.md` v0.31.0; Art. I.7 sobre un requerimiento construido), a petición del responsable del proyecto —«que los movimientos se puedan filtrar por tipos de movimiento»—. §2.2 gana la séptima pregunta —«¿qué depósitos hubo?»—, §4.1 pasa de seis filtros a siete, §6.1 gana la entrada y el párrafo que la pone **del lado del estado y no de las personas**: el catálogo es cerrado por `RN-MV-017`, y un tipo inexistente es un **error**. `VAL-005`, `CA-MV-119` y el caso límite del catálogo con un solo tipo. **Nada más cambia**: ni el permiso, ni el alcance, ni la fila, ni el conteo. `RF-MV-008` se enmienda el mismo día con el mismo filtro. | Responsable del proyecto |
