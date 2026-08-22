# PLAN — `RF-SP-031` Retirar roles de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-031` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |

---

## 1. Enfoque

Es la operación inversa de `RF-SP-030` y **no es su simétrica**. Conceder solo amplía, y ampliar nunca deja nada inconsistente. Retirar sí, y por eso este requerimiento tiene tres reglas que la asignación no necesita y una decisión de sesión que allí se resolvió al revés.

Las tres reglas miran a sitios distintos y ninguna se deduce de las otras:

- **`RN-SP-001`** mira al **sistema**: no puede quedarse sin superadministrador activo.
- **`RN-SP-015`** mira a la **persona**: sin rol `CONSUMIDOR` no puede quedarle membresía, de modo que el retiro del último la arrastra.
- **`RN-SP-022`** mira a **terceros**: quien tiene equipo a cargo no pierde su rol comercial hasta que ese equipo se reasigne.

Las dos últimas resuelven el mismo problema en direcciones opuestas, y esa asimetría es deliberada. La membresía **cae en cascada** porque solo afecta a la persona misma; el equipo **bloquea** porque reasignarlo en silencio cambiaría a quién pertenece un resultado de negocio sin que nadie lo haya decidido. Es la misma postura que `RN-SEG-008` toma con un rol que tiene hijos.

Y una tercera diferencia, que es la que da forma a la implementación: **el retiro revoca las sesiones de la persona**. El token de acceso transporta los códigos de rol, de modo que sin revocación quien pierde un rol seguiría ejerciéndolo hasta quince minutos — y esa ventana se abre justo cuando alguien decidió que dejara de poder hacer algo. `RF-SP-030` **no** las revoca. Conceder puede esperar; retirar no.

La forma sigue siendo **sustractiva e idempotente**: retira los roles que estaban, ignora los que no, y si ninguno estaba no escribe nada. Y se aplica **entera o no se aplica**.

## 2. Cambios de esquema

**Ninguno.**

`user_roles`, `user_memberships` y `user_supervisors` las crea `RF-SP-024` (`V19`, `V20`, `V21`); el índice sobre `user_roles (role_id)` lo crea `RF-SP-030` (`V25`), y el acceso por superior vigente —`ix_user_supervisors_supervisor_vigente`— lo crea `RF-SP-028` (`V24`), que necesita exactamente la misma consulta para su propia comprobación de `RN-SP-022`. Este requerimiento la reutiliza sin añadir nada.

Tres consecuencias del esquema existente que este plan da por sentadas y que conviene tener a la vista:

- **El retiro de la membresía es un `DELETE` de la fila**, no un `UPDATE`. La clave primaria de `user_memberships` es `user_id` (`requirements/sp.md` §10.12): no hay estado «sin membresía» que escribir, hay ausencia de fila.
- **El cierre del superior es un `UPDATE` de `ended_at`**, nunca un `DELETE`. `RN-SP-021` obliga a conservar quién estuvo a cargo de quién y hasta cuándo, y la unicidad parcial `uq_user_supervisors_vigente` —sobre `user_id WHERE ended_at IS NULL`— deja de aplicar en cuanto la fila se cierra, que es lo que permite que la persona vuelva a tener superior después.
- **El retiro en `user_roles` es un `DELETE` de filas**, y no necesita `ON CONFLICT`: borrar lo que no está no es un conflicto, son cero filas afectadas. La idempotencia sale gratis, al contrario que en `RF-SP-030`.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `User` | Modificado | `revokeRoles(...)`: retira los roles presentes y devuelve **cuáles se retiraron realmente**, si la persona queda sin rol `CONSUMIDOR` y si queda sin rol `VENDEDOR` |
| `domain` | `RoleGrantPolicy` | Sin cambios | Componente compartido de `RF-SP-030`. `RN-SEG-010` aplica igual al retiro (`spec.md` §14, pregunta 2) |
| `domain` | `CommercialRank` | Sin cambios | Componente compartido de `RF-SP-030`. Aquí decide si el retiro deja a la persona sin ningún rol de clasificación `VENDEDOR` |
| `domain` | `RootRoleGuard` | **Nuevo, compartido** | `RN-SP-001` en un solo sitio: cuenta los portadores **activos** del rol raíz bajo el bloqueo que serializa la comprobación. Lo consumen `RF-SP-028`, `RF-SP-029` y este requerimiento |
| `domain` | `UserRepository` | Modificado | Añade el conteo de subordinados vigentes y el cierre de la asignación de superior |
| `application` | `RevokeUserRolesService` | Nuevo | Caso de uso. `@Transactional`, aplica el orden de `plan.md` §4, escribe la cascada y emite la auditoría |
| `application` | `SessionRevoker` | **Nuevo** | Puerto hacia `shared/security` para revocar los refresh tokens de una persona. Lo estrena este requerimiento; `RF-SP-028` y `RF-SP-029` usan el mismo |
| `infrastructure` | `JpaUserRepository` | Modificado | `DELETE` sobre `user_roles` y `user_memberships`, `UPDATE` de `ended_at` sobre `user_supervisors` |
| `api` | `UserController` | Modificado | Añade `POST /api/v1/users/{id}/roles/revocations` |
| `api` | `RevokeRolesRequest` | Nuevo | DTO de entrada con Bean Validation (`VAL-001`, `VAL-002`, `VAL-005`) |

