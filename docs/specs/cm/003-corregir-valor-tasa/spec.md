# SPEC — `RF-CM-003` Corregir el valor de una tasa

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-003` |
| Módulo | `CM` — Comisiones |
| Versión | 0.3.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`.

!!! danger "Esta operación BORRA EL PASADO, y es lo primero que hay que saber de ella"

    Las tasas de rol **no tienen vigencia** desde el rediseño del 01-09-2026. No hay dos filas contando cada una su parte de la historia: hay **una que ahora dice otra cosa**.

    Pasar un `AGENTE` de 10 a 12 **borra el 10**. Y lo borra **de todo el sistema**, no solo de la vista: nada más lo guardaba.

    Lo único que preservaría lo ya pagado es que la liquidación haya copiado el porcentaje que aplicó (`RN-CM-008`) — y **esa liquidación no existe todavía**. La v0.1.0 de este documento distinguía «corregir» de «cambiar a partir de una fecha»; **esa distinción desapareció con la vigencia** y solo queda reescribir.

!!! warning "Y desde la v0.3.0 esta operación también cambia la FORMA, no solo el número"

    El requerimiento **se llamaba «corregir el porcentaje»** y ya no: con `cm.md` v0.7.0 una tasa paga un porcentaje **o** un importe fijo (`RN-CM-016`), y esta es la operación que decide cuál de los dos.

    **Cambiar la forma se decidió que sí se puede**, por el responsable del proyecto el 02-09-2026, y §14 recoge el argumento. Lo que hay que leer con el aviso de arriba es la suma de las dos cosas: pasar un `AGENTE` de «10 %» a «10.000 fijos» **borra el 10 %** exactamente igual que pasarlo a 12 — y lo que queda escrito en su lugar **no es comparable con lo anterior**.

    Esta parte se decidió **antes de construirse** —que es el orden del Art. I.6— y se construyó el mismo día: `CA-CM-090` a `CA-CM-095` en verde.

---

## 1. Objetivo

Corregir **lo que paga** una tasa de comisión mal declarada: su valor, y **la forma en que se paga**.

## 2. Contexto

Un porcentaje se teclea mal, o se aprueba uno y se registra otro. Corregirlo no debería exigir retirar la tasa y declararla de nuevo — sobre todo porque retirarla obliga antes a desasociarla de cada producto sobre el que rige (`RN-CM-015`), y volver a asociarla después.

**Y desde el 02-09-2026 la equivocación puede estar en la forma, no en el número**: se acuerda un importe fijo y se registra un porcentaje. Es el mismo tipo de error y **tiene la misma salida**, por decisión del responsable del proyecto: se corrige aquí. §14 recoge por qué se preguntó y por qué se descartó hacerla inmutable.

**Lo que esta operación NO puede hacer, y la v0.1.0 sí podía**, es cambiar lo que se paga **a partir de una fecha**. Con vigencia, eso eran dos operaciones —cerrar la vigente y registrar otra— y las dos existían. Sin vigencia **no hay «desde cuándo»**: solo hay un número, y corregirlo lo cambia hacia atrás y hacia delante a la vez.

Quien necesite conservar qué se pagó antes tiene **una sola vía, y está fuera de este módulo**: que la liquidación copie **la forma y el valor** en el momento de aplicarlos.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Corrige el valor de una tasa ya registrada, **y la forma en que se paga** |

## 4. Alcance

### 4.1 Incluye

- Corregir **el valor y su forma** en una tasa viva, **como una sola cosa**.
- **Rechazar** el intento de cambiar el rol, en lugar de ignorarlo.
- Rechazar una petición que no informa nada corregible.
- Dejar en la auditoría de cambios **la forma y el valor anteriores, y los nuevos**.

### 4.2 No incluye

