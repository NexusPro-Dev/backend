# SPEC — `RF-AC-010` Consultar el detalle de un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-010` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Ver un curso entero —lo suyo, sus relaciones y su árbol de módulos y lecciones— y saber **qué le falta para publicarse**.

## 2. Contexto

Es la vista con la que administración **arma** el curso: desde aquí se ve qué categorías tiene, qué membresías lo abren, qué cursos recomienda, y sus módulos con sus lecciones en orden, con el estado de cada pieza y con `offerable` **también por módulo**. Y es la vista que dice por qué un curso no se ofrece: `offerableReason`, el **primer motivo** en el orden fijo de `RN-AC-015` —retirado, inactivo, sin membresías, sin módulo activo con lección activa—, que es el orden en que hay que arreglarlo.

Hereda de `RF-AC-003` que **se devuelve también un retirado**, con su motivo leído de la auditoría, y de `RF-PM-019` que **es la lectura de administración y devuelve todo**: un módulo inactivo o retirado se devuelve con su estado, porque esta es la única pantalla desde la que se arregla. **Es la misma forma que devuelven el alta y las siete escrituras del curso**: quien acaba de tocarlo ve el resultado sin volver a pedirlo.

**Hoy las cuatro listas y el árbol viajan vacíos.** Cada relación y cada nivel del árbol los estrena su requerimiento (`RF-AC-016`, `RF-AC-018`, `RF-AC-020`, `RF-AC-022`, `RF-AC-028`), y cada uno enmienda esta lectura (Art. I.7). Lo que **sí** se decide hoy y no cambia es la forma y el orden de los motivos.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Consulta un curso |

## 4. Alcance

### 4.1 Incluye

- Devolver el curso por identificador, **vivo o retirado**, con todos sus campos, el instructor resuelto y `coverImageUrl`.
- Sus **categorías** vivas; los **cursos que recomienda** —identificador, título, estado, `offerable`—; las **membresías que lo abren** —identificador, código, nombre, color—.
- Sus **módulos en su orden, cada uno con sus lecciones en su orden**, vivos y retirados marcados, cada módulo con `offerable` y cada lección con tipo, duración, estado y `open`, **sin el contenido**.
- `totalDurationMinutes` y `lessonCount`, sumados en la lectura.
- `offerable` y `offerableReason` del curso.
- Si está retirado: `deletedAt` y el motivo.

### 4.2 No incluye

- **El contenido de las lecciones.** Es del aula (`RF-AC-035`) y de la edición (`RF-AC-029`); aquí viajaría un Markdown de veinte páginas por lección.
- **`offerableReason` por módulo.** El del curso ya nombra el primero; uno a uno es mirar el módulo.
- **Los cursos que lo recomiendan a él** («¿quién me tiene de previo?»). Nadie lo ha pedido; se sabría desde cada uno de ellos.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-002` | Los módulos y las lecciones salen en su orden, con desempate por identificador | `requirements/ac.md` §5.1 |
| `RN-AC-004` | `coverImageUrl` presente y nula, también en un retirado | `requirements/ac.md` §5.1 |
| `RN-AC-010`, `RN-AC-011`, `RN-AC-012` | Las tres relaciones, tal como se declararon; las categorías retiradas no se enseñan | `requirements/ac.md` §5.1 |
| `RN-AC-015` | La ofrecibilidad del curso y de cada módulo se calcula en la lectura, con el primer motivo en orden fijo | `requirements/ac.md` §5.1 |
| `RN-AC-017` | La duración del curso y del módulo es la suma de sus lecciones vivas, en cada lectura | `requirements/ac.md` §5.1 |
| `RN-AC-018` | El retirado se devuelve con su motivo; sus módulos y lecciones retirados con él, marcados | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál | Ruta; UUID |

### 6.2 Salida

`200` con: `id`, `title`, `instructor { id, username, fullName }`, `difficulty`, `shortDescription`, `longDescription`, `introVideoUrl` (presentes y nulos), `displayOrder`, `status`, `coverImageUrl` (presente y nula), `categories [{ id, name, color, icon }]`, `recommendedCourses [{ id, title, status, offerable }]`, `memberships [{ id, code, name, color }]`, `modules [{ id, title, shortDescription, displayOrder, status, deleted, coverImageUrl, offerable, durationMinutes, lessons [{ id, type, title, durationMinutes, displayOrder, status, open, deleted }] }]`, `totalDurationMinutes`, `lessonCount`, `offerable`, `offerableReason`, `createdAt`, `updatedAt`, y **solo si está retirado** `deletedAt` y `deletionReason`.

**`offerableReason` en su orden** (`RN-AC-015`): «El curso está retirado.» → «El curso está inactivo.» → **«El curso no tiene descripción corta o larga.»** → «El curso no tiene ninguna membresía que lo abra.» → «El curso no tiene ningún módulo activo con al menos una lección activa con contenido.» El primero que se cumple. **Hoy un curso `INACTIVO` dice lo segundo y uno `ACTIVO` lo tercero**, porque no hay membresías que darle; es la verdad, y lo seguirá siendo hasta `RF-AC-020`.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:read`. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llega el identificador.
2. El sistema valida su forma (`VAL-001`).
3. El sistema resuelve el curso **en cualquier estado** con su instructor: si no existe, `EX-001`.
4. El sistema resuelve sus relaciones y su árbol, suma duraciones y cuenta lecciones, y decide la ofrecibilidad del curso y de cada módulo.
5. Si está retirado, lee el motivo de la auditoría de eliminación.
6. Devuelve `200`.

