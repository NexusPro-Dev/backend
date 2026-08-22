# PLAN — `RF-SP-028` Cambiar el estado de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-028` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026, enmendada el 22-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide cuatro cosas de las que depende que la operación signifique algo: **cómo se distingue un bloqueo manual de uno automático sin añadir columna**, **cómo se corta el acceso de verdad y no en quince minutos**, **cómo se serializa `RN-SP-001` sobre un conjunto y no sobre una fila**, y **dónde vive el motivo**.

---

## 1. Enfoque

Una columna cambia de valor, y como en [`RF-SP-007`](../007-cambiar-estado-rol/plan.md) la dificultad no está ahí. Está en la palabra **inmediato**: `security.md` §4.5 declara que retirar el acceso a un usuario tiene efecto al instante, se revocan todos sus refresh tokens y su token de acceso deja de admitirse aunque siga siendo válido por firma. Es **la única situación** en que el sistema, que por diseño valida la mayoría de peticiones sin consultar nada, tiene que mirar el estado vigente.

Cinco decisiones gobiernan el plan:

1. **El origen del bloqueo no necesita columna nueva.** `BLOQUEADO` con `locked_until` informado es automático y se levanta solo; `BLOQUEADO` con `locked_until` nulo es manual y no caduca. Esa correspondencia es lo que hace verificable `CA-SP-350` y `CA-SP-351` sin ampliar el esquema, y también lo que hace que `FA-003` —bloquear a mano una cuenta ya bloqueada por el sistema— sea un cambio real y no un caso idempotente (§4).
2. **El corte inmediato se resuelve con un registro de invalidación por usuario, no consultando la base de datos en cada petición.** El filtro rechaza todo token de acceso emitido **antes** del instante en que se retiró el acceso. Cuesta una entrada en memoria por cuenta afectada y vive lo que vive un token (§7).
3. **`RN-SP-001` se serializa sobre el conjunto de portadores activos del rol raíz, no sobre la fila del afectado.** `spec.md` §13 lo exige con un caso concreto —dos superadministradores desactivándose a la vez—, y bloquear la fila del usuario no lo impide: son filas distintas. Se bloquean las filas de todos los portadores activos, en orden ascendente de identificador (§7).
4. **El motivo no es un atributo de la cuenta sino de la operación**, y por eso vive en el `detail` del evento de seguridad y no en una columna de `users` (§2, §6).
5. **`RN-SP-017` produce `403` y no `409`.** Es una prohibición sobre **quién** ejecuta —el mismo cuerpo enviado por otro actor sería válido—, exactamente la forma de `RN-SEG-011` en `RF-SP-004` §4, y se resuelve igual: código de la regla, evento de seguridad de severidad **alta** emitido por el caso de uso.

`domain` participa en lo que puede verificarse sin base de datos: la transición de estado y qué significa cada una, la exigencia del motivo, y la regla del último administrador una vez alguien le dice cuántos quedan.

## 2. Cambios de esquema

**Migración:** `V24__create_user_supervisor_index.sql`

Ninguna columna nueva y ninguna restricción nueva. Lo único que falta es un acceso:

| Tabla | Cambio | Detalle |
|---|---|---|
| `user_supervisors` | Altera (índice) | `ix_user_supervisors_supervisor_vigente`, único acceso por superior |

```sql
CREATE INDEX ix_user_supervisors_supervisor_vigente
    ON user_supervisors (supervisor_id)
 WHERE ended_at IS NULL;
```

**Por qué aquí.** `RF-SP-024` §2 lo dejó anotado con precisión: «`ix_user_supervisors_supervisor_id` no se crea aquí. La consulta por superior es de `RF-SP-042`, y `RN-SP-022` la usa desde `RF-SP-028`, `RF-SP-029` y `RF-SP-031`. Ninguno de ellos existe todavía; **el primero que lo necesite lo declara**». Este es el primero en el orden de implementación (§8).

**Por qué parcial y por qué cambia de nombre.** La pregunta que hace `RN-SP-022` es «¿alguien está a cargo de esta persona **hoy**?», y las filas cerradas —historial que `RN-SP-021` conserva para siempre— nunca forman parte de la respuesta. Un índice total las indexaría igual y crecería con el historial sin servir a ninguna consulta; el parcial cubre exactamente el conjunto que se pregunta, y es además el que `RF-SP-042` necesita para listar el equipo. El nombre que `RF-SP-024` anticipó, `ix_user_supervisors_supervisor_id`, describiría un índice sobre una columna; este es sobre una columna **y una condición**, y llamarlo por la columna induciría a error a quien lo lea en el esquema. `requirements/sp.md` §10.8 recoge el nombre definitivo (§8).

**No se añade columna de motivo a `users`.** `spec.md` §14, resolución 3, lo fija: el motivo se guarda en el detalle del evento de seguridad, porque **no es un atributo de la cuenta sino de la operación**. Una columna nulable produciría un campo que casi siempre está vacío, que nadie sabe si puede interpretar, y que además quedaría mintiendo tras la siguiente reactivación —donde el motivo no se admite—. Es el mismo criterio de `RF-SP-007` §2.

**No se añade columna de origen del bloqueo.** La distingue `locked_until`: informado en el automático, nulo en el manual. Añadir un `block_type` sería una segunda fuente de la misma verdad, que puede desincronizarse de la primera.

### Lo que este requerimiento no crea y necesita

