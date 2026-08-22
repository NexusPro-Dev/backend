# PLAN — `RF-SP-027` Editar usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-027` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide tres cosas: **cómo se distingue un campo ausente de uno vaciado cuando ninguno puede vaciarse**, **por qué el correo emite un evento de seguridad y el apellido no**, y **por qué esta operación no revoca ninguna sesión pese a tocar una vía de acceso**.

---

## 1. Enfoque

Es el gemelo de [`RF-SP-004`](../004-editar-rol/plan.md) sobre la otra entidad, y hereda de él la forma entera: `PATCH` parcial, `Patchable<T>` para distinguir la ausencia del vaciado, detección del cambio efectivo en `application` comparando el estado leído con el solicitado, diff producido por el dominio y no por un listener de JPA, y concurrencia resuelta como «gana el último en escribir». Lo que allí está argumentado **no se repite**.

Tres cosas difieren, y las tres nacen de lo mismo: **aquí se edita una vía de acceso**.

1. **El correo es credencial de entrada desde `RF-SP-024`.** Cambiar el de una cuenta ajena altera cómo esa persona entra en el sistema, y ese es el patrón clásico de apropiación de cuentas. Por eso emite evento de seguridad y el nombre no (§6), y por eso `security.md` §8.1 ya lo incorporó a su catálogo cerrado el 21-08-2026.
2. **Ningún campo admite vaciarse.** En `RF-SP-004` la descripción de un rol podía borrarse enviando `null`; aquí los tres campos son obligatorios en la fila y `VAL-002` y `VAL-003` prohíben dejarlos vacíos. `Patchable<T>` sigue siendo necesario —hay que distinguir «no lo envié» de «lo envié»— pero su tercer estado, el nulo explícito, pasa a ser **un rechazo** en lugar de una orden (§4).
3. **La unicidad que se verifica no es la del rol.** `uq_roles_name` es parcial y libera el nombre de un rol eliminado; `uq_users_email` es **total** y no libera nada (`RN-SP-016`). La consecuencia es que un correo puede estar ocupado por alguien que ya no existe, y el mensaje de rechazo no puede decirlo.

`domain` participa poco y de forma precisa: el agregado `User` gana un método que aplica los tres campos y devuelve cuáles mutaron, reutilizando los objetos de valor `Email` y `PersonName` que creó `RF-SP-024`. La normalización del correo no se escribe aquí: **ya vive en el constructor de `Email`**, y ese es exactamente el motivo por el que se hizo un objeto de valor.

## 2. Cambios de esquema

**Ninguno. Este requerimiento no cambia el esquema.**

| Objeto | De dónde viene | Para qué lo usa este requerimiento |
|---|---|---|
| `users` | `V18__create_users.sql` (`RF-SP-024`) | Las tres columnas editables, `updated_at` y `deleted_at` |
| `uq_users_email` | `V18` | `RN-SP-016`. Restricción **corriente y total**: decide el empate entre dos ediciones simultáneas |
| `ck_users_email_normalized` | `V18` | Garantiza que lo escrito quedó en minúsculas y recortado, incluso por un camino que no pase por el DTO |
| `ck_users_email_format` | `V18` | Comprobación de forma mínima. La validación buena está en el DTO |
| `Patchable<T>` | `RF-SP-004` | Distinguir campo ausente de campo enviado |
| `CanonicalUuidConverter` | `RF-SP-003` | Que un identificador no canónico sea `400` y no `404` |

**No se añade columna de versión.** `spec.md` §13 resolvió la concurrencia como «gana el último en escribir», igual que `RF-SP-004`, y la vía de recuperación es la auditoría, que conserva ambas ediciones. Si esa decisión se revirtiera, la columna debe añadirse a `V18` **antes del primer despliegue**.

**No se añade nada para verificar el correo.** `spec.md` §14, resolución 3, dejó la verificación fuera con su condición de disparo declarada, y este plan no la adelanta: el día que exista canal de correo, la verificación es un requerimiento con su tabla de solicitudes pendientes, no una columna añadida por iniciativa de este plan (§10).

