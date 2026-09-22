# SPEC — `RF-AC-021` Quitar la visibilidad de un curso a una membresía

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-021` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que un nivel **deje de abrir** el curso.

## 2. Contexto

La mitad inversa de `RF-AC-020`, con la forma de `RF-AC-017`: borra la fila, `ASSOCIATION` sin motivo, devuelve el curso. Lo propio es lo que pasa al quitar la última: **nunca se rechaza** (`RN-AC-012`), y el curso **deja de ofrecerse** en la siguiente lectura — es la forma de retirar un curso de la vista de todos **sin desactivarlo**, que administración puede querer cuando el curso está bien y lo que cambia es a quién se le vende. El detalle lo dice: `offerable: false`, «sin membresías».

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Quita la visibilidad |

## 4. Alcance

### 4.1 Incluye

- Borrar la pareja curso–membresía.
- Devolver el curso en la forma del detalle, con `offerable` recalculado.

### 4.2 No incluye

- **Un motivo.**
- **Rechazar quitar la última.**
- **Quitar de un curso retirado.** Su lista se conserva y no se usa.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-012` | Quitar una membresía borra la fila; sin lista, el curso no se ofrece | `requirements/ac.md` §5.1 |
| `RN-AC-015` | La ofrecibilidad cambia en la siguiente lectura | `requirements/ac.md` §5.1 |
| Art. V.13 | `ASSOCIATION`, sin motivo | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso y membresía | Sí | Qué pareja | Ruta. Curso **vivo**; la pareja tiene que existir |

### 6.2 Salida

`200` con el curso en la forma del detalle.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; curso vivo; pareja existente.

**Postcondiciones:** la fila no existe; `audit_deletion_log` tiene una fila `ASSOCIATION`; si era la última, el curso ya no se ofrece.

## 8. Flujo principal

1. Llega la petición con curso y membresía en la ruta.
2. El sistema valida la forma (`VAL-001`).
3. El sistema resuelve el curso **vivo**, bloqueándolo (`EX-001`).
4. El sistema resuelve la pareja (`EX-002`).
5. Borra la fila, registra la eliminación de la asociación, y devuelve `200` con el detalle.

## 9. Flujos alternativos

### FA-001 — Era la última membresía de un curso ofrecido

**Comportamiento:** se quita. El detalle vuelve con `offerable: false` «sin membresías»; el aula deja de enseñarlo, **incluidas sus lecciones abiertas** (`RN-AC-014`).

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-002 — Esa membresía no abre el curso

**Respuesta del sistema:** `404` — *«La membresía indicada no abre este curso.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-141` | El sistema quita la visibilidad con `200` y devuelve el detalle sin la membresía; la fila no existe |
| `CA-AC-142` | `audit_deletion_log` tiene la fila `ASSOCIATION` sin motivo, con el curso como entidad, el actor y la pareja con el código |
| `CA-AC-143` | **Quitar la última nunca se rechaza**: el curso sigue `ACTIVO`, el detalle vuelve `offerable: false` «sin membresías», y **el aula deja de enseñarlo** — también sus lecciones abiertas |
| `CA-AC-144` | `404` al curso retirado o inexistente y `404` a la pareja inexistente, con mensajes distintos; dos retiros simultáneos dejan un `200`, un `404` y **una** fila de auditoría |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Quitar y volver a dar la misma membresía | Dos filas de auditoría —`ASSOCIATION` y `CREATE`— y el curso vuelve a ofrecerse; la historia queda |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| — | Ninguna | |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-AC-017` sobre la visibilidad; quitar la última nunca se rechaza y es la forma de esconder un curso sin desactivarlo. | Responsable técnico |
