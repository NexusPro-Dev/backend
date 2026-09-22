# PLAN — `RF-SP-034` Iniciar sesión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-034` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 24-08-2026 |
| Enmendado el | 25-08-2026 — ver §12 |

---

## 1. Enfoque

Es el único requerimiento **público** del módulo y el que sostiene a los otros cuarenta y uno: hasta que exista, `SecurityConfig` responde `401` a todo lo que no sea la salud del sistema, y ninguna prueba de API de ningún otro requerimiento puede ejercitar su camino feliz.

Su dificultad no está en verificar una contraseña. Está en **no decir nada por accidente**. `security.md` §3.2 y §5.5 exigen que la respuesta ante credenciales inválidas no revele si la cuenta existe, y eso alcanza a tres canales a la vez: el mensaje, el **código de estado** y el **tiempo de respuesta**. El tercero es el que se pierde sin darse cuenta — una comprobación que termina antes cuando el usuario no existe filtra exactamente la misma información que el mensaje, solo que más despacio. De ahí que §4 fije un orden de ejecución que **no cortocircuita** y que la verificación Argon2id se ejecute incluso contra una cuenta inexistente.

La única excepción consciente es la **cuenta bloqueada**, que sí se identifica como tal (`EX-002`), y ahí el riesgo se invierte: hay que rechazar **sin comprobar la contraseña**, porque comprobarla permitiría distinguir por tiempo una correcta de una incorrecta sobre una cuenta que el mensaje ya delató.

Lo demás es construcción: dos credenciales de propósito distinto (`security.md` §5.2), el contador de fallos con bloqueo creciente y con techo, y tres eventos de auditoría que son la materia prima con la que después se reconoce un ataque de fuerza bruta.

## 2. Cambios de esquema

**Dos migraciones.** Este requerimiento es quien crea todo lo que la autenticación necesita y nadie más escribe (`security.md` §9).

**`V26__add_user_access_control_columns.sql`**

| Tabla | Cambio | Detalle |
|---|---|---|
| `users` | Altera | `failed_attempts smallint NOT NULL DEFAULT 0`, `locked_until timestamptz NULL`, `last_login_at timestamptz NULL` |

Las tres las crea este requerimiento y **ninguno más**, según el reparto que `security.md` §9 cerró el 22-08-2026: `RF-SP-028` únicamente **lee** `locked_until` y **limpia** las dos primeras al reactivar una cuenta. Nacen aquí y no en `V18` por el criterio de `requirements/sp.md` §10.10 — una columna disponible antes de que exista la regla que la gobierna se acaba usando por un camino que nadie diseñó.

`locked_until` nulo significa **no bloqueada**, y no hace falta una columna de «origen del bloqueo»: el bloqueo manual de `RF-SP-028` se distingue porque pone `status = 'BLOQUEADO'` sin `locked_until`, y el automático porque pone `locked_until` sin tocar `status`. `CA-SP-378` exige distinguirlos en el mensaje y esta forma basta.

**`V27__create_refresh_tokens.sql`**

| Tabla | Cambio | Detalle |
|---|---|---|
| `refresh_tokens` | Crea | `id`, `user_id`, `token_hash`, `family_id`, `family_started_at`, `expires_at`, `revoked_at`, `revoked_reason`, `replaced_by_id`, `ip_address`, `user_agent`, `created_at` |

Decisiones del esquema que este plan fija y de las que dependen `RF-SP-035` y `RF-SP-036`:

- **`token_hash` con índice único**, exigido por `security.md` §9. Es además el único acceso: el token se localiza por su hash, nunca por su valor.
- **`family_id` y `family_started_at` no son redundantes.** El primero agrupa la cadena de rotación para poder revocarla entera (`EX-001` de `RF-SP-035`); el segundo mide la **duración máxima de sesión desde el inicio de sesión**, no desde el último refresco, y es lo que impide que una sesión rotada con disciplina no caduque nunca. Guardar solo el primero obligaría a recorrer la cadena hasta su origen en cada refresco.
- **`revoked_reason` es obligatorio en toda fila revocada**, con `CHECK (revoked_at IS NULL) = (revoked_reason IS NULL)`. Es el dato del que depende que `RF-SP-035` distinga un robo de un cierre de sesión, y sin la restricción una revocación sin motivo pasaría inadvertida hasta producir un incidente falso. Dominio cerrado: `ROTACION`, `CIERRE`, `ACCESO_RETIRADO`, `CAMBIO_CONTRASENA`, `SESION_AGOTADA`, `REUTILIZACION`.
- **`replaced_by_id`** autorreferenciada y nulable: conserva el vínculo con el token que sustituyó al revocado (`CA-SP-303`).
- **`ip_address` de tipo `inet`**, igual que en los cuatro registros de auditoría y por el mismo motivo (`V4__create_audit_logs.sql`).
- **Índices:** `ix_refresh_tokens_user` sobre `(user_id) WHERE revoked_at IS NULL` —el acceso de `RF-SP-036` en su variante total y el de toda revocación en cascada— e `ix_refresh_tokens_family` sobre `(family_id)`.

