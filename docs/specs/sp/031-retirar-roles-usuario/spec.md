# SPEC — `RF-SP-031` Retirar roles de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-031` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |
| Enmendada | 21-08-2026 — `RN-SP-015` pasa de rechazar a **retirar la membresía en cascada**, al aprobar `RF-SP-033` (Art. I.7) |

---

## 1. Objetivo

Retirar a una persona el alcance de uno o varios roles, reduciendo lo que puede hacer en el sistema.

## 2. Contexto

Es la operación inversa de `RF-SP-030`, y no es su simétrica: conceder solo puede ampliar, y ampliar nunca deja nada inconsistente. Retirar sí, y por eso tiene reglas que la asignación no necesita.

Dos, en concreto. La primera, que el sistema no puede quedarse sin superadministrador (`RN-SP-001`): retirar el rol raíz al último que lo porta lo dejaría sin administración posible.

La segunda es `RN-SP-015`, y es más que una comprobación: **esta operación es la única salida del estado de consumidor**. `RN-SP-018` no admite que alguien porte un rol `CONSUMIDOR` sin membresía, y `RN-SP-013` no admite membresía sin ese rol; los dos juntos hacen que el rol y el nivel sean inseparables. Si retirar el último rol de consumidor se rechazara mientras hubiera membresía —como decía el borrador de esta spec— y retirar la membresía se rechazara mientras hubiera rol, nadie podría dejar de ser consumidor nunca.

Por eso el retiro del último rol `CONSUMIDOR` **arrastra la membresía**, en la misma transacción y bajo el mismo identificador de correlación. Se adquieren juntos con `RF-SP-030` y se sueltan juntos aquí.

Retirar un rol **no elimina una entidad de negocio**: elimina una asociación, una fila cuyo significado se agota en el par que vincula. Por eso no se exige motivo, por la excepción que el Art. V.13 admite y que `RN-SP-005` ya aplicó a los permisos de un rol. El «por qué» ya está en el propio evento: qué rol, a quién, quién lo hizo y cuándo.

Y a diferencia de la asignación, aquí la latencia sí importa. El token de acceso transporta los códigos de rol, de modo que quien pierde un rol seguiría ejerciéndolo hasta que su token expire. Retirar el acceso y que siga funcionando quince minutos es un resultado que esta operación no acepta: **el retiro revoca las sesiones de la persona**, igual que hace `RF-SP-028` al desactivar.

Esa es la asimetría deliberada con `RF-SP-030`, que **no** las revoca. Conceder puede esperar —la latencia solo retrasa un permiso nuevo—; retirar no, porque la ventana se abre justo cuando alguien decidió que esa persona dejara de poder hacer algo. El coste asumido es que la persona debe volver a autenticarse cada vez que se le retira un rol, y se prefiere a que el retiro sea una promesa a plazo.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Retira cualquier rol |
| Administrador | Retira roles cuyos permisos estén contenidos en los suyos |

## 4. Alcance

### 4.1 Incluye

- Retirar uno o varios roles a una persona. La operación **solo retira**: nunca asigna ninguno.
- Verificación de que el retiro no deja al sistema sin superadministrador activo.
- **Retiro de la membresía en cascada** cuando el último rol `CONSUMIDOR` desaparece (`RN-SP-015`).

### 4.2 No incluye

