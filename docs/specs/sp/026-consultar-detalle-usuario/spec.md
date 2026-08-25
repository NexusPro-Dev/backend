# SPEC — `RF-SP-026` Consultar detalle de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-026` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Conocer el acceso exacto de una persona —qué roles porta, qué puede hacer con ellos y en qué estado está su cuenta— antes de modificarlo.

## 2. Contexto

Es la pantalla que responde «¿qué puede hacer esta persona?», y es la contraria de `RF-SP-003`: allí se pregunta por el alcance de un rol, aquí por el de alguien concreto.

La diferencia importante está en los **permisos efectivos**. `RF-SP-003` devuelve los permisos que un rol declara; una persona porta varios roles y sus permisos efectivos son la **unión** de los de sus roles activos (`RN-SEG-009`). Esa unión no se puede deducir mirando los roles por separado, y es justamente lo que hay que saber antes de retirarle uno: quitar un rol no siempre quita un permiso, porque otro rol puede seguir concediéndolo.

Es también la pantalla a la que se acude cuando alguien no puede entrar, de modo que debe explicar **por qué**: si está inactivo, si está bloqueado y hasta cuándo.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Consulta cualquier usuario |
| Administrador | Consulta cualquier usuario |

## 4. Alcance

### 4.1 Incluye

- Datos de identificación de la persona: nombre de usuario, correo, nombre y estado.
- Sus roles asignados y sus **permisos efectivos**, resueltos como la unión de los permisos de sus roles activos.
- Su membresía vigente, cuando la tiene.
- El contexto de su acceso: último inicio de sesión y, si está bloqueada, hasta cuándo.

### 4.2 No incluye

- Cualquier dato de la credencial. Ni el hash, ni su antigüedad, ni nada derivado de ella.
- El historial de sesiones ni la lista de sesiones abiertas. Quién entró y cuándo es una pregunta de la auditoría de seguridad → `RF-SP-014`.
- Quién le asignó cada rol y cuándo: reside en la auditoría (Art. V.7) y se consulta con `RF-SP-011`.
- Los usuarios eliminados lógicamente, que se tratan como inexistentes.
- El **número de intentos fallidos** acumulados de la cuenta: se devuelve cuándo expira el bloqueo, no cuántos intentos le quedan a nadie.
- La consulta del **propio perfil** por parte de quien no tiene el permiso de lectura de usuarios → `RF-SP-039`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-009` | Los permisos efectivos son la unión de los permisos de los roles **activos** | `security.md` §4.3 |
| `RN-SEG-002` | Un rol inactivo no concede permisos aunque siga asignado | `security.md` §4.3 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador | Sí | Usuario que se consulta | Debe existir y no estar eliminado |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Usuario | Nombre de usuario, correo, nombre y estado |
| Roles | Roles asignados, cada uno con su estado, para que se vea cuáles no están concediendo nada |
| Permisos efectivos | Unión de los permisos de sus roles **activos**, ya resuelta |
| Membresía | Membresía vigente y su nivel, cuando la persona tiene una |
| Contexto de acceso | Último inicio de sesión y, si está bloqueada, el momento en que expira el bloqueo |
| Fechas de creación y modificación | Contexto mínimo. El **actor** de esos cambios no se devuelve: reside en la auditoría (Art. V.7) |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de usuarios.
- El usuario existe y no está eliminado.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el detalle de un usuario.
2. El sistema recupera al usuario con sus roles y su membresía vigente.
3. El sistema resuelve los permisos efectivos como la unión de los permisos de sus roles activos.
4. El sistema devuelve el detalle completo.

## 9. Flujos alternativos

### FA-001 — Usuario sin permisos efectivos

**Cuándo ocurre:** todos los roles de la persona están inactivos.

1. El sistema devuelve la lista de roles con su estado, y la de permisos efectivos **vacía**.
2. Es un estado válido: la persona puede autenticarse pero no puede hacer nada (`RN-SEG-002`).

*(Hasta el 24-08-2026 este flujo cubría también «la persona se creó sin roles o se le retiraron todos». `RN-SP-023` eliminó ese estado: el alta exige al menos un rol y el retiro no admite quitar el último. La lista de roles **vacía** ya no es alcanzable por la API; si aparece, es un dato incoherente anterior a la regla.)*

### FA-002 — Usuario con todos sus roles inactivos

**Cuándo ocurre:** la persona porta roles, pero ninguno está activo.

1. El sistema devuelve los roles asignados, cada uno marcado como inactivo.
2. La lista de permisos efectivos queda **vacía** (`RN-SEG-002`).
3. Es la respuesta que explica por qué esa persona no puede hacer nada pese a tener roles.

## 10. Excepciones

### EX-001 — Usuario inexistente

**Condición:** el identificador no corresponde a ningún usuario, o el usuario está eliminado lógicamente.
**Respuesta del sistema:** informa que el usuario no existe, sin distinguir entre nunca haber existido y haber sido eliminado.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador del usuario no es válido. |
| `VAL-002` | Usuario existente y no eliminado | El usuario solicitado no existe. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-212` | El sistema devuelve al usuario con sus roles y su estado |
| `CA-SP-213` | El sistema devuelve los permisos efectivos como la unión de los permisos de sus roles activos, sin duplicados |
| `CA-SP-214` | Los permisos de un rol inactivo **no** aparecen entre los permisos efectivos, aunque el rol siga asignado |
| `CA-SP-215` | El sistema devuelve las listas de roles y permisos vacías cuando la persona no tiene roles |
| `CA-SP-216` | El sistema devuelve la membresía vigente cuando la persona tiene una, y nada cuando no |
| `CA-SP-217` | El sistema devuelve el momento en que expira el bloqueo cuando la persona está bloqueada |
| `CA-SP-346` | La respuesta **no** contiene el número de intentos fallidos acumulados |
| `CA-SP-218` | La respuesta no contiene ningún dato de la credencial, ni siquiera transformado |
| `CA-SP-219` | El sistema informa que el usuario no existe cuando está eliminado lógicamente |
| `CA-SP-220` | El sistema rechaza la consulta a un actor sin el permiso de lectura de usuarios |