**El correo anterior no se guarda en ninguna parte de `users`.** Queda en `audit_change_log` como el `before` del diff, y eso es todo lo que `CA-SP-355` necesita: liberarlo es simplemente no reservarlo, y la reserva no existe porque la restricción se evalúa sobre el valor **actual** de la fila.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `User` | **Modificado** | Método `updateIdentity(firstName, lastName, email)` que aplica lo recibido y **devuelve qué campos mutaron**. No conoce Spring ni JPA |
| `domain` | `UserChanges` | Nuevo | Resultado de `updateIdentity`: campos modificados con su valor anterior y el nuevo. Es lo que alimenta el diff y lo que decide si hubo evento |
| `domain` | `Email`, `PersonName` | Sin cambios | Objetos de valor de `RF-SP-024`. La normalización del correo **ya ocurre en su constructor** |
| `domain` | `UserRepository` | **Modificado** | Puerto de `RF-SP-024`. Añade `findActiveByIdForUpdate(UUID)` y `existsEmailOfOther(Email, UUID)` |
| `application` | `UpdateUserService` | Nuevo | Caso de uso. `@Transactional`, orquesta el orden de §4 y emite las auditorías que correspondan |
| `application` | `UpdateUserCommand` | Nuevo | Entrada del caso de uso. Los tres campos llegan como opcionales, sin tipos de HTTP |
| `application` | `UserChangeAuditor` | Sin cambios | Puerto de `RF-SP-024` hacia `shared/audit` |
| `application` | `UserSecurityAuditor` | Sin cambios | Puerto de `RF-SP-024`. Aquí emite `EMAIL_CHANGED` en lugar de `USER_CREATED` |
| `infrastructure` | `JpaUserRepository` | **Modificado** | Traduce la violación de `uq_users_email` **por nombre de restricción**, nunca por el texto del mensaje del driver |
| `infrastructure` | `UserEntity`, `UserJpaMapper` | Sin cambios | Definidos en `RF-SP-024` |
| `api` | `UserController` | **Modificado** | Añade `PATCH /api/v1/users/{id}` |
| `api` | `UpdateUserRequest` | Nuevo | DTO de entrada con Bean Validation y `Patchable<T>` en los tres campos |
| `api` | `UserResponse` | Sin cambios | DTO de `RF-SP-024`. Se reutiliza tal cual |
| `shared/api` | `Patchable<T>` | Sin cambios | Creado en `RF-SP-004`. Este es su **segundo** consumidor, y el primero fuera de los roles |

Dos decisiones de reparto:

**Se devuelve `UserResponse` y no `UserDetailResponse`.** El detalle de `RF-SP-026` arrastra la resolución de permisos efectivos y la membresía con su nivel a un camino de escritura que no las necesita. Es el mismo criterio con el que `RF-SP-004` §4 devolvió `RoleResponse` y no el detalle del rol, y aquí pesa más: la resolución de permisos consulta una caché que esta operación no toca.

**El diff lo produce el dominio.** `User.updateIdentity` devuelve qué campos mutaron, con su antes y su después ya normalizados. Un listener de JPA vería la entidad y no la intención: registraría la fila entera y no sabría distinguir una escritura idéntica de un cambio efectivo, y `CA-SP-225` y `CA-SP-226` fallarían las dos. Es literalmente el argumento de `RF-SP-004` §6, y se repite porque el atajo también está disponible aquí.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `PATCH` | `/api/v1/users/{id}` | Modifica el nombre, los apellidos y el correo de una persona |

Se usa `PATCH` y no `PUT` por el motivo de `RF-SP-004` §4: `PUT` obligaría a enviar el recurso completo, incluidos el nombre de usuario, el estado y los roles, que este requerimiento **no** puede modificar, y habría que decidir qué hacer si llegaran con valores distintos.

**Petición**

```json
{
  "firstName": "Juan Carlos",
  "email": "juan.perez@factech.co"
}
```

**Los tres estados de cada campo, y qué significa cada uno aquí:**

| Cuerpo | Significado |
|---|---|
| `{ "firstName": "X" }` | Cambia el nombre. **Los apellidos y el correo no se tocan** |
| `{ "email": "x@y.co" }` | Cambia el correo. El nombre y los apellidos no se tocan |
| `{ "firstName": null }` | **Rechazado** por `VAL-002`. Ningún campo admite vaciarse |
| `{ "firstName": "   " }` | Rechazado por `VAL-002` **tras recortar los extremos** |
| `{}` | Rechazado por `VAL-001` |

