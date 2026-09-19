# SPEC — `RF-CM-006` Registrar la tasa personalizada de una persona sobre un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-006` |
| Módulo | `CM` — Comisiones |
| Versión | 0.6.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |
| Enmendada | 11-09-2026 — **la tasa personalizada SE ASOCIA a productos**, con el mismo mecanismo que la de rol: se crea sin producto y se asocia después, a uno o a varios. Entra el ciclo de asociar y desasociar, `RN-CM-012` y `RN-CM-015` la alcanzan, y `RN-CM-006` pasa a comprobarse **al asociar** (Art. I.7) |
| Enmendada | 14-09-2026 — **el producto gratuito SÍ comisiona, y solo por importe fijo** (`RN-CM-020`, `cm.md` v0.13.0): se invierte lo del 08-09-2026. Ver §15 |
| Enmendada | 16-09-2026 — **la personalizada NACE con su producto** (`RN-CM-021`, `cm.md` v0.15.0 §5.5): una vigente por persona, producto y día; la asociación desaparece y `RN-CM-006` vuelve al motor. Ver §15 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`.

!!! danger "Este requerimiento se construyó ANTES de tener especificación"

    Es una excepción al Art. I.1, registrada en `requirements/cm.md` §4 y en la matriz. El motivo: el módulo se rehizo entero el 02-09-2026 y **sin esta pieza no había forma de probarlo de punta a punta**.

    Queda dicho porque cambia cómo leerla: **la v0.1.0 no proponía, describía**.

    **La v0.2.0 sí propone.** El valor fijo de `cm.md` v0.7.0 no existe en el código, y lo que este documento diga de él decide lo que habrá que construir.

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`user-commission-rates:create`** y no `commissions:create` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `commissions:create` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `commissions:create`. Las menciones de `commissions:create` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Objetivo

Declarar que **una persona concreta gana algo distinto** de lo que le correspondería por su rol —un porcentaje o una cantidad fija— **por un producto concreto**, y **desde cuándo**.

## 2. Contexto

El catálogo por rol dice lo que gana un `AGENTE`. Pero se negocia con personas, no con roles: alguien entra con condiciones mejores, alguien las gana por resultados. **Eso es una excepción, y no un grado más del catálogo.**

**Se separó del alta de rol el 01-09-2026**, y no por gusto: **no son la misma operación**. Una escribe la configuración de un producto, sin fechas; la otra registra una excepción **con vigencia**, que rige desde el día que declara. Hasta entonces eran un solo endpoint con campos opcionales, y eso obligaba a validaciones que dependían de qué campo había llegado. **Desde el 16-09-2026 las dos nacen con su producto** (`RN-CM-021`): lo que las distingue es la vigencia, y solo eso.

**Y esta tasa no lleva rol**, por decisión del responsable del proyecto: es de la persona y punto. Lo que eso cuesta está en §13, y es la parte de este requerimiento que más fácil se subestima.

**Es la única pieza del módulo con vigencia**, y por tanto **el único historial que le queda**. Sus filas cerradas dicen qué ganó esa persona y hasta cuándo. El catálogo por rol perdió esa capacidad.

**Y desde el 02-09-2026 puede declararse en valor fijo** (`RN-CM-016`), igual que una tasa de rol. Es la misma elección y el mismo objeto —una forma y solo una, nunca las dos—, pero **aquí no significa lo mismo**, y la diferencia no es de grado.

!!! success "Un importe fijo personalizado tiene moneda desde el 16-09-2026: la de su producto"

    Del 02-09-2026 al 11-09-2026 esta caja decía que un importe fijo personalizado regía sobre **todo el catálogo, en todas sus monedas a la vez**, porque la tasa no se asociaba a nada; del 11-09-2026 al 16-09-2026, que se interpretaba en la moneda de cada producto al que se la asociara. **Hoy la tasa nace con un producto** (`RN-CM-021`), tiene una sola moneda, y el importe **se valida contra sus decimales** al registrar y al corregir (`RN-CM-017`, `VAL-014`).

    Lo que esta especificación sigue haciendo es **decirlo**: §6.2 devuelve la forma junto al valor, y el producto con su precio y su moneda; y `RF-CM-005` la devuelve otra vez al resolver.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Declara la excepción de una persona **sobre un producto**: **en qué forma gana**, cuánto, y desde cuándo |

## 4. Alcance

### 4.1 Incluye

- Registrar la tasa personalizada de una persona **sobre un producto**, con **su forma, su valor** y su **vigencia**. **Rige sobre ese producto desde su inicio de vigencia**, sin paso de asociación (`RN-CM-021`, 16-09-2026).
- Verificar que la persona existe, y que **el producto existe y no está retirado** (`RN-CM-002`, `RN-CM-010`).
- Garantizar, **al registrar**, que **ningún día queda cubierto por dos tasas vivas de la misma persona sobre el mismo producto** (`RN-CM-006`) — y que el motor lo garantice aunque el caso de uso no lo compruebe.
- Garantizar, **al registrar**, que **lo que se paga no supera el precio de ese producto** cuando la forma es un valor fijo (`RN-CM-019`), que sobre un producto gratuito solo entra un importe fijo (`RN-CM-020`), y que el importe fijo **cabe en los decimales de la moneda del producto** (`RN-CM-017`).
- **Corregir** el valor —**y su forma**— y el fin de vigencia de una tasa ya registrada.
- **Retirar** una tasa con motivo obligatorio.
- Dejar constancia de todo ello en la auditoría.

**La corrección y el retiro están aquí y no en `RF-CM-003` y `RF-CM-004`**, aunque sean la misma operación conceptual. El motivo es que **se comportan distinto**: aquí corregir **no borra el pasado** —hay vigencia, y cambiar lo que se gana a partir de una fecha es cerrar la vigente y abrir otra—, y el retiro **sí tiene una vigencia que podría cerrarse «de paso»** y no debe. Describirlas junto a las de rol habría obligado a un documento lleno de «salvo en el caso de».

### 4.2 No incluye

- **Cambiar la persona, el producto o el inicio de vigencia.** Son parte de **lo que la tasa es**: cambiarlos no la corrige, crea otra, y reescribiría a quién se le pagó qué por qué (`EX-005`). **El producto volvió a esta lista el 16-09-2026**: del 11-09-2026 al 16-09-2026 se añadía y se quitaba por asociación; hoy nace con la tasa y no se corrige (`RN-CM-021`).
- **Asociar ni desasociar productos.** Existió del 11-09-2026 al 16-09-2026; una excepción que abarque varios productos son hoy varias tasas, una por producto.
- **Declarar en qué moneda se paga un valor fijo.** No es un campo de esta tasa: es la de su producto (`RN-CM-017`), y el producto ya está en la fila.
- **Exigir que la persona sea vendedora.** Se consideró y **se descartó al quitarle el rol**. Ver §13.
- **Resolver cuál se aplica.** Es `RF-CM-005`.
- **Calcular ni liquidar.**

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-002` | El producto debe existir | `requirements/cm.md` §5.1 |
| `RN-CM-004` | La personalizada gana siempre, sobre su producto | `requirements/cm.md` §5.1 |
| `RN-CM-005` | La tasa no desaparece | `requirements/cm.md` §5.1 |
| `RN-CM-006` | Una sola tasa personalizada vigente por persona **y producto** — en el motor | `requirements/cm.md` §5.1 |
| `RN-CM-007` | El porcentaje va de cero a cien | `requirements/cm.md` §5.1 |
| `RN-CM-009` | Toda tasa personalizada declara desde cuándo rige | `requirements/cm.md` §5.1 |
| `RN-CM-010` | No se configura lo que ya no se vende | `requirements/cm.md` §5.1 |
| `RN-CM-016` | **Una tasa declara una forma y solo una** | `requirements/cm.md` §5.1 |
| `RN-CM-017` | El valor fijo no lleva moneda: **es la de su producto** | `requirements/cm.md` §5.1 |
| `RN-CM-019` | Tope individual contra el precio de su producto | `requirements/cm.md` §5.1 |
| `RN-CM-020` | Un producto gratuito comisiona solo por importe fijo | `requirements/cm.md` §5.1 |
| `RN-CM-021` | **Toda tasa nace con su producto, y no lo cambia** | `requirements/cm.md` §5.1 |

