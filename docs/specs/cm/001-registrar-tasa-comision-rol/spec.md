# SPEC — `RF-CM-001` Registrar una tasa de comisión por rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-001` |
| Módulo | `CM` — Comisiones |
| Versión | 0.3.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

    Debe poder leerlo alguien del negocio y entenderlo completo. Es la primera compuerta del Art. I.6: hasta que no esté aprobada, no se escribe `plan.md`.

!!! warning "Esta especificación tiene dos capas, y se leen distinto"

    **La v0.2.0 se escribió DESPUÉS de construirse.** Describía el modelo de `cm.md` v0.4.0 con el código ya hecho, que es el orden inverso al que manda el Art. I.6: **no proponía, describía**.

    **La v0.3.0 se escribe ANTES.** El valor fijo de `cm.md` v0.7.0 **no existe en el código todavía**, y lo que este documento dice de él **decide lo que habrá que construir**. Todo lo que hable de forma, tipo o valor fijo está sin escribir; el resto sigue describiendo lo que la suite verifica hoy.

---

## 1. Objetivo

Declarar **cuánto gana un rol vendedor** por vender —**una proporción de la venta o una cantidad de dinero**—, para que exista en el sistema el dato sobre el que una liquidación futura podrá calcular.

## 2. Contexto

Hoy el sistema sabe **qué se vende** y **quién vende**, y **no sabe cuánto se le paga a quien vende**. Este es el primer requerimiento del módulo y el que crea el objeto del que dependerán después el cálculo y la liquidación.

**Lo que esta operación registra es un catálogo, no una configuración aplicada.** Es el cambio de fondo respecto a la versión anterior de este documento, y es lo que más fácil se lee mal: hasta el 01-09-2026 una tasa sin producto regía sobre **todo el catálogo**, y ahora **no rige sobre nada** hasta que alguien la asocie a un producto (`RN-CM-012`, y la operación es `RF-CM-007`).

**La ausencia cambió de significado**: pasó de «todos» a «ninguno». Una tasa recién registrada **parece configurada y no paga nada a nadie**, y eso no falla — se descubriría liquidando. Por eso esta operación **devuelve explícitamente sobre cuántos productos rige**, que al registrarla es siempre **cero**: sin ese dato, la respuesta sería idéntica para una tasa que paga y para una que no.

**Varias tasas del mismo rol son legítimas.** El catálogo puede ofrecer «`AGENTE` 10 %» y «`AGENTE` 15 %» para asociarlas a productos distintos. Lo que no puede repetirse es un rol sobre el **mismo** producto, y eso no lo decide esta operación (`RN-CM-013`, y lo comprueba `RF-CM-007`).

**Y desde el 02-09-2026 el catálogo puede ofrecer también «`AGENTE` 10.000 fijos».** Es el cambio de esta versión: una tasa declara **una forma y solo una** (`RN-CM-016`), o proporción o cantidad, y las dos son alta legítima de la misma operación. No se suman: no existe «5 % más 10.000».

!!! danger "El valor fijo se declara sin moneda, y quien registra la tasa tiene que saberlo"

    El importe **toma la moneda del producto que se venda** (`RN-CM-017`). La tasa no la declara, y no puede: al registrarla **no hay ningún producto todavía** —`RN-CM-012`—, de modo que no hay de dónde tomarla ni con qué compararla.

    La consecuencia es que **la misma tasa paga cosas distintas** según a qué producto se la asocie después. «10.000 fijos» sobre un producto en pesos y sobre otro en dólares no es un fallo: es exactamente lo que esa fila declara.

    Es una decisión aceptada en `cm.md` §1.1.1, no un defecto pendiente de arreglar. Lo que esta operación puede hacer al respecto es **no ocultarla**, y por eso §6.2 devuelve la forma junto al valor.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Declara la tasa: a qué rol corresponde, **en qué forma paga** y cuánto |

## 4. Alcance

### 4.1 Incluye

- Registrar una tasa de comisión **para un rol de tipo vendedor**.
- Declarar **en cuál de las dos formas** se paga —proporción o cantidad— y **su valor**, que en las dos puede ser **cero**.
- Verificar que **se declara una forma y solo una**, y que el valor es el que esa forma admite.
- Verificar que el rol existe y que es de tipo vendedor.
- Informar de que la tasa **todavía no rige sobre ningún producto**.
- Dejar constancia del alta en la auditoría de cambios.