**No lleva `deleted_at` ni `updated_at`.** No es una tabla de negocio: una sesión no se edita ni se borra lógicamente, se revoca, y `revoked_at` es su marca. La purga de `security.md` §5.5 la elimina físicamente.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `User` | Modificado | `authenticate(...)`: contador de fallos, umbral y bloqueo creciente con techo. `RN` de acceso sin Spring ni base de datos |
| `domain` | `LockoutPolicy` | **Nuevo** | Progresión del bloqueo y su techo, a partir de los parámetros de configuración. Es una función pura del número de bloqueos previos |
| `domain` | `RefreshToken` | **Nuevo** | Agregado de la sesión: vigencia, revocación con motivo, pertenencia a una familia y agotamiento de la duración máxima |
| `domain` | `RefreshTokenRepository` | **Nuevo** | Puerto: alta, búsqueda por hash, revocación individual, por familia y por usuario |
| `domain` | `UserRepository` | Modificado | Búsqueda por nombre de usuario **o** correo en una sola consulta |
| `application` | `LoginService` | Nuevo | Caso de uso. Orden de `plan.md` §4, emisión de credenciales y auditoría |
| `application` | `PasswordHasher` | **Modificado** | Puerto de `RF-SP-024`, que solo cifra. Gana la **verificación** resistente a temporización y la **verificación en vacío** contra un hash de descarte, para el caso de cuenta inexistente |
| `application` | `AccessTokenIssuer` | **Nuevo** | Puerto de emisión del JWT. Lo consume también `RF-SP-035` |
| `application` | `RateLimiter` | **Nuevo** | Puerto de limitación de tasa por clave. Lo consumen `RF-SP-034` y `RF-SP-035` |
| `infrastructure` | `Argon2PasswordHasher` | **Modificado** | Adaptador de `RF-SP-024` sobre `Argon2PasswordEncoder` de Spring Security. Aquí implementa las dos operaciones nuevas del puerto, con los parámetros de costo en configuración (`security.md` §3.2) |
| `infrastructure` | `JwtAccessTokenIssuer` | Nuevo | Firma con `JWT_SECRET`, sin valor por defecto en ningún entorno (Art. IX.5) |
| `infrastructure` | `InMemoryRateLimiter` | Nuevo | Ventana deslizante en memoria del proceso. Limitación declarada en §9 y §10 |
| `infrastructure` | `JpaRefreshTokenRepository` | Nuevo | Adaptador sobre `refresh_tokens` |
| `shared/security` | `JwtAuthenticationFilter` | **Nuevo** | Valida el token de acceso y puebla el contexto. Es lo que hace que `CurrentActor` deje de devolver vacío siempre |
| `shared/security` | `MustChangePasswordFilter` | **Nuevo** | Niega todo endpoint salvo `RF-SP-037` mientras la marca esté puesta (§5) |
| `shared/security` | `SecurityConfig` | Modificado | Registra los dos filtros. `/api/v1/auth/login` ya figura como ruta pública |
| `api` | `AuthController` | Nuevo | `POST /api/v1/auth/login` |
| `api` | `LoginRequest` / `LoginResponse` | Nuevos | DTO de entrada y salida |
| `domain` | `FailedAttemptLedger` | **Nuevo** (25-08-2026) | Contador de fallos de los identificadores **sin cuenta**. No defiende de nada: existe para que la respuesta a un identificador inventado sea indistinguible de la de uno real (§9) |
| `shared/error` | `DomainException` | **Modificado** (25-08-2026) | Transporta miembros de extensión de RFC 9457 §3.2, que es como viajan `remainingAttempts`, `unlockAt` y `retryAfterSeconds` |
| `shared/error` | `GlobalExceptionHandler` | **Modificado** (25-08-2026) | Vuelca esos miembros en el `ProblemDetail` sin filtrarlos: la lista de lo que la API publica vive en el dominio y no en dos sitios |