`RootRoleGuard` se declara compartido por el mismo motivo que `RoleGrantPolicy` en `RF-SP-030`: `RN-SP-001` la comprueban tres requerimientos, y `requirements/sp.md` §5.1 exige que la comprobación **se serialice sobre el conjunto de portadores activos del rol raíz**, no sobre la fila del usuario afectado. Esa es la parte que se implementa mal si se escribe tres veces — y el modo de fallo no es un error visible, es que dos retiros concurrentes pasen los dos.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/users/{id}/roles/revocations` | Retira roles del usuario |

**Petición**

```json
{
  "roleIds": [
    "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01"
  ]
}
```

**No se solicita motivo.** Es la eliminación de una asociación, por la excepción del Art. V.13 que `RN-SP-005` ya aplicó a los permisos de un rol.

!!! warning "Enmienda a `requirements/sp.md` §9 — `POST …/revocations` y no `DELETE` con cuerpo (Art. I.7)"

    La tabla de API declara `DELETE /api/v1/users/{id}/roles`, y esa forma no sirve aquí por la misma razón que la descartó `RF-SP-006` §4 el 21-08-2026: **la lista de roles viaja en el cuerpo**, y RFC 9110 no define semántica para el cuerpo de un `DELETE`. Es admisible en OpenAPI 3.1 y Spring lo soporta, pero un intermediario puede descartarlo sin avisar, y entonces el retiro llegaría sin roles. El fallo sería silencioso, que es la peor forma de fallar en una operación que revoca sesiones.

    Pasar los identificadores por *query string* tampoco sirve: con cien UUID la URL supera los límites habituales de longitud.

    `revocations` es un subrecurso: cada petición **crea** una revocación sobre el usuario, que es lo que `POST` significa. La tabla de §9 se corrige en el mismo pase.

    **`RF-SP-033` conserva su `DELETE`** y no le alcanza esta enmienda: no lleva cuerpo, porque solo puede haber una membresía y no hay nada que indicar (`RF-SP-033` §6.1).

**Respuesta `200`** — `UserResponse`, definido en `RF-SP-024`: la persona con su lista de roles actualizada, su membresía —ausente si la cascada la retiró— y su superior vigente, ausente si el retiro lo cerró.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Lista vacía, identificador malformado o más de 100 elementos | `VAL-001`, `VAL-002`, `VAL-005` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `users:assign-roles` | `AUTH-002` |
| `404` | El usuario no existe o está eliminado (`EX-004`) | `VAL-006` |
| `409` | El retiro dejaría al sistema sin superadministrador activo (`EX-001`) | `RN-SP-001` |
| `409` | Algún rol declara permisos que el actor no posee (`EX-003`) | `RN-SEG-010` |
| `409` | La persona tiene equipo a cargo (`EX-005`) | `RN-SP-022` |
| `500` | Fallo no controlado | `ERR-500` |

Los tres `409` son reglas de negocio violadas **sobre datos que existen**, que es exactamente la frontera que `RF-SP-001` fijó frente al `422`. Aquí no hay ninguna referencia del cuerpo que no resuelva: un rol que la persona no tiene no es un error, es `FA-001`.

**El cuerpo del `409` por `RN-SP-022` informa cuántas personas tiene a cargo, sin listarlas.** Quién forma ese equipo se consulta con `RF-SP-042`, que tiene su propio permiso; devolver la lista aquí sería conceder ese permiso por una puerta lateral.

**Orden de verificación**

1. Formato, obligatoriedad y límite de 100, todas juntas.
2. Usuario existente y no eliminado.
3. Contención en el actor (`RN-SEG-010`).
4. El retiro no deja al sistema sin superadministrador activo (`RN-SP-001`), bajo el bloqueo de `RootRoleGuard`.
5. Si el retiro dejaría a la persona sin ningún rol `VENDEDOR`, no tiene a nadie a cargo (`RN-SP-022`).

Los pasos 4 y 5 no son evaluables sin haber resuelto antes qué roles se retiran de verdad —los que la persona no tenía no cuentan—, y el paso 5 necesita además el cálculo de `CommercialRank`. El orden es dependencia.

**No se verifica que los roles existan.** Es la asimetría con `RF-SP-030` que más fácilmente se implementa de más: retirar un rol eliminado del catálogo es legítimo, porque la asignación sigue ahí y debe poder soltarse. Lo que se comprueba es la contención en el actor, que se evalúa sobre los permisos que el rol declara, exista o no en el catálogo vigente.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/users/{id}/roles/revocations` | `users:assign-roles` |

