# SPEC — `RF-AC-041` Consultar el progreso de un alumno en un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-041` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Ver **lección a lección** qué vio un alumno de un curso: cuánto de cada video, qué abrió, qué completó y cuándo.

## 2. Contexto

Es la vista que responde «¿por dónde va este alumno?». La lista de `RF-AC-040` dice cuánto lleva de cada curso; esta dice **qué** lleva. La piden administración, el instructor del curso y la red comercial del alumno, **cada uno sobre lo que le toca** (`RN-AC-024`).

**El árbol es el ofrecido hoy** (`RN-AC-023`): los módulos y lecciones que el alumno vería en el aula, en su orden, con su avance —cero y sin fechas en las que no abrió—. **Lo que el alumno vio de algo que hoy no se ofrece no se pierde ni se esconde**: va aparte, en `notOffered`, porque la fila existe (`RN-AC-023`) y quien consulta tiene que poder verla.

**Fuera del alcance es `404`, no `403`** (`RN-AC-024`): un `403` diría que el alumno existe y que el curso tiene su progreso. El cuerpo es **el mismo** que el de un alumno o un curso que no existen.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador (`FUNCIONARIO`) | Consulta a cualquier alumno |
| Instructor | Consulta a cualquier alumno **de los cursos que dicta** |
| Vendedor | Consulta a **su red y a los clientes de su red**, en cualquier curso |

## 4. Alcance

### 4.1 Incluye

- El alumno —identificador, nombre de usuario, nombre completo— y el curso —identificador, título, estado, retirado o no—.
- El avance del curso (`RN-AC-023`): porcentaje, lecciones completadas de cuántas, segundos vistos de cuántos, primera apertura y última actividad.
- El árbol ofrecido con el avance de cada lección, y `notOffered`.

### 4.2 No incluye

- **El contenido** de las lecciones.
- **Corregir** el progreso.

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-015` | Qué es ofrecible: el árbol y la cuenta |
| `RN-AC-021`, `RN-AC-022` | Qué significa cada cifra de una lección |
| `RN-AC-023` | El avance se calcula; lo no ofrecido va aparte |
| `RN-AC-024` | Quién ve a quién; fuera, `404` |

## 6. Datos

### 6.1 Entrada

| Dato | Dónde | Obligatorio | Regla |
|---|---|---|---|
| `courseId` | Ruta | Sí | `uuid` |
| `userId` | Ruta | Sí | `uuid` |

### 6.2 Salida

`student {id, username, fullName}`, `course {id, title, status, deleted}`, `progress {percent, completedLessons, lessonCount, watchedSeconds, totalSeconds, firstOpenedAt, lastActivityAt}`, `modules[] {id, title, displayOrder, lessons[]}` y `notOffered[]`. Cada lección: `id`, `title`, `type`, `durationSeconds`, `displayOrder`, `watchedSeconds`, `percent`, `completed`, `completedAt`, `firstOpenedAt`, `lastOpenedAt`. Las fechas de lo no abierto, **presentes y nulas**.

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| Precondición | Sesión con `courses:read-progress` |
| Postcondición | Ninguna: es una lectura |

## 8. Flujo principal

1. Se resuelve el alcance de quien pregunta.
2. Si el alumno está fuera de él y quien pregunta no es el instructor del curso, `404`.
3. Se leen el curso, el árbol ofrecido y las filas de progreso del alumno en ese curso.
4. Se arma el árbol con el avance de cada lección, `notOffered` con el resto y el avance del curso.

## 9. Flujos alternativos

### FA-001 — El alumno no ha abierto nada del curso

Responde `200` con todo en cero y sin fechas. **No es `404`**: el alumno y el curso existen y quien pregunta lo alcanza; que no haya empezado es una respuesta.

### FA-002 — El curso está retirado o no se ofrece

Se lee igual. El árbol ofrecido puede quedar vacío y todo lo visto, en `notOffered`.

## 10. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | El curso no existe, el alumno no existe o **está fuera del alcance** | `404`, **el mismo mensaje** en los tres casos |

## 11. Validaciones

| Código | Campo | Regla |
|---|---|---|
| `VAL-001` | `courseId`, `userId` | Mal formado: `400` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-253` | El árbol ofrecido trae el avance de cada lección —un video a medias, uno completado, un texto abierto, una lección sin abrir en cero y con fechas nulas— y el avance del curso cuadra con `RN-AC-023` |
| `CA-AC-254` | Una lección con progreso que **hoy no se ofrece** —inactiva o retirada— no aparece en el árbol, **sale en `notOffered`** con su avance y **no cuenta** en el porcentaje |
| `CA-AC-255` | **Alcance**: administración lee a cualquiera; un vendedor lee a alguien de su red y a un cliente de su red, y **no** a un cliente de otro; el instructor lee a cualquier alumno **de su curso** y no de otro curso |
| `CA-AC-256` | Fuera del alcance, curso inexistente y alumno inexistente responden **`404` con el mismo cuerpo**; un alumno alcanzado que no empezó, `200` en cero |
| `CA-AC-257` | Sin `courses:read-progress` responde `403` aunque porte `courses:list-progress`; un identificador mal formado, `400` |

## 13. Casos límite

- **El alumno es quien pregunta**: con el permiso, se lee a sí mismo — está en su alcance.
- **Una lección completada cuya duración subió**: cuenta entera y `percent` 100.
- **Un video visto más allá de su duración actual**: `percent` 100, `watchedSeconds` tal cual.

## 14. Preguntas abiertas

Ninguna.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con el progreso del alumno (`ac.md` v0.21.0 §5.2.14). | Responsable técnico |
