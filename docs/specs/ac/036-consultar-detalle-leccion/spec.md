# SPEC — `RF-AC-036` Consultar el detalle de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-036` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que administración **lea el contenido de una lección sin editarla**: la lección entera, viva o retirada, con `courses:read`.

## 2. Contexto

Nace el 18-09-2026 por decisión del responsable del proyecto, para cerrar el hueco que `RF-AC-029` §14.2 dejó escrito: el detalle del curso (`RF-AC-010`) y el del módulo no traen el contenido —a propósito, es un Markdown por lección— y la corrección exige al menos un campo, de modo que **administración no tenía cómo ver lo que escribió**. Se eligió **un `GET` de lección con `courses:read`** frente a cargar el detalle del curso con el contenido: la pantalla de edición abre una lección a la vez, y el árbol seguiría pesando lo que pesa.

Es `RF-AC-003` para lecciones: **devuelve también una retirada**, con su fecha y el motivo leído de la auditoría —incluida la que arrastró el retiro de su módulo o de su curso (`RN-AC-018`)—, porque es la lectura de administración y devuelve todo. Y **es la misma forma que devuelven las cuatro escrituras de la lección** (`RF-AC-028`, `029`, `030`, `031` por su `204` aparte): `LessonResponse`, que desde hoy admite `deletedAt` y `deletionReason` cuando los hay.

**El módulo va en la ruta**, como en las escrituras: `GET /courses/{courseId}/modules/{moduleId}/lessons/{lessonId}`, y la ruta **afirma la pertenencia en los tres niveles** —una lección de otro módulo, o de un módulo de otro curso, es `404`—, que es lo que el bloque 3 decidió para todo lo anidado. El aula (`RF-AC-035`) no lleva el módulo porque no es la misma forma ni el mismo actor.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Consulta una lección |

## 4. Alcance

### 4.1 Incluye

- Devolver la lección por su ruta anidada, **viva o retirada**, con todos sus campos **incluido el contenido**.
- Si está retirada: `deletedAt` y el motivo.

### 4.2 No incluye

- **Ninguna condición de ofrecibilidad ni de acceso.** Es administración: ve lo inactivo, lo vacío y lo retirado.
- **El módulo ni el curso**, más allá de sus identificadores. Ya están en el detalle del curso.
- **`offerable`.** Lo dice el detalle del curso por módulo, y una lección se ofrece si está activa y con contenido, que son dos campos que aquí viajan.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-016` | El contenido viaja tal cual se guardó, del tipo que sea | `requirements/ac.md` §5.1 |
| `RN-AC-018` | La retirada se devuelve con su motivo, también la arrastrada | `requirements/ac.md` §5.1 |
| `RN-AC-019` | La ruta afirma la pertenencia: la lección es de ese módulo y el módulo de ese curso | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso, módulo y lección | Sí | Cuál | Ruta; tres UUID. La lección **de ese módulo**, el módulo **de ese curso**, en cualquier estado los tres |

### 6.2 Salida

`200` con `LessonResponse` (`RF-AC-028` §6.2): `id`, `moduleId`, `courseId`, `type`, `title`, `description`, `content` (presentes y nulos), `durationMinutes`, `displayOrder`, `open`, `status`, `createdAt`, `updatedAt`, y **solo si está retirada** `deletedAt` y `deletionReason`.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:read`. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llegan los tres identificadores.
2. El sistema valida su forma (`VAL-001`).
3. El sistema resuelve la lección **en cualquier estado** cuyo módulo es el de la ruta y cuyo curso es el de la ruta: si no, `EX-001`.
4. Si está retirada, lee el motivo de la auditoría de eliminación.
5. Devuelve `200`.

## 9. Flujos alternativos

### FA-001 — La lección está retirada

**Comportamiento:** se devuelve con `deletedAt` y `deletionReason`. Si la retiró el arrastre de su módulo o de su curso, el motivo es el de aquel retiro, que `CourseTreeRetirement` escribió con un registro por fila (`RF-AC-013`, `RF-AC-025`).

## 10. Excepciones

### EX-001 — La lección no existe en esa ruta

**Respuesta del sistema:** `404` — *«No existe una lección con ese identificador en ese módulo.»* — también si el módulo no es del curso.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-209` | El sistema devuelve la lección viva con todos sus campos **incluido el contenido** —de los dos tipos, y nulo cuando no lo tiene—, **campo a campo igual que la respuesta del alta** (`RF-AC-028`) |
| `CA-AC-210` | La lección **retirada** se devuelve con `deletedAt` y `deletionReason`; la **arrastrada** por el retiro del módulo o del curso, con el motivo de aquel retiro; en una viva los dos campos **no viajan** |
| `CA-AC-211` | Responde `404` si la lección no existe, si es de **otro módulo**, o si el módulo es de **otro curso**; un identificador mal formado, `400` |
| `CA-AC-212` | La lectura cuesta **una** sentencia, y **una más** con motivo de retiro |
| `CA-AC-213` | Sin `courses:read` responde `403` **aunque el actor porte `courses:learn` o `courses:update`** |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Retirada cuyo registro de eliminación no existe | `deletionReason` nulo y presente, sin `500` |
| Lección `ACTIVA` a la que se le vació el contenido | Se devuelve con `content: null` y `status: ACTIVO`: es exactamente lo que esta lectura existe para que administración vea |
| Contenido de veinte páginas | Viaja entero |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Un `GET` de lección o el contenido en el detalle del curso? | **El `GET`**, por decisión del responsable del proyecto (18-09-2026): el detalle del curso pesaría un Markdown por lección para una pantalla que abre una a la vez |
| 2 | ¿Se devuelve la retirada? | **Sí**, como `RF-AC-003` y `RF-AC-010`: es la lectura de administración, y lo que se retiró por arrastre tiene que poder leerse con su motivo |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. Nace el mismo día por decisión del responsable del proyecto, cerrando `RF-AC-029` §14.2: `RF-AC-003` para lecciones, con `courses:read`, ruta anidada que afirma la pertenencia y la misma forma que las escrituras —`LessonResponse`, que gana `deletedAt` y `deletionReason` cuando los hay—; devuelve también la retirada y la arrastrada con su motivo. | Responsable técnico |
