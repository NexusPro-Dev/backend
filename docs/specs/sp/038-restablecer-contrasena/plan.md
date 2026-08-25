# PLAN — `RF-SP-038` Restablecer la contraseña de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-038` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 24-08-2026 |

---

## 1. Enfoque

Es la operación **más delicada del módulo**, y su especificación no lo disimula: quien la ejecuta obtiene, durante un tiempo, la capacidad de entrar como otra persona. Ninguna otra operación de `SP` concede eso — ni siquiera asignar el rol raíz, porque eso deja rastro atribuible a quien lo hizo, mientras que entrar con la credencial de otro produce actividad atribuida a esa otra persona.

De ahí que el plan no tenga margen creativo. Las tres defensas ya están decididas y lo que corresponde es implementarlas sin aflojar ninguna:

- **Permiso propio**, `users:reset-password`, que no acompaña a `users:update`.
- **Marca de cambio obligatorio con caducidad**, para que la ventana en que el administrador conoce la credencial se cierre sola.
- **Evento de severidad alta**, porque esta es la primera operación que hay que revisar cuando alguien pregunta cómo entró alguien donde no debía.

La pieza que este plan sí aporta es la **caducidad**, que la spec resolvió pedir sin decir cómo se guarda. Es el único cambio de esquema del requerimiento y la única decisión de diseño real: sin ella, una cuenta restablecida y nunca usada queda indefinidamente con una credencial conocida por otra persona, y nadie se entera porque no falla nada.

Y dos cosas que esta operación **no** hace, ambas fáciles de añadir por descuido: no levanta un bloqueo (`CA-SP-394`) y no devuelve la contraseña asignada (`CA-SP-393`).

## 2. Cambios de esquema

**Migración:** `V28__add_provisional_credential_expiry.sql`

| Tabla | Cambio | Detalle |
|---|---|---|
| `users` | Altera | `provisional_password_expires_at timestamptz NULL` |

**Nulo significa credencial definitiva.** La columna se puebla al restablecer, con el instante de la operación más el plazo configurado, y se **limpia** cuando `RF-SP-037` sustituye la contraseña — que es el momento en que la credencial deja de ser provisional porque la eligió su titular.

Va acompañada de un `CHECK` que la ata a la marca que la justifica:

```sql
CONSTRAINT ck_users_provisional_expiry
    CHECK (provisional_password_expires_at IS NULL OR must_change_password)
```

Sin él caben dos estados que no significan nada: una credencial definitiva con fecha de caducidad, y una provisional sin ella. El segundo es el peligroso —es exactamente la ventana que este requerimiento existe para cerrar— y sería invisible.

**No se añade una columna de estado ni una marca de «caducada».** Igual que con la membresía de `RF-SP-032`, la vigencia **se evalúa al consultarla**: `RF-SP-034` compara la fecha con el momento del intento y rechaza si pasó. Mantener una marca exigiría un proceso programado que ningún requerimiento cubre, y un dato derivable que alguien tiene que escribir es un dato que un día estará mal.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `User` | Modificado | `resetPasswordBy(...)`: sustituye el hash, marca el cambio obligatorio y fija la caducidad de la credencial provisional. **No toca el estado ni el bloqueo** |
| `domain` | `SelfOperationGuard` | Sin cambios | `RN-SP-017` como regla pura, creada por `RF-SP-028`. **Este requerimiento es el tercero que la consume**, junto con `RF-SP-029` y `RF-SP-041` |
| `domain` | `PasswordPolicy` | Sin cambios | Componente de `RF-SP-024` |
| `application` | `ResetUserPasswordService` | Nuevo | Caso de uso. `@Transactional`, orden de `plan.md` §4, revocación y auditoría |
| `application` | `PasswordHasher` | Sin cambios | Puerto de `RF-SP-024` |
| `application` | `CommonPasswordCatalog` | Sin cambios | Puerto de `RF-SP-024` |
| `application` | `SessionRevoker` | Sin cambios | Puerto de `RF-SP-028`, implementado por `RF-SP-034` |
| `application` | `AccessRevocationPublisher` | Sin cambios | Puerto de `RF-SP-028` |
| `api` | `UserController` | Modificado | Añade `POST /api/v1/users/{id}/password-reset` |
| `api` | `ResetPasswordRequest` | Nuevo | DTO de entrada |

