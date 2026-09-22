# SPEC — `RF-AC-028` Registrar lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-028` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que exista **algo que estudiar**: una lección de video o de texto dentro de un módulo, con su duración, y —si administración lo decide— **abierta a todos** como demostración.

## 2. Contexto

La lección es la hoja del árbol (`ac.md` §5.2.1): nace dentro de un módulo y no se mueve (`RN-AC-019`), se ordena dentro de él, y es lo único de Academia que tiene **contenido**. El contenido es **una sola cosa** y el tipo dice cómo leerla (`RN-AC-016`): una URL si es `VIDEO`, un Markdown si es `TEXTO`, que el backend **guarda y devuelve sin mirar** (`ac.md` §5.2.4). Es **opcional al registrar** —la lección se prepara— y **obligatorio para activar** (`RN-AC-009`).

**La duración es obligatoria en los dos tipos** (`RN-AC-017`): en video es lo que dura; en texto, el tiempo estimado de lectura que administración declara. **Y la bandera `open`** es la demostración (`RN-AC-014`): falsa por omisión, se abre a cualquier alumno con sesión aunque su membresía no abra el curso. Aquí solo se declara; lo que hace lo decide el aula.

Es el requerimiento que **crea `lessons`** y **cierra `RN-AC-015`**: con lecciones, un módulo puede ser ofrecible y, con él, un curso. Construye las enmiendas que quedaban del bloque 2 —`lessonCount` de `RF-AC-009`, las lecciones en el árbol de `RF-AC-010`, el arrastre de lecciones de `RF-AC-013`— y las de `RF-AC-022` (`lessons` y `durationMinutes` del módulo).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Registra la lección |

## 4. Alcance

### 4.1 Incluye

- Registrar una lección dentro de un módulo vivo con **tipo, título, duración y orden**, obligatorios, y **descripción, contenido y `open`**, opcionales.
- Validar el contenido, si viene, **contra el tipo**.
- Crear `lessons`.
- **Enmendar** `RF-AC-009`, `RF-AC-010`, `RF-AC-013` y `RF-AC-022` con las lecciones.
- Devolver la lección **con su contenido**.

### 4.2 No incluye

