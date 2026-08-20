# SPEC — `RF-SP-004` Editar rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-004` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Corregir el nombre o la descripción de un rol cuando dejan de reflejar lo que el rol representa.

## 2. Contexto

Un rol se crea con un nombre que puede quedar desfasado —una reorganización, un cambio de denominación de un área—. Ese cambio no altera lo que el rol permite hacer, y por eso se separa de las operaciones que sí lo alteran.

**Esta funcionalidad no modifica permisos, estado ni rol padre.** Cada una de esas operaciones tiene sus propias reglas de rechazo y su propio requerimiento: `RF-SP-005`, `RF-SP-006`, `RF-SP-007` y `RF-SP-008`. Agruparlas en una sola edición haría imposible especificar por separado cuándo se rechaza cada una.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Edita cualquier rol que no sea de sistema |
| Administrador | Edita roles que no tenga asignados y que no sean de sistema |

## 4. Alcance

### 4.1 Incluye

- Modificación del nombre y de la descripción.

### 4.2 No incluye

- El código, que es estable por diseño: cambiarlo rompería cualquier referencia externa.
- Los permisos → `RF-SP-005` y `RF-SP-006`.
- El estado → `RF-SP-007`.
- El rol padre → `RF-SP-008`.
- La clasificación: ver pregunta abierta 1.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-001` | Nombre único entre los no eliminados | `security.md` §4.3 |
| `RN-SEG-011` | Nadie modifica un rol que tiene asignado | `security.md` §4.3 |
| `RN-SEG-012` | Los roles de sistema no se modifican por la API | `security.md` §4.3 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador | Sí | Rol que se edita | Debe existir y no ser de sistema |
| Nombre | No | Nuevo nombre | Único entre los roles no eliminados |
| Descripción | No | Nueva descripción | — |

Al menos uno de los dos campos modificables debe venir informado.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Rol | Rol con sus datos actualizados |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de roles.
- El rol existe, no está eliminado y no es de sistema.
- El actor no tiene ese rol asignado.

**Postcondiciones**

- El rol conserva su código, sus permisos, su estado y su rol padre.
- Queda constancia del cambio en la auditoría de cambios y en la de seguridad, con el detalle de qué campos se modificaron.

## 8. Flujo principal

1. El actor solicita editar un rol y proporciona los campos a modificar.
2. El sistema verifica que el rol exista y no sea de sistema.
3. El sistema verifica que el actor no tenga ese rol asignado.
4. El sistema verifica que el nombre nuevo no esté en uso.
5. El sistema aplica los cambios.
6. El sistema registra el evento en la auditoría de cambios, con el antes y el después de cada campo modificado, y en la de seguridad.
7. El sistema informa el rol actualizado.

## 9. Flujos alternativos

### FA-001 — Edición sin cambio efectivo

**Cuándo ocurre:** los valores enviados coinciden con los actuales.

1. El sistema no registra evento de auditoría, porque nada cambió.
2. Devuelve el rol sin modificar, sin tratarlo como error.

## 10. Excepciones

### EX-001 — Rol de sistema

**Condición:** el rol está marcado como de sistema.
**Respuesta del sistema:** rechaza la edición y cita `RN-SEG-012`.

### EX-002 — El actor tiene el rol asignado

**Condición:** el rol que se edita está entre los del actor.
**Respuesta del sistema:** rechaza la edición y cita `RN-SEG-011`.

### EX-003 — Nombre ya en uso

**Condición:** otro rol no eliminado ya usa ese nombre.
**Respuesta del sistema:** rechaza la edición e informa el conflicto.

### EX-004 — Rol inexistente

**Condición:** el identificador no corresponde a ningún rol vigente.
**Respuesta del sistema:** informa que el rol no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Al menos un campo modificable informado | Debe indicar al menos un campo a modificar. |
| `VAL-002` | Nombre no vacío si se envía | El nombre del rol no puede estar vacío. |
| `VAL-003` | Nombre único entre no eliminados | Ya existe un rol con ese nombre. |
| `VAL-004` | Longitud máxima de nombre y descripción | El campo excede la longitud permitida. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-023` | El sistema modifica el nombre y la descripción de un rol con datos válidos |
| `CA-SP-024` | El sistema conserva sin cambios el código, los permisos, el estado y el rol padre |
| `CA-SP-025` | El sistema rechaza la edición de un rol de sistema |
| `CA-SP-026` | El sistema rechaza la edición de un rol que el propio actor tiene asignado |
| `CA-SP-027` | El sistema rechaza el nombre ya usado por otro rol no eliminado |
| `CA-SP-028` | El sistema permite el nombre de un rol eliminado lógicamente |
| `CA-SP-029` | El sistema registra en la auditoría de cambios solo los campos que cambiaron, con su antes y después |
| `CA-SP-030` | El sistema no registra evento cuando los valores enviados coinciden con los actuales |

## 13. Casos límite

- **Nombre igual al actual:** no es conflicto consigo mismo; la unicidad se verifica contra los demás roles.
- **Descripción a vacío:** debe poder borrarse, ya que es opcional.
- **Rol eliminado lógicamente:** se trata como inexistente.
- **Edición concurrente:** dos ediciones simultáneas del mismo rol; ver pregunta abierta 2.
- **Nombre solo con espacios:** se rechaza por validación tras recortar los extremos.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | ¿La clasificación (`FUNCIONARIO`, `VENDEDOR`, `CONSUMIDOR`) es editable? Cambiarla altera qué puede hacer el rol —solo los consumidores llevan membresía— así que probablemente merezca su propio requerimiento | Responsable técnico | Abierta |
| 2 | ¿Se controla la edición concurrente con versión optimista, o gana el último en escribir? | Responsable técnico | Abierta |
| 3 | `RN-SEG-011` prohíbe modificar un rol propio. ¿Alcanza también a los roles ancestros del propio, o solo a los asignados directamente? | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
