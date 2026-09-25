# SPEC — `RF-AC-035` Consultar el contenido de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-035` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Estudiar: la lección con su **contenido** —la URL si es `VIDEO`, el Markdown si es `TEXTO`— para quien tiene derecho a verlo.

## 2. Contexto

Es **el único sitio donde el contenido de una lección sale hacia un alumno**, y por eso es donde la lista de visibilidad se aplica de verdad. Todo lo anterior —catálogo y detalle— se enseña a cualquiera con sesión y `courses:learn` (`RN-AC-013`); aquí se comprueba: **el contenido se abre a quien tiene VIGENTE una de las membresías del curso, o a cualquiera si la lección está abierta** (`RN-AC-014`). A los demás, **`403` diciendo qué membresías lo abren**, que es la invitación a subir y es lo que §1.4 promete desde el 17-09-2026.

**El orden de las comprobaciones es parte del requerimiento**: primero **si se ofrece** —el curso, y la lección dentro de él, en un módulo ofrecible— y solo después **si se abre**. Una lección cerrada de un curso que no se ofrece es `404` y no `403`, porque para el alumno no existe (`RF-AC-034` §14.1), y responder `403` le diría que hay algo detrás. Y **la lección abierta no exime de ofrecerse** (`RN-AC-014`): la demostración de un curso inactivo no se ve.

**El contenido viaja como se guardó** (`ac.md` §5.2.4): sin convertir, sin sanear, sin recortar. Quien lo pinta es el frontend, y la obligación de hacerlo con un conversor que no ejecute lo que encuentre está escrita allí.

## 3. Actores

| Actor | Papel |
|---|---|
| Alumno | Pide el contenido de una lección |

## 4. Alcance

### 4.1 Incluye

- Devolver una lección **ofrecida** de un curso **ofrecido**, por identificadores de curso y lección, con su contenido y lo demás.
- Comprobar el acceso: membresía vigente en la lista del curso, o lección abierta.
- Responder `403` con **las membresías que abren el curso** cuando no.

### 4.2 No incluye

- **Registrar que se vio.** No hay progreso (`ac.md` §1.3); esta lectura no escribe nada.
- **El árbol ni el curso.** El detalle (`RF-AC-034`) ya los dio; aquí viaja la lección con los identificadores de su módulo y su curso.
- **Convertir, sanear o recortar el contenido.** §5.2.4.
- **La lección para administración.** `RF-AC-036`, con `courses:read` y sin ninguna condición.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-012` | La lista es explícita; se comprueba pertenencia, no nivel | `requirements/ac.md` §5.1 |
| `RN-AC-013` | El contenido se abre por membresía vigente; a los demás, `403` con las que lo abren | `requirements/ac.md` §5.1 |
| `RN-AC-014` | La abierta se abre a cualquiera con sesión, y no exime de ofrecerse | `requirements/ac.md` §5.1 |
| `RN-AC-015` | Solo lo ofrecido: el curso, el módulo y la lección | `requirements/ac.md` §5.1 |
| `RN-AC-016` | El contenido es una URL o un texto, según el tipo, y viaja tal cual | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso | Sí | De cuál | Ruta; UUID |
| Lección | Sí | Cuál | Ruta; UUID. Tiene que ser de un módulo **de ese curso** |

**Sin el módulo en la ruta.** El detalle del alumno ya dio el identificador de la lección, y el módulo no añade nada que el sistema no sepa; en administración (`RF-AC-036`) sí va, porque allí la ruta afirma la pertenencia en los tres niveles como en las escrituras.

### 6.2 Salida

`200` con: `id`, `courseId`, `moduleId`, `type`, `title`, `description` (presente y nula), **`content`**, `durationMinutes`, `displayOrder`, `open`.

**Sin estado, sin `accessible`**: si se devolvió, se ofrece y se abre.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:learn`; curso ofrecido; lección ofrecida dentro de él; acceso por membresía vigente o lección abierta. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llegan los dos identificadores.
2. El sistema valida su forma (`VAL-001`).
3. El sistema resuelve **en una lectura** la lección viva con su módulo vivo y su curso vivo, con las entradas de ofrecibilidad de los tres: si no existe, si el módulo no es de ese curso, o si **cualquiera de los tres no se ofrece**, `EX-001`.
4. Si la lección **está abierta**, devuelve `200`.
5. El sistema pregunta a `SP` la membresía vigente de quien llama y lee las membresías del curso.
6. Si la vigente está en la lista, `200`; si no, `EX-002` con la lista.

**El puerto se pregunta después de la abierta**, y no antes: la demostración no necesita saber quién mira, y ahorrarse la pregunta es gratis. El resultado no cambia.

## 9. Flujos alternativos

### FA-001 — Lección abierta, alumno sin membresía vigente

**Comportamiento:** `200` con el contenido. Es la demostración funcionando para quien está pensada (§1.4).

### FA-002 — Lección cerrada, alumno con una membresía que no está en la lista

