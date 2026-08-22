# SPEC — `RF-SP-028` Cambiar el estado de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-028` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |
| Enmendada | 22-08-2026 — `RN-SP-022` rechaza retirar el acceso a quien tiene equipo a cargo, al registrarse `RF-SP-041` (Art. I.7) |

---

## 1. Objetivo

Retirar el acceso de una persona de forma inmediata y reversible, o devolvérselo, sin tocar su identidad ni lo que tiene asignado.

## 2. Contexto

Alguien se va de vacaciones, deja la empresa, o su cuenta se comporta de forma sospechosa. En los tres casos hay que cortarle el acceso **ya**, y en los tres hay que poder devolvérselo sin rehacer nada.

Es la operación de seguridad más urgente del módulo, y por eso su efecto no puede depender de que expire nada. `security.md` §4.5 lo declara sin ambigüedad: retirar el acceso a un usuario tiene efecto **inmediato**, se revocan todos sus refresh tokens y su token de acceso se rechaza aunque siga siendo válido por firma. Es la única situación en que el sistema, que por diseño valida la mayoría de peticiones sin consultar la base de datos, **sí** verifica el estado vigente.

### Dos formas de negar el acceso, y qué las separa

La operación puede dejar a una persona en dos estados distintos que impiden entrar, y la diferencia **no** es el mecanismo sino el motivo:

| Estado | Qué significa | Quién lo fija | ¿Caduca solo? |
|---|---|---|---|
| `INACTIVO` | Decisión **organizativa**: la persona ya no debe operar. Una baja, una excedencia, alguien que dejó la empresa | Un actor con el permiso | No |
| `BLOQUEADO` | Respuesta de **seguridad**: hay sospecha sobre la cuenta y se suspende mientras se aclara | El sistema, por intentos fallidos, **o** un actor con el permiso | Solo si lo puso el sistema |

Esa separación es lo que evita que el estado de una cuenta sea ambiguo, y conviene sostenerla al leer la auditoría: una cuenta inactiva cuenta una historia de recursos humanos; una bloqueada cuenta una historia de seguridad. Confundirlas haría inútil filtrar por estado.

El **bloqueo manual no caduca**: no lleva momento de expiración, y solo un actor lo levanta devolviendo la cuenta a activa. El bloqueo por intentos fallidos sí expira solo (`security.md` §3.2), pero también puede levantarse antes, porque cuando quien está bloqueado necesita entrar ahora tiene que haber una forma de liberarlo sin esperar.

Levantar un bloqueo, sea del origen que sea, es devolver la cuenta a activa: la misma operación y no una distinta.

### Dos límites

Nadie se retira el acceso a sí mismo (`RN-SP-017`) y el sistema no puede quedarse sin superadministrador **activo** (`RN-SP-001`). El primero evita el error trivial de perderse el acceso; el segundo evita el error irreversible de que nadie pueda administrarlo.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Cambia el estado de cualquier usuario que no sea él mismo |
| Administrador | Cambia el estado de cualquier usuario que no sea él mismo |

## 4. Alcance

### 4.1 Incluye

- Desactivar un usuario activo y reactivar uno inactivo.
- **Bloquear manualmente** una cuenta sobre la que hay sospecha, sin momento de expiración.
- Levantar un bloqueo —manual o por intentos fallidos— devolviendo la cuenta a activa.
- Revocación inmediata de todas sus sesiones al retirarle el acceso, por cualquiera de las dos vías.

### 4.2 No incluye