**Es la diferencia con `RF-SP-004`, y conviene no copiarla mal.** Allí el nulo explícito era una orden —«borra la descripción»— porque la columna admite nulo. Aquí las tres columnas son `NOT NULL` y `ck_users_names_not_blank` impide además el nombre en blanco: el nulo explícito no puede ser una orden, y **aceptarlo en silencio sería peor que rechazarlo**, porque produciría una violación de integridad traducida a `500` en lugar del `400` que corresponde. `Patchable<T>` sigue haciendo falta para distinguir el campo ausente del enviado; lo que cambia es qué se hace con su tercer estado.

**El nombre de usuario no está en el DTO** (`CA-SP-223`). Enviarlo devuelve `400` por propiedad desconocida, porque el deserializador rechaza campos que no declara. Sin ese rechazo, `username` se ignoraría en silencio y el criterio no comprobaría nada — es exactamente lo que `CA-SP-151` verifica en `RF-SP-004` para la clasificación de un rol. Lo mismo alcanza a `status`, `roles`, `membership` y `password`: cada uno tiene su requerimiento y ninguno entra por aquí.

**El correo se normaliza antes de compararse** —recorte y minúsculas—, y esa normalización **no se escribe en este requerimiento**: la hace el constructor de `Email` (`RF-SP-024` §3). La consecuencia práctica está en `spec.md` §13: enviar `" Juan.Perez@FACTECH.CO "` sobre quien ya tiene `juan.perez@factech.co` es un **cambio sin efecto**, no un conflicto consigo mismo ni una edición. Sin normalizar antes de comparar, esa petición produciría un evento de auditoría que documenta un cambio que no ocurrió.

