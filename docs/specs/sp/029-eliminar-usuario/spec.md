# SPEC — `RF-SP-029` Eliminar usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-029` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |
| Enmendada | 22-08-2026 — `RN-SP-022` rechaza eliminar a quien tiene equipo a cargo, y la asignación de superior se cierra en lugar de desaparecer, al registrarse `RF-SP-041` (Art. I.7) |

---

## 1. Objetivo

Retirar definitivamente a una persona del sistema, dejando constancia de por qué se retiró, sin perder el rastro de lo que hizo.

## 2. Contexto

La eliminación es **lógica y nunca física**. `security.md` §3.1 lo exige de forma expresa: los cuatro registros de auditoría referencian al actor por su identificador, y ese identificador debe seguir resolviendo a un usuario. Un registro de auditoría que apunta a alguien que ya no existe no prueba nada.

Esa exigencia obliga a una asimetría con los roles que conviene tener presente. Al eliminar un rol, su código y su nombre **se liberan** (`RN-SEG-001`) y otro rol puede tomarlos. Al eliminar un usuario, su nombre de usuario y su correo **no se liberan** (`RN-SP-016`): si otra persona pudiera tomarlos, la actividad de dos personas distintas quedaría bajo la misma etiqueta y la auditoría dejaría de poder separarlas.

Y una pregunta que conviene hacerse antes de usar esta operación: casi siempre lo que se quiere es **desactivar**, no eliminar. Desactivar retira el acceso de inmediato y es reversible (`RF-SP-028`). Eliminar no tiene vuelta, no libera nada y no aporta ninguna capacidad que desactivar no dé. Existe para cuando el registro fue un error o la persona debe desaparecer de la operación, no como forma habitual de dar de baja a alguien.

Como aquí sí se elimina una entidad de negocio, el **motivo es obligatorio** (Art. V.13) y se conserva el estado del registro al momento de eliminarse.

### La persona se conserva; sus asignaciones no

La eliminación **retira sus roles y su membresía**, borrando esas filas. La persona sigue existiendo como registro —tiene que seguir existiendo, para que la auditoría la resuelva—, pero deja de estar vinculada a nada.

Eso resuelve de paso una pregunta que quedaba colgando: `RN-SEG-008` impide eliminar un rol que alguien tenga asignado, y `CA-SP-163` extendió la prohibición a los usuarios **inactivos**, porque reactivarlos los dejaría con un rol inexistente. Un usuario eliminado no se reactiva, de modo que ese argumento no aplicaba; al desaparecer sus filas de `user_roles`, el conteo de `RF-SP-009` deja de verlas **sin que haya que cambiar nada allí**. Un rol que solo portaba alguien ya eliminado vuelve a poder borrarse, y el catálogo no acumula roles muertos.

**La contrapartida es que la auditoría pasa a ser la única fuente de qué tenía esa persona**, y eso deja de ser un detalle: si el evento de eliminación no conserva sus roles y su membresía, esa información se pierde para siempre. Por eso el estado que se guarda al eliminar no es opcional ni resumido — es lo único que quedará.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Elimina cualquier usuario que no sea él mismo |
| Administrador | Elimina cualquier usuario que no sea él mismo |

## 4. Alcance

### 4.1 Incluye

- Eliminación lógica de un usuario.
- **Retiro de sus roles y de su membresía**, cuyas filas se borran.
- Registro obligatorio del motivo y del estado del usuario al eliminarse, **incluidos los roles y la membresía que tenía**.
- Revocación inmediata de todas sus sesiones.

### 4.2 No incluye

