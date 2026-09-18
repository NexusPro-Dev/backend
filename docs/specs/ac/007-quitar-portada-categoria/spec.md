# SPEC — `RF-AC-007` Quitar la portada de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-007` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Dejar la categoría sin imagen, para que se pinte con su color y su icono.

## 2. Contexto

Es `RF-PM-029` —quitar la portada del paquete— sobre la categoría, y **nunca se rechaza**: la categoría siempre tiene color e icono (`RN-AC-003`), de modo que no puede quedarse sin nada con qué pintarse, y no existe aquí la condición de `RN-PM-034`. Suelta la imagen y la borra físicamente: su dirección responde `404` desde ese instante (`RF-AC-032`). Sin portada, `200` con la categoría **sin escribir nada**: «quítala» sobre una categoría sin portada ya ha conseguido lo que quería.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Quita la portada |

## 4. Alcance

### 4.1 Incluye

- Vaciar `cover_image_id` de una categoría viva y borrar la fila de `academy_images`.
- Devolver la categoría en la forma de su detalle, con `coverImageUrl` presente y nula.

### 4.2 No incluye

- **Rechazar por ningún estado.**
- **Un motivo.** Es corregir el valor de un campo, no una baja (`requirements/pm.md` §5.2.9).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-004` | Quitar la portada nunca se rechaza; la fila se borra | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Una categoría retirada no se corrige | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Qué categoría | Ruta. Categoría **viva** |

### 6.2 Salida

`200` con la categoría en la forma del detalle, `coverImageUrl` **presente y nula**.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `course-categories:update`; categoría viva.

**Postcondiciones:** `cover_image_id` nulo y `updated_at` avanzado **solo si había portada**; la fila de `academy_images` **ya no existe**; `audit_change_log` tiene un `UPDATE` con `cover_image_id` antes y después **solo si había portada**.

## 8. Flujo principal

1. Llega la petición.
2. El sistema valida la forma (`VAL-001`).
3. El sistema resuelve la categoría **viva**, bloqueándola (`EX-001`).
4. Si no tiene portada, devuelve `200` sin escribir.
5. Vacía la columna, **vuelca**, borra la imagen, registra el `UPDATE`, y devuelve `200`.

## 9. Flujos alternativos

### FA-001 — Sin portada

**Comportamiento:** `200` con la categoría, sin escribir ni auditar.

## 10. Excepciones

### EX-001 — La categoría no existe o está retirada

**Respuesta del sistema:** `404` — *«No existe una categoría viva con ese identificador.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-168` | El sistema quita la portada con `200`, `coverImageUrl` nulo y presente, `cover_image_id` nulo, y la fila de `academy_images` **ya no existe**: su dirección responde `404`. **Nunca responde `400`** |
| `CA-AC-169` | `audit_change_log` tiene un `UPDATE` de `course_categories` con `cover_image_id` —antes el identificador, después vacío— y ningún otro campo |
| `CA-AC-170` | Sobre una categoría **sin portada** responde `200` **sin escribir nada**: `updated_at` no avanza y la auditoría no crece |
| `CA-AC-171` | `404` sobre una inexistente y sobre una retirada; sin `course-categories:update`, `403` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Quitar mientras otro sube | El bloqueo los ordena; quien llegue segundo ve el resultado del primero |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿`204` o `200` con la categoría? | **`200` con la categoría**, como `PM` (`RF-PM-029`): lo que cambió es un campo y el cliente repinta la ficha. La ficha de `ac.md` §6.2 decía `204` y se precisa en `ac.md` v0.7.0 |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-PM-029` sobre la categoría: nunca se rechaza, borra la imagen, `200` con la categoría, sin escribir si no había. | Responsable técnico |