| Objeto | Quién lo crea | Para qué |
|---|---|---|
| `failed_attempts`, `locked_until` | **`RF-SP-034`** | Ponerlos a cero y limpiarlos al reactivar; leer `locked_until` para responder hasta cuándo dura un bloqueo |
| `refresh_tokens` y su revocación con motivo | **`RF-SP-034`** y `RF-SP-035` | Revocar todas las sesiones al retirar el acceso, con motivo `ACCESO_RETIRADO` |
| `ck_users_status` | `V18` (`RF-SP-024`) | Los cuatro estados. Este requerimiento **no admite `PENDIENTE`** en su contrato, aunque el esquema lo acepte |
| `user_supervisors` | `V21` (`RF-SP-024`) | Verificar `RN-SP-022` |

**`RF-SP-034` precede a este requerimiento**, y no por comodidad: sin `refresh_tokens` no hay nada que revocar y `CA-SP-232` no es verificable; sin `locked_until` y `failed_attempts` no hay bloqueo que levantar ni contador que poner a cero, y `CA-SP-234` y `CA-SP-351` tampoco lo son. La precedencia se declara en §8 y se enmienda `requirements/sp.md` §6.1.

**Las tres columnas de control de acceso son de `RF-SP-034`**, decisión que fija `RF-SP-026` §2 y que aquí se confirma desde el otro lado: este requerimiento **las limpia y no las crea**. `security.md` §9 y `requirements/sp.md` §10.10 se enmiendan para repartirlas sin ambigüedad.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `User` | **Modificado** | `deactivate(motivo)`, `block(motivo)` y `activate()`. Devuelven **qué cambió** —estado, expiración del bloqueo o ambos— o «sin cambio». Aquí vive `FA-001` y `FA-003` |
| `domain` | `UserStatus` | Nuevo | Enumerado de los cuatro estados. `PENDIENTE` existe y **ninguna transición de este requerimiento lo produce** |
| `domain` | `RootAdministratorPresence` | Nuevo | `RN-SP-001` como regla pura: recibe si el afectado porta el rol raíz y cuántos **otros** portadores activos quedan, y decide. Sin Spring ni base de datos (Art. VI.3) |
| `domain` | `SelfOperationGuard` | Nuevo | `RN-SP-017` como regla pura. **Lo reutilizan `RF-SP-029`, `RF-SP-038` y `RF-SP-041`** |
| `domain` | `StatusChangeReason` | Nuevo | Objeto de valor del motivo: recorta, exige contenido y **no admite construirse vacío** |
| `application` | `ChangeUserStatusService` | Nuevo | Caso de uso. `@Transactional`, orquesta el orden de §4, revoca sesiones y emite las dos auditorías |
| `application` | `ChangeUserStatusCommand` | Nuevo | Entrada del caso de uso: identificador, estado destino y motivo opcional. Sin tipos de HTTP |
| `application` | `RootRoleHolderRepository` | Nuevo | Puerto: bloquea y cuenta los portadores **activos** del rol raíz. **Lo reutilizan `RF-SP-029` y `RF-SP-031`** |
| `application` | `SupervisedTeamCounter` | Nuevo | Puerto: cuántas personas tiene a cargo alguien hoy. **Lo reutilizan `RF-SP-029`, `RF-SP-031` y `RF-SP-042`** |
| `application` | `SessionRevoker` | Nuevo | Puerto: revoca todos los refresh tokens de una persona con un motivo. Lo implementa `RF-SP-034`; **lo reutilizan `RF-SP-029`, `RF-SP-031`, `RF-SP-037`, `RF-SP-038` y `RF-SP-040`** |
| `application` | `AccessRevocationPublisher` | Nuevo | Puerto: declara el instante a partir del cual los tokens de acceso de una persona dejan de admitirse (§7) |
| `application` | `UserChangeAuditor`, `UserSecurityAuditor` | Sin cambios | Puertos de `RF-SP-024` |
| `infrastructure` | `JpaUserRepository` | **Modificado** | Carga bloqueada de la persona; consultas de portadores del rol raíz y de equipo a cargo |
| `shared/security` | `AccessRevocationRegistry` | **Nuevo** | Registro en memoria de cortes por usuario, consultado por el filtro de autenticación (§7) |
| `api` | `UserController` | **Modificado** | Añade `PATCH /api/v1/users/{id}/status` |
| `api` | `ChangeUserStatusRequest` | Nuevo | DTO con el estado destino y el motivo condicional |
| `api` | `UserStatusResponse` | Nuevo | DTO de salida: identidad mínima, estado y expiración del bloqueo |

Cuatro decisiones de reparto:

**Se devuelve `UserStatusResponse` y no `UserResponse`.** Es la diferencia con `RF-SP-007`, que devolvió el `RoleResponse` completo. `UserResponse` de `RF-SP-024` lleva la lista de roles, y esta operación **no los toca**: devolverlos costaría una segunda sentencia para un dato que la petición no cambió, y además no lleva `lockedUntil`, que aquí es la mitad de la respuesta (`CA-SP-351`). Un DTO propio y pequeño evita las dos cosas. La comprobación de que roles y membresía siguen intactos (`CA-SP-236`) se hace contra `RF-SP-026`, no contra este cuerpo.

**Tres puertos nacen aquí y los heredan cinco requerimientos.** `RootRoleHolderRepository`, `SupervisedTeamCounter` y `SessionRevoker` resuelven preguntas que `RF-SP-029` y `RF-SP-031` repiten literalmente. Declararlos aquí —el primero en el orden— es lo que impide que cada uno escriba su propia consulta de «¿cuántos superadministradores activos quedan?», que es exactamente el tipo de duplicación de la que salen las divergencias silenciosas.

**`SelfOperationGuard` es un componente de dominio, no un `if` en el servicio.** `RN-SP-017` alcanza a cuatro requerimientos (`spec.md` de `RF-SP-041` la amplió el 22-08-2026) y es una regla de negocio: tiene que poder probarse sin Spring (Art. VI.3), y tiene que ser la misma en los cuatro.