### 4.2 No incluye

- **Poner la tasa en vigor.** Eso es asociarla a un producto, y es `RF-CM-007`. Sin esa operación, lo que aquí se registra no paga nada a nadie.
- **Acotar la tasa a un producto o a una persona.** Ya no son campos de la tasa: el producto vive en la asociación (`RF-CM-007`) y la excepción por persona es otra cosa distinta (`RF-CM-006`).
- **Declarar desde cuándo rige.** Las tasas de rol **no tienen vigencia**; la única que la tiene es la personalizada. Ver §13.
- **Declarar en qué moneda se paga un valor fijo.** No es un campo de la tasa: se toma del producto que se venda (`RN-CM-017`). Ver §2.
- **Comprobar que un valor fijo cabe en el precio de algo.** Nada lo acota por arriba (`RN-CM-018`), y aquí no podría comprobarse: la tasa **no conoce ningún producto todavía**. Ver §13.
- **Corregir una tasa ya registrada**, que es `RF-CM-003`, ni retirarla, que es `RF-CM-004`.
- **Calcular ni liquidar comisiones.** No existe ninguna venta sobre la que calcular (`requirements/cm.md` §1.4).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-001` | Solo comisionan los roles vendedores | `requirements/cm.md` §5.1 |
| `RN-CM-007` | El porcentaje va de cero a cien | `requirements/cm.md` §5.1 |
| `RN-CM-012` | Una tasa de rol no rige hasta que se asocia | `requirements/cm.md` §5.1 |
| `RN-CM-016` | **Una tasa declara una forma y solo una** | `requirements/cm.md` §5.1 |
| `RN-CM-017` | El valor fijo **no lleva moneda** | `requirements/cm.md` §5.1 |
| `RN-CM-018` | El valor fijo **no está acotado por arriba** | `requirements/cm.md` §5.1 |

**Tres reglas donde la versión anterior citaba siete**, y ninguna se relajó: **se mudaron**. `RN-CM-002` y `RN-CM-010` —el producto— viajaron a `RF-CM-007`; `RN-CM-003` desapareció con el rol de las personalizadas; `RN-CM-006` y `RN-CM-009` —el solapamiento y la vigencia— viajaron a `RF-CM-006`, que es lo único que conserva fechas.

**Las tres que entran en v0.3.0 no son iguales entre sí**, y conviene no leerlas de corrido:

- **`RN-CM-016` esta operación la hace cumplir.** Es la única de las tres que se comprueba aquí, y rechaza el alta.
- **`RN-CM-017` esta operación la sufre.** No hay nada que comprobar: se cita porque explica por qué no se pide la moneda, que es la pregunta que hará quien lea la entrada.
- **`RN-CM-018` esta operación la deja pasar a sabiendas.** Se cita porque un lector podría suponer que si `RN-CM-007` acota el porcentaje, algo acotará el importe. **No lo acota nada**, y aquí no podría — ver §13.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Rol | Sí | A qué rol corresponde la comisión | Debe existir y ser de **tipo vendedor** (`RN-CM-001`) |
| Forma | Sí | Si se paga **una proporción de la venta** o **una cantidad de dinero** | Una de las dos, y **solo una** (`RN-CM-016`) |
| Porcentaje | **Solo si la forma es proporción** | Qué proporción de la venta gana | De **cero a cien** (`RN-CM-007`) |
| Valor fijo | **Solo si la forma es cantidad** | Cuánto dinero gana por venta | **Cero o más, sin tope** (`RN-CM-018`). **Sin moneda** (`RN-CM-017`) |

**Cuatro campos, de los que siempre llegan tres.** El alta anterior tenía dos y ninguno opcional; ahora hay una elección que hacer, y la elección se **declara** en lugar de deducirse.

!!! warning "La forma se pide aunque parezca deducible de qué valor se envíe"

    Un lector razonable dirá que sobra: si llega un porcentaje, la forma es proporción. Y funcionaría **mientras llegue exactamente uno**.

    El problema es el resto de los casos. Si llegan **los dos**, o **ninguno**, el sistema tiene que rechazar la petición diciendo qué está mal — y sin la forma **no puede saberlo**: no distingue a quien quiso declarar un porcentaje y se equivocó de campo, de quien quiso declarar un importe y lo dejó vacío. El mensaje sería el mismo para dos errores distintos.

    Con la forma declarada, `RN-CM-016` se comprueba contra **lo que el negocio dijo que quería**, y el rechazo puede nombrarlo.

**El cero es una declaración en las dos formas, y significa lo mismo: «esto no comisiona».** Y en las dos **no es lo mismo que no declarar la tasa** — quien resuelve distingue una respuesta de cero de la ausencia de respuesta (`RF-CM-005`).

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tasa | La tasa registrada, con su identificador |
| Forma y valor | **En qué forma paga y cuánto**, siempre las dos cosas juntas. El valor de la otra forma **se devuelve vacío**, no omitido |
| Rol resuelto | El código y el nombre del rol, y no solo su identificador |
| Productos asociados | **Cuántos productos hacen que esta tasa rija.** Al registrarla es **siempre cero**, y ese cero significa que no paga nada a nadie |

**La forma se devuelve aunque el actor acabe de enviarla**, y no es una cortesía. Un valor de comisión **no significa nada solo**: «10» es el diez por ciento o son diez unidades de dinero, y son cosas de órdenes de magnitud distintos. Devolver el valor sin la forma obligaría a quien lo consuma a adivinarla por el campo que venga lleno, que es exactamente lo que §6.1 acaba de decidir que no se hace.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de tasas de comisión.
- Existe al menos un rol de tipo vendedor.

**Postcondiciones**

- La tasa queda registrada en el catálogo **y no rige sobre ningún producto**.
- La auditoría de cambios contiene un evento de creación con el estado inicial completo de la tasa.
- **Nadie cobra nada distinto por esta operación.** Es la postcondición que conviene leer dos veces: registrar una tasa no cambia ninguna comisión hasta que se la asocia.

## 8. Flujo principal

1. El actor envía el rol, la forma y el valor que esa forma pide.
2. El sistema comprueba que **se declaró una forma y solo una**, y que el valor presente es el que le corresponde.
3. El sistema comprueba el valor contra lo que su forma admite: el porcentaje, entre cero y cien; el importe, **cero o más y nada más**.
4. El sistema comprueba que el rol existe.
5. El sistema comprueba que el rol es de tipo vendedor.
6. El sistema registra la tasa y emite el evento de auditoría de creación.
7. El sistema devuelve la tasa registrada, **con su forma y su valor**, con el rol resuelto y con **cero productos asociados**.

**Los pasos 2 y 3 están separados a propósito**, aunque los dos hablen del valor. El 2 pregunta **qué se quiso declarar** y el 3 **si es admisible**; invertirlos obligaría a validar un rango sin saber cuál de los dos rangos aplica.

## 9. Flujos alternativos

### FA-001 — Segunda tasa del mismo rol

**Cuándo ocurre:** ya existe una tasa para ese rol.

1. **No hay ningún conflicto que comprobar**, y esa es la diferencia con el modelo anterior: sin vigencia, dos tasas del mismo rol no pueden solaparse en el tiempo porque no hay tiempo que solapar.
2. Las dos quedan en el catálogo, disponibles para asociarse a productos distintos.
3. El resto del flujo es idéntico.

### FA-002 — Valor cero, en cualquiera de las dos formas

**Cuándo ocurre:** se declara una tasa del cero por ciento, o de cero de importe fijo.

1. Se registra con normalidad. Es la forma de declarar «este rol **no cobra** por el producto al que se asocie esta tasa».
2. **No es lo mismo que no tener tasa**, y `RF-CM-005` las distingue: el cero es una decisión, la ausencia es que nadie la tomó.
3. **Las dos formas del cero son la misma decisión con distinta letra**, y el sistema no intenta unificarlas. Registrar «cero fijo» en lugar de «cero por ciento» es redundante, no incorrecto, y prohibirlo obligaría a tratar el cero como un caso aparte en una operación que no lo trata como tal en ningún otro sitio.

### FA-003 — Primera tasa del sistema

**Cuándo ocurre:** no hay ninguna tasa registrada.

1. La tasa queda registrada con normalidad. **No es un caso especial**, y se enumera para que quede escrito que no lo es.

### FA-004 — Valor fijo mayor que cualquier precio del catálogo

**Cuándo ocurre:** se declara una tasa de un importe fijo superior al precio de todos los productos que existen.

1. **Se registra con normalidad, y esto es lo que hay que subrayar**: no hay advertencia, no hay rechazo, no hay nada.
2. **El sistema no puede saber que es un disparate.** Al registrar la tasa no hay ningún producto asociado (`RN-CM-012`), de modo que no existe precio contra el que comparar; y aunque lo hubiera, mañana podría asociarse a otro más barato.
3. `RN-CM-018` declara que **nada acota el importe por arriba**, y esta operación es el sitio donde esa ausencia se nota primero. La defensa está en la liquidación, **que no existe todavía**, y su forma será **rechazar el pago, no recortarlo** (`cm.md` §5.3).

**Se enumera como flujo alternativo y no como excepción a propósito.** Una excepción describe algo que el sistema impide; esto es algo que el sistema **permite sabiendo lo que permite**, y quedarse callado sobre ello dejaría la impresión de que alguien lo vigila.

## 10. Excepciones

### EX-001 — El rol no es de tipo vendedor

**Condición:** el rol existe, y es funcionario o consumidor.
**Respuesta del sistema:** rechaza el alta diciendo que solo los roles de tipo vendedor pueden llevar comisión, y no registra nada.

### EX-002 — El rol no existe

**Condición:** el rol indicado no existe.
**Respuesta del sistema:** rechaza el alta diciendo que el rol indicado no existe. **No es un «no encontrado»**: lo que no existe es un dato que el actor envió, no el recurso que estaba pidiendo. Se distingue de `EX-001` a propósito — quien escribió bien el identificador no debe buscar el error donde no está.

!!! note "Las cinco excepciones que esta especificación perdió"

    La v0.1.0 tipificaba siete. Las cinco que faltan —producto inexistente, producto retirado, persona inexistente, persona sin el rol y solapamiento— **no se eliminaron: se mudaron** con los campos que las causaban, a `RF-CM-007` y a `RF-CM-006`. Se dice aquí para que la reducción no se lea como una relajación de las comprobaciones.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Rol obligatorio | El rol de la tasa es obligatorio. |
| `VAL-002` | **Forma obligatoria** | La forma de la comisión es obligatoria: porcentaje o valor fijo. |
| `VAL-003` | Rango del porcentaje | El porcentaje debe estar entre cero y cien. |
| `VAL-011` | **El valor corresponde a la forma** | Una comisión por porcentaje lleva porcentaje y no valor fijo; una comisión por valor fijo, al revés. |
| `VAL-012` | **Valor fijo no negativo** | El valor fijo no puede ser negativo. |

**`VAL-002` cambió de significado sin cambiar de identificador**, y queda dicho para que nadie lo lea como el mismo aviso de antes. Decía «el porcentaje es obligatorio»; **ahora el porcentaje no siempre lo es**, y lo que se ha vuelto obligatorio es la elección. Conservar el identificador es correcto —ocupa el mismo lugar en la validación de entrada— y reutilizar el mensaje habría sido un error.

**`VAL-011` es un solo mensaje para lo que podrían parecer tres errores** —las dos formas llenas, ninguna llena, y la equivocada llena—. Son el mismo: **lo enviado no concuerda con lo declarado**, y separarlos obligaría a redactar tres frases que dicen lo mismo con distinta cara.

**No hay validación que acote el valor fijo por arriba, y su ausencia es la decisión.** `VAL-003` acota el porcentaje porque cien es un límite que el negocio conoce sin mirar nada; para el importe **no existe ese número** (`RN-CM-018`), y ponerlo aquí sería inventarlo.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-001` | El sistema registra la tasa de un rol vendedor y devuelve el rol resuelto |
| `CA-CM-002` | La tasa recién registrada declara **cero productos asociados**, y ese cero significa que no rige sobre ninguno |
| `CA-CM-003` | La respuesta **no lleva** producto, persona, vigencia ni grado: son campos que el modelo ya no tiene |
| `CA-CM-004` | El sistema admite **varias tasas del mismo rol**, para asociarlas a productos distintos |
| `CA-CM-005` | El sistema registra una tasa con porcentaje **cero**, y la distingue de no tener tasa |
| `CA-CM-006` | El sistema rechaza una tasa sobre un rol que no es de tipo vendedor |
| `CA-CM-007` | El sistema rechaza una tasa sobre un rol inexistente, y **lo distingue** de un rol que no es vendedor |
| `CA-CM-008` | El sistema rechaza un porcentaje negativo o mayor que cien, y una petición sin rol |
| `CA-CM-079` | El sistema registra una tasa **en valor fijo**, y la respuesta declara **la forma junto al valor** |
| `CA-CM-080` | El sistema rechaza una tasa que declara **las dos formas a la vez** |
| `CA-CM-081` | El sistema rechaza una tasa cuyo valor **no corresponde a la forma declarada**, y una que no declara forma |
| `CA-CM-082` | El sistema rechaza un valor fijo **negativo** |
| `CA-CM-083` | El sistema registra un valor fijo **cero**, y lo distingue de no tener tasa |
| `CA-CM-084` | El sistema registra un valor fijo **mayor que el precio de cualquier producto existente**, y **no falla ni advierte** |

