# SPEC — `RF-AC-017` Desclasificar un curso de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-017` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Sacar el curso de un cajón.

## 2. Contexto

La mitad inversa de `RF-AC-016`: **borra la fila** (`RN-AC-010`), sin motivo, porque la clasificación no es una entidad sino el valor de una relación y no cabe en el Art. V.13 como baja lógica — cabe como **`ASSOCIATION`**, la tercera clase de eliminación, que es la que `PM` usa al desasociar un producto (`RF-PM-025`). Nada del estado ni de la ofrecibilidad cambia: un curso sin categoría se ofrece igual.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Desclasifica el curso |

## 4. Alcance

### 4.1 Incluye

- Borrar la pareja curso–categoría.
- Devolver el curso en la forma del detalle, sin esa categoría.

### 4.2 No incluye

- **Un motivo.** Es una asociación.
- **Desclasificar un curso retirado.** Su clasificación se conserva y no se enseña (`RN-AC-018`); no hay nada que quitar.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-010` | Desclasificar borra la fila | `requirements/ac.md` §5.1 |
| Art. V.13 | La eliminación de una asociación se registra como `ASSOCIATION`, sin motivo | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso y categoría | Sí | Qué pareja | Ruta. Curso **vivo**; la pareja tiene que existir |

### 6.2 Salida

`200` con el curso en la forma del detalle.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; curso vivo; pareja existente.

**Postcondiciones:** la fila no existe; `audit_deletion_log` tiene una fila `ASSOCIATION` con el curso como entidad y la pareja en la instantánea; la categoría deja de contarlo.

## 8. Flujo principal

1. Llega la petición con curso y categoría en la ruta.
2. El sistema valida la forma (`VAL-001`).
3. El sistema resuelve el curso **vivo**, bloqueándolo (`EX-001`).
4. El sistema resuelve la pareja (`EX-002`).
5. Borra la fila, registra la eliminación de la asociación, y devuelve `200` con el detalle.

## 9. Flujos alternativos

### FA-001 — La categoría está retirada

**Comportamiento:** se desclasifica igual: la fila existe y se borra. Es la forma de limpiar lo que una categoría retirada dejó, si alguien quiere.

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-002 — El curso no está en esa categoría

**Respuesta del sistema:** `404` — *«El curso no está clasificado en esa categoría.»* Se distingue del anterior por el mensaje; los dos son `404` porque la ruta nombra algo que no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-131` | El sistema desclasifica con `200` y devuelve el detalle sin la categoría; la fila no existe; el curso no cambia de estado y sigue ofreciéndose si se ofrecía |
| `CA-AC-132` | `audit_deletion_log` tiene la fila `ASSOCIATION` **sin motivo**, con el curso como entidad, el actor y la pareja en la instantánea |
| `CA-AC-133` | El sistema responde `404` al curso retirado o inexistente y `404` a la pareja inexistente, con mensajes distintos; una categoría **retirada** se desclasifica igual |
| `CA-AC-134` | Dos desclasificaciones simultáneas de la misma pareja dejan un `200` y un `404`, y **una** fila de auditoría |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Desclasificar la única categoría de un curso ofrecido | Se desclasifica y el curso se sigue ofreciendo sin cajón (`RN-AC-010`) |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| — | Ninguna: la forma es la de `RF-PM-025` | |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. Borrado físico de la fila con `ASSOCIATION` sin motivo, como `RF-PM-025`; una categoría retirada se desclasifica igual. | Responsable técnico |
| 0.2.0 | 25-09-2026 | **Construida** (`CourseClassificationIT`, junto con `RF-AC-016`). Sin enmiendas de comportamiento: la instantánea lleva el nombre que la categoría tiene al desclasificar, viva o retirada. | Responsable técnico |