**El registro de invalidación vive en `shared/security` y se consume por un puerto.** El caso de uso declara «desde este instante, los tokens de esta persona no valen» y no sabe cómo se materializa. Es lo que permite cambiar el mecanismo —de memoria a un canal compartido entre instancias— sin tocar `application`, que es justamente la corrección que §10 anticipa.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `PATCH` | `/api/v1/users/{id}/status` | Retira o devuelve el acceso de una persona |

Subrecurso propio y no un campo dentro de `PATCH /users/{id}`, por lo dicho en `RF-SP-007` §4 y con un motivo más: el estado tiene reglas de rechazo que la edición no tiene —`RN-SP-001`, `RN-SP-017`, `RN-SP-022`— y exige un motivo que la edición no admite. Juntarlos obligaría a un solo endpoint a aplicar dos conjuntos de reglas según qué campos llegaran.

**Petición**

```json
{ "status": "INACTIVO", "reason": "Baja voluntaria, último día 31-08-2026." }
```

| Campo | Obligatorio | Notas |
|---|---|---|
| `status` | Sí | `ACTIVO`, `INACTIVO` o `BLOQUEADO`. **`PENDIENTE` no se admite** |
| `reason` | **Condicional** | Obligatorio con `INACTIVO` y `BLOQUEADO`; **rechazado** con `ACTIVO` |

- **Se envía el estado destino y no una acción**, por el motivo de `RF-SP-007` §4: hace la operación idempotente por construcción, que es lo que `FA-001` describe.
- **`PENDIENTE` se rechaza con `400`**, aunque `ck_users_status` lo acepte. `spec.md` §4.2 lo deja fuera del dominio admitido: ningún requerimiento lo produce (`RF-SP-024`, resolución 1) y admitirlo aquí sería abrir el único camino hacia un estado del que nadie sabe salir.
- **El motivo condicional en los dos sentidos.** Ausente al retirar el acceso es `400` con `VAL-005` (`EX-001`); presente al devolverlo es `400` con `VAL-006` (`EX-004`). Aceptarlo en silencio al reactivar dejaría un texto que nadie sabría si interpretar como justificación de la reactivación o como resto de una petición anterior.
- **El cuerpo rechaza propiedades desconocidas.** Enviar `lockedUntil` devuelve `400`: el momento de expiración del bloqueo **lo calcula el sistema** (`spec.md` §4.2) y el manual no lo tiene.

