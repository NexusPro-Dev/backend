# SPEC — `RF-CM-002` Consultar las tasas de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-002` |
| Módulo | `CM` — Comisiones |
| Versión | 1.3.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |
| Enmendada el | 12-09-2026 — **la asociación de la tasa personalizada se puede LEER**: filtro por producto y cuenta de asociados en el listado, y una quinta lectura con los productos de una personalizada (`RN-CM-014`). Ver §15 |
| Enmendada el | 15-09-2026 — **cada tasa de rol trae su producto** y el listado filtra por `productId`; `GET /commission-rates/{id}/products` se retira (`RN-CM-021`). Ver §15 |
| Enmendada el | 15-09-2026 — **el producto de cada tasa de rol trae su precio y su moneda**, a petición del responsable del proyecto. Ver §15 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`.

---

## 1. Objetivo

Ver **qué comisiones hay declaradas** y, sobre todo, **cuáles de ellas rigen de verdad**.

## 2. Contexto

Es la lectura administrativa del módulo, y **devuelve las tasas tal como se declararon**. No resuelve nada: cuál se aplica a un caso concreto lo responde `RF-CM-005`, y son preguntas distintas.

**Hay tres cosas que mirar y no una.** El catálogo por rol, las excepciones por persona y las asociaciones son tres tablas con formas distintas: una no tiene vigencia, otra sí, y la tercera no tiene ni identificador propio. Un listado único devolvería filas con la mitad de los campos vacíos y obligaría al cliente a deducir de qué tipo es cada una.

**Y aun así es UN requerimiento y no tres, y conviene decir por qué.** La pregunta es la misma —«qué comisiones hay declaradas»— sobre tres soportes; el permiso es el mismo; y ninguna de las tres decide nada. Partirlo habría multiplicado por tres una especificación cuyo contenido propio cabe en dos párrafos, y habría escondido lo único que de verdad hay que entender: **que las tres juntas responden a la pregunta y ninguna sola la responde**.

**Hay algo que solo se ve mirando las tres a la vez.** Una tasa de rol puede aparecer en el catálogo con su valor y **no regir sobre nada**. Es la consecuencia de `RN-CM-012`, y es invisible desde el catálogo si el catálogo no lo dice. Por eso cada tasa de rol declara **sobre cuántos productos rige**: sin ese número, el listado sería idéntico para una tasa que paga y para una que no.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Revisa las comisiones declaradas, busca una para corregirla, y comprueba **qué se está pagando de verdad** |
| Administrador | Comprueba, antes de retirar una tasa, sobre qué productos rige (`RN-CM-015`) |

## 4. Alcance

### 4.1 Incluye

Cinco lecturas que responden a la misma pregunta desde cinco sitios:

| Lectura | Responde a |
|---|---|
| **El catálogo por rol** | Qué tasas hay declaradas, para qué rol, y **sobre cuántos productos rige cada una** |
| **Las tasas personalizadas** | Qué excepciones por persona hay, incluido su **historial**, **sobre cuántos productos rige cada una** y —filtrando— **quién tiene excepción en este producto** |
| **Las asociaciones de una tasa de rol** | Sobre qué productos rige esta tasa concreta |
| **Las asociaciones de una tasa personalizada** | Sobre qué productos rige esta excepción concreta (12-09-2026) |
| **Las asociaciones de un producto** | **Qué paga este producto, y a qué rol** |

Además: filtrar, paginar los dos primeros, marcar las retiradas sin excluirlas, y devolver el orden aplicado.

**La cuarta nació el 12-09-2026, un día después que la asociación que lee.** `RN-CM-014` se invirtió el 11-09-2026 y la personalizada pasó a asociarse a productos con el mismo mecanismo que la de rol — con sus dos escrituras y **sin ninguna lectura**: la única forma de saber sobre qué regía una excepción era la respuesta de la operación que la asociaba. Lo pidió el responsable del proyecto al preguntar si existía un endpoint para consultar las comisiones personalizadas por producto, y la respuesta era que no.

### 4.2 No incluye

- **Resolver cuál se aplica a una persona.** Es `RF-CM-005`, y es otra pregunta. La diferencia importa: filtrar las personalizadas por persona devuelve **las declaradas para ella**, no la que **le aplica** hoy sobre un producto.
- **El motivo del retiro** de una tasa retirada. Uno a uno es una consulta legítima; en bloque sería una exportación de decisiones comerciales. Mismo criterio que `RF-PM-002` aplicó al catálogo.
- **Filtrar el catálogo por fecha.** Las tasas de rol **no tienen vigencia**: no hay una respuesta a «qué regía el mes pasado». Solo las personalizadas admiten ese filtro.
- **Paginar las asociaciones.** Una tasa —de rol o personalizada— rige sobre un puñado de productos y un producto tiene tantas asociaciones como roles vendedores hay. Ver §13.
- **Devolver las personalizadas dentro de la lectura por producto.** `GET` de las asociaciones de un producto sigue devolviendo **solo los roles**: mezclar en una colección entradas con rol y entradas con persona obligaría a un campo discriminador que hoy nadie necesita. «Qué personas tienen excepción en este producto» se responde **filtrando el listado de personalizadas por producto**, que además pagina. Se descartó, no se olvidó (`plan.md` §11).
- **Modificar nada.**

## 5. Reglas de negocio aplicables

Ninguna. Es una consulta: no decide nada y no cambia nada.

**Lo que sí hace es hacer visible una regla que de otro modo no se ve**: `RN-CM-012` —una tasa no rige hasta que se asocia— no produce ningún error en ningún sitio. **Solo se ve mirando este listado.**

## 6. Datos

### 6.1 Entrada

**Del catálogo por rol**

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Rol | No | Filtra las tasas de un rol | Si el rol no existe, la colección vuelve vacía y **no es un error** |
| Forma | No | Filtra por cómo paga la tasa: porcentaje o valor fijo | Una de las dos. Ausente, **no filtra** |
| Incluir retiradas | No | Si se piden también las retiradas | Por omisión, **no** se incluyen |
| Página y tamaño | No | Paginación | Los límites del sistema |

**El filtro por forma está por decisión del responsable del proyecto**, y no por necesidad técnica: no hay ninguna operación que lo requiera. Responde a la pregunta que nace cuando conviven las dos formas — «enséñame las que pagan importe fijo».

**No lo lleva el listado de personalizadas**, y no es un olvido: allí se filtra por persona, y una persona tiene **una** tasa vigente (`RN-CM-006`). Filtrar por forma sobre un historial de una sola línea no responde a ninguna pregunta.

**De las tasas personalizadas**

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Persona | No | Filtra las tasas de una persona | Devuelve **las declaradas para ella**, no la que le aplica |
| Producto | No | Filtra las tasas **asociadas** a ese producto (12-09-2026) | Devuelve las excepciones que **rigen ahí**, de cualquier persona. Si el producto no existe o nadie tiene excepción en él, la colección vuelve vacía y **no es un error**. **Se combina con los demás**: persona y producto juntos dicen si esa persona tiene excepción en ese producto, y con la fecha, si la tiene hoy |
| Las que rigen en una fecha | No | Devuelve únicamente las vigentes ese día | Una fecha |
| Incluir retiradas | No | Si se piden también las retiradas | Por omisión, **no** |
| Página y tamaño | No | Paginación | Los límites del sistema |

**No hay filtro «solo las vigentes» como interruptor aparte:** es el filtro por fecha con la de hoy. Un interruptor y una fecha podrían contradecirse, y esa contradicción no la detecta nada.

**De las asociaciones**

| Dato | Obligatorio | Descripción |
|---|---|---|
| Tasa de rol, tasa personalizada **o** producto | Sí | Cuál de las tres direcciones se pregunta |

### 6.2 Salida

**Del catálogo por rol**

| Dato | Descripción |
|---|---|
| Tasas | De cada una: su identificador, **su forma y su valor** |
| **Producto resuelto** | Identificador, código y nombre del producto sobre el que rige (15-09-2026, `RN-CM-021`), **con su precio y su moneda** —identificador, código y decimales— |
| Rol resuelto | Código y nombre del rol |
| Marca de retiro | En las retiradas, que lo están y desde cuándo |
| Total y orden | Cuántas cumplen el filtro, y sobre qué se está paginando |

!!! danger "El orden no es «de mayor a menor», y no puede serlo"

    El orden que se esperaría es el útil: por rol y, dentro de cada rol, **lo que más paga arriba**. Con dos formas ese orden **compara cosas que no admiten un «mayor que»**.

    «10 %» y «10.000 fijos» no se pueden ordenar entre sí: cuál paga más depende del precio del producto, que el catálogo no conoce — y que puede ser distinto para cada producto asociado a esa misma tasa.

    Ordenar por la cifra a secas es **peor que no ordenar**, porque produce una lista que **parece** de mayor a menor y no lo es: pondría «100 fijos» por encima de «50 %» sin que eso signifique nada.

    **El orden es: código de producto, código de rol, luego forma, y dentro de cada forma de mayor a menor valor.** No es una ordenación por lo que se paga —eso no existe— sino **dos listas comparables una detrás de otra**, y agrupar por forma es lo que hace honesto el «de mayor a menor» que queda dentro de cada grupo. **El producto va primero desde el 15-09-2026** (`RN-CM-021`): el catálogo se lee como «qué paga cada producto». Y como un rol tiene **una** tasa viva por producto, los criterios de forma y valor solo separan hoy filas retiradas de la misma pareja — siguen ahí por estabilidad, no porque decidan nada.

**De las tasas personalizadas**

| Dato | Descripción |
|---|---|
| Tasas | Identificador, **forma, valor** y **vigencia** |
| Persona resuelta | Nombre de usuario y nombre |
| **Productos asociados** | Cuántos hacen que esa excepción rija (12-09-2026). **El cero significa que no paga nada**, exactamente como en el catálogo: desde `RN-CM-012` sin excepción, una personalizada sin asociar es una excepción declarada que **no rige sobre nada** |
| Marca de retiro, total y orden | Como en el catálogo |

**No llevan rol**, y no es que falte: una tasa personalizada no tiene ninguno. **Y los productos van contados, no listados**: el listado se pagina y una lista dentro de cada fila multiplicaría el cuerpo sin decir nada que la quinta lectura no diga mejor. Hasta el 12-09-2026 este párrafo decía «ni rol ni producto», y era cierto: la personalizada no se asociaba a nada.

**De las asociaciones de una tasa personalizada**

| Dato | Descripción |
|---|---|
| Tasa | Su identificador, el mismo que se pidió |
| Productos resueltos | Identificador, código y nombre de cada uno, **por código** |

**Es la misma forma que devuelven asociar y desasociar** (`RF-CM-006`): la lista completa de productos de esa tasa. Una lectura que devolviera otra forma obligaría al cliente a dos modelos para el mismo dato. **No lleva forma ni valor** porque la tasa es una y ya se conoce; en la lectura por producto viajan porque allí hay una tasa distinta por fila.

**De las asociaciones**

| Dato | Descripción |
|---|---|
| Producto resuelto | Código y nombre |
| Rol resuelto | Código y nombre |
| **Forma y valor** | **Los de la tasa**, ya resueltos |
| Tasa | Su identificador, para poder desasociarla |

**El valor viaja aquí aunque sea de la tasa y no de la asociación**, y es lo que hace útil la lectura por producto: «qué paga este producto a cada rol» se responde de un vistazo, sin cruzar con el catálogo.

**Y la forma viaja con él por la misma razón, con más motivo.** Sin ella, la lectura por producto devolvería una columna de cifras que **no se pueden ni sumar ni comparar entre sí** — que es exactamente lo que un administrador intentaría hacer con ellas al mirar `RN-CM-011`. Ver §13.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de comisiones.

**Postcondiciones**

- Ninguna. No cambia nada.

## 8. Flujo principal

1. El actor pide una de las cinco lecturas, con sus filtros.
2. El sistema aplica los filtros y, en las dos paginadas, la paginación.
3. El sistema resuelve los datos de otros módulos —rol, persona, producto— y los devuelve junto a cada fila.
4. El sistema devuelve la colección, y en las paginadas el total y el orden aplicado.

## 9. Flujos alternativos

### FA-001 — El filtro no encuentra nada

**Cuándo ocurre:** ningún registro cumple el filtro, o el identificador filtrado no existe.

1. La colección vuelve **vacía**, con total cero. **No es un error.**
2. Es válido tanto para un rol sin tasas como para un identificador que no corresponde a nada: distinguirlos costaría una consulta a otro módulo **para no cambiar lo que el cliente hace después**.

### FA-002 — Una tasa que no rige sobre nada

**Cuándo ocurre:** una tasa de rol sin ninguna asociación, o —desde el 12-09-2026— una personalizada sin ninguna.

1. Aparece en su listado con su valor y con **cero productos asociados**.
2. **Es la respuesta correcta y es lo que hay que saber ver**: esa tasa está declarada y no paga nada a nadie. En la personalizada es el estado en que **nace** (`RF-CM-006`), y la cuenta en cero es lo que distingue una excepción a medio configurar de una que rige.

### FA-003 — Un producto que no paga a nadie

**Cuándo ocurre:** se piden las asociaciones de un producto que no tiene ninguna.

1. La colección vuelve vacía.
2. Significa que **ese producto no comisiona a ningún rol** — ni siquiera a los que tienen tasa en el catálogo, si nadie la asoció.

## 10. Excepciones

Ninguna propia. Los parámetros mal formados los rechaza la validación de entrada del sistema, y un filtro que no encuentra nada **no es una excepción** (`FA-001`).

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| — | Las de paginación y formato de fecha del sistema | — |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-009` | El catálogo devuelve las tasas de rol con el rol resuelto, paginadas y con el orden publicado |
| `CA-CM-010` | Cada tasa declara **sobre cuántos productos rige**, y el cero significa que no paga nada |
| `CA-CM-011` | **La cuenta de asociaciones no multiplica las filas**: una tasa con dos asociaciones aparece **una vez** |
| `CA-CM-012` | Las retiradas no salen salvo que se pidan, y cuando salen van **marcadas** |
| `CA-CM-013` | El catálogo se ordena por código de producto (15-09-2026), código de rol, **luego por forma**, y dentro de cada forma de mayor a menor valor; la respuesta publica `product.code,asc;role.code,asc` |
| `CA-CM-014` | El catálogo **no admite** filtro por fecha: las tasas de rol no tienen vigencia |
| `CA-CM-015` | El listado de personalizadas devuelve el **historial** completo de una persona |
| `CA-CM-016` | Filtrando por fecha, devuelve solo la que regía ese día |
| `CA-CM-017` | Las personalizadas se ordenan del inicio de vigencia más reciente al más antiguo |
| `CA-CM-018` | Las asociaciones de una tasa devuelven el producto resuelto y el valor |
| `CA-CM-019` | Las asociaciones de un producto devuelven **una entrada por rol** que cobra por él |
| `CA-CM-020` | Las cuatro lecturas exigen el permiso de lectura de comisiones |
| `CA-CM-096` | Las cuatro lecturas devuelven **la forma junto al valor**, y el valor de la otra forma **vacío, no omitido** |
| `CA-CM-097` | El filtro por forma devuelve solo esa forma, y **ausente no filtra nada** |
| `CA-CM-098` | **Un catálogo con las dos formas no se ordena por la cifra**: los importes fijos no se intercalan entre los porcentajes |
| `CA-CM-099` | La lectura por producto devuelve **la forma de cada rol**, de modo que las cifras no se puedan comparar por error |
| `CA-CM-126` | El listado de personalizadas **filtra por producto**: devuelve las asociadas a él, de cualquier persona, y **se combina** con persona y fecha |
| `CA-CM-127` | Cada personalizada declara **sobre cuántos productos rige**, el cero significa que no paga nada, y **la cuenta no multiplica las filas**: una tasa con dos asociaciones aparece **una vez** y el total cuadra |
| `CA-CM-128` | Las asociaciones de una personalizada devuelven **sus productos resueltos**, por código, **con la misma forma** que devuelve asociar |
| `CA-CM-129` | La quinta lectura exige el permiso de lectura de comisiones, y **un identificador que no es de nada devuelve la colección vacía**, como las otras dos direcciones |
| `CA-CM-141` | Cada tasa de rol del listado trae **su producto** —`id`, `code`, `name`— y el filtro `productId` devuelve solo las de ese producto; `GET /commission-rates/{id}/products` **ya no existe** (`404` de ruta) |
| `CA-CM-145` | El producto de cada tasa de rol trae **su precio** y **su moneda** —`id`, `code`, `decimalPlaces`—, **sea cual sea la forma de la tasa**; y la misma forma la devuelven el alta y la corrección |

