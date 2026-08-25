# PLAN — `RF-SP-040` Restablecer la propia contraseña olvidada

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-040` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 22-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 24-08-2026 |

---

!!! warning "Aprobado, y con una tarea que no puede ejecutarse hasta cerrar D-23"

    La especificación advertía que este `plan.md` no podía escribirse antes de decidir el mecanismo de envío. **Se escribe igual**, y conviene decir por qué el aviso sigue siendo cierto solo a medias.

    `architecture.md` §15.1 ya decidió la **forma**: infraestructura transversal con **puerto publicado**. Eso basta para diseñar todo lo demás — el esquema, las dos operaciones, la respuesta indistinguible, el desacople y la auditoría—, porque todo eso se apoya en el puerto y no en quién lo implementa.

    **D-23 bloquea exactamente una tarea**: el adaptador que envía de verdad. Está aislada en `T-09` y declarada como bloqueo 1 de `tasks.md`. El resto del requerimiento es construible y probable contra un doble del puerto, y así queda planificado.

    Lo que **no** puede darse por terminado sin D-23 es el requerimiento: sin envío real, nadie recibe nada.

---

## 1. Enfoque

Es la única operación de **escritura pública** del módulo junto al inicio de sesión, y eso decide su diseño entero. Cualquiera puede invocarla contra la cuenta de otro, de modo que todo lo que revele —en el cuerpo, en el estado o en el tiempo— es información que se está regalando.

La diferencia esencial con `RF-SP-038` es **quién termina conociendo el secreto**: aquí, solo el titular. Por eso esta operación no marca la cuenta para cambio obligatorio ni fija caducidad de credencial: no hay ventana que cerrar, porque nadie más la conoce.

Son **dos operaciones encadenadas** y se planifican juntas porque por separado no significan nada: una emite el permiso temporal, la otra lo consume. Ninguna de las dos exige autenticación.

Tres defensas sostienen la operación y las tres se implementan mal con facilidad:

- **Respuesta indistinguible**, en el cuerpo **y en el tiempo**. Igualar solo el mensaje deja la defensa declarada y no real: emitir el permiso y enviar cuesta cientos de milisegundos más que no hacer nada, y eso se mide desde fuera con un cronómetro. De ahí que el envío sea **desacoplado** (`RNF-FIA-001`) — decisión de seguridad, no de rendimiento.
- **Un solo permiso vivo por persona**: emitir uno invalida el anterior, de modo que no quedan varias puertas abiertas a la vez.
- **Límite de tasa por identidad y por origen**, sin el cual la operación permite inundar de correos a una persona real —que es acoso— y sondear identidades en masa.

Y una asimetría que hay que respetar: `EX-002` —contraseña que no cumple la política— **no consume el permiso**. El error es de la persona legítima, y obligarla a pedir otro por escribir una contraseña corta sería castigar el intento correcto.

## 2. Cambios de esquema

**Migración:** `V29__create_password_reset_permits.sql`

| Tabla | Cambio | Detalle |
|---|---|---|
| `password_reset_permits` | Crea | `id`, `user_id`, `permit_hash`, `expires_at`, `consumed_at`, `superseded_at`, `requested_ip`, `created_at` |

Decisiones del esquema:

- **`permit_hash` con índice único, nunca el valor.** Es el mismo criterio que `refresh_tokens` de `RF-SP-034`: quien lea la base de datos no puede tomar la cuenta de nadie. El permiso se localiza por su hash.
- **Un solo permiso vivo por persona**, declarado en el esquema y no solo en el dominio, con un índice único parcial:

  ```sql
  CREATE UNIQUE INDEX uq_password_reset_permits_vigente
      ON password_reset_permits (user_id)
   WHERE consumed_at IS NULL AND superseded_at IS NULL;
  ```

  `FA-002` exige que emitir uno invalide el anterior. Escrito solo en el caso de uso, dos solicitudes concurrentes dejarían dos permisos vivos y **dos vías de entrada abiertas**; el índice lo hace imposible.

- **`consumed_at` y `superseded_at` separados**, y no una sola columna de estado. Las dos razones por las que un permiso deja de servir son distintas y la auditoría necesita distinguirlas: uno se usó —la persona entró— y el otro se sustituyó porque pidió otro. Colapsarlas haría indistinguible «alguien completó el flujo» de «alguien pidió dos veces».
- **La caducidad se evalúa al consultarla**, comparando `expires_at` con el momento del intento. Ningún proceso la limpia, por el mismo criterio de `RF-SP-032` §2 y `RF-SP-038` §2.
- **`requested_ip` de tipo `inet`**, para que una ráfaga de solicitudes sea investigable por origen.
- **Sin `updated_at` ni `deleted_at`.** No es una tabla de negocio: es un registro de permisos de un solo uso.

**La purga** de permisos consumidos y caducados tiene el mismo hueco que la de `refresh_tokens` (`RF-SP-035` §10): no hay requerimiento que la cubra. Queda declarado.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `PasswordResetPermit` | **Nuevo** | Agregado del permiso: vigencia, consumo, sustitución y la regla de que solo uno vive a la vez |
| `domain` | `PasswordResetPermitRepository` | **Nuevo** | Puerto: emisión, búsqueda por hash con bloqueo, consumo y sustitución |
| `domain` | `User` | Modificado | `resetPasswordByOwner(...)`: sustituye el hash y **limpia** la marca de cambio obligatorio y su caducidad. No toca estado ni bloqueo |
| `domain` | `PasswordPolicy` | Sin cambios | Componente de `RF-SP-024` |
| `application` | `RequestPasswordRecoveryService` | Nuevo | Caso de uso de la solicitud. Respuesta indistinguible y emisión desacoplada |
| `application` | `ConfirmPasswordRecoveryService` | Nuevo | Caso de uso de la confirmación |
| `application` | `PasswordHasher`, `CommonPasswordCatalog` | Sin cambios | Puertos de `RF-SP-024` |
| `application` | `SessionRevoker`, `AccessRevocationPublisher` | Sin cambios | Puertos de `RF-SP-028` |
| `application` | `RateLimiter` | Sin cambios | Puerto de `RF-SP-034` |
| `shared/notification` | `NotificationSender` | **Nuevo, transversal** | Puerto de envío saliente (`architecture.md` §15.1). **No pertenece a `SP`**: lo declara la infraestructura compartida y lo consumirán academia, comisiones y `RF-SP-027` |
| `shared/notification` | Adaptador de envío | **Bloqueado por D-23** | Proveedor, desacople, reintentos y rebotes. Es la única pieza que la decisión pendiente impide escribir |
| `api` | `AuthController` | Modificado | Añade las dos rutas de §4 |
| `api` | `PasswordRecoveryRequest`, `PasswordRecoveryConfirmation` | Nuevos | DTO de cada operación |

**`NotificationSender` vive en `shared/notification` y no en el módulo**, y es lo que `architecture.md` §15.1 decidió el 22-08-2026: ni submódulo de `SP` —haría a `SP` dueño de los envíos de academia y comisiones, que no son suyos— ni módulo propio, que fijaría código inmutable sobre un alcance que `modules.md` §6 dice expresamente que no puede fijarse todavía.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/password-recovery` | Solicita el permiso temporal |
| `POST` | `/api/v1/auth/password-recovery/confirmation` | Consume el permiso y fija la contraseña nueva |