**Respuesta `200`**

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d40",
  "username": "jperez",
  "status": "BLOQUEADO",
  "lockedUntil": null,
  "updatedAt": "2026-08-22T17:03:55Z"
}
```

- **`lockedUntil` nulo con `status = BLOQUEADO` significa bloqueo manual**, y es la mitad de `CA-SP-351`. La otra mitad es que un bloqueo por intentos fallidos —que este endpoint no produce pero sí puede encontrarse— lo devuelva informado.
- **`FA-001` devuelve `200` igual**, con la persona sin cambios y **sin dejar evento** en ningún registro (`CA-SP-239`).
- **No se devuelven los roles ni la membresía**, y `CA-SP-236` no se verifica aquí sino contra `RF-SP-026`.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | Estado ausente, fuera del dominio o `PENDIENTE` (`VAL-001`) | `VAL-001` | `status` |
| `400` | Motivo ausente o vacío tras recortar, al retirar el acceso (`EX-001`, `VAL-005`) | `VAL-005` | `reason` |
| `400` | Motivo enviado al reactivar (`EX-004`, `VAL-006`) | `VAL-006` | `reason` |
| `400` | Cuerpo con campo desconocido | `VAL-001` | El campo sobrante |
| `400` | El identificador no es un UUID en forma canónica | `VAL-001` | `id` |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `users:update` | `AUTH-002` | — |
| `403` | El identificador es la cuenta del actor (`EX-002`) | `RN-SP-017` | — |
| `404` | No existe usuario vigente con ese identificador (`EX-005`) | `EX-005` | — |
| `409` | Es el último portador **activo** del rol raíz (`EX-003`) | `RN-SP-001` | — |
| `409` | Tiene personas a su cargo (`EX-006`) | `RN-SP-022` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **Los dos `403` son distintos y no deben fusionarse.** El primero lo produce la capa de seguridad antes de entrar al caso de uso; el segundo, el caso de uso con el identificador ya comparado. Comparten estado HTTP y no `error_code`, exactamente como en `RF-SP-004` §4.
- **`RN-SP-017` es `403` y no `409`** porque es una prohibición sobre quién ejecuta: el mismo cuerpo enviado por otro actor sería válido. Es la forma de `RN-SEG-011`, y mantener la misma correspondencia permite a un cliente tratar los dos casos igual.
- **`RN-SP-001` y `RN-SP-022` son `409`**: conflictos con el estado actual del sistema sobre datos que existen, que es la definición de `409` en `architecture.md` §7.2.
- **El cuerpo del `409` de `RN-SP-022` dice cuántas personas tiene a cargo y no quiénes** (`CA-SP-410`). Quiénes son se consulta con `RF-SP-042`, que tiene su propio permiso; decirlo aquí devolvería datos de terceros a quien solo pidió cambiar un estado.
- **El cuerpo del `409` de `RN-SP-001` explica la consecuencia**, no solo niega: el sistema quedaría sin ninguna vía de administración. Sin esa frase, quien lo recibe busca cuál es el error de su petición.
- El `403` de `AUTH-002` y su evento los produce la capa de seguridad (§6). `CA-SP-237` se verifica sobre el otro `403`.

**Orden de verificación.** Determina qué error recibe una petición que incumple varias cosas a la vez:

1. **Formato**: estado presente y dentro del dominio, motivo coherente con el estado destino, cuerpo sin campos desconocidos. Todo junto (`VAL-001`, `VAL-005`, `VAL-006`).
2. **Persona existente y no eliminada** (`EX-005`), cargada **con bloqueo de fila**.
3. **La persona no es el actor** (`EX-002`, `RN-SP-017`).
4. **Si el estado destino retira el acceso**: que no sea el último portador activo del rol raíz (`EX-003`), **con el conjunto bloqueado** (§7).
5. **Si el estado destino retira el acceso**: que no tenga personas a cargo (`EX-006`).
6. Aplicación del estado en el dominio, que decide si hubo cambio.
7. Revocación de sesiones, escritura y auditoría.

**El motivo se valida el primero de todos** —antes incluso de saber si la persona existe— porque es formato, y porque `spec.md` §10 exige rechazar «antes de ejecutarla». **Los pasos 4 y 5 solo se evalúan al retirar el acceso**: devolverlo no puede dejar a nadie sin administración ni a ningún equipo huérfano, y evaluarlos igualmente costaría dos consultas por reactivación.

### Qué hace exactamente cada transición

| Desde | Hacia | Qué ocurre |
|---|---|---|
| `ACTIVO` | `INACTIVO` | Estado, revocación de sesiones y corte de tokens de acceso. `locked_until` y `failed_attempts` no se tocan |
| `ACTIVO` | `BLOQUEADO` | Ídem, y `locked_until` queda **nulo**: bloqueo manual, sin expiración |
| `BLOQUEADO` automático | `BLOQUEADO` manual | **`FA-003`**: `locked_until` pasa de informado a nulo. Hay cambio y hay evento, aunque el estado sea el mismo |
| `BLOQUEADO` manual | `BLOQUEADO` manual | `FA-001`: sin cambio, sin evento |
| `BLOQUEADO` (cualquiera) | `ACTIVO` | **`FA-002`**: `locked_until` a nulo, `failed_attempts` a cero, credencial **intacta**. No se exige motivo |
| `INACTIVO` | `ACTIVO` | Estado. `failed_attempts` a cero por uniformidad: una cuenta que vuelve empieza con el contador limpio |
| Cualquiera | El mismo, sin más diferencias | `FA-001`: sin cambio, sin evento |

**`FA-003` es el único caso en que pedir el estado que ya se tiene no es idempotente**, y el dominio lo decide mirando `locked_until`, no un campo aparte. Es lo que hace que la distinción entre los dos bloqueos sea observable sin ampliar el esquema.

**Reactivar nunca falla por regla.** `RN-SP-001`, `RN-SP-022` y el motivo solo alcanzan a retirar el acceso. Es la asimetría que `spec.md` §10 declara en `EX-006` y que `CA-SP-411` verifica desde el otro lado.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `PATCH /api/v1/users/{id}/status` | `users:update` |

- El permiso **ya existe**: lo siembra `V3__seed_permissions.sql` (`RF-SP-010`).
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **Levantar un bloqueo exige el mismo permiso que desactivar una cuenta**, y `spec.md` §14, resolución 1, declaró el coste: **no se puede dar a soporte técnico la facultad de liberar bloqueos sin darle además la de retirar accesos**. Este plan no lo corrige por su cuenta; si esa separación hace falta, será un permiso nuevo y un requerimiento nuevo.
- **No hay techo de privilegios que verificar.** Cambiar un estado no concede permisos, de modo que `RN-SEG-010` no interviene y la resolución del permiso **sí** puede usar la caché de `security.md` §4.5.
- **`RN-SP-017` se verifica contra el identificador del actor autenticado**, que el token transporta en `sub` y que no puede haber cambiado durante la sesión. **A diferencia de `RN-SEG-011`, aquí no hace falta consultar la base de datos**: aquella regla comparaba los *roles* del actor, que sí cambian mientras el token vive; esta compara su identidad, que no.
- **No hay filtrado por alcance de datos.** Se revisa con **D-22**.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Cambio efectivo | `audit_change_log` | `module = 'SP'`, `entity = 'users'`, `entity_id`, `action = 'UPDATE'`, `changes` con `status` y —cuando cambió— `locked_until`, cada uno con su antes y su después |
| Cambio efectivo | `audit_security_log` | `event_type = 'USER_STATUS_CHANGED'`, `severity = 'ALTA'`, `outcome = 'SUCCESS'`, `target_user_id` de la persona afectada, `detail` con el estado anterior, el nuevo, **el motivo cuando lo hubo** y si el bloqueo resultante es manual |
| Cambio sin efecto (`FA-001`) | — | **Ningún evento**, en ningún registro |
| Rechazo `409` por `EX-003` o `EX-006` | `audit_error_log` | `resource = 'users'`, `operation = 'PATCH /api/v1/users/{id}/status'`, `error_code = 'RN-SP-001'` o `'RN-SP-022'`, `error_type = 'BUSINESS_RULE'`, `http_status = 409`, `severity = 'MEDIA'`, `message` saneado |
| Rechazo `403` por `EX-002` (`RN-SP-017`) | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, severidad **`ALTA`**, `outcome = 'FAILURE'`, `target_user_id` del propio actor. **No** va a `audit_error_log` |
| Rechazo `404` por `EX-005` | — | **No se audita**: `architecture.md` §6.6.4 lo deja fuera y `ck_audit_error_log_status` lo impide en el esquema |
| Rechazo `400` de formato, incluido el motivo ausente | — | **No se audita** (`architecture.md` §6.6.4): es validación, no regla incumplida |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_deletion_log` | No aplica: la operación no elimina nada |

Cinco decisiones:

**Un solo evento de seguridad, `USER_STATUS_CHANGED`, también para el bloqueo manual.** `security.md` §8.1 lista «bloqueo manual de una cuenta» como fila aparte desde `RF-SP-028`, y `RF-SP-014` §2 ya resolvió que **no gana código propio**: es un cambio de estado, con la misma severidad y el mismo `outcome`, y qué clase de bloqueo fue vive en `detail`. Este plan lo confirma desde el lado del emisor y no amplía el catálogo cerrado.

**La severidad del cambio efectivo es `ALTA`**, también al reactivar. Retirar el acceso es evidente; devolverlo lo es igual de mucho, porque es la operación con la que alguien recupera la capacidad de operar y es la que hay que revisar cuando se pregunta cómo entró alguien donde no debía. Es el mismo criterio con el que `RF-SP-007` §6 puso `ALTA` al cambio de estado de un rol.

**El `AUTHORIZATION_DENIED` de `RN-SP-017` va con severidad `ALTA` y lo emite el caso de uso**, no la capa de seguridad, porque la regla no puede verificarse antes de leer a quién apunta el identificador. Es exactamente el segundo emisor que `RF-SP-014` §2 ya contempla para `RF-SP-004` a `RF-SP-009`, y que el esquema admite porque **no liga `event_type` con `severity`**. Impacto declarado en §8.

**`changes` lleva `status` y `locked_until`, y no lleva `failed_attempts` ni `updated_at`.** Los dos primeros son lo que alguien decidió cambiar; el contador puesto a cero y la marca de modificación son **consecuencias** de la escritura. Es el criterio de `RF-SP-022` §6, y aquí tiene un efecto útil: en `FA-003` el diff contiene **solo** `locked_until`, y eso basta para leer qué ocurrió.

**El motivo vive en `detail` y no en `changes`.** No es un dato de la fila —`users` no tiene columna de motivo— y ponerlo en el diff simularía un cambio de un campo inexistente. `CA-SP-354` se verifica sobre el evento de seguridad, que es donde `spec.md` §14, resolución 3, lo situó.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Bloqueo de la fila, bloqueo del conjunto de portadores del rol raíz, `UPDATE` y su evento en `audit_change_log` | **La misma** (Art. V.14) |
| Revocación de los refresh tokens | **La misma.** Ver abajo |
| Publicación del corte de tokens de acceso | **Tras el commit**, nunca antes |
| `audit_security_log` del cambio | **Independiente**, `REQUIRES_NEW`, **enganchada al commit** |
| `audit_security_log` de `RN-SP-017` | **Independiente**, `REQUIRES_NEW`, emitida **sin esperar al commit**: se escribe mientras la transacción se revierte |
| `audit_error_log` de un rechazo o un fallo | **Independiente**, `REQUIRES_NEW` |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`@Transactional` vive sobre `ChangeUserStatusService`, en `application`.

**La revocación de sesiones va dentro de la transacción de negocio, y es deliberado.** `CA-SP-232` exige que los refresh tokens queden revocados «en el mismo acto»: si la revocación fuera posterior al commit y fallara, quedaría una cuenta inactiva con sesiones vivas y nada que lo delatara. Al revés no ocurre nada malo: si falla la revocación, se revierte también el cambio de estado y la operación se reintenta. **La regla es que lo que no puede quedar a medias va junto.**

**La publicación del corte de tokens de acceso va después del commit**, y por el motivo contrario: es un efecto sobre memoria que no participa de la transacción, y publicarlo antes dejaría tokens rechazados por un cambio que pudo revertirse. Es la misma asimetría de `RF-SP-007` §7 con la invalidación de la caché de permisos.

### Cómo se serializa `RN-SP-001`

`spec.md` §13 lo plantea sin rodeos: dos superadministradores desactivándose mutuamente a la vez pasarían ambos la comprobación leyendo el estado anterior, y el sistema quedaría sin ninguno. **Bloquear la fila del afectado no lo impide**: son dos filas distintas y ninguna transacción espera a la otra.

Se resuelve bloqueando **el conjunto**:

```sql
SELECT u.id
  FROM users u
  JOIN user_roles ur ON ur.user_id = u.id
 WHERE ur.role_id = :rolRaiz
   AND u.status = 'ACTIVO'
   AND u.deleted_at IS NULL
 ORDER BY u.id
   FOR UPDATE OF u;
```

Cuatro propiedades de esa sentencia:

1. **Solo se ejecuta cuando el afectado porta el rol raíz y el estado destino le retira el acceso.** Para el resto de las personas —la inmensa mayoría— la operación no la ejecuta, y `RN-SP-001` no cuesta nada.
2. **Bloquea todas las filas del conjunto, no la del afectado.** Dos transacciones que intenten desactivar a dos portadores distintos compiten por las mismas filas: una espera, y cuando entra ve el estado que dejó la primera. Es lo que convierte «cada una lee el estado anterior» en imposible.
3. **El orden ascendente por identificador evita el interbloqueo**, por el mismo motivo que `RF-SP-024` §7 ordena la lectura de roles: sin orden, dos transacciones pueden tomar las mismas filas en distinta secuencia y bloquearse mutuamente.
4. **El conjunto es diminuto.** Los portadores activos del rol raíz se cuentan con los dedos, de modo que bloquearlos todos no serializa nada que importe.

Se descarta un bloqueo consultivo (`pg_advisory_xact_lock`) sobre una clave derivada del rol raíz: funcionaría igual y serializaría **todas** las operaciones sobre superadministradores, incluidas las que no compiten entre sí, además de introducir un mecanismo de bloqueo que ningún otro requerimiento del módulo usa.

### Cómo se corta el token de acceso

