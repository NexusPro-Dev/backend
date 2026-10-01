# SPEC — `RF-MV-043` Consultar los pagos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-043` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que **quien administra vea todos los intentos de pago del sistema** —de cualquier persona y de cualquier tipo de movimiento que se pague— y pueda acotarlos para responder las preguntas de caja: qué cobros están pendientes, qué se rechazó, qué entró por un medio, qué se intentó en un periodo y qué pagos tiene un comprobante.

---

## 2. Contexto

**Desde el 26-09-2026 el pago es un intento, y un movimiento puede tener varios** (`RN-MV-039`, [`requirements/mv.md`](../../../requirements/mv.md) §4.3). Esa decisión prometía algo que hoy no se puede cumplir: §4.3 dice que el número que protegía la vieja venta `RECHAZADA` —**cuánto se intenta cobrar y no entra**— «sale de los pagos con más detalle que antes, porque cuenta intentos y no ventas». **Ninguna lectura lo saca.** Los pagos solo se ven **dentro** del detalle de un movimiento (`RF-MV-007`), uno por uno, y el libro (`RF-MV-006`) devuelve movimientos con el método de su **último** pago: un movimiento con tres intentos rechazados y uno confirmado aparece allí como una fila confirmada, y los tres rechazos no se ven en ninguna parte.

**Con la tarjeta la pregunta se vuelve diaria.** Desde la etapa 4 (§4.6) un pago con tarjeta puede quedar **pendiente** mientras quien compra reintenta, y una tarjeta rechazada **no** cierra el pago: se registra lo que contestó el banco y el pago sigue pendiente. Quien atiende a un cliente que dice «pagué y no me llegó» necesita encontrar **su pago**, no su venta.

**Lo pidió el responsable del proyecto el 01-10-2026** —«un endpoint para ver todas las transacciones… las payments»— y decidió, preguntado antes de escribir, **las tres cosas que lo delimitan**:

| Pregunta | Decisión |
|---|---|
| ¿Quién lo ve? | **Solo administración**: todos los pagos, de cualquier persona. Ni una vista propia ni un alcance por estructura comercial |
| ¿Qué tipos de movimiento entran? | **Todos los que tienen pagos**, con un filtro por tipo |
| ¿Con qué se acota? | **Estado y medio de pago**, **periodo**, **la persona** y **el comprobante** del movimiento |

### 2.1 Se llama «pagos» y no «transacciones»

**Por lo mismo que la tabla** ([`requirements/mv.md`](../../../requirements/mv.md) §7.7): el nombre tiene que decir qué es la fila sin que haya que preguntarlo, y «transacción» en este sistema ya significa otra cosa. La fila es **un intento de cobrar o de pagar un movimiento**, con su método, su importe y su estado.

### 2.2 No es el libro con otra fila, y por eso es otro requerimiento

`RF-MV-006` responde por **movimientos** —qué se vendió, qué se retiró— y este por **intentos de pago** —con qué se intentó cobrarlo y qué contestó quien cobra—. Una venta es **una** fila allí y **tantas como intentos** aquí. Meter los intentos en el libro con un parámetro daría una respuesta cuya fila cambia de significado según la petición, y un total que unas veces cuenta ventas y otras intentos. Son dos preguntas, dos filas y dos permisos (`RN-SEG-014`).

### 2.3 Qué tipos tienen pagos hoy, y qué significa el sentido

**No todo movimiento tiene pagos** ([`requirements/mv.md`](../../../requirements/mv.md) §4.3): un pago es dinero que entra o sale **por fuera** de la plataforma. Hoy los tienen **la venta** y **la compra de puntos** —dinero que entra— y **el retiro** —el de la transferencia que lo liquida, dinero que sale—. El pago de una comisión y el bono no mueven dinero por fuera y **no aparecen** aquí, sin que haga falta excluirlos: no tienen intentos.

**El sentido lo dice el tipo**, y por eso cada fila lo lleva. Este listado **no suma**: un total que mezclara lo que entra con lo que sale no respondería ninguna pregunta, y uno que los separara sería un informe con sus propias reglas —¿los pendientes cuentan?, ¿los reembolsos restan?— que este listado no decide. Es el argumento de `RF-MV-006` §2.2, y aquí pesa más.

### 2.4 Lo que acota la consulta