**Respuesta `200`** — `UserResponse`, el mismo cuerpo que devuelve el alta, con los datos ya actualizados:

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d40",
  "username": "jperez",
  "email": "juan.perez@factech.co",
  "firstName": "Juan Carlos",
  "lastName": "Pérez",
  "status": "ACTIVO",
  "mustChangePassword": false,
  "roles": [
    { "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01", "code": "ASESOR", "name": "Asesor comercial" }
  ],
  "createdAt": "2026-08-20T14:32:11Z",
  "updatedAt": "2026-08-22T16:41:07Z"
}
```

- **Se devuelve el nombre de usuario aunque no pueda cambiar**, y es deliberado: es la mitad de `CA-SP-222`, que exige comprobar que sigue idéntico. Un campo que no se devuelve no puede verificarse en la misma respuesta.
- **`FA-001` devuelve `200` igual**, con la persona sin cambios y **sin dejar evento en ningún registro** (`CA-SP-226`).

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | Ningún campo modificable informado (`VAL-001`) | `VAL-001` | — |
| `400` | Nombre o apellidos nulos, vacíos o en blanco tras recortar (`VAL-002`) | `VAL-002` | El campo |
| `400` | Correo con formato inválido (`VAL-003`) | `VAL-003` | `email` |
| `400` | Longitud excedida (`VAL-005`) | `VAL-005` | El campo |
| `400` | Cuerpo con campo desconocido, incluido `username` | `VAL-001` | El campo sobrante |
| `400` | El identificador no es un UUID en forma canónica | `VAL-001` | `id` |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `users:update` | `AUTH-002` | — |
| `404` | No existe usuario vigente con ese identificador (`EX-002`) | `EX-002` | — |
| `409` | El correo ya está en uso (`EX-001`, `VAL-004`) | `RN-SP-016` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **El `409` lleva `RN-SP-016` como `error_code`** y no `EX-001`, por la convención de `development-guide.md` §7.2: el código es el identificador de la regla incumplida cuando existe una. Es la misma elección que hizo `RF-SP-024` §4 para el duplicado del alta, y mantenerla permite a un cliente tratar los dos casos igual.
- **El mensaje del `409` no dice de quién es el correo** (`CA-SP-224`), ni si esa persona existe todavía. `RN-SP-016` reserva el correo de los eliminados **para siempre**, de modo que el conflicto puede ser con alguien que ya no está; decirlo informaría de la existencia de una cuenta que la respuesta no debe revelar. Es el mismo silencio que `RF-SP-024` §4 impone en el alta, y por el mismo motivo.
- **No hay `422`.** Este cuerpo no referencia ninguna entidad de otra tabla: sus tres campos son escalares propios. La distinción de `development-guide.md` §7.1 entre `409` y `422` no tiene caso aquí.
- **No hay `403` por regla de negocio.** No existe un `RN-SEG-011` para las personas: `spec.md` §13 lo dice explícitamente —esa regla protege a los roles, no a los usuarios— y **el actor puede editarse a sí mismo**, porque corregir el propio apellido no concede ningún privilegio. Es una asimetría deliberada con `RF-SP-028` y `RF-SP-029`, donde `RN-SP-017` sí lo prohíbe: allí lo que está en juego es el propio acceso.
- El `403` de `AUTH-002` lo produce la capa de seguridad antes de entrar al caso de uso (§6). `CA-SP-229` se satisface ahí.

**Orden de verificación**

1. **Formato y obligatoriedad** (`VAL-001` a `VAL-003`, `VAL-005`), todas juntas y devueltas juntas en `errors`.
2. **Persona existente y no eliminada** (`EX-002`), cargada **con bloqueo de fila** (§7).
3. **Normalización** de los campos recibidos: recorte en los tres, minúsculas en el correo.
4. **Detección del cambio efectivo**, comparando contra el estado ya cargado.
5. **Unicidad del correo** (`EX-001`), **solo si el correo cambió**, por consulta para el mensaje y en última instancia por la restricción.
6. Escritura y auditoría.

Dos cosas de este orden importan. **La unicidad va después de detectar el cambio**, de modo que reenviar el correo actual no dispara ninguna consulta ni puede producir un conflicto consigo mismo. Y **la normalización va antes de comparar**, o `" JUAN.PEREZ@FACTECH.CO "` parecería un cambio y dejaría un evento de auditoría de algo que no cambió.

**Qué ocurre si la verificación previa faltara.** La operación no pasaría igualmente: `uq_users_email` la rechazaría en el `flush`, y el adaptador traduce esa violación —distinguiéndola **por el nombre de la restricción**, nunca por el texto del mensaje del driver— al mismo `409`. La verificación previa existe para el mensaje; la restricción, para la garantía. Ambas se prueban por separado (§11), con el mismo criterio que `RF-SP-023` §4.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `PATCH /api/v1/users/{id}` | `users:update` |

- El permiso **ya existe**: lo siembra `V3__seed_permissions.sql` (`RF-SP-010`).
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **Es el mismo permiso que `RF-SP-028`**, y esa unión tiene un coste que conviene dejar escrito: **quien puede corregir el apellido de alguien puede además cambiar su correo**, que es una vía de acceso, **y desactivarle la cuenta**. `spec.md` §14, resolución 4, aceptó lo primero al decidir que el cambio de correo se audita como evento de seguridad en lugar de exigir un permiso propio; lo segundo lo aceptó `RF-SP-028` §14, resolución 1, con el mismo razonamiento. Si algún día hace falta separar «corregir la ficha» de «tocar el acceso», será un permiso nuevo y un requerimiento nuevo, no una reorganización de estos.
- **No hay techo de privilegios que verificar**: editar el nombre o el correo no concede permisos, de modo que `RN-SEG-010` no interviene y la resolución del permiso **sí** puede usar la caché de `security.md` §4.5.
- **No hay filtrado por alcance de datos.** Quien tiene el permiso edita a cualquiera. Se revisa con **D-22**.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Edición efectiva | `audit_change_log` | `module = 'SP'`, `entity = 'users'`, `entity_id`, `action = 'UPDATE'`, `changes` con **solo** los campos que mutaron, cada uno con su antes y su después. El correo, **normalizado en ambos lados** |
| Edición efectiva **que cambió el correo** | `audit_security_log` | `event_type = 'EMAIL_CHANGED'`, `severity = 'ALTA'`, `outcome = 'SUCCESS'`, `target_user_id` de la persona editada, `detail` **sin** el correo anterior ni el nuevo (§ abajo) |
| Edición efectiva que **no** cambió el correo | — | **Ningún evento de seguridad.** Solo el de cambio |
| Edición sin cambio (`FA-001`) | — | **Ningún evento**, en ningún registro |
| Rechazo `409` por `EX-001` | `audit_error_log` | `resource = 'users'`, `operation = 'PATCH /api/v1/users/{id}'`, `error_code = 'RN-SP-016'`, `error_type = 'BUSINESS_RULE'`, `http_status = 409`, `severity = 'MEDIA'`, `message` saneado |
| Rechazo `404` por `EX-002` | — | **No se audita**: `architecture.md` §6.6.4 lo deja fuera y `ck_audit_error_log_status` lo impide en el esquema |
| Rechazo `400` de formato | — | **No se audita** (`architecture.md` §6.6.4) |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_deletion_log` | No aplica: la edición no elimina nada |