- Eliminación física, prohibida por `security.md` §3.1.
- Liberar su nombre de usuario o su correo: quedan reservados de forma permanente (`RN-SP-016`).
- Restaurar un usuario eliminado.
- Borrar o anonimizar lo que la persona hizo: los registros de auditoría se conservan íntegros.
- Retirar el acceso de forma reversible → `RF-SP-028`, que es lo que se busca en casi todos los casos.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-001` | Debe existir siempre al menos un usuario activo con el rol raíz | `requirements/sp.md` §5.1 |
| `RN-SP-016` | El nombre de usuario y el correo **no se liberan al eliminar** | `requirements/sp.md` §5.1 |
| `RN-SP-017` | El actor no aplica la operación sobre su propia cuenta | `requirements/sp.md` §5.1 |
| `RN-SP-022` | No se elimina a quien tiene personas a su cargo | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del usuario | Sí | Usuario que se elimina | Debe existir, no estar ya eliminado y no ser el propio actor |
| Motivo | **Sí** | Razón de la eliminación, declarada por el actor | No puede quedar vacío tras recortar los extremos. No se admite un valor generado por el sistema |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Confirmación | Resultado de la operación, sin cuerpo de datos |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de eliminación de usuarios.
- El usuario existe y no está eliminado.
- El usuario no es el propio actor.
- Si el usuario porta el rol raíz, existe otro usuario activo que también lo porta.
- El usuario **no tiene a nadie a cargo** (`RN-SP-022`).

**Postcondiciones**

- El usuario queda marcado como eliminado y deja de aparecer en las consultas por defecto.
- **Sus roles y su membresía quedan retirados**: esas filas desaparecen, y con ellas deja de contar para `RN-SEG-008`.
- Su asignación de superior comercial, si la tenía, **se cierra con la fecha de la eliminación en lugar de desaparecer**. La asimetría con los roles y la membresía es deliberada: aquellos dicen qué podía hacer hoy y no significan nada una vez la persona se va; el historial de mando dice **a quién se atribuía su producción**, y eso lo necesitarán las comisiones mucho después de la baja (`RN-SP-021`).
- No puede autenticarse, y todos sus refresh tokens quedan revocados.
- Su nombre de usuario y su correo **siguen reservados**: ningún alta posterior puede tomarlos.
- Su identificador sigue resolviendo a un usuario, de modo que los eventos de auditoría que lo referencian siguen siendo legibles.
- Queda constancia en la auditoría de eliminación, con el motivo y el estado del usuario, y en la de seguridad.

## 8. Flujo principal

1. El actor solicita eliminar un usuario y declara el motivo.
2. El sistema verifica que el motivo venga informado y tenga contenido.
3. El sistema verifica que el usuario exista y no esté ya eliminado.
4. El sistema verifica que el usuario no sea el propio actor.
5. Si el usuario porta el rol raíz, el sistema verifica que no sea el último activo que lo porta.
6. El sistema verifica que el usuario no tenga a nadie a cargo.
7. El sistema registra el estado del usuario al momento de eliminarse, **incluidos sus roles, su membresía y su superior comercial**, antes de tocar nada.
8. El sistema marca el usuario como eliminado, **retira sus roles y su membresía** y **cierra su asignación de superior**.
9. El sistema revoca todos sus refresh tokens.
10. El sistema registra el evento en la auditoría de eliminación, con el motivo y ese estado, y en la de seguridad.
11. El sistema confirma la eliminación.

El orden de los pasos 7 y 8 no es indiferente: si las asignaciones se borraran antes de capturar el estado, el evento de auditoría quedaría sin ellas y la información se perdería sin que nada fallara.

## 9. Flujos alternativos

Ninguno. La eliminación no admite variantes: o se cumplen todas las condiciones, o se rechaza.

## 10. Excepciones

### EX-001 — Motivo ausente o insuficiente

**Condición:** no se declara motivo, o el texto no tiene contenido real tras recortar los extremos.
**Respuesta del sistema:** rechaza la operación **antes de ejecutarla** y explica que el motivo es obligatorio (Art. V.13).

### EX-002 — El actor es el propio usuario

**Condición:** el identificador corresponde a la cuenta del actor.
**Respuesta del sistema:** rechaza la operación y cita `RN-SP-017`.

### EX-003 — Último superadministrador activo

**Condición:** el usuario es el único activo que porta el rol raíz.
**Respuesta del sistema:** rechaza la operación, cita `RN-SP-001` y explica que el sistema quedaría sin ninguna vía de administración.

### EX-004 — Usuario inexistente o ya eliminado

**Condición:** el identificador no corresponde a ningún usuario, o el usuario ya está eliminado.
**Respuesta del sistema:** rechaza la operación e informa que el usuario no existe, sin distinguir entre nunca haber existido y haber sido eliminado (Art. V.13).

### EX-005 — La persona tiene equipo a cargo

**Condición:** hay al menos una asignación vigente que declara superior a esta persona.
**Respuesta del sistema:** rechaza la eliminación, cita `RN-SP-022` e informa cuántas personas tiene a cargo, sin listarlas. Se reasignan una a una con `RF-SP-041` antes de poder eliminarla.

Es la misma protección que `RN-SEG-008` da a un rol con hijos, y por el mismo motivo: la baja de una persona no puede decidir en silencio a quién pasa a reportar el equipo que dependía de ella.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Motivo obligatorio | Debe indicar el motivo de la eliminación. |
| `VAL-002` | Motivo no vacío tras recortar los extremos | Debe indicar el motivo de la eliminación. |
| `VAL-003` | El actor no es el usuario afectado | No es posible eliminar su propia cuenta. |
| `VAL-004` | No es el último superadministrador activo | No es posible eliminar al último administrador del sistema. |
| `VAL-005` | La persona no tiene a nadie a cargo | Esta persona tiene personas a su cargo; reasígnelas antes de eliminarla. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-241` | El sistema elimina lógicamente un usuario y este deja de aparecer en el listado por defecto |
| `CA-SP-242` | El sistema rechaza la eliminación sin motivo, antes de ejecutarla |
| `CA-SP-243` | El usuario eliminado no puede autenticarse, y sus refresh tokens quedan revocados |
| `CA-SP-244` | El nombre de usuario y el correo del usuario eliminado **no** quedan disponibles para un alta nueva |
| `CA-SP-245` | El identificador del usuario eliminado sigue resolviendo en los eventos de auditoría que lo referencian |
| `CA-SP-246` | La auditoría de eliminación conserva el motivo y el estado del usuario, incluidos sus roles y su membresía al momento de eliminarse |
| `CA-SP-358` | Al eliminar al usuario, sus filas de roles y de membresía **desaparecen** |
| `CA-SP-359` | Un rol que solo portaba un usuario eliminado **puede eliminarse** con `RF-SP-009`, sin que `RN-SEG-008` lo impida |
| `CA-SP-360` | El estado guardado en la auditoría de eliminación es anterior al retiro de las asignaciones, de modo que las conserva |
| `CA-SP-408` | El sistema rechaza eliminar a quien tiene al menos una persona a cargo, e informa cuántas sin listarlas |
| `CA-SP-409` | Al eliminar a un vendedor sin equipo, su asignación de superior **se cierra con fecha de fin** y su fila **permanece**, a diferencia de las de rol y membresía |
| `CA-SP-247` | El sistema rechaza que el actor elimine su propia cuenta |
| `CA-SP-248` | El sistema rechaza eliminar al último usuario activo con el rol raíz |
| `CA-SP-249` | El sistema no expone ninguna operación de restauración |
| `CA-SP-250` | El sistema rechaza la eliminación a un actor sin el permiso de eliminación de usuarios |

