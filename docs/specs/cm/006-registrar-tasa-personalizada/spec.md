# SPEC — `RF-CM-006` Registrar la tasa personalizada de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-006` |
| Módulo | `CM` — Comisiones |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`.

!!! danger "Este requerimiento se construyó ANTES de tener especificación"

    Es una excepción al Art. I.1, registrada en `requirements/cm.md` §4 y en la matriz. El motivo: el módulo se rehizo entero el 02-09-2026 y **sin esta pieza no había forma de probarlo de punta a punta**.

    Queda dicho porque cambia cómo leerla: **la v0.1.0 no proponía, describía**.

    **La v0.2.0 sí propone.** El valor fijo de `cm.md` v0.7.0 no existe en el código, y lo que este documento diga de él decide lo que habrá que construir.

---

## 1. Objetivo

Declarar que **una persona concreta gana algo distinto** de lo que le correspondería por su rol —un porcentaje o una cantidad fija—, y **desde cuándo**.

## 2. Contexto

El catálogo por rol dice lo que gana un `AGENTE`. Pero se negocia con personas, no con roles: alguien entra con condiciones mejores, alguien las gana por resultados. **Eso es una excepción, y no un grado más del catálogo.**

**Se separó del alta de rol el 01-09-2026**, y no por gusto: **no son la misma operación**. Una escribe en un catálogo sin fechas y no rige hasta que se la asocia; la otra registra una excepción **con vigencia**, que **rige desde el primer día** y **sin asociarse a nada**. Hasta entonces eran un solo endpoint con campos opcionales, y eso obligaba a validaciones que dependían de qué campo había llegado.

**Y esta tasa no lleva rol**, por decisión del responsable del proyecto: es de la persona y punto. Lo que eso cuesta está en §13, y es la parte de este requerimiento que más fácil se subestima.

**Es la única pieza del módulo con vigencia**, y por tanto **el único historial que le queda**. Sus filas cerradas dicen qué ganó esa persona y hasta cuándo. El catálogo por rol perdió esa capacidad.

**Y desde el 02-09-2026 puede declararse en valor fijo** (`RN-CM-016`), igual que una tasa de rol. Es la misma elección y el mismo objeto —una forma y solo una, nunca las dos—, pero **aquí no significa lo mismo**, y la diferencia no es de grado.

!!! danger "Un importe fijo personalizado rige sobre TODO el catálogo, en todas sus monedas a la vez"

    Una tasa de rol en valor fijo se interpreta en la moneda de **los productos a los que se la asocie**, que son los que alguien eligió (`RF-CM-007`). El desconcierto está acotado a esa lista.

    **Esta no se asocia a nada** (`RN-CM-014`): rige sobre todo lo que su titular venda. De modo que «10.000 fijos» significa **diez mil de cada moneda que haya en el catálogo**, y su titular gana más o menos según qué venda, sin que nadie lo haya decidido así.

    No hay ningún momento en el que el sistema lo advierta. `RN-CM-017` lo declara y `cm.md` §1.1.1 lo acepta a conciencia, **habiendo descartado la alternativa**: darle moneda propia y exigir coincidencia al asociar **no funciona aquí**, porque esta tasa no tiene producto con el que coincidir. Es exactamente el caso que hizo descartar esa salida para todo el módulo.

    Lo que esta especificación hace al respecto es **decirlo**: §6.2 devuelve la forma junto al valor, y `RF-CM-005` la devuelve otra vez al resolver.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Declara la excepción de una persona: **en qué forma gana**, cuánto, y desde cuándo |

## 4. Alcance

### 4.1 Incluye

- Registrar la tasa personalizada de una persona, con **su forma, su valor** y su **vigencia**.
- Verificar que **se declara una forma y solo una**, y que el valor es el que esa forma admite.
- Verificar que la persona existe.
- Garantizar que **ningún día queda cubierto por dos tasas vivas de la misma persona**.
- **Corregir** el valor —**y su forma**— y el fin de vigencia de una tasa ya registrada.
- **Retirar** una tasa con motivo obligatorio.
- Dejar constancia de todo ello en la auditoría.

**La corrección y el retiro están aquí y no en `RF-CM-003` y `RF-CM-004`**, aunque sean la misma operación conceptual. El motivo es que **se comportan distinto**: aquí corregir **no borra el pasado** —hay vigencia, y cambiar lo que se gana a partir de una fecha es cerrar la vigente y abrir otra—, y el retiro **sí tiene una vigencia que podría cerrarse «de paso»** y no debe. Describirlas junto a las de rol habría obligado a un documento lleno de «salvo en el caso de».

### 4.2 No incluye

- **Acotarla a un producto.** Una tasa personalizada **no se acota** (`RN-CM-014`): quien la tiene gana lo mismo venda lo que venda.
- **Declarar en qué moneda se paga un valor fijo.** No es un campo de esta tasa, y aquí **no podría serlo**: no hay ningún producto del que tomarla ni con el que compararla. Ver §2.
- **Exigir que la persona sea vendedora.** Se consideró y **se descartó al quitarle el rol**. Ver §13.
- **Resolver cuál se aplica.** Es `RF-CM-005`.
- **Calcular ni liquidar.**

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-004` | La personalizada gana siempre | `requirements/cm.md` §5.1 |
| `RN-CM-005` | La tasa no desaparece | `requirements/cm.md` §5.1 |
| `RN-CM-006` | Una sola tasa personalizada vigente por persona | `requirements/cm.md` §5.1 |
| `RN-CM-007` | El porcentaje va de cero a cien | `requirements/cm.md` §5.1 |
| `RN-CM-009` | Toda tasa personalizada declara desde cuándo rige | `requirements/cm.md` §5.1 |
| `RN-CM-014` | Solo las tasas de rol se asocian a productos | `requirements/cm.md` §5.1 |
| `RN-CM-016` | **Una tasa declara una forma y solo una** | `requirements/cm.md` §5.1 |
| `RN-CM-017` | El valor fijo **no lleva moneda** | `requirements/cm.md` §5.1 |
| `RN-CM-018` | El valor fijo **no está acotado por arriba** | `requirements/cm.md` §5.1 |