**Ningún componente de dominio nuevo**, igual que en `RF-SP-037`. Lo único propio es la caducidad, y vive en el agregado `User` porque es un atributo de la cuenta, no una regla aparte.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/users/{id}/password-reset` | Fija una credencial provisional sobre la cuenta indicada |

`POST` sobre un subrecurso y no `PATCH` sobre el usuario: cada petición **crea** un restablecimiento, que es un hecho con fecha y con caducidad propia, no la edición de un campo. Es la misma forma que `RF-SP-029` usa para la eliminación.

**Petición**

```json
{ "newPassword": "..." }
```

**Respuesta `204 No Content`.** La contraseña asignada **no se devuelve**: la conoce quien la escribió, y repetirla en la respuesta la expondría a cualquier registro de la operación (`CA-SP-393`). Tampoco se devuelve la fecha de caducidad — quien la necesite la consulta con `RF-SP-026`.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Contraseña ausente | `VAL-001` |
| `400` | La contraseña no cumple la política (`EX-002`) | `VAL-002` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `users:reset-password` | `AUTH-002` |
| `404` | El usuario no existe o está eliminado (`EX-003`) | `VAL-004` |
| `409` | El actor es el propio usuario (`EX-001`) | `RN-SP-017` |
| `500` | Fallo no controlado | `ERR-500` |

El cuerpo del `409` **debe indicar que corresponde `RF-SP-037`**, que es lo que `spec.md` `EX-001` exige. Sin esa indicación, quien lo recibe concluye que no puede cambiar su propia contraseña.

**Orden de verificación**

1. Formato y obligatoriedad.
2. La contraseña cumple la política mínima.
3. El usuario existe y no está eliminado.
4. El usuario no es el propio actor (`RN-SP-017`).

El paso 4 va después del 3 porque necesita el usuario resuelto, y el 2 va el primero porque **no depende de nada**: rechazar una contraseña débil antes de leer nada evita que la operación toque la cuenta para nada.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/users/{id}/password-reset` | `users:reset-password` |

**Permiso propio y separado de `users:update`**, ya sembrado en `V3__seed_permissions.sql`. `security.md` §4.4 lo trata como los cuatro de auditoría: se concede aparte, y quien administra datos de usuarios no restablece credenciales por el mero hecho de administrarlos. `CA-SP-337` verifica que poseer `users:update` no basta.

**`RN-SP-017` no es autorización, es regla de negocio.** Se comprueba en el caso de uso con `SelfOperationGuard` y no en `@PreAuthorize`, porque depende de comparar el actor con el sujeto de la ruta — y porque su rechazo es `409`, no `403`. Confundirlas haría que un administrador legítimo recibiera un error de permisos sobre un permiso que sí tiene.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Restablecimiento efectivo | `audit_security_log` | `event_type = 'PASSWORD_RESET'`, `severity = 'ALTA'`, `outcome = 'SUCCESS'`, `target_user_id` de la persona afectada, `actor_id` de quien lo ejecutó. En `detail`, **la fecha de caducidad de la credencial provisional** y nada más |
| Rechazo por `RN-SP-017` (`409`) | `audit_error_log` | `resource = 'users'`, `error_type = 'BUSINESS_RULE'`, `severity = 'MEDIA'` |
| Rechazo `404` y `400` de política | — | **No se auditan**: `ck_audit_error_log_status` rechaza ambos estados |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

**`actor_id` y `target_user_id` son personas distintas por construcción** —`RN-SP-017` lo garantiza— y ese es justamente el valor del evento: responde *quién* fijó la credencial de *quién*, que es la pregunta que se hace cuando alguien entró donde no debía.

**En `detail` no va la contraseña, ni su longitud, ni ninguna pista sobre ella** (`CA-SP-393`). Sí va la caducidad, porque es lo que permite saber después si la ventana llegó a cerrarse sola o alguien usó la credencial antes.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Sustitución del hash, marca de cambio obligatorio, caducidad y revocación de todas las sesiones | **La misma** |
| Publicación del corte de tokens de acceso | Dentro de la misma, **antes** del commit |
| Evento `PASSWORD_RESET` | **Independiente**, `REQUIRES_NEW`, enganchada al commit |
| Auditoría del rechazo por `RN-SP-017` | **Independiente**, `REQUIRES_NEW`, sin esperar al commit |

Mismo criterio que `RF-SP-037` §7: la revocación va dentro, y si falla, el restablecimiento falla. Una credencial sustituida con las sesiones antiguas vivas deja dentro exactamente a quien se quería expulsar si la cuenta estaba comprometida.

## 8. Impacto sobre otros módulos

