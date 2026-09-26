# SPEC — `RF-AC-035` Consultar el contenido de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-035` |
| Módulo | `AC` — Academia |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |

---

## 1. Objetivo

Estudiar: la lección con su **contenido** —la URL si es `VIDEO`, el Markdown si es `TEXTO`— para quien tiene derecho a verlo.

## 2. Contexto

Es **el único sitio donde el contenido de una lección sale hacia un alumno**, y por eso es donde las llaves se aplican de verdad. Todo lo anterior —catálogo y detalle— se enseña a cualquiera con sesión (`RN-AC-013`); aquí se comprueba: **el contenido se abre si la lección está abierta** (`RN-AC-014`), **si el curso no declara llaves** (`ac.md` §5.2.12), **si la membresía vigente de quien pregunta está en la lista del curso** o **si tiene vigente uno de sus servicios** (`RN-AC-020`). A los demás, **`403` diciendo qué membresías y qué servicios lo abren**, que es la invitación.

**El orden de las comprobaciones es parte del requerimiento**: primero **si se ofrece** —el curso, el módulo dentro de él y la lección dentro del módulo— y solo después **si se abre**. Una lección cerrada de un curso que no se ofrece es `404` y no `403`, porque para el alumno no existe; y **la lección abierta no exime de ofrecerse**.

**El contenido viaja como se guardó** (`ac.md` §5.2.4): sin convertir, sin sanear, sin recortar.

**Desde el 26-09-2026 tiene permiso propio**, `lessons:learn` (`ac.md` §5.2.13, `RN-SEG-014`).

## 3. Actores

| Actor | Papel |
|---|---|
| Alumno | Pide el contenido de una lección |

## 4. Alcance

### 4.1 Incluye

- Devolver una lección **ofrecida** de un curso **ofrecido**, por identificadores de curso y lección, con su contenido.
- Comprobar el acceso: lección abierta, curso sin llaves, membresía vigente en la lista o servicio vigente de la lista.
- Responder `403` con **las membresías y los servicios que abren el curso** cuando no.

### 4.2 No incluye