**`RN-CM-006` es la que sostiene a `RN-CM-004`**: con dos tasas cubriendo el mismo día, la resolución dejaría de ser determinista.

**`RN-CM-014` y `RN-CM-017` se agravan mutuamente, y conviene leerlas juntas.** Por separado son dos decisiones razonables —esta tasa no se acota; el importe toma la moneda del producto—. **Juntas dicen que un importe fijo personalizado se interpreta en tantas monedas como haya en el catálogo**, y ninguna de las dos lo menciona por su cuenta. Está en §2.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Persona | Sí | De quién es la tasa | Debe existir. **No se le exige portar rol vendedor** |
| Forma | Sí | Si gana **una proporción de la venta** o **una cantidad de dinero** | Una de las dos, y **solo una** (`RN-CM-016`) |
| Porcentaje | **Solo si la forma es proporción** | Qué proporción gana | De **cero a cien** (`RN-CM-007`) |
| Valor fijo | **Solo si la forma es cantidad** | Cuánto dinero gana por venta | **Cero o más, sin tope** (`RN-CM-018`). **Sin moneda** (`RN-CM-017`) |
| Inicio de vigencia | Sí | Desde qué día rige | Una fecha. Puede ser pasada o futura |
| Fin de vigencia | No | Hasta qué día rige, **inclusive** | No puede ser anterior al inicio. **Sin él, rige indefinidamente** (`RN-CM-009`) |

**No hay rol ni producto**, y su ausencia no significa nada: **no son campos de esta tasa.** Es distinto de la ausencia en el modelo anterior, donde sí significaba «para todos».

**La forma es exactamente la misma elección que en una tasa de rol**, con los mismos motivos para declararla en vez de deducirla del campo que venga lleno. Están en `RF-CM-001` §6.1 y no se repiten.

**La ausencia del valor de la otra forma sí significa algo, y no es lo mismo que la ausencia del fin de vigencia.** Un fin vacío es una declaración —«indefinidamente»—; un porcentaje vacío en una tasa de tipo `FIJO` **no declara nada**: es la consecuencia mecánica de haber elegido la otra forma. Se dice porque este es el único documento del módulo donde conviven los dos tipos de ausencia.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tasa | Identificador y vigencia |
| Forma y valor | **En qué forma gana y cuánto**, siempre juntos. El valor de la otra forma viaja **vacío, no omitido** |
| Persona resuelta | Nombre de usuario y nombre |