`LockoutPolicy` se extrae a un componente propio porque es la única parte del requerimiento que tiene **aritmética**, y la aritmética es lo que se prueba barato y se equivoca caro: sin techo, la progresión convierte un ataque en una denegación de servicio contra el titular de la cuenta (`security.md` §3.2).

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Autentica y entrega las credenciales de sesión |

**Petición**

```json
{
  "identifier": "jperez",
  "password": "..."
}
```

Un solo campo para el identificador y no dos, porque **el cliente no tiene que declarar con cuál se presenta**: la prohibición del `@` en el nombre de usuario (`RF-SP-024`) hace que ningún valor sea ambiguo, y el sistema busca por ambas columnas sabiendo que a lo sumo una resuelve.

**Respuesta `200`**

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "9f2c...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "mustChangePassword": false
}
```

`expiresIn` en segundos, para que el cliente sepa cuándo renovar sin tener que decodificar el token. **No se devuelven los permisos efectivos ni ningún dato personal**: quien los necesite los consulta ya autenticado con `RF-SP-039`.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Identificador o contraseña ausentes | `VAL-001`, `VAL-002` |
| `401` | Credenciales inválidas, cuenta inexistente, inactiva o eliminada (`EX-001`) | `VAL-003` |
| `423` | Cuenta bloqueada (`EX-002`) | `VAL-004` |
| `429` | Límite de intentos por credencial u origen superado (`EX-004`) | `ERR-429` |
| `500` | Fallo no controlado | `ERR-500` |

**Cuerpo de los errores**

Los dos rechazos de este endpoint llevan, además de los cinco miembros de RFC 9457, **miembros de extensión** (§3.2 de la propia norma). No es un formato paralelo: es el sitio que la norma reserva para exactamente esto.

```json
{
  "type": "https://nexus.factech.co/errors/no-autenticado",
  "title": "Se requiere autenticación",
  "status": 401,
  "detail": "Las credenciales no son válidas. Le quedan 3 intentos antes de que la cuenta se bloquee.",
  "correlationId": "…",
  "errors": [],
  "remainingAttempts": 3
}
```

```json
{
  "type": "https://nexus.factech.co/errors/cuenta-bloqueada",
  "title": "La cuenta está bloqueada",
  "status": 423,
  "detail": "La cuenta está bloqueada temporalmente por intentos fallidos.",
  "correlationId": "…",
  "errors": [],
  "unlockAt": "2026-08-25T14:32:00Z",
  "retryAfterSeconds": 174
}
```

| Miembro | En qué respuestas | Qué es |
|---|---|---|
| `remainingAttempts` | **Todo** `401` de este endpoint | Intentos que quedan antes del bloqueo. Cero significa que este mismo intento lo provocó |
| `unlockAt` | `423` automático, y el `401` que agota el contador | Instante en que el bloqueo se levanta solo |
| `retryAfterSeconds` | Los mismos | Lo que falta, medido por el servidor |

**El bloqueo manual no lleva ninguno de los dos**, y su ausencia es la información: esa cuenta no expira sola. Se **omiten**, no viajan nulos — con las dos formas admitidas, el cliente acabaría comprobando solo una.

**La espera no va escrita en el `detail`, y es una decisión.** «Vuelva a intentarlo en dos minutos» es cierto en el instante en que se serializa y deja de serlo enseguida: la respuesta viaja, el cliente la conserva, la persona lee el aviso un minuto después y el número le miente. Entregada como valor, el cliente la **descuenta**, y una cuenta regresiva no envejece. Van los dos valores porque sirven a dos cosas: el instante permite decir «hasta las 14:32» y sobrevive a que la respuesta se guarde; la duración permite la cuenta regresiva **sin depender del reloj del cliente**, que es lo que sí ocurriría derivándola del instante.

**Los cuatro casos de `EX-001` comparten también el número.** Eso obliga a dos cosas que no son obvias: que los rechazos con la contraseña correcta —cuenta no habilitada, credencial provisional caducada— consuman intento, y que el identificador **sin cuenta** tenga su propio contador (§3). Sin la segunda, `remainingAttempts` sería el verificador de cuentas que todo el orden de verificación existe para impedir.

**Los cuatro casos de `EX-001` comparten código, cuerpo y mensaje**, sin una sola diferencia observable. El `401` no lleva detalle por campo: un `errors` que señalara `identifier` frente a `password` reintroduciría por la puerta de atrás justo lo que el mensaje único evita.

**`423 Locked` y no `401` para la cuenta bloqueada.** La spec exige una respuesta **distinta** y distinguible (`CA-SP-377`), y hacerlo solo en el mensaje dejaría a un cliente decidiendo por el texto. `423` está definido en RFC 4918 y es el estado que expresa que el recurso existe y está bloqueado, que es exactamente lo que aquí se comunica a conciencia.

**Orden de verificación**

1. Formato y obligatoriedad.
2. Límite de tasa por credencial y por origen.
3. Localizar la cuenta por nombre de usuario o correo.
4. Si está bloqueada y el bloqueo sigue vigente → `423`, **sin comprobar la contraseña**.
5. Verificar la contraseña — **siempre**, aunque la cuenta no exista (§9).
6. Verificar que la cuenta esté activa y no eliminada.
7. Emitir credenciales.

**El paso 5 se ejecuta incluso cuando el paso 3 no encontró nada**, contra un hash de descarte de coste equivalente. Es lo que hace `CA-SP-293` verificable: sin ello, la respuesta ante un usuario inexistente vuelve en unos milisegundos y la de una contraseña incorrecta en las decenas que cuesta Argon2id, y el atacante enumera cuentas cronometrando.

El paso 6 va **después** de la contraseña y no antes, por lo mismo: cortar en el estado ahorraría el hash y volvería a abrir el canal de temporización.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/auth/login` | **Ninguno.** Público |