Es el **mismo permiso** que la asignación, y es deliberado: `requirements/sp.md` §9 lo declara así para las dos operaciones. Separarlos sugeriría que retirar es menos delicado que conceder, y `spec.md` §14 pregunta 2 razona lo contrario — quien puede desarmar el acceso de otro tiene tanto poder como quien lo arma.

**`RN-SEG-010` aplica también al retiro** y se evalúa leyendo la base de datos, no la caché. El argumento en contra —que retirar solo reduce y por tanto no permite escalar— es cierto sobre el privilegio propio e ignora el daño de poder desarmar a otro: sin esta comprobación, un administrador menor podría desmontar el acceso de uno mayor retirándole roles que él mismo no podría asignar.

La urgencia queda cubierta por otra vía y conviene que esté escrita: **`RF-SP-028` corta el acceso entero sin mirar roles**, y esa sí es la operación de contención.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Roles retirados | `audit_deletion_log` | `deletion_type = 'ASSOCIATION'`, entidad `users`, con los roles retirados por código. **Sin motivo** (Art. V.13) |
| Membresía retirada en cascada | `audit_deletion_log` | Evento propio, `deletion_type = 'ASSOCIATION'`, con `snapshot` de la membresía y su vigencia. **Mismo `correlation_id`** (`RN-SP-015`) |
| Superior cerrado en cascada | `audit_change_log` | `action = UPDATE`: la fila **no se borra**, se cierra con su `ended_at`. **Mismo `correlation_id`** (`RN-SP-019`, `RN-SP-021`) |
| Roles retirados | `audit_security_log` | `event_type = 'USER_ROLES_REVOKED'`, `severity = 'ALTA'`, `outcome = 'SUCCESS'`, `target_user_id` de la persona afectada |
| Ninguno retirado | — | **Ningún evento**: si ninguno de los roles estaba asignado, nada cambió (`CA-SP-268`) |
| Rechazo por `EX-001`, `EX-003` y `EX-005` (`409`) | `audit_error_log` | `resource = 'users'`, `error_type = 'BUSINESS_RULE'`, `severity = **ALTA**` para `EX-003` —es un intento de desarmar a otro— y **Media** para `EX-001` y `EX-005`, que son estados del sistema y no intentos de nadie |
| Rechazo `404` por `EX-004` | — | **No se audita**: `ck_audit_error_log_status` rechaza el `404` |
| Rechazo `400` de formato | — | **No se audita** |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

**La cascada de la membresía va a `audit_deletion_log` y la del superior a `audit_change_log`**, y la diferencia no es un descuido: una desaparece y la otra se cierra. Escribir el cierre del superior como eliminación diría que la fila se fue, y `RN-SP-021` exige justo lo contrario — que siga ahí para responder quién estuvo a cargo de quién y hasta cuándo.

**La revocación de sesiones no emite evento propio.** Es la consecuencia mecánica del retiro, no una decisión independiente, y `security.md` §8.1 no declara ningún tipo de evento para ella. Lo que la hace investigable es que comparte `correlation_id` con `USER_ROLES_REVOKED`.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| `DELETE` de `user_roles` y de `user_memberships`, `UPDATE` de `user_supervisors`, y sus eventos de `audit_deletion_log` y `audit_change_log` | **La misma** (Art. V.14) |
| Comprobación de `RN-SP-001` | Dentro de la misma, **bajo bloqueo** sobre el conjunto de portadores activos del rol raíz |
| Revocación de los refresh tokens | Dentro de la misma transacción, **antes** del commit |
| Evento `USER_ROLES_REVOKED` en `audit_security_log` | **Independiente**, `REQUIRES_NEW`, enganchada al commit |
| Auditoría de los rechazos | **Independiente**, `REQUIRES_NEW`, sin esperar a un commit que no llega |

