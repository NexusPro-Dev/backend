# SPEC — `RF-AC-027` Quitar la portada de un módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-027` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Dejar el módulo sin imagen.

## 2. Contexto

Es `RF-AC-015` sobre el módulo, con el padre en la ruta: nunca se rechaza, borra la imagen, `200` con el módulo, sin escribir si no había. El módulo se pinta con el icono por omisión del sistema (`RN-AC-004`).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Quita la portada |

## 4. Alcance

### 4.1 Incluye

- Vaciar `cover_image_id` de un módulo vivo del curso de la ruta y borrar la fila de `academy_images`.
- Devolver el módulo en la forma de su detalle.

### 4.2 No incluye

- **Rechazar por ningún estado** ni **un motivo**.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-004`, `RN-AC-018`, `RN-AC-019` | Como en `RF-AC-007`, sobre un módulo del curso de la ruta | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso y módulo | Sí | Cuál | Ruta. Módulo **vivo** de ese curso |

### 6.2 Salida

`200` con el módulo en la forma de su detalle, `coverImageUrl` presente y nula.

## 7. Precondiciones y postcondiciones

Las de `RF-AC-007` sobre `course_modules`.

## 8. Flujo principal

El de `RF-AC-007` §8 sobre el módulo vivo del curso de la ruta.

## 9. Flujos alternativos

### FA-001 — Sin portada

**Comportamiento:** `200` sin escribir ni auditar.

## 10. Excepciones

### EX-001 — El módulo no existe, está retirado o no es de ese curso

**Respuesta del sistema:** `404` — *«No existe un módulo vivo con ese identificador en este curso.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-183` | El sistema quita la portada con `200`, `coverImageUrl` nulo y presente, y la fila ya no existe; **nunca responde `400`**; un módulo activo y ofrecible sigue ofrecible |
| `CA-AC-184` | `audit_change_log` tiene un `UPDATE` de `course_modules` con `cover_image_id` antes y después; sobre un módulo **sin portada**, `200` sin escribir ni auditar |
| `CA-AC-185` | `404` sobre un módulo inexistente, retirado o **de otro curso**; sin `courses:update`, `403` |

## 13. Casos límite

Los de `RF-AC-007` §13.

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| — | Ninguna | |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-AC-015` sobre el módulo, con el padre en la ruta. | Responsable técnico |