- Asignar roles → `RF-SP-030`.
- Retirar la membresía **sin retirar el rol de consumidor** → `RF-SP-033`, que es una operación correctiva. Lo que sí hace esta operación es retirar la membresía **como consecuencia** de quitar el último rol `CONSUMIDOR` (`RN-SP-015`), y eso no es deshacer en silencio nada: es la única forma de que el estado de consumidor tenga salida.
- Eliminar el rol del catálogo → `RF-SP-009`.
- Retirar el acceso completo de una persona → `RF-SP-028`, que es lo que se busca cuando el objetivo es que alguien deje de entrar.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-001` | Debe existir siempre al menos un usuario activo con el rol raíz | `requirements/sp.md` §5.1 |
| `RN-SP-015` | Retirar el último rol `CONSUMIDOR` retira también la membresía, en la misma operación | `requirements/sp.md` §5.1 |
| `RN-SP-018` | Todo consumidor tiene membresía: el rol y el nivel son inseparables | `requirements/sp.md` §5.1 |
| `RN-SEG-010` | Nadie manipula privilegios que no posee | `security.md` §4.3 |
| `RN-SP-005` | La eliminación de una asociación se audita sin motivo declarado | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del usuario | Sí | Persona a la que se retiran roles | Debe existir y no estar eliminada |
| Roles | Sí | Roles a retirar | Entre 1 y 100 por petición |

No se declara motivo: es la eliminación de una asociación (Art. V.13).

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Usuario | Usuario con su lista de roles actualizada |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de asignación de roles.
- El usuario existe y no está eliminado.
- Los permisos de los roles que se retiran están contenidos en los permisos efectivos del actor.
- El retiro no deja al sistema sin ningún usuario activo con el rol raíz.

**Postcondiciones**

- Los roles quedan desasociados del usuario, y los que no se pidieron se conservan.
- Si la persona queda sin ningún rol `CONSUMIDOR`, **su membresía queda retirada con ellos**, en la misma transacción y bajo el mismo identificador de correlación (`RN-SP-015`).
- Sus permisos efectivos dejan de incluir los de esos roles, **salvo los que otro de sus roles siga concediendo**.
- **Todos sus refresh tokens quedan revocados y sus tokens de acceso vigentes dejan de admitirse**, de modo que el retiro tiene efecto de inmediato y no en quince minutos. La persona debe autenticarse de nuevo.
- Queda constancia en la auditoría de eliminación, sin motivo declarado, y en la de seguridad con severidad alta y el usuario afectado como objeto del evento.

## 8. Flujo principal

1. El actor solicita retirar uno o varios roles a un usuario.
2. El sistema verifica que el usuario exista y no esté eliminado.
3. El sistema verifica que los permisos de los roles a retirar estén contenidos en los del actor.
4. El sistema verifica que el retiro no deje al sistema sin ningún usuario activo con el rol raíz.
5. El sistema desasocia los roles que el usuario tenía.
6. Si con ello la persona queda sin ningún rol `CONSUMIDOR`, el sistema retira también su membresía.
7. El sistema revoca todos los refresh tokens de la persona, de modo que el retiro aplique de inmediato.
8. El sistema registra el evento en la auditoría de eliminación y en la de seguridad.
9. El sistema informa el usuario con sus roles actualizados.

## 9. Flujos alternativos

### FA-001 — Roles que el usuario no tenía

**Cuándo ocurre:** alguno de los roles indicados no estaba asignado a la persona.

1. El sistema ignora los que no estaban y retira solo los que sí.
2. La operación es **idempotente**: repetirla no produce error.
3. Si **ninguno** de los roles estaba asignado, no se registra evento: nada cambió.

### FA-002 — Retiro de todos los roles

**Cuándo ocurre:** el retiro deja a la persona sin ningún rol.

1. Se admite, siempre que no incumpla `RN-SP-001` ni `RN-SP-015`.
2. La persona queda autenticable pero sin permiso efectivo alguno, el mismo estado en que `RF-SP-024` permite crearla.

### FA-003 — Retiro del último rol consumidor

**Cuándo ocurre:** el retiro deja a la persona sin ningún rol de clasificación `CONSUMIDOR` y tenía membresía.

1. El sistema retira **también su membresía**, en la misma transacción (`RN-SP-015`).
2. Ambos hechos se auditan por separado y bajo el **mismo identificador de correlación**, de modo que la operación completa pueda recuperarse entera.
3. **No es un rechazo.** Es la única salida del estado de consumidor, y por eso esta operación es la puerta.

## 10. Excepciones

### EX-001 — Último superadministrador

**Condición:** el retiro dejaría al sistema sin ningún usuario activo portando el rol raíz.
**Respuesta del sistema:** rechaza la operación completa y cita `RN-SP-001`.

*(La excepción que aquí declaraba el rechazo por membresía huérfana se retiró el 21-08-2026: ese caso ya no es un error, sino el flujo `FA-003`.)*

### EX-003 — Rol fuera del alcance del actor

**Condición:** algún rol a retirar declara permisos que el actor no posee.
**Respuesta del sistema:** rechaza la operación completa y cita `RN-SEG-010`.

### EX-004 — Usuario inexistente o eliminado

**Condición:** el identificador no corresponde a ningún usuario vigente.
**Respuesta del sistema:** rechaza la operación e informa que el usuario no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Al menos un rol informado | Debe indicar al menos un rol. |
| `VAL-002` | Identificadores de rol con formato válido | El identificador de rol no es válido. |
| `VAL-003` | El retiro no deja al sistema sin superadministrador activo | No es posible retirar el rol al último administrador del sistema. |
| `VAL-004` | — | *(Retirada el 21-08-2026: el caso dejó de ser un rechazo y pasó a ser la cascada de `FA-003`.)* |
| `VAL-005` | Como máximo 100 roles por petición | No es posible retirar más de 100 roles en una sola solicitud. |
| `VAL-006` | Usuario existente y no eliminado | El usuario solicitado no existe. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-262` | El sistema retira a un usuario los roles indicados y conserva los demás |
| `CA-SP-263` | Un permiso que otro rol vigente sigue concediendo **permanece** entre los permisos efectivos tras el retiro |
| `CA-SP-264` | El sistema rechaza el retiro que dejaría al sistema sin ningún usuario activo con el rol raíz |
| `CA-SP-265` | El retiro del último rol consumidor **retira también la membresía**, en la misma transacción y bajo el mismo identificador de correlación |
| `CA-SP-371` | Tras ese retiro, la persona no conserva ni rol de consumidor ni membresía, y la operación completa se recupera filtrando por su identificador de correlación |
| `CA-SP-266` | El sistema rechaza el retiro de un rol cuyos permisos exceden los del actor |
| `CA-SP-267` | El sistema ignora los roles que el usuario no tenía, sin producir error |
| `CA-SP-268` | El sistema no registra evento cuando ninguno de los roles indicados estaba asignado |
| `CA-SP-269` | El sistema permite dejar a una persona sin ningún rol cuando no lo impide ninguna regla |
| `CA-SP-270` | La operación no solicita ni admite un motivo, y la auditoría de eliminación conserva el evento sin él |
| `CA-SP-271` | El sistema registra el evento en la auditoría de eliminación y en la de seguridad, con severidad alta y el usuario afectado como objeto |
| `CA-SP-361` | Tras el retiro, los refresh tokens de la persona quedan revocados y su token de acceso vigente deja de admitirse |
| `CA-SP-362` | El permiso retirado deja de concederse **de inmediato**, sin esperar a que expire ningún token |
| `CA-SP-363` | La asignación de `RF-SP-030` **no** revoca sesiones, y el retiro sí: la asimetría es verificable |