!!! warning "Enmienda a `requirements/sp.md` §9 — una segunda ruta (Art. I.7)"

    La tabla de API declara **una sola** ruta para este requerimiento, y `requirements.md` v0.14.0 ya anticipó el 22-08-2026 que haría falta otra: son **dos operaciones públicas encadenadas** y la tabla solo describe la primera.

    `confirmation` es un subrecurso de la recuperación: cada petición **crea** una confirmación, que es lo que `POST` significa. Se descartó reutilizar la misma ruta distinguiendo por el cuerpo, que es lo que hace `RF-SP-032` con la membresía: allí es la misma operación con datos distintos, y aquí son dos operaciones con **reglas opuestas** —una no exige nada y la otra exige un permiso vigente—, cuyo único punto en común es que ambas son públicas.

**Solicitud**

```json
{ "identifier": "jperez" }
```

Nombre de usuario **o** correo, igual que en `RF-SP-034` y por el mismo motivo: el `@` prohibido en el nombre de usuario hace que ningún valor sea ambiguo.

**Respuesta `202 Accepted`**, con un cuerpo **idéntico** exista o no la identidad. `202` y no `200`: lo que se acepta es la solicitud, y el envío ocurre después y por otro camino (`RNF-FIA-001`). Es además el estado honesto — el sistema no puede afirmar que algo se entregó.