El token de acceso es un JWT firmado que se valida sin consultar la base de datos, y `security.md` §4.5 exige que deje de admitirse **de inmediato**. Se resuelve con un registro en memoria de `shared/security`:

- El caso de uso publica, tras el commit, «para esta persona, todo token emitido antes de este instante deja de valer».
- El filtro de autenticación compara el `iat` del token con ese corte y responde `401` si es anterior.
- Cada entrada **caduca sola** al cabo de la vida del token de acceso —quince minutos—, porque a partir de ahí ningún token afectado sigue siendo válido por firma. El registro no crece.
- **Al arrancar, el registro se siembra** con las cuentas cuyo estado dejó de ser `ACTIVO` dentro de esa ventana. Sin esa siembra, un reinicio devolvería la validez a los tokens que se acababan de cortar, y sería un agujero que ninguna prueba funcional detecta.

Se descarta consultar el estado del usuario en cada petición: convierte el diseño sin estado en una consulta por petición sobre el camino más caliente del sistema, para atender un caso que ocurre pocas veces al día. Y se descarta una lista negra por `jti`, que exige recordar tokens individuales sin ganar nada: el corte es siempre por persona.

**Con más de una instancia del backend, el registro solo corta en la que atendió la petición.** Es el mismo riesgo que `RF-SP-007` §10 aceptó para la caché de permisos, con la misma corrección disponible —un canal compartido detrás del mismo puerto— y la misma consecuencia mientras tanto: hasta quince minutos de tolerancia en las demás instancias. Los **refresh tokens sí quedan revocados en la base de datos**, de modo que la sesión no puede prolongarse en ninguna instancia (§10).

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| **`RF-SP-034`** | **Precedencia declarada:** debe implementarse antes. Crea `refresh_tokens`, `failed_attempts`, `locked_until` y `last_login_at`, e implementa `SessionRevoker`. Además **hereda una obligación**: el bloqueo **automático** por intentos fallidos se aplica aunque la persona tenga equipo a cargo (`CA-SP-411`), porque `RN-SP-022` alcanza solo al cambio de estado **por decisión de un actor** |
| **`RF-SP-029` y `RF-SP-031`** | Heredan `RootRoleHolderRepository`, `SupervisedTeamCounter`, `SessionRevoker`, `SelfOperationGuard` y la serialización de `RN-SP-001` de §7. **Ninguno debe escribir su propia consulta de portadores del rol raíz**: es de donde salen las divergencias silenciosas |
| **`RF-SP-042`** | Hereda `ix_user_supervisors_supervisor_vigente` y `SupervisedTeamCounter`. Su `CA-SP-447` exige que el total que devuelve **coincida** con el número que este requerimiento informa al rechazar |
| **`RF-SP-041`** | Es la salida del `409` de `RN-SP-022`: el equipo se reasigna **persona a persona** antes de poder retirar el acceso. Y hereda `SelfOperationGuard` |
| **`RF-SP-014`** | Su §2 atribuye `AUTHORIZATION_DENIED` a la capa de seguridad y a los casos de uso de `RF-SP-004` a `RF-SP-009`. Desde este plan, **también a `RF-SP-028`, `RF-SP-029`, `RF-SP-038` y `RF-SP-041`**, por `RN-SP-017` y con severidad `ALTA`. No cambia el esquema ni el literal: le falta una fila en la columna de emisores, y esa compuerta se tramita aparte |
| `RF-SP-026` | Devuelve `lockedUntil` y es donde se comprueba que roles y membresía sobrevivieron al cambio (`CA-SP-236`) |
| `RF-SP-025` | Su filtro por estado es lo que hace útil la distinción entre `INACTIVO` y `BLOQUEADO` (`security.md` §3.1) |
| `RF-SP-035` | Su `EX-003` rechaza el refresco de una persona inactiva, bloqueada o eliminada. Este requerimiento le garantiza que, además, **el token ya estaba revocado** con motivo `ACCESO_RETIRADO`, de modo que cae en su `EX-004` y **no** en la reutilización sospechosa de `EX-001` |
| `RF-SP-038` | Restablecer la contraseña **no** levanta un bloqueo (`CA-SP-394`). La única operación que lo levanta es esta |
| `shared/security` | Gana `AccessRevocationRegistry`, consultado por el filtro de autenticación en **cada petición**. Es el componente de este plan con mayor alcance fuera de él |
| `requirements/sp.md` | **§6.1 gana las precedencias del bloque de usuarios**; **§10.8 recoge `ix_user_supervisors_supervisor_vigente`** con su predicado, en lugar del nombre que `RF-SP-024` anticipó; **§10.10 reparte** las tres columnas de control de acceso a `RF-SP-034`. Enmiendas de este plan (Art. I.7) |
| `security.md` | **§9 recoge el mismo reparto de columnas.** §8.1 **no cambia**: el bloqueo manual ya está en el catálogo y no gana código propio (§6) |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Añadir una columna `block_type` o `blocked_manually` | Segunda fuente de una verdad que `locked_until` ya expresa: informado es automático, nulo es manual. Dos fuentes se desincronizan, y la que quede atrás decidirá mal si un bloqueo caduca |
| Añadir una columna de motivo a `users` | El motivo es de la operación, no de la cuenta (`spec.md` §14, resolución 3). La columna quedaría mintiendo tras la siguiente reactivación, donde el motivo no se admite |
| Enviar una acción (`activate` / `deactivate` / `block`) en lugar del estado destino | Obliga al servidor a conocer el estado actual para saber si la acción aplica, y hace que repetir la petición no sea neutro. El estado destino es idempotente por construcción |
| Separar el desbloqueo en su propio endpoint y su propio permiso | `spec.md` §14, resolución 1, lo resolvió: bloqueado y activo son valores del mismo campo, y separarlos daría dos endpoints escribiendo la misma columna con reglas que habría que mantener sincronizadas. El coste —soporte no puede liberar bloqueos sin poder desactivar— queda declarado |
| Admitir `PENDIENTE` como estado destino | Abriría el único camino hacia un estado del que ningún requerimiento sabe salir (`RF-SP-024`, resolución 1) |
| Aceptar el motivo al reactivar e ignorarlo | Dejaría un texto que nadie sabe si es justificación de la reactivación o resto de una petición anterior. `EX-004` lo rechaza |
| Consultar el estado del usuario en la base de datos en **cada** petición | Convierte el diseño sin estado en una consulta por petición sobre el camino más caliente, para atender un caso que ocurre pocas veces al día |
| Lista negra de tokens por `jti` | Obliga a recordar tokens individuales y no gana nada: el corte es siempre por persona, y una entrada por persona caduca sola |
| Bloquear la fila del usuario afectado para `RN-SP-001` | No sirve: dos desactivaciones de personas distintas tocan filas distintas y ninguna espera a la otra. El caso de `spec.md` §13 quedaría abierto |
| Bloqueo consultivo sobre el rol raíz | Serializaría **todas** las operaciones sobre superadministradores, incluidas las que no compiten, e introduciría un mecanismo que ningún otro requerimiento del módulo usa |
| Contar los portadores activos sin bloquear y confiar en una restricción | `RN-SP-001` no es expresable como restricción declarativa: depende de dos tablas, y PostgreSQL no admite subconsultas en `CHECK` (`requirements/sp.md` §10.8) |
| Reasignar automáticamente el equipo al superior del superior | `RN-SP-022` lo prohíbe explícitamente: la estructura determinará atribución de negocio y desplazarla en silencio cambiaría a quién pertenece un resultado. La salida es `RF-SP-041`, persona a persona |
| Listar en el `409` quiénes son las personas a cargo | Devolvería datos de terceros a quien solo pidió cambiar un estado. `RF-SP-042` los devuelve, con su permiso y su paginación |
| Revocar las sesiones **después** del commit | Si esa revocación fallara, quedaría una cuenta inactiva con sesiones vivas y nada que lo delatara. Lo que no puede quedar a medias va dentro de la transacción |
| Publicar el corte de tokens **antes** del commit | Rechazaría tokens por un cambio que pudo revertirse. Misma asimetría que `RF-SP-007` §7 |
| Levantar el bloqueo al restablecer la contraseña | `RF-SP-038` §14, resolución 4, lo descartó: una operación sobre la credencial no debe deshacer en silencio una decisión de seguridad |
| Devolver `UserResponse` completo | Costaría una segunda sentencia para traer roles que esta operación no toca, y no lleva `lockedUntil`, que aquí es la mitad de la respuesta |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| **Con más de una instancia, el token de acceso sigue admitiéndose en las instancias que no atendieron la petición** | **Alto** | Registrado y **acotado**: los refresh tokens quedan revocados en la base de datos, de modo que la sesión **no puede prolongarse** en ninguna instancia y la ventana es como mucho la vida del token de acceso —quince minutos—. Es el mismo riesgo que `RF-SP-007` §10 aceptó, con la misma corrección disponible: un canal compartido detrás de `AccessRevocationPublisher`, **sin tocar el caso de uso**. Debe resolverse **antes** de desplegar una segunda instancia |
| Un reinicio del proceso devuelve la validez a los tokens recién cortados | Medio | La siembra del registro al arrancar lo cierra (§7). Sin ella, el agujero **no lo detecta ninguna prueba funcional**: solo aparece reiniciando entre el corte y la expiración |
| `RN-SP-001` se implementa bloqueando la fila del afectado y el sistema puede quedarse sin superadministradores | **Crítico** | Es el defecto que `spec.md` §13 anticipa. Prueba concurrente obligatoria en §11, con dos transacciones reales sobre dos portadores distintos. Sin ella, el caso solo aparece en producción y es irreversible |
| Interbloqueo entre dos operaciones que bloquean el mismo conjunto | Medio | Orden ascendente por identificador (§7). Sin él, el interbloqueo depende de que PostgreSQL lo detecte matando una transacción, y el actor recibiría un `500` esporádico e irreproducible |
| `FA-003` se implementa como caso idempotente y el bloqueo manual sigue caducando | **Alto** | Es el defecto más silencioso del requerimiento: la cuenta se desbloquearía sola pese a haber sido bloqueada a mano. `CA-SP-350` lo verifica **esperando a que pase el momento de expiración anterior** |
| La revocación de sesiones se implementa fuera de la transacción | **Alto** | `CA-SP-232` lo verifica; §7 lo declara. Una cuenta inactiva con sesiones vivas no falla, simplemente sigue funcionando |
| El bloqueo automático de `RF-SP-034` copia la verificación de `RN-SP-022` y deja de bloquear a quien tiene equipo | Medio | Declarado en §8 y verificado por `CA-SP-411`. La defensa de seguridad no puede quedar supeditada a que alguien reorganice un equipo primero |
| Se emiten dos eventos de seguridad al bloquear a mano —uno de cambio de estado y otro de bloqueo | Bajo | `RF-SP-014` §2 ya lo resolvió: un solo código, y la clase de bloqueo en `detail`. Prueba de conteo de filas en §11 |
| Quien tiene `users:update` puede desactivar cualquier cuenta y además cambiar correos (`RF-SP-027`) | Medio | Aceptado en `spec.md` §14, resolución 1. Acotado por la auditoría: todo cambio de estado deja evento de severidad alta con el motivo |
| Una petición en curso se completa después de la desactivación | Bajo | Declarado en `spec.md` §13: la garantía es que **la siguiente** se rechaza. Cortar una petición ya autorizada exigiría verificar el estado a mitad de la transacción |