- Eliminar al usuario → `RF-SP-029`.
- Retirarle sus roles o su membresía: los conserva todos → `RF-SP-031` y `RF-SP-033`.
- Restablecer su contraseña → `RF-SP-038`. Levantar un bloqueo no cambia la credencial, y restablecer la credencial no levanta un bloqueo.
- Fijar el momento de expiración de un bloqueo: el del sistema lo calcula él (`security.md` §3.2) y el manual no tiene.
- El estado `PENDIENTE`, que ningún requerimiento produce (`RF-SP-024`, resolución 1) y que queda fuera del dominio admitido.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-001` | Debe existir siempre al menos un usuario **activo** con el rol raíz | `requirements/sp.md` §5.1 |
| `RN-SP-017` | El actor no aplica la operación sobre su propia cuenta | `requirements/sp.md` §5.1 |
| `RN-SP-022` | No se retira el acceso a quien tiene personas a su cargo | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del usuario | Sí | Usuario cuyo estado cambia | Debe existir, no estar eliminado y no ser el propio actor |
| Estado | Sí | Nuevo estado | Activo, inactivo o bloqueado |
| Motivo | **Condicional** | Razón por la que se le retira el acceso | Obligatorio al **desactivar** y al **bloquear**; no se admite al activar. No puede quedar vacío tras recortar los extremos |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Usuario | Usuario con su estado actualizado y, si quedó bloqueado por el sistema, el momento en que expira |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de usuarios.
- El usuario existe y no está eliminado.
- El usuario no es el propio actor.
- Si se le retira el acceso, viene informado el motivo.
- Si se le retira el acceso y porta el rol raíz, existe otro usuario **activo** que también lo porta.
- Si se le retira el acceso, **no tiene a nadie a cargo** (`RN-SP-022`).

**Postcondiciones**

- El usuario queda en el estado solicitado.
- Si quedó inactivo o bloqueado: **todos sus refresh tokens quedan revocados** y sus tokens de acceso vigentes dejan de admitirse, sin esperar a que expiren.
- Si quedó bloqueado por decisión de un actor, **el bloqueo no tiene momento de expiración**: solo se levanta devolviendo la cuenta a activa.
- Si estaba bloqueado y quedó activo: el bloqueo se levanta, su contador de intentos fallidos vuelve a cero y su credencial no cambia.
- Sus roles, su membresía y su credencial se conservan intactos.
- Queda constancia en la auditoría de cambios y en la de seguridad, esta última con el usuario afectado como objeto del evento y con el motivo en su detalle cuando lo hubo.

## 8. Flujo principal

1. El actor solicita cambiar el estado de un usuario.
2. El sistema verifica que el usuario exista y no esté eliminado.
3. El sistema verifica que el usuario no sea el propio actor.
4. Si el estado solicitado retira el acceso, el sistema verifica que venga informado el motivo.
5. Si el estado solicitado retira el acceso, el sistema verifica que el usuario no sea el último **activo** con el rol raíz.
6. Si el estado solicitado retira el acceso, el sistema verifica que el usuario no tenga a nadie a cargo.
7. El sistema aplica el nuevo estado. Si la cuenta queda activa, pone a cero su contador de intentos fallidos y levanta cualquier bloqueo vigente; si queda bloqueada por decisión del actor, la deja sin momento de expiración.
8. Si el usuario deja de poder entrar, el sistema revoca todos sus refresh tokens.
9. El sistema registra el evento en la auditoría de cambios y en la de seguridad.
10. El sistema informa el usuario actualizado.

## 9. Flujos alternativos

### FA-001 — El usuario ya está en ese estado

**Cuándo ocurre:** se solicita activar un usuario activo, desactivar uno inactivo, o bloquear uno ya bloqueado.

1. El sistema no aplica cambio ni registra evento.
2. Devuelve el usuario sin tratarlo como error: la operación es idempotente.

### FA-002 — Levantar un bloqueo

**Cuándo ocurre:** el usuario está bloqueado —por intentos fallidos o por decisión de un actor— y se solicita activarlo.

1. El sistema levanta el bloqueo y pone a cero el contador de intentos fallidos.
2. El usuario queda activo y puede autenticarse de inmediato, con la **misma credencial** que tenía.
3. **No** se trata como un caso sin cambio: el usuario estaba bloqueado y pasa a activo, de modo que sí hay evento de auditoría.
4. **No se exige motivo:** devolver el acceso no es lo que hay que justificar.

### FA-003 — Bloqueo manual sobre una cuenta ya bloqueada por el sistema

**Cuándo ocurre:** la cuenta está bloqueada por intentos fallidos y un actor la bloquea de forma deliberada.

1. El bloqueo pasa a ser **manual y sin expiración**, de modo que deja de levantarse solo.
2. Sí hay evento de auditoría: el estado es el mismo, pero su origen y su duración han cambiado.
3. Es el único caso en que solicitar el estado que ya se tiene **no** cae en `FA-001`.

## 10. Excepciones

### EX-001 — Motivo ausente al retirar el acceso

**Condición:** se solicita desactivar o bloquear y no se declara motivo, o el texto no tiene contenido real tras recortar los extremos.
**Respuesta del sistema:** rechaza la operación **antes de ejecutarla** e informa que el motivo es obligatorio.

### EX-002 — El actor es el propio usuario

**Condición:** el identificador corresponde a la cuenta del actor.
**Respuesta del sistema:** rechaza la operación y cita `RN-SP-017`. Evita que alguien se retire su propio acceso y quede sin poder revertirlo.

### EX-003 — Último superadministrador activo

**Condición:** se solicita retirar el acceso al único usuario **activo** que porta el rol raíz.
**Respuesta del sistema:** rechaza la operación, cita `RN-SP-001` y explica que el sistema quedaría sin ninguna vía de administración. El rechazo debe evaluarse sobre el estado vigente en el momento de aplicar el cambio, no antes.

### EX-004 — Motivo declarado al devolver el acceso

**Condición:** se solicita activar una cuenta y se envía un motivo.
**Respuesta del sistema:** rechaza la petición por campo no admitido. Aceptarlo en silencio dejaría un motivo que nadie sabría si interpretar como justificación de la reactivación o resto de una petición anterior.

### EX-005 — Usuario inexistente

**Condición:** el identificador no corresponde a ningún usuario vigente, o el usuario está eliminado.
**Respuesta del sistema:** informa que el usuario no existe, sin distinguir ambos casos.

### EX-006 — La persona tiene equipo a cargo

**Condición:** el estado solicitado le retira el acceso —`INACTIVO` o `BLOQUEADO`— y hay al menos una asignación vigente que la declara superior.
**Respuesta del sistema:** rechaza la operación, cita `RN-SP-022` e informa cuántas personas tiene a cargo, sin listarlas. Se reasignan con `RF-SP-041` antes de retirarle el acceso.

**No alcanza a devolver el acceso**, que nunca deja a nadie huérfano. Y la asimetría con el bloqueo **automático** por intentos fallidos es deliberada: aquel es una respuesta de seguridad que no puede quedar supeditada a que alguien reorganice un equipo primero. Esta excepción solo alcanza al cambio de estado **por decisión de un actor**, que es el que esta especificación gobierna.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Estado obligatorio y dentro del dominio admitido | El estado indicado no es válido. |
| `VAL-002` | Usuario existente y no eliminado | El usuario solicitado no existe. |
| `VAL-003` | El actor no es el usuario afectado | No es posible cambiar el estado de su propia cuenta. |
| `VAL-004` | No es el último superadministrador activo | No es posible retirar el acceso al último administrador del sistema. |
| `VAL-005` | Motivo obligatorio al desactivar o bloquear, no vacío tras recortar | Debe indicar el motivo por el que retira el acceso. |
| `VAL-006` | Motivo no admitido al activar | No es posible indicar un motivo al reactivar una cuenta. |
| `VAL-007` | Al retirar el acceso, la persona no tiene a nadie a cargo | Esta persona tiene personas a su cargo; reasígnelas antes de retirarle el acceso. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-230` | El sistema desactiva un usuario activo y lo reactiva después |
| `CA-SP-231` | El usuario desactivado no puede autenticarse |
| `CA-SP-232` | Los refresh tokens del usuario quedan revocados en el mismo acto, tanto al desactivarlo como al bloquearlo |
| `CA-SP-233` | Un token de acceso emitido antes de retirarle el acceso deja de admitirse de inmediato, sin esperar a que expire |
| `CA-SP-350` | El sistema bloquea manualmente una cuenta activa, y ese bloqueo **no** tiene momento de expiración ni se levanta solo |
| `CA-SP-351` | El estado devuelve su origen: un bloqueo por intentos fallidos trae momento de expiración y uno manual no |
| `CA-SP-234` | El sistema levanta el bloqueo al activar la cuenta, sea manual o por intentos fallidos, y pone su contador de intentos fallidos a cero |
| `CA-SP-235` | Levantar un bloqueo no cambia la credencial: la persona entra con la contraseña que ya tenía |
| `CA-SP-236` | El usuario conserva sus roles y su membresía tras el cambio de estado |
| `CA-SP-352` | El sistema rechaza desactivar o bloquear sin motivo, antes de ejecutar la operación |
| `CA-SP-353` | El sistema rechaza un motivo enviado al reactivar una cuenta |
| `CA-SP-354` | La auditoría de seguridad conserva el motivo por el que se retiró el acceso |
| `CA-SP-410` | El sistema rechaza desactivar o bloquear a quien tiene al menos una persona a cargo, e informa cuántas sin listarlas |
| `CA-SP-411` | El bloqueo **automático** por intentos fallidos se aplica aunque la persona tenga equipo a cargo, y la reactivación nunca se rechaza por ese motivo |
| `CA-SP-237` | El sistema rechaza que el actor cambie el estado de su propia cuenta |
| `CA-SP-238` | El sistema rechaza retirar el acceso al último usuario **activo** con el rol raíz, aunque existan otros inactivos que lo porten |
| `CA-SP-239` | El sistema no registra evento cuando el usuario ya estaba en el estado solicitado |
| `CA-SP-240` | El sistema registra el cambio en la auditoría de cambios y en la de seguridad, con el usuario afectado como objeto del evento |