| Filtro | Pregunta que responde |
|---|---|
| Estado | «¿Qué cobros siguen pendientes?» y «¿qué se rechazó?» — la segunda es la que §4.3 prometía |
| Medio de pago | «¿Qué se intentó por tarjeta?» — quien concilia contra el panel de la pasarela, o contra un extracto |
| Tipo de movimiento | «¿Qué pagos de retiros hubo?» — separa lo que entra de lo que sale (§2.3) |
| Persona | «¿Qué intentó pagar esta persona?» — **a nombre de quién es el movimiento**, como el sujeto del libro |
| Comprobante | «¿Qué intentos tiene esta venta?» — por el código que la persona cita al llamar, **o una parte de él** |
| Periodo | «¿Qué se intentó entre estas dos fechas?» — sobre **cuándo se intentó**, no cuándo se confirmó |

**Se combinan**: los rechazados **por tarjeta** **en septiembre** son una sola pregunta.

**Lo que NO se ofrece, y es decisión del responsable del proyecto:** filtrar por **la incidencia** de la pasarela —reembolsos y disputas— y buscar por **la referencia** del cobro en la pasarela. La incidencia ya se filtra en el libro (`RF-MV-006` §14.2), que es donde administración la decide; y las dos **viajan en cada fila**, de modo que se ven aunque no se filtre por ellas. Tampoco se ofrece buscar por texto libre, ordenar por otro criterio ni sumar.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene el permiso de consultar los pagos | Consulta **todos** los pagos, con los filtros que quiera. Se siembra al superadministrador y al administrador |

**Quien no lo tiene no ve nada por aquí**, aunque tenga pagos propios. Lo propio se ve en el detalle de cada compra propia (`RF-MV-008`).

---

## 4. Alcance

### 4.1 Incluye

- El **listado paginado** de todos los pagos del sistema, del intento más reciente al más antiguo.
- Los **seis filtros** de §2.4, combinables.
- En cada fila: el pago —estado, importe, medio, cuándo se intentó, se confirmó o se rechazó, por qué no entró, la referencia de quien cobra y la incidencia de la pasarela— y **el movimiento al que pertenece** —su identificador, su comprobante, su tipo, su estado, su moneda y a nombre de quién está—.

### 4.2 No incluye