**`RN-CM-006` es la que sostiene a `RN-CM-004`**: con dos tasas cubriendo el mismo día sobre el mismo producto, la resolución dejaría de ser determinista. **Vive en el motor desde el 16-09-2026** —otra vez—: el caso de uso la comprueba antes para dar el mensaje, y la restricción la garantiza aunque nadie la comprobara.

**`RN-CM-014` y `RN-CM-018` ya no aplican** (retiradas el 16-09-2026): ninguna tasa se asocia y ninguna desconoce el precio de su producto. Lo que juntas decían —que un importe fijo personalizado se interpretaba en tantas monedas como hubiera— está cerrado en §2.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Persona | Sí | De quién es la tasa | Debe existir. **No se le exige portar rol vendedor** |
| **Producto** | **Sí** (16-09-2026) | Sobre qué producto rige | Debe existir y **no estar retirado** (`RN-CM-002`, `RN-CM-010`). **No se corrige** (`RN-CM-021`) |
| Forma | Sí | Si gana **una proporción de la venta** o **una cantidad de dinero** | Una de las dos, y **solo una** (`RN-CM-016`). Sobre un producto **gratuito**, solo cantidad (`RN-CM-020`) |
| Porcentaje | **Solo si la forma es proporción** | Qué proporción gana | De **cero a cien** (`RN-CM-007`) |
| Valor fijo | **Solo si la forma es cantidad** | Cuánto dinero gana por venta | **Cero o más**, y **no más que el precio del producto** (`RN-CM-019`, individual; sin tope si el producto es gratuito). **En la moneda del producto** (`RN-CM-017`): con los decimales que esa moneda admite |
| Inicio de vigencia | Sí | Desde qué día rige | Una fecha. Puede ser pasada o futura |
| Fin de vigencia | No | Hasta qué día rige, **inclusive** | No puede ser anterior al inicio. **Sin él, rige indefinidamente** (`RN-CM-009`) |

