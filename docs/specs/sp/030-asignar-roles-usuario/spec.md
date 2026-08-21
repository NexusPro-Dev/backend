# SPEC — `RF-SP-030` Asignar roles a un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-030` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |
| Enmendada | 21-08-2026 — `RN-SP-018` obliga a indicar la membresía al asignar el primer rol `CONSUMIDOR`, al aprobar `RF-SP-033` (Art. I.7) |

---

## 1. Objetivo

Conceder a una persona el alcance de uno o varios roles, ampliando lo que puede hacer en el sistema.

## 2. Contexto

Es la operación que convierte la definición de acceso en acceso real. Todo lo que `SP` construye —el catálogo de permisos, los roles, su contención— no tiene efecto sobre nadie hasta que un rol se asigna a una persona.

Los permisos efectivos de alguien son la **unión** de los permisos de sus roles activos (`RN-SEG-009`). Asignar un rol, por tanto, solo puede ampliar: nunca retira nada. Retirar tiene sus propias reglas y su propio requerimiento (`RF-SP-031`), y por el mismo motivo que en `RF-SP-005`, esta operación **agrega y no reemplaza**: un reemplazo haría retiros implícitos que se saltarían las comprobaciones de `RN-SP-015`.

Hay una diferencia con `RF-SP-005` que conviene tener a la vista, porque afecta a lo que se puede prometer. Cambiar los permisos de un rol tiene efecto **inmediato**, por invalidación de caché. Cambiar los roles de una persona **no**: el token de acceso transporta los códigos de rol del usuario (`security.md` §4.5), de modo que el rol nuevo no se aplica hasta que ese token expira, como mucho quince minutos. Es una latencia conocida y aceptada, no un defecto; pero significa que esta operación no puede ofrecerse como forma de conceder acceso urgente.

Y un límite que sostiene todo el modelo: **nadie concede lo que no posee** (`RN-SEG-010`). Sin él, cualquiera con `users:assign-roles` podría asignarse a sí mismo, o a un cómplice, el rol raíz.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Asigna cualquier rol |
| Administrador | Asigna roles cuyos permisos estén contenidos en los suyos |

## 4. Alcance

### 4.1 Incluye

- Asignar uno o varios roles a una persona. La operación **solo agrega**: nunca retira ninguno.
- Verificación de que los roles concedidos no exceden los privilegios del actor.

### 4.2 No incluye

