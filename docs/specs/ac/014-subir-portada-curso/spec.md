# SPEC — `RF-AC-014` Subir o reemplazar la portada de un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-014` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que el curso tenga una imagen con la que presentarse en la tarjeta del catálogo y en su página.

## 2. Contexto

Es `RF-AC-006` aplicado al curso, sobre la **misma tabla** y el **mismo detector**, sin condición de estado ni de contenido: un curso inactivo, sin módulos o sin membresías la admite igual, porque subir una portada nunca deja a nada peor de lo que estaba. Sin portada, el curso se pinta con el icono por omisión del sistema (`RN-AC-004`), y por eso tampoco aquí hay regla cruzada. **La única diferencia con la categoría es la entidad y su permiso**: `courses:update`.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Sube o reemplaza la portada |

## 4. Alcance

### 4.1 Incluye

- Recibir un archivo y convertirlo en la portada de un curso vivo, con las condiciones de `RF-AC-006`.
- Devolver el curso en la forma del detalle, con la dirección nueva.

### 4.2 No incluye

- Lo mismo que `RF-AC-006` §4.2, y **exigir nada del curso** salvo que esté vivo.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-004`, `RN-AC-018`, `RN-PM-033` | Como en `RF-AC-006` | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Qué curso | Ruta. Curso **vivo**, en cualquier estado |
| `file` | Sí | La imagen | Las condiciones de `RF-AC-006` |

### 6.2 Salida

`200` con el curso en la forma del detalle (`RF-AC-010`), con `coverImageUrl` de la imagen nueva.

## 7. Precondiciones y postcondiciones

Las de `RF-AC-006` sobre `courses`: fila nueva en `academy_images`, `courses.cover_image_id` la señala, la anterior no existe, `UPDATE` de `courses` con `cover_image_id` antes y después.

## 8. Flujo principal

El de `RF-AC-006` §8 sobre el curso vivo.

## 9. Flujos alternativos

### FA-001 — El curso está `ACTIVO` y se ofrece

**Comportamiento:** se reemplaza; el aula enseña la dirección nueva en la siguiente lectura.

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-002 — No es `multipart/form-data`

**Respuesta del sistema:** `400`, como `RF-AC-006`.

## 11. Validaciones

Las de `RF-AC-006` §11, con los mismos códigos y mensajes.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-172` | El sistema sube y **reemplaza** la portada de un curso con `200`, la respuesta y las lecturas del curso —listado, detalle— traen la dirección nueva con la forma `/api/v1/academy-images/{uuid}`, y la fila anterior ya no existe |
| `CA-AC-173` | `audit_change_log` tiene un `UPDATE` de `courses` con `cover_image_id` antes y después y ningún otro campo |
| `CA-AC-174` | Los tres rechazos del archivo y el no-`multipart` responden como en `RF-AC-006`, **sin dejar fila**; `404` sobre un curso inexistente o retirado; **sube igual** sobre uno `INACTIVO`, sin módulos y sin membresías |
| `CA-AC-175` | Sin `courses:update` responde `403`, también con `course-categories:update` |

## 13. Casos límite

Los de `RF-AC-006` §13.

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| — | Ninguna: todo viene de `RF-AC-006` | |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-AC-006` sobre el curso, sin condición de estado ni contenido. | Responsable técnico |
| 0.2.0 | 25-09-2026 | **Construida** (`AcademyCoverIT`) sobre `CoverUploader` y `HasCover`, que `CourseCategory` y `Course` implementan; el módulo lo hará con `RF-AC-026`. Es la portada que el responsable del proyecto pidió para el alta, y va por esta operación y no dentro del alta (`ac.md` §5.2.9). | Responsable técnico |
