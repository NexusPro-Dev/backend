# SPEC — `RF-AC-034` Consultar el detalle de un curso como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-034` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que el alumno vea **el curso entero antes de entrar** —qué enseña, quién, cuánto dura, qué conviene ver antes— y sepa **qué le abre**: cada lección marcada con si puede pedir su contenido.

## 2. Contexto

Es `RF-AC-010` mirado desde el aula, y la diferencia está toda en **qué se enseña**: administración ve todo con sus estados y marcas para arreglarlo; el alumno ve **solo lo ofrecido** —el curso, sus módulos ofrecibles, sus lecciones ofrecibles— **sin estados**, porque todo lo que ve está activo por definición, y **sin el contenido** de las lecciones, que se pide una a una (`RF-AC-035`) y es lo que la membresía cierra.

Es la pantalla donde la decisión de §1.4 se ve entera: **un curso que la membresía del alumno no abre se enseña completo** —portada, título, instructor, descripciones, video de introducción, categorías, recomendaciones, y la lista de módulos y lecciones con su título, tipo y duración— con **`accessible: false`** en el curso y en cada lección cerrada, y `true` en las abiertas (`RN-AC-013`, `RN-AC-014`). Y **las membresías que lo abren** viajan con código, nombre y color, que es la invitación a subir antes de que el `403` la repita.

**Un curso que no se ofrece es `404`**, con el mismo mensaje que uno inexistente: para el alumno no existe, y distinguir «no existe» de «existe y no se ofrece» le diría que hay algo escondido — es la decisión de `RN-PM-021` con el hotlink. **Las recomendaciones solo si su curso se ofrece** (`RN-AC-011`): una recomendación a un curso retirado o inactivo se conserva y no se ve.

Es el requerimiento que **hace real `CA-AC-075`** de `RF-AC-013` —un curso retirado deja de aparecer como recomendado en el aula— y cierra su bloqueo 2.

## 3. Actores

| Actor | Papel |
|---|---|
| Alumno | Consulta un curso que se le ofrece |

## 4. Alcance

### 4.1 Incluye

- Devolver un curso **ofrecido** por identificador, con todos sus campos públicos, el instructor resuelto y `coverImageUrl`.
- Sus **categorías** vivas; los **cursos que recomienda y que se ofrecen** —identificador, título, dificultad, portada—; **las membresías que lo abren** —identificador, código, nombre, color—.
- **El árbol ofrecido**: módulos ofrecibles en su orden, con portada, descripciones, video de presentación y duración; sus lecciones ofrecibles en su orden, con tipo, título, descripción, duración, `open` y **`accessible`**, **sin el contenido**.
- `accessible` del curso, `currentMembership`, `totalDurationSeconds` y `lessonCount` sobre lo ofrecido.

### 4.2 No incluye

- **Lo no ofrecido**: ni el curso, ni un módulo inactivo o sin lección ofrecible, ni una lección inactiva, vacía o retirada. Ni marcados: **no aparecen**.
- **Estados, marcas de retiro, `offerable` y `offerableReason`.** Todo lo que el alumno ve está activo y ofrecido; el motivo es de administración.
- **El contenido de las lecciones.** `RF-AC-035`, una a una y con su comprobación.
- **Un `accessible` por curso recomendado.** Es una lista de invitaciones; su detalle lo dice.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-002` | Módulos y lecciones en su orden, con desempate por identificador | `requirements/ac.md` §5.1 |
| `RN-AC-004`, `RN-AC-005` | `coverImageUrl` y los videos presentes y nulos | `requirements/ac.md` §5.1 |
| `RN-AC-010`, `RN-AC-011`, `RN-AC-012` | Las tres relaciones: categorías vivas, recomendaciones ofrecidas, membresías que abren | `requirements/ac.md` §5.1 |
| `RN-AC-013` | El curso se ve entero con sesión; `accessible` en el curso y en cada lección | `requirements/ac.md` §5.1 |
| `RN-AC-014` | La lección abierta es accesible para cualquiera con sesión | `requirements/ac.md` §5.1 |
| `RN-AC-015` | Solo lo ofrecido: curso, módulos y lecciones, con los mismos objetos que administración | `requirements/ac.md` §5.1 |
| `RN-AC-017` | Duración del curso y de cada módulo sobre lo ofrecido | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál | Ruta; UUID |

### 6.2 Salida

`200` con: `id`, `title`, `instructor { id, username, fullName }`, `difficulty`, `shortDescription`, `longDescription`, `introVideoUrl` (presente y nulo), `displayOrder`, `coverImageUrl` (presente y nula), `categories [{ id, name, color, icon }]`, `recommendedCourses [{ id, title, difficulty, coverImageUrl }]`, `memberships [{ id, code, name, color }]`, `currentMembership` (presente y nula), `accessible`, `modules [{ id, title, shortDescription, longDescription, presentationVideoUrl, displayOrder, coverImageUrl, durationSeconds, lessons [{ id, type, title, description, durationSeconds, displayOrder, open, accessible }] }]`, `totalDurationSeconds`, `lessonCount`.

**`accessible` de la lección es «el curso es accesible o la lección está abierta»** (`StudentAccess`, `RF-AC-033`). **Sin estados ni `offerable`**: todo lo que viaja se ofrece.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:learn`; curso ofrecido. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llega el identificador.
2. El sistema valida su forma (`VAL-001`).
3. El sistema pregunta a `SP` la membresía vigente de quien llama.
4. El sistema resuelve el curso **vivo** con su instructor y las entradas de `CourseOfferability`: si no existe o **no se ofrece**, `EX-001`.
5. El sistema resuelve categorías, recomendados **con sus entradas de ofrecibilidad**, membresías, módulos vivos y lecciones vivas; **descarta** los recomendados, módulos y lecciones que no se ofrecen; suma y cuenta sobre lo que queda; decide `accessible`.
6. Devuelve `200`.