**`CA-CM-081` cubre dos peticiones distintas en un criterio** —el valor que no corresponde a la forma, y la forma ausente— porque las dos verifican lo mismo: que **la forma y el valor se comprueban juntos** y no por separado.

!!! danger "`CA-CM-084` afirma que el sistema NO hace algo, y es el criterio más importante de los seis"

    Los otros cinco comprueban rechazos. Este comprueba que **una tasa disparatada entra sin resistencia**, que es lo que `RN-CM-018` declara y lo que la liquidación tendrá que arreglar algún día.

    Se escribe como criterio de aceptación —y no solo como caso límite en §13— porque **el día que alguien añada un tope aquí, esta prueba fallará**, y la discusión pasará por `cm.md` en lugar de resolverse en silencio con un número inventado.

## 13. Casos límite

- **Una tasa registrada y nunca asociada:** queda en el catálogo con su porcentaje y **no paga nada a nadie**. Es el caso límite más importante de este requerimiento y **no es un error del sistema**: es la consecuencia de `RN-CM-012`, y la respuesta lo dice devolviendo cero productos asociados.
- **Dos tasas idénticas del mismo rol y el mismo porcentaje:** se admiten. No hay ninguna regla que lo impida y prohibirlo obligaría a decidir si dos tasas iguales son un error o una preparación para asociarlas a productos distintos — decisión que el sistema no puede tomar por quien administra.
- **Cambiar el porcentaje a partir de una fecha:** **no se puede**, y hay que saberlo. Sin vigencia en las tasas de rol, la única operación disponible es corregir (`RF-CM-003`), que **reescribe lo que la tasa dijo siempre**. Registrar una tasa nueva y reasociar el producto es la alternativa, y tampoco conserva desde cuándo rigió cada una.
- **El rol se desactiva o se elimina después de registrarse la tasa:** la tasa **permanece**. `RN-CM-001` se comprueba al registrar, no continuamente: retirar un rol no es motivo para borrar el historial de lo que se pagó por él.
- **Porcentaje cero frente a ausencia de tasa:** son cosas distintas y el sistema no las confunde. Cero es una respuesta —no comisiona—; la ausencia es que nadie lo declaró, y quien resuelve decide qué hacer con cada una (`RF-CM-005`).
- **Un valor fijo que supera el precio del producto al que se lo asocie:** **se registra sin resistencia**, y no hay ningún momento posterior en el que el sistema lo advierta. `RF-CM-007` tampoco lo comprobará: asociar una tasa a un producto es donde por primera vez existen las dos cifras a la vez, y aun así **la comparación no serviría** —el precio del producto puede corregirse después (`RF-PM-004`) y dejar desfasada una comprobación hecha el día de la asociación—. Es `RN-CM-018`, y su dueño será la liquidación.
- **Dos tasas del mismo rol, una en porcentaje y otra en valor fijo:** se admiten, y **es el caso para el que existe** que varias tasas del mismo rol sean legítimas. Un producto barato puede pagar el 10 % y uno caro un importe fijo, y son dos filas del catálogo asociadas a productos distintos.
- **Cambiar la forma de una tasa ya registrada:** **no lo decide esta operación** — es corregir, y es `RF-CM-003`. Se nombra aquí porque la pregunta nace al leer esta especificación, y porque la respuesta que `RF-CM-003` da no es obvia.
- **Un valor fijo con más decimales de los que su moneda admite:** se registra. Es la asimetría con `RN-PM-007`, que sí valida los decimales de un precio contra su moneda: **aquí no hay moneda con la que validar** (`RN-CM-017`), y no la habrá hasta la liquidación. Está escrito en `modelo-datos.md` §4 y no se repite.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Queda declarado un condicionante que no es una pregunta de este requerimiento:** quién puede ver las tasas de quién depende de **D-22**, abierta. Esta especificación se escribe con **alcance global explícito** —quien tiene el permiso, opera sobre todas—, y lo que puede tener que cambiar el día que D-22 se cierre es `RF-CM-002`, no esta.