## 13. Casos límite

- **Usuario desactivado con sesión abierta en varios dispositivos:** todas caen a la vez. La revocación es por usuario, no por sesión.
- **Petición en curso en el momento de la desactivación:** puede completarse; la garantía es que **la siguiente** se rechaza. Cortar una petición ya autorizada exigiría verificar el estado a mitad de la transacción.
- **Dos superadministradores desactivándose mutuamente a la vez:** ambas peticiones pasarían la comprobación de `RN-SP-001` leyendo el estado anterior, y el sistema quedaría sin ninguno. La verificación debe serializarse sobre el **conjunto de portadores activos** del rol raíz, no solo sobre la fila del usuario.
- **Último superadministrador activo con otros inactivos que portan el rol:** se rechaza igual. `RN-SP-001` se lee sobre usuarios activos, porque un superadministrador que no puede entrar no administra nada.
- **Usuario bloqueado por el sistema cuyo bloqueo expira mientras se libera:** el resultado es el mismo, activo, y la operación no debe fallar por haber llegado tarde.
- **Bloqueo manual seguido de intentos fallidos:** la cuenta ya no puede autenticarse, de modo que el contador no llega a moverse. El bloqueo manual sigue siendo el vigente.
- **Cuenta bloqueada manualmente y luego desactivada:** se admite. Pasa de una suspensión de seguridad a una baja organizativa, y la auditoría conserva ambos eventos con sus motivos.
- **Reactivar a alguien cuyos roles fueron eliminados mientras estaba inactivo:** vuelve sin esos roles. `RF-SP-009` impide eliminar un rol con usuarios asignados, incluso inactivos (`CA-SP-163`), de modo que no debería ocurrir; si ocurre, es un defecto.
- **Desactivar a alguien que ya está eliminado:** se trata como inexistente.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación. La segunda se resolvió en contra de la propuesta del borrador, lo que obligó a reescribir §2 para separar los dos significados de negar el acceso.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Liberar un bloqueo es esta operación o un requerimiento aparte? | **Esta misma operación**, con `FA-002` como flujo propio para que pueda probarse por separado. Bloqueado y activo son valores del mismo campo, y separarlo daría dos endpoints escribiendo la misma columna con reglas que tendrían que mantenerse sincronizadas. **El coste queda declarado:** el permiso es `users:update` para todo, de modo que no se puede dar a soporte técnico la facultad de levantar bloqueos sin darle además la de desactivar cuentas. Si esa separación llega a hacer falta, será un permiso nuevo y un requerimiento nuevo, no una reorganización de este |
| 2 | ¿Se admite bloquear manualmente a alguien? | **Sí.** Se resolvió en contra de la propuesta del borrador, que temía la ambigüedad entre `BLOQUEADO` e `INACTIVO`. Esa ambigüedad se cierra dándoles significados distintos y explícitos (§2): `INACTIVO` es una decisión **organizativa** —la persona ya no debe operar— y `BLOQUEADO` es una respuesta de **seguridad** —hay sospecha sobre la cuenta—. Ambos impiden entrar y ambos revocan sesiones, pero cuentan historias distintas y por eso conviene poder filtrarlos por separado en `RF-SP-025`. El **bloqueo manual no caduca**: no lleva momento de expiración y solo se levanta devolviendo la cuenta a activa (`CA-SP-350`), a diferencia del automático, que expira solo |
| 3 | ¿Se exige motivo al desactivar a una persona? | **Sí, al desactivar y al bloquear; no al reactivar.** Es un patrón nuevo respecto de `RF-SP-007`, `RF-SP-022` y `RF-SP-023`, que resolvieron que no, y la asimetría es deliberada: allí lo afectado era un rol o una fila de catálogo, y aquí es el acceso de una persona, sobre la que «¿por qué se le retiró?» es una pregunta que se hace de verdad. El motivo se guarda en el detalle del evento de seguridad, no en una columna de `users`: no es un atributo de la cuenta sino de la operación. Devolver el acceso no se justifica —`EX-004` rechaza el motivo enviado al reactivar—, porque un motivo que a veces significa una cosa y a veces otra no informa de nada |
| 4 | ¿`RN-SP-001` se comprueba sobre usuarios activos o sobre usuarios existentes? | **Sobre usuarios activos.** La regla decía «al menos un usuario con rol `SUPERADMIN`» sin calificar el estado, y leída así la garantía sería vacía: un superadministrador inactivo no puede entrar ni administrar nada, de modo que el sistema podría quedarse sin ninguna vía de administración cumpliendo la regla al pie de la letra. `RN-SP-001` se enmienda para decirlo, y la **misma lectura se aplica en `RF-SP-029` y `RF-SP-031`**, o las tres divergirán. `CA-SP-238` la verifica en el caso que la hace visible: último activo, con otros inactivos que también portan el rol |
