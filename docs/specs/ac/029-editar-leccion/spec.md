# SPEC — `RF-AC-029` Editar lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-029` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Corregir lo que se declaró de una lección —**incluido el tipo y el contenido**— sin tocar lo que no se pidió.

## 2. Contexto

Es `RF-AC-023` con lo que la lección tiene y el módulo no: **el tipo, el contenido, la duración y `open`**. Lo que este requerimiento decide está en `RN-AC-016`: **el tipo se corrige, y el contenido resultante tiene que casar con el tipo resultante**. Pasar a `VIDEO` con un Markdown guardado y sin URL en la petición **se rechaza sin aplicar nada**; pasar a `TEXTO` con una URL guardada se admite —una URL es un texto—, y es raro pero no es un error. **El contenido se vacía con nulo explícito aunque la lección esté activa**: `RN-AC-009` rige al activar, y una lección activa sin contenido sigue activa y **deja de ofrecerse** hasta que alguien la reponga o la desactive — es el mismo trato que el curso da a sus descripciones desde el 18-09-2026 (§14.1).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Corrige la lección |

## 4. Alcance

### 4.1 Incluye

- Corregir **tipo, título, descripción, contenido, duración, orden y `open`**, por separado o juntos.
- Vaciar la descripción y el contenido con nulo explícito.
- Validar el contenido resultante contra el tipo resultante.
- Devolver la lección **con su contenido**.

### 4.2 No incluye