Cuatro decisiones:

**El evento de seguridad se emite solo cuando cambió el correo, y `CA-SP-356` lo verifica en los dos sentidos.** Es la asimetría que `spec.md` §14, resolución 4, fijó: el correo es una de las dos vías de acceso desde `RF-SP-024`, y modificar el de una cuenta ajena altera cómo esa persona entra en el sistema; el apellido no toca ninguna vía. `security.md` §8.1 incorporó `EMAIL_CHANGED` a su catálogo cerrado el 21-08-2026 y `RF-SP-014` §2 le dio literal el 22, de modo que **este requerimiento no necesita ampliar nada**: solo emitirlo.

**Un solo evento de seguridad aunque la petición cambie además el nombre.** Es una sola operación y el `detail` puede decir qué más se tocó. Dos eventos harían que cualquier recuento de cambios de correo contase de más, que es el mismo argumento con el que `RF-SP-024` §6 emitió uno y no dos.

**El `detail` del evento de seguridad no lleva ninguno de los dos correos.** Podría argumentarse lo contrario —es el dato que uno quiere ver al investigar—, y se descarta: `audit_security_log` es de **retención prolongada y no se purga sin decisión documentada** (Art. XV.8), mientras que el dato completo ya está en `audit_change_log` bajo el mismo identificador de correlación. Guardarlo dos veces multiplica la superficie de un dato personal sin responder ninguna pregunta nueva; quien investigue el evento tiene la correlación para llegar al diff.

**`changes` guarda el correo normalizado en el antes y en el después.** La auditoría refleja lo que había y lo que quedó en la tabla, no lo que llegó por HTTP; cómo llegó exactamente es asunto del `request_log` (Art. XV.3). Y `updated_at` **queda fuera** del diff, por ser consecuencia de la escritura y no un dato que alguien decidiera cambiar — mismo criterio que `RF-SP-022` §6 y `RF-SP-023` §6.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Bloqueo de la fila, `UPDATE` de los campos y su evento en `audit_change_log` | **La misma** (Art. V.14) |
| `audit_security_log` de `EMAIL_CHANGED` | **Independiente**, `REQUIRES_NEW`, **enganchada al commit** (`AFTER_COMMIT`) |
| `audit_error_log` de un rechazo o un fallo | **Independiente**, `REQUIRES_NEW` |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`@Transactional` vive sobre `UpdateUserService`, en `application`; nunca en el controlador ni en el repositorio.

**El evento de seguridad se engancha al commit y el de cambio no**, por la asimetría que `RF-SP-001` §7 fijó y `RF-SP-024` §7 repitió: el de cambio pertenece a la transacción de negocio, y el de seguridad se emite después para no documentar un cambio de correo que se revirtió. Un evento de seguridad que describe una apropiación de cuenta que no ocurrió es, en una investigación, un dato falso.

**La persona se carga con bloqueo de fila.** No lo exige ninguna regla, y se toma igual por dos motivos: dos ediciones simultáneas de la misma persona escribirían dos diffs, cada uno calculado contra el mismo estado anterior, y el segundo describiría una transición que no ocurrió —el mismo defecto que `RF-SP-022` §7 cerró con el bloqueo—; y entre comprobar que el correo está libre y escribirlo no debe poder colarse otra edición de **esta misma fila**. El bloqueo **no** protege contra dos personas distintas tomando el mismo correo a la vez: eso lo decide `uq_users_email`, y por eso la traducción de su violación no es opcional (`CA-SP-224` bajo concurrencia).