- **Cambiar el rol de la tasa.** No corrige la tasa: la convierte en otra, y arrastraría consigo todas sus asociaciones a un rol que nadie eligió.
- **Cambiar lo que se paga a partir de una fecha.** No existe la operación. Ver §13.
- **Cambiar sobre qué productos rige la tasa.** Eso es asociar y desasociar (`RF-CM-007`, `RF-CM-008`).
- **Corregir una tasa personalizada.** Es la misma operación sobre otra tabla y otro recurso, y **allí sí conserva vigencia**: ver `RF-CM-006` §4.
- **Retirar la tasa**, que es `RF-CM-004`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-007` | El porcentaje va de cero a cien | `requirements/cm.md` §5.1 |
| `RN-CM-008` | La liquidación conserva lo que aplicó, y es la única defensa del pasado | `requirements/cm.md` §5.1 |
| `RN-CM-016` | **Una tasa declara una forma y solo una** | `requirements/cm.md` §5.1 |
| `RN-CM-018` | El valor fijo **no está acotado por arriba** | `requirements/cm.md` §5.1 |

**`RN-CM-008` no la cumple este requerimiento: la necesita.** Es la única regla del módulo dirigida a un módulo que no existe, y esta operación es exactamente la que la hace imprescindible.

!!! danger "Y esta versión la agrava: lo que la liquidación tiene que copiar dejó de ser un número"

    Con solo porcentajes, `RN-CM-008` se satisfacía guardando **una cifra**. Ahora hay que guardar **la forma y la cifra**, porque «10» no significa nada sin saber si son diez por ciento o diez unidades de dinero.

    Y hay una tercera: **la moneda**, que **no está en ninguna tabla de este módulo** (`RN-CM-017`) y que se toma del producto que se venda. La resolución tampoco la devuelve, por decisión del responsable del proyecto el 02-09-2026 — de modo que **quien liquide tendrá que ir a buscarla al producto**, que para entonces puede haberse retirado.

    Está escrito en `modelo-datos.md` §4.1 y se repite aquí porque **esta es la operación que hace la deuda impagable si nadie la atiende**: corregir la forma de una tasa borra la forma anterior igual que borra el número.

**`RN-CM-018` se cita porque `VAL-003` deja de aplicar a la mitad de las correcciones.** Corregir a `150` se rechaza si la forma es porcentaje y **se acepta si es importe fijo**, y esa asimetría es la regla, no un descuido.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador de la tasa | Sí | Cuál se corrige | Debe existir y **no estar retirada** |
| Forma | Sí | En qué forma paga la tasa **después** de la corrección | Una de las dos, y solo una (`RN-CM-016`). **Vaciarla se rechaza** |
| Porcentaje | **Solo si la forma es proporción** | El valor correcto | De **cero a cien** (`RN-CM-007`) |
| Valor fijo | **Solo si la forma es cantidad** | El valor correcto | **Cero o más, sin tope** (`RN-CM-018`) |

**Se distingue el campo ausente del campo enviado en vacío**, y no es un tecnicismo: son dos peticiones distintas y la segunda pide algo imposible. Enviarlo vacío se rechaza con su propio mensaje en lugar de tratarse como «no se envió».

!!! warning "La forma se envía SIEMPRE, aunque no cambie, y eso rompe la costumbre de las correcciones del proyecto"

    En el resto del sistema una corrección es parcial: se manda lo que cambia y lo demás se queda como está. Aquí **el valor y su forma viajan juntos o no viajan**.

    El motivo es que **por separado no se puede saber qué se pidió**. Un importe suelto sobre una tasa que era de porcentaje puede ser «cámbiala a importe fijo» o «me equivoqué de campo», y las dos peticiones se escriben igual. Deducirlo es exactamente lo que `RF-CM-001` §6.1 decidió que no se hace al registrar, y **corregir es donde más caro sale**: al registrar solo se pierde un alta, aquí se cambia lo que ya está pagando.

    De modo que **hay un solo campo corregible y es una pareja.** Lo que se envía no es «el porcentaje nuevo»: es **cuánto paga esta tasa a partir de ahora**.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tasa | La tasa corregida, **con su forma y su valor**, el rol resuelto y el número de productos sobre los que rige |

**La respuesta incluye sobre cuántos productos rige**, y aquí ese número tiene una lectura propia: dice **a cuántas ventas futuras afecta la corrección**. Si es cero, la corrección no cambia nada para nadie.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de corrección de tasas.
- La tasa existe y está viva.

**Postcondiciones**

- La tasa declara **la forma y el valor** corregidos.
- **Ni el valor ni la forma anteriores existen ya en la tabla**, y el registro de auditoría del cambio es el único sitio donde quedan escritos. **Los dos**, no solo el número: un evento que guardara el «10» sin decir que era un porcentaje sería tan inútil como no guardarlo.
- Si la petición no cambió nada, **la marca de última modificación no se mueve**: una petición que no cambia nada no es un cambio, y moverla haría creer que alguien tocó la tasa.

## 8. Flujo principal

1. El actor envía el identificador de la tasa, **la forma** y el valor que esa forma pide.
2. El sistema comprueba que la petición **no trae el rol**, y la rechaza si lo trae.
3. El sistema comprueba que la petición informa al menos un campo corregible.
4. El sistema comprueba que **se declaró una forma y solo una**, y que el valor presente es el que le corresponde.
5. El sistema comprueba que la tasa existe y está viva.
6. El sistema comprueba el valor contra lo que **su forma** admite: el porcentaje, entre cero y cien; el importe, cero o más.
7. Si **la forma o el valor** difieren de los que la tasa tiene, el sistema los cambia y emite el evento de auditoría con el **antes y el después de los dos**.
8. El sistema devuelve la tasa, con su forma y su valor, el rol resuelto y el número de productos asociados.

**Los pasos 2, 3 y 4 van antes de buscar la tasa**, y es deliberado: no cuesta una consulta enterarse de que la petición pedía algo que no se puede hacer.

!!! danger "El paso 7 compara DOS cosas, y comparar solo el número es el defecto que este documento existe para evitar"

    El valor viejo y el nuevo se comparan por su cifra —`10.00` y `10.0000` son la misma, y por eso `FA-002` no registra cambio—.

    **Con dos formas, esa comparación deja de bastar.** Pasar una tasa de `10 %` a `10` de importe fijo cambia **todo lo que paga**, y los dos números **comparan iguales**. Si el sistema mira solo la cifra, concluye que no hubo cambio, **no escribe nada, no audita nada y devuelve un éxito** — y la tasa se queda como estaba mientras quien la corrigió cree que la cambió.

    Es un fallo que **no se parece a su causa** y que ninguna prueba del modelo anterior podía delatar. `FA-004` y `CA-CM-091` existen para eso.

## 9. Flujos alternativos

### FA-001 — La corrección no cambia nada

**Cuándo ocurre:** el porcentaje enviado es el que la tasa ya tenía.

1. El sistema **no emite ningún evento de auditoría** y **no mueve** la marca de modificación.
2. Devuelve la tasa tal como está, con éxito. **No es un error**: pedir lo que ya es cierto no falla.

### FA-002 — El mismo valor con otra escala

**Cuándo ocurre:** se envía `10.0000` sobre una tasa que declara `10.00`, **en la misma forma**.

1. El sistema los trata como **el mismo valor** y se comporta como `FA-001`.
2. Compararlos como texto llenaría el registro de auditoría de cambios que no cambian nada.

### FA-003 — La tasa no rige sobre ningún producto

**Cuándo ocurre:** la tasa está en el catálogo y nadie la asoció.

1. La corrección se aplica con normalidad.
2. **No afecta a nada**, y la respuesta lo dice devolviendo cero productos asociados.

### FA-004 — La misma cifra en la otra forma

**Cuándo ocurre:** se envía `10` de importe fijo sobre una tasa que declara `10 %`.

1. **Es un cambio, y de los grandes.** La tasa pasa de pagar una décima parte de la venta a pagar diez unidades de dinero, y sobre un producto de 15.000 eso son 1.500 antes y 10 después.
2. El sistema lo registra como cambio, mueve la marca de modificación y **audita el antes y el después incluyendo la forma**.
3. **Es el caso contrario de `FA-002` con los mismos números**, y por eso están juntos: allí dos cifras distintas son el mismo valor, aquí dos cifras iguales son valores incomparables.

## 10. Excepciones

### EX-001 — La tasa no existe o está retirada

**Condición:** el identificador no corresponde a ninguna tasa viva.
**Respuesta del sistema:** rechaza la corrección diciendo que la tasa indicada no existe. **Una tasa retirada se trata como inexistente**: lo que se retiró debe quedar como estaba, para que lo que la referencie siga diciendo la verdad.

### EX-002 — Se intenta cambiar el rol

**Condición:** la petición trae el rol de la tasa.
**Respuesta del sistema:** rechaza la corrección diciendo que el rol no se puede corregir. **Se rechaza y no se ignora**: ignorarlo haría creer que el cambio se aplicó, y quien lo pidió seguiría creyendo que la tasa paga a otro rol.

### EX-003 — La petición no informa nada

**Condición:** no se envía ningún campo corregible.
**Respuesta del sistema:** rechaza la corrección diciendo que debe enviarse al menos un campo corregible.

### EX-004 — Se intenta vaciar la forma o el valor

**Condición:** la forma o el valor se envían explícitamente vacíos.
**Respuesta del sistema:** rechaza la corrección diciendo que no pueden vaciarse. **Una tasa sin forma no significa nada, y sin valor tampoco.** Es lo contrario del fin de vigencia de una tasa personalizada, que vaciarlo **es una orden que se cumple** (`RF-CM-006` `FA-003`) — allí el vacío declara «indefinidamente», aquí no declara nada.

### EX-005 — El valor no corresponde a la forma

**Condición:** la corrección declara una forma y envía el valor de la otra, o envía las dos, o ninguna.
**Respuesta del sistema:** rechaza la corrección diciendo que el valor no concuerda con la forma declarada. **Es la misma respuesta que al registrar** (`RF-CM-001` `VAL-011`), con el mismo mensaje, porque es el mismo error.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-002` | **La forma no se vacía** | La forma de la comisión no puede vaciarse. |
| `VAL-003` | Rango del porcentaje | El porcentaje debe estar entre cero y cien. |
| `VAL-009` | Campo no corregible | El rol de una tasa de comisión no se puede corregir. |
| `VAL-010` | Petición vacía | Debe enviarse al menos un campo corregible. |
| `VAL-011` | **El valor corresponde a la forma** | Una comisión por porcentaje lleva porcentaje y no valor fijo; una comisión por valor fijo, al revés. |
| `VAL-012` | **Valor fijo no negativo** | El valor fijo no puede ser negativo. |