## 9. Flujos alternativos

### FA-001 — El curso se ofrece y la membresía del alumno no lo abre

**Comportamiento:** `200` con el curso entero, `accessible: false`, cada lección cerrada con `false` y cada abierta con `true`, y `memberships` diciendo qué lo abre. Es §1.4 aplicada.

### FA-002 — Un módulo ofrecible con una lección inactiva o vacía entre las activas

**Comportamiento:** la lección **no aparece**; el módulo sí, con las que se ofrecen, y su duración no la suma.

### FA-003 — Una recomendación a un curso que no se ofrece

**Comportamiento:** no aparece en `recommendedCourses`; las demás sí. La fila se conserva (`RN-AC-011`).

## 10. Excepciones

### EX-001 — El curso no se ofrece

**Respuesta del sistema:** `404` — *«No existe un curso con ese identificador.»* — el mismo mensaje para inexistente, retirado, inactivo y activo al que le falta algo.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-195` | El sistema devuelve el curso ofrecido con sus campos, el instructor resuelto, `coverImageUrl` y los videos presentes y nulos, las categorías vivas, las membresías con código, nombre y color, el árbol ofrecido en orden y las sumas, **sin estados, sin `offerable` y sin contenido de lecciones** |
| `CA-AC-196` | Un curso **inexistente, retirado, inactivo o activo al que le falta algo** responde `404` **con el mismo mensaje**; un identificador mal formado, `400` |
| `CA-AC-197` | **El árbol es solo lo ofrecido**: un módulo inactivo, retirado o sin lección ofrecible **no aparece**; una lección inactiva, vacía o retirada **no aparece**; `durationSeconds` del módulo y `totalDurationSeconds` y `lessonCount` del curso cuentan solo lo que aparece |
| `CA-AC-198` | `accessible` del curso es verdadero si la vigente está en su lista y falso si no; el de cada lección es **el del curso o `open`**; sin vigente, solo las abiertas son accesibles y `currentMembership` es nula |
| `CA-AC-199` | `recommendedCourses` trae **solo los que se ofrecen**: uno retirado, inactivo o sin membresías **no aparece** aunque la fila exista (**hace real `CA-AC-075`**) |
| `CA-AC-200` | La lectura cuesta **seis sentencias fijas** —curso con instructor, categorías, recomendados, membresías, módulos, lecciones— **más las del puerto**; sin módulos, la de lecciones no se ejecuta |
| `CA-AC-201` | Sin `courses:learn` responde `403` aunque el actor porte `courses:read` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El curso se retira entre el catálogo y el detalle | `404` en el detalle; el frontend vuelve al catálogo |
| Un curso ofrecido cuyo instructor se retiró de `SP` | Con su nombre actual y sin marca |
| Un alumno con `courses:read` y sin `courses:learn` | `403`: el detalle de administración está en `/courses/{id}` y este no lo sustituye |
| Un módulo ofrecible con **cero** lecciones accesibles para este alumno | Aparece con sus lecciones todas `accessible: false`: la lista de visibilidad cierra, no esconde |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El `404` de «no se ofrece» dice que existe? | **No.** El mismo mensaje que inexistente (`RN-PM-021` como precedente): decir «existe y no se ofrece» le diría al alumno que hay algo escondido, y no tiene cómo arreglarlo |
| 2 | ¿Se reutiliza `CourseDetailReader` filtrando su resultado? | **No.** Devuelve la forma de administración con estados y motivo de retiro, lee la auditoría si está retirado y no conoce la vigente. Se reutilizan **sus lecturas del repositorio** y los mismos objetos de ofrecibilidad, que es lo que garantiza la concordancia (`plan.md` §1) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-AC-010` desde el aula: solo lo ofrecido, sin estados, sin contenido; el curso que la membresía no abre se enseña entero con `accessible: false` y `memberships` como invitación (§1.4); `404` con el mismo mensaje que inexistente (§14.1); recomendaciones solo si se ofrecen, **haciendo real `CA-AC-075`**. Reutiliza las lecturas y los objetos de `RF-AC-010` sin reutilizar su lector (§14.2). | Responsable técnico |
| 0.2.0 | 25-09-2026 | **Enmienda declarada por `RF-AC-037`** (Art. I.7, `RN-AC-013` y `RN-AC-020`; `ac.md` v0.12.0 §5.2.8): el detalle trae **`products`** —los servicios que abren el curso, con código y nombre— junto a `memberships`, y `accessible` cuenta también el servicio vigente. **El cuerpo de esta spec se reescribe al construirla**; hasta entonces, donde dice «membresía» como llave se lee «membresía o servicio». | Responsable técnico |
| 0.3.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.10, `RN-AC-017`): **la duración de la lección se guarda en segundos**, y las sumas del módulo y del curso también: `durationSeconds` y `totalDurationSeconds` sustituyen a `durationMinutes` y `totalDurationMinutes` en el cuerpo de esta spec. Las filas anteriores de esta tabla conservan el nombre que tenía el campo en su fecha. | Responsable técnico |
| 0.4.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.12, `RN-AC-015` reescrita): **un curso sin membresías ni servicios es de todos** y las llaves dejan de ser motivo de la ofrecibilidad, que queda en **cuatro** —retirado, inactivo, sin descripción, sin módulo ofrecible—. **En el aula, un curso sin llaves es `accessible` para todo alumno con sesión** y sus lecciones cerradas se abren a todos. El cuerpo de esta spec se reescribe al construirla. | Responsable técnico |