**No hay rol en el alta, y sí hay producto.** El rol **no es un campo de esta tasa** y no lo será. El producto **llega en el alta desde el 16-09-2026** (`RN-CM-021`): del 11-09-2026 al 16-09-2026 llegaba en otra operación, la asociación, y hasta entonces la tasa no regía en ninguna parte; antes del 11-09-2026 no llegaba nunca y la tasa regía sobre todo. Hoy una tasa sin producto **no existe** (`VAL-013`).

**La forma es exactamente la misma elección que en una tasa de rol**, con los mismos motivos para declararla en vez de deducirla del campo que venga lleno. Están en `RF-CM-001` §6.1 y no se repiten.

**La ausencia del valor de la otra forma sí significa algo, y no es lo mismo que la ausencia del fin de vigencia.** Un fin vacío es una declaración —«indefinidamente»—; un porcentaje vacío en una tasa de tipo `FIJO` **no declara nada**: es la consecuencia mecánica de haber elegido la otra forma. Se dice porque este es el único documento del módulo donde conviven los dos tipos de ausencia.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tasa | Identificador y vigencia |
| Forma y valor | **En qué forma gana y cuánto**, siempre juntos. El valor de la otra forma viaja **vacío, no omitido** |
| Persona resuelta | Nombre de usuario y nombre |
| **Producto resuelto** | Identificador, código y nombre, **con su precio y su moneda** —identificador, código y decimales—, con la misma forma que en la tasa de rol (`RF-CM-002` `CA-CM-145`). En el alta, en la corrección y en cada fila del listado (16-09-2026) |

**El fin de vigencia viaja vacío y presente** cuando la tasa rige indefinidamente: un campo que desaparece del resultado es indistinguible de uno que el cliente no conoce, y aquí significa algo.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso correspondiente.
- La persona existe, y el producto existe y no está retirado.

**Postcondiciones**