## 13. Casos límite

- **Usuario con actividad en la auditoría:** se elimina igual. Conservar el registro es precisamente lo que permite que esa actividad siga siendo atribuible.
- **Usuario con roles asignados:** no lo impide. A diferencia de `RF-SP-009`, que rechaza eliminar un rol con portadores, aquí no hay nada aguas abajo que quede colgando: las asignaciones se retiran con él.
- **Rol que solo porta un usuario eliminado:** **puede eliminarse**. Al desaparecer la fila de `user_roles`, el conteo de `RN-SEG-008` deja de verla sin que `RF-SP-009` tenga que distinguir nada.
- **Usuario eliminado que tenía membresía:** la membresía sigue existiendo en la cadena; lo que desaparece es su asignación. `RN-SP-008` impide borrar membresías, y esta operación no lo intenta.
- **Reconstruir qué tenía la persona:** solo se puede desde la auditoría de eliminación. Es la consecuencia directa de retirar las asignaciones, y por eso `CA-SP-360` verifica que el estado se capture **antes** de retirarlas.
- **Usuario eliminado que aparece en auditoría antigua:** su identificador debe seguir resolviendo al registro conservado, con su nombre de usuario, para que el evento pueda mostrarse.
- **Eliminar a alguien ya eliminado:** se trata como inexistente.
- **Eliminación concurrente con un inicio de sesión:** ambas se serializan sobre la fila del usuario. O la sesión se abre y queda revocada acto seguido, o el inicio de sesión ya encuentra al usuario eliminado y lo rechaza.
- **Motivo con solo espacios:** se rechaza tras recortar los extremos, mismo criterio que `RF-SP-009`.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación. La segunda y la tercera se resolvieron juntas y en contra de lo que proponía el borrador, lo que obligó a añadir a §2 el apartado sobre las asignaciones.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Este requerimiento debe existir? | **Sí, se mantiene.** `security.md` §3.1 dice que un usuario se desactiva y no se elimina, y `RF-SP-028` cubre lo que hace falta casi siempre; pero el alta equivocada —alguien registrado por error, un duplicado— necesita una salida, y una cuenta inactiva para siempre no lo es. Lo que hace aceptable mantenerlo es que la eliminación sea **lógica y con motivo obligatorio**, de modo que sigue siendo trazable. §2 deja escrito de forma expresa que casi siempre lo que se busca es desactivar, para que esta operación no se use como baja habitual |
| 2 | ¿Un usuario eliminado sigue impidiendo eliminar su rol? | **No, y se resuelve solo.** Al eliminar a la persona se **retiran sus roles** (resolución 3), de modo que sus filas de `user_roles` desaparecen y el conteo de `RN-SEG-008` deja de verlas. `RF-SP-009` **no necesita enmienda**: sigue contando exactamente lo que siempre contó, y el resultado correcto sale de que ya no hay nada que contar. Es mejor que la alternativa que el borrador proponía —hacer que `RF-SP-009` distinguiera usuarios eliminados—, porque no añade una condición que alguien pueda olvidar. `CA-SP-359` lo verifica desde el otro lado: el rol pasa a poder eliminarse |
| 3 | ¿Se conservan las filas de roles y membresía del usuario eliminado? | **No: se borran.** La persona sigue existiendo como registro —tiene que seguir, para que la auditoría la resuelva—, pero deja de estar vinculada a nada. Eso mantiene limpias las tablas de asignación, evita que un eliminado aparezca por descuido en un filtro por rol, y resuelve la pregunta 2 sin tocar `RF-SP-009`. **La contrapartida es seria y queda declarada:** la auditoría de eliminación pasa a ser la **única** fuente de qué tenía esa persona. Por eso el estado que se guarda no es un resumen sino el conjunto completo de sus roles y su membresía, y por eso el flujo lo captura **antes** de retirar nada (`CA-SP-360`): invertir esos dos pasos perdería la información sin que nada fallara |
| 4 | ¿La eliminación debe poder anonimizar los datos personales de la persona? | **No se resuelve en este requerimiento.** Anonimizar alcanza a todos los módulos que guarden datos de la persona, no solo a `SP`, y choca de frente con `RN-SP-016` —que reserva el nombre de usuario y el correo precisamente para que dos identidades no se confundan— y con que la auditoría pueda seguir mostrando quién hizo qué. Merece una decisión documentada en `docs/security/` que pondere ambas cosas. Queda anotado como **riesgo** con su condición de disparo: hoy la eliminación conserva nombre y correo indefinidamente, y el día que exista una obligación formal de supresión habrá que decidir qué se sustituye y qué se conserva |