Ya figura en `RUTAS_PUBLICAS` de `SecurityConfig` desde el 22-08-2026. Este requerimiento añade los dos filtros que hacen que el resto deje de ser inalcanzable.

!!! warning "Enmienda a `security.md` §5.2 — el token de acceso gana el claim `mcp` (Art. I.7)"

    §5.2 enumera los claims del token de acceso: `iss`, `sub`, `jti`, `iat`, `exp` y los códigos de rol. Falta uno, y sin él `FA-002` no es implementable sin romper el diseño.

    La spec exige que, con el cambio obligatorio de contraseña pendiente, **el resto de endpoints se le nieguen a la persona** hasta que ejecute `RF-SP-037`. Comprobarlo en cada petición leyendo `users.must_change_password` obligaría a una consulta a la base de datos por petición, que es exactamente lo que D-08 y `architecture.md` §4 existen para evitar. La alternativa es que el dato viaje en el token: **`mcp`, booleano**.

    No contradice la prohibición de §5.2, que veta datos personales, correo e información sensible. `mcp` no identifica a nadie ni dice nada de la persona más allá de que le toca cambiar la contraseña, y su único lector posible es quien ya porta el token — su propio titular.

    La contrapartida está declarada y es la de todo el diseño: quien cambia la contraseña con `RF-SP-037` conserva un token de acceso con `mcp` en verdadero hasta quince minutos. Se resuelve como el resto —`RF-SP-037` revoca todas las sesiones y obliga a autenticarse de nuevo— y por eso no queda ventana real.

### 5.1 `MustChangePasswordFilter` — dónde va y qué deja pasar

Escrito el 26-08-2026 al ejecutar `T-12`, que era lo único que faltaba para que el claim `mcp` restringiera algo en lugar de solo viajar.

**Dónde va.** Dentro de la cadena de seguridad, **después** de la autorización y **después** de `ActorCaptureFilter`. Las tres posiciones importan y ninguna es indiferente:

| Si fuera… | Qué pasaría |
|---|---|
| Fuera de la cadena, en la del servlet | El contexto de seguridad está vacío: no hay token validado y por tanto **no hay claim que leer**. Dejaría pasar todo |
| Antes de la autorización | Quien además carece del permiso recibiría un rechazo que le cuenta algo de **su cuenta** en lugar de algo de **lo que intentaba hacer** |
| Antes de `ActorCaptureFilter` | La petición retenida quedaría en `request_log` como **anónima**, que es la peor forma de registrar algo: la fila existe y parece correcta |