- La tasa queda registrada y **rige sobre su producto desde el día que declara**, que puede no ser hoy.
- **Rige sin necesidad de nada más**, como toda tasa desde `RN-CM-021`.
- **Ningún día queda cubierto por dos tasas vivas de esa persona sobre ese producto** (`RN-CM-006`), y lo garantiza el motor.
- La auditoría contiene el evento correspondiente.

## 8. Flujo principal

**Registrar**

1. El actor envía la persona, **el producto**, **la forma y su valor** y el inicio de vigencia, y opcionalmente el fin.
2. El sistema comprueba que **se declaró una forma y solo una**, que el valor es admisible para ella —el porcentaje, entre cero y cien; el importe, cero o más—, y que el fin, si se envió, no es anterior al inicio.
3. El sistema comprueba que la persona existe, y que el producto existe y no está retirado.
4. El sistema comprueba que el importe fijo cabe en los decimales de la moneda del producto, y que el valor cabe en el producto: el tope individual (`RN-CM-019`) y, si es gratuito, que la forma sea importe (`RN-CM-020`).
5. El sistema comprueba que **ningún día del periodo declarado está ya cubierto** por otra tasa viva de esa persona **sobre ese producto** — y el motor lo vuelve a comprobar al escribir.
6. El sistema registra la tasa y emite el evento de auditoría de creación.
7. El sistema devuelve la tasa, con la persona y el producto resueltos.

**Corregir**

1. El actor envía **el valor corregido con su forma**, el fin de vigencia, o los dos.
2. El sistema rechaza la petición si trae la persona o el inicio de vigencia, o si no informa nada.

**Corregir el valor es corregir la pareja entera**, y no uno de los dos campos: quien corrige envía la forma **aunque no la cambie**. Enviar un importe suelto sobre una tasa que era de porcentaje dejaría al sistema decidiendo si es un cambio de forma o una equivocación, y ya está decidido que eso no se deduce (§6.1).
3. El sistema comprueba que la tasa existe y está viva; que el resultado **no se solapa** con otra de la misma persona sobre el mismo producto; y, si cambia el valor, que cabe en su producto —tope, gratuito y decimales de la moneda—.
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

**Condición:** **al registrar**, o al corregir el fin de vigencia, ya existe otra tasa viva de esa persona **sobre ese producto** que cubre alguno de los días de esta.
**Respuesta del sistema:** rechaza diciendo que esa persona ya tiene una tasa viva sobre ese producto en parte de ese periodo, y no escribe nada (`409`). **Es la misma respuesta cuando la que llega primero es otra petición simultánea**: el motor rechaza la segunda, y el sistema la traduce igual.

Del 11-09-2026 al 16-09-2026 ocurría **al asociar** y nunca al registrar, porque el alta no tenía producto. Hoy vuelve a ser del alta.

**Sobre OTRO producto no se solapa**: la misma persona puede tener varias tasas vivas a la vez mientras hablen de productos distintos.

### EX-006 — El producto no existe (16-09-2026)

**Condición:** el identificador no corresponde a ningún producto.
**Respuesta del sistema:** rechaza el alta diciendo que el producto indicado no existe (`422`). Como `EX-001`, no es un «no encontrado».

### EX-007 — El producto está retirado (16-09-2026)

**Condición:** el producto existe y está retirado (`RN-CM-010`).
**Respuesta del sistema:** rechaza el alta diciendo que no se configuran comisiones sobre un producto retirado (`422`), distinto de `EX-006` para que quien mira un catálogo desactualizado sepa cuál de las dos cosas pasó.

### EX-008 — El valor no cabe en el producto (16-09-2026)

**Condición:** un importe fijo mayor que el precio del producto (`RN-CM-019`, tope individual), o un porcentaje sobre un producto gratuito (`RN-CM-020`). Al registrar y al corregir el valor.
**Respuesta del sistema:** rechaza con `409` y el mensaje propio de cada caso. Del 11-09-2026 al 16-09-2026 ocurría al asociar; hoy, al registrar.

### EX-003 — La tasa no existe o está retirada

**Condición:** al corregir, el identificador no corresponde a ninguna tasa viva.
**Respuesta del sistema:** rechaza la corrección diciendo que no existe.