## 13. Casos límite

- **Usuario eliminado lógicamente:** se trata como inexistente, igual que un rol eliminado en `RF-SP-003`. Reconstruir qué era corresponde a la auditoría de eliminación.
- **Permiso concedido por dos roles a la vez:** aparece **una sola vez** en los permisos efectivos. La unión no duplica.
- **Rol inactivo que concede un permiso que otro rol activo también concede:** el permiso sigue apareciendo, porque lo aporta el rol activo. Es el caso que hace ver por qué la lista debe resolverse y no deducirse.
- **Usuario con membresía y sin rol consumidor:** no debería poder ocurrir; lo impiden `RN-SP-013` y `RN-SP-015`. Si se observa, es un defecto de datos y conviene que la consulta lo haga visible en lugar de ocultarlo.
- **Identificador con formato incorrecto:** se rechaza por validación, no se trata como usuario inexistente.
- **El actor se consulta a sí mismo:** se admite sin regla especial, **siempre que tenga el permiso**. Quien no lo tenga no puede ver aquí ni sus propios datos, y para eso existe `RF-SP-039`.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación. La tercera dio lugar a un requerimiento nuevo, `RF-SP-039`.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se devuelven los permisos efectivos ya resueltos, o solo los roles? | **Resueltos**, con la unión ya calculada. Es exactamente la pregunta que hay que responder antes de retirar un rol, y no se puede deducir mirando los roles por separado: un permiso que dos roles conceden sobrevive al retiro de uno de ellos. El dato ya está en la caché de resolución de `security.md` §4.5, de modo que no cuesta una consulta nueva. El coste asumido —exponer en un solo sitio el alcance completo de una persona— recae sobre un actor que ya posee `users:read` y que puede ver la lista entera de todos modos |
| 2 | ¿El detalle informa del número de intentos fallidos acumulados? | **No.** Se devuelve **cuándo expira el bloqueo** (`CA-SP-217`), que es lo que responde la pregunta operativa —«¿hasta cuándo no puede entrar?»—, y no el contador, que diría a cualquiera con `users:read` cuántos intentos le quedan a una cuenta antes de bloquearse. `CA-SP-346` deja verificada la ausencia. Quien necesite reconstruir por qué se bloqueó una cuenta lo tiene en la auditoría de seguridad, `RF-SP-014`, que registra cada intento fallido con su IP de origen |
| 3 | ¿Debe existir un endpoint para que cada persona consulte su propio perfil sin `users:read`? | **Sí, y es un requerimiento aparte: `RF-SP-039`.** Hoy alguien sin ese permiso no puede ver ni sus propios datos ni sus propios permisos, y toda interfaz autenticada los necesita para saber qué mostrar. No se resuelve dentro de esta consulta porque su alcance de datos y su autorización son distintos —siempre el actor, nunca otro—, y mezclarlos obligaría a este endpoint a comportarse de dos maneras según a quién apuntara el identificador. Registrado el 21-08-2026 en la matriz de `docs/requirements.md`, todavía sin especificación |
