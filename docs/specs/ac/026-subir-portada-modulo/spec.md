# SPEC — `RF-AC-026` Subir o reemplazar la portada de un módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-026` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que un módulo tenga una imagen con la que presentarse dentro del curso.

## 2. Contexto

Es `RF-AC-014` sobre el módulo, con el padre en la ruta: `PUT /courses/{courseId}/modules/{moduleId}/cover`, y el módulo de otro curso es `404` como en toda operación sobre él (`RF-AC-023`). Las condiciones del archivo, la tabla, el detector y el reemplazo son los de `RF-AC-006`; la respuesta es **el módulo** en la forma de su detalle, como toda escritura sobre él (`RF-AC-022` §14.1). Sin condición de estado: un módulo inactivo o sin lecciones la admite igual.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Sube o reemplaza la portada |

## 4. Alcance

### 4.1 Incluye

- Recibir un archivo y convertirlo en la portada de un módulo vivo del curso de la ruta.
- Devolver el módulo en la forma de su detalle, con la dirección nueva.

### 4.2 No incluye

- Lo mismo que `RF-AC-006` §4.2, y **una portada para la lección**, que no la tiene (`ac.md` §8.7).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-004`, `RN-AC-018`, `RN-AC-019`, `RN-PM-033` | Como en `RF-AC-006`, sobre un módulo del curso de la ruta | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso y módulo | Sí | Cuál | Ruta. Módulo **vivo** de ese curso |
| `file` | Sí | La imagen | Las condiciones de `RF-AC-006` |

### 6.2 Salida

`200` con el módulo en la forma de su detalle (`RF-AC-022`), con `coverImageUrl` de la imagen nueva.

## 7. Precondiciones y postcondiciones

Las de `RF-AC-006` sobre `course_modules`.

## 8. Flujo principal

El de `RF-AC-006` §8 sobre el módulo vivo del curso de la ruta.

## 9. Flujos alternativos

### FA-001 — El curso está retirado

**Comportamiento:** el módulo también lo está (arrastre de `RF-AC-013`), y responde `404`.

## 10. Excepciones

### EX-001 — El módulo no existe, está retirado o no es de ese curso

**Respuesta del sistema:** `404` — *«No existe un módulo vivo con ese identificador en este curso.»*

### EX-002 — No es `multipart/form-data`

**Respuesta del sistema:** `400`, como `RF-AC-006`.

## 11. Validaciones

Las de `RF-AC-006` §11, con `VAL-001` sobre los dos identificadores.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-179` | El sistema sube y **reemplaza** la portada de un módulo con `200`, la respuesta y el detalle del curso traen la dirección nueva, y la fila anterior ya no existe |
| `CA-AC-180` | `audit_change_log` tiene un `UPDATE` de `course_modules` con `cover_image_id` antes y después y ningún otro campo |
| `CA-AC-181` | Los rechazos del archivo responden como en `RF-AC-006` sin dejar fila; `404` sobre un módulo inexistente, retirado o **de otro curso**; sube igual sobre uno `INACTIVO` y sin lecciones |
| `CA-AC-182` | Sin `courses:update` responde `403` |

## 13. Casos límite

Los de `RF-AC-006` §13.

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| — | Ninguna | |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-AC-014` sobre el módulo, con el padre en la ruta y la respuesta del módulo. | Responsable técnico |