- Retirar roles → `RF-SP-031`. Esta operación no puede usarse para reemplazar la lista: un reemplazo haría retiros implícitos, y retirar tiene reglas propias que `RN-SP-015` y `RN-SP-001` imponen.
- Crear o modificar roles → `RF-SP-001` y siguientes.
- Cambiar o renovar la membresía de quien ya la tiene → `RF-SP-032`. Esta operación solo la establece en un caso: cuando concede el **primer** rol `CONSUMIDOR` a alguien que no la tenía, porque `RN-SP-018` no admite el estado intermedio.
- Modificar los permisos que el rol declara → `RF-SP-005` y `RF-SP-006`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-009` | Los permisos efectivos son la unión de los permisos de los roles activos | `security.md` §4.3 |
| `RN-SEG-010` | Nadie concede privilegios que no posee | `security.md` §4.3 |
| `RN-SP-013` | La membresía exige un rol de clasificación `CONSUMIDOR` | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del usuario | Sí | Persona a la que se asignan roles | Debe existir y no estar eliminada |
| Roles | Sí | Roles a asignar | Entre 1 y 100 por petición; cada uno debe existir y estar activo |
| Membresía | **Condicional** | Nivel de acceso que acompaña al rol de consumidor | **Obligatoria** cuando la asignación da a la persona su primer rol `CONSUMIDOR` y no tiene membresía (`RN-SP-018`). No se admite en ningún otro caso |
| Fecha de fin de la membresía | No | Hasta cuándo está vigente | Solo si se indica membresía. Mismas reglas que en `RF-SP-032` |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Usuario | Usuario con su lista de roles actualizada |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de asignación de roles.
- El usuario existe y no está eliminado.
- Los roles indicados existen, están activos y no están eliminados.
- Los permisos de los roles indicados están contenidos en los permisos efectivos del actor.

**Postcondiciones**

- Los roles quedan asignados al usuario, y los que ya tenía se conservan.
- Sus permisos efectivos pasan a incluir los de los roles nuevos, **desde que expire su token de acceso vigente** (`security.md` §4.5).
- Si la asignación le da su primer rol `CONSUMIDOR`, la persona queda **también con la membresía indicada**, escrita en la misma transacción, y ambos hechos quedan auditados bajo el mismo identificador de correlación (`RN-SP-018`).
- Queda constancia en la auditoría de cambios y en la de seguridad, esta última con severidad alta y con el usuario afectado como objeto del evento.

## 8. Flujo principal

1. El actor solicita asignar uno o varios roles a un usuario.
2. El sistema verifica que el usuario exista y no esté eliminado.
3. El sistema verifica que todos los roles existan, no estén eliminados y estén activos.
4. El sistema verifica que los permisos de todos los roles estén contenidos en los permisos efectivos del actor.
5. El sistema asocia los roles que el usuario aún no tenía.
6. El sistema registra el evento en la auditoría de cambios y en la de seguridad.
7. El sistema informa el usuario con sus roles actualizados.

## 9. Flujos alternativos

### FA-001 — Roles ya asignados

**Cuándo ocurre:** alguno de los roles ya lo tenía el usuario.

1. El sistema ignora los ya presentes y asocia solo los nuevos.
2. La operación es **idempotente**: repetirla no produce error ni duplicados.
3. Si **ninguno** de los roles era nuevo, no se registra evento de auditoría: nada cambió.

## 10. Excepciones

### EX-001 — Rol fuera del alcance del actor

**Condición:** algún rol indicado declara permisos que el actor no posee.
**Respuesta del sistema:** rechaza la operación completa, cita `RN-SEG-010` e informa qué roles lo incumplen. Es la excepción que impide la escalada de privilegios.

### EX-002 — Rol inexistente o eliminado

**Condición:** alguno de los roles indicados no existe o está eliminado lógicamente.
**Respuesta del sistema:** rechaza la operación completa e informa cuáles no existen, sin distinguir entre nunca haber existido y haber sido eliminado.

### EX-003 — Rol inactivo

**Condición:** alguno de los roles indicados está inactivo.
**Respuesta del sistema:** rechaza la operación completa e informa cuáles. Asignar un rol inactivo no concedería nada (`RN-SEG-002`) y dejaría a quien lo asigna creyendo que sí.

### EX-004 — Usuario inexistente o eliminado

**Condición:** el identificador no corresponde a ningún usuario vigente.
**Respuesta del sistema:** rechaza la operación e informa que el usuario no existe.

### EX-005 — Primer rol consumidor sin membresía

**Condición:** la asignación daría a la persona su primer rol `CONSUMIDOR` y no se indica membresía, ni la tiene ya.
**Respuesta del sistema:** rechaza la operación completa, cita `RN-SP-018` e indica que debe acompañarse de la membresía. El estado «consumidor sin nivel» no existe, y admitirlo aunque fuera por una petición dejaría a la persona en un limbo del que solo se sale con otra llamada que nadie garantiza que llegue.

### EX-006 — Membresía indicada sin que corresponda

**Condición:** se indica membresía y la asignación **no** concede el primer rol `CONSUMIDOR` —porque la persona ya lo tenía, ya tiene membresía, o ninguno de los roles es de consumidor.
**Respuesta del sistema:** rechaza la petición por campo no admitido e indica que cambiar la membresía de quien ya la tiene es `RF-SP-032`. Aceptarla convertiría esta operación en una segunda vía para asignar membresías, con reglas que no son las suyas.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Al menos un rol informado | Debe indicar al menos un rol. |
| `VAL-002` | Identificadores de rol con formato válido | El identificador de rol no es válido. |
| `VAL-003` | Los roles existen y están activos | Uno o más roles no son válidos. |
| `VAL-004` | Los permisos de los roles están contenidos en los del actor | No puede asignar roles con permisos que usted no posee. |
| `VAL-005` | Como máximo 100 roles por petición | No es posible asignar más de 100 roles en una sola solicitud. |
| `VAL-006` | Usuario existente y no eliminado | El usuario solicitado no existe. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-251` | El sistema asigna a un usuario roles cuyos permisos están contenidos en los del actor |
| `CA-SP-252` | El sistema conserva los roles que el usuario ya tenía: la operación nunca retira ninguno |
| `CA-SP-253` | El sistema rechaza la operación completa si un solo rol excede los permisos efectivos del actor, e indica cuál |
| `CA-SP-254` | El sistema rechaza asignar un rol inactivo, indicando cuál |
| `CA-SP-255` | El sistema rechaza asignar un rol inexistente o eliminado, sin distinguir ambos casos |
| `CA-SP-256` | El sistema ignora los roles ya asignados sin producir error ni duplicados |
| `CA-SP-257` | El sistema no registra evento cuando ninguno de los roles indicados era nuevo |
| `CA-SP-258` | Los permisos efectivos del usuario incluyen los del rol nuevo una vez renovado su token de acceso |
| `CA-SP-259` | Asignar el primer rol `CONSUMIDOR` junto con su membresía deja ambas cosas escritas en la misma transacción y bajo el mismo identificador de correlación |
| `CA-SP-369` | El sistema rechaza asignar el primer rol `CONSUMIDOR` sin indicar membresía |
| `CA-SP-370` | El sistema rechaza una membresía indicada cuando la persona ya la tiene o cuando ningún rol asignado es de consumidor |
| `CA-SP-260` | El sistema registra el evento en la auditoría de cambios y en la de seguridad, con severidad alta y con el usuario afectado como objeto |
| `CA-SP-261` | El sistema rechaza la operación a un actor sin el permiso de asignación de roles |