- **El detalle del movimiento**, con sus líneas → `RF-MV-007`. La fila lleva su identificador para abrirlo.
- **Una vista de los pagos propios** ni **por estructura comercial** (§2).
- **Resolver un pago** —confirmarlo, rechazarlo, volver a pagarlo—: tienen sus requerimientos (`RF-MV-003`, `RF-MV-004`, `RF-MV-018`, `RF-MV-028`, `RF-MV-029`).
- **Filtrar por incidencia o por referencia de la pasarela**, buscar por texto, ordenar a elección, **sumar** y exportar (§2.4).
- **Lo que contestó la pasarela en cada notificación**: es constancia técnica, no una fila de caja.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-001` | Esta operación **solo lee** |
| `RN-MV-026` | «La persona» es **a nombre de quién está el movimiento**: en una venta, quien compra; en un retiro, quien retira |
| `RN-MV-037` | El comprobante se busca **por fragmento**, sin distinguir mayúsculas, y lo que se escribe es texto y no patrón — como en el libro |
| `RN-MV-039` | Cada fila es **un intento**: un movimiento aparece tantas veces como intentos tenga, y sus estados son los del pago —pendiente, confirmado, rechazado—, no los del movimiento |
| `RN-MV-060` | La incidencia **viaja** en la fila del pago confirmado que la tiene, y **no cambia su estado**: un pago reembolsado sigue saliendo como confirmado |

**Ninguna regla nueva.** Este requerimiento no decide nada sobre los pagos: los muestra.

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Página, tamaño | No | Como todo listado del sistema |
| Estado | No | Solo los pagos en ese estado: pendiente, confirmado o rechazado. Uno que no exista es un **error** |
| Medio de pago | No | Solo los intentados con ese medio. Uno que no exista da una **página vacía** |
| Tipo de movimiento | No | Solo los pagos de movimientos de ese tipo, por su código, sin distinguir mayúsculas. Uno que no esté en el catálogo es un **error** |
| Persona | No | Solo los pagos de movimientos a nombre de esa persona. Una que no exista da una **página vacía** |
| Comprobante | No | Los pagos de los movimientos cuyo comprobante **contenga** lo escrito, sin distinguir mayúsculas |
| Desde, hasta | No | Instantes con zona horaria sobre **cuándo se intentó**. **Semiabierto** —incluye «desde», excluye «hasta»—. «Desde» posterior a «hasta» es un **error** |

**El estado y el tipo son errores; el medio y la persona, página vacía**, por el argumento de `RF-MV-006` §6.1, que vale entero: los estados y el catálogo de tipos son conjuntos **cerrados que el sistema declara**, y pedir uno inventado es una pregunta mal escrita; los medios y las personas son **datos**, y preguntar por uno que no está devuelve lo que hay, que es nada.

**El estado es el del pago y no el del movimiento**, y se escribe en masculino —pendiente, confirmado, rechazado— por la misma razón: una venta confirmada puede tener pagos rechazados, y un filtro que los confundiera devolvería justo lo contrario de lo que se pide.

### 6.2 Salida

Cada pago devuelve:

| Dato | Descripción |
|---|---|
| Identificador | El del intento |
| Estado | Pendiente, confirmado o rechazado |
| Importe y moneda | Lo que se intentó cobrar o pagar. La moneda es la del movimiento |
| Medio de pago | Con qué se intentó |
| Cuándo se intentó | La fecha del intento |
| Cuándo se confirmó, cuándo se rechazó | Cada una **presente solo** en el estado que la tiene |
| Por qué no entró | Lo que contestó quien cobra, o el motivo de la anulación del movimiento. Presente solo en los rechazados que lo tienen |
| Referencia de quien cobra | La del cobro en la pasarela, o la de la transferencia de un retiro. **Ausente** si nadie la dio |
| Incidencia | Reembolsado, en disputa, disputa ganada o perdida, **con su fecha** y, si es un reembolso, **lo devuelto**. Ausente en los que no la tienen |
| **El movimiento** | Su identificador, su comprobante, **su tipo**, su estado, y **a nombre de quién está** |

**«Ausente» significa nulo y presente**, como en el libro: quien pinta la fila tiene que poder distinguir «no la tiene» de «no la mandaron».

**No lleva la clave de idempotencia**, como el detalle: es del cliente que la mandó, y a quien administra no le dice nada.

**El total puede ser aproximado**, por el argumento de `RF-MV-006` §6.2, y aquí con más razón: hay **al menos un pago por cada movimiento que se paga**, y varios por cada uno que se reintentó.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y tiene el permiso de consultar los pagos |
| Postcondición | **Ninguna.** No se escribe nada, no se audita nada |

**No se audita**, por el criterio de `RF-MV-006` §7: consultar con permiso no es un evento. Lo que se audita es **conceder** el permiso.

---

## 8. Flujo principal

1. El actor pide los pagos, con los filtros que quiera.
2. El sistema comprueba que tiene el permiso.
3. Selecciona los pagos que cumplen **todos** los filtros indicados; sin filtros, todos.
4. Ordena del intento más reciente al más antiguo y devuelve la página pedida, con el total hasta el techo.

---

## 9. Flujos alternativos

### FA-001 — Ningún pago cumple los filtros

Página **vacía**, no un error. Vale también para el medio y la persona que no existen (§6.1).

### FA-002 — Un movimiento con varios intentos

Aparece **una vez por intento**, cada uno con su estado. Es el caso que justifica el requerimiento (§2): tres rechazos y un confirmado son cuatro filas.

### FA-003 — Hay más pagos que el techo del conteo

La página se devuelve igual; el total **es el techo** y la respuesta declara que no es exacto.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pregunta no tiene el permiso | Prohibido, **aunque tenga pagos propios** |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La página no es negativa y el tamaño está dentro del límite del sistema |
| `VAL-002` | El estado indicado, si viene, es uno de los tres del pago |
| `VAL-003` | Los identificadores del medio y de la persona, si vienen, están bien formados |
| `VAL-004` | «Desde» y «hasta», si vienen, son instantes bien formados, y «desde» no es posterior a «hasta» |
| `VAL-005` | El tipo indicado, si viene, es uno del catálogo de tipos de movimiento |

**Los problemas de validación se devuelven juntos**, como en el libro.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-480` | Quien tiene el permiso ve pagos de movimientos **en los que no participa** |
| `CA-MV-481` | Quien **no** tiene el permiso recibe **prohibido**, aunque tenga pagos propios; **sin autenticar**, `401` |
| `CA-MV-482` | El listado va **paginado y envuelto**, del intento más reciente al más antiguo, y el orden es **estable entre páginas** |
| `CA-MV-483` | Un movimiento con **varios intentos** aparece **una vez por intento**, cada uno con su estado |
| `CA-MV-484` | El filtro por **estado** devuelve solo los pagos en ese estado —también los rechazados de un movimiento confirmado—; un estado que no existe es un **error** |
| `CA-MV-485` | El filtro por **medio de pago** devuelve solo los intentados con él; uno que no existe da página vacía |
| `CA-MV-486` | El filtro por **tipo** devuelve solo los pagos de movimientos de ese tipo, en mayúsculas o minúsculas; uno que no existe es un **error**, devuelto **junto** con los demás problemas |
| `CA-MV-487` | El filtro por **persona** devuelve solo los pagos de movimientos a su nombre; una que no existe da página vacía |
| `CA-MV-488` | El filtro por **comprobante** devuelve los pagos de los movimientos que lo **contienen**, sin distinguir mayúsculas, y `%` o `_` se buscan como texto |
| `CA-MV-489` | El **periodo** es sobre cuándo se intentó, incluye «desde» y excluye «hasta»; «desde» posterior a «hasta» es un error |
| `CA-MV-490` | Los filtros **se combinan**: con dos puestos, solo lo que cumple los dos |
| `CA-MV-491` | Cada fila trae el pago —estado, importe, moneda, medio, cuándo se intentó— y **el movimiento** —identificador, comprobante, tipo, estado y persona— |
| `CA-MV-492` | Las fechas de confirmación y de rechazo, el motivo, la referencia y la incidencia van **nulas y presentes** cuando no aplican; la clave de idempotencia **no viaja** |
| `CA-MV-493` | Un pago **reembolsado** sale **confirmado**, con su incidencia, su fecha y lo devuelto |
| `CA-MV-494` | Por encima del techo del conteo, el total **es el techo** y la respuesta lo declara **inexacto**; por debajo, es el real y exacto |