- **Activarla.** Nace `INACTIVA` y se publica con `RF-AC-030`, cuando tenga contenido.
- **Interpretar el contenido.** Ni validar Markdown, ni convertir, ni sanear (`ac.md` §5.2.4).
- **Seguir el video.** Se comprueba la forma del enlace y nada más (`RN-AC-005`).
- **Una portada.** La lección se estudia, no se presenta (`ac.md` §8.7).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-001` | El título es único **dentro del módulo**, entre las vivas | `requirements/ac.md` §5.1 |
| `RN-AC-002` | El orden es un entero ≥ 0 **dentro del módulo**, no único | `requirements/ac.md` §5.1 |
| `RN-AC-005` | El contenido de un `VIDEO` es un enlace con la forma de `RN-PM-032` | `requirements/ac.md` §5.1 |
| `RN-AC-008` | La lección nace `INACTIVA` | `requirements/ac.md` §5.1 |
| `RN-AC-014` | `open` es la demostración; falsa por omisión; no exime de nada más | `requirements/ac.md` §5.1 |
| `RN-AC-016` | El tipo manda sobre el contenido; opcional al registrar | `requirements/ac.md` §5.1 |
| `RN-AC-017` | Duración en minutos enteros, mayor que cero, en los dos tipos | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Un módulo retirado no admite lecciones | `requirements/ac.md` §5.1 |
| `RN-AC-019` | La lección no cambia de módulo | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso y módulo | Sí | En cuál nace | Ruta. Módulo **vivo** de ese curso, en cualquier estado |
| Tipo (`type`) | Sí | Qué es | `VIDEO` o `TEXTO`, sin valor por omisión |
| Título (`title`) | Sí | Cómo se llama | Hasta 150; **único entre las lecciones vivas del módulo** |
| Descripción (`description`) | No | De qué va | Hasta 1000; de solo espacios queda nula |
| Contenido (`content`) | No | Lo que se estudia | Si `VIDEO`: URL con la forma de `RN-AC-005`. Si `TEXTO`: texto, **sin tope corto** —hasta el tamaño máximo de una petición— y sin interpretar. De solo espacios queda nulo |
| Duración (`durationMinutes`) | Sí | Cuánto dura, o cuánto se tarda en leer | Entero **mayor que cero** |
| Orden (`displayOrder`) | Sí | En qué lugar del módulo | Entero ≥ 0; no único |
| Abierta (`open`) | No | ¿Demostración? | Booleano; **falsa por omisión** |

**Ni estado, ni módulo, ni curso en el cuerpo.**

### 6.2 Salida

`201` con la lección **entera**: identificador, `moduleId`, `courseId`, tipo, título, descripción y contenido —presentes y nulos si no vinieron—, duración, orden, `open`, estado `INACTIVO`, y las dos fechas. **Es la forma que lleva el contenido hacia administración**: el detalle del curso y el del módulo no lo traen, y la lee sin escribir `RF-AC-036`, que le añade `deletedAt` y `deletionReason` solo cuando la lección está retirada.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; módulo vivo del curso de la ruta; título libre entre las lecciones vivas del módulo.

**Postcondiciones:** existe la fila en `lessons` con `module_id`, `status = INACTIVO` y `open` como se declaró; `audit_change_log` tiene una fila `CREATE` de `lessons`; el módulo y el curso la enseñan en su árbol, `lessonCount` sube en uno y la duración del módulo y del curso **no cambian** —solo suman lecciones activas—.

## 8. Flujo principal

1. Llega la petición con curso y módulo en la ruta, y el cuerpo.
2. El sistema valida la forma de los siete campos, **juntos**, incluido el contenido contra el tipo (§11).
3. El sistema resuelve el módulo **vivo** del curso de la ruta, bloqueándolo: si no existe, está retirado o no es de ese curso, `EX-001`.
4. El sistema comprueba que el **título** no lo usa otra lección viva del módulo (`EX-002`).
5. El sistema inserta la lección en `INACTIVO` y registra la creación.
6. Devuelve `201` con la lección.

**Se bloquea el módulo, no el curso**: es el padre inmediato, y lo que hay que ordenar frente a esta alta es el retiro del módulo (`RF-AC-025`), que a su vez bloquea el suyo. La cadena de bloqueos va de abajo arriba y nunca cruza.

## 9. Flujos alternativos

### FA-001 — Sin contenido

**Comportamiento:** se registra igual. El contenido es lo que `RF-AC-030` exigirá para activar.

### FA-002 — `TEXTO` con lo que parece HTML dentro

**Comportamiento:** se guarda tal cual. El backend no interpreta el contenido (`ac.md` §5.2.4); es el frontend quien tiene que pintarlo sin ejecutarlo, y esa obligación está escrita allí.

### FA-003 — `open: true` en una lección de un curso que no se ofrece

**Comportamiento:** se registra igual. La bandera no exime de nada: la demostración de un curso que no se ofrece no se ve (`RN-AC-014`).

## 10. Excepciones

### EX-001 — El módulo no existe, está retirado o no es de ese curso

**Respuesta del sistema:** `404` — *«No existe un módulo vivo con ese identificador en este curso.»* Un módulo de **otro** curso responde lo mismo: la ruta afirma la pertenencia, y negarla es un `404` y no un `409`.

### EX-002 — El título ya lo usa una lección viva del módulo

**Respuesta del sistema:** `409` — *«Ya existe una lección con ese título en este módulo.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Tipo presente y en el dominio | El tipo es obligatorio y debe ser VIDEO o TEXTO. |
| `VAL-003` | Título presente y de hasta 150 | El título es obligatorio y no puede superar los 150 caracteres. |
| `VAL-004` | Duración presente y mayor que cero | La duración es obligatoria y debe ser un entero de minutos mayor que cero. |
| `VAL-005` | Orden presente y ≥ 0 | El orden es obligatorio y debe ser un entero mayor o igual que cero. |
| `VAL-006` | Contenido de un `VIDEO`, si viene, con la forma de `RN-AC-005` | El contenido de una lección de video debe ser una URL absoluta http o https, sin espacios y de hasta 500 caracteres. |
| `VAL-007` | Descripción de hasta 1000 | La descripción no puede exceder 1000 caracteres. |
| `VAL-008` | Ningún campo desconocido — `status`, `moduleId`, `courseId` | El cuerpo de la petición contiene campos no admitidos. |

Todas **juntas**; `VAL-006` depende de `VAL-002` y solo se evalúa si el tipo es válido.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-085` | El sistema registra la lección con `201`, `INACTIVA`, con `moduleId` y `courseId` de la ruta, `open` **falsa cuando no vino**, y el contenido tal como llegó — un Markdown con `<script>` dentro **se guarda y se devuelve sin tocar** |
| `CA-AC-086` | El sistema rechaza con `409` un título que ya usa una lección viva **del mismo módulo**, y admite el de una retirada y el mismo título en otro módulo |
| `CA-AC-087` | El sistema responde `404` a un módulo inexistente, a uno retirado y a uno **de otro curso** |
| `CA-AC-088` | El sistema rechaza con `400`, **juntos**, el tipo ausente o fuera de dominio, el título ausente, la duración ausente, cero o negativa, el orden negativo y —si el tipo es `VIDEO`— un contenido que no es una URL; un `TEXTO` con cualquier texto se admite |
| `CA-AC-089` | El sistema registra una fila `CREATE` en `audit_change_log` de `lessons` con el actor, **sin el contenido en la instantánea** cuando es `TEXTO` —solo su longitud— |
| `CA-AC-090` | **Enmienda de `RF-AC-009` y `RF-AC-022`**: `lessonCount` del listado y `lessons` y `durationMinutes` del módulo cuentan las lecciones **vivas**, y la duración suma solo las **activas** |
| `CA-AC-091` | **Enmienda de `RF-AC-010`**: el detalle del curso trae las lecciones de cada módulo en orden, con tipo, duración, estado, `open` y marca, **sin contenido**, y cuesta **una sentencia más** cuando hay lecciones |
| `CA-AC-092` | **Enmienda de `RF-AC-013`**: retirar el curso retira las lecciones vivas de sus módulos vivos, con una fila de auditoría cada una |
| `CA-AC-093` | Dos altas simultáneas con el mismo título en el mismo módulo dejan **una** fila y un `409` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| `TEXTO` con contenido de un megabyte | Se admite si cabe en la petición; la columna es `text` (`ac.md` §5.2.4) |
| `VIDEO` con contenido de solo espacios | Queda nulo: se prepara sin video |
| Duración `1` en un `TEXTO` de veinte páginas | Se admite: es lo que administración declaró |
| El módulo está `ACTIVO` y esta es su primera lección | Nace inactiva; el módulo sigue `ACTIVO` y **no ofrecible** hasta que se active la lección — que el módulo estuviera activo sin lecciones solo puede venir de un retiro posterior |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La instantánea de auditoría lleva el Markdown entero? | **No: su longitud.** Un `TEXTO` de veinte páginas en `audit_change_log` por cada alta y cada corrección multiplicaría la auditoría por el tamaño del contenido; y el diff de una corrección —antes y después— lo duplicaría. Se audita `content_length` y, en la corrección, que cambió. El contenido vive en la fila, y quien lo retira lo deja en la instantánea de eliminación, que sí lo lleva entero porque es la última copia (`RF-AC-031`) |
| 2 | ¿Se valida que el Markdown sea Markdown? | **No hay Markdown inválido** (`ac.md` §5.2.4) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. La lección nace dentro del módulo —bloqueando el módulo, no el curso—, inactiva y con contenido opcional que se valida **contra el tipo**; `open` falsa por omisión. **La auditoría de cambios no lleva el Markdown, sino su longitud** (§14.1). Crea `lessons`, **cierra `RN-AC-015`** y construye las enmiendas que quedaban del bloque 2 y de `RF-AC-022`. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Enmienda de Art. I.7 (18-09-2026)**: `RN-AC-015` gana dos motivos por decisión del responsable del proyecto —«sin descripción» en el curso y «sin contenido» en la lección—. `ModuleOfferability` cuenta **lecciones ofrecibles** —activas, vivas y con contenido— y no lecciones activas (`plan.md` §1 y §3, `tasks.md` `T-02`). Y `LessonResponse` es también la respuesta de **`RF-AC-036`**, nacido el mismo día, que le añade los dos campos del retiro con `NON_NULL`. | Responsable técnico |
| 0.3.0 | 19-09-2026 | **Construida** (`V24`, `LessonsIT` (7), la carrera en `CourseTreeConcurrencyIT`, `LessonTest`). **Una precisión**: las validaciones del alta **no van por Bean Validation** sino por el caso de uso, porque `VAL-006` depende del tipo y `CA-AC-088` las exige juntas; el DTO conserva sus anotaciones para el contrato. `LessonResponse` nace con `deletedAt` y `deletionReason` `NON_NULL` para `RF-AC-036`. | Responsable técnico |