### EX-004 — Ya estaba retirada

**Condición:** al retirar, la tasa existe y ya fue retirada.
**Respuesta del sistema:** rechaza el retiro diciendo que ya estaba retirada. **No es idempotente a propósito**: dos motivos distintos sobre un mismo hecho harían que el registro mienta.

### EX-005 — Se intenta cambiar la persona o el inicio de vigencia

**Condición:** la corrección trae alguno de los dos.
**Respuesta del sistema:** los rechaza diciendo que no se pueden corregir. **Se rechazan y no se ignoran.**

**El producto vuelve a esta lista el 16-09-2026**, después de haber estado unas horas el 11-09-2026 y haber salido ese mismo día: nace con la tasa y no se corrige (`RN-CM-021`). No entra en `VAL-009`, porque el cuerpo de la corrección no lo declara: enviarlo es un campo desconocido y responde `400` sin código.

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
| `VAL-013` | **Producto obligatorio** (16-09-2026) | El producto de la tasa es obligatorio. |
| `VAL-014` | **Decimales del importe fijo según la moneda del producto** (`RN-CM-017`, 16-09-2026) | El valor fijo no admite más decimales que los de la moneda del producto. |

**`VAL-011` y `VAL-012` son las mismas de `RF-CM-001`**, con el mismo texto, porque es la misma regla sobre la otra pieza. Reutilizar el identificador es lo que el módulo hace con `VAL-007` y `VAL-008`, y aquí importa más: **si las dos altas dieran mensajes distintos ante el mismo error, parecería que las dos formas se declaran de dos maneras**.

