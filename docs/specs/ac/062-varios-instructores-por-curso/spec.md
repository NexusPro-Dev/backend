# SPEC — `RF-AC-062` Varios instructores por curso

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-062`; enmienda `RF-AC-008` a `RF-AC-011`, `RF-AC-033`, `RF-AC-034`, `RF-AC-040` y `RF-AC-041` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 13-10-2026 |

---

## 1. Objetivo

Que un curso lo enseñen **una o varias personas**, como la clase en vivo desde `RN-AC-032`.

## 2. Contexto

El responsable del proyecto lo pidió el 13-10-2026 ([`requirements/ac.md`](../../../requirements/ac.md) §5.2.19), justo después de separar las clases en vivo de los cursos: «y lo de los instructores ocurre lo mismo para los cursos, varios por curso».

Hasta hoy el curso tenía **un instructor**, en `courses.instructor_id`, obligatorio desde `RF-AC-008`. Pasa a **una lista de uno o más** (`RN-AC-033`), en `course_instructors`. Cada uno se comprueba como siempre (`RN-AC-006`). No nace ninguna ruta: cambian las que ya escriben y leen el curso.

## 3. Actores

| Actor | Permisos |
|---|---|
| Administrador | `courses:create`, `courses:update`, `courses:read`, sin cambios |
| Alumno | `courses:learn`, `courses:read-available`, sin cambios |
| Instructor, red comercial y administración en el progreso | `courses:list-progress`, `courses:read-progress`, sin cambios |

## 4. Alcance

### 4.1 Incluye

- **Registrar** (`RF-AC-008`): `instructorIds`, obligatorio, en lugar de `instructorId`.
- **Editar** (`RF-AC-011`): `instructorIds`, que si viene **reemplaza la lista entera**, en lugar de `instructorId`.
- **Las lecturas** (`RF-AC-009`, `RF-AC-010`, `RF-AC-033`, `RF-AC-034`): `instructors` en lugar de `instructor`; el filtro `instructorId` del listado, por pertenencia a la lista.
- **El progreso** (`RF-AC-040`, `RF-AC-041`): lo ve cualquiera de los instructores del curso.
- `V106`: `course_instructors` rellenada con lo que había, y `courses` sin `instructor_id`.

### 4.2 No incluye

- Que el instructor edite sus cursos: sigue sin existir (§4 de `ac.md`).
- Un instructor «principal».
- Mantener `instructorId` o `instructor` como campos de transición.

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-006` | Cada instructor existe, no está retirado y porta `courses:teach`, al asignarse |
| `RN-AC-024` | El progreso se le abre a cualquiera de los instructores del curso |
| `RN-AC-033` | Una lista de uno o más, sin repetir, que se reemplaza entera |

## 6. Datos

### 6.1 Entrada

| Campo | Dónde | Regla |
|---|---|---|
| `instructorIds` | Registrar | **Obligatorio**, uno o más, sin repetir ni vacíos |
| `instructorIds` | Editar | Opcional; si viene, **reemplaza la lista entera**; vacía o nula, `400` |

### 6.2 Salida

`instructors` —`[{ id, username, fullName }]`, por nombre completo— en `CourseDetailResponse`, `CourseItem`, `ClassroomCourseResponse` y la tarjeta del catálogo del alumno; **sin `instructor`**.

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| Precondición | El permiso de cada ruta, sin cambios |
| Postcondición | La lista escrita entera; registrar audita `instructor_ids` en la instantánea, y editar el antes y el después |

## 8. Flujo principal (editar los instructores)

1. Se valida la forma: lista no vacía y sin repetidos.
2. Se busca el curso vivo; si no, `404`.
3. Cada instructor **que no estaba** pasa la comprobación de `RN-AC-006`.
4. Se reemplaza la lista y se audita el `UPDATE` con `instructor_ids` antes y después.

## 9. Flujos alternativos

### FA-001 — La misma lista, en otro orden

Nada cambia y nada se audita.

### FA-002 — Un instructor que ya estaba y perdió `courses:teach`

Sigue en la lista: se comprueba **al asignar** (`RN-AC-006`).

## 10. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-002` | Un instructor no existe o está retirado | `422`, campo `instructorIds` |
| `EX-003` | Un instructor no porta `courses:teach` | `422`, campo `instructorIds` |

## 11. Validaciones

| Código | Campo | Regla |
|---|---|---|
| `VAL-002` | `instructorIds` | Al registrar: sin lista o vacía, `400` |
| `VAL-003` | `instructorIds` | Al editar: nula o vacía, `400` |
| `VAL-008` | `instructorIds` | Identificadores repetidos o vacíos, `400`, como las demás listas del alta |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-324` | **Registrar con `instructorIds`** —uno o varios— guarda la lista y responde `instructors` con identificador, usuario y nombre completo, por nombre, y **ningún `instructor`**; sin lista o vacía, `400`; repetidos, `400`; uno inexistente o retirado, `422` `EX-002`; uno sin `courses:teach`, `422` `EX-003`; y en los rechazos **no se guarda nada** |
| `CA-AC-325` | **Editar `instructorIds` reemplaza la lista entera** y audita `instructor_ids` antes y después; la misma lista en otro orden no cambia nada ni audita; vacía o nula, `400`; un nuevo sin `courses:teach`, `422`, y la lista no cambia |
| `CA-AC-326` | **El listado y el detalle** traen `instructors`; **el filtro `instructorId`** deja los cursos en cuya lista está esa persona, **también cuando no es la primera** |
| `CA-AC-327` | **El aula** —catálogo y detalle del alumno— trae `instructors` |
| `CA-AC-328` | **El progreso**: **cualquiera de los instructores** del curso ve a sus alumnos, en el listado y en el detalle |
| `CA-AC-329` | **`V106`**: cada curso tiene como instructor al que tenía; `courses` no tiene `instructor_id` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Quitar a todos los instructores | `400`: el curso dice quién lo enseña |
| Un instructor retirado después | Sigue en la lista, como en `RN-AC-006` |
| Dos instructores con el mismo nombre | Se desempata por identificador |

## 14. Preguntas abiertas

Ninguna.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 13-10-2026 | Nace con los varios instructores por curso (`ac.md` v0.28.0 §5.2.19, `RN-AC-033`). | Responsable técnico |