!!! danger "`CA-CM-098` es el criterio que delata la implementación perezosa, y hay que construirlo para que falle"

    El caso tiene que mezclar formas **con cifras que se crucen**: un `AGENTE` con `50 %` y otro `AGENTE` con `100` de importe fijo.

    Ordenando por la cifra a secas, el importe fijo sale **primero** y la lista parece correcta — mayor arriba. Ordenando como decide §6.2, salen **agrupados por forma**, y esa es la única diferencia observable.

    Con cifras que no se crucen —`50 %` y `10` fijos— **las dos implementaciones dan el mismo resultado** y la prueba no verifica nada. El dato de la prueba **es** la prueba.

## 13. Casos límite

- **Una tasa declarada y nunca asociada:** aparece con cero productos. Es el caso que este listado existe para hacer visible, y **el sistema no puede decir si está a medio configurar o mal configurada** — solo puede decir que no rige.
- **Filtrar las personalizadas por una persona sin ninguna:** colección vacía. **No significa que esa persona no cobre**: significa que no tiene excepción, y cobrará lo que su rol tenga asociado. Confundir las dos cosas es el error del que avisa §4.2.
- **Un producto con asociaciones de tres roles:** se devuelven las tres. Es el override de `RN-CM-011` visto desde el producto, y **la suma de esos tres porcentajes puede pasar de cien** sin que nada lo impida.
- **Un producto con tres roles y formas mezcladas:** se devuelven las tres, y **ya no hay ninguna suma que hacer**. «10 %, 15 % y 5.000 fijos» no se suma sin conocer el precio, de modo que este listado —que es donde `RN-CM-011` se veía venir— **no puede enseñarla**. Es una pérdida real y declarada: la vigilancia que nadie hacía tampoco se puede hacer a ojo. Por eso la forma viaja siempre (§6.2).
- **Un catálogo donde el mismo rol tiene `50 %` y `100` de importe fijo:** los dos aparecen, agrupados por forma. **Cuál paga más no lo sabe este listado ni puede saberlo**: depende del precio de cada producto asociado. Ordenarlos entre sí sería inventar una comparación.
- **Filtrar por forma un rol que solo tiene la otra:** colección vacía, como cualquier otro filtro que no encuentra nada (`FA-001`). **No significa que ese rol no cobre.**
- **Las asociaciones no se paginan:** una tasa rige sobre un puñado de productos, y un producto tiene tantas asociaciones como roles vendedores hay en el sistema — un orden de magnitud que fija `SP` y que es pequeño. Paginarlas sería complejidad sin cliente. **Si algún día deja de serlo, la colección viaja envuelta** para que añadir paginación no rompa a nadie.
- **Una tasa retirada con asociaciones vivas:** no puede existir (`RN-CM-015`), y por eso este listado no tiene que decidir cómo mostrarla.
- **Pedir las asociaciones de un identificador que no es de nada:** colección vacía, igual que si no tuviera ninguna. No se comprueba que la tasa o el producto existan, por lo mismo que en `FA-001`. **Vale igual para la personalizada** (`CA-CM-129`).
- **Filtrar las personalizadas por un producto donde nadie tiene excepción:** colección vacía. **No significa que ese producto no comisione**: significa que todo el que lo venda cobra por su rol, y eso lo dice la lectura por producto.
- **Filtrar por persona Y por producto:** cero o una fila viva, porque `RN-CM-006` impide que dos excepciones de la misma persona se solapen en el mismo producto — pero **puede haber varias en el historial**, consecutivas o retiradas. Es el filtro que responde «¿tiene esta persona excepción en este producto?», y responde con el historial, no con un sí o un no.
- **Una personalizada con dos productos aparece UNA vez en el listado**, con `2` en la cuenta. Es la misma trampa que `CA-CM-011` cerró en el catálogo, y se cierra de la misma forma (`plan.md` §5).

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Este es el requerimiento que D-22 puede tener que cambiar.** Se especifica con **alcance global explícito** —quien tiene el permiso, ve todas—, y el día que se decida quién puede ver las comisiones de quién, el filtro que haga falta entra aquí y no en los demás.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial. | Responsable técnico |
| 0.2.0 | 02-09-2026 | **Reescrita sobre el modelo de `cm.md` v0.4.0**, y después de construirse el código. El requerimiento pasa de **un listado sobre una tabla a cuatro lecturas sobre tres**, y §2 argumenta por qué sigue siendo **un solo requerimiento**. El catálogo pierde los filtros por producto y por persona —esas columnas ya no existen— y **pierde el filtro por fecha**, porque las tasas de rol no tienen vigencia. Entra el cambio de fondo: cada tasa declara **sobre cuántos productos rige**, y §5 dice para qué — `RN-CM-012` **no produce ningún error en ningún sitio y solo se ve aquí**. | Responsable técnico |
| 0.3.0 | 02-09-2026 | **Entra el valor fijo** (`cm.md` v0.7.0), antes del código. En una consulta parecería un campo más, y **rompe una cosa que no se ve**: el orden. Ordenar por la cifra a secas sería **peor que no ordenar**, y el orden pasa a **rol, forma, y valor dentro de cada forma**. `CA-CM-098` lo comprueba con cifras que **se cruzan** a propósito, porque con cifras que no se crucen las dos implementaciones dan el mismo resultado y la prueba no verificaría nada. Entra el **filtro por forma** en el catálogo. Y §13 recoge una pérdida: la lectura por producto era donde `RN-CM-011` se veía venir sumando porcentajes, y **con formas mezcladas ya no hay suma que hacer**. | Responsable técnico |
| **1.0.0** | 02-09-2026 | **Consolidación: el documento describe lo que existe, y deja de explicarse por contraste con sus versiones anteriores.** No cambia ninguna lectura, ningún filtro, ningún criterio — cambia la voz. Se retira el aviso de cabecera y se reescriben en presente los pasajes que decían «la v0.1.0 tenía un listado sobre una tabla» o «hasta la v0.2.0 el orden era otro»: quien lea esto no ha visto aquellas versiones, y lo que necesita saber es **por qué el orden no puede ser de mayor a menor**, que es lo que §6.2 dice ahora sin citar ninguna. **La deuda no se borra**: las tres filas anteriores conservan el registro de que la `0.2.0` se escribió después del código. | Responsable técnico |
| 1.1.0 | 12-09-2026 | **La asociación de la personalizada se puede LEER.** El 11-09-2026 `RN-CM-014` se invirtió y la tasa personalizada pasó a asociarse a productos con el mismo mecanismo que la de rol — con sus dos escrituras y **sin ninguna lectura**. Lo destapó el responsable del proyecto al preguntar si existía un endpoint para consultar las comisiones personalizadas por producto. Entran tres cosas y las tres calcan lo que la de rol ya tenía: el listado de personalizadas **filtra por producto** —«quién tiene excepción aquí», combinable con persona y fecha— y **cuenta los productos asociados** de cada fila —el cero vuelve a significar «no paga nada», ahora también para la excepción—; y nace la **quinta lectura**, los productos de una personalizada, **con la misma forma** que devuelven asociar y desasociar. **Se descarta a propósito** meter las personalizadas en la lectura por producto: mezclar filas con rol y filas con persona en una colección exige un discriminador que nadie necesita, y el filtro del listado responde lo mismo paginado. Nacen `CA-CM-126` a `CA-CM-129`; §6.2 deja de decir que la personalizada «no lleva producto», que dejó de ser cierto el día anterior. | Responsable del proyecto |
| 1.2.0 | 15-09-2026 | **La tasa de rol trae su producto** (`RN-CM-021`, [`requirements/cm.md`](../../../requirements/cm.md) v0.14.0 §5.4): el listado deja de contar «productos asociados» y pasa a **decir cuál es**, y gana el filtro `productId` — lo que el responsable del proyecto pidió leer es **todas las comisiones que se han configurado**, en una sola lista. La lectura «los productos de una tasa de rol» se retira con `RF-CM-007`; `GET /product-commission-rates?productId=` se conserva, y devuelve solo las **vivas**. **El orden gana el producto delante del rol** (`CA-CM-013`). `CA-CM-141`. | Responsable del proyecto |
| 1.3.0 | 15-09-2026 | **El producto trae su precio y su moneda**, a petición del responsable del proyecto: un porcentaje es una parte del precio y un importe fijo es dinero en la moneda del producto, y sin los dos la cifra de la fila no dice cuánto es. Van dentro de `product` —`price`, y `currency` con `id`, `code` y `decimalPlaces`, la misma forma que `PM` publica en sus fichas—, **siempre**, sea cual sea la forma. El alta y la corrección devuelven lo mismo. Sin cambio de esquema; la lectura por producto (`GET /product-commission-rates`) **no cambia**. `CA-CM-145`. | Responsable del proyecto |