## 13. Casos límite

- **Operación parcialmente válida:** se rechaza **entera**. No se asignan los roles válidos ignorando los que fallan, mismo criterio que `RF-SP-005`.
- **Roles duplicados en la petición:** se normalizan a una sola ocurrencia, sin error.
- **El actor se asigna roles a sí mismo:** se admite, pero `RN-SEG-010` lo acota a lo que ya posee, de modo que no gana nada. No hay regla equivalente a `RN-SEG-011` aquí: esa protege a los roles, no a las personas.
- **Asignación concurrente con la eliminación del rol:** ambas se serializan sobre la fila del rol —la eliminación toma bloqueo exclusivo y esta operación uno compartido—, según el contrato que `RF-SP-009` fijó en su pregunta 3. Es la garantía de `CA-SP-165`.
- **Asignación concurrente del mismo rol al mismo usuario:** la clave primaria compuesta de `user_roles` absorbe el empate sin error interno.
- **Usuario inactivo o bloqueado:** puede recibir roles. Los tendrá cuando vuelva a estar activo, y `RN-SEG-002` no interviene porque afecta al estado del rol, no al de la persona.
- **Asignar un rol a más de 100 personas:** no es esta operación. Aquí el límite es de roles por usuario, no de usuarios por rol; la asignación masiva no existe como requerimiento.
- **Latencia de hasta quince minutos:** si hace falta que alguien tenga un permiso ya, el camino es ampliar un rol que ya porta (`RF-SP-005`), que sí es inmediato.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación. La segunda se arrastró de `RF-SP-029`, aprobada el mismo día.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿`RN-SEG-010` se comprueba comparando permisos, o comparando roles? | **Comparando permisos:** el actor puede asignar un rol si posee **todos** los permisos que ese rol declara. La alternativa —exigir que el rol fuera descendiente de alguno de los del actor— era más barata de calcular, porque bastaba recorrer la cadena de rol padre, pero rechazaría asignaciones perfectamente legítimas: un rol de otra rama del árbol cuyos permisos el actor sí posee. Y acoplaría la asignación de roles a la jerarquía de contención, que existe para acotar **qué declara un rol**, no para decidir quién puede repartirlo. El `plan.md` debe resolverla con **un único componente compartido** con `RF-SP-024` y `RF-SP-005`, para que las tres comprobaciones no puedan divergir con el tiempo |
| 2 | ¿Debe rechazarse asignar un rol a un usuario eliminado? | **Sí, y se trata como inexistente**, arrastrado de `RF-SP-029`. Aquella resolución cerró la pregunta desde el otro lado: eliminar a una persona **retira** sus asignaciones, de modo que asignarle un rol después dejaría una fila que contradice lo que la eliminación acababa de hacer, y volvería a bloquear el borrado del rol por `RN-SEG-008`. `EX-004` lo rechaza sin distinguir entre nunca haber existido y haber sido eliminado |
| 3 | ¿La asignación debe forzar la renovación del token de la persona afectada? | **No.** El rol nuevo tarda hasta quince minutos en aplicarse, y esa latencia ya está declarada en `security.md` §4.5. Forzar la renovación exigiría revocar su refresh token, es decir, **expulsar a alguien de su sesión por haberle concedido algo** — un efecto desproporcionado para una operación que solo amplía. La consecuencia práctica queda escrita en §13: si hace falta que alguien tenga un permiso ya, el camino es ampliar un rol que **ya porta** con `RF-SP-005`, que sí es inmediato. **En `RF-SP-031` la respuesta será la contraria**, y esa asimetría es deliberada: conceder puede esperar, retirar no |
| 4 | ¿Hay un tope de roles simultáneos por persona? | **No se fija tope de negocio.** El token de acceso transporta los códigos de rol, de modo que una persona con muchos roles produce un token grande que viaja en cada petición; pero nadie sabría cuál es el número correcto, y un tope elegido a ojo rechazaría asignaciones legítimas el día que alguien necesitara una más. El `plan.md` lo anota como **riesgo** con su condición de disparo: el día que el token supere el tamaño razonable de una cabecera HTTP. La corrección, llegado el caso, no sería un tope sino dejar de transportar los códigos de rol en el token, que es un cambio en `security.md` §4.5 y no en este requerimiento |