**No se anota `@Component`**, a diferencia de `ActorCaptureFilter`. Spring Boot registra todo bean de tipo `Filter` también en la cadena del servlet, y ahí este correría antes de la autenticación —dejando pasar toda petición— para volver a correr después dentro de la cadena. Un filtro que **deniega** ejecutándose dos veces por petición, una siempre en vano, se lee mal en un volcado de pila meses después. Lo construye `SecurityConfig`, que es quien lo coloca.

**Qué deja pasar, y por qué son siete y no dos.** La lista lleva el motivo escrito al lado, con la misma forma que la lista blanca de `EndpointPermissionsIT`, porque es lo mismo: una excepción declarada, no un olvido.

| Ruta | Por qué |
|---|---|
| `POST /api/v1/auth/password` | `RF-SP-037` es quien limpia la marca. **Sin esta entrada la cuenta no tiene salida** |
| `GET /api/v1/users/me` | `RF-SP-039`: quien no puede leer su perfil no puede saber **por qué** lo rechazan |
| `POST /api/v1/auth/login` | Pública: obtener una credencial no puede exigir haber usado la anterior |
| `POST /api/v1/auth/refresh` | Pública, y el refresco **recalcula** la marca (`FA-002` punto 4) |
| `POST /api/v1/auth/logout` | Pública (`RF-SP-036`): retener a alguien **en** una sesión que quiere cerrar es lo contrario de lo que ese requerimiento persigue |
| Las dos de `RF-SP-040` | Declaradas por adelantado, igual que su política de límite de tasa. Una prueba las señala como futuras, y es lo que obligará a revisarlas el día que existan |

**Las tres públicas no sobran.** Sin token no llegarían marcadas… salvo que el cliente adjunte igualmente su `Authorization`, que es lo que hace todo cliente que guarda el token y lo pone en cada petición. Sin exceptuarlas, quien tiene la marca puesta **no podría cerrar su propia sesión**.

**No audita.** Ver `spec.md` `FA-002`: no es un intento de saltarse un permiso, y un evento por rechazo sepultaría `audit_security_log` bajo el ruido de cualquier interfaz que reintente en bucle.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Inicio de sesión exitoso | `audit_security_log` | `event_type = 'LOGIN_SUCCESS'`, `severity = 'INFORMATIVA'`, `outcome = 'SUCCESS'`, `target_user_id` de quien entró |
| Intento fallido (`EX-001`) | `audit_security_log` | `event_type = 'LOGIN_FAILURE'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`, `target_user_id` de la cuenta que se intentó usar — **nulo si no existe** |
| Bloqueo alcanzado (`EX-003`) | `audit_security_log` | `event_type = 'ACCOUNT_LOCKED'`, `severity = 'ALTA'`, `outcome = 'FAILURE'`, con la duración aplicada en `detail` |
| Exceso de peticiones (`EX-004`) | — | **No se audita.** El `429` no llega a tocar ninguna cuenta, y registrarlo convertiría el propio registro en el amplificador del ataque |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

Los tres tipos ya están en el catálogo cerrado de `security.md` §8.1 y en `ck_audit_security_log_event_type` (`V4`). **No hay enmienda que tramitar** en la auditoría.

**`actor_id` va en nulo en los tres**, y no es un descuido: durante el inicio de sesión no hay identidad probada todavía, ni siquiera en el caso exitoso — el evento describe el acto de probarla. Quién es la persona viaja en `target_user_id`, que es la columna que `RF-SP-014` filtra.

**`detail` nunca contiene la contraseña presentada**, ni en claro ni transformada (`CA-SP-298`), ni el identificador cuando la cuenta no existe: registrarlo convertiría el registro de seguridad en la lista de nombres de usuario que alguien está probando, legible por quien tenga `audit:read-security`.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Contador de fallos, `locked_until`, `last_login_at` y alta en `refresh_tokens` | **La misma** |
| Los tres eventos de `audit_security_log` | **Independiente**, `REQUIRES_NEW`, **sin esperar al commit** |

