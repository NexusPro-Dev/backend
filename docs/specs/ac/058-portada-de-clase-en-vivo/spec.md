# SPEC — `RF-AC-058` a `RF-AC-061` La portada de una clase en vivo, y la clase sin curso

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-058`, `RF-AC-059`, `RF-AC-060`, `RF-AC-061`; enmienda `RF-AC-042` a `RF-AC-056` y retira `RF-AC-057` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 13-10-2026 |

---

## 1. Objetivo

Que la clase en vivo **no dependa de ningún curso**, que tenga **sus propios instructores** —uno o varios— y **una portada**.

## 2. Contexto

El responsable del proyecto lo pidió el 13-10-2026 ([`requirements/ac.md`](../../../requirements/ac.md) §5.2.18): «en las clases en vivo, es independiente de los cursos de la academia y cada sesión en vivo tendrá su imagen de portada». Respuestas: **la clase tiene su instructor, y puede tener más de uno**; **el curso se quita del todo**; **la portada es opcional**.

Hasta hoy el curso hacía dos cosas: agrupar la clase y **decidir qué instructor la administraba** (`RN-AC-028`). Lo primero desaparece; lo segundo pasa a **una lista de instructores de la clase** (`RN-AC-032`). Las rutas del instructor (`RF-AC-048` a `RF-AC-052`, `RF-AC-056`) siguen iguales y cambian de regla; `RF-AC-057`, que servía para elegir el curso, **se retira**.

La portada es la de los cursos (`RN-AC-004`, `RF-AC-014`) y la del producto (`RF-PM-014`): un archivo en `academy_images`, servido sin token en `/api/v1/academy-images/{id}`. **Cuatro operaciones** —poner y quitar, de administración y propias— con **cuatro permisos** (`RN-SEG-014`).

## 3. Actores

| Actor | Permisos |
|---|---|
| Administrador | `live-sessions:set-cover`, `live-sessions:remove-cover`, y los de siempre |
| Instructor de la clase | `live-sessions:set-cover-own`, `live-sessions:remove-cover-own`, y los `-own` de siempre |

## 4. Alcance

### 4.1 Incluye

- **Sin curso**: `courseId` y `removeCourse` salen de programar y corregir; `course` sale de las respuestas; el filtro `courseId` sale del listado y de la vitrina.
- **Instructores**: `instructorIds` al programar y corregir; `instructors` en el listado, el detalle y la vitrina; el filtro `instructorId` en el listado de administración.
- **Portada**: `PUT` y `DELETE` `/live-sessions/{id}/cover` y `/live-sessions/mine/{id}/cover`; `coverImageUrl` en el listado, el detalle y la vitrina.
- `V105`: la tabla de instructores con lo que había, la portada, sin `course_id`; los cuatro permisos y el retiro de `courses:list-own`.

### 4.2 No incluye

- Que el instructor deje de portar `courses:teach` después: se comprueba al asignar y solo al asignar, como en el curso.
- Avisar a los instructores de nada.

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-004` | La portada: archivo, tabla propia, opcional |
| `RN-AC-006` | Quién puede ser instructor |
| `RN-AC-028` | Lo propio es lo de las clases de las que se es instructor |
| `RN-AC-029` | Ni portada ni nada en una cancelada o terminada |
| `RN-AC-032` | Sin curso, con instructores y portada |

## 6. Datos

### 6.1 Entrada

| Campo | Dónde | Regla |
|---|---|---|
| `instructorIds` | Programar | Opcional; personas que existen, no retiradas, con `courses:teach`, sin repetir. **Por `/mine`, quien pide entra siempre** aunque no se nombre |
| `instructorIds` | Corregir | Si viene, **reemplaza la lista entera**; vacía la deja sin instructores. **Por `/mine`, tiene que incluir a quien pide** |
| `file` | Poner la portada | `multipart/form-data`; `JPEG`, `PNG` o `WebP` por sus bytes; hasta 5 MB |

### 6.2 Salida

