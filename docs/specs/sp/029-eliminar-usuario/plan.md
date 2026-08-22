# PLAN — `RF-SP-029` Eliminar usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-029` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026, enmendada el 22-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide tres cosas: **en qué orden se captura y se destruye**, **por qué tres tablas se tratan de tres maneras distintas en la misma transacción**, y **qué código de evento merece una baja**.

---

## 1. Enfoque

Es el gemelo de [`RF-SP-009`](../009-eliminar-rol/plan.md) sobre las personas, y hereda de él la forma: `POST` sobre un subrecurso `deletion` con el motivo en el cuerpo, borrado lógico, estado conservado en `audit_deletion_log`, y ninguna operación de restauración. Lo que allí está argumentado —por qué no `DELETE` con cuerpo, por qué el motivo se valida el primero, por qué no hay papelera— **no se repite**.

Cuatro decisiones lo gobiernan:

1. **El orden de los pasos 7 y 8 de `spec.md` §8 no es indiferente, y es lo que más fácil resulta implementar mal.** El estado se captura **antes** de tocar nada. Si las asignaciones se borraran primero, el evento quedaría sin ellas y **la información se perdería sin que nada fallara** (`CA-SP-360`). Es el único defecto de este requerimiento cuyo síntoma es la ausencia silenciosa de un dato.
2. **Tres tablas asociadas, tres tratamientos distintos, una sola transacción.** `user_roles` y `user_memberships` se **borran**; `user_supervisors` se **cierra** con fecha de fin y su fila permanece. La asimetría es deliberada y `spec.md` §7 la explica: los dos primeros dicen qué podía hacer alguien hoy y no significan nada cuando se va; el historial de mando dice **a quién se atribuía su producción**, y eso lo necesitarán las comisiones mucho después de la baja.
3. **Nada se libera.** `uq_users_username` y `uq_users_email` son **totales**, sin cláusula `WHERE` (`RF-SP-024` §2), de modo que el nombre de usuario y el correo del eliminado siguen ocupados para siempre. Es la asimetría explícita con `RF-SP-009`, donde eliminar un rol **sí** libera su código y su nombre, y conviene entenderla al revés de como suena: aquí no hay que hacer nada para que se cumpla, precisamente porque la restricción es la simple.
4. **La baja de una persona merece código de evento propio.** `security.md` §8.1 no lista la eliminación de un usuario, y `RF-SP-014` §2 la resolvió provisionalmente asignándole `USER_STATUS_CHANGED`. Este plan lo corrige: se añade `USER_DELETED` (§6).

`domain` participa en lo que puede probarse sin base de datos: el motivo, la marca de eliminación, `RN-SP-017` y `RN-SP-001`, todos reutilizados de `RF-SP-028`.

## 2. Cambios de esquema

**Ninguno. Este requerimiento no crea ni altera ninguna tabla, columna, restricción ni índice.**

Es una afirmación que conviene sostener, porque el `plan.md` de `RF-SP-024` §2 decía lo contrario: allí `deleted_at` se asignaba a este requerimiento. **Se corrigió al escribir sus `tasks.md`** (Art. I.7) y la columna nace con la tabla, en `V18__create_users.sql`, por dos motivos que aquí se confirman:

- `architecture.md` §6.4 declara `deleted_at` **columna obligatoria de toda tabla de negocio**, junto a `id`, `created_at` y `updated_at`. `users` sin ella era una excepción que nadie había declarado.
- **Diez requerimientos la leen antes de que este la escriba.** `RF-SP-003` y `RF-SP-009` se implementan antes y sus planes ya la daban por existente; `RF-SP-025`, `RF-SP-026` y `RF-SP-027` no serían implementables sin ella. El criterio de `RF-SP-024` §2 —«una columna disponible antes de que exista la regla que la gobierna se acaba usando por un camino que nadie diseñó»— vale para `failed_attempts` y `locked_until`, que **nadie lee** hasta `RF-SP-034`; no vale para esta.

Lo que sigue siendo de este requerimiento es **escribirla**: es el único que la pone a un valor distinto de nulo.