**El fin de vigencia viaja vacío y presente** cuando la tasa rige indefinidamente: un campo que desaparece del resultado es indistinguible de uno que el cliente no conoce, y aquí significa algo.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso correspondiente.
- La persona existe.

**Postcondiciones**

- La tasa queda registrada y **rige desde el día que declara**, que puede no ser hoy.
- **Rige sin necesidad de nada más**, al revés que una tasa de rol.
- **Ningún día queda cubierto por dos tasas vivas de esa persona** (`RN-CM-006`).
- La auditoría contiene el evento correspondiente.

## 8. Flujo principal

**Registrar**

1. El actor envía la persona, **la forma y su valor** y el inicio de vigencia, y opcionalmente el fin.
2. El sistema comprueba que **se declaró una forma y solo una**, que el valor es admisible para ella —el porcentaje, entre cero y cien; el importe, cero o más—, y que el fin, si se envió, no es anterior al inicio.
3. El sistema comprueba que la persona existe.
4. El sistema comprueba que **ningún día del periodo declarado está ya cubierto** por otra tasa viva de esa persona.
5. El sistema registra la tasa y emite el evento de auditoría de creación.
6. El sistema devuelve la tasa, con la persona resuelta.

**Corregir**

1. El actor envía **el valor corregido con su forma**, el fin de vigencia, o los dos.
2. El sistema rechaza la petición si trae la persona o el inicio de vigencia, o si no informa nada.

**Corregir el valor es corregir la pareja entera**, y no uno de los dos campos: quien corrige envía la forma **aunque no la cambie**. Enviar un importe suelto sobre una tasa que era de porcentaje dejaría al sistema decidiendo si es un cambio de forma o una equivocación, y ya está decidido que eso no se deduce (§6.1).
3. El sistema comprueba que la tasa existe y está viva, y que el resultado **no se solapa** con otra.
4. El sistema aplica lo que cambió de verdad y emite el evento con el antes y el después.

**Retirar**

1. El actor envía el motivo.
2. El sistema lo verifica, comprueba que la tasa existe y no está ya retirada.
3. El sistema toma la instantánea **con la vigencia intacta**, marca el retiro y emite el evento de eliminación.

## 9. Flujos alternativos

### FA-001 — Varias tasas consecutivas

**Cuándo ocurre:** una termina el 31 y la siguiente empieza el 1.

1. Las dos conviven. **Son el historial**, y es el único que el módulo conserva.
2. `RN-CM-006` prohíbe el solapamiento, no la sucesión.

### FA-002 — Cambiar lo que gana alguien a partir de una fecha

**Cuándo ocurre:** se quiere subir el porcentaje desde el mes que viene.

1. Son **dos operaciones**: cerrar la vigente poniéndole fin, y registrar otra desde el día siguiente.
2. **Y aquí sí se puede**, al revés que con una tasa de rol (`RF-CM-003` §13). Es lo que la vigencia compra.

### FA-003 — Quitar el fin de vigencia

**Cuándo ocurre:** se corrige enviando el fin explícitamente vacío.

1. Es **una orden que se cumple**: la tasa vuelve a regir indefinidamente.
2. Se trata **al revés que el porcentaje**, que vaciarlo se rechaza. Son dos campos y dos comportamientos opuestos ante el mismo gesto.

### FA-004 — La persona no porta rol vendedor

**Cuándo ocurre:** se declara la tasa de alguien que no vende, o que dejó de vender.

1. **Se admite.** No hay ninguna comprobación que lo impida.
2. **Y esa tasa rige**: `RF-CM-005` `FA-003` la resuelve y la persona cobra. Ver §13.

### FA-005 — Retirar libera los días

**Cuándo ocurre:** se retira una tasa y se quiere declarar otra que cubra su periodo.

1. Se admite. Los días que ocupaba **quedan libres**.
2. Es lo que distingue una tasa **retirada** de una **vencida**: la vencida sigue explicando lo que se pagó, la retirada no debió existir.

### FA-006 — Cambiar de forma a partir de una fecha

**Cuándo ocurre:** alguien pasa de cobrar un porcentaje a cobrar un importe fijo, o al revés, desde el mes que viene.

