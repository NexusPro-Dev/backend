# SPEC — `RF-SP-006` Revocar permisos de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-006` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Reducir el alcance de un rol retirándole permisos, sin romper la contención de los roles que dependen de él.

## 2. Contexto

Retirar un permiso es más delicado que concederlo. Al conceder, el conjunto del rol crece y ningún hijo queda fuera de su cota. Al retirar, el conjunto **encoge**, y cualquier rol hijo que declarase ese permiso quedaría de pronto excediendo a su padre.

El sistema **rechaza** la operación en ese caso, en lugar de revocar en cascada. Una cascada silenciosa quitaría privilegios que nadie pidió quitar, y el efecto se descubriría cuando alguien dejara de poder trabajar.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Revoca permisos de cualquier rol que no sea de sistema |
| Administrador | Revoca permisos de roles que no tenga asignados |

## 4. Alcance

### 4.1 Incluye

- Retirar uno o varios permisos de un rol.
- Verificación de que ningún rol hijo directo declara el permiso que se retira, **esté activo o inactivo**.

### 4.2 No incluye

- Agregar permisos → `RF-SP-005`.
- Revocación en cascada sobre los roles descendientes. Si alguna vez se ofreciera, sería un requerimiento propio: cada rol afectado necesitaría su evento de auditoría y su verificación de contención.
- Eliminar el permiso del catálogo, que es inmutable (`RN-SP-004`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-005` | La revocación se rechaza si un rol descendiente declara el permiso | `security.md` §4.3 |
| `RN-SEG-011` | Nadie modifica un rol que tiene asignado | `security.md` §4.3 |
| `RN-SEG-012` | Los roles de sistema no se modifican por la API | `security.md` §4.3 |
| `RN-SP-005` | La revocación es una eliminación física y no exige motivo | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del rol | Sí | Rol al que se retiran permisos | Debe existir y no ser de sistema |
| Permisos | Sí | Permisos a retirar | Al menos uno |

No se solicita motivo: se trata de una asociación, no de una entidad de negocio (Art. V.13).

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Rol | Rol con su lista de permisos actualizada |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de roles.
- El rol existe, no está eliminado y no es de sistema.
- El actor no tiene ese rol asignado.

**Postcondiciones**

- Los permisos quedan desasociados del rol, con eliminación física de la asociación.
- Ningún rol descendiente queda excediendo a su padre.
- Queda constancia en la auditoría de eliminación, sin motivo y con los códigos de rol y de permiso legibles, y en la de seguridad.

## 8. Flujo principal

1. El actor solicita retirar permisos de un rol.
2. El sistema verifica que el rol exista y no sea de sistema.
3. El sistema verifica que el actor no tenga ese rol asignado.
4. El sistema verifica que ningún rol hijo directo, activo o inactivo, declare alguno de los permisos que se retiran.
5. El sistema elimina las asociaciones.
6. El sistema invalida la caché de resolución de permisos del rol.
7. El sistema registra el evento en la auditoría de eliminación y en la de seguridad.
8. El sistema informa el rol con sus permisos actualizados.

## 9. Flujos alternativos

### FA-001 — Permisos no asociados

**Cuándo ocurre:** alguno de los permisos no lo declaraba el rol.

1. El sistema ignora los que no estaban asociados.
2. La operación es **idempotente**: repetirla no produce error.

## 10. Excepciones

### EX-001 — Un rol descendiente declara el permiso

**Condición:** algún rol hijo directo declara alguno de los permisos que se retiran, con independencia de su estado.
**Respuesta del sistema:** rechaza la operación completa, cita `RN-SEG-005` e informa **qué roles** lo impiden y **qué permisos** son. Sin ese detalle, el actor no sabría qué corregir.

### EX-002 — Rol de sistema

**Condición:** el rol está marcado como de sistema.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-012`.

### EX-003 — El actor tiene el rol asignado

**Condición:** el rol está entre los del actor.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-011`.

### EX-004 — Rol inexistente

**Condición:** el identificador no corresponde a ningún rol vigente.
**Respuesta del sistema:** informa que el rol no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Al menos un permiso informado | Debe indicar al menos un permiso. |
| `VAL-002` | Identificadores de permiso con formato válido | El identificador de permiso no es válido. |
| `VAL-003` | Ningún rol descendiente declara el permiso | No es posible retirar el permiso: lo declaran uno o más roles dependientes. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-041` | El sistema retira los permisos indicados de un rol sin roles hijos que los declaren |
| `CA-SP-042` | El sistema rechaza la revocación cuando un rol hijo declara el permiso, e indica qué roles y qué permisos |
| `CA-SP-043` | El sistema no revoca en cascada sobre los roles descendientes |
| `CA-SP-044` | El sistema ignora los permisos que el rol no declaraba, sin producir error |
| `CA-SP-045` | El sistema registra el evento en la auditoría de eliminación **sin motivo declarado** |
| `CA-SP-046` | El sistema elimina físicamente la asociación, no de forma lógica |
| `CA-SP-047` | El sistema rechaza la operación sobre un rol de sistema o sobre un rol propio del actor |
| `CA-SP-048` | El sistema deja sin efecto la caché de permisos, de modo que el cambio aplica de inmediato |
| `CA-SP-155` | El sistema rechaza la revocación cuando el rol hijo que declara el permiso está **inactivo** |
| `CA-SP-156` | El estado conservado en la auditoría incluye los códigos de rol y de permiso, legibles sin resolver referencias |

## 13. Casos límite

- **Operación parcialmente válida:** se rechaza entera, igual que en `RF-SP-005`.
- **Retirar todos los permisos:** válido. El rol queda existiendo sin conceder nada.
- **Rol hijo inactivo que declara el permiso:** impide la revocación igual que uno activo. El invariante de contención vale siempre, no solo mientras el rol concede permisos.
- **Rol hijo eliminado lógicamente que lo declara:** no debería impedir la revocación, ya que el rol no está vigente.
- **Rol ancestro del propio actor:** puede revocarse. `RN-SEG-011` solo alcanza a los roles asignados directamente.
- **Nieto que declara el permiso pero el hijo no:** imposible por la transitividad de la contención; si el hijo no lo tiene, el nieto tampoco puede tenerlo.
- **Revocación concurrente del mismo permiso:** la segunda no encuentra la asociación y se comporta como el flujo alternativo, sin error.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Un rol hijo inactivo impide la revocación? | **Sí.** El invariante `permisos(hijo) ⊆ permisos(padre)` vale siempre, no solo mientras el rol concede permisos. Si un hijo inactivo no bloqueara, reactivarlo con `RF-SP-007` produciría un rol que excede a su padre sin que ninguna operación hubiera violado `RN-SEG-003`, y habría que añadir esa verificación a la reactivación |
| 2 | ¿Se ofrece revocación en cascada? | **No**, solo el rechazo. Ya informa qué roles lo impiden, de modo que el actor sabe qué corregir. Una cascada es un cambio masivo de privilegios y merece requerimiento propio: cada rol afectado necesitaría su evento de auditoría y su verificación de contención |
| 3 | ¿Basta con los dos identificadores en la auditoría? | **No**, se guardan también los códigos de rol y de permiso. El Art. V.13 existe porque saber que algo se borró no sirve si ya no puede saberse qué era, y dos identificadores obligan a resolver referencias que pueden haber desaparecido |
