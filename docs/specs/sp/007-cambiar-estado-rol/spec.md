# SPEC — `RF-SP-007` Cambiar el estado de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-007` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Suspender temporalmente un rol sin perder su definición ni sus asignaciones, y poder reactivarlo después.

## 2. Contexto

A veces hay que retirar el acceso que concede un rol sin desmontarlo: una reorganización, un área que se detiene, un rol creado por error al que ya se le asignaron personas. Eliminarlo obligaría a rehacerlo y a reasignarlo; desactivarlo conserva todo y es reversible.

Un rol inactivo **deja de conceder permisos de inmediato**, aunque siga asignado a usuarios (`RN-SEG-002`). Esa inmediatez es lo que distingue esta operación: no basta con marcar un campo, hay que invalidar la resolución de permisos en curso.

El efecto alcanza **solo a quienes tienen ese rol**. Desactivar un rol padre no toca a sus roles hijos: la contención se mide sobre permisos **declarados**, y desactivar no cambia lo que un rol declara, solo lo que concede. Suspender a `MANAGER` no debe dejar sin acceso a los directores, que son personas distintas con otro rol.

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
- Desactivar los roles hijos. La operación no se propaga hacia abajo ni se rechaza por tener descendencia.

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

### EX-001 — Rol de sistema o rol raíz

**Condición:** el rol está marcado como de sistema, o es el rol raíz.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-012`.

El rol raíz se nombra de forma explícita aunque hoy sea de sistema y `RN-SEG-012` ya lo cubra: un rol raíz inactivo no concede nada (`RN-SEG-002`), lo que dejaría al sistema sin su última vía de administración. La prohibición no debe depender de que alguien recuerde marcarlo como de sistema.

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
| `CA-SP-157` | Desactivar un rol padre no altera el estado de sus roles hijos, que siguen concediendo sus permisos |
| `CA-SP-158` | El sistema rechaza desactivar el rol raíz |
| `CA-SP-159` | La operación no solicita ni admite un motivo |

## 13. Casos límite

- **Desactivar un rol padre de roles activos:** se admite y no altera a los hijos. Quienes tengan el rol padre pierden lo que este concedía; quienes tengan un rol hijo conservan lo suyo.
- **Rol padre inactivo como cota:** `RF-SP-005` valida la contención contra los permisos **declarados** por el padre, que no cambian al desactivarlo. Ampliar un rol cuyo padre está inactivo sigue siendo válido, y conviene que el plan lo deje escrito para que nadie añada una comprobación de estado que la spec no pide.
- **Rol inactivo como rol padre en un alta:** `RF-SP-001` ya lo rechaza.
- **Usuario cuyo único rol se desactiva:** queda autenticado pero sin permisos efectivos. No es un error, pero conviene que la interfaz lo explique.
- **Rol eliminado lógicamente:** se trata como inexistente.
- **Token vigente de un portador:** los permisos se resuelven desde el rol, de modo que el cambio aplica sin esperar a que expire el token.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Qué ocurre con los hijos activos? | **Nada.** Desactivar suspende lo que el rol concede a quienes lo tienen, no a los demás. La contención se mide sobre permisos declarados, y desactivar no cambia lo que un rol declara: ningún hijo queda excediendo a su padre. La pregunta estaba mal planteada en el borrador, que sugería un conflicto inexistente |
| 2 | ¿Se exige motivo? | **No.** El Art. V.13 lo obliga solo en las eliminaciones, y por una razón concreta: el registro desaparece. Aquí el rol sigue existiendo y la auditoría ya guarda quién y cuándo. Exigirlo crearía un patrón nuevo para una sola operación |
| 3 | ¿Puede desactivarse el rol raíz? | **No**, y se dice de forma explícita. Hoy `RN-SEG-012` ya lo impide por ser de sistema, pero la prohibición no debe depender de que alguien recuerde marcarlo así: un rol raíz inactivo no concede nada y dejaría al sistema sin su última vía de administración |
