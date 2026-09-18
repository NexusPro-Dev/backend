# SPEC — `RF-AC-015` Quitar la portada de un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-015` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Dejar el curso sin imagen, para que se pinte con el icono por omisión del sistema.

## 2. Contexto

Es `RF-AC-007` sobre el curso: **nunca se rechaza** —el curso no declara icono ni color, y el frontend le pone los suyos por omisión, como al bot y al paquete (`RN-AC-004`)—, borra la imagen, `200` con el curso, y sin portada no escribe nada. Un curso `ACTIVO` y ofrecible **sigue activo y ofrecible** sin portada: nada de la ofrecibilidad mira la imagen.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Quita la portada |

## 4. Alcance

### 4.1 Incluye

- Vaciar `cover_image_id` de un curso vivo y borrar la fila de `academy_images`.
- Devolver el curso en la forma del detalle, con `coverImageUrl` presente y nula.

### 4.2 No incluye

- **Rechazar por ningún estado** ni **un motivo**.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-004`, `RN-AC-018` | Como en `RF-AC-007` | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Qué curso | Ruta. Curso **vivo** |

### 6.2 Salida

`200` con el curso en la forma del detalle, `coverImageUrl` presente y nula.

## 7. Precondiciones y postcondiciones

Las de `RF-AC-007` sobre `courses`.

## 8. Flujo principal

El de `RF-AC-007` §8 sobre el curso vivo.

## 9. Flujos alternativos

### FA-001 — Sin portada

**Comportamiento:** `200` sin escribir ni auditar.

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-176` | El sistema quita la portada con `200`, `coverImageUrl` nulo y presente, y la fila de `academy_images` ya no existe; **nunca responde `400`**; un curso **activo y ofrecible** sigue `offerable: true` |
| `CA-AC-177` | `audit_change_log` tiene un `UPDATE` de `courses` con `cover_image_id` antes y después y ningún otro campo; sobre un curso **sin portada**, `200` sin escribir ni auditar |
| `CA-AC-178` | `404` sobre un curso inexistente y sobre uno retirado; sin `courses:update`, `403` |

## 13. Casos límite

Los de `RF-AC-007` §13.

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| — | Ninguna | |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-AC-007` sobre el curso; la ofrecibilidad no mira la portada. | Responsable técnico |