**La revocación de sesiones va dentro de la transacción, y no después del commit como la invalidación de caché de `RF-SP-005`.** La diferencia es qué pasa si falla. Una caché que no se invalida deja un dato viejo unos segundos; una sesión que no se revoca deja **el acceso que se acaba de retirar** funcionando quince minutos, que es exactamente el resultado que este requerimiento existe para impedir. Si la revocación falla, el retiro debe fallar con ella.

La comprobación de `RN-SP-001` debe serializarse, no basta con leer y contar: dos retiros concurrentes sobre el último superadministrador leerían ambos el estado anterior y pasarían los dos (`spec.md` §13). El bloqueo es sobre el conjunto de portadores activos del rol raíz, el mismo contrato que `RF-SP-028` y `RF-SP-029`.

## 8. Impacto sobre otros módulos

- **`shared/security`** expone `SessionRevoker`, el puerto de revocación de refresh tokens. Es la primera vez que se necesita; `RF-SP-028` —desactivar— y `RF-SP-029` —eliminar— usan el mismo. Su implementación depende de que exista el almacén de refresh tokens, que crea `RF-SP-035`.
- **`RF-SP-030`** comparte `RoleGrantPolicy` y `CommercialRank`, y aporta `RootRoleGuard` a `RF-SP-028` y `RF-SP-029`.
- **`RF-SP-033`** queda como operación **correctiva** y no como la salida del estado de consumidor: esa salida es este requerimiento. Su `EX-001` rechaza justo lo que aquí ocurre en cascada, y las dos cosas son coherentes porque allí el rol permanece y aquí desaparece.
- **`RF-SP-041`** es la única vía para reasignar el equipo que `EX-005` bloquea. Esta operación no lo ofrece ni lo insinúa.
- **`RF-SP-042`** es donde se consulta quién forma ese equipo, y por eso el `409` informa cuántos sin listarlos (§4).
- **`security.md` §8.1** ya enumera `USER_ROLES_REVOKED` y el `CHECK` del esquema ya lo admite. No hay enmienda que tramitar.
- **`requirements/sp.md` §9** se enmienda por §4: `DELETE /api/v1/users/{id}/roles` pasa a `POST /api/v1/users/{id}/roles/revocations`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| `DELETE` con la lista en el cuerpo | RFC 9110 no le define semántica y un intermediario puede descartarlo sin avisar. El retiro llegaría sin roles y el fallo sería silencioso, en una operación que además revoca sesiones (§4) |
| Los identificadores por *query string* | Con cien UUID la URL supera los límites habituales de longitud de proxies y servidores |
| Rechazar el retiro del último rol `CONSUMIDOR` mientras haya membresía | Es lo que decía el borrador de `spec.md`, y produce un **bloqueo mutuo** con `RN-SP-018`: nadie podría dejar de ser consumidor nunca (`spec.md` §14, pregunta 3) |
| Reasignar el equipo al superior del superior en lugar de rechazar | Cambiaría en silencio a quién pertenece un resultado de negocio. La estructura comercial decide atribución, y moverla como efecto secundario de retirar un rol no es una decisión que este requerimiento pueda tomar |
| No aplicar `RN-SEG-010` al retiro | El argumento —retirar solo reduce— es cierto sobre el privilegio propio e ignora el daño de desarmar a otro. Un administrador menor podría desmontar el acceso de uno mayor (§5) |
| No revocar sesiones, aceptando la latencia de quince minutos | Deja funcionando el acceso que se acaba de retirar, en la ventana exacta en que alguien decidió que dejara de funcionar. Es la respuesta contraria a la de `RF-SP-030` y la asimetría es deliberada |
| Revocar sesiones **después** del commit | Si falla, el retiro queda hecho y el acceso sigue vivo. Es el modo de fallo que el requerimiento existe para impedir (§7) |
| Borrar la fila de `user_supervisors` en vez de cerrarla | `RN-SP-021` exige conservar el historial: determina a quién se atribuía cada resultado en cada momento, y las comisiones lo necesitarán |
| Un solo evento de auditoría para el retiro y sus dos cascadas | Son tres hechos sobre tres tablas, y `RF-SP-011` y `RF-SP-012` deben poder consultarlos por separado. Lo que los ata es el `correlation_id` |
| Comprobar `RN-SP-001` leyendo y contando, sin bloqueo | Dos retiros concurrentes sobre el último superadministrador pasarían los dos. El modo de fallo no es visible: no hay error, hay un sistema sin administración |
| Verificar que los roles a retirar existan en el catálogo | Impediría soltar la asignación de un rol eliminado, que es un caso legítimo y frecuente (§4) |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| `RN-SP-001` se implementa sin serializar y dos retiros concurrentes dejan el sistema sin superadministrador | **Alto** | `RootRoleGuard` compartido con el bloqueo dentro; prueba de integración concurrente en `T-13` |
| La revocación de sesiones se mueve fuera de la transacción «para no alargarla» | **Alto** | Declarado en §7 y en §9. Si falla, el retiro debe fallar |
| El cierre del superior se implementa como `DELETE` | **Alto** | `RN-SP-021` y `CA-SP-405` exigen que la fila permanezca con su `ended_at` |
| La cascada de la membresía se olvida y queda huérfana | **Alto** | `CA-SP-265` y `CA-SP-371` verifican el estado final y la recuperación por `correlation_id` |
| El `409` de `RN-SP-022` lista el equipo y filtra datos que exigen `RF-SP-042` | Medio | §4 lo declara: se informa cuántos, no quiénes. `CA-SP-406` lo verifica |
| El actor se retira a sí mismo el permiso de volver a asignarse roles | Medio | Se admite (`spec.md` §13). No hay regla que lo impida más allá de `RN-SP-001`; la interfaz debería advertirlo |
| Se retira un rol creyendo que se retira un permiso que otro rol sigue concediendo | Medio | `CA-SP-263` lo verifica. El camino correcto es consultar `RF-SP-026` antes, que devuelve los permisos efectivos ya resueltos |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-262` | Integración | Las filas desaparecen de `user_roles` y las demás se conservan |
| `CA-SP-263` | Integración | Un permiso que otro rol vigente concede **permanece** entre los efectivos |
| `CA-SP-264` | API | El retiro que dejaría al sistema sin superadministrador activo devuelve `409` |
| `CA-SP-265` | Integración | El retiro del último rol consumidor retira la membresía en la misma transacción y bajo el mismo `correlation_id` |
| `CA-SP-371` | Integración | Tras ese retiro no queda ni rol ni membresía, y la operación se recupera entera filtrando por `correlation_id` |
| `CA-SP-405` | Integración | El retiro del último rol `VENDEDOR` **cierra** la asignación con su `ended_at` y **no borra** la fila |
| `CA-SP-406` | API | Quien tiene equipo a cargo recibe `409` con **cuántas** personas, sin listarlas |
| `CA-SP-407` | Integración | Retirar un rol `VENDEDOR` a quien conserva otro **no** cierra su asignación de superior |
| `CA-SP-266` | Unitaria + API | `RoleGrantPolicy` rechaza y la API devuelve `409` con `RN-SEG-010` |
| `CA-SP-267` | Integración | Los roles que la persona no tenía se ignoran sin error |
| `CA-SP-268` | Integración | Ninguna fila de auditoría cuando ninguno estaba asignado |
| `CA-SP-269` | Integración | Se admite dejar a la persona sin ningún rol cuando ninguna regla lo impide |
| `CA-SP-270` | API + Integración | El endpoint no admite motivo y la fila de `audit_deletion_log` queda sin él |
| `CA-SP-271` | Integración | Una fila en `audit_deletion_log` y otra en `audit_security_log`, severidad Alta y `target_user_id` |
| `CA-SP-361` | Integración | Tras el retiro los refresh tokens quedan revocados y el token de acceso vigente deja de admitirse |
| `CA-SP-362` | Integración | El permiso retirado deja de concederse **de inmediato**, sin esperar a ningún token |
| `CA-SP-363` | Integración | `RF-SP-030` **no** revoca sesiones y este sí: la asimetría se verifica en una sola prueba que ejecuta las dos operaciones |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Dos retiros concurrentes sobre el último superadministrador | **Integración concurrente** | Uno termina con `200` y el otro con `409`. Sin el bloqueo de `RootRoleGuard` ambos pasan, que es justo lo que hace valer la prueba |
| Permiso concedido por dos roles | Integración | Retirar uno no lo quita — es `CA-SP-263` desde el otro lado |
| Retirar un rol inactivo | Integración | Se admite, no cambia los permisos efectivos y **sí** produce evento: la asignación desaparece |
| Retirar un rol eliminado del catálogo | Integración | Se admite: la asignación existe y debe poder soltarse (§4) |
| El actor se retira roles a sí mismo | API | Se admite salvo que incumpla `RN-SP-001` |

`CA-SP-363` es la única prueba del módulo que verifica una **asimetría entre dos requerimientos**, y por eso debe vivir en un solo sitio y ejecutar las dos operaciones seguidas. Repartida entre las dos tripletas, cada mitad pasaría sin que nadie comprobase la diferencia.
