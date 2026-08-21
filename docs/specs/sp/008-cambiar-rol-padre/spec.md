# SPEC — `RF-SP-008` Cambiar el rol padre de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-008` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Reubicar un rol en la jerarquía, cambiando el rol que acota sus privilegios.

## 2. Contexto

El rol padre cumple dos funciones a la vez: acota los permisos que el rol puede declarar (`RN-SEG-003`) y, en los roles comerciales, expresa el orden de mando (`RN-SP-011`). Moverlo altera ambas cosas.

Es la operación más peligrosa del módulo, porque puede romper un invariante en dos direcciones: el rol podría quedar excediendo a su nuevo padre, o podría formarse un ciclo que dejaría la jerarquía sin raíz.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Reubica cualquier rol que no sea de sistema |
| Administrador | Reubica roles que no tenga asignados |

## 4. Alcance

### 4.1 Incluye

- Asignar un rol padre distinto a un rol existente.
- Revalidación de la contención frente al nuevo padre.
- Verificación de ausencia de ciclos.

### 4.2 No incluye

- Ajustar automáticamente los permisos del rol para que quepan en el nuevo padre: si no caben, la operación se rechaza. Retirarlos sería una revocación implícita, y revocar tiene reglas propias (`RN-SEG-005`).
- Exigir que el nuevo padre sea del mismo tipo que el rol movido: el tipo del padre es indiferente.
- Dejar un rol sin padre: solo el rol raíz carece de él (`RN-SEG-007`, `RN-SP-002`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-003` | Los permisos son subconjunto de los del rol padre | `security.md` §4.3 |
| `RN-SEG-006` | La cadena de roles padre no admite ciclos | `security.md` §4.3 |
| `RN-SEG-007` | Existe exactamente un rol raíz sin padre | `security.md` §4.3 |
| `RN-SEG-013` | Cambiar el rol padre revalida la contención contra el nuevo | `security.md` §4.3 |
| `RN-SEG-011` | Nadie modifica un rol que tiene asignado | `security.md` §4.3 |
| `RN-SEG-012` | Los roles de sistema no se modifican por la API | `security.md` §4.3 |
| `RN-SP-002` | Rol padre obligatorio salvo en el rol raíz | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del rol | Sí | Rol que se reubica | Debe existir, no ser de sistema y no ser el rol raíz |
| Nuevo rol padre | Sí | Rol que pasará a acotarlo | Debe existir, estar activo y no ser descendiente del rol que se mueve. Su clasificación es indiferente: un rol comercial puede colgar de uno funcionario |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Rol | Rol con su nuevo rol padre |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de roles.
- El rol existe, no está eliminado, no es de sistema y no es el rol raíz.
- El actor no tiene ese rol asignado.

**Postcondiciones**

- El rol cuelga del nuevo padre.
- Los permisos del rol siguen contenidos en los del nuevo padre.
- La jerarquía sigue siendo acíclica y con una sola raíz.
- Los roles hijos del rol movido lo acompañan, y su contención sigue siendo válida por transitividad.
- Queda constancia en la auditoría de cambios y en la de seguridad.

## 8. Flujo principal

1. El actor solicita cambiar el rol padre de un rol.
2. El sistema verifica que el rol exista, no sea de sistema y no sea el rol raíz.
3. El sistema verifica que el actor no tenga ese rol asignado.
4. El sistema verifica que el nuevo rol padre exista y esté activo.
5. El sistema verifica que el nuevo padre no sea el propio rol ni uno de sus descendientes.
6. El sistema verifica que los permisos del rol estén contenidos en los del nuevo padre.
7. El sistema aplica el cambio.
8. El sistema invalida la caché de resolución de permisos afectada.
9. El sistema registra el evento en la auditoría de cambios y en la de seguridad.
10. El sistema informa el rol actualizado.

## 9. Flujos alternativos

### FA-001 — El nuevo padre coincide con el actual

**Cuándo ocurre:** se solicita el mismo rol padre que ya tiene.

1. El sistema no aplica cambio ni registra evento.
2. Devuelve el rol sin tratarlo como error.

### FA-002 — Rol sin permisos declarados

**Cuándo ocurre:** el rol que se mueve no declara ningún permiso.

1. La verificación de contención se cumple de forma trivial.
2. El cambio se aplica sin más comprobaciones sobre permisos.

## 10. Excepciones

### EX-001 — El rol excede al nuevo padre

**Condición:** el rol declara permisos que el nuevo padre no tiene.
**Respuesta del sistema:** rechaza la operación, cita `RN-SEG-013` e informa **qué permisos** sobran. Con ese detalle, el actor puede retirarlos con `RF-SP-006` y reintentar.

### EX-002 — Ciclo en la jerarquía

**Condición:** el nuevo padre es el propio rol o uno de sus descendientes.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-006`.

### EX-003 — Rol raíz

**Condición:** se intenta asignar un padre al rol raíz, o dejar sin padre a un rol.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-007`.

### EX-004 — Nuevo padre inexistente o inactivo

**Condición:** el rol padre indicado no existe, está eliminado o está inactivo.
**Respuesta del sistema:** rechaza la operación e informa que el rol padre no es válido.

### EX-005 — Rol de sistema o rol propio del actor

**Condición:** el rol es de sistema, o el actor lo tiene asignado.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-012` o `RN-SEG-011`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Nuevo rol padre obligatorio | El rol padre es obligatorio. |
| `VAL-002` | El nuevo padre existe y está activo | El rol padre indicado no es válido. |
| `VAL-003` | El nuevo padre no es el propio rol ni un descendiente | El cambio formaría un ciclo en la jerarquía. |
| `VAL-004` | Los permisos del rol caben en el nuevo padre | El rol padre indicado no concede uno o más de los permisos del rol. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-056` | El sistema reubica un rol cuyos permisos caben en el nuevo padre |
| `CA-SP-057` | El sistema rechaza la reubicación cuando el rol excede al nuevo padre, e indica qué permisos sobran |
| `CA-SP-058` | El sistema rechaza la reubicación bajo un descendiente del propio rol |
| `CA-SP-059` | El sistema rechaza asignar un rol padre a sí mismo |
| `CA-SP-060` | El sistema rechaza asignar rol padre al rol raíz |
| `CA-SP-061` | El sistema mueve el rol junto con sus hijos, que siguen cumpliendo la contención |
| `CA-SP-062` | El sistema no registra evento cuando el nuevo padre coincide con el actual |
| `CA-SP-063` | El sistema registra el cambio en la auditoría de cambios y en la de seguridad |
| `CA-SP-160` | El sistema admite reubicar un rol comercial bajo un rol funcionario |
| `CA-SP-161` | Dos reubicaciones simultáneas que formarían un ciclo no llegan a producirlo |
| `CA-SP-162` | El sistema no retira permisos del rol al reubicarlo, ni siquiera los que sobran |

## 13. Casos límite

- **Cadena profunda:** la detección de ciclos recorre la descendencia; conviene acotar la profundidad para que una jerarquía corrupta no provoque un recorrido infinito.
- **Rol con hijos que exceden al nuevo abuelo:** imposible por transitividad. Si el rol cabe en el nuevo padre, sus hijos también.
- **Mover un rol comercial:** altera además su posición en el orden de mando (`RN-SP-011`), que se lee entre los roles comerciales de la cadena. El tipo del nuevo padre no se verifica.
- **Nuevo padre eliminado lógicamente:** se trata como inexistente.
- **Reubicación concurrente que formaría ciclo entre dos ramas:** imposible, porque las reubicaciones se serializan. Sin esa serialización, dos operaciones podrían validarse por separado y cerrar el ciclo al aplicarse, cada una correcta en aislamiento.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se retiran los permisos sobrantes? | **No, solo se rechaza.** Retirarlos sería una revocación implícita, y revocar tiene reglas propias: `RN-SEG-005` rechaza retirar un permiso que un rol hijo declara. Es el mismo criterio con el que se descartó el reemplazo en `RF-SP-005` y la cascada en `RF-SP-006`. El rechazo ya enumera qué permisos sobran |
| 2 | ¿Puede un rol comercial colgar de uno funcionario? | **Sí, el tipo del padre es indiferente.** El catálogo aprobado ya lo exige: `MANAGER` es comercial y cuelga de `ADMIN`, que es funcionario. La cabeza de la cadena comercial tiene que colgar de algo, y es coherente con `RF-SP-001`, donde la clasificación es independiente de la del padre |
| 3 | ¿Cómo se evita el ciclo por concurrencia? | **Serializando las reubicaciones** con un bloqueo aplicativo único para toda mutación de la jerarquía. Dos reubicaciones nunca se solapan y el ciclo es imposible por construcción. Reubicar un rol es una operación rara, de modo que serializarla no cuesta nada |