**Confirmación**

```json
{
  "permit": "a1b2...",
  "newPassword": "..."
}
```

**Respuesta `204 No Content`.** Ni el permiso ni la contraseña aparecen en ninguna respuesta.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Identidad ausente en la solicitud | `VAL-001` |
| `400` | Permiso o contraseña ausentes en la confirmación | `VAL-002`, `VAL-003` |
| `400` | La contraseña no cumple la política (`EX-002`) | `VAL-004` |
| `422` | Permiso inexistente, caducado, ya usado o sustituido (`EX-001`) | `VAL-005` |
| `429` | Límite de tasa superado (`EX-003`) | `ERR-429` |
| `500` | Fallo no controlado | `ERR-500` |

**La solicitud no tiene más error que el `400` de identidad ausente y el `429`.** Ninguna otra condición puede producir un rechazo sin revelar algo: una identidad inexistente devuelve `202` igual que una existente, y eso es toda la defensa.

**Los cuatro casos de `EX-001` comparten respuesta.** Distinguirlos le diría a quien prueba permisos al azar cuál de ellos estuvo a punto de acertar.

**El `400` de política se comprueba antes de tocar el permiso**, y ese orden es la implementación de `CA-SP-461`: rechazar sin consumir.

**Orden de verificación — confirmación**