## 13. Casos límite

- **Operación parcialmente válida:** se rechaza **entera**, mismo criterio que en la asignación y en `RF-SP-006`.
- **Roles duplicados en la petición:** se normalizan a una sola ocurrencia.
- **Permiso concedido por dos roles:** retirar uno no lo quita. Es el caso que hace imprescindible consultar `RF-SP-026` antes de retirar: la lista de permisos efectivos ya resuelta es la única que responde qué pierde la persona en realidad.
- **El actor se retira roles a sí mismo:** se admite. Puede dejarse sin permisos, incluido el de volver a asignárselos, y no hay regla que lo impida más allá de `RN-SP-001`. Es un pie de disparo posible y conviene que la interfaz avise.
- **Dos retiros concurrentes sobre el último superadministrador:** ambos leerían el estado anterior y pasarían la comprobación. La verificación de `RN-SP-001` debe serializarse sobre el conjunto de portadores del rol raíz, igual que en `RF-SP-028` y `RF-SP-029`.
- **Retirar un rol inactivo:** se admite y no produce cambio en los permisos efectivos, que ya no lo incluían (`RN-SEG-002`). Sí produce evento: la asignación desaparece.
- **Usuario eliminado:** se trata como inexistente.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El retiro debe revocar las sesiones de la persona para que tenga efecto inmediato? | **Sí**, se revocan sus refresh tokens y sus tokens de acceso vigentes dejan de admitirse, igual que hace `RF-SP-028` al desactivar. Sin ello, quien pierde un rol seguiría ejerciéndolo hasta quince minutos, y esa ventana se abre justo cuando alguien decidió que dejara de poder hacer algo. **Es la respuesta contraria a la de `RF-SP-030`** —donde conceder no fuerza la renovación— y la asimetría es deliberada: conceder puede esperar, retirar no. El coste asumido es que la persona vuelve a autenticarse cada vez que se le retira un rol, y se prefiere a que el retiro sea una promesa a plazo. `CA-SP-361` a `CA-SP-363` lo verifican, incluida la asimetría |
| 2 | ¿`RN-SEG-010` aplica también al retiro? | **Sí** (`EX-003`). Quien no puede conceder un privilegio tampoco debería poder manipularlo: sin esta comprobación, un administrador menor podría desmontar el acceso de uno mayor retirándole roles que él mismo no podría asignar. El argumento en contra —que retirar solo reduce y por tanto no permite escalar— es cierto sobre el privilegio propio, pero ignora el daño de poder desarmar a otro. La urgencia queda cubierta por otra vía: `RF-SP-028` corta el acceso entero sin mirar roles, y esa sí es la operación de contención |
| 3 | ¿El retiro que deja una membresía huérfana se rechaza, o retira además la membresía? | **Enmendada el 21-08-2026 (Art. I.7): retira además la membresía.** La resolución original decía rechazar, siguiendo el enunciado que `RN-SP-015` tenía entonces. Al aprobar `RF-SP-033` ese mismo día se resolvió que **todo consumidor tiene membresía** (`RN-SP-018`), y con esa regla el rechazo producía un **bloqueo mutuo**: no se podía retirar el rol mientras hubiera membresía, ni la membresía mientras hubiera rol, de modo que nadie podía dejar de ser consumidor nunca. La salida es la cascada: retirar el último rol `CONSUMIDOR` arrastra la membresía, en la misma transacción y bajo el mismo identificador de correlación (`FA-003`). Sigue siendo cierto lo que motivaba la resolución original —una operación no debe deshacer en silencio lo que otra decidió—, pero aquí no hay silencio: es la consecuencia declarada de una regla, y queda auditada por separado 
| 4 | ¿La auditoría de eliminación es el registro correcto para el retiro de un rol? | **Sí, y además evento de seguridad.** Es donde vive el resto de asociaciones borradas: `RN-SP-005` lo estableció para los permisos de un rol, y mantener dos criterios para dos asociaciones sería arbitrario. La objeción —que un retiro de rol pesa más que borrar una fila cualquiera y que buscarlo entre eliminaciones no es evidente— se resuelve por el otro lado: el evento de **seguridad**, con severidad alta, sí lo enumera `security.md` §8.1 y es el que se consultará en la práctica cuando alguien pregunte quién retiró qué a quién |