**`VAL-002` cambió de significado y conserva el identificador**, igual que en `RF-CM-001` §11. Aquí además conserva **las dos caras** que ya tenía —obligatorio al registrar, no vaciable al corregir— y las dos pasan a hablar de la forma, no del porcentaje.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| ~~`CA-CM-051`~~ | ~~Registra la tasa de una persona con la persona resuelta, sin rol y sin producto~~ **Superado el 16-09-2026** por `CA-CM-146`: el producto llega en el alta |
| `CA-CM-052` | El fin de vigencia ausente viaja **vacío y presente**, y significa «indefinidamente» |
| `CA-CM-053` | **Admite a quien no porta rol vendedor**, y esa tasa queda registrada |
| `CA-CM-054` | **Al registrar**, rechaza la que se solapa con otra viva de la misma persona **sobre el mismo producto** (reescrito el 16-09-2026; hasta entonces, al asociar) |
| `CA-CM-055` | **El día de corte cuenta**: si una termina el 30, la siguiente no empieza el 30 |
| `CA-CM-056` | Admite **varias consecutivas**: son el historial |
| `CA-CM-057` | Dos **personas distintas** pueden solapar sin conflicto |
| `CA-CM-058` | Retirar **libera los días** que ocupaba |
| ~~`CA-CM-118`~~ | ~~Una tasa se asocia a varios productos~~ **Superado el 16-09-2026** (`CA-CM-149`): una excepción sobre varios productos son varias tasas |
| `CA-CM-119` | **Al registrar**, el **producto inexistente** y el **retirado** se rechazan con respuestas **distintas** (`422`, `EX-006` y `EX-007`; reescrito el 16-09-2026) |
| `CA-CM-120` | **Al registrar**, rechaza el **valor fijo que supera el precio** de ese producto — y **el mismo valor** entra sobre otro más caro: lo que decide es el producto (reescrito el 16-09-2026) |
| `CA-CM-134` | **Al registrar** sobre un producto de **precio cero**, una personalizada de **valor fijo** entra **sin tope** —el importe que sea— (`RN-CM-020`, 14-09-2026; reescrito el 16-09-2026) |
| `CA-CM-135` | **Al registrar** sobre un producto de **precio cero**, una personalizada de **porcentaje** se rechaza con `EX-008`, y **el mismo porcentaje** entra sobre un producto con precio: lo que decide es el producto (reescrito el 16-09-2026) |
| ~~`CA-CM-121`~~ | ~~Desasociar deja de regir ahí y la tasa sigue viva~~ **Superado el 16-09-2026** (`CA-CM-149`): no hay asociación que quitar; dejar de regir es retirar o cerrar la vigencia |
| ~~`CA-CM-124`~~ | ~~Una tasa sin asociar no paga nada~~ **Superado el 16-09-2026** (`CA-CM-146`): no existe una tasa sin producto |
| ~~`CA-CM-125`~~ | ~~`RN-CM-015`: una tasa asociada no se retira~~ **Superado el 16-09-2026** (`CA-CM-151`): el retiro no tiene condición |
| `CA-CM-146` | Registra la tasa **con su producto** y lo devuelve resuelto —`id`, `code`, `name`, `price`, `currency`—; **rige sobre él desde su inicio de vigencia** sin ningún paso más, y `RF-CM-005` la resuelve. Sin producto se rechaza (`VAL-013`) |
| `CA-CM-147` | `RN-CM-006` **en el motor**: dos altas de la misma persona sobre el mismo producto con días en común se rechazan con `409` **también cuando llegan a la vez** —una queda, la otra recibe el conflicto, ninguna un `500`—; sobre productos distintos conviven, y consecutivas sobre el mismo también |
| `CA-CM-148` | Rechaza al registrar un importe fijo con **más decimales** de los que admite la moneda del producto (`VAL-014`), y admite el mismo importe con los decimales justos |
| `CA-CM-149` | Las rutas de asociación de la personalizada —`POST /{id}/products`, `GET /{id}/products`, `POST /{id}/products/{productId}/deletion`— **ya no existen** (`404` de ruta) |
| `CA-CM-059` | Retirar **no cierra la vigencia** |
| `CA-CM-060` | Corregir **vacía** el fin de vigencia, y la tasa vuelve a regir indefinidamente |
| `CA-CM-061` | Rechaza corregir la persona o el inicio de vigencia |
| `CA-CM-062` | Rechaza el fin anterior al inicio, la persona inexistente, y exige el permiso |
| `CA-CM-085` | Registra una tasa personalizada **en valor fijo**, y la respuesta declara la forma junto al valor |
| `CA-CM-086` | Rechaza las dos formas a la vez, ninguna, y el valor que no corresponde a la forma |
| `CA-CM-087` | Admite **dos tasas consecutivas de formas distintas** —porcentaje hasta el 31, importe desde el 1— y **las dos quedan** |
| `CA-CM-088` | Corregir **cambia la forma** de una tasa viva, y el evento registra el antes y el después **de las dos cosas** |
| ~~`CA-CM-089`~~ | ~~El valor fijo de una personalizada rige igual sobre productos de monedas distintas, y nada lo advierte~~ **Superado el 16-09-2026** (`CA-CM-148`): una tasa tiene un producto y una moneda, y el importe se valida contra ella |

**`CA-CM-087` y `CA-CM-088` prueban las dos mitades de `FA-006`, y la pareja es el criterio**. Por separado, cada uno comprueba una operación corriente; juntos verifican que **el sistema ofrece las dos maneras de cambiar de forma y no las confunde** — una deja historial, la otra reescribe.

