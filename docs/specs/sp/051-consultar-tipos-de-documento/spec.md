# SPEC — `RF-SP-051` Consultar tipos de documento

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-051` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 08-09-2026 |

---

## 1. Objetivo

Disponer del catálogo de documentos de identidad admitidos, para poder registrar a una persona.

## 2. Contexto

`RN-SP-035` obliga a que toda persona declare **tipo y número de documento**, y el tipo sale de aquí. Sin esta consulta, el formulario de alta no tiene de dónde sacar las opciones y quien registra tendría que conocer identificadores internos de memoria.

**Pero este catálogo no es una lista de opciones, y ahí está todo lo que hay que entender de él.** Se pidió que el sistema validara que sus personas son **mayores de edad**, y la validación **no es una comprobación en ningún caso de uso**: es el **contenido** de esta tabla. El catálogo no ofrece los documentos que identifican a un menor —tarjeta de identidad, registro civil—, de modo que no hay forma de declarar a uno.

La diferencia con la alternativa obvia importa, y conviene verla escrita antes de leer el resto:

- Con una columna del tipo `acredita_mayoría`, registrar a un menor sería **posible y rechazado**. Bastaría con que un caso de uso futuro —una carga masiva, un registro por enlace, un endpoint nuevo— olvidara mirarla para que dejara de rechazarse, y nada fallaría.
- Sin ella, registrar a un menor es **inexpresable**. No hay identificador que poner en `users.document_type_id` que signifique «Tarjeta de Identidad», y `fk_users_document_type` no admite otra cosa. Ningún caso de uso puede olvidar una regla que no tiene que ejecutar.

Es el patrón **inverso** al de `user_roles.role_type` (`RN-SP-025`), y los dos merecen leerse juntos: allí se **trajo** un dato a la tabla para que la regla cupiera en el motor; aquí se **quitó** una opción del catálogo para que la regla no hiciera falta. Los dos acaban igual —la sostiene el esquema y no un `if`— y el segundo es más barato.

**De ahí sale `RN-SP-036`, que en las monedas es una preferencia y aquí es una necesidad.** `RN-SP-010` deja las monedas fuera de la API porque son un catálogo estable que nadie edita; este queda fuera porque **su contenido decide quién puede entrar al sistema**. Un `document-types:create` dejaría que cualquiera con ese permiso añadiera el tipo que falta, y **la validación desaparecería sin cambiar ninguna regla, sin migración y sin que nadie lo notara**.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier rol autenticado con el permiso | Consulta el catálogo para registrar o editar a una persona |

## 4. Alcance

### 4.1 Incluye

- Listado completo de los tipos de documento, con su **nombre**, su **abreviación** y su estado.
- Por defecto solo los activos; los inactivos se piden explícitamente.

### 4.2 No incluye

- Crear, editar o eliminar tipos de documento: el catálogo se puebla por migración (`RN-SP-036`). **No es una omisión de alcance, es la regla** — ver §2.
- Cambiar el estado de un tipo de documento. **Y aquí hay una asimetría deliberada con los otros dos catálogos**: `RF-SP-022` y `RF-SP-023` existen para cambiar `is_active` de un país y de una moneda, y **su equivalente aquí no se registra**. Retirar un tipo de documento se hace con una migración revisada, por el mismo motivo por el que añadirlo no puede ser una llamada.
- Declarar la **fecha de nacimiento** de una persona, ni calcular su edad. Ver §13.
- Asignar el documento a una persona → `RF-SP-024` y `RF-SP-027`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-035` | Toda persona se identifica con un documento, y el tipo sale de este catálogo | `requirements/sp.md` §5.1 |
| `RN-SP-036` | El catálogo de tipos de documento no se administra por API | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Incluir inactivos | No | Incorpora al resultado los tipos retirados de la circulación | Por defecto no |

No se pagina: el catálogo se devuelve completo. Son unos pocos elementos y partirlos obligaría a dos llamadas para pintar un desplegable.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tipos de documento | Identificador, **nombre**, **abreviación** y estado de cada uno |

**El identificador viaja además del nombre y la abreviación**, y no es redundante: es lo que `RF-SP-024` y `RF-SP-027` reciben en su cuerpo. Sin él, el cliente tendría que resolver el tipo por su abreviación en cada alta, que es mezclar dos espacios de identificación en la misma petición.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura del catálogo.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el catálogo de tipos de documento.
2. El sistema recupera los activos, o todos si se pidieron también los inactivos.
3. El sistema devuelve el catálogo completo resultante, ordenado por nombre.