1. Son **dos operaciones**, las mismas de `FA-002`: cerrar la vigente poniéndole fin, y registrar otra —**en la otra forma**— desde el día siguiente.
2. **Y esta es la única pieza del módulo donde eso deja rastro.** Las dos filas conviven: la cerrada dice qué se ganó en porcentaje y hasta cuándo, la nueva qué se gana en importe y desde cuándo.
3. **Corregir la forma de la tasa viva también funciona, y no es lo mismo.** Reescribe lo que esa tasa dijo **durante toda su vigencia**, incluidos los días ya pasados. Es la operación correcta para arreglar una equivocación, y la equivocada para acordar un cambio.

**Es la diferencia que la vigencia compra**, y aquí es más visible que en `FA-002`: cambiar de 10 % a 12 % y cambiar de 10 % a 10.000 fijos son el mismo gesto, y el segundo hace mucho más ruido si se aplica hacia atrás sin querer.

## 10. Excepciones

### EX-001 — La persona no existe

**Condición:** el identificador no corresponde a ninguna persona.
**Respuesta del sistema:** rechaza el alta diciendo que la persona indicada no existe. **No es un «no encontrado»**: lo que no existe es un dato que el actor envió.

### EX-002 — La tasa se solapa con otra

**Condición:** ya existe una tasa viva de esa persona que cubre alguno de los días declarados.
**Respuesta del sistema:** rechaza la operación diciendo que esa persona ya tiene una tasa viva en parte de ese periodo, y no registra nada.

### EX-003 — La tasa no existe o está retirada

**Condición:** al corregir, el identificador no corresponde a ninguna tasa viva.
**Respuesta del sistema:** rechaza la corrección diciendo que no existe.

### EX-004 — Ya estaba retirada

**Condición:** al retirar, la tasa existe y ya fue retirada.
**Respuesta del sistema:** rechaza el retiro diciendo que ya estaba retirada. **No es idempotente a propósito**: dos motivos distintos sobre un mismo hecho harían que el registro mienta.

### EX-005 — Se intenta cambiar la persona o el inicio de vigencia

**Condición:** la corrección trae alguno de los dos.
**Respuesta del sistema:** los rechaza diciendo que no se pueden corregir. **Se rechazan y no se ignoran.**

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Persona obligatoria | La persona de la tasa es obligatoria. |
| `VAL-002` | **Forma obligatoria**, y no se vacía | La forma de la comisión es obligatoria: porcentaje o valor fijo. / La forma no puede vaciarse. |
| `VAL-003` | Rango del porcentaje | El porcentaje debe estar entre cero y cien. |
| `VAL-004` | Inicio de vigencia obligatorio | El inicio de vigencia es obligatorio. |
| `VAL-005` | Orden de la vigencia | El fin de vigencia no puede ser anterior a su inicio. |
| `VAL-007` | Motivo obligatorio | El motivo del retiro es obligatorio. |
| `VAL-008` | Longitud del motivo | El motivo no puede exceder 500 caracteres. |
| `VAL-009` | Campos no corregibles | La persona y el inicio de vigencia de una tasa personalizada no se pueden corregir. |
| `VAL-010` | Petición vacía | Debe enviarse al menos un campo corregible. |
| `VAL-011` | **El valor corresponde a la forma** | Una comisión por porcentaje lleva porcentaje y no valor fijo; una comisión por valor fijo, al revés. |
| `VAL-012` | **Valor fijo no negativo** | El valor fijo no puede ser negativo. |

**`VAL-011` y `VAL-012` son las mismas de `RF-CM-001`**, con el mismo texto, porque es la misma regla sobre la otra pieza. Reutilizar el identificador es lo que el módulo hace con `VAL-007` y `VAL-008`, y aquí importa más: **si las dos altas dieran mensajes distintos ante el mismo error, parecería que las dos formas se declaran de dos maneras**.