**Comportamiento:** `EX-002`. Un alumno de `PLATINO` ante un curso de `ORO` recibe `403` con `ORO` en la lista: la lista no mira niveles (`RN-AC-012`).

### FA-003 — Lección abierta de un curso que no se ofrece

**Comportamiento:** `EX-001`. La abierta no exime de ofrecerse (`RN-AC-014`).

## 10. Excepciones

### EX-001 — La lección no se ofrece

**Respuesta del sistema:** `404` — *«No existe una lección con ese identificador en ese curso.»* — el mismo mensaje si no existe, si es de otro curso, si está inactiva, vacía o retirada, si su módulo no se ofrece o si el curso no se ofrece.

### EX-002 — La membresía vigente no abre el curso

**Respuesta del sistema:** `403` — *«Tu membresía no abre este curso.»* — con el miembro de extensión **`memberships`**: `[{ id, code, name, color }]` de las que lo abren, en el orden de la cadena de `SP` (nivel). Es RFC 9457 §3.2, como el bloqueo de cuenta lleva cuándo se levanta.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-202` | El sistema devuelve el contenido a quien tiene **vigente** una membresía de la lista, **de los dos tipos**: la URL de un `VIDEO` y el Markdown de un `TEXTO` **byte a byte** como se guardó, con un `<script>` dentro incluido |
| `CA-AC-203` | Una lección **abierta** se devuelve a cualquier alumno con `courses:learn`: con una membresía fuera de la lista, **sin vigente** y con una vencida |
| `CA-AC-204` | Una lección **cerrada** responde `403` con `memberships` —identificador, código, nombre y color de las que abren el curso— a quien tiene una membresía **fuera** de la lista, a quien **no tiene** vigente y a quien la tiene **vencida**; `PLATINO` no abre un curso de `ORO` |
| `CA-AC-205` | Responde `404` **con el mismo mensaje** si la lección no existe, es de **otro curso**, está **inactiva**, **vacía** o **retirada**, si su módulo está inactivo o retirado, o si el curso no se ofrece por **cualquiera** de sus cinco motivos; un identificador mal formado, `400` |
| `CA-AC-206` | **`404` antes que `403`**: una lección cerrada de un curso que no se ofrece responde `404` aunque el alumno no tuviera acceso; y una **abierta** de un curso que no se ofrece, `404` |
| `CA-AC-207` | La lectura cuesta **una sentencia** para la abierta y **dos** para la cerrada —más la del puerto—; el `404` cuesta una |
| `CA-AC-208` | Sin `courses:learn` responde `403` **sin `memberships`** aunque el actor porte `courses:read`; el `403` de permiso y el de membresía se distinguen por el código de error |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El curso pierde su última membresía entre el detalle y esta petición | `404`: ya no se ofrece. El frontend vuelve al catálogo |
| La lección se cierra (`open` → falso) entre dos peticiones | La segunda es `403` si la membresía no la abre; nada se guarda de la primera |
| Contenido de veinte páginas | Viaja entero; el único tope es el de la petición (`ac.md` §5.2.4) |
| La membresía de la lista cambia de nombre o color en `SP` | Se enseña como esté hoy: `memberships` se resuelve por `JOIN` de lectura en cada petición, y `SP` no retira membresías (`RN-SP-008`) |
| Un alumno con `courses:read` y `courses:update` y sin `courses:learn` | `403` de permiso: administración lee la lección por `RF-AC-036` |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El `403` lleva las membresías en el cuerpo o solo en el mensaje? | **En el cuerpo**, como miembro de extensión. El frontend tiene que pintar «sube a `ORO`» con su color, y leerlo del mensaje se rompería con el primer retoque de redacción (`DomainException`, Javadoc) |
| 2 | ¿El módulo va en la ruta? | **No** (§6.1). La lección es única y el detalle ya la dio; en administración sí va, porque las escrituras la anidan y la lectura sigue su forma |
| 3 | ¿Se pregunta al puerto antes de saber si la lección está abierta? | **Después.** La abierta no necesita saber quién mira; una sentencia menos por demostración, y el resultado es el mismo |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. El único sitio donde el contenido sale hacia un alumno: **primero si se ofrece, después si se abre** (`404` antes que `403`); la abierta a cualquiera con sesión y sin eximir de ofrecerse; el `403` **con las membresías que abren el curso como miembro de extensión** (§14.1); el contenido tal cual se guardó. Sin módulo en la ruta (§14.2) y el puerto solo para la cerrada (§14.3). | Responsable técnico |
| 0.2.0 | 25-09-2026 | **Enmienda declarada por `RF-AC-037`** (Art. I.7, `RN-AC-013` y `RN-AC-020`; `ac.md` v0.12.0 §5.2.8): el contenido se abre también a quien **tiene vigente uno de los servicios del curso**, y el `403` lleva **las membresías y los servicios** que lo abren. **El cuerpo de esta spec se reescribe al construirla**; hasta entonces, donde dice «membresía» como llave se lee «membresía o servicio». | Responsable técnico |