**`VAL-009` sigue teniendo un solo campo y no dos.** La forma **no** entra en la lista de no corregibles — se preguntó y se decidió que sí se corrige (§14). Lo único inmutable de una tasa de rol sigue siendo **su rol**.

**`VAL-003` solo aplica a la mitad de las correcciones.** Si la forma es importe fijo, ni cien ni ningún otro número acotan nada (`RN-CM-018`), y `VAL-012` es todo lo que queda. Es la asimetría de §5.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-021` | El sistema corrige el porcentaje y devuelve la tasa con el rol resuelto y sus productos asociados |
| `CA-CM-022` | **El porcentaje anterior desaparece de la tasa**, y solo el registro de auditoría lo conserva, con el antes y el después |
| `CA-CM-023` | Una corrección que no cambia nada **no mueve** la marca de modificación |
| `CA-CM-024` | `10.0000` sobre una tasa de `10.00` no se registra como cambio |
| `CA-CM-025` | El sistema **rechaza** el intento de cambiar el rol, y la tasa conserva el suyo |
| `CA-CM-026` | El sistema rechaza vaciar el porcentaje |
| `CA-CM-027` | El sistema rechaza una petición que no informa nada corregible |
| `CA-CM-028` | El sistema trata una tasa retirada como inexistente |
| `CA-CM-090` | El sistema corrige una tasa **de porcentaje a importe fijo**, y la tasa queda en importe fijo |
| `CA-CM-091` | **`10 %` corregido a `10` de importe fijo SE REGISTRA COMO CAMBIO**, aunque las dos cifras comparen iguales |
| `CA-CM-092` | El evento de auditoría lleva **la forma anterior y la nueva**, no solo los dos números |
| `CA-CM-093` | El sistema rechaza una corrección que envía el valor **sin la forma**, y una que las envía descuadradas |
| `CA-CM-094` | `150` se **rechaza** si la forma es porcentaje y se **acepta** si es importe fijo |
| `CA-CM-095` | Los productos asociados pasan a pagar **la forma nueva**, y la respuesta lo hace visible con su cuenta |

!!! danger "`CA-CM-091` es el criterio más importante de los seis, y el único que puede fallar en silencio"

    Los otros cinco comprueban comportamientos que se ven. Este comprueba que **el sistema no confunde dos valores incomparables por tener la misma cifra**.

    Si la comparación se queda en el número —que es como está escrita hoy, y con razón, por `FA-002`—, esta corrección **devuelve éxito sin cambiar nada, sin auditar nada y sin mover la marca de modificación**. Nada falla. La tasa sigue pagando el 10 % y quien la corrigió cree que paga 10.

    Es el único sitio del módulo donde `FA-002` y `FA-004` **piden lo contrario con los mismos números**, y por eso las dos se prueban.

## 13. Casos límite

- **Corregir una tasa que rige sobre veinte productos:** los veinte pasan a pagar el porcentaje nuevo, **inmediatamente y sin aviso**. Es el comportamiento previsto —una tasa se reutiliza precisamente para eso— y la respuesta lo hace visible devolviendo cuántos son.
- **Querer cambiar el porcentaje a partir del mes que viene:** **no se puede.** La alternativa es registrar una tasa nueva y reasociar cada producto el día que corresponda, y **tampoco conserva** desde cuándo rigió cada una. Es la pérdida aceptada en `cm.md` v0.4.0.
- **Corregir hacia abajo lo que ya se pagó:** el sistema lo admite y **no puede detectarlo**, porque no sabe qué se pagó. Es `RN-CM-008` mirándonos de frente.
- **Corregir una tasa del cero por ciento:** se corrige como cualquier otra. El cero no es un estado especial, es un porcentaje.
- **Cambiar la forma de una tasa que rige sobre veinte productos:** los veinte pasan a pagar **de otra manera**, no solo otra cantidad, e **inmediatamente**. Es el caso que hizo preguntar si la forma debía ser inmutable, y §14 recoge por qué se decidió que no.
- **Corregir a importe fijo un valor mayor que el precio de los productos asociados:** se admite, **y aquí el sistema sí tendría con qué compararlo** —conoce las asociaciones— y aun así no lo hace. La comparación no serviría: el precio de un producto se corrige después (`RF-PM-004`), de modo que una comprobación hecha hoy quedaría desfasada mañana. `RN-CM-018`.
- **Corregir la forma de una tasa sin asociaciones:** no afecta a nadie, y es la única versión inofensiva de esta operación. Se distingue de las demás en la respuesta, que devuelve cero.
- **Dos correcciones simultáneas de la misma tasa:** ninguna regla lo impide y ninguna hace falta. La última escritura gana, las dos quedan en la auditoría, y no hay ninguna invariante entre filas que pueda romperse — al revés que en `RF-CM-006`.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**La que habría que hacerse ya está respondida y por eso no figura aquí:** si el módulo debería impedir corregir una tasa que rige sobre productos vendidos. No puede, porque **no existe ninguna venta**. El día que exista, esta especificación tendrá que revisarse.

**Y queda registrada una decisión que el responsable del proyecto tomó el 02-09-2026, sobre la que `cm.md` no dice nada: la forma SE PUEDE corregir.**

`RN-CM-016` exige una forma y solo una, y calla sobre cambiarla; el esquema la admite. Se preguntó porque la alternativa era defendible —hacerla inmutable, junto al rol, en `VAL-009`— y se descartó **por lo que crea, no por lo que impide**:

| Si se prohibiera | Qué pasaría |
|---|---|
| 1. Desasociar cada producto | **Dejan de comisionar**, uno a uno |
| 2. Retirar la tasa | Solo entonces lo permite `RN-CM-015` |
| 3. Registrar otra en la forma correcta | — |
| 4. Volver a asociar cada producto | — |

**La ventana entre el paso 1 y el 4 no existe hoy. Prohibir la corrección la inventaría**, y para arreglar una equivocación de tecleo. Se consideró también permitirlo solo en tasas sin asociaciones —regla nueva, `RN-CM-019`— y se descartó por lo mismo: el caso en que hace falta corregir es precisamente aquel en que la tasa ya está en uso.

Lo que se paga a cambio está escrito y es real: `CA-CM-095` deja constancia de que veinte productos cambian de forma de pago sin aviso, y la respuesta lo hace visible devolviendo cuántos son.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial. | Responsable técnico |
| 0.2.0 | 02-09-2026 | **Reescrita sobre el modelo de `cm.md` v0.4.0**, y después de construirse el código. La corrección **pierde el fin de vigencia** y se queda con un solo campo, y con ello **desaparece la distinción entre corregir y cambiar**: sin vigencia no hay «desde cuándo», solo un número que se reescribe hacia atrás y hacia delante a la vez. El documento se reordena alrededor de esa consecuencia — el aviso de cabecera dice que esta operación **borra el pasado de todo el sistema**, y §5 declara que `RN-CM-008` no es una regla que este requerimiento cumpla sino **una que necesita**, dirigida a un módulo que no existe. Los inmutables pasan de cuatro a uno, y §6.2 estrena una lectura del número de productos asociados que el alta no tiene: dice **a cuántas ventas futuras afecta la corrección**. §13 recoge que corregir una tasa compartida cambia lo que pagan veinte productos **sin aviso**, y que corregir hacia abajo lo ya pagado **el sistema no puede detectarlo**. | Responsable técnico |
| 0.3.0 | 02-09-2026 | **Entra el valor fijo** (`cm.md` v0.7.0), **antes del código**, y el requerimiento **cambia de nombre**: «corregir el porcentaje» dejó de describir lo que hace. Lo que se corrige es **el valor y su forma, como una sola cosa** — §6.1 rompe a propósito la costumbre del proyecto de que una corrección sea parcial, porque un importe suelto sobre una tasa de porcentaje se escribe igual tanto si se quiso cambiar la forma como si se erró el campo, y **corregir es donde deducirlo sale más caro**: al registrar solo se pierde un alta, aquí se cambia lo que ya paga. De ahí sale la pieza central de esta versión, `FA-004` y `CA-CM-091`: pasar una tasa de `10 %` a `10` de importe fijo **es un cambio de los grandes y los dos números comparan iguales**, de modo que una comparación que mire solo la cifra **devuelve éxito sin cambiar, sin auditar y sin mover la marca de modificación**. Es el contrario exacto de `FA-002`, que con esos mismos números pide lo opuesto, y por eso se prueban las dos. §5 declara que esta versión **agrava `RN-CM-008`**: lo que la liquidación tiene que copiar pasa de un número a **tres cosas**, y la tercera —la moneda— no está en ninguna tabla de `CM` ni la devuelve la resolución. §14 registra la decisión del responsable del proyecto de que **la forma se pueda corregir**, con la tabla de los cuatro pasos que la prohibición obligaría a dar y la ventana en la que los productos no comisionarían. | Responsable del proyecto |