Que la auditoría sea independiente **y** sin esperar al commit es la parte que no puede relajarse, y es lo que `CA-SP-297` verifica. El intento fallido se registra precisamente **mientras** la transacción de negocio se revierte: escrito dentro de ella, el `rollback` borraría justo el evento que hay que conservar, y el ataque de fuerza bruta dejaría de tener rastro — que es el único sitio donde ese ataque es visible.

El inicio exitoso **tampoco espera al commit**, al contrario que los eventos de éxito de `RF-SP-030` y `RF-SP-031`. La asimetría es deliberada: allí un `SUCCESS` de algo revertido sería un evento fantasma, y aquí el hecho auditado —«alguien presentó credenciales válidas»— ya ocurrió aunque la emisión del token falle después. Perder ese registro es peor que tenerlo por duplicado.

## 8. Impacto sobre otros módulos

- **Todo el módulo.** Hasta este requerimiento, `CurrentActor` devuelve siempre vacío y ninguna prueba de API de ningún otro `RF` puede ejercitar su camino feliz. `requirements/sp.md` §6.1 ya declara la precedencia: `RF-SP-034` antes de los seis requerimientos que revocan sesiones.
- **`RF-SP-035` y `RF-SP-036`** consumen `refresh_tokens`, `RefreshToken`, `RefreshTokenRepository` y `AccessTokenIssuer`, todos creados aquí.
- **`RF-SP-028`, `RF-SP-029`, `RF-SP-031`, `RF-SP-037`, `RF-SP-038` y `RF-SP-040`** consumen `SessionRevoker`, el puerto que declaró `RF-SP-028`. Este requerimiento es quien lo **implementa**, porque solo puede hacerse contra la tabla que crea aquí. Es el bloqueo 2 de `tasks.md` de `RF-SP-031`.
- **`RF-SP-030`** deja de tener `CA-SP-258` bloqueado: los permisos efectivos tras renovar el token pasan a ser verificables.
- **`security.md` §5.2** se enmienda por §5: el token gana el claim `mcp`.
- **`security.md` §9** no cambia: el reparto de columnas que fijó el 22-08-2026 es exactamente el que este plan implementa.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Cortocircuitar cuando la cuenta no existe, sin verificar contraseña | Es la fuga de temporización que `CA-SP-293` prohíbe. La respuesta volvería en milisegundos frente a las decenas que cuesta Argon2id, y enumerar cuentas sería cuestión de cronometrar |
| Verificar el estado de la cuenta **antes** que la contraseña | Mismo problema por otra vía: ahorra el hash en las cuentas inactivas y las delata por tiempo |
| Un solo `401` también para la cuenta bloqueada | La spec resolvió lo contrario a conciencia (`spec.md` §14, pregunta 3): quien provocó el bloqueo ya sabe que la cuenta existe, y callarlo solo deja al titular sin entender por qué su contraseña correcta no funciona |
| Distinguir el bloqueo solo en el mensaje, conservando `401` | Obliga al cliente a decidir por el texto. `423` lo dice en el estado, que es donde un cliente mira primero |
| Dos campos de entrada, `username` y `email` | El cliente tendría que declarar con cuál se presenta, y el `@` prohibido en el nombre de usuario hace que no haga falta (`spec.md` §14, pregunta 1) |
| Leer `must_change_password` de la base de datos en cada petición | Una consulta por petición, que es lo que D-08 existe para evitar. El claim `mcp` lo resuelve sin coste (§5) |
| Rechazar el inicio de sesión con el cambio obligatorio pendiente | Dejaría a la persona sin poder cambiar la contraseña: necesita una sesión para hacerlo (`spec.md` §14, pregunta 4) |
| Guardar solo `family_id` y recorrer la cadena para medir la sesión | Una consulta recursiva en cada refresco para un dato que cabe en una columna. `family_started_at` lo resuelve en la fila |
| Un tope de sesiones simultáneas por persona | Obligaría a decidir qué ocurre al superarlo, y las dos respuestas posibles sorprenden a quien usa varios dispositivos. El crecimiento se controla con la purga (`spec.md` §14, pregunta 5) |
| Limitación de tasa distribuida, en base de datos o en un almacén externo | No hay almacén compartido en la plataforma y añadirlo es una decisión de infraestructura que este requerimiento no puede tomar. La ventana en memoria protege lo que hay hoy —un proceso— y su límite queda declarado en §10 |
| Auditar el `429` de exceso de peticiones | Convertiría el registro de seguridad en el amplificador del ataque: un atacante escribiría tantas filas como peticiones enviara |
| Registrar el identificador probado cuando la cuenta no existe | El registro de seguridad pasaría a ser la lista de nombres que alguien está probando, legible por quien tenga `audit:read-security` |
| Devolver los intentos restantes **solo cuando la cuenta existe** | Es lo directo y es un oráculo: con una contraseña cualquiera y mirando si el campo aparece se averigua qué correos están registrados. Deshace de un plumazo el orden de verificación entero, que existe para impedir exactamente eso |
| No devolver los intentos restantes en absoluto | Era el estado anterior y tiene una víctima concreta: quien no recuerda cuál de sus contraseñas usó se queda bloqueado sin previo aviso, y su primera noticia es no poder entrar |
| Contar los fallos de los identificadores sin cuenta **en la base de datos** | Cerraría el resto de §10 —la ventana efímera frente al contador persistente—, pero la clave la elige quien ataca: una fila por identificador inventado convierte la defensa en el amplificador del ataque, que es lo mismo que se rechazó para el `429` |
| Desalojar entradas del contador en memoria al llenarse, en vez de dejar de anotar | Desalojando, quien inunda el registro **elige** qué entradas se olvidan. Dejando de anotar, el único perjudicado es quien lo llenó |
| Escribir la duración de la espera en el mensaje | El texto es cierto al serializarse y deja de serlo enseguida: viaja, se guarda y se lee minutos después. La duración va como valor para que el cliente la descuente |
| Derivar la cuenta regresiva de `unlockAt` en el cliente, sin enviar la duración | Ataría la cuenta atrás a que el reloj del navegador coincida con el del servidor. Uno adelantado cinco minutos daría el bloqueo por terminado antes de tiempo |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Alguien «optimiza» el camino saltando el hash cuando la cuenta no existe | **Alto** | Es el defecto más probable del requerimiento y no produce ningún fallo visible. `CA-SP-293` lo prueba por tiempo, y §4 lo declara como orden obligatorio |
| La progresión del bloqueo se implementa sin techo | **Alto** | Convierte la defensa en una denegación de servicio contra el titular. `LockoutPolicy` es una función pura con prueba unitaria sobre el techo |
| La auditoría del intento fallido se escribe dentro de la transacción y el `rollback` la borra | **Alto** | `REQUIRES_NEW` sin esperar al commit; `CA-SP-297` lo verifica |
| El valor del refresh token se persiste o se registra | **Alto** | Solo su hash, con índice único sobre él. `CA-SP-291` y `CA-SP-298` |
| `revoked_reason` se deja opcional y `RF-SP-035` acaba tratando cierres como robos | **Alto** | `CHECK` en el esquema que lo hace obligatorio en toda fila revocada (§2) |
| La limitación de tasa en memoria no cubre varias instancias | Medio | **Declarado**: hoy la plataforma corre un proceso. Condición de disparo: el día que haya más de una instancia detrás de un balanceador, el `RateLimiter` cambia de adaptador sin tocar el caso de uso |
| El claim `mcp` se olvida y el cambio obligatorio no se aplica | Medio | `MustChangePasswordFilter` con prueba de API sobre un endpoint cualquiera |
| El reloj del servidor se desajusta y la vigencia deja de valer | Bajo | Riesgo de operación, declarado en `spec.md` §13. Fuera del alcance del requerimiento |
| El contador de un identificador sin cuenta caduca y el de una cuenta real no | Medio | **Declarado y aceptado** (`spec.md` §13): quien pruebe, espere a que cierre la ventana y repita, nota la diferencia. La señal que obtiene es la misma que el `423` ya entrega por diseño, y cerrarla exige persistir fallos de identificadores inventados. Se acota con una ventana no menor que el techo del bloqueo |
| El contador en memoria no se comparte entre instancias | Medio | **Declarado**, y con la misma raíz que la limitación de tasa: hoy la plataforma corre un proceso. Con varias, cada una contaría por su cuenta y el aviso sería inexacto para los identificadores sin cuenta — nunca para las cuentas reales, cuyo contador vive en la base. Se mueve con el almacén compartido de `T-08` |
| Un cliente presenta la duración leyéndola del texto del mensaje | Medio | El mensaje **no la lleva**, precisamente para que no pueda hacerse. `CA-SP-478` lo fija como comprobable |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-289` | API | Credenciales válidas devuelven ambos tokens |
| `CA-SP-290` | Unitaria | El JWT decodificado lleva los códigos de rol y **ningún** dato personal ni correo |
| `CA-SP-291` | Integración | En `refresh_tokens` está el hash y **no** el valor |
| `CA-SP-292` | API | Usuario inexistente, contraseña incorrecta y cuenta inactiva devuelven cuerpo idéntico byte a byte |
| `CA-SP-293` | **Integración de temporización** | La diferencia de tiempo entre usuario inexistente y contraseña incorrecta queda dentro del margen declarado, sobre un número de repeticiones suficiente |
| `CA-SP-294` | Integración | La cuenta se bloquea al alcanzar el umbral configurado |
| `CA-SP-295` | Integración | Un inicio exitoso pone el contador a cero |
| `CA-SP-296` | Integración | Las tres severidades de `security.md` §8.1 en sus tres eventos |
| `CA-SP-297` | Integración | El evento de fallo sobrevive al `rollback` de la transacción de negocio |
| `CA-SP-298` | Integración | Ni la contraseña ni el identificador inexistente aparecen en ningún registro |
| `CA-SP-299` | API | Una persona sin roles activos entra y su token no concede nada |
| `CA-SP-375` | API | Entra con nombre de usuario y con correo; con ninguno si la contraseña no coincide |
| `CA-SP-376` | Unitaria + Integración | Bloqueo al **quinto** fallo; la progresión crece y **se detiene en el techo** |
| `CA-SP-377` | API + temporización | La cuenta bloqueada devuelve `423`, y el tiempo de respuesta demuestra que **no** se comprobó la contraseña |
| `CA-SP-378` | API | El bloqueo automático informa cuándo expira; el manual remite a un administrador |
| `CA-SP-379` | API | La cuenta con cambio pendiente entra y la respuesta lo advierte |
| `CA-SP-380` | Integración | Dos inicios simultáneos producen dos refresh tokens independientes y ambos funcionan |
| `CA-SP-300` | API | Superar el límite por credencial o por origen devuelve `429` |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Intentos fallidos alternados con uno exitoso | Integración | El umbral cuenta fallos **consecutivos**: el exitoso pone el contador a cero |
| Bloqueo que expira entre dos intentos | Integración | El siguiente intento se procesa con normalidad, sin intervención de nadie |
| Identificador con arroba | Unitaria | Resuelve solo contra la columna de correo |
| Correo que otra persona tuvo y liberó | Integración | Identifica a su titular actual, y a lo sumo a una cuenta |
| Muchos intentos sobre una cuenta inexistente | API | No hay contador que incrementar; **el límite por origen es la única defensa** y debe dispararse |
| Contraseña correcta sobre cuenta eliminada | API | Se rechaza como `EX-001`, con el cuerpo genérico |

**`CA-SP-293` y `CA-SP-377` son pruebas de temporización y hay que escribirlas con cuidado**, porque una prueba de tiempo mal hecha es intermitente y acaba desactivada — y desactivarla deja el requerimiento sin su defensa principal. Se miden medianas sobre repeticiones, no una sola medición, y el margen se declara en la prueba con su justificación. Si resultara inestable en CI, la salida es subir las repeticiones o afinar el margen, **nunca** eliminarla.

---

## 12. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.2.0 | 25-08-2026 | Reconciliado con `spec.md` v0.2.0. Tres componentes: `FailedAttemptLedger` —contador efímero de los identificadores sin cuenta, que **no defiende de nada** y existe solo para que la respuesta no delate—, y las dos piezas de `shared/error` que hacen viajar los miembros de extensión de RFC 9457 §3.2. §4 gana el **cuerpo** de los dos errores, que hasta ahora el contrato declaraba sin él. Seis alternativas descartadas y tres riesgos nuevos, uno de ellos —la ventana efímera frente al contador persistente— es un resto que **no se cierra** y se declara. | Responsable técnico |
| 0.1.0 | 24-08-2026 | Redacción inicial sobre `spec.md` v0.1.0. | Responsable técnico |