1. Formato y obligatoriedad.
2. La contraseña cumple la política mínima. **Aquí no se ha tocado el permiso todavía.**
3. Localizar el permiso por su hash, con bloqueo de fila.
4. Vigente, no consumido y no sustituido.
5. Sustituir la credencial, consumir el permiso y revocar sesiones.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/auth/password-recovery` | **Ninguno.** Público |
| `POST /api/v1/auth/password-recovery/confirmation` | **Ninguno.** Público, autorizado por el permiso temporal |

**Ambas deben añadirse a `RUTAS_PUBLICAS`** de `SecurityConfig`, junto a las de `RF-SP-035` y `RF-SP-036`.

**El `MustChangePasswordFilter` no las alcanza**: no hay token que llevar la marca. Es correcto y es además un camino legítimo — quien recibió una credencial provisional de un administrador y la olvidó antes de usarla llega por aquí (`spec.md` §13).

**Límite de tasa por identidad y por origen** sobre la solicitud, y **por origen** sobre la confirmación. La cota de la solicitud es la más estricta de las cuatro del sistema: es la única operación pública que provoca un envío saliente, y cada repetición es un correo a una persona real.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Solicitud sobre identidad existente | `audit_security_log` | `event_type = 'PASSWORD_RESET'`, `severity = 'ALTA'`, `outcome = 'SUCCESS'`, `target_user_id`, y en `detail` la etapa `SOLICITUD` |
| Solicitud sobre identidad inexistente | `audit_security_log` | Ídem con `outcome = 'FAILURE'` y **`target_user_id` nulo**. La identidad probada **no** se registra (§6, nota) |
| Restablecimiento completado | `audit_security_log` | `event_type = 'PASSWORD_RESET'`, `severity = 'ALTA'`, `outcome = 'SUCCESS'`, etapa `CONFIRMACION` |
| Permiso inválido (`EX-001`) | `audit_security_log` | Ídem con `outcome = 'FAILURE'`, etapa `CONFIRMACION`, `target_user_id` **nulo** salvo que el permiso resolviera a alguien |
| Rechazo `400` de política y `429` | — | **No se auditan**, por el criterio de `RF-SP-034` §6 |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

**Las cuatro filas usan `PASSWORD_RESET` y se distinguen por `outcome` y por la etapa en `detail`.** El catálogo de `security.md` §8.1 es cerrado y no tiene literales para «solicitud de recuperación»; añadirlos obligaría a alterar `ck_audit_security_log_event_type` en una migración para distinguir algo que dos columnas ya distinguen. Es la misma decisión que `RF-SP-035` §6 tomó con la sesión agotada y `RF-SP-037` §6 con el cambio fallido.

!!! important "La identidad probada no se registra cuando no existe"

    `CA-SP-469` exige registrar las solicitudes sobre identidades inexistentes, y es correcto: una ráfaga de ellas es un reconocimiento en curso. Lo que **no** se registra es **cuál** identidad se probó.

    Registrarla convertiría `audit_security_log` en la lista de nombres de usuario y correos que alguien está sondeando, legible por quien tenga `audit:read-security`. Lo que hace investigable la ráfaga es el **origen** —`ip_address` del núcleo común— y su **volumen**, no la identidad concreta. Mismo criterio que `RF-SP-034` §6.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Emisión del permiso y sustitución del anterior | **La misma** |
| Sustitución del hash, consumo del permiso, limpieza de la marca y revocación de sesiones | **La misma** |
| Publicación del corte de tokens de acceso | Dentro de la misma, antes del commit |
| Los cuatro eventos de `audit_security_log` | **Independiente**, `REQUIRES_NEW`. Los de éxito enganchados al commit; los de fallo **sin esperarlo** |
| **Envío saliente** | **Fuera de toda transacción y de la respuesta** (`RNF-FIA-001`) |

**El envío no participa de ninguna transacción ni de la respuesta**, y no es una decisión de rendimiento: si la respuesta esperase al envío, el tiempo delataría si la identidad existe y la defensa central caería. La consecuencia asumida —un fallo de envío no se refleja en la respuesta— está en `spec.md` §13 y su tratamiento forma parte de **D-23**.

El envío se dispara **después del commit**, no antes: un permiso enviado sobre una transacción que se revierte sería una vía de entrada que la base de datos no conoce.

## 8. Impacto sobre otros módulos

- **`architecture.md` §15.1** estrena `NotificationSender`. Este requerimiento es el primero que lo necesita; `RF-SP-027` y el aviso de `RF-SP-038` lo consumirán después.
- **`RF-SP-038`** cierra por fin la condición de disparo de su resolución 3: el aviso al titular existe (`CA-SP-474`).
- **`RF-SP-034`** no cambia. La credencial que esta operación fija **no es provisional**, de modo que no fija `provisional_password_expires_at` y el inicio de sesión no tiene nada nuevo que comprobar.
- **`RF-SP-028`** aporta `SessionRevoker` y `AccessRevocationPublisher`.
- **`RF-SP-027`** hereda y **agrava** el riesgo del correo no verificado: esta operación convierte el correo en la llave de la cuenta (`spec.md` §13).
- **`requirements/sp.md` §9** se enmienda por §4: la segunda ruta.
- **`security.md` §5.5** gana los dos límites de tasa nuevos, junto a los del inicio de sesión y el refresco.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Una sola ruta que solicita o confirma según el cuerpo | Son dos operaciones con reglas **opuestas** —una no exige nada, la otra un permiso vigente— cuyo único punto en común es ser públicas |
| Igualar solo el mensaje, sin desacoplar el envío | Deja la defensa declarada y no real: emitir y enviar cuesta cientos de milisegundos que se miden con un cronómetro (`spec.md` §14, pregunta 5) |
| Un retardo uniforme en lugar del desacople | Penaliza a todo el mundo y vuelve a filtrar en cuanto el envío se ralentiza por encima del retardo elegido |
| Guardar el valor del permiso en lugar de su hash | Quien lea la base de datos podría tomar cualquier cuenta. Mismo criterio que `refresh_tokens` |
| Una sola columna de estado en vez de `consumed_at` y `superseded_at` | Haría indistinguible «alguien completó el flujo» de «alguien pidió dos veces», que es lo que la auditoría necesita separar |
| Confiar la unicidad del permiso vivo solo al caso de uso | Dos solicitudes concurrentes dejarían **dos vías de entrada abiertas**. El índice único parcial lo hace imposible |
| Consumir el permiso aunque la contraseña no cumpla la política | Castiga el intento correcto: obliga a pedir otro permiso por escribir una contraseña corta (`CA-SP-461`) |
| Marcar la cuenta para cambio obligatorio | No hay ventana que cerrar: la contraseña la eligió su titular y nadie más la conoce. Es la diferencia deliberada con `RF-SP-038` |
| Rechazar la solicitud sobre cuentas bloqueadas | Revelaría que la cuenta está bloqueada, y esa fuga rompe la defensa central. Se admite sin levantar el bloqueo (`spec.md` §14, pregunta 4) |
| Vincular el permiso al origen desde el que se pidió | Rompe el caso normal: se pide en el ordenador y se abre el correo en el teléfono |
| Registrar la identidad probada cuando no existe | Convierte el registro de seguridad en la lista de identidades que alguien sondea (§6) |
| Literales propios en el catálogo para solicitud y confirmación | Obligan a alterar el `CHECK` del esquema para distinguir lo que `outcome` y `detail` ya distinguen |
| Esperar a D-23 para escribir este plan | Bloquearía todo el requerimiento por una sola tarea. La forma del canal ya está decidida y el resto se construye contra el puerto |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| **D-23 sin cerrar**: el adaptador de envío no existe y nadie recibe nada | **Alto** | Aislado en `T-09`; bloqueo 1 de `tasks.md`. El resto se construye y prueba contra un doble del puerto |
| La respuesta se iguala en el mensaje pero no en el tiempo | **Alto** | `CA-SP-473` es una prueba de temporización, con medianas sobre repeticiones. Sin desacople falla |
| Dos solicitudes concurrentes dejan dos permisos vivos | **Alto** | Índice único parcial en el esquema (§2), no solo en el caso de uso |
| El valor del permiso acaba en la base de datos o en un registro | **Alto** | Solo su hash, con índice único. `CA-SP-466` |
| El permiso se consume al rechazar por política | Medio | Orden de §4: la política se comprueba antes de tocarlo. `CA-SP-461` |
| El correo de la cuenta no es de su titular | **Alto**, y **no es de este requerimiento** | Heredado de `RF-SP-027` y **agravado** aquí: el correo pasa a ser la llave de la cuenta. La verificación del correo no tiene requerimiento. **Anotado como hueco del módulo** |
| El aviso se usa para molestar a alguien | Medio | El límite de tasa de `EX-003` lo acota. Declarado en `spec.md` §13 |
| La tabla crece sin purga | Bajo | Mismo hueco que `refresh_tokens` (§2) |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-456` | API | El flujo completo, sin intervención de nadie |
| `CA-SP-457` | API | Respuesta **idéntica** para identidad existente e inexistente |
| `CA-SP-458` | Integración | El permiso no sirve dos veces |
| `CA-SP-459` | API | El caducado se rechaza con la misma respuesta que uno inexistente |
| `CA-SP-460` | Integración | Emitir uno nuevo invalida el anterior |
| `CA-SP-461` | Integración | Contraseña que no cumple la política: rechazo **sin consumir** el permiso |
| `CA-SP-462` | Integración | Todas las sesiones revocadas y los tokens de acceso rechazados |
| `CA-SP-463` | Integración | La cuenta **no** queda marcada para cambio obligatorio |
| `CA-SP-464` | Integración | No levanta el bloqueo, ni automático ni manual |
| `CA-SP-465` | Integración | El estado de la cuenta no cambia |
| `CA-SP-466` | Integración | Ni la contraseña ni el permiso aparecen en respuesta ni registro |
| `CA-SP-467` | API | El límite de tasa rechaza sin revelar si la identidad existe |
| `CA-SP-468` | Integración | Solicitud y restablecimiento con severidad alta |
| `CA-SP-469` | Integración | Las solicitudes sobre identidades inexistentes se registran, **sin la identidad probada** |
| `CA-SP-473` | **Integración de temporización** | El tiempo de respuesta no distingue identidad existente de inexistente |
| `CA-SP-474` | Integración | El titular recibe el aviso al completarse |
| `CA-SP-475` | Integración | Un fallo del envío **no altera** ninguna de las dos respuestas |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Dos confirmaciones concurrentes con el mismo permiso | **Integración concurrente** | La primera lo consume, la segunda se rechaza. Sin bloqueo de fila ambas sustituyen la credencial |
| Dos solicitudes concurrentes | **Integración concurrente** | Queda **un** permiso vivo. El índice único parcial es lo que lo garantiza |
| Cuenta con cambio obligatorio pendiente que usa esta vía | Integración | La marca y su caducidad quedan limpias |
| Permiso pedido y usado desde dispositivos distintos | API | Se admite |
| Solicitud sobre cuenta eliminada | API | Se trata como identidad inexistente |
| Permiso emitido y nunca usado | Integración | Caduca solo; no queda nada abierto |

**`CA-SP-473` y `CA-SP-475` solo tienen sentido juntas**, y conviene escribirlas en el mismo archivo. La primera exige que la respuesta no espere al envío; la segunda, que un fallo del envío no la altere. Las dos son consecuencia de la misma decisión —el desacople— y una implementación que satisfaga una sola de ellas está mal por el mismo motivo.