## 9. Flujos alternativos

### FA-001 — El curso está retirado

**Comportamiento:** se devuelve con `deletedAt` y `deletionReason`, con sus módulos y lecciones **también retirados y marcados** —los arrastró el retiro (`RN-AC-018`)— y con `offerable: false` diciendo «retirado».

### FA-002 — Un módulo inactivo o retirado dentro de un curso activo

**Comportamiento:** se devuelve con su estado y su marca, y **no cuenta** para la ofrecibilidad del curso ni para la duración total. Es la única pantalla desde la que se arregla.

## 10. Excepciones

### EX-001 — El curso no existe

**Respuesta del sistema:** `404` — *«No existe un curso con ese identificador.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-051` | El sistema devuelve el curso con sus campos, el instructor resuelto, `coverImageUrl` presente y nula, las cuatro listas y el árbol —**vacíos hasta sus requerimientos**—, y `totalDurationMinutes` y `lessonCount` en cero |
| `CA-AC-052` | `offerable` y `offerableReason` viajan **siempre**, con el motivo en su orden: un `INACTIVO` dice «inactivo»; un `ACTIVO` sin membresías dice «sin membresías» |
| `CA-AC-053` | El curso **retirado** se devuelve con `deletedAt`, `deletionReason` y `offerable: false` «retirado»; el **inexistente** responde `404`; un identificador mal formado, `400` |
| `CA-AC-054` | La lectura cuesta **una** sentencia hoy —el curso con su instructor— y **una más** con motivo de retiro; cada requerimiento que llene una lista declara cuántas añade |
| `CA-AC-055` | Sin `courses:read` responde `403` aunque el actor porte `course-categories:read` |
| `CA-AC-056` | El alta y la corrección devuelven **la misma forma** que este detalle, campo a campo |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Curso retirado cuyo registro de eliminación no existe | `deletionReason` nulo y presente, sin `500` |
| Instructor retirado de `SP` | Se devuelve con su nombre actual y sin marca (`RN-AC-006`) |
| Curso recomendado que después se retiró | Aparece en `recommendedCourses` con `status` y `offerable: false`: administración tiene que poder ver qué recomendación quedó colgada; el aula no lo enseña (`RN-AC-011`) |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El detalle trae el contenido de las lecciones? | **No.** Es un Markdown potencialmente largo por lección; la edición lo lee una a una (`RF-AC-029`) y el aula lo sirve con su permiso (`RF-AC-035`) |
| 2 | ¿Se traen los módulos retirados? | **Sí, marcados**, como `PM` devuelve el producto retirado dentro del paquete: es la lectura de administración y devuelve todo |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. La vista con la que se arma el curso: relaciones, árbol con estados y marcas, duración sumada y **`offerableReason` en el orden fijo de `RN-AC-015`**, que nace hoy con los dos motivos que ya se pueden decidir. **Declara cinco enmiendas futuras** (Art. I.7): `RF-AC-016`, `RF-AC-018`, `RF-AC-020`, `RF-AC-022` y `RF-AC-028` llenan cada lista y declaran sus sentencias. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Enmienda de Art. I.7 (18-09-2026)**: `RN-AC-015` gana dos motivos por decisión del responsable del proyecto —«sin descripción» en el curso y «sin contenido» en la lección—. §6.2 lista los **cinco** motivos en su orden; `CA-AC-052` no cambia —un `ACTIVO` tiene las dos descripciones al activarse— y el módulo del árbol pasa a decir «sin lección activa con contenido». | Responsable técnico |
