# SPEC — `RF-SP-009` Eliminar rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-009` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Enmendada | 21-08-2026 — la verificación de usuarios deja de depender de `USR`, retirado como módulo (Art. I.7) |
| Fecha de aprobación | 21-08-2026 |
| Enmendada | 21-08-2026 — `EX-006` y `CA-SP-176`, al aprobar `plan.md` (Art. I.7) |

---

## 1. Objetivo

Retirar del sistema un rol que dejó de tener sentido, dejando constancia de por qué se retiró y de qué era.

## 2. Contexto

La eliminación es lógica: el registro se conserva marcado como eliminado. Esa decisión sostiene dos cosas. La primera, que la auditoría siga pudiendo resolver qué rol era el que aparece en un evento antiguo. La segunda, que el código y el nombre queden **liberados para reutilizarse** (`RN-SEG-001`), cosa que una eliminación física también permitiría pero perdiendo el rastro.

A diferencia de la revocación de un permiso, aquí sí se elimina una entidad de negocio, de modo que el **motivo es obligatorio** (Art. V.13) y se conserva el estado del registro al momento de eliminarse.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Elimina cualquier rol que no sea de sistema |
| Administrador | Elimina roles que no tenga asignados |

## 4. Alcance

### 4.1 Incluye

- Eliminación lógica de un rol sin roles hijos ni usuarios asignados.
- Registro obligatorio del motivo y del estado del rol al eliminarse.

### 4.2 No incluye

- Eliminación física.
- Reasignar los usuarios del rol a otro: debe hacerse antes, con `RF-SP-030` y `RF-SP-031`.
- Reubicar los roles hijos: debe hacerse antes, con `RF-SP-008`.
- Restaurar un rol eliminado. El borrado lógico existe para que la auditoría pueda resolver qué rol era, no como papelera: al eliminarlo se libera su código y su nombre, y su rol padre pudo cambiar o desaparecer. Si el rol vuelve a hacer falta, se crea otro.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-008` | No se elimina un rol con hijos o con usuarios asignados | `security.md` §4.3 |
| `RN-SEG-011` | Nadie modifica un rol que tiene asignado | `security.md` §4.3 |
| `RN-SEG-012` | Los roles de sistema no se eliminan por la API | `security.md` §4.3 |
| `RN-SP-001` | Debe existir siempre un usuario con el rol raíz | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del rol | Sí | Rol que se elimina | Debe existir, no ser de sistema y no ser el rol raíz |
| Motivo | **Sí** | Razón de la eliminación, declarada por el actor | No puede quedar vacío tras recortar los extremos. No se admite un valor generado por el sistema |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Confirmación | Resultado de la operación, sin cuerpo de datos |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de eliminación de roles.
- El rol existe, no está eliminado, no es de sistema y no es el rol raíz.
- El rol no tiene roles hijos vigentes ni usuarios asignados.
- El actor no tiene ese rol asignado.

**Postcondiciones**

- El rol queda marcado como eliminado y deja de aparecer en consultas y de poder asignarse.
- Su código y su nombre quedan disponibles para un rol nuevo.
- Sus asociaciones con permisos dejan de tener efecto.
- Queda constancia en la auditoría de eliminación, con el motivo y el estado del rol, y en la de seguridad.

## 8. Flujo principal

1. El actor solicita eliminar un rol y declara el motivo.
2. El sistema verifica que el motivo venga informado y tenga contenido.
3. El sistema verifica que el rol exista, no sea de sistema y no sea el rol raíz.
4. El sistema verifica que el actor no tenga ese rol asignado.
5. El sistema verifica que el rol no tenga roles hijos vigentes.
6. El sistema verifica que el rol no tenga usuarios asignados.
7. El sistema marca el rol como eliminado.
8. El sistema invalida la caché de resolución de permisos del rol.
9. El sistema registra el evento en la auditoría de eliminación, con motivo y estado del rol, y en la de seguridad.
10. El sistema confirma la eliminación.

## 9. Flujos alternativos

Ninguno. La eliminación no admite variantes: o se cumplen todas las condiciones, o se rechaza.

## 10. Excepciones

### EX-001 — Motivo ausente o insuficiente

**Condición:** no se declara motivo, o el texto no tiene contenido real.
**Respuesta del sistema:** rechaza la operación **antes de ejecutarla** y explica que el motivo es obligatorio (Art. V.13).

### EX-002 — El rol tiene roles hijos

**Condición:** existe al menos un rol vigente cuyo rol padre es este.
**Respuesta del sistema:** rechaza la operación, cita `RN-SEG-008` e informa qué roles lo impiden.

### EX-003 — El rol tiene usuarios asignados

**Condición:** al menos un usuario tiene el rol, **activo o inactivo**.
**Respuesta del sistema:** rechaza la operación, cita `RN-SEG-008` e informa cuántos usuarios lo tienen. Sugiere desactivarlo con `RF-SP-007` si el objetivo es retirar el acceso.

### EX-004 — Rol de sistema o rol raíz

**Condición:** el rol está marcado como de sistema, o es el rol raíz.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-012` o `RN-SEG-007`.

