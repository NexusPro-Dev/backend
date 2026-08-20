# SPEC — `RF-SP-007` Cambiar el estado de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-007` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Suspender temporalmente un rol sin perder su definición ni sus asignaciones, y poder reactivarlo después.

## 2. Contexto

A veces hay que retirar el acceso que concede un rol sin desmontarlo: una reorganización, un área que se detiene, un rol creado por error al que ya se le asignaron personas. Eliminarlo obligaría a rehacerlo y a reasignarlo; desactivarlo conserva todo y es reversible.

Un rol inactivo **deja de conceder permisos de inmediato**, aunque siga asignado a usuarios (`RN-SEG-002`). Esa inmediatez es lo que distingue esta operación: no basta con marcar un campo, hay que invalidar la resolución de permisos en curso.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Cambia el estado de cualquier rol que no sea de sistema |
| Administrador | Cambia el estado de roles que no tenga asignados |

## 4. Alcance

### 4.1 Incluye

- Activar un rol inactivo y desactivar uno activo.

### 4.2 No incluye

- Eliminar el rol → `RF-SP-009`.
- Retirar el rol a los usuarios que lo tienen: siguen teniéndolo asignado, pero deja de concederles nada.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-002` | Un rol inactivo no concede permisos aunque siga asignado | `security.md` §4.3 |
| `RN-SEG-011` | Nadie modifica un rol que tiene asignado | `security.md` §4.3 |
| `RN-SEG-012` | Los roles de sistema no se modifican por la API | `security.md` §4.3 |
| `RN-SP-001` | Debe existir siempre un usuario con el rol raíz | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del rol | Sí | Rol cuyo estado cambia | Debe existir y no ser de sistema |
| Estado | Sí | Nuevo estado | Activo o inactivo |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Rol | Rol con su estado actualizado |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de roles.
- El rol existe, no está eliminado y no es de sistema.
- El actor no tiene ese rol asignado.

**Postcondiciones**

- El rol queda en el estado solicitado.
- Si quedó inactivo, deja de conceder permisos de forma inmediata a todos sus portadores.
- Sus asignaciones a usuarios se conservan intactas.
- Queda constancia en la auditoría de cambios y en la de seguridad.

## 8. Flujo principal

1. El actor solicita cambiar el estado de un rol.
2. El sistema verifica que el rol exista y no sea de sistema.
3. El sistema verifica que el actor no tenga ese rol asignado.
4. El sistema aplica el nuevo estado.
5. El sistema invalida la caché de resolución de permisos del rol.
6. El sistema registra el evento en la auditoría de cambios y en la de seguridad.
7. El sistema informa el rol actualizado.

## 9. Flujos alternativos

### FA-001 — El rol ya está en ese estado

**Cuándo ocurre:** se solicita activar un rol activo, o desactivar uno inactivo.

1. El sistema no aplica cambio ni registra evento.
2. Devuelve el rol sin tratarlo como error: la operación es idempotente.

## 10. Excepciones

### EX-001 — Rol de sistema

**Condición:** el rol está marcado como de sistema.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-012`.

### EX-002 — El actor tiene el rol asignado

**Condición:** el rol está entre los del actor.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-011`. Evita que alguien se desactive su propio acceso.

### EX-003 — Rol inexistente

**Condición:** el identificador no corresponde a ningún rol vigente.
**Respuesta del sistema:** informa que el rol no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Estado obligatorio y dentro del dominio | El estado indicado no es válido. |
| `VAL-002` | Rol existente y no eliminado | El rol solicitado no existe. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-049` | El sistema desactiva un rol activo y lo reactiva después |
| `CA-SP-050` | Un rol inactivo deja de conceder permisos a sus portadores de forma inmediata |
| `CA-SP-051` | Las asignaciones del rol a usuarios se conservan tras desactivarlo |
| `CA-SP-052` | El sistema no registra evento cuando el rol ya estaba en el estado solicitado |
| `CA-SP-053` | El sistema rechaza la operación sobre un rol de sistema |
| `CA-SP-054` | El sistema rechaza la operación sobre un rol que el propio actor tiene asignado |
| `CA-SP-055` | El sistema registra el cambio en la auditoría de cambios y en la de seguridad |

## 13. Casos límite

- **Desactivar un rol padre de roles activos:** ver pregunta abierta 1. Es el caso que más consecuencias tiene.
- **Rol inactivo como rol padre en un alta:** `RF-SP-001` ya lo rechaza.
- **Usuario cuyo único rol se desactiva:** queda autenticado pero sin permisos efectivos. No es un error, pero conviene que la interfaz lo explique.
- **Rol eliminado lógicamente:** se trata como inexistente.
- **Token vigente de un portador:** los permisos se resuelven desde el rol, de modo que el cambio aplica sin esperar a que expire el token.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | Al desactivar un rol padre, ¿qué ocurre con sus hijos activos? Siguen concediendo permisos que su padre ya no concede. ¿Se desactivan en cascada, se rechaza la operación, o se admite? | Responsable técnico | Abierta |
| 2 | ¿Debe exigirse un motivo al desactivar? No es una eliminación, de modo que el Art. V.13 no lo obliga, pero es información útil | Responsable técnico | Abierta |
| 3 | ¿Puede desactivarse el rol raíz? `RN-SP-001` exige un usuario con ese rol, pero no dice que el rol deba estar activo | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