**«Gana el último en escribir» sigue siendo cierto y ahora es ordenado.** El bloqueo no introduce control optimista ni rechaza ninguna edición: serializa las dos, y la segunda calcula su diff contra el resultado de la primera. La auditoría conserva ambas, de modo que el cambio sobrescrito es reconstruible (`spec.md` §13).

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| **`RF-SP-034`** | **Obligación declarada:** tras esta operación, la persona autentica con el correo **nuevo** y no con el anterior, y su nombre de usuario sigue funcionando en ambos casos (`CA-SP-357`). El inicio de sesión resuelve el correo por igualdad directa sobre el valor almacenado, de modo que lo hereda sin cambios — pero es aquí donde se verifica de extremo a extremo, y por eso las pruebas de `CA-SP-227` y `CA-SP-357` necesitan que `RF-SP-034` exista (§11) |
| `RF-SP-024` | Comparte `UserController`, `UserResponse`, `Email`, `PersonName` y `uq_users_email`. Su §4 dejó dicho que el correo se persiste normalizado; esta operación es la única que puede cambiarlo después |
| `RF-SP-004` | Segundo consumidor de `Patchable<T>`, y el primero fuera de los roles. **Su tercer estado se usa aquí para rechazar, no para vaciar** (§4): quien lea los dos requerimientos debe ver la diferencia |
| `RF-SP-026` | Es la pantalla desde la que se llega a esta edición, y la que muestra el resultado |
| `RF-SP-040` | **Hereda y agrava el riesgo del correo sin verificar** (§10). Convertirá el correo en la llave de recuperación de la cuenta, y a partir de ahí un correo mal escrito no solo deja a alguien sin notificaciones: entrega su cuenta a un tercero. Es la condición de disparo de `spec.md` §14, resolución 3 |
| `RF-SP-011` | Su consulta responde también por la entidad `users` con `action = 'UPDATE'`. Ninguna adaptación: el registro es genérico por diseño |
| `RF-SP-014` | Recibe `EMAIL_CHANGED` con `target_user_id` poblado. El literal ya está en `ck_audit_security_log_event_type` desde su propia ampliación del 22-08-2026 |
| `security.md` | **Ninguna enmienda.** §8.1 ya incorporó el cambio de correo el 21-08-2026, al aprobarse esta especificación. Es el primer requerimiento del bloque que **no** amplía el catálogo cerrado, y conviene notarlo: la spec se aprobó con la enmienda ya hecha |
| `requirements/sp.md` | **Ninguna enmienda.** `RN-SP-016` ya distingue desde v1.6.0 que la reserva permanente alcanza solo a la eliminación y que al corregir el correo el anterior queda liberado |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Aceptar `null` como orden de vaciado, como en `RF-SP-004` | Las tres columnas son `NOT NULL` y `ck_users_names_not_blank` impide el nombre en blanco: la orden acabaría en una violación de integridad traducida a `500` en lugar del `400` que corresponde |
| Modelar los campos como `String` corriente, sin `Patchable<T>` | Hace indistinguible «no envié el campo» de «lo envié vacío», y `{"firstName":"X"}` borraría el correo. Es el defecto que `CA-SP-222` existe para detectar |
| Permitir editar el nombre de usuario | `RN-SP-016` lo declara inmutable: es la identidad con la que la persona aparece en la auditoría y con la que entra. Cambiarlo haría que la misma persona pareciera dos a lo largo del tiempo. `CA-SP-223` verifica que **no existe operación alguna** que lo haga |
| Exigir un permiso propio para cambiar el correo | `spec.md` §14, resolución 4, resolvió el riesgo por otra vía: evento de seguridad de severidad alta. Un permiso propio obligaría a concederlo a quien corrige fichas —que es quien recibe la petición— y no cambiaría quién puede hacerlo |
| Revocar las sesiones al cambiar el correo | `CA-SP-227` lo prohíbe explícitamente. El correo cambia por corrección, no por sospecha, y expulsar a alguien de sus sesiones por haberle corregido la dirección es desproporcionado. Quien quiera cortar el acceso tiene `RF-SP-028`, que sí las revoca |
| Marcar la cuenta para cambio de contraseña al cambiar el correo | Confundiría dos cosas distintas: la marca existe para acotar la ventana en que **otra persona conoce la credencial** (`security.md` §3.2), y aquí nadie la conoce |
| Exigir verificación del correo antes de aplicarlo | `spec.md` §14, resolución 3, lo dejó fuera: exige un canal de correo y un flujo de confirmación que ningún requerimiento cubre, y exigirlo dejaría este requerimiento bloqueado. El riesgo está acotado porque el nombre de usuario sigue funcionando |
| Guardar el correo anterior en una columna, «por si hay que revertir» | Sería un dato personal más, permanente, para un caso que la auditoría ya cubre con su diff. Y obligaría a decidir cuántos correos anteriores se guardan |
| Llevar los dos correos al `detail` del evento de seguridad | Duplica un dato personal en el registro de **retención más larga** del sistema, para responder algo que la correlación con `audit_change_log` ya responde |
| Calcular el diff con un listener de JPA | El listener ve la entidad, no la intención: registraría la fila entera y no distinguiría una escritura idéntica. `CA-SP-225` y `CA-SP-226` fallarían las dos |
| Detectar el cambio efectivo consultando la base de datos | El estado anterior ya está cargado en memoria; ir a buscarlo añade una consulta y deja la decisión fuera del dominio, donde no puede probarse sin PostgreSQL (Art. VI.3) |
| No bloquear la fila y confiar en «gana el último» | Dos ediciones simultáneas producirían dos diffs calculados contra el mismo estado anterior, y el segundo describiría una transición que no ocurrió. El bloqueo no cambia quién gana: ordena a los dos |
| Añadir bloqueo optimista con columna de versión | `spec.md` §13 resolvió la concurrencia como «gana el último». Introducirlo por iniciativa del plan contradiría una decisión tomada y añadiría un código de error que nadie pidió |
| Devolver `UserDetailResponse` | Arrastraría la resolución de permisos efectivos y la membresía con su nivel a un camino de escritura que no las necesita |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| **El correo se cambia por error o con mala intención y no hay verificación** | **Alto** | Es el riesgo declarado por `spec.md` §14, resolución 3, y este plan **no lo cierra**. Hoy está acotado: el nombre de usuario sigue funcionando y otro actor puede corregirlo. **Condición de disparo:** el día que exista `RF-SP-040`, el correo pasa a ser la llave de recuperación y la verificación deja de ser opcional. Debe revisarse **antes** de aprobar el plan de aquel requerimiento, no después |
| `Patchable<T>` se copia de `RF-SP-004` con su semántica de vaciado y `{"firstName": null}` acaba en `500` | Medio | Prueba de API explícita para los cinco cuerpos de §4. Es el defecto más probable de este requerimiento, precisamente porque el componente se reutiliza y su comportamiento aquí es otro |
| La violación de `uq_users_email` no se traduce y sale como `500` | Medio | El adaptador la distingue **por nombre de restricción**, nunca por el texto del mensaje del driver, que cambia entre versiones. Prueba propia en §11, forzando el camino que salta la verificación previa |
| El mensaje del `409` revela que el correo pertenece a una cuenta eliminada | Medio | `CA-SP-224` lo verifica sobre un correo vigente; §11 añade el caso del eliminado, que debe producir **el mismo cuerpo**. Sin esa segunda prueba, el silencio depende de que nadie mejore el mensaje |
| Se emite el evento de seguridad también al cambiar el nombre, por copiar la fila de la tabla | Medio | `CA-SP-356` verifica las dos mitades: que está cuando cambió el correo y que **no está** cuando solo cambió el nombre |
| Alguien revoca las sesiones «por seguridad» al cambiar el correo | Medio | `CA-SP-227` lo verifica. La intuición empuja en esa dirección y por eso el criterio existe |
| Un cambio se pierde por edición concurrente | Bajo | Aceptado, con el bloqueo de fila que lo ordena (§7). La auditoría conserva ambas ediciones |
| El correo liberado lo toma otra persona y la auditoría antigua parece apuntar a la equivocada | Bajo | No ocurre: la auditoría referencia a las personas por identificador y por nombre de usuario, **nunca por correo**. Es la razón por la que `RN-SP-016` puede liberarlo (`spec.md` §14, resolución 2) |