!!! success "`CA-CM-089` afirmaba que el sistema NO advertía de algo, y dejó de ser cierto el 16-09-2026"

    Resolvía la misma tasa personalizada contra dos productos de monedas distintas y comprobaba que devolvía el mismo importe las dos veces, sin señal. Era lo que `RN-CM-017` y `RN-CM-014` declaraban **juntas**. Con la tasa naciendo con su producto no hay «dos productos» contra los que resolver la misma tasa: la prueba se retira, y `CA-CM-148` ocupa su sitio afirmando lo contrario — que el importe se valida contra la moneda de su único producto.

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
- **Un importe fijo personalizado, sobre un catálogo con varias monedas:** dejó de ser un caso límite el 16-09-2026: la tasa es de un producto y de su moneda (§2).
- **Una tasa personalizada en importe fijo mayor que el precio de su producto:** se rechaza al registrar (`RN-CM-019`, `EX-008`). Hasta el 11-09-2026 se registraba y regía sobre todo; del 11-09-2026 al 16-09-2026 se registraba y se rechazaba al asociar.
- **La misma persona, con excepción en varios productos:** son varias tasas, una por producto, cada una con su vigencia. Es el precio de que `RN-CM-006` viva en el motor.
- **Corregir la forma de una tasa cuya vigencia ya pasó entera:** se admite, y **reescribe un periodo cerrado**. Es el mismo riesgo que corregir su porcentaje, con más ruido: `FA-006` explica cuándo esa es la operación correcta y cuándo no.
- **Dos altas simultáneas del mismo periodo sobre el mismo producto:** una queda y la otra recibe el conflicto. Del 11-09-2026 al 16-09-2026 lo cerraba un bloqueo por persona en el caso de uso; hoy lo cierra el motor otra vez, y el conflicto puede llegar de dos formas —la exclusión, o el interbloqueo de dos inserciones que se esperan— que el sistema traduce igual (`CA-CM-147`).
- **La persona se elimina después de registrarse la tasa:** la tasa permanece, como todo el historial. No se aplica, porque no hay a quién.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Y queda declarada una decisión que `cm.md` no fija y que el responsable del proyecto tomó el 02-09-2026**: que **la forma se pueda corregir**. `RN-CM-016` exige una y solo una, y no dice nada de cambiarla; el esquema la admite. Se preguntó porque la alternativa era defendible —hacerla inmutable como el rol— y **se descartó por lo que crea, no por lo que impide**: prohibirlo obligaría a retirar la tasa y registrar otra, y **entre las dos el producto no comisiona a esa persona**. La ventana no existe hoy; prohibir la corrección la inventaría. El argumento entero está en `RF-CM-003`.

Aquí cuesta menos que en el catálogo por rol —hay vigencia, y `FA-006` describe la salida limpia— y aun así la respuesta es la misma, para que corregir signifique lo mismo en las dos piezas.

