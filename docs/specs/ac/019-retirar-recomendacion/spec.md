# SPEC — `RF-AC-019` Retirar una recomendación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-019` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Dejar de sugerir un curso previo.

## 2. Contexto

La mitad inversa de `RF-AC-018`, con la forma de `RF-AC-017`: borra la fila, `ASSOCIATION` sin motivo, devuelve el curso. Lo único propio es que **el recomendado puede estar retirado**: es justamente el caso en que administración quiere limpiar una recomendación colgada (`RF-AC-018` §13), y por eso no se comprueba nada del recomendado — solo que la pareja exista.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Retira la recomendación |

## 4. Alcance

### 4.1 Incluye

- Borrar la pareja curso → recomendado.
- Devolver el curso en la forma del detalle.

### 4.2 No incluye

- **Un motivo.**
- **Retirar la inversa** (`B → A`) al retirar `A → B`. Son dos parejas.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-011` | Retirar la recomendación borra la fila | `requirements/ac.md` §5.1 |
| Art. V.13 | `ASSOCIATION`, sin motivo | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso y recomendado | Sí | Qué pareja | Ruta. Curso **vivo**; la pareja tiene que existir; el recomendado **en cualquier estado** |

### 6.2 Salida

`200` con el curso en la forma del detalle.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; curso vivo; pareja existente.

**Postcondiciones:** la fila no existe; `audit_deletion_log` tiene una fila `ASSOCIATION`.

## 8. Flujo principal

1. Llega la petición con curso y recomendado en la ruta.
2. El sistema valida la forma (`VAL-001`).
3. El sistema resuelve el curso **vivo**, bloqueándolo (`EX-001`).
4. El sistema resuelve la pareja (`EX-002`).
5. Borra la fila, registra la eliminación de la asociación, y devuelve `200` con el detalle.

## 9. Flujos alternativos

### FA-001 — El recomendado está retirado

**Comportamiento:** se retira igual. Es para lo que sirve.

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-002 — Ese curso no está recomendado

**Respuesta del sistema:** `404` — *«El curso indicado no está recomendado como previo de este.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-151` | El sistema retira la recomendación con `200` y devuelve el detalle sin ella; la fila no existe; **la inversa, si existía, permanece** |
| `CA-AC-152` | `audit_deletion_log` tiene la fila `ASSOCIATION` sin motivo, con el curso como entidad, el actor y la pareja |
| `CA-AC-153` | Un recomendado **retirado** se retira de la lista igual; `404` al curso retirado o inexistente y `404` a la pareja inexistente, con mensajes distintos |
| `CA-AC-154` | Dos retiros simultáneos de la misma pareja dejan un `200`, un `404` y **una** fila de auditoría |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Retirar y volver a recomendar | Dos filas de auditoría; la historia queda |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| — | Ninguna | |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-AC-017` sobre las recomendaciones; el recomendado retirado se retira igual, que es para lo que sirve. | Responsable técnico |