## 11. Estrategia de prueba

Niveles: **Unitaria** (dominio, sin Spring ni base de datos), **Integración** (Testcontainers sobre PostgreSQL real, con `V18` a `V24` aplicadas y el esquema de `RF-SP-034`) y **API** (extremo a extremo por HTTP, con autenticación).

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-230` | Unitaria + Integración + API | Desactivar y reactivar deja la columna en el valor esperado, y ambas devuelven `200` |
| `CA-SP-231` | Integración | Tras desactivar, el inicio de sesión de esa persona se rechaza |
| `CA-SP-232` | Integración | Tras desactivar **y** tras bloquear, **todos** sus refresh tokens quedan revocados con motivo `ACCESO_RETIRADO`, en la misma transacción: forzando el fallo de la revocación, el estado **tampoco** cambia |
| `CA-SP-233` | Integración + API | Un token de acceso emitido antes del cambio recibe `401` en la petición siguiente, **sin esperar a que expire** |
| `CA-SP-350` | Integración | Un bloqueo manual deja `locked_until` nulo y la cuenta **sigue bloqueada** pasado el tiempo que habría durado un bloqueo automático |
| `CA-SP-351` | Integración + API | Un bloqueo por intentos fallidos devuelve `lockedUntil` informado; uno manual, nulo |
| `CA-SP-234` | Integración + API | Activar una cuenta bloqueada —manual o automática— la deja `ACTIVO`, con `locked_until` nulo y `failed_attempts` en cero |
| `CA-SP-235` | Integración | Tras levantar el bloqueo, la persona entra con **la misma contraseña**: `password_hash` es idéntico antes y después |
| `CA-SP-236` | Integración | Tras el cambio de estado, sus filas de `user_roles` y `user_memberships` son **idénticas**. Se comprueba además sobre `RF-SP-026` |
| `CA-SP-352` | API | Desactivar o bloquear sin motivo, o con un motivo en blanco, devuelve `400` **sin haber tocado la fila** |
| `CA-SP-353` | API | Un motivo enviado al reactivar devuelve `400` con `VAL-006`, **no se ignora** |
| `CA-SP-354` | Integración | El evento de `audit_security_log` conserva el motivo en su `detail`, y `users` **no tiene** columna de motivo |
| `CA-SP-410` | Integración + API | Con una persona a cargo, desactivar y bloquear devuelven `409` con `RN-SP-022` e informan **cuántas**; el cuerpo **no las nombra** |
| `CA-SP-411` | Integración | El bloqueo **automático** de `RF-SP-034` se aplica sobre alguien con equipo a cargo; y **reactivar** a esa misma persona nunca se rechaza por `RN-SP-022` |
| `CA-SP-237` | API | El actor recibe `403` con `RN-SP-017` sobre su propia cuenta, y queda el evento de denegación con severidad **alta** |
| `CA-SP-238` | Integración + API | Con un superadministrador activo y otros dos **inactivos** que portan el rol, retirarle el acceso devuelve `409` con `RN-SP-001` |
| `CA-SP-239` | Integración | Repetir el mismo estado no genera fila en ninguno de los dos registros, y `updated_at` no cambia |
| `CA-SP-240` | Integración | Una fila en `audit_change_log` y **una sola** en `audit_security_log`, esta con `target_user_id` de la persona afectada |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| **Dos desactivaciones concurrentes de dos superadministradores distintos** | **Integración concurrente** | Con dos transacciones reales: una `200` y una `409` con `RN-SP-001`, y **queda al menos un portador activo**. Es la prueba que distingue bloquear el conjunto de bloquear la fila del afectado |
| **`FA-003`** | Integración | Sobre una cuenta bloqueada automáticamente, un bloqueo manual deja `locked_until` nulo, **sí** produce evento, y el diff contiene **solo** `locked_until` |
| Bloqueo manual repetido | Integración | El segundo cae en `FA-001`: sin cambio y sin evento |
| Sesión en varios dispositivos | Integración | Todas caen a la vez: la revocación es **por usuario**, no por sesión |
| Bloqueo que expira mientras se libera | Integración | El resultado es `ACTIVO` y la operación **no falla por haber llegado tarde** |
| Bloqueo manual seguido de desactivación | Integración | Se admite, y la auditoría conserva **ambos** eventos con sus motivos |
| Reactivar a alguien con equipo a cargo | API | `200`. `RN-SP-022` no alcanza a devolver el acceso |
| Reactivar al último superadministrador inactivo | API | `200`. `RN-SP-001` no alcanza a devolver el acceso |
| Reinicio del proceso entre el corte y la expiración | Integración | Tras rearrancar, el token cortado **sigue rechazándose**: es la prueba de la siembra del registro (§7) |
| Persona eliminada | API | `404` con `EX-005`, indistinguible de un identificador inexistente |
| `PENDIENTE` como estado destino | API | `400` con `VAL-001`, aunque `ck_users_status` lo acepte |
| Cuerpo con `lockedUntil` | API | `400` por campo desconocido: el momento de expiración lo calcula el sistema |
| Identificador no canónico | API | `1-1-1-1-1` devuelve `400` con `VAL-001` y campo `id`, no `404` |
| Número de sentencias por petición | Integración | Sobre alguien que **no** porta el rol raíz y **no** retira el acceso, la operación **no** ejecuta ni la consulta de portadores ni la de equipo |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento; en particular que `application` no importe `shared/security` salvo por sus puertos, que es lo que mantiene el registro de invalidación fuera del caso de uso.