| Objeto | De dónde viene | Para qué lo usa este requerimiento |
|---|---|---|
| `users.deleted_at` | `V18` (`RF-SP-024`) | La marca que se escribe |
| `uq_users_username`, `uq_users_email` | `V18` | **Totales**: no liberan nada al eliminar (`RN-SP-016`, `CA-SP-244`) |
| `user_roles`, `user_memberships` | `V19`, `V20` (`RF-SP-024`) | Filas que se borran |
| `user_supervisors` | `V21` (`RF-SP-024`) | Fila vigente que se **cierra**, no se borra |
| `ix_user_supervisors_supervisor_vigente` | `V24` (`RF-SP-028`) | Verificar `RN-SP-022` sin recorrer el historial |
| `refresh_tokens` | `RF-SP-034` | Revocar todas las sesiones |
| `ck_deletion_reason` | `V4` (`RF-SP-001`) | El motivo no puede quedar vacío ni siquiera por `INSERT` directo |

**`RF-SP-034` y `RF-SP-028` preceden a este requerimiento.** El primero por `refresh_tokens`; el segundo porque aquí se reutilizan cinco de sus componentes y su índice (§3, §8).

**No se añade columna de «eliminado por» ni de fecha de motivo.** El actor no vive en la tabla de negocio (Art. V.7) y el motivo pertenece al evento, no a la fila. Quién eliminó a quién y por qué se responde con `RF-SP-012`, que es la única fuente.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `User` | **Modificado** | `delete(motivo)`: valida el motivo, marca el borrado y **devuelve el estado previo** para el registro de eliminación |
| `domain` | `SelfOperationGuard` | Sin cambios | `RN-SP-017`, creado en `RF-SP-028` |
| `domain` | `RootAdministratorPresence` | Sin cambios | `RN-SP-001`, creado en `RF-SP-028` |
| `domain` | `DeletionReason` | Nuevo | Objeto de valor del motivo: recorta y exige contenido. **Distinto de `StatusChangeReason`** solo en su nombre; ver abajo |
| `application` | `DeleteUserService` | Nuevo | Caso de uso. `@Transactional`, orquesta el orden de §4, captura el estado, destruye, revoca y audita |
| `application` | `UserDeletionAuditor` | Nuevo | Puerto hacia `shared/audit` para `audit_deletion_log` |
| `application` | `UserSecurityAuditor` | Sin cambios | Puerto de `RF-SP-024`. Aquí emite `USER_DELETED` |
| `application` | `RootRoleHolderRepository` | Sin cambios | Puerto de `RF-SP-028`. **Misma consulta, mismo bloqueo, mismo orden** |
| `application` | `SupervisedTeamCounter` | Sin cambios | Puerto de `RF-SP-028` |
| `application` | `SessionRevoker` | Sin cambios | Puerto de `RF-SP-028`, implementado por `RF-SP-034` |
| `application` | `AccessRevocationPublisher` | Sin cambios | Puerto de `RF-SP-028` |
| `infrastructure` | `JpaUserRepository` | **Modificado** | Carga bloqueada, captura del estado completo, borrado de asignaciones y cierre de la asignación de superior |
| `api` | `UserController` | **Modificado** | Añade `POST /api/v1/users/{id}/deletion` |
| `api` | `DeleteUserRequest` | Nuevo | DTO con el motivo. Único campo |

Dos decisiones de reparto:

**Cinco componentes se reutilizan de `RF-SP-028` y ninguno se reescribe.** `RN-SP-001` en particular: la consulta que bloquea y cuenta los portadores activos del rol raíz es **exactamente la misma**, con el mismo orden de bloqueo, y `spec.md` §14, resolución 4, de `RF-SP-028` lo exigió por escrito —«la misma lectura se aplica en `RF-SP-029` y `RF-SP-031`, o las tres divergirán»—. Este plan lo cumple no escribiendo nada.