**Y quedan declaradas dos pérdidas aceptadas a conciencia**, que no son preguntas abiertas sino decisiones tomadas:

1. **Sin vigencia, este catálogo no conserva historial.** La defensa del pasado es que la liquidación copie lo que aplicó (`RN-CM-008`), y esa liquidación **no existe todavía**.
2. **Con el valor fijo, esa copia deja de ser un número.** Lo que la liquidación tendrá que guardar son **tres cosas** —la forma, el valor y la moneda—, y **la tercera no está en ninguna tabla de este módulo**: se toma del producto que se venda (`RN-CM-017`), que para entonces puede haberse retirado. Está escrito en `modelo-datos.md` §4.1 y se repite aquí porque nace de esta operación: **es aquí donde se registra un importe sin decir en qué moneda está.**

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial, sin preguntas abiertas. | Responsable técnico |
| 0.2.0 | 02-09-2026 | **Reescrita sobre el modelo de `cm.md` v0.4.0**, y **después de construirse** el código — orden inverso al del Art. I.6, declarado en cabecera. El alta pierde cuatro de sus seis campos: el producto viaja a `RF-CM-007`, la persona a `RF-CM-006` y la vigencia desaparece del catálogo. Con ellos se van **cinco de las siete excepciones y cuatro de las siete reglas**, y §10 avisa de que **no se relajaron, se mudaron**. Lo que entra en su lugar es el cambio de fondo: **lo que esta operación registra ya no rige** (`RN-CM-012`), de modo que §7 declara como postcondición que **nadie cobra nada distinto** por ejecutarla, y §6.2 añade el número de productos asociados —siempre cero al registrar— porque sin él la respuesta sería idéntica para una tasa que paga y para una que no. §13 recoge la consecuencia que más incomoda: **cambiar un porcentaje a partir de una fecha ya no se puede**. | Responsable técnico |
| 0.3.0 | 02-09-2026 | **Entra el valor fijo** (`cm.md` v0.7.0), y esta vez **antes del código**: lo que aquí se decide está sin construir. El alta pasa de dos campos a cuatro, de los que llegan tres — la novedad no es el importe sino **la forma**, que se **declara** en lugar de deducirse de qué campo venga lleno. §6.1 argumenta por qué: sin ella, una petición con las dos formas llenas y otra con ninguna **producirían el mismo mensaje de error para dos equivocaciones distintas**. Entran `RN-CM-016`, `RN-CM-017` y `RN-CM-018`, y §5 avisa de que **las tres se relacionan con esta operación de tres maneras opuestas**: la primera la hace cumplir, la segunda la sufre, la tercera **la deja pasar a sabiendas**. De ahí salen las dos piezas incómodas de esta versión: `FA-004` y `CA-CM-084`, que **afirman que el sistema no impide** registrar un importe mayor que el precio de cualquier producto —no puede: al registrar no hay producto, y `RF-CM-007` tampoco podría, porque el precio se corrige después—; y el aviso de §2 de que **la misma tasa paga cosas distintas** según a qué producto se la asocie, porque el importe **no lleva moneda**. §14 recoge lo que eso le hace a `RN-CM-008`: la copia que defiende el pasado deja de ser un número y pasa a ser **tres cosas, de las que una no está en este módulo**. `VAL-002` **conserva su identificador y cambia de significado** —era «el porcentaje es obligatorio», ahora lo obligatorio es la elección—, y queda dicho para que nadie reutilice el mensaje viejo. | Responsable técnico |
