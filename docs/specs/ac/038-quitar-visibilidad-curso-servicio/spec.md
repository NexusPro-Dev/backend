# SPEC — `RF-AC-038` Quitar la visibilidad de un curso a un servicio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-038` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que **un servicio deje de abrir el curso**.

## 2. Contexto

La mitad inversa de `RF-AC-037`, con la forma de `RF-AC-017`: **borra la fila** (`RN-AC-020`), sin motivo, y lo registra como **`ASSOCIATION`** (Art. V.13). **Quitar el último servicio nunca se rechaza**: si el curso tampoco tiene membresías, deja de ofrecerse y el detalle lo dice (`RN-AC-015`). Es la forma de cerrar un curso a quien lo tenía por ese servicio sin tocar el servicio en `PM`.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Quita la visibilidad |

## 4. Alcance

### 4.1 Incluye

- Borrar la pareja curso–producto.
- Devolver el curso en la forma del detalle, sin ese servicio y con `offerable` recalculado.

### 4.2 No incluye

- **Un motivo.** Es una asociación.
- **Quitar la visibilidad de un curso retirado.** Su lista se conserva y no se enseña (`RN-AC-018`).
- **Comprobar el producto en `PM`.** La pareja existe o no existe; el tipo ya se comprobó al añadirla y no cambia (`RN-PM-001`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-020` | Quitar un servicio borra la fila | `requirements/ac.md` §5.1 |
| `RN-AC-015` | Sin membresías ni servicios, el curso deja de ofrecerse | `requirements/ac.md` §5.1 |
| Art. V.13 | La eliminación de una asociación se registra como `ASSOCIATION`, sin motivo | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso y producto | Sí | Qué pareja | Ruta. Curso **vivo**; la pareja tiene que existir |

### 6.2 Salida

`200` con el curso en la forma del detalle.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; curso vivo; pareja existente.

**Postcondiciones:** la fila no existe; `audit_deletion_log` tiene una fila `ASSOCIATION` con el curso como entidad y la pareja en la instantánea; si era la última llave del curso, deja de ofrecerse.

## 8. Flujo principal

1. Llega la petición con curso y producto en la ruta.
2. El sistema valida la forma (`VAL-001`).
3. El sistema resuelve el curso **vivo**, bloqueándolo (`EX-001`).
4. El sistema resuelve la pareja (`EX-002`).
5. Borra la fila, registra la eliminación de la asociación, y devuelve `200` con el detalle.

## 9. Flujos alternativos

### FA-001 — El servicio está retirado en `PM`

**Comportamiento:** se quita igual: la fila existe y se borra.

### FA-002 — Era el único servicio y el curso no tiene membresías

**Comportamiento:** se quita; el detalle vuelve con `offerable: false` y el cuarto motivo.

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-002 — El servicio no abre este curso

**Respuesta del sistema:** `404` — *«Ese servicio no abre este curso.»* Se distingue del anterior por el mensaje, como en `RF-AC-017`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-223` | El sistema quita la visibilidad con `200` y devuelve el detalle sin el servicio; la fila no existe; el curso no cambia de estado |
| `CA-AC-224` | `audit_deletion_log` tiene la fila `ASSOCIATION` **sin motivo**, con el curso como entidad, el actor y la pareja con el código del producto en la instantánea |
| `CA-AC-225` | El sistema responde `404` al curso retirado o inexistente y `404` a la pareja inexistente, con mensajes distintos; un servicio **retirado en `PM`** se quita igual; **quitar el último de un curso sin membresías** lo deja con `offerable: false` y el cuarto motivo |
| `CA-AC-226` | Dos retiros simultáneos de la misma pareja dejan un `200` y un `404`, y **una** fila de auditoría |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Quien tenía el curso por ese servicio lo estaba estudiando | Se le cierra en la siguiente lectura del aula. No hay progreso que perder (`ac.md` §1.3) |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| — | Ninguna: la forma es la de `RF-AC-017` y `RF-PM-025` | |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 25-09-2026 | Redacción inicial. Borrado físico de la fila con `ASSOCIATION` sin motivo, como `RF-AC-017`; un servicio retirado se quita igual, y quitar el último nunca se rechaza. | Responsable técnico |