**`DeletionReason` es un tipo distinto de `StatusChangeReason` aunque su comportamiento sea idéntico.** Ambos recortan y exigen contenido, y podría haber uno solo. Se prefieren dos porque **sus reglas van a divergir**: el motivo de eliminación está sujeto a `ck_deletion_reason` en el esquema y el Art. V.13 lo obliga; el de cambio de estado es una exigencia de `spec.md` que podría relajarse sin tocar la base de datos. Un tipo compartido haría que aflojar uno aflojara el otro sin que nadie lo notara.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/users/{id}/deletion` | Elimina lógicamente a una persona, con motivo declarado |

`POST` sobre un subrecurso y no `DELETE` con cuerpo, por lo dicho en `RF-SP-009` §4: RFC 9110 no define semántica para el cuerpo de un `DELETE` y un intermediario puede descartarlo, con lo que la petición se convertiría en un rechazo por motivo ausente que el actor no puede entender ni corregir. Y no por *query string*, o el motivo acabaría en los registros de acceso de proxies y en `request_log`.

**Petición**

```json
{ "reason": "Registro duplicado: la persona ya existía como jperez2." }
```

**Respuesta `204`** — sin cuerpo. `spec.md` §6.2 declara como salida «confirmación, sin cuerpo de datos», y devolver la persona eliminada sería devolver un recurso que la operación acaba de retirar de todas las consultas.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | Motivo ausente o vacío tras recortar (`EX-001`, `VAL-001`, `VAL-002`) | `VAL-001` | `reason` |
| `400` | Cuerpo con campo desconocido | `VAL-001` | El campo sobrante |
| `400` | El identificador no es un UUID en forma canónica | `VAL-001` | `id` |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `users:delete` | `AUTH-002` | — |
| `403` | El identificador es la cuenta del actor (`EX-002`) | `RN-SP-017` | — |
| `404` | No existe usuario con ese identificador, o ya está eliminado (`EX-004`) | `EX-004` | — |
| `409` | Es el último portador **activo** del rol raíz (`EX-003`) | `RN-SP-001` | — |
| `409` | Tiene personas a su cargo (`EX-005`) | `RN-SP-022` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **Los dos `403` son distintos**, igual que en `RF-SP-028` §4 y por el mismo motivo.
- **El cuerpo del `409` de `RN-SP-022` dice cuántas personas y no quiénes** (`CA-SP-408`). Quiénes son se consulta con `RF-SP-042`.
- **El `404` no distingue «nunca existió» de «ya estaba eliminado»** (`EX-004`, Art. V.13). Es el mismo silencio que en `RF-SP-026`.
- **No hay `409` por tener roles asignados.** Es la diferencia con `RF-SP-009`, que rechaza eliminar un rol con portadores: aquí no hay nada aguas abajo que quede colgando, porque **las asignaciones se retiran con la persona** (`spec.md` §13).

**Orden de verificación**

1. **Motivo presente y con contenido.** Primero de todo, porque el Art. V.13 exige rechazar la eliminación sin motivo **antes de ejecutarla** (`CA-SP-242`).
2. **Persona existente y no eliminada** (`EX-004`), cargada **con bloqueo exclusivo de fila**.
3. **La persona no es el actor** (`EX-002`, `RN-SP-017`).
4. **No es el último portador activo del rol raíz** (`EX-003`), con el conjunto bloqueado según `RF-SP-028` §7.
5. **No tiene personas a cargo** (`EX-005`, `RN-SP-022`).
6. **Captura del estado completo**, antes de tocar nada.
7. Marca de eliminación, borrado de asignaciones, cierre del superior, revocación de sesiones y auditoría.

### Qué pasa con cada tabla, y en qué orden

Todo dentro de **una sola transacción**, y el orden importa:

| Paso | Tabla | Operación | Por qué en ese momento |
|---|---|---|---|
| 1 | — | **Captura** de roles, membresía y superior vigente | `CA-SP-360`. Después de borrar ya no hay nada que capturar, y **nada falla** si se olvida |
| 2 | `users` | `UPDATE deleted_at = now()` | La marca. `status` **no cambia**: se conserva como estaba, y así queda en el estado guardado |
| 3 | `user_roles` | `DELETE` de todas sus filas | `spec.md` §14, resolución 3. Es lo que hace que `RN-SEG-008` deje de verlas **sin tocar `RF-SP-009`** |
| 4 | `user_memberships` | `DELETE` de su fila | Ídem. La membresía sigue existiendo en la cadena; lo que desaparece es la asignación |
| 5 | `user_supervisors` | `UPDATE ended_at = now()` **solo de la fila vigente** | `CA-SP-409`. La fila **permanece**: es historial de mando, no una versión vieja de un dato (`RN-SP-021`) |
| 6 | `refresh_tokens` | Revocación con motivo `ACCESO_RETIRADO` | Dentro de la transacción, por el criterio de `RF-SP-028` §7 |

Tres precisiones:

**`status` no se toca.** Podría parecer natural dejarlo en `INACTIVO`, y sería un error: el estado guardado en el registro de eliminación dejaría de decir en qué situación estaba la persona cuando se la eliminó, que es parte de lo que el Art. V.13 exige conservar. Quien no puede entrar es cualquiera con `deleted_at` informado, y eso lo verifica el inicio de sesión sin mirar `status`.

**El cierre del superior usa la misma marca de tiempo que la eliminación**, no una posterior. Si difirieran, el historial diría que la persona estuvo a cargo de alguien durante unos milisegundos después de haber dejado de existir.

**Nadie más cambia de superior.** Si esta persona tuviera equipo a cargo, la operación ya se rechazó en el paso 5. Es lo que garantiza que el paso 6 no pueda dejar a nadie huérfano.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/users/{id}/deletion` | `users:delete` |