### EX-005 — El actor tiene el rol asignado

**Condición:** el rol está entre los del actor.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-011`.

### EX-006 — Rol inexistente

**Condición:** no existe un rol vigente con el identificador indicado, o ya está eliminado lógicamente.
**Respuesta del sistema:** rechaza la operación e informa que el rol no existe, sin distinguir entre nunca haber existido y haber sido eliminado (Art. V.13). No debe confundirse con `EX-004`, que es el rol de sistema o el rol raíz. Añadida el 21-08-2026 al aprobar el `plan.md` (Art. I.7).

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Motivo obligatorio | Debe indicar el motivo de la eliminación. |
| `VAL-002` | Motivo no vacío tras recortar los extremos | Debe indicar el motivo de la eliminación. |
| `VAL-003` | Sin roles hijos vigentes | No es posible eliminar el rol: tiene roles dependientes. |
| `VAL-004` | Sin usuarios asignados | No es posible eliminar el rol: hay usuarios que lo tienen asignado. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-064` | El sistema elimina lógicamente un rol sin hijos ni usuarios asignados |
| `CA-SP-065` | El sistema rechaza la eliminación sin motivo, antes de ejecutarla |
| `CA-SP-066` | El sistema rechaza la eliminación de un rol con roles hijos, e indica cuáles |
| `CA-SP-067` | El sistema rechaza la eliminación de un rol con usuarios asignados |
| `CA-SP-068` | El sistema rechaza la eliminación del rol raíz y de los roles de sistema |
| `CA-SP-069` | El código y el nombre del rol eliminado quedan disponibles para un rol nuevo |
| `CA-SP-070` | El rol eliminado deja de aparecer en el listado por defecto |
| `CA-SP-071` | La auditoría de eliminación conserva el motivo y el estado del rol al eliminarse |
| `CA-SP-072` | El sistema registra el evento también en la auditoría de seguridad |
| `CA-SP-163` | El sistema rechaza la eliminación cuando el usuario que tiene el rol está **inactivo** |
| `CA-SP-164` | El sistema no expone ninguna operación de restauración |
| `CA-SP-165` | Eliminar el rol y asignárselo a alguien de forma simultánea no deja usuarios apuntando a un rol eliminado |
| `CA-SP-176` | El sistema rechaza la eliminación de un rol inexistente o ya eliminado, con un error distinto del de rol de sistema |

## 13. Casos límite

- **Rol con hijos eliminados lógicamente:** no impiden la eliminación, porque no están vigentes.
- **Rol asignado solo a usuarios inactivos:** impide la eliminación igual que si estuvieran activos. Reactivar a esa persona la dejaría con un rol inexistente.
- **Eliminar un rol ya eliminado:** se trata como inexistente.
- **Motivo con solo espacios:** se rechaza tras recortar los extremos.
- **Eliminación concurrente con una asignación de usuario:** ambas se serializan sobre la fila del rol, de modo que la carrera no llega a producirse. Exige que `RF-SP-030` respete ese contrato al asignar.
- **Rol eliminado que aparece en auditoría antigua:** su identificador debe seguir resolviendo al registro conservado.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Existe restauración? | **No.** El borrado lógico existe para que la auditoría pueda resolver qué rol era, no como papelera. Al eliminarlo se libera su código y su nombre, de modo que otro rol pudo tomarlos, y su rol padre pudo cambiar o desaparecer. Si vuelve a hacer falta, se crea otro |
| 2 | ¿Un usuario inactivo impide eliminar? | **Sí**, igual que uno activo. Mismo criterio que en `RF-SP-006` con los roles hijos inactivos: si no impidiera, reactivar a esa persona la dejaría con un rol que ya no existe, una referencia que no lleva a ninguna parte |
| 3 | ¿Cómo se evita la carrera con la asignación? | **Bloqueo de fila sobre el rol.** La eliminación toma un bloqueo exclusivo y la asignación uno compartido, de modo que ambas se serializan sobre la misma fila sin afectar a nada más. `SP` debe publicar ese contrato junto con la interfaz de conteo |
| 4 | ¿Longitud mínima del motivo? | **Solo que no esté vacío** tras recortar los extremos. Se relaja la restricción de diez caracteres que `architecture.md` declaraba. La garantía queda formal: obliga a escribir algo, no a que ese algo informe |