- **`RF-SP-034`** debe comparar `provisional_password_expires_at` con el momento del intento y **rechazar si pasó** (`CA-SP-392`). Es la mitad de este requerimiento que no vive en este requerimiento, y sin ella la caducidad no existe.
- **`RF-SP-037`** limpia la marca **y la caducidad** al sustituir la contraseña (`CA-SP-330`).
- **`RF-SP-028`** aporta `SelfOperationGuard`, `SessionRevoker` y `AccessRevocationPublisher`, y es además la operación que sí levanta bloqueos — esta no.
- **`RF-SP-026`** devuelve la marca y su caducidad, que es lo que permite a soporte saber si la credencial provisional sigue viva.
- **`RF-SP-040`**, cuando exista, reduce el uso de esta operación a los casos en que el titular no puede recibir el aviso. No la sustituye.
- **`requirements/sp.md` §10.10** gana la columna nueva.
- **`security.md` §3.2** gana la mención de la caducidad de la credencial provisional, que hoy solo describe la marca.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Que el sistema genere la contraseña | Obliga a **devolverla en la respuesta**, con lo que la credencial viaja por HTTP y acaba en los registros del cliente y a la vista de quien mire la pantalla (`spec.md` §14, pregunta 1) |
| No caducar la credencial provisional | Una cuenta restablecida y nunca usada queda indefinidamente con una credencial conocida por otra persona, y nadie se entera porque no falla nada (`spec.md` §14, pregunta 2) |
| Guardar la caducidad como marca mantenida por un proceso | Exige un proceso programado que ningún requerimiento cubre. Se evalúa al consultarla, igual que la membresía de `RF-SP-032` |
| Fundir restablecer con desbloquear | Restablecer una credencial no debe levantar en silencio un bloqueo que el sistema puso por sospecha. El coste —dos llamadas con dos permisos— se acepta (`spec.md` §14, pregunta 4) |
| Conceder el restablecimiento a quien tiene `users:update` | Quien administra datos de usuarios no debería poder entrar como ellos por el mero hecho de administrarlos (`security.md` §4.4) |
| Comprobar `RN-SP-017` en `@PreAuthorize` | Su rechazo es `409`, no `403`. Un administrador legítimo recibiría un error de permisos sobre un permiso que sí tiene |
| Devolver la fecha de caducidad en la respuesta | No hace falta para la operación y añade un dato al cuerpo de una respuesta que no lleva ninguno. Se consulta con `RF-SP-026` |
| Notificar a la persona afectada | Exige el canal de correo de **D-23**, y resolverlo aquí dejaría este requerimiento bloqueado — y con él, a quien olvida su contraseña sin ninguna vía de vuelta. Anotado con su condición de disparo (`spec.md` §14, pregunta 3), y **`RF-SP-040` lo cierra** |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| `RF-SP-034` no comprueba la caducidad y la ventana no se cierra nunca | **Alto** | §8 lo declara como dependencia explícita; `CA-SP-392` la verifica desde este lado y falla si aquel no la implementa |
| El restablecimiento levanta un bloqueo vigente | **Alto** | `CA-SP-394` lo verifica sobre las dos clases de bloqueo, automático y manual |
| La contraseña asignada aparece en la respuesta o en un registro | **Alto** | `CA-SP-334` y `CA-SP-393`; `PasswordHash` de `RF-SP-024` enmascara su `toString()` |
| Alguien con `users:update` puede restablecer credenciales | **Alto** | Permiso separado y sembrado aparte; `CA-SP-337` |
| Queda una credencial provisional sin caducidad por un camino no previsto | Medio | El `CHECK` del esquema lo hace imposible (§2) |
| El actor restablece su propia contraseña por esta vía | Medio | `SelfOperationGuard`; `CA-SP-332` |
| Quien atiende el caso olvida desbloquear y cree que restableció el acceso | Medio | Consecuencia asumida de separar las operaciones. La interfaz de soporte puede ofrecerlas contiguas (`spec.md` §14, pregunta 4) |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-329` | Integración | La persona se autentica con la contraseña nueva |
| `CA-SP-330` | Integración | La cuenta queda marcada, y `RF-SP-037` limpia marca **y caducidad** |
| `CA-SP-331` | Integración | Todos sus refresh tokens revocados y sus tokens de acceso rechazados |
| `CA-SP-332` | API | El actor sobre sí mismo: `409` indicando que corresponde `RF-SP-037` |
| `CA-SP-333` | API | Contraseña que no cumple la política: `400` indicando qué regla |
| `CA-SP-334` | API | La respuesta **no contiene** la contraseña asignada |
| `CA-SP-335` | Integración | Estado, roles y membresía intactos; una cuenta bloqueada **sigue bloqueada** |
| `CA-SP-336` | Integración | Evento `PASSWORD_RESET` con severidad alta y `target_user_id` de la persona afectada |
| `CA-SP-392` | **Integración de dos requerimientos** | Pasado el plazo, `RF-SP-034` **rechaza** la credencial provisional y hay que restablecerla de nuevo |
| `CA-SP-393` | Integración | Ni la respuesta ni ningún registro contienen la contraseña |
| `CA-SP-394` | Integración | No levanta el bloqueo, **ni el automático ni el manual** |
| `CA-SP-337` | API | Un actor con `users:update` y sin `users:reset-password` recibe `403` |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Usuario inactivo | Integración | Se restablece, y sigue sin poder entrar. Preparar la credencial no concede acceso |
| Último superadministrador | Integración | Se admite: la cuenta sigue activa y conserva su rol. No es como desactivarla |
| Persona con sesión abierta | Integración | Cae de inmediato, no en quince minutos |
| La persona nunca inicia sesión tras el restablecimiento | Integración | La credencial caduca sola; superado el plazo hay que restablecerla de nuevo |
| Dos restablecimientos concurrentes | Integración | Se serializan; prevalece el último y **ambos** quedan en la auditoría |
| Restablecimiento seguido de cambio propio | Integración | La marca y la caducidad se limpian; las sesiones se revocan dos veces sin efecto adverso |

**`CA-SP-392` es la única prueba de este requerimiento que no puede escribirse dentro de él.** La caducidad se fija aquí y se comprueba en `RF-SP-034`, de modo que la prueba tiene que ejecutar el restablecimiento, adelantar el reloj y después intentar iniciar sesión. Escrita solo desde este lado verificaría que la columna se pobló, que no es lo mismo que verificar que la ventana se cierra.
