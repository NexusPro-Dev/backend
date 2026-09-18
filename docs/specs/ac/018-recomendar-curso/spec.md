# SPEC — `RF-AC-018` Recomendar un curso previo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-018` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Decirle al alumno **qué conviene ver antes** de este curso.

## 2. Contexto

La tercera relación del curso, y la única entre cursos: «antes de **este** conviene ver **aquel**». **Es una sugerencia, no un candado** (`RN-AC-011`, decisión del 17-09-2026): el alumno entra con o sin haber visto el recomendado, y el sistema **no sabría** si lo vio, porque no lleva progreso (`ac.md` §1.3). Lo que se comprueba es poco y es de forma: un curso **no se recomienda a sí mismo**, la pareja **no se repite**, y **no se recomienda un curso retirado**. **No se exige que sea acíclico**: `A` recomienda `B` y `B` recomienda `A` es una sugerencia tonta, no un estado inválido, y comprobar ciclos costaría un recorrido por cada alta para prohibir algo que no rompe nada.

**El recomendado puede no ofrecerse todavía** —inactivo, sin membresías, sin módulos—: se recomienda igual, y el aula **solo lo enseña cuando se ofrezca** (`RN-AC-011`, `RN-AC-015`). Administración lo ve siempre en el detalle, con su estado y su `offerable`, que es como sabe qué recomendación quedó colgada. Es el requerimiento que **crea `course_recommendations`** y construye las enmiendas de `RF-AC-010` (`recommendedCourses`) y `RF-AC-013` (`recommended_course_ids`, y «deja de recomendarse donde figure»).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Recomienda |

## 4. Alcance

### 4.1 Incluye

- Añadir la pareja curso → curso recomendado, con los dos vivos y distintos.
- Crear `course_recommendations`.
- **Enmendar** `RF-AC-010` y `RF-AC-013`.
- Devolver el curso en la forma del detalle, con `recommendedCourses`.

### 4.2 No incluye

- **Comprobar ciclos.**
- **Bloquear la entrada al curso** por no haber visto el recomendado.
- **Un orden de las recomendaciones.** Se enseñan en el orden global de los cursos recomendados (`RN-AC-002`, `ac.md` §8.4).
- **La dirección inversa** («¿quién me tiene de previo?»): `RF-AC-010` §4.2.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-011` | Sugerencia; no a sí mismo; sin repetir; no a un retirado; sin exigir aciclicidad; el aula enseña solo las ofrecidas | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Ni el curso ni el recomendado retirados | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso | Sí | Al que se entra | Ruta. Curso **vivo**, en cualquier estado |
| `recommendedCourseId` | Sí | El que conviene ver antes | Curso **vivo**, en cualquier estado, **distinto** del de la ruta |

### 6.2 Salida

`201` con el curso en la forma del detalle, `recommendedCourses` con el nuevo —identificador, título, estado, `offerable`—.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; los dos cursos vivos y distintos; pareja inexistente.

**Postcondiciones:** existe la fila `(course_id, recommended_course_id)`; `audit_change_log` tiene una fila `CREATE` de `course_recommendations` con el curso como entidad.

## 8. Flujo principal

1. Llega la petición con el curso en la ruta y el recomendado en el cuerpo.
2. El sistema valida la forma, **incluido que no sean el mismo** (`VAL-001` a `VAL-003`).
3. El sistema resuelve el curso **vivo**, bloqueándolo (`EX-001`).
4. El sistema resuelve el recomendado **vivo**, sin bloquearlo (`EX-002`).
5. El sistema comprueba que la pareja no existe (`EX-003`).
6. Inserta, registra la creación, y devuelve `201` con el detalle.

**El recomendado no se bloquea**: nada suyo cambia, y bloquear dos cursos en un orden que depende de la petición es el interbloqueo de siempre —`A` recomienda `B` mientras `B` recomienda `A`—.

## 9. Flujos alternativos

### FA-001 — El recomendado no se ofrece hoy

**Comportamiento:** se recomienda igual. El detalle del curso lo trae con `offerable: false`; el aula no lo enseña hasta que se ofrezca.

### FA-002 — `B` ya recomienda a `A` y ahora `A` recomienda a `B`

**Comportamiento:** se admite. Es una sugerencia tonta, no un estado inválido.

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-002 — El curso recomendado no existe o está retirado

**Respuesta del sistema:** `422` — *«El curso recomendado no existe o está retirado.»*

### EX-003 — Ya está recomendado

**Respuesta del sistema:** `409` — *«El curso {título} ya está recomendado como previo.»* Nombra el recomendado.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador del curso con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | `recommendedCourseId` presente y con formato válido | El curso recomendado es obligatorio. |
| `VAL-003` | El recomendado no es el propio curso | Un curso no se recomienda a sí mismo. |
| `VAL-004` | Ningún campo desconocido | El cuerpo de la petición contiene campos no admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-145` | El sistema recomienda con `201` y devuelve el detalle con el recomendado en `recommendedCourses` —identificador, título, estado, `offerable`—; un recomendado `INACTIVO` se admite y sale con `offerable: false` |
| `CA-AC-146` | El sistema rechaza con `400` recomendarse a sí mismo —**sin consultar nada**—; con `409` la pareja repetida, nombrando el título; con `422` un recomendado inexistente o retirado; con `404` un curso inexistente o retirado |
| `CA-AC-147` | **No exige aciclicidad**: `A → B` y después `B → A` responden `201` las dos |
| `CA-AC-148` | El sistema registra una fila `CREATE` de `course_recommendations` con el curso como entidad, el actor y la pareja con el título del recomendado |
| `CA-AC-149` | **Enmienda de `RF-AC-010` y `RF-AC-013`**: el detalle trae `recommendedCourses` en el orden global de los recomendados, en una sentencia más; la instantánea del retiro lleva `recommended_course_ids`; y **un recomendado que se retira** sigue en el detalle del otro curso con `offerable: false` y **deja de enseñarse en el aula** |
| `CA-AC-150` | Dos altas simultáneas de la misma pareja dejan **una** fila y un `409`; `A → B` y `B → A` simultáneas dejan **dos** filas y ningún `500` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Recomendar un curso de otra categoría o de otro instructor | Se admite: nada de eso interviene |
| Recomendar el mismo previo a diez cursos | Diez filas; no hay tope |
| El recomendado se retira después | La fila permanece; el detalle lo enseña colgado y el aula no; administración decide si retira la recomendación (`RF-AC-019`) |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se bloquea el recomendado? | **No** (§8): nada suyo cambia y el orden de dos bloqueos dependería de la petición |
| 2 | ¿Se rechaza recomendar un curso que no se ofrece? | **No**: se arma antes de publicar, como todo; el aula filtra |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. Sugerencia sin candado, sin aciclicidad, recomendado sin bloquear; `400` a sí mismo, `422` al recomendado que no sirve, `409` que lo nombra. Crea la tabla y construye las enmiendas de `RF-AC-010` y `RF-AC-013`. | Responsable técnico |