- **Registrar que se vio.** No hay progreso (`ac.md` §1.3).
- **El árbol ni el curso.** El detalle (`RF-AC-034`) ya los dio.
- **Convertir, sanear o recortar el contenido.** §5.2.4.
- **La lección para administración.** `RF-AC-036`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-012`, `RN-AC-020` | Las listas son explícitas; se comprueba pertenencia; sin llaves, de todos | `requirements/ac.md` §5.1 |
| `RN-AC-013` | El contenido se abre por llave; a los demás, `403` con las que lo abren | `requirements/ac.md` §5.1 |
| `RN-AC-014` | La abierta se abre a cualquiera con sesión, y no exime de ofrecerse | `requirements/ac.md` §5.1 |
| `RN-AC-015` | Solo lo ofrecido: el curso, el módulo y la lección | `requirements/ac.md` §5.1 |
| `RN-AC-016` | El contenido es una URL o un texto, según el tipo, y viaja tal cual | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso | Sí | De cuál | Ruta; UUID |
| Lección | Sí | Cuál | Ruta; UUID. Tiene que ser de un módulo **de ese curso** |

**Sin el módulo en la ruta.** El detalle del alumno ya dio el identificador de la lección.

### 6.2 Salida

`200` con: `id`, `courseId`, `moduleId`, `type`, `title`, `description` (presente y nula), **`content`**, `durationSeconds`, `displayOrder`, `open`.

**Sin estado, sin `accessible`**: si se devolvió, se ofrece y se abre.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `lessons:learn`; curso ofrecido; lección ofrecida dentro de él; acceso. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llegan los dos identificadores.
2. El sistema valida su forma (`VAL-001`).
3. El sistema resuelve **en una lectura** la lección con su módulo y su curso, con las entradas de ofrecibilidad de los tres y cuántas llaves declara el curso: si no existe, si no es de ese curso, o si **cualquiera de los tres no se ofrece**, `EX-001`.
4. Si la lección **está abierta** o el curso **no declara llaves**, devuelve `200`.
5. El sistema pregunta a `SP` la membresía y los productos vigentes de quien llama y lee las listas del curso.
6. Si alguna llave abre, `200`; si no, `EX-002` con las listas.

**Los puertos se preguntan solo si hace falta**: la demostración y el curso gratuito no necesitan saber quién mira.

## 9. Flujos alternativos

### FA-001 — Lección abierta, alumno sin llave

**Comportamiento:** `200` con el contenido.

### FA-002 — Lección cerrada, alumno con una membresía que no está en la lista

**Comportamiento:** `EX-002`. `PLATINO` ante un curso de `ORO` recibe `403` con `ORO` en la lista.

### FA-003 — Lección abierta de un curso que no se ofrece

**Comportamiento:** `EX-001`.

### FA-004 — Lección cerrada de un curso sin llaves

**Comportamiento:** `200`: el curso es de todos.

## 10. Excepciones

### EX-001 — La lección no se ofrece

**Respuesta del sistema:** `404` — *«No existe una lección con ese identificador en ese curso.»* — el mismo mensaje si no existe, si es de otro curso, si está inactiva, vacía o retirada, si su módulo no se ofrece o si el curso no se ofrece.

### EX-002 — Ninguna llave abre el curso

**Respuesta del sistema:** `403` — *«Ni tu membresía ni tus servicios abren este curso.»* — con dos miembros de extensión: **`memberships`** `[{ id, code, name, color }]` y **`products`** `[{ id, code, name }]`, las listas del curso. Es RFC 9457 §3.2.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-202` | El sistema devuelve el contenido a quien tiene **vigente** una membresía de la lista, **de los dos tipos**: la URL de un `VIDEO` y el Markdown de un `TEXTO` **byte a byte** como se guardó, con un `<script>` dentro incluido |
| `CA-AC-203` | Una lección **abierta** se devuelve a cualquier alumno con `lessons:learn`: con una membresía fuera de la lista y sin vigente; y una **cerrada de un curso sin llaves**, también |
| `CA-AC-204` | Una lección **cerrada** responde `403` con `memberships` y `products` a quien tiene una membresía **fuera** de la lista y a quien **no tiene** vigente; `PLATINO` no abre un curso de `ORO` |
| `CA-AC-205` | Responde `404` **con el mismo mensaje** si la lección no existe, es de **otro curso**, está **inactiva**, **vacía** o **retirada**, si su módulo está inactivo o retirado, o si el curso no se ofrece; un identificador mal formado, `400` |
| `CA-AC-206` | **`404` antes que `403`**: una lección cerrada de un curso que no se ofrece responde `404`; y una **abierta** de un curso que no se ofrece, `404` |
| `CA-AC-207` | La lectura cuesta **una sentencia** para la abierta, la del curso sin llaves y el `404`, y **tres** para la cerrada —lección, membresías, servicios— más las de los puertos |
| `CA-AC-208` | Sin `lessons:learn` responde `403` **sin `memberships`** aunque el actor porte `courses:read` o `courses:learn`; el `403` de permiso y el de llave se distinguen por el código de error |
| `CA-AC-239` | **Un servicio vigente abre**: la lección cerrada de un curso con un servicio en su lista se devuelve a quien lo tiene vigente, y responde `403` a quien lo tuvo vencido |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El curso pierde sus llaves entre el detalle y esta petición | Se abre a todos: sin llaves es gratuito |
| La lección se cierra (`open` → falso) entre dos peticiones | La segunda es `403` si ninguna llave la abre |
| Contenido de veinte páginas | Viaja entero |
| La membresía de la lista cambia de nombre o color en `SP` | Se enseña como esté hoy: se resuelve por `JOIN` en cada petición |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El `403` lleva las llaves en el cuerpo o solo en el mensaje? | **En el cuerpo**, como miembros de extensión |
| 2 | ¿El módulo va en la ruta? | **No** (§6.1) |
| 3 | ¿Se pregunta a los puertos antes de saber si la lección está abierta? | **Después**, y tampoco si el curso no declara llaves |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. El único sitio donde el contenido sale hacia un alumno: **primero si se ofrece, después si se abre** (`404` antes que `403`); la abierta a cualquiera con sesión y sin eximir de ofrecerse; el `403` **con las membresías que abren el curso como miembro de extensión** (§14.1); el contenido tal cual se guardó. Sin módulo en la ruta (§14.2) y el puerto solo para la cerrada (§14.3). | Responsable técnico |
| 0.2.0 | 25-09-2026 | **Enmienda declarada por `RF-AC-037`** (Art. I.7, `RN-AC-013` y `RN-AC-020`; `ac.md` v0.12.0 §5.2.8): el contenido se abre también a quien **tiene vigente uno de los servicios del curso**, y el `403` lleva **las membresías y los servicios** que lo abren. **El cuerpo de esta spec se reescribe al construirla**; hasta entonces, donde dice «membresía» como llave se lee «membresía o servicio». | Responsable técnico |
| 0.3.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.10, `RN-AC-017`): **la duración de la lección se guarda en segundos**, y las sumas del módulo y del curso también: `durationSeconds` y `totalDurationSeconds` sustituyen a `durationMinutes` y `totalDurationMinutes` en el cuerpo de esta spec. Las filas anteriores de esta tabla conservan el nombre que tenía el campo en su fecha. | Responsable técnico |
| 0.4.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.12, `RN-AC-015` reescrita): **un curso sin membresías ni servicios es de todos** y las llaves dejan de ser motivo de la ofrecibilidad, que queda en **cuatro** —retirado, inactivo, sin descripción, sin módulo ofrecible—. **En el aula, un curso sin llaves es `accessible` para todo alumno con sesión** y sus lecciones cerradas se abren a todos. El cuerpo de esta spec se reescribe al construirla. | Responsable técnico |
| 1.0.0 | 26-09-2026 | **Cuerpo reescrito al construir**, con las enmiendas del 25-09-2026 dentro: el servicio vigente abre (`CA-AC-239`, nuevo), el curso sin llaves es de todos (`FA-004`) y el `403` lleva **`memberships` y `products`**, con el mensaje «Ni tu membresía ni tus servicios abren este curso.». **Permiso propio `lessons:learn`** (`ac.md` v0.20.0 §5.2.13), sembrado por `V47`. La cerrada cuesta **tres** sentencias —la de servicios entra— (`CA-AC-207`). Se retira de `CA-AC-205` «por cualquiera de sus cinco motivos»: son cuatro, y el caso del curso no ofrecido se prueba con uno. | Responsable técnico |