- El permiso **ya existe**: lo siembra `V3__seed_permissions.sql` (`RF-SP-010`).
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **Es un permiso propio, distinto de `users:update`.** Quien puede desactivar cuentas no elimina personas por el mero hecho de poder desactivarlas, y esa separación es la que hace que `spec.md` §2 pueda recomendar `RF-SP-028` como la operación habitual: son dos facultades y se conceden por separado.
- **No hay techo de privilegios que verificar**, y la resolución del permiso **sí** puede usar la caché de `security.md` §4.5.
- **`RN-SP-017` se compara contra el identificador del actor**, que el token transporta y que no cambia durante la sesión (`RF-SP-028` §5).
- **No hay filtrado por alcance de datos.** Se revisa con **D-22**.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Eliminación | `audit_deletion_log` | `module = 'SP'`, `entity = 'users'`, `entity_id`, `deletion_type = 'LOGICAL'`, `reason` declarado por el actor, y `snapshot` con **el estado completo capturado en el paso 1** |
| Eliminación | `audit_security_log` | `event_type = 'USER_DELETED'`, `severity = 'ALTA'`, `outcome = 'SUCCESS'`, `target_user_id` de la persona eliminada, `detail` con el motivo y el número de asignaciones retiradas |
| Rechazo `409` por `EX-003` o `EX-005` | `audit_error_log` | `resource = 'users'`, `operation = 'POST /api/v1/users/{id}/deletion'`, `error_code = 'RN-SP-001'` o `'RN-SP-022'`, `error_type = 'BUSINESS_RULE'`, `http_status = 409`, `severity = 'ALTA'`, `message` saneado |
| Rechazo `403` por `EX-002` (`RN-SP-017`) | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, severidad **`ALTA`**, `outcome = 'FAILURE'`, `target_user_id` del propio actor. **No** va a `audit_error_log` |
| Rechazo `404` por `EX-004` | — | **No se audita**: `architecture.md` §6.6.4 lo deja fuera y `ck_audit_error_log_status` lo impide en el esquema |
| Rechazo `400`, incluido el motivo ausente | — | **No se audita** (`architecture.md` §6.6.4): es validación, no regla incumplida |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log` | **No aplica.** La eliminación no es una edición, y registrarla también allí duplicaría el hecho en dos registros con criterios de retención distintos |

### `USER_DELETED` se añade al catálogo cerrado

`security.md` §8.1 enumera catorce eventos y **la baja de una persona no está entre ellos**, pese a que la eliminación de un **rol** sí lo está. `RF-SP-014` §2, al fijar los dieciocho literales, resolvió el hueco atribuyendo a `RF-SP-029` el código `USER_STATUS_CHANGED`. Este plan lo corrige, aplicando el criterio que aquel mismo plan declara:

> «Se desdoblan las filas que agrupan operaciones distintas. El criterio es la pregunta que se hace al filtrar: un responsable de seguridad busca *quién eliminó roles*, no *quién tocó roles*.»

**«Quién eliminó usuarios» es exactamente esa clase de pregunta**, y con `USER_STATUS_CHANGED` habría que responderla filtrando sobre `jsonb`. Además, la simetría lo pide: existe `ROLE_DELETED` y no existiría su equivalente para las personas.

Tres consecuencias, todas declaradas en §8:

- **`security.md` §8.1 gana la fila** «Baja de un usuario — Alta — `SUCCESS`».
- **`ck_audit_security_log_event_type` gana el literal** en `V4__create_audit_logs.sql`, que pasa de dieciocho a diecinueve valores. Mientras nada esté desplegado es una edición de la migración; después sería una alteración de restricción sobre una tabla en uso.
- **`RF-SP-014` §2 corrige su tabla**: `USER_STATUS_CHANGED` deja de tener a `RF-SP-029` como emisor.

Dos decisiones más:

**La severidad del rechazo por `RN-SP-001` y `RN-SP-022` es `ALTA`, no `MEDIA`.** Es la diferencia con `RF-SP-028` §6, donde los mismos dos rechazos van con `MEDIA`, y la asimetría es deliberada: allí el intento fallido deja una cuenta que sigue igual; aquí es un intento de **destruir** al último administrador del sistema o de dejar un equipo huérfano de forma irreversible. Es el mismo criterio con el que `RF-SP-009` §6 puso `ALTA` a los rechazos por `RN-SEG-008`.

**El `snapshot` pasa por el enmascarador** (Art. XV.5) y **no contiene `password_hash`**. `architecture.md` §6.6.3 lo dice con este mismo ejemplo: «el estado de un usuario eliminado se conserva sin su `password_hash`». Sí contiene el nombre de usuario y el correo, porque son la identidad que `RN-SP-016` reserva para siempre y sin la cual el registro no diría a quién se eliminó.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Bloqueo de la fila, bloqueo del conjunto de portadores del rol raíz, captura del estado, marca de borrado, borrado de asignaciones, cierre del superior y su evento en `audit_deletion_log` | **La misma** (Art. V.14) |
| Revocación de los refresh tokens | **La misma**, por el criterio de `RF-SP-028` §7 |
| Publicación del corte de tokens de acceso | **Tras el commit**, nunca antes |
| `audit_security_log` de `USER_DELETED` | **Independiente**, `REQUIRES_NEW`, **enganchada al commit** |
| `audit_security_log` de `RN-SP-017` | **Independiente**, `REQUIRES_NEW`, emitida **sin esperar al commit** |
| `audit_error_log` de un rechazo o un fallo | **Independiente**, `REQUIRES_NEW` |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`@Transactional` vive sobre `DeleteUserService`, en `application`.

**Todo lo destructivo va en una sola transacción, y es lo que hace correcta la operación.** Si el borrado de `user_roles` se confirmara y el evento de eliminación fallara, quedaría una persona sin roles y **sin ningún registro de cuáles tenía**: la información se habría perdido para siempre, porque `spec.md` §14, resolución 3, declara que la auditoría pasa a ser la única fuente. Es el caso que hace del Art. V.14 algo más que una convención en este requerimiento.

**La persona se carga con bloqueo exclusivo de fila** (`SELECT … FOR UPDATE`), y eso resuelve el caso límite que `spec.md` §13 plantea: la eliminación concurrente con un inicio de sesión se serializa sobre esa fila. O la sesión se abre y queda revocada acto seguido —porque la eliminación revoca dentro de su transacción—, o el inicio de sesión ya encuentra a la persona eliminada y la rechaza. **Ningún orden deja una sesión viva sobre una cuenta eliminada.**

**`RN-SP-001` se serializa igual que en `RF-SP-028` §7**, con el mismo bloqueo del conjunto de portadores activos y el mismo orden ascendente. No se repite el argumento; se repite el código, que es de lo que se trata.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| **`security.md`** | **§8.1 gana «Baja de un usuario — Alta — `SUCCESS`»**. Sin esa ampliación, el evento sería un tipo que el catálogo cerrado no reconoce (§6) |
| **`RF-SP-014`** | **Su §2 se corrige dos veces:** `ck_audit_security_log_event_type` pasa de dieciocho a **diecinueve** literales con `USER_DELETED`, y `USER_STATUS_CHANGED` **deja de tener a `RF-SP-029` como emisor**. Es una enmienda de un plan aprobado (Art. I.7), y su compuerta se tramita con este Pull Request |
| **`RF-SP-001`** | Su `V4__create_audit_logs.sql` gana el literal en la restricción. **Antes del primer despliegue** es una edición de la migración; después, una alteración sobre una tabla en uso |
| **`RF-SP-009`** | **No necesita enmienda**, y es el resultado buscado: al desaparecer las filas de `user_roles`, su conteo deja de verlas sin distinguir nada (`spec.md` §14, resolución 2). `CA-SP-359` lo verifica desde el otro lado —el rol pasa a poder eliminarse—, y esa prueba vive **aquí** |
| **`RF-SP-028`** | Precede a este requerimiento y le presta cinco componentes y el índice `ix_user_supervisors_supervisor_vigente`. `RN-SP-001` se resuelve con **la misma consulta y el mismo bloqueo**, como exigió su resolución 4 |
| **`RF-SP-034`** | Precede a este requerimiento: implementa `SessionRevoker` y crea `refresh_tokens`. Y **hereda una obligación**: el inicio de sesión rechaza a quien tiene `deleted_at` informado **sin mirar `status`**, porque este requerimiento no lo cambia (§4) |
| `RF-SP-042` | La estructura de una persona eliminada **no** se consulta: su asignación queda cerrada y aquella consulta solo devuelve las vigentes (`CA-SP-453` y su resolución 4) |
| `RF-SP-025`, `RF-SP-026`, `RF-SP-027` | Dejan de devolverla por defecto, la tratan como inexistente y la excluyen de la edición, respectivamente. Ninguno necesita cambios: los tres ya filtran por `deleted_at` |
| `RF-SP-012` | Es la **única** fuente de qué tenía la persona eliminada. Su consulta responde ahora también por la entidad `users`, sin adaptación: el registro es genérico por diseño |
| `RF-SP-030`, `RF-SP-031`, `RF-SP-032`, `RF-SP-033` | Tratan a la persona eliminada como inexistente. `RF-SP-030` lo declaró en su resolución 2 |
| `requirements/sp.md` | **§6.1 gana las precedencias del bloque de usuarios.** §10.10 **ya recoge** que `deleted_at` es de `V18`; ninguna otra enmienda |
| **Anonimización** | Queda como riesgo abierto con su condición de disparo, heredado de `spec.md` §14, resolución 4: hoy la eliminación conserva nombre y correo indefinidamente. El día que exista una obligación formal de supresión, la decisión alcanza a todos los módulos y se documenta en `docs/security/`, no aquí |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Capturar el estado **después** de borrar las asignaciones | El evento quedaría sin ellas y la información se perdería **sin que nada fallara**. Es el defecto que `CA-SP-360` existe para detectar, y el único de este requerimiento que no produce error |
| Conservar las filas de `user_roles` y `user_memberships` | `spec.md` §14, resolución 3, lo descartó: dejaría a un eliminado apareciendo por descuido en un filtro por rol y volvería a bloquear el borrado de ese rol por `RN-SEG-008` |
| Borrar también la fila de `user_supervisors` | Perdería a quién se atribuía la producción de esa persona, que es lo que las comisiones necesitarán **después** de la baja. `RN-SP-021` obliga a conservar el historial, y `CA-SP-409` verifica la asimetría |
| Dejar `status = 'INACTIVO'` al eliminar | El estado guardado dejaría de decir en qué situación estaba la persona al eliminarse, que es parte de lo que el Art. V.13 exige conservar |
| Eliminación física | Prohibida por `security.md` §3.1: los cuatro registros de auditoría referencian al actor por identificador, y ese identificador debe seguir resolviendo. Las tres claves foráneas son además `ON DELETE RESTRICT` |
| Índices únicos parciales sobre `deleted_at`, como en `roles` | `RN-SEG-001` libera el código de un rol eliminado; `RN-SP-016` **no libera nada**. Copiarlo permitiría registrar a alguien con el nombre de usuario de una persona eliminada, y su actividad sería indistinguible en la auditoría |
| Ofrecer restauración | `CA-SP-249` exige que no exista. Restaurar obligaría a revalidar unicidad, roles vigentes, membresía y estructura comercial, y a decidir qué hacer ante cada colisión: es otro requerimiento, no una variante de este |
| Rechazar la eliminación de quien tiene roles asignados, como hace `RF-SP-009` con los roles | No hay nada aguas abajo que quede colgando: las asignaciones se retiran con la persona. Exigir retirarlas antes sería fricción sin garantía |
| Reasignar el equipo automáticamente al superior del superior | `RN-SP-022` lo prohíbe: cambiaría en silencio a quién pertenece un resultado de negocio. La salida es `RF-SP-041`, persona a persona |
| Reutilizar `USER_STATUS_CHANGED` para la baja | Obligaría a filtrar sobre `jsonb` para responder «quién eliminó usuarios», que es la pregunta que se hace al abrir ese registro. Y dejaría el catálogo asimétrico frente a `ROLE_DELETED` |
| Registrar además el evento en `audit_change_log` | Duplicaría el hecho en dos registros con criterios de retención distintos. La eliminación tiene su registro y su `snapshot` |
| Guardar `password_hash` en el `snapshot` | `architecture.md` §6.6.3 lo excluye con este mismo ejemplo. Un hash conservado es superficie de exposición sin ningún uso |
| Anonimizar los datos personales al eliminar | `spec.md` §14, resolución 4: choca de frente con `RN-SP-016` y alcanza a todos los módulos. Merece una decisión documentada en `docs/security/`, no una línea en este plan |
| Un solo tipo de motivo compartido con `RF-SP-028` | Sus reglas van a divergir: el de eliminación está sujeto a `ck_deletion_reason` y al Art. V.13; el de cambio de estado, solo a una spec. Aflojar uno aflojaría el otro sin que nadie lo notara |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| **El estado se captura después de borrar y la información se pierde en silencio** | **Crítico** | `CA-SP-360` es la prueba, y debe verificar el **contenido** del `snapshot` —los roles y la membresía que tenía—, no solo que la fila exista. Una prueba que compruebe únicamente la presencia del evento da verde con la implementación equivocada |
| `RN-SP-001` se implementa con una consulta propia en lugar de reutilizar la de `RF-SP-028` | **Alto** | Es exactamente lo que `RF-SP-028` §14, resolución 4, prohibió. La prueba concurrente de §11 lo detecta, y la revisión debe comprobar que **no hay una segunda consulta de portadores del rol raíz** en el código |
| El literal `USER_DELETED` no llega a `V4` y el evento falla dentro de su transacción `REQUIRES_NEW` | **Alto** | El síntoma sería un fallo secundario **justo después de una eliminación correcta**, sin relación aparente con ella. Es el mismo caso que `RF-SP-014` §2 describe para `USER_CREATED`. Tarea propia y prueba de la restricción en §11 |
| El cierre del superior usa una marca de tiempo distinta de la eliminación | Bajo | El historial diría que la persona estuvo a cargo de alguien después de dejar de existir. Prueba en §11 comparando ambas marcas |
| Se elimina a alguien y su nombre de usuario queda liberado por un índice parcial escrito por costumbre | **Alto** | `CA-SP-244` lo verifica, y `RF-SP-024` `T-01` comprueba que los índices **no llevan cláusula `WHERE`**. El defecto sería invisible hasta que alguien reutilizara la identidad |
| Con más de una instancia, el token de acceso de la persona eliminada sigue admitiéndose hasta quince minutos | Medio | Heredado de `RF-SP-028` §10, con la misma acotación: los refresh tokens quedan revocados en la base de datos y la sesión no puede prolongarse |
| La operación se usa como baja habitual en lugar de `RF-SP-028` | Medio | `spec.md` §2 lo advierte de forma expresa y el permiso propio lo acota. No hay mitigación técnica: es una decisión de quien opera, y por eso la especificación la deja escrita |
| Nombre y correo del eliminado se conservan indefinidamente sin política de supresión | Medio | Riesgo declarado en `spec.md` §14, resolución 4, con su condición de disparo. **No se resuelve aquí**: alcanza a todos los módulos y exige decisión documentada en `docs/security/` |

## 11. Estrategia de prueba

Niveles: **Unitaria** (dominio, sin Spring ni base de datos), **Integración** (Testcontainers sobre PostgreSQL real, con `V18` a `V24` aplicadas y el esquema de `RF-SP-034`) y **API** (extremo a extremo por HTTP, con autenticación).

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-241` | Integración + API | `deleted_at` queda informado, la operación devuelve `204`, y la persona desaparece del listado por defecto de `RF-SP-025` |
| `CA-SP-242` | API | Motivo ausente, vacío o en blanco devuelve `400` **sin haber tocado ninguna fila** |
| `CA-SP-243` | Integración | La persona eliminada no puede autenticarse, y **todos** sus refresh tokens quedan revocados |
| `CA-SP-244` | Integración | Tras eliminar, un alta con su nombre de usuario devuelve `409`; con su correo, también. **Ninguno queda disponible** |
| `CA-SP-245` | Integración | Un evento de `audit_change_log` anterior que referencia su identificador **sigue resolviendo** al registro conservado, con su nombre de usuario |
| `CA-SP-246` | Integración | `audit_deletion_log` conserva el motivo y un `snapshot` con `status`, roles y membresía que tenía, y **sin `password_hash`** |
| `CA-SP-358` | Integración | Tras eliminar, **no queda ninguna fila** en `user_roles` ni en `user_memberships` para esa persona |
| `CA-SP-359` | Integración | Un rol que solo portaba la persona eliminada **puede eliminarse** con `RF-SP-009`, sin que `RN-SEG-008` lo impida y **sin haber cambiado nada allí** |
| `CA-SP-360` | Integración | El `snapshot` **contiene** los roles y la membresía. Se prueba invirtiendo el orden de los pasos en una implementación de control y comprobando que la prueba falla |
| `CA-SP-408` | Integración + API | Con una persona a cargo, devuelve `409` con `RN-SP-022` e informa **cuántas**; el cuerpo **no las nombra** |
| `CA-SP-409` | Integración | Al eliminar a un vendedor sin equipo, su fila de `user_supervisors` **permanece** con `ended_at` informado y **con la misma marca de tiempo** que `deleted_at` |
| `CA-SP-247` | API | El actor recibe `403` con `RN-SP-017` sobre su propia cuenta, y queda el evento de denegación con severidad **alta** |
| `CA-SP-248` | Integración + API | Eliminar al último portador **activo** del rol raíz devuelve `409` con `RN-SP-001`, aunque existan otros inactivos que lo porten |
| `CA-SP-249` | API | **No existe** ningún endpoint de restauración: ni `POST /users/{id}/restoration`, ni un `PATCH` que ponga `deleted_at` a nulo |
| `CA-SP-250` | API | Un actor autenticado sin `users:delete` recibe `403`, la persona no cambia, y queda el evento de denegación. **Tenerlo `users:update` no basta** |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| **Eliminación concurrente con un inicio de sesión** | **Integración concurrente** | Con dos transacciones reales: o la sesión se abre y queda revocada acto seguido, o el inicio de sesión encuentra a la persona eliminada y la rechaza. **Nunca queda una sesión viva sobre una cuenta eliminada** |
| **Dos eliminaciones concurrentes de dos superadministradores distintos** | **Integración concurrente** | Una `204` y una `409` con `RN-SP-001`; queda al menos un portador activo. Es la misma prueba de `RF-SP-028` sobre esta operación, y comprueba que se reutiliza el bloqueo del conjunto |
| **El literal del evento** | Integración | `ck_audit_security_log_event_type` acepta `USER_DELETED` y rechaza cualquier variante de capitalización |
| Persona con actividad en la auditoría | Integración | Se elimina igual, y su actividad sigue siendo atribuible |
| Persona con roles asignados | API | No lo impide: las asignaciones se retiran con ella |
| Persona con membresía | Integración | La membresía **sigue existiendo en la cadena**; lo que desaparece es su asignación |
| Eliminar a alguien ya eliminado | API | `404` con `EX-004`, indistinguible de un identificador inexistente |
| Motivo con solo espacios | API | `400` tras recortar los extremos, mismo criterio que `RF-SP-009` |
| Motivo de un solo carácter | Integración | **Se admite**: `ck_deletion_reason` exige contenido, no longitud (`architecture.md` §6.6.3) |
| Cuerpo con campo desconocido | API | `400`; no se ignora en silencio |
| Identificador no canónico | API | `1-1-1-1-1` devuelve `400` con `VAL-001` y campo `id`, no `404` |
| `INSERT` directo en `audit_deletion_log` sin motivo | Integración | `ck_deletion_reason` lo rechaza: la garantía no depende de que la petición pase por el DTO |
| Número de sentencias por petición | Integración | Sobre alguien que no porta el rol raíz, la consulta de portadores **no se ejecuta**; sobre alguien sin superior, el `UPDATE` de cierre tampoco |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento, y la prueba de ausencia de cascadas de `RF-SP-012` §11 se ejecuta sobre el esquema completo: **ninguna clave foránea hacia `users` es `ON DELETE CASCADE`**, y esa comprobación es la que impide que alguien convierta esta eliminación lógica en física por el camino más corto.