**Queda declarada una decisión que podría revisarse y que hoy está tomada:** que la tasa personalizada **no lleve rol**. Devolvérselo recuperaría la protección de §13 y volvería a atarla a que su titular siga vendiendo. El responsable del proyecto decidió lo contrario el 01-09-2026, sabiendo el coste, y aquí queda escrito qué habría que deshacer si algún día se quisiera revertir.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 02-09-2026 | Redacción inicial, **después de construirse el requerimiento** — excepción al Art. I.1 declarada en cabecera. Recoge la pieza que nació al partir el alta en dos el 01-09-2026: la **excepción por persona**, con vigencia, sin rol y sin producto. §4.1 argumenta por qué su corrección y su retiro viven aquí y no en `RF-CM-003` y `RF-CM-004` — **se comportan distinto**, y describirlos juntos habría llenado aquellos documentos de «salvo en el caso de». §13 recoge, con la evidencia que dio construir `RF-CM-005`, **la protección que se perdió al quitarle el rol**: la tasa no «se queda callada» cuando su titular deja de vender, **sigue pagando**, y cerrarla exige un acto deliberado. §14 deja escrito qué habría que deshacer para revertir esa decisión. | Responsable técnico |
| 0.2.0 | 02-09-2026 | **Entra el valor fijo** (`cm.md` v0.7.0), y esta vez **antes del código**. La mecánica de la elección es la de `RF-CM-001` §6.1 y se hereda sin repetirla; lo que este documento tiene que decir es **por qué aquí no significa lo mismo**. §2 lo recoge: una tasa de rol en importe fijo se interpreta en la moneda de los productos que alguien le asoció, y **esta no se asocia a nada** (`RN-CM-014`), de modo que «10.000 fijos» son **diez mil de cada moneda del catálogo**. §5 avisa de que eso lo dicen `RN-CM-014` y `RN-CM-017` **juntas** y ninguna de las dos por su cuenta, y `CA-CM-089` lo fija como prueba que afirma que **nada lo advierte**. Nace `FA-006` —cambiar de forma a partir de una fecha—, que es la única operación del módulo donde un cambio de forma **deja historial**, y distingue las dos maneras de hacerlo: cerrar y abrir, que conserva el pasado, frente a corregir, que lo reescribe. §14 declara la decisión que `cm.md` no fija y que este documento toma: **la forma se puede corregir**, con lo que habría que cambiar si el responsable prefiere lo contrario. `VAL-002` cambia de significado conservando el identificador y **sus dos caras**, y `VAL-011` y `VAL-012` se reutilizan de `RF-CM-001` **con el mismo texto**, para que el mismo error no se cuente de dos maneras. | Responsable técnico |
| 0.3.0 | 11-09-2026 | **Corregida el mismo día por v0.4.0, y se conserva para que el cambio quede a la vista.** Declaró que la tasa personalizada dejaba de regir sobre todo el catálogo —eso se mantiene— y lo resolvió con un `productId` **obligatorio en el alta** e inmutable. El responsable del proyecto corrigió la forma, no el fondo. | Responsable del proyecto |
| 0.4.0 | 11-09-2026 | **La tasa personalizada SE ASOCIA a productos, con el mismo mecanismo que la de rol.** Se crea sin producto y se asocia después, a uno o a varios, con su operación de **desasociar**. §4.1 gana el ciclo entero; §4.2 cambia de contenido —lo que ya no se puede es cambiar la persona o el inicio de vigencia, y el producto **sale** de esa lista porque se añade y se quita—; §6.1 pierde la fila de producto y el valor fijo vuelve a no tener tope **en el alta** (`RN-CM-018`), que se lo pone `RN-CM-019` **al asociar**; §6.2 publica la lista de productos en la respuesta de asociar y desasociar, no en la del alta. **`EX-002` se muda al asociar** y deja escrito que al registrar no puede ocurrir. Nacen `CA-CM-118` a `CA-CM-121`, `CA-CM-124` —una tasa sin asociar **no paga nada**, que es `RN-CM-012` alcanzando por fin a esta pieza— y `CA-CM-125` —`RN-CM-015`: asociada no se retira—. | Responsable del proyecto |
| 0.5.0 | 14-09-2026 | **El producto gratuito SÍ comisiona, y solo por importe fijo** (`RN-CM-020`, [`cm.md`](../../../requirements/cm.md) v0.13.0), por decisión del responsable del proyecto. Al asociar una personalizada a un producto de precio cero, el **valor fijo entra sin tope** —el tope individual del 11-09-2026 no aplica a los gratuitos— y el **porcentaje se rechaza** con `EX-008`. Nacen `CA-CM-134` y `CA-CM-135`. | Responsable del proyecto |
| 0.6.0 | 16-09-2026 | **La personalizada NACE con su producto** (`RN-CM-021`, [`cm.md`](../../../requirements/cm.md) v0.15.0 §5.5), por decisión del responsable del proyecto —«una sola comisión personalizada por usuario y producto»—, **conservando la vigencia**. `productId` entra **obligatorio e inmutable** en el alta (`VAL-013`); asociar, desasociar y «los productos de una tasa» **se retiran** (`CA-CM-149`); todo lo que se comprobaba al asociar se comprueba al registrar —producto vivo (`EX-006`, `EX-007`), solapamiento (`EX-002`, otra vez del alta), tope y gratuito (`EX-008`)— y el importe fijo gana los decimales de la moneda (`VAL-014`, `CA-CM-148`). **`RN-CM-006` vuelve al motor** (`CA-CM-147`). `CA-CM-051`, `118`, `121`, `124`, `125` y `089` quedan superados; `054`, `119`, `120`, `134` y `135` se reescriben al alta; nacen `CA-CM-146` a `CA-CM-149`. La corrección y el retiro, en `RF-CM-003` v0.8.0 y `RF-CM-004` v0.4.0. | Responsable del proyecto |
| 0.7.0 | 19-09-2026 | **Cambia el permiso: `user-commission-rates:create` y no `commissions:create`** (`RF-SP-060`, `RN-SEG-014`, un permiso por operación; [`security.md`](../../../security.md) v0.63.0). Enmienda de Art. I.7 sin cambio de comportamiento: la misma operación, el mismo actor, un código propio sembrado por `V28` y dado a todo rol que portara `commissions:create`. | Responsable del proyecto |