- **El módulo.** `RN-AC-019`.
- **El estado.** `RF-AC-030`.
- **Corregir una retirada.**

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-001` | El título nuevo no lo usa otra lección viva del módulo | `requirements/ac.md` §5.1 |
| `RN-AC-002` | El orden es un entero ≥ 0 dentro del módulo | `requirements/ac.md` §5.1 |
| `RN-AC-014` | `open` se corrige en cualquier momento, en los dos sentidos | `requirements/ac.md` §5.1 |
| `RN-AC-016` | El tipo se corrige y el contenido se valida contra el tipo resultante; el contenido se vacía | `requirements/ac.md` §5.1 |
| `RN-AC-017` | La duración es un entero mayor que cero | `requirements/ac.md` §5.1 |
| `RN-AC-018`, `RN-AC-019` | Lo retirado no se corrige; la lección no cambia de módulo | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso, módulo y lección | Sí | Cuál | Ruta. Lección **viva** de ese módulo de ese curso |
| `type` | No | Tipo nuevo | `VIDEO` o `TEXTO`; **no admite nulo** |
| `title` | No | Título nuevo | Hasta 150; único en el módulo; **no admite nulo** |
| `description` | No | Descripción nueva | Hasta 1000; **nulo explícito la vacía** |
| `content` | No | Contenido nuevo | Contra el tipo resultante; **nulo explícito lo vacía** |
| `durationSeconds` | No | Duración nueva | Entero > 0; **no admite nulo** |
| `displayOrder` | No | Orden nuevo | Entero ≥ 0; **no admite nulo** |
| `open` | No | Demostración | Booleano; **no admite nulo** |

**Al menos uno de los siete.** **La pareja `(tipo, contenido)` resultante** —lo que venga más lo que ya había— es lo que se valida.

### 6.2 Salida

`200` con la lección entera, contenido incluido, `updatedAt` avanzado solo si algo cambió.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; lección viva de la ruta; si viene título, libre entre las otras vivas del módulo; el contenido resultante casa con el tipo resultante.

**Postcondiciones:** la fila refleja los campos corregidos; fila `UPDATE` con antes y después —**el contenido audita su longitud y que cambió, no el texto**—, o ninguna.

## 8. Flujo principal

1. Llega la petición con uno o más campos.
2. El sistema valida la forma de lo que viene, **juntos**, y que venga al menos uno (§11).
3. El sistema resuelve la lección **viva** del módulo y curso de la ruta, bloqueándola (`EX-002`).
4. El sistema compone la pareja `(tipo, contenido)` resultante y la valida (`VAL-004`), **antes de aplicar nada**.
5. Si viene el título, comprueba que no lo usa **otra** viva del módulo (`EX-001`).
6. Aplica, escribe y audita si algo cambió; devuelve `200`.

## 9. Flujos alternativos

### FA-001 — Cambiar a `VIDEO` trayendo la URL en la misma petición

**Comportamiento:** se admite: la pareja resultante es `(VIDEO, url)`.

### FA-002 — Cambiar a `TEXTO` sin tocar el contenido

**Comportamiento:** se admite: la URL guardada pasa a ser el texto de la lección, que es lo que se pidió. Administración lo verá en la respuesta.

### FA-003 — Vaciar el contenido de una lección `ACTIVA`

**Comportamiento:** se admite. Sigue `ACTIVA` y **no se ofrece**: el aula no la enseña, el módulo que solo la tenía a ella deja de ofrecerse y su detalle dice «sin lección activa con contenido» (§14.1).

## 10. Excepciones

### EX-001 — El título ya lo usa otra lección viva del módulo

**Respuesta del sistema:** `409` — *«Ya existe una lección con ese título en este módulo.»*

### EX-003 — No se pudo leer la duración del video

**Respuesta del sistema:** `422` — el de `RF-AC-028` `EX-003`. Solo cuando la corrección **cambia el enlace** de un `VIDEO` o **pasa a `VIDEO`** con enlace, y **no** trae `durationSeconds`. **No se aplica nada.** Desde el 25-09-2026.

### EX-002 — La lección no existe, está retirada o no es de ese módulo y curso

**Respuesta del sistema:** `404` — *«No existe una lección viva con ese identificador en este módulo.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Tipo, título, duración, orden y `open` no admiten vaciarse, y cada uno con su forma | Los de `RF-AC-028`, con «no puede quedar vacío» |
| `VAL-003` | Descripción de hasta 1000 | La descripción no puede exceder 1000 caracteres. |
| `VAL-004` | **La pareja resultante**: si el tipo resultante es `VIDEO` y hay contenido resultante, es un video de YouTube o de Vimeo en una forma reconocida | El de `RF-AC-028` `VAL-006` |
| `VAL-005` | Al menos un campo corregible | Debe informar al menos uno de los campos corregibles. |
| `VAL-006` | Ningún campo desconocido — `moduleId`, `courseId`, `status` | El cuerpo de la petición contiene campos no admitidos. |

`VAL-001` a `VAL-003`, `VAL-005` y `VAL-006` **juntas y antes de consultar**; `VAL-004` después de leer la fila, porque necesita lo que ya había — como `VAL-007` de la vigencia del paquete.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-099` | El sistema corrige los siete campos por separado y juntos, y devuelve la lección con su contenido y `updatedAt` avanzado |
| `CA-AC-100` | **`RN-AC-016` en la corrección**: pasar a `VIDEO` con un texto guardado y sin URL en la petición responde `400` **sin aplicar nada** —ni el título que venía junto—; pasar a `VIDEO` con la URL en la misma petición se admite; pasar a `TEXTO` con una URL guardada se admite y la respuesta la trae como contenido |
| `CA-AC-101` | El nulo explícito **vacía** la descripción y el contenido —también en una lección `ACTIVA`, que sigue `ACTIVA`— y **se rechaza** en tipo, título, duración, orden y `open`, juntos |
| `CA-AC-102` | El sistema rechaza con `400` un cuerpo vacío y uno con `moduleId`, `courseId` o `status`; con `409` un título de **otra** viva del módulo; con `404` una lección retirada, inexistente o **de otro módulo o curso** |
| `CA-AC-103` | Un cuerpo sin cambios responde `200` sin auditar; uno con cambios deja la fila `UPDATE` con solo lo que cambió, y **el contenido se audita como longitud y no como texto** |
| `CA-AC-104` | Cambiar `open` en los dos sentidos se aplica y se audita; **cambiar la duración cambia la del módulo y la del curso** en su siguiente lectura si la lección está activa |
| `CA-AC-236` | **Desde el 25-09-2026**: corregir el enlace de un `VIDEO`, o pasar a `VIDEO` con enlace, **sin** `durationSeconds` relee la duración del proveedor; **con** `durationSeconds`, manda la enviada; corregir cualquier otro campo **no consulta** al proveedor; y si la relectura falla responde `422` `EX-003` **sin aplicar nada** |
| `CA-AC-215` | **Enmienda del 18-09-2026**: vaciar el contenido de la única lección activa de un módulo ofrecido deja la lección `ACTIVA`, el módulo `ACTIVO` con `offerable: false` «sin lección activa con contenido» y el curso `offerable: false` por su último motivo; reponerlo devuelve los dos a `offerable: true`; el aula lo comprueba desde `RF-AC-034` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Cambiar a `VIDEO` con `content: null` en la misma petición | Se admite: la pareja resultante es `(VIDEO, nulo)`, una lección de video que se prepara |
| Cambiar a `TEXTO` con un contenido de veinte páginas | Se admite; sin tope corto |
| Reducir la duración a `0` | `400`: mayor que cero |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Vaciar el contenido de una lección activa la desactiva? | **No.** Por lo mismo que las descripciones del curso (`RF-AC-011` §14.1): `RN-AC-009` rige al activar. **Pero deja de ofrecerse** desde el 18-09-2026: el responsable del proyecto decidió que «sin contenido» sea **un motivo más en `RN-AC-015`** —una lección se ofrece si está activa, viva y con contenido, y el módulo cuenta esas—, no una desactivación. Hasta ese día esta spec dejaba escrito que se enseñaba vacía |
| 2 | ¿Cómo lee administración el contenido sin editar? | **Por `RF-AC-036`**, nacido el 18-09-2026 por decisión del responsable del proyecto: un `GET` de lección con `courses:read`, en la misma forma que esta corrección devuelve. Hasta ese día no podía: el detalle del curso y el del módulo no lo traen (`RF-AC-010` §14.1), y esta operación exige al menos un campo |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. Hereda `RF-AC-023` y añade **la pareja `(tipo, contenido)` resultante**, validada antes de aplicar nada, y el vaciado del contenido en cualquier estado. Deja escritos dos huecos para el responsable del proyecto (§14): la lección activa y vacía se enseña vacía, y administración no tiene cómo leer el contenido sin editar. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Enmienda de Art. I.7 (18-09-2026)**: `RN-AC-015` gana dos motivos por decisión del responsable del proyecto —«sin descripción» en el curso y «sin contenido» en la lección—, y **nace `RF-AC-036`**. Los dos huecos de §14 se cierran: la lección activa y vacía **deja de ofrecerse** (`FA-001` reescrito, **`CA-AC-215`** añadido) y administración lee el contenido por el `GET` nuevo. | Responsable técnico |
| 0.3.0 | 19-09-2026 | **Construida** (`LessonUpdateIT` (7), incluido `CA-AC-215`). **Una precisión a `CA-AC-215`**: el quinto motivo del curso **solo se observa con una membresía delante** (`RF-AC-020`); hasta entonces el curso dice el cuarto y es el módulo quien enseña el hueco con `offerable: false`. La carrera sobre el título sale con el código del alta, como en el módulo. | Responsable técnico |
| 0.4.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.10, `RN-AC-017`): **la duración de la lección se guarda en segundos**, y las sumas del módulo y del curso también: `durationSeconds` y `totalDurationSeconds` sustituyen a `durationMinutes` y `totalDurationMinutes` en el cuerpo de esta spec. Las filas anteriores de esta tabla conservan el nombre que tenía el campo en su fecha. | Responsable técnico |
| 0.5.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.11, `RN-AC-005` reescrita): **corregir el enlace de un `VIDEO`, o pasar a `VIDEO`, relee la duración del proveedor** si no viene `durationSeconds`; si viene, manda la enviada. `VAL-004` solo admite YouTube y Vimeo; nace **`EX-003`** y `CA-AC-236`. | Responsable técnico |
| 0.6.0 | 25-09-2026 | **La enmienda de §5.2.11 está construida**: `LessonVideoDurationIT` (`CA-AC-236`). La relectura se decide antes de tocar la lección, de modo que su fallo no aplica nada; un enlace inválido no se relee, lo rechaza `VAL-004` sin llamar a nadie. | Responsable técnico |
