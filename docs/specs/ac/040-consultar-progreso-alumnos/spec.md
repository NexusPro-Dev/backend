# SPEC — `RF-AC-040` Consultar el progreso de los alumnos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-040` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Saber, **por alumno y por curso**, cuánto ha visto cada uno: es la pregunta que el responsable del proyecto hizo el 09-10-2026 («quiero saber por usuario qué tanto ha visto las lecciones»).

## 2. Contexto

Una fila por **alumno y curso** en el que el alumno abrió al menos una lección, con su avance (`RN-AC-023`). El detalle lección a lección es `RF-AC-041`; esta es la tabla desde la que se llega a él.

**Cada uno ve lo que le toca** (`RN-AC-024`), con **las mismas condiciones sumadas** que el detalle: administración todo; un vendedor su red y los clientes de su red, en cualquier curso; el instructor todos los alumnos de los cursos que dicta. **Fuera del alcance no hay error**: página vacía, como `RF-MV-015` (`RN-MV-031`). Un `userId` fuera del alcance comercial **no se descarta antes de consultar**, porque puede ser alumno de un curso que quien pregunta dicta y eso solo lo sabe la sentencia; el predicado del alcance va **siempre** en ella, de modo que el filtro **no descubre** quién cuelga de quién: fuera de la red y de los cursos propios, la página sale vacía igual.

**Un curso retirado o que ya no se ofrece sigue saliendo**: el progreso es historia, y su avance se calcula sobre lo que ofrezca hoy, que puede ser nada.

## 3. Actores

Los de `RF-AC-041` §3.

## 4. Alcance

### 4.1 Incluye

- La lista paginada, por omisión **por última actividad descendente**.
- Filtros `userId`, `courseId` y `completed`.

### 4.2 No incluye

- **Los alumnos que no han abierto nada** de un curso: no hay fila que enseñar. Quien quiera saber quién **no** empezó lo cruza con su cartera.
- **Exportar**, ni sumas por curso o por vendedor: si se piden, son indicadores de `IN`.
- **Orden elegible**: uno fijo, el que responde «quién estudió último».

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-023` | El avance de cada fila |
| `RN-AC-024` | Qué filas se ven |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| `page`, `size` | No | La paginación del sistema |
| `userId` | No | `uuid`; un alumno |
| `courseId` | No | `uuid`; un curso, vivo o retirado; uno inexistente da página vacía |
| `completed` | No | `true`: solo filas con **todas** las lecciones ofrecibles completadas y al menos una; `false`: las demás |

### 6.2 Salida

La envoltura de página del sistema (`content`, `totalElements`, `totalPages`, `page`, `size`, `totalIsExact`). Cada fila: `student {id, username, fullName}`, `course {id, title, status, deleted}`, `percent`, `completedLessons`, `lessonCount`, `watchedSeconds`, `totalSeconds`, `completed`, `firstOpenedAt` y `lastActivityAt`.

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| Precondición | Sesión con `courses:list-progress` |
| Postcondición | Ninguna |

## 8. Flujo principal

1. Se validan paginación e identificadores.
2. Se resuelve el alcance (`RF-AC-041` · `ProgressAudience`).
3. Se leen el total y, si no es cero, la página, con el alcance como predicado, y la identidad de los alumnos de la página en una llamada.

## 9. Flujos alternativos

### FA-001 — Nadie ha empezado nada

Página vacía, `200`.

## 10. Excepciones

Ninguna propia: fuera del alcance es página vacía.

## 11. Validaciones

| Código | Campo | Regla |
|---|---|---|
| `VAL-001` | `page`, `size`, `userId`, `courseId`, `completed` | Mal formado o fuera de rango: `400`, juntos |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-258` | Una fila por alumno y curso con al menos una lección abierta, con las cifras de `RN-AC-023`, ordenadas por última actividad descendente; un alumno con dos cursos da dos filas |
| `CA-AC-259` | **Alcance**: administración ve todas; un vendedor ve las de su red y de los clientes de su red y **no** las de un cliente ajeno; el instructor ve las de **su curso** de cualquier alumno y no las de otro curso; un vendedor que además dicta un curso ve **la suma** |
| `CA-AC-260` | `userId` fuera del alcance da página vacía; `courseId` acota; `completed=true` deja solo los cursos terminados y `false` el resto |
| `CA-AC-261` | Un curso **retirado** con progreso sigue saliendo, con `deleted` verdadero y su avance sobre lo que se ofrece hoy |
| `CA-AC-262` | Sin `courses:list-progress` responde `403` aunque porte `courses:read-progress`; parámetros mal formados, `400` juntos; la página cuesta un número fijo de sentencias |

## 13. Casos límite

- **Un alumno que es a la vez de la red y del curso del instructor**: una sola fila.
- **El curso sin lecciones ofrecibles hoy**: `lessonCount` 0, `percent` 0, `completed` falso.

## 14. Preguntas abiertas

Ninguna.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con el progreso del alumno (`ac.md` v0.21.0 §5.2.14). | Responsable técnico |
| 1.0.1 | 09-10-2026 | **Al construir**: el `userId` fuera de la red **no se corta antes de consultar** —puede ser alumno de un curso que el actor dicta—; el alcance va siempre en la sentencia y la página sale vacía igual (`CA-AC-260` pierde «sin consultar la tabla»). | Responsable técnico |