## 11. Estrategia de prueba

Niveles: **Unitaria** (dominio, sin Spring ni base de datos), **Integración** (Testcontainers sobre PostgreSQL real, con `V18` aplicada) y **API** (extremo a extremo por HTTP, con autenticación).

**Dos criterios necesitan `RF-SP-034`.** `CA-SP-227` y `CA-SP-357` se verifican iniciando sesión antes y después de la edición, y eso exige que el inicio de sesión exista. Hasta entonces se cubren en su parte comprobable —que los refresh tokens de la persona **no** quedan revocados, sobre la tabla— y se completan en el mismo Pull Request en que `RF-SP-034` se integre. Está declarado en §8 y en `tasks.md`.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-221` | Unitaria + Integración + API | Nombre, apellidos y correo quedan modificados; enviar solo uno deja los otros dos intactos |
| `CA-SP-222` | Integración + API | Tras la edición, `username`, `status`, `password_hash`, los roles y la membresía son **idénticos** a los anteriores |
| `CA-SP-223` | API | Un cuerpo con `username` devuelve `400` por campo desconocido, **no se ignora**; y no existe ningún otro endpoint que lo modifique |
| `CA-SP-224` | Integración + API | Un correo ya usado por otra persona devuelve `409` con `RN-SP-016`, y el cuerpo **no nombra** a esa persona |
| `CA-SP-225` | Unitaria + Integración | `User.updateIdentity` devuelve solo los campos mutados; la fila de `audit_change_log` contiene **solo** esos, con su antes y su después |
| `CA-SP-226` | Integración | Enviar los valores actuales —incluido el correo con otra caja y espacios— no genera fila en `audit_change_log` ni en `audit_security_log`, y `updated_at` no cambia |
| `CA-SP-355` | Integración | Tras cambiar el correo de A, **otra persona puede tomar el anterior**, tanto en un alta como en una edición |
| `CA-SP-356` | Integración | Cambiar el correo deja fila en `audit_security_log` con `EMAIL_CHANGED`, `severity = 'ALTA'` y `target_user_id`; cambiar solo el nombre **no deja ninguna** |
| `CA-SP-357` | Integración + API | Tras cambiar el correo, la persona autentica con el nuevo y **no** con el anterior; su nombre de usuario funciona en ambos momentos. **Requiere `RF-SP-034`** |
| `CA-SP-227` | Integración | Los refresh tokens de la persona **siguen vigentes** tras el cambio de correo. **Requiere `RF-SP-034`** para su mitad de extremo a extremo |
| `CA-SP-228` | Integración + API | Una persona con `deleted_at` no nulo devuelve `404` con `EX-002`, con el mismo cuerpo que un identificador inexistente |
| `CA-SP-229` | API | Un actor autenticado sin `users:update` recibe `403`, la persona no cambia, y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| **Los cinco cuerpos de §4** | API | `{"firstName":"X"}` no toca el correo; `{"firstName":null}` devuelve `400` con `VAL-002`; `{"firstName":"   "}` también; `{}` devuelve `400` con `VAL-001`; un cuerpo con `username` devuelve `400` por campo desconocido |
| Correo igual al actual con otra caja o espacios | Integración | `" JUAN.PEREZ@FACTECH.CO "` sobre quien ya lo tiene es **cambio sin efecto**: `200`, sin evento y sin conflicto consigo mismo |
| **Correo de una persona eliminada** | Integración + API | Devuelve `409` con `RN-SP-016` y **el mismo cuerpo** que el de una vigente. `RN-SP-016` lo reserva para siempre, y la respuesta no puede delatarlo |
| Traducción de la restricción sin verificación previa | Integración | Forzando el camino que salta la comprobación, la violación de `uq_users_email` se traduce a `409` con `RN-SP-016`, **nunca a `500`**, y se distingue por nombre de restricción |
| **Edición concurrente de la misma persona** | Integración | Dos transacciones reales editando el mismo campo: ambas devuelven `200`, la fila queda con el valor de la segunda, y `audit_change_log` conserva **dos** eventos cuyos diffs encadenan —el `before` del segundo es el `after` del primero— |
| Dos ediciones concurrentes hacia el **mismo correo** sobre personas distintas | Integración | Una `200` y una `409` con `RN-SP-016`. **Nunca `500`**: es el caso que el bloqueo de fila no cubre y la restricción sí |
| El actor se edita a sí mismo | API | Se admite y devuelve `200`. No hay regla equivalente a `RN-SEG-011` para las personas |
| Persona inactiva o bloqueada | API | Se edita con normalidad: corregir el nombre de alguien no depende de que pueda entrar |
| Longitud en el límite | Integración | 100 caracteres de nombre y 255 de correo se aceptan; uno más devuelve `400` con `VAL-005` |
| `INSERT`/`UPDATE` directo con correo sin normalizar | Integración | `ck_users_email_normalized` lo rechaza. La defensa no depende de que la petición pase por el DTO |
| Identificador no canónico | API | `1-1-1-1-1` devuelve `400` con `VAL-001` y campo `id`, no `404` |
| Número de sentencias por petición | Integración | El `SELECT … FOR UPDATE`, la comprobación de unicidad **solo si el correo cambió**, el `UPDATE` y la inserción del evento; **ninguna escritura** cuando la petición cae en `FA-001` |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento, y la prueba de ausencia de cascadas de `RF-SP-012` §11 se ejecuta sobre el esquema completo.