## 9. Flujos alternativos

Ninguno.

## 10. Excepciones

Ninguna propia. Los fallos de autenticación y autorización se resuelven en el borde, como en cualquier endpoint.

## 11. Validaciones

Ninguna. El único parámetro es un indicador opcional que no admite valores inválidos.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-584` | El sistema devuelve el catálogo completo con **identificador, nombre, abreviación y estado** de cada tipo |
| `CA-SP-585` | El sistema **no expone** ninguna operación de creación, edición, eliminación ni cambio de estado sobre este catálogo |
| `CA-SP-586` | Los tipos inactivos **no aparecen** salvo que se soliciten explícitamente |
| `CA-SP-587` | El catálogo **no contiene ningún documento que identifique a un menor de edad** — ni tarjeta de identidad ni registro civil, por abreviación ni por nombre |
| `CA-SP-588` | El catálogo **no está vacío**: contiene al menos un tipo activo, o el alta de personas sería irrealizable |
| `CA-SP-589` | El sistema rechaza la consulta a un actor sin el permiso de lectura del catálogo |

**`CA-SP-587` es el criterio que sostiene la validación entera**, y por eso se escribe como una comprobación sobre el **contenido** y no sobre una respuesta. Es la única prueba del sistema que verifica que algo **no está**, y su valor es exactamente ese: el día que alguien añada «Tarjeta de Identidad» a la migración de siembra, la suite lo dirá — que es lo que sustituye al `if` que este diseño no tiene.

## 13. Casos límite

- **Catálogo vacío:** solo ocurriría si faltara la migración de siembra, y dejaría el alta de personas irrealizable — `RN-SP-035` exige un tipo y no habría ninguno que elegir. `CA-SP-588` lo cubre.
- **Un tipo de documento que ya nadie admite:** se retira con `is_active` en una migración. **No se borra**: `fk_users_document_type` lo impide en cuanto una sola persona lo haya declarado, y quienes ya lo tienen lo siguen resolviendo. Es el mismo trato que `RF-SP-022` da a un país.
- **Persona que solo tiene un documento de menor:** no se registra, y es el comportamiento buscado. La salida no es añadir el tipo al catálogo — eso desactivaría la regla para todo el mundo.
- **Documento que un menor también puede tener:** existe, y es el límite honesto de este diseño. Un pasaporte lo tiene un niño igual. Ver §14, pregunta 2.
- **Dos tipos con el mismo nombre en distinta caja o con acentos distintos:** imposible. La unicidad del nombre va sobre `f_unaccent(lower(name))`, con el mismo criterio que `countries`.

## 14. Preguntas abiertas

Ninguna abierta. Las tres se resolvieron el 08-09-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La mayoría de edad se valida con una columna o con el contenido del catálogo? | **Con el contenido**, por decisión del responsable del proyecto. Una columna `acredita_mayoría` haría que registrar a un menor fuera *posible y rechazado*, y bastaría con que un caso de uso futuro olvidara mirarla; el contenido lo hace **inexpresable**. El razonamiento completo está en §2, y arrastra `RN-SP-036`: el catálogo no puede administrarse por API, porque una fila nueva desactivaría la regla sin cambiar ninguna regla |
| 2 | ¿Basta el tipo de documento para acreditar la mayoría de edad? | **No, y se acepta a conciencia.** El tipo es un **indicio**: una cédula de ciudadanía solo se expide a mayores, pero un pasaporte lo tiene un niño igual. La prueba de verdad exige **fecha de nacimiento**, que no se pide hoy y que arrastra sus propias decisiones —edad mínima por país, qué hacer con quien no la declare—. **La condición para abrirla queda escrita**: en cuanto haya que acreditar una edad concreta y no solo «es adulto», se registra el campo con su propia regla y este catálogo pasa a ser lo que su nombre dice, una lista de tipos. Lo que este diseño compra hoy es que **el camino barato para colar a un menor —declarar su tarjeta de identidad— no existe** |
| 3 | ¿Se registra la abreviación además del nombre? | **Sí, y es el identificador estable de la fila.** El nombre es lo que se muestra y la abreviación es lo que se reconoce —`CC`, `PA`— y lo que cabe en una columna de una tabla. **No hay una tercera columna `code`**: la abreviación *es* el código, y tener las dos daría tres identificadores para un mismo concepto y obligaría a decidir cuál manda. Es la diferencia con `currencies`, que sí separa `code` de `symbol` porque el símbolo no identifica nada |