`instructors` —`[{ id, username, fullName }]`— y `coverImageUrl` —o nulo— en `LiveSessionDetailResponse`, `LiveSessionItem` y `AvailableLiveSessionItem`; **sin `course`**. Las cuatro operaciones de portada devuelven el detalle.

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| Precondición | El permiso de cada ruta; la clase programada y sin terminar para poner o quitar la portada |
| Postcondición | La lista de instructores escrita; la portada apuntada y la anterior borrada; un `UPDATE` auditado |

## 8. Flujo principal (poner la portada)

1. Se valida el archivo, antes de bloquear nada.
2. Se bloquea la clase; si es propia, se comprueba que quien pide esté en la lista; cancelada o terminada, `409`.
3. Se guarda la imagen, se apunta la clase, se borra la anterior y se audita.

## 9. Flujos alternativos

### FA-001 — Quitar una portada que no hay

`200` con el detalle y nada que auditar.

### FA-002 — Programar por `/mine` sin nombrar a nadie

La clase queda con quien la programó como único instructor.

## 10. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | La clase no existe, o por `/mine` no es de las suyas | `404` |
| `EX-002` | Un instructor no existe o está retirado | `422`, campo `instructorIds` |
| `EX-003` | Un instructor no porta `courses:teach`; o, en la portada, la clase está cancelada o terminada | `422`; `409` en la portada |
| `EX-004` | Por `/mine`, corregir lo quita de la lista | `422`, campo `instructorIds` |

## 11. Validaciones

| Código | Campo | Regla |
|---|---|---|
| `VAL-002` | `file` | Sin archivo o vacío: `400` |
| `VAL-003` | `file` | Ni `JPEG`, ni `PNG`, ni `WebP` por sus bytes: `400` |
| `VAL-004` | `file`, `instructorIds` | Más de 5 MB; o identificadores repetidos o vacíos: `400` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-317` | **Programar con `instructorIds`** guarda la lista y responde `instructors` con nombre y usuario, `coverImageUrl` nulo y **ningún `course`**; un instructor inexistente, retirado o sin `courses:teach`, `422`; repetidos, `400`; y en los tres **Zoom no recibe nada** |
| `CA-AC-318` | **Por `/mine`, quien programa queda en la lista**, con los que nombre; el listado, el detalle, corregir, cancelar e iniciar propios valen **sobre las clases de las que es instructor** —también si la programó otro— y **`404` sobre las demás**, incluida una sin instructores |
| `CA-AC-319` | **Corregir `instructorIds` reemplaza la lista**; vacía la deja sin instructores; **por `/mine`, una lista que no lo incluye responde `422`** y nada cambia |
| `CA-AC-320` | **Poner la portada** —administración y propia— responde el detalle con `coverImageUrl` en `/api/v1/academy-images/{id}`, que se sirve sin token; **otra la reemplaza y borra la anterior**; sin archivo, de otro formato o de más de 5 MB, `400`; cancelada o terminada, `409`; la propia sobre una ajena, `404`; se audita como `UPDATE` |
| `CA-AC-321` | **Quitar la portada** la borra y deja `coverImageUrl` nulo; sin portada, `200` sin cambio; cancelada o terminada, `409` |
| `CA-AC-322` | **El listado de administración filtra por `instructorId`** y ya no por curso; **la vitrina** trae `instructors` y `coverImageUrl` |
| `CA-AC-323` | **`V105`**: `live_sessions` no tiene `course_id`; las clases que colgaban de un curso **tienen como instructor al del curso**; `courses:list-own` y `GET /courses/mine` ya no existen; los cuatro permisos, a administración los dos de administración y donde está `courses:teach` los dos propios; **cada ruta exige el suyo** |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Un instructor que pierde `courses:teach` después | Sigue en la lista: se comprueba al asignar |
| Un instructor retirado después | Sigue en la lista; no puede entrar a la plataforma |
| La misma imagen subida dos veces | Dos imágenes: cada subida estrena identificador |
| Administración nombra al mismo instructor dos veces | `400` |

## 14. Preguntas abiertas

Ninguna.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 13-10-2026 | Nace con la separación de las clases en vivo de los cursos (`ac.md` v0.27.0 §5.2.18, `RN-AC-032`). | Responsable técnico |