**`VAL-002` cambió de significado y conserva el identificador**, igual que en `RF-CM-001` §11. Aquí además conserva **las dos caras** que ya tenía —obligatorio al registrar, no vaciable al corregir— y las dos pasan a hablar de la forma, no del porcentaje.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-051` | Registra la tasa de una persona, con la persona resuelta y **sin rol ni producto** |
| `CA-CM-052` | El fin de vigencia ausente viaja **vacío y presente**, y significa «indefinidamente» |
| `CA-CM-053` | **Admite a quien no porta rol vendedor**, y esa tasa queda registrada |
| `CA-CM-054` | Rechaza dos tasas de la misma persona que **comparten algún día** |
| `CA-CM-055` | **El día de corte cuenta**: si una termina el 30, la siguiente no empieza el 30 |
| `CA-CM-056` | Admite **varias consecutivas**: son el historial |
| `CA-CM-057` | Dos **personas distintas** pueden solapar sin conflicto |
| `CA-CM-058` | Retirar **libera los días** que ocupaba |
| `CA-CM-059` | Retirar **no cierra la vigencia** |
| `CA-CM-060` | Corregir **vacía** el fin de vigencia, y la tasa vuelve a regir indefinidamente |
| `CA-CM-061` | Rechaza corregir la persona o el inicio de vigencia |
| `CA-CM-062` | Rechaza el fin anterior al inicio, la persona inexistente, y exige el permiso |
| `CA-CM-085` | Registra una tasa personalizada **en valor fijo**, y la respuesta declara la forma junto al valor |
| `CA-CM-086` | Rechaza las dos formas a la vez, ninguna, y el valor que no corresponde a la forma |
| `CA-CM-087` | Admite **dos tasas consecutivas de formas distintas** —porcentaje hasta el 31, importe desde el 1— y **las dos quedan** |
| `CA-CM-088` | Corregir **cambia la forma** de una tasa viva, y el evento registra el antes y el después **de las dos cosas** |
| `CA-CM-089` | El valor fijo de una personalizada **rige igual sobre productos de monedas distintas**, y nada lo advierte |

**`CA-CM-087` y `CA-CM-088` prueban las dos mitades de `FA-006`, y la pareja es el criterio**. Por separado, cada uno comprueba una operación corriente; juntos verifican que **el sistema ofrece las dos maneras de cambiar de forma y no las confunde** — una deja historial, la otra reescribe.

!!! danger "`CA-CM-089` afirma que el sistema NO advierte de algo, igual que `CA-CM-084`"

    Resuelve la misma tasa personalizada contra dos productos de monedas distintas y comprueba que **devuelve el mismo importe las dos veces**, sin señal de ninguna clase.

    Es lo que `RN-CM-017` y `RN-CM-014` declaran **juntas** (§5), y ninguna de las dos lo dice sola. Se escribe como criterio para que el día que alguien intente arreglarlo, la prueba falle y la discusión pase por `cm.md`.

## 13. Casos límite

!!! danger "La protección que se perdió al quitarle el rol"

    El modelo anterior exigía que la persona **portara el rol** de la tarifa, y con ello impedía que una excepción **sobreviviera a que su titular dejara de vender**.

    Al quitarle el rol el 01-09-2026, esa protección **desapareció**. Una tasa personalizada sigue viva —y **sigue pagando**— aunque su titular pase a un rol que no comisiona, o se quede sin ninguno.

    `cm.md` §5.3 lo describía como que «no falla — se queda callada hasta que alguien la mira». **Construir `RF-CM-005` demostró que no se queda callada**: la resolución la consulta **antes** que el rol, de modo que responde y la persona cobra. La forma de cerrarla es **retirarla** o **ponerle fin de vigencia**, y las dos son actos deliberados que alguien tiene que acordarse de hacer.

- **Inicio de vigencia en el pasado:** se admite. Declarar el día 5 una tasa que rige desde el día 1 es un caso real y prohibirlo obligaría a mentir en la fecha.
- **Inicio de vigencia en el futuro:** se admite, y es la mitad del valor de tener vigencia — permite programar el cambio con antelación.
- **Una tasa que rige un solo día:** se admite. El fin igual al inicio es un periodo válido.
- **Porcentaje cero personalizado:** se admite, y significa que esa persona **no cobra nada**, ganando sobre lo que su rol tuviera asociado. Es una decisión legítima y drástica.
- **Valor fijo cero personalizado:** lo mismo, y **es la forma más clara de decirlo**. «Cero por ciento de la venta» y «cero de importe» apagan igual la comisión de esa persona, y el sistema no prefiere ninguna.
- **Un importe fijo personalizado, sobre un catálogo con varias monedas:** es el caso límite propio de este requerimiento, y **no tiene salida dentro del módulo**. Está en §2 con su argumento entero.
- **Una tasa personalizada en importe fijo mayor que el precio de todo lo que su titular vende:** se registra, y **aquí es peor que en una tasa de rol**: aquella al menos se acota a los productos que alguien eligió asociarle. Esta rige sobre todos. `RN-CM-018`, sin dueño hasta que exista la liquidación.
- **Corregir la forma de una tasa cuya vigencia ya pasó entera:** se admite, y **reescribe un periodo cerrado**. Es el mismo riesgo que corregir su porcentaje, con más ruido: `FA-006` explica cuándo esa es la operación correcta y cuándo no.
- **Dos altas simultáneas del mismo periodo:** una queda y la otra recibe el conflicto. Es la **única regla del módulo que dos peticiones simultáneas pueden burlar**, y por eso no se comprueba consultando antes de escribir.
- **La persona se elimina después de registrarse la tasa:** la tasa permanece, como todo el historial. No se aplica, porque no hay a quién.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Y queda declarada una decisión que `cm.md` no fija y que el responsable del proyecto tomó el 02-09-2026**: que **la forma se pueda corregir**. `RN-CM-016` exige una y solo una, y no dice nada de cambiarla; el esquema la admite. Se preguntó porque la alternativa era defendible —hacerla inmutable como el rol— y **se descartó por lo que crea, no por lo que impide**: prohibirlo obligaría a desasociar los productos, retirar la tasa y volver a asociarlos, y **durante esa secuencia esos productos no comisionan**. La ventana no existe hoy; prohibir la corrección la inventaría. El argumento entero está en `RF-CM-003`.

Aquí cuesta menos que en el catálogo por rol —hay vigencia, y `FA-006` describe la salida limpia— y aun así la respuesta es la misma, para que corregir signifique lo mismo en las dos piezas.

**Queda declarada una decisión que podría revisarse y que hoy está tomada:** que la tasa personalizada **no lleve rol**. Devolvérselo recuperaría la protección de §13 y volvería a atarla a que su titular siga vendiendo. El responsable del proyecto decidió lo contrario el 01-09-2026, sabiendo el coste, y aquí queda escrito qué habría que deshacer si algún día se quisiera revertir.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 02-09-2026 | Redacción inicial, **después de construirse el requerimiento** — excepción al Art. I.1 declarada en cabecera. Recoge la pieza que nació al partir el alta en dos el 01-09-2026: la **excepción por persona**, con vigencia, sin rol y sin producto. §4.1 argumenta por qué su corrección y su retiro viven aquí y no en `RF-CM-003` y `RF-CM-004` — **se comportan distinto**, y describirlos juntos habría llenado aquellos documentos de «salvo en el caso de». §13 recoge, con la evidencia que dio construir `RF-CM-005`, **la protección que se perdió al quitarle el rol**: la tasa no «se queda callada» cuando su titular deja de vender, **sigue pagando**, y cerrarla exige un acto deliberado. §14 deja escrito qué habría que deshacer para revertir esa decisión. | Responsable técnico |
| 0.2.0 | 02-09-2026 | **Entra el valor fijo** (`cm.md` v0.7.0), y esta vez **antes del código**. La mecánica de la elección es la de `RF-CM-001` §6.1 y se hereda sin repetirla; lo que este documento tiene que decir es **por qué aquí no significa lo mismo**. §2 lo recoge: una tasa de rol en importe fijo se interpreta en la moneda de los productos que alguien le asoció, y **esta no se asocia a nada** (`RN-CM-014`), de modo que «10.000 fijos» son **diez mil de cada moneda del catálogo**. §5 avisa de que eso lo dicen `RN-CM-014` y `RN-CM-017` **juntas** y ninguna de las dos por su cuenta, y `CA-CM-089` lo fija como prueba que afirma que **nada lo advierte**. Nace `FA-006` —cambiar de forma a partir de una fecha—, que es la única operación del módulo donde un cambio de forma **deja historial**, y distingue las dos maneras de hacerlo: cerrar y abrir, que conserva el pasado, frente a corregir, que lo reescribe. §14 declara la decisión que `cm.md` no fija y que este documento toma: **la forma se puede corregir**, con lo que habría que cambiar si el responsable prefiere lo contrario. `VAL-002` cambia de significado conservando el identificador y **sus dos caras**, y `VAL-011` y `VAL-012` se reutilizan de `RF-CM-001` **con el mismo texto**, para que el mismo error no se cuente de dos maneras. | Responsable técnico |
