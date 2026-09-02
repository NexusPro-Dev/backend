# SPEC — `RF-CM-002` Consultar las tasas de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-002` |
| Módulo | `CM` — Comisiones |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`.

!!! warning "Esta especificación se reescribió después de construirse"

    La v0.1.0 describía **un** listado sobre **una** tabla. El rediseño del 01-09-2026 partió esa tabla en tres, y esta consulta con ella.

---

## 1. Objetivo

Ver **qué comisiones hay declaradas** y, sobre todo, **cuáles de ellas rigen de verdad**.

## 2. Contexto

Es la lectura administrativa del módulo, y **devuelve las tasas tal como se declararon**. No resuelve nada: cuál se aplica a un caso concreto lo responde `RF-CM-005`, y son preguntas distintas.

**Lo que este requerimiento tiene que resolver, y la v0.1.0 no tenía que resolver, es que ahora hay tres cosas que mirar y no una.** El catálogo por rol, las excepciones por persona y las asociaciones son tres tablas con formas distintas: una no tiene vigencia, otra sí, y la tercera no tiene ni identificador propio. Un listado único devolvería filas con la mitad de los campos vacíos y obligaría al cliente a deducir de qué tipo es cada una.

**Sigue siendo UN requerimiento y no tres, y conviene decir por qué.** La pregunta es la misma —«qué comisiones hay declaradas»— sobre tres soportes; el permiso es el mismo; y ninguna de las tres decide nada. Partirlo en tres habría multiplicado por tres una especificación cuyo contenido propio cabe en dos párrafos, y habría escondido lo único que de verdad hay que entender: **que las tres juntas responden a la pregunta y ninguna sola la responde**.

**Y hay algo que solo se ve mirando las tres a la vez.** Una tasa de rol puede aparecer en el catálogo con su porcentaje y **no rige sobre nada**. Es la consecuencia de `RN-CM-012`, y es invisible desde el catálogo si el catálogo no lo dice. Por eso cada tasa de rol declara **sobre cuántos productos rige**: sin ese número, el listado sería idéntico para una tasa que paga y para una que no.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Revisa las comisiones declaradas, busca una para corregirla, y comprueba **qué se está pagando de verdad** |
| Administrador | Comprueba, antes de retirar una tasa, sobre qué productos rige (`RN-CM-015`) |

## 4. Alcance

### 4.1 Incluye

Cuatro lecturas que responden a la misma pregunta desde cuatro sitios:

| Lectura | Responde a |
|---|---|
| **El catálogo por rol** | Qué tasas hay declaradas, para qué rol, y **sobre cuántos productos rige cada una** |
| **Las tasas personalizadas** | Qué excepciones por persona hay, incluido su **historial** |
| **Las asociaciones de una tasa** | Sobre qué productos rige esta tasa concreta |
| **Las asociaciones de un producto** | **Qué paga este producto, y a qué rol** |

Además: filtrar, paginar los dos primeros, marcar las retiradas sin excluirlas, y devolver el orden aplicado.

### 4.2 No incluye

- **Resolver cuál se aplica a una persona.** Es `RF-CM-005`, y es otra pregunta. La diferencia importa: filtrar las personalizadas por persona devuelve **las declaradas para ella**, no la que **le aplica** hoy sobre un producto.
- **El motivo del retiro** de una tasa retirada. Uno a uno es una consulta legítima; en bloque sería una exportación de decisiones comerciales. Mismo criterio que `RF-PM-002` aplicó al catálogo.
- **Filtrar el catálogo por fecha.** Las tasas de rol **no tienen vigencia**: no hay una respuesta a «qué regía el mes pasado». Solo las personalizadas admiten ese filtro.
- **Paginar las asociaciones.** Una tasa rige sobre un puñado de productos y un producto tiene tantas asociaciones como roles vendedores hay. Ver §13.
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
| Incluir retiradas | No | Si se piden también las retiradas | Por omisión, **no** se incluyen |
| Página y tamaño | No | Paginación | Los límites del sistema |

**De las tasas personalizadas**

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Persona | No | Filtra las tasas de una persona | Devuelve **las declaradas para ella**, no la que le aplica |
| Las que rigen en una fecha | No | Devuelve únicamente las vigentes ese día | Una fecha |
| Incluir retiradas | No | Si se piden también las retiradas | Por omisión, **no** |
| Página y tamaño | No | Paginación | Los límites del sistema |

**No hay filtro «solo las vigentes» como interruptor aparte:** es el filtro por fecha con la de hoy. Un interruptor y una fecha podrían contradecirse, y esa contradicción no la detecta nada.

**De las asociaciones**

| Dato | Obligatorio | Descripción |
|---|---|---|
| Tasa **o** producto | Sí | Cuál de las dos direcciones se pregunta |

### 6.2 Salida

**Del catálogo por rol**

| Dato | Descripción |
|---|---|
| Tasas | De cada una: su identificador y su porcentaje |
| Rol resuelto | Código y nombre del rol |
| **Productos asociados** | Cuántos hacen que esa tasa rija. **El cero significa que no paga nada a nadie** |
| Marca de retiro | En las retiradas, que lo están y desde cuándo |
| Total y orden | Cuántas cumplen el filtro, y sobre qué se está paginando |

**De las tasas personalizadas**

| Dato | Descripción |
|---|---|
| Tasas | Identificador, porcentaje y **vigencia** |
| Persona resuelta | Nombre de usuario y nombre |
| Marca de retiro, total y orden | Como en el catálogo |

**No llevan rol ni producto**, y no es que falten: una tasa personalizada no tiene ninguno de los dos.

**De las asociaciones**

| Dato | Descripción |
|---|---|
| Producto resuelto | Código y nombre |
| Rol resuelto | Código y nombre |
| Porcentaje | **El de la tasa**, ya resuelto |
| Tasa | Su identificador, para poder desasociarla |

**El porcentaje viaja aquí aunque sea de la tasa y no de la asociación**, y es lo que hace útil la lectura por producto: «qué paga este producto a cada rol» se responde de un vistazo, sin cruzar con el catálogo.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de comisiones.

**Postcondiciones**

- Ninguna. No cambia nada.

## 8. Flujo principal

1. El actor pide una de las cuatro lecturas, con sus filtros.
2. El sistema aplica los filtros y, en las dos paginadas, la paginación.
3. El sistema resuelve los datos de otros módulos —rol, persona, producto— y los devuelve junto a cada fila.
4. El sistema devuelve la colección, y en las paginadas el total y el orden aplicado.

## 9. Flujos alternativos

### FA-001 — El filtro no encuentra nada

**Cuándo ocurre:** ningún registro cumple el filtro, o el identificador filtrado no existe.

1. La colección vuelve **vacía**, con total cero. **No es un error.**
2. Es válido tanto para un rol sin tasas como para un identificador que no corresponde a nada: distinguirlos costaría una consulta a otro módulo **para no cambiar lo que el cliente hace después**.

### FA-002 — Una tasa que no rige sobre nada

**Cuándo ocurre:** una tasa de rol sin ninguna asociación.

1. Aparece en el catálogo con su porcentaje y con **cero productos asociados**.
2. **Es la respuesta correcta y es lo que hay que saber ver**: esa tasa está declarada y no paga nada a nadie.

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
| `CA-CM-013` | El catálogo se ordena por código de rol, y dentro de cada rol de mayor a menor porcentaje |
| `CA-CM-014` | El catálogo **no admite** filtro por fecha: las tasas de rol no tienen vigencia |
| `CA-CM-015` | El listado de personalizadas devuelve el **historial** completo de una persona |
| `CA-CM-016` | Filtrando por fecha, devuelve solo la que regía ese día |
| `CA-CM-017` | Las personalizadas se ordenan del inicio de vigencia más reciente al más antiguo |
| `CA-CM-018` | Las asociaciones de una tasa devuelven el producto resuelto y el porcentaje |
| `CA-CM-019` | Las asociaciones de un producto devuelven **una entrada por rol** que cobra por él |
| `CA-CM-020` | Las cuatro lecturas exigen el permiso de lectura de comisiones |

## 13. Casos límite

- **Una tasa declarada y nunca asociada:** aparece con cero productos. Es el caso que este listado existe para hacer visible, y **el sistema no puede decir si está a medio configurar o mal configurada** — solo puede decir que no rige.
- **Filtrar las personalizadas por una persona sin ninguna:** colección vacía. **No significa que esa persona no cobre**: significa que no tiene excepción, y cobrará lo que su rol tenga asociado. Confundir las dos cosas es el error que `§4.2` avisa.
- **Un producto con asociaciones de tres roles:** se devuelven las tres. Es el override de `RN-CM-011` visto desde el producto, y **la suma de esos tres porcentajes puede pasar de cien** sin que nada lo impida.
- **Las asociaciones no se paginan:** una tasa rige sobre un puñado de productos, y un producto tiene tantas asociaciones como roles vendedores hay en el sistema — un orden de magnitud que fija `SP` y que es pequeño. Paginarlas sería complejidad sin cliente. **Si algún día deja de serlo, la colección viaja envuelta** para que añadir paginación no rompa a nadie.
- **Una tasa retirada con asociaciones vivas:** no puede existir (`RN-CM-015`), y por eso este listado no tiene que decidir cómo mostrarla.
- **Pedir las asociaciones de un identificador que no es de nada:** colección vacía, igual que si no tuviera ninguna. No se comprueba que la tasa o el producto existan, por lo mismo que en `FA-001`.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Este es el requerimiento que D-22 puede tener que cambiar.** Se especifica con **alcance global explícito** —quien tiene el permiso, ve todas—, y el día que se decida quién puede ver las comisiones de quién, el filtro que haga falta entra aquí y no en los demás.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial. | Responsable técnico |
| 0.2.0 | 02-09-2026 | **Reescrita sobre el modelo de `cm.md` v0.4.0**, y después de construirse el código. El requerimiento pasa de **un listado sobre una tabla a cuatro lecturas sobre tres**, y §2 argumenta por qué sigue siendo **un solo requerimiento**: la pregunta es la misma sobre tres soportes, y partirlo escondería lo único que hay que entender — que las tres juntas la responden y ninguna sola. El catálogo **pierde** los filtros por producto y por persona —esas columnas ya no existen— y **pierde el filtro por fecha**, porque las tasas de rol no tienen vigencia; el filtro por fecha sobrevive solo en las personalizadas, que son el único historial que le queda al módulo. Entra el cambio de fondo: cada tasa de rol declara **sobre cuántos productos rige**, y §5 dice para qué — `RN-CM-012` **no produce ningún error en ningún sitio y solo se ve aquí**. Nacen las dos lecturas de la asociación, y §6.2 explica por qué la del producto lleva el porcentaje resuelto. §13 recoge que la suma de los porcentajes de un producto **puede pasar de cien** y nada lo impide. | Responsable técnico |