**`CA-MV-480`, `CA-MV-481` y `CA-MV-483` sostienen el requerimiento**: los dos primeros, que el permiso es lo único que abre lo ajeno, como `CA-MV-068` y `CA-MV-069` en el libro; el tercero, que la fila es el intento y no el movimiento — sin él, este listado sería el libro con otra forma.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un movimiento con **un pago rechazado y otro confirmado** | Dos filas. Filtrar por rechazado devuelve una, aunque el movimiento esté confirmado |
| El pago de una venta **anulada** | Aparece **rechazado**, con el motivo de la anulación (§4.3) |
| Un pago con tarjeta **pendiente con tarjetas rechazadas** | Una fila **pendiente**: los rechazos del banco no cierran el pago (§4.6), y no son intentos aparte |
| El pago de un **retiro** | Aparece con el tipo `RETIRO`, el método manual y, si la hay, la referencia de la transferencia |
| Un pago **gratuito** o **con puntos** | Aparece, con su medio y su importe. Es un intento como cualquier otro |
| Un movimiento cuya **persona fue eliminada** | Aparece, con sus datos tal como están |
| Un **periodo** abierto por un lado | Abierto por ese lado |
| Dos pagos **en el mismo instante** | El orden entre ellos es estable |
| Ningún pago en el sistema | Página vacía y total cero, exacto |

---

## 14. Preguntas abiertas

**Los pagos propios.** Una persona ve los suyos dentro del detalle de cada compra, y no tiene un listado. Si se pide, es un requerimiento propio con su permiso por tipo de rol (`RN-SEG-015`), y la fila de este le sirve de base.

**Sumar.** «¿Cuánto entró por tarjeta en septiembre?» es la pregunta que vendrá después de esta, y es un informe con reglas que este listado no decide (§2.3).

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, a petición del responsable del proyecto —«un endpoint para ver todas las transacciones… las payments»— y con sus decisiones, preguntadas antes de escribir: **solo administración**, **todos los tipos que tienen pagos** con filtro por tipo, y como filtros **estado, medio, periodo, persona y comprobante** —ni la incidencia ni la referencia de la pasarela, que viajan en la fila—. **La fila es el intento y no el movimiento** (§2.2): cumple lo que [`requirements/mv.md`](../../../requirements/mv.md) §4.3 prometía —«cuánto se intenta cobrar y no entra»— y que ninguna lectura sacaba. Se escribe sobre `RF-MV-006` y hereda su argumentación: errores para los conjuntos cerrados, página vacía para los datos, periodo semiabierto, total acotado, sin sumas y sin auditoría. Criterios `CA-MV-480` a `CA-MV-494`. | Responsable del proyecto |
