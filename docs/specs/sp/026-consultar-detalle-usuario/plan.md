# PLAN — `RF-SP-026` Consultar detalle de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-026` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide una sola cosa importante, y las demás se derivan de ella: **de dónde salen los permisos efectivos**.

---

## 1. Enfoque

Es el gemelo de [`RF-SP-003`](../003-consultar-detalle-rol/plan.md) del otro lado de la asignación, y hereda su forma: proyecciones en lugar del agregado, dos sentencias de lectura, `404` para el eliminado, `400` para el identificador no canónico. Lo que allí está argumentado no se repite.

Lo propio es el campo que da sentido al requerimiento. `spec.md` §2 lo dice con precisión: **la unión de permisos no se puede deducir mirando los roles por separado**, y es justamente lo que hay que saber antes de retirarle uno a alguien. La decisión de este plan es de dónde sale esa unión, y hay dos fuentes posibles que devuelven casi siempre lo mismo:

- **El mismo componente que autoriza**, alimentado por la caché de `rol → permisos` de `security.md` §4.5.
- **Una consulta propia** que recorra `user_roles ⋈ roles ⋈ role_permissions ⋈ permissions`.

Se elige la primera, y el motivo no es el coste sino la coherencia: **lo que esta pantalla muestra tiene que ser exactamente lo que el sistema concede**. Con una consulta propia habría dos implementaciones de `RN-SEG-009` —la que autoriza y la que informa— y el día que divergieran, la pantalla desde la que se decide retirar un rol estaría describiendo un sistema distinto del que atiende las peticiones. §4 desarrolla la elección y su contrapartida.

`domain` no participa. Las dos reglas aplicables, `RN-SEG-009` y `RN-SEG-002`, ya viven en el componente de resolución que este requerimiento **consume**; implementarlas otra vez aquí sería exactamente lo que la decisión anterior evita.

## 2. Cambios de esquema

**Ninguno. Este requerimiento no cambia el esquema.**

Todo lo que necesita ya existe o lo crea otro requerimiento:

| Objeto | De dónde viene | Para qué lo usa este requerimiento |
|---|---|---|
| `users` | `V18__create_users.sql` (`RF-SP-024`) | Fila de la persona, incluida `deleted_at` |
| `user_roles` | `V19__create_user_roles.sql` (`RF-SP-024`) | Roles asignados, leídos por el prefijo `user_id` de su clave primaria |
| `user_memberships`, `memberships` | `V20` (`RF-SP-024`) y `V13` (`RF-SP-016`) | Membresía vigente y su nivel |
| `roles`, `role_permissions`, `permissions` | `V5`, `V6` (`RF-SP-001`) y `V2` (`RF-SP-010`) | Estado de cada rol y permisos que declara |
| `CanonicalUuidConverter` | `RF-SP-003` | Que un identificador no canónico sea `400` y no `404` |

**No se crea ningún índice.** Las dos consultas acceden por clave primaria o por el prefijo de una clave primaria compuesta: `users` por `id`, y `user_roles` por `user_id`, que es la primera columna de `pk_user_roles`. `ix_user_roles_role_id`, que declara `RF-SP-030`, sirve la consulta contraria —quién porta un rol— y aquí no interviene.

### Dependencia de esquema con `RF-SP-034`

`spec.md` §6.2 exige devolver **el último inicio de sesión** y **el momento en que expira el bloqueo**. Esas dos columnas no existen todavía:

| Columna | Quién la crea | Quién la escribe |
|---|---|---|
| `last_login_at` | `RF-SP-034` | `RF-SP-034`, en cada inicio de sesión |
| `locked_until` | `RF-SP-034` | `RF-SP-034` al bloquear por intentos fallidos; `RF-SP-028` la limpia al reactivar |

`RF-SP-024` §2 las dejó fuera de `V18` de forma deliberada —«una columna disponible antes de que exista la regla que la gobierna se acaba usando por un camino que nadie diseñó»— y ese criterio se mantiene: **no se crean aquí**. Este requerimiento solo las lee.

La consecuencia es una **precedencia de implementación**, no un cambio de esquema: `RF-SP-034` debe implementarse antes que este requerimiento, igual que `RF-SP-024` y `RF-SP-030` se adelantaron por delante de `RF-SP-003`. Se declara en §8 y se enmienda `requirements/sp.md` §6.1 para recogerlo, junto con el resto de precedencias del bloque de usuarios.

**Quién crea las tres columnas de control de acceso queda fijado aquí**, porque hasta ahora estaba repartido: `security.md` §9 y `requirements/sp.md` §10.10 dicen que `failed_attempts`, `locked_until` y `last_login_at` «las crean `RF-SP-034` y `RF-SP-028`» sin repartirlas. **Las tres son de `RF-SP-034`**, que es quien las escribe todas y quien se implementa primero; `RF-SP-028` las lee y las limpia. La enmienda va en §8.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: las reglas aplicables viven en el componente de resolución que se consume (§1) |
| `application` | `GetUserDetailService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)` |
| `application` | `UserDetailQueryRepository` | Nuevo | Puerto de consulta: `findById(UUID)` devuelve `Optional<UserDetail>`; `findAssignedRoles(UUID)` devuelve los roles con su estado |
| `application` | `UserDetail` | Nuevo | Modelo de lectura: escalares de la persona, membresía embebida y el contexto de acceso |
| `application` | `AssignedRoleItem` | **Nuevo, amplía** | `UserRoleItem` de `RF-SP-025` **más el estado del rol**. Es lo que hace visible por qué un rol asignado no concede nada |
| `application` | `EffectivePermissionResolver` | Nuevo | Puerto hacia el componente que resuelve `rol → permisos`. Es el mismo que usa la autorización (§4) |
| `infrastructure` | `JpaUserDetailQueryRepository` | Nuevo | Adaptador. Construye las dos proyecciones con la API de criterios |
| `infrastructure` | `UserEntity`, `UserRoleEntity`, `UserMembershipEntity`, `RoleEntity` | Sin cambios | Solo como metamodelo; no se instancian |
| `shared/security` | `RolePermissionCache` | **Modificado** | Publica la resolución que hasta ahora solo consumía el filtro de autorización (§4) |
| `api` | `UserController` | **Modificado** | Añade `GET /api/v1/users/{id}` |
| `api` | `UserDetailResponse` | Nuevo | DTO de salida del detalle |
| `api` | `RoleSummaryResponse` | Sin cambios | Definido en `RF-SP-001`. Se embebe dentro de la forma con estado |
| `api` | `MembershipSummaryResponse` | **Modificado** | Definido en `RF-SP-025`. Gana `level`, que el listado no devuelve |
| `shared/api` | `CanonicalUuidConverter` | Sin cambios | Creado en `RF-SP-003` |

Tres decisiones de reparto:

**`UserDetailResponse` no es `UserResponse` ni `UserListItemResponse`.** El primero lo devuelve el alta y lleva `mustChangePassword`, que aquí no se devuelve —es un dato de la credencial y `spec.md` §4.2 la excluye entera—; el segundo no lleva permisos efectivos ni contexto de acceso. Es el mismo criterio con el que `RF-SP-003` separó `RoleDetailResponse` de `RoleResponse`: un DTO compartido obligaría a que el listado arrastrara campos que `CA-SP-209` le prohíbe llenar.

**`AssignedRoleItem` amplía `UserRoleItem` en lugar de sustituirlo.** El listado devuelve el rol sin su estado (`RF-SP-025` §4) y el detalle lo devuelve con él, porque aquí es donde se decide retirarlo y `FA-002` exige poder explicar por qué alguien con roles no puede hacer nada. Que el detalle sea un superconjunto del listado, y no otra forma, es lo que permite a la interfaz pintar la misma tarjeta de rol en las dos pantallas.

**El resolutor de permisos se publica desde `shared/security`, no se reimplementa.** Hoy la caché de `security.md` §4.5 vive allí y solo la consume el filtro de autorización. Este requerimiento la convierte en un componente con dos clientes, y el puerto `EffectivePermissionResolver` es lo que impide que `application` conozca la caché.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/users/{id}` | Detalle de una persona con sus roles, sus permisos efectivos, su membresía y el contexto de su acceso |

**Petición**

```
GET /api/v1/users/018f3a2b-7c41-7000-9a3d-1f2e5b8c9d40
```

Sin cuerpo y sin parámetros de consulta. No hay `?include=…`: la especificación define un único detalle, y ofrecer variantes multiplicaría los contratos que hay que probar. Es la misma decisión de `RF-SP-003` §4.

**Respuesta `200`**

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d40",
  "username": "jperez",
  "email": "juan.perez@factech.co",
  "firstName": "Juan",
  "lastName": "Pérez",
  "status": "ACTIVO",
  "roles": [
    { "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01", "code": "ASESOR", "name": "Asesor comercial", "status": "ACTIVO" },
    { "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d02", "code": "SOPORTE", "name": "Soporte", "status": "INACTIVO" }
  ],
  "effectivePermissions": ["users:read", "roles:read"],
  "membership": {
    "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d05",
    "code": "ORO",
    "name": "Membresía Oro",
    "level": 2,
    "endsAt": "2027-01-31T23:59:59Z",
    "current": true
  },
  "lastLoginAt": "2026-08-22T09:14:03Z",
  "lockedUntil": null,
  "createdAt": "2026-08-20T14:32:11Z",
  "updatedAt": "2026-08-21T11:02:44Z"
}
```

Decisiones del contrato:

- **`roles` lleva el estado de cada uno**, y esa es la mitad de `FA-002`: la otra mitad es que `effectivePermissions` llegue vacía. Las dos juntas son lo que explica por qué una persona con roles no puede hacer nada.
- **`effectivePermissions` es una lista de códigos, ordenada y sin duplicados** (`CA-SP-213`). No se devuelven los identificadores de los permisos ni su descripción: la pregunta es «qué puede hacer», y `RF-SP-015` responde qué significa cada uno. El orden es alfabético por código, para que la respuesta sea estable entre llamadas y comparable entre personas.
- **No se pagina.** `architecture.md` §7.4 exige paginar «las colecciones», y aquí se aparta de forma consciente por el mismo argumento de `RF-SP-003` §4: los permisos efectivos de una persona son decenas, no constituyen un recurso navegable y paginarlos obligaría a dos peticiones para responder la única pregunta del requerimiento.
- **`membership` lleva `level`**, que el listado no devuelve. Es el dato con el que los módulos de academia y productos deciden qué contenido ofrecer, y esta es la pantalla donde se comprueba.
- **`membership` no es nula cuando está vencida**, con la misma semántica que `RF-SP-025` §4: `current` dice si concede nivel y `endsAt` hasta cuándo lo hizo. Vencer no es lo mismo que no tener (`RN-SP-014`).
- **`lockedUntil` es nulo en dos casos distintos y eso es información**: la cuenta no está bloqueada, o lo está **por decisión de un actor** y por tanto sin expiración (`RF-SP-028`). El estado desambigua: `BLOQUEADO` con `lockedUntil` nulo es un bloqueo manual, que no se levanta solo. `CA-SP-217` se satisface en el otro caso.
- **No existe `failedAttempts`** (`CA-SP-346`). `spec.md` §14, resolución 2, lo resolvió: diría a cualquiera con `users:read` cuántos intentos le quedan a una cuenta antes de bloquearse. La columna existe desde `RF-SP-034` y **la proyección no la selecciona**, que es lo único que hace verificable el criterio.
- **No existe ningún dato de la credencial**, ni `mustChangePassword`, ni la antigüedad del hash (`CA-SP-218`). La marca de cambio obligatorio sí la devuelve `RF-SP-039` —a su titular, que es quien tiene que actuar— y no aquí.
- **No existe `deletedAt`**: un usuario eliminado devuelve `404`, de modo que el campo sería siempre nulo. Es el criterio de `RF-SP-003` §4.
- **No existe el historial de sesiones ni la lista de sesiones abiertas** (`spec.md` §4.2). Quién entró y cuándo es `RF-SP-014`.
- **No existe `createdBy`**: el actor no vive en la tabla de negocio (Art. V.7). Quién registró o editó a esta persona se responde con `RF-SP-011`.
- **No existe el superior comercial ni el equipo.** Esa es la estructura de `RF-SP-042`, con su propia paginación. Devolver aquí el superior habría sido barato y habría creado una segunda fuente del mismo dato.

### De dónde salen los permisos efectivos

Del **mismo componente que autoriza**. El servicio lee los roles asignados (segunda sentencia), se queda con los que están `ACTIVO` y pide a `EffectivePermissionResolver` la unión de sus permisos. Ese resolutor es el que alimenta la caché de `security.md` §4.5, invalidada ante cualquier cambio en `role_permissions` o en el estado de un rol.

Cuatro consecuencias, y conviene aceptarlas de forma explícita:

1. **La respuesta no puede contradecir a la autorización.** Si el detalle dice que alguien tiene `users:delete`, el filtro que atiende su próxima petición dirá lo mismo, porque ambos preguntan al mismo sitio. Con una consulta propia esa garantía no existe: dos implementaciones de `RN-SEG-009` que hoy coinciden pueden dejar de hacerlo con un cambio en cualquiera de las dos.
2. **`RN-SEG-002` se aplica una sola vez y en un solo sitio.** El filtrado por rol activo lo hace este servicio antes de preguntar, con el estado que la segunda sentencia acaba de leer de la base de datos —no con el que la caché pudiera recordar—. Es lo que hace verificable `CA-SP-214` sin depender del contenido de la caché.
3. **Un rol eliminado lógicamente no aparece ni concede.** La segunda sentencia lo excluye con `r.deleted_at IS NULL`, de modo que ni entra en `roles` ni llega al resolutor. Es la misma semántica que `RF-SP-025` §4 fija para la lista del listado.
4. **La contrapartida es la obsolescencia de la caché, y está acotada por construcción.** Una entrada obsoleta haría que el detalle mostrara permisos que ya no corresponden — pero exactamente durante el mismo tiempo en que la autorización los seguiría concediendo. **El detalle no puede mentir más de lo que miente el sistema.** Con una consulta propia el detalle sería más fresco que la realidad, que suena mejor y es peor: mostraría un permiso ya retirado como ausente mientras el filtro lo sigue admitiendo, y quien lo mirara concluiría que el retiro surtió efecto. El riesgo real, que es el de varias instancias, está en §10 y es el mismo que `RF-SP-007` §10 aceptó.

**Cuántas sentencias cuesta el detalle.** Dos contra PostgreSQL, y ninguna llamada fuera del proceso:

```sql
-- 1: la persona, su membresía y su contexto de acceso
SELECT u.id, u.username, u.email, u.first_name, u.last_name, u.status,
       u.last_login_at, u.locked_until, u.created_at, u.updated_at,
       m.id, m.code, m.name, m.level, um.ends_at
  FROM users u
  LEFT JOIN user_memberships um ON um.user_id = u.id
  LEFT JOIN memberships m       ON m.id = um.membership_id
 WHERE u.id = :id AND u.deleted_at IS NULL;

-- 2: los roles asignados, con su estado
SELECT r.id, r.code, r.name, r.status
  FROM user_roles ur
  JOIN roles r ON r.id = ur.role_id AND r.deleted_at IS NULL
 WHERE ur.user_id = :id
 ORDER BY r.code;
```

- **El orden importa.** Primero la persona: si no existe o está eliminada, se devuelve `404` **sin ejecutar la segunda sentencia** y sin preguntar por permiso alguno.
- **La membresía va por `LEFT JOIN` en la primera sentencia**, porque es a lo sumo una fila —lo garantiza `pk_user_memberships`— y traerla aparte costaría una tercera sentencia para un dato que el `JOIN` resuelve gratis. Los roles no pueden ir ahí: dos colecciones en la misma sentencia producen el producto cartesiano que `RF-SP-003` §4 describe, y aquí la segunda colección sería la de permisos.
- **No hay `N+1` posible**, por el mismo argumento de `RF-SP-002` y `RF-SP-003`: no se carga `UserEntity`, de modo que no hay asociación perezosa que un mapeador, un `toString` o la serialización puedan recorrer.
- **La resolución de permisos no añade sentencias en el caso común**, porque la caché ya tiene la entrada de cada rol; en un fallo de caché añade las que ese componente necesite, que son suyas y no de este requerimiento.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | El identificador no es un UUID en forma canónica (`VAL-001`) | `VAL-001` | `id` |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `users:read` | `AUTH-002` | — |
| `404` | No existe usuario con ese identificador, o está eliminado (`EX-001`) | `EX-001` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **Un usuario eliminado devuelve el mismo `404` y el mismo mensaje que uno inexistente**, sin ninguna pista de que existió (`CA-SP-219`). Reconstruir qué era corresponde a `RF-SP-012` sobre `audit_deletion_log`.
- **`VAL-002` no produce código propio**: enuncia como validación lo mismo que `EX-001`. Un solo hecho, un solo código.
- **El identificador malformado es `400` y no `404`** por el mecanismo que `RF-SP-003` §4 desarrolla y que aquí solo se reutiliza: `CanonicalUuidConverter` exige los treinta y seis caracteres canónicos antes de delegar en `UUID.fromString`, cuya permisividad convertiría `1-1-1-1-1` en un identificador válido que no existe.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso (§6). `CA-SP-220` se satisface ahí.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/users/{id}` | `users:read` |

- El permiso **ya existe**: lo siembra `V3__seed_permissions.sql` (`RF-SP-010`).
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **Es el mismo permiso que `RF-SP-025` y `RF-SP-042`**, por el criterio de `RF-SP-003` §5: detalle y listado responden la misma pregunta con distinto grano.
- **El actor puede consultarse a sí mismo sin regla especial, siempre que tenga el permiso** (`spec.md` §13). Este endpoint **no** cambia de comportamiento según a quién apunte el identificador, y esa uniformidad es exactamente lo que `RF-SP-039` existe para no romper: quien no tiene `users:read` no ve aquí ni sus propios datos, y tiene su propio endpoint.
- **La resolución del permiso de acceso sí puede usar la caché** de `security.md` §4.5: aquí se decide acceso, no una concesión. Es distinto de los permisos efectivos **que se devuelven**, que también salen de la caché pero por el motivo de §4 —coherencia con la autorización—, no por coste.
- **No hay filtrado por alcance de datos.** Quien tiene el permiso consulta a cualquiera, y `spec.md` §5 no declara ninguna regla que lo acote. Cuando se resuelva **D-22**, este endpoint se revisa junto con `RF-SP-025`.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita** |
| Rechazo `400` por `VAL-001` | — | **No se audita**: es validación de formato (`architecture.md` §6.6.4) |
| Rechazo `404` por `EX-001` | — | **No se audita**. Ver abajo |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'users'`, `operation = 'GET /api/v1/users/{id}'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log`, `audit_deletion_log` | No aplican: la consulta no altera el estado (`spec.md` §7) |

Dos decisiones:

- **Una consulta exitosa no produce evento**, aunque esta sea la pantalla que expone el alcance completo de una persona en un solo sitio. El catálogo de `security.md` §8.1 es cerrado y no incluye la lectura de usuarios; la trazabilidad de quién consultó qué la aporta `request_log`. Es la misma conclusión de `RF-SP-002` §6, `RF-SP-003` §6 y `RF-SP-025` §6, y conviene que las cuatro digan lo mismo: **el único registro de lectura que el catálogo contempla es el de la propia auditoría de seguridad**, y `RF-SP-014` lo justificó por lo que se lee allí, no por su sensibilidad.
- **El `404` tampoco se audita.** No hay regla incumplida ni cambio de estado: es un identificador que no encuentra fila, y en un endpoint de consulta eso es navegación. Además `ck_audit_error_log_status` lo rechazaría en el esquema.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Las dos sentencias de lectura | **Una sola**, `@Transactional(readOnly = true)` sobre `GetUserDetailService` |
| Resolución de permisos efectivos | **Dentro** de la misma transacción, sin llamadas fuera del proceso |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`readOnly = true` no es decorativo, por el motivo de `RF-SP-002` §7.

**La vigencia de la membresía se evalúa con `now()` de la base de datos**, igual que en `RF-SP-025` §7 y por el mismo motivo: un solo reloj, o el listado y el detalle darían respuestas distintas sobre la misma persona en el mismo segundo.

**La resolución de permisos ocurre dentro de la transacción y no la necesita.** No consulta la base de datos en el caso común y no escribe nunca, de modo que no puede marcarla para revertir. Si un fallo de caché la obligara a consultar, esa consulta es de solo lectura y comparte la transacción sin efecto adverso. Es la simplificación que `RF-SP-003` §7 consiguió al desaparecer la llamada externa, y aquí se hereda ya resuelta.

Bajo `READ COMMITTED` cada sentencia toma su propia instantánea: la lista de roles puede corresponder a un instante distinto al de la persona. Se acepta con el razonamiento de `RF-SP-002` §7.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| **`RF-SP-034`** | **Precedencia declarada:** debe implementarse **antes** que este requerimiento, porque crea `last_login_at` y `locked_until`, que `spec.md` §6.2 exige devolver. Y **queda fijado que las tres columnas de control de acceso —`failed_attempts`, `locked_until` y `last_login_at`— son suyas**, no repartidas con `RF-SP-028`: es quien las escribe todas y quien se implementa primero |
| **`shared/security`** | La caché de `security.md` §4.5 pasa de tener un cliente a tener dos: el filtro de autorización y esta consulta. Se publica tras el puerto `EffectivePermissionResolver`, y **cualquier cambio en cómo resuelve alcanza ahora también a lo que esta pantalla muestra** |
| `RF-SP-025` | Hereda `MembershipSummaryResponse` —que aquí gana `level`— y `UserRoleItem`, que aquí se amplía con el estado del rol. La forma del listado es un subconjunto de la del detalle, no otra forma |
| `RF-SP-024` | Comparte `UserController`. Su cabecera `Location` **resuelve gracias a este requerimiento**, tal como aquel plan anunció en §4 |
| `RF-SP-031` | Es el consumidor natural de esta consulta: `spec.md` §13 de aquel requerimiento dice que la lista de permisos efectivos ya resuelta «es la única que responde qué pierde la persona en realidad» antes de retirarle un rol |
| `RF-SP-039` | Devuelve un **subconjunto** de esta respuesta al propio actor y **reutiliza el mismo resolutor**. Lo que no puede reutilizar es el endpoint, porque su autorización es otra |
| `RF-SP-003` | Es su simétrico. Aquel responde el alcance de un rol; este, el de una persona. Comparten `CanonicalUuidConverter` y el criterio de `404` |
| `requirements/sp.md` | **§6.1 gana las precedencias del bloque de usuarios** (§2 de este plan y las de `RF-SP-028` y `RF-SP-029`), y **§10.10 precisa el reparto de las tres columnas de control de acceso**. Enmiendas de este plan (Art. I.7) |
| `security.md` | **§9 precisa lo mismo**: las tres columnas las crea `RF-SP-034` |
| **D-22** | Se revisa junto con `RF-SP-025` cuando se cierre |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Resolver los permisos efectivos con una consulta propia sobre `role_permissions` | Habría dos implementaciones de `RN-SEG-009`: la que autoriza y la que informa. El día que divergieran, la pantalla desde la que se decide retirar un rol describiría un sistema distinto del que atiende las peticiones. Y sería **más fresca que la realidad**, que suena mejor y es peor (§4) |
| Devolver los permisos efectivos **sin** resolver, dejando que el cliente una los de cada rol | `spec.md` §14, resolución 1, lo descartó: un permiso que dos roles conceden sobrevive al retiro de uno, y esa deducción es justo la que nadie hace bien a mano. Además obligaría a devolver los permisos de cada rol, multiplicando la respuesta |
| Devolver los permisos efectivos con su nombre y descripción, no solo el código | La pregunta es qué puede hacer la persona; qué significa cada permiso lo responde `RF-SP-015`. Añadirlo multiplicaría el tamaño de la respuesta por un dato que la interfaz puede cachear una vez |
| Paginar `effectivePermissions` | Convertiría la única pregunta del requerimiento en dos peticiones, para una lista de decenas de elementos. Mismo argumento que `RF-SP-003` §4 con los permisos de un rol |
| Devolver `failedAttempts` | `spec.md` §14, resolución 2: diría a cualquiera con `users:read` cuántos intentos le quedan a una cuenta. `CA-SP-346` verifica la ausencia |
| Devolver `mustChangePassword` | Es un dato de la credencial y `spec.md` §4.2 la excluye entera. Quien tiene que actuar sobre esa marca es el titular, y `RF-SP-039` se la devuelve a él |
| Devolver también el superior comercial y el equipo | Crearía una segunda fuente del mismo dato, que `RF-SP-042` ya devuelve **paginado** porque un equipo puede ser grande. Aquí no habría dónde paginarlo |
| Devolver el detalle de un usuario eliminado con un parámetro `includeDeleted` | Añadiría una rama permanente al endpoint para responder algo que `RF-SP-012` ya conserva. Mismo criterio que `RF-SP-003` §9 |
| Reutilizar `UserResponse` de `RF-SP-024` añadiéndole campos | Arrastraría `mustChangePassword` a esta respuesta y obligaría al alta a devolver permisos efectivos que en ese momento son vacíos o irrelevantes |
| Traer roles y permisos en la misma sentencia | Producto cartesiano entre dos colecciones: filas multiplicadas para deduplicar después en memoria |
| Cargar el agregado `User` y mapearlo | Reutiliza código de `RF-SP-024` a cambio de exponer la credencial al mapeador y de dejar el `N+1` de los roles a un `JOIN FETCH` que hay que acordarse de escribir |
| Crear aquí `last_login_at` y `locked_until` para no depender de `RF-SP-034` | Serían dos columnas que este requerimiento **lee y nunca escribe**, gobernadas por reglas que aún no existen. Es exactamente lo que `RF-SP-024` §2 evitó, y la precedencia de implementación resuelve lo mismo sin inventar esquema |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Se implementa antes que `RF-SP-034` y no compila, o se resuelve creando las columnas aquí | Medio | Precedencia declarada en §2 y §8, y enmendada en `requirements/sp.md` §6.1. Es una dependencia de esquema declarada, no una suposición — igual que la que `RF-SP-003` tuvo con `users` |
| Con más de una instancia del backend, la caché de permisos de una instancia puede estar obsoleta y el detalle mostrar permisos que otra ya no concede | Medio | Es **el mismo riesgo que `RF-SP-007` §10 aceptó** para la autorización, y aquí no añade uno nuevo: el detalle refleja lo que **esa instancia** concedería. Registrado y aceptado; la corrección, el día que se despliegue una segunda instancia, es un adaptador compartido del mismo puerto |
| Alguien reimplementa la unión de permisos en una consulta «para no depender de la caché» | Medio | Es la alternativa descartada en §9 y el defecto sería silencioso: dos fuentes que hoy coinciden. La prueba de §11 que compara el detalle con lo que la autorización concede es la que lo detecta |
| La proyección incorpora `failed_attempts` por estar en la tabla | Medio | `CA-SP-346` lo verifica. El riesgo es real porque la columna estará en `UserEntity` desde `RF-SP-034` y basta con seleccionarla sin pensar |
| `lockedUntil` nulo se interpreta como «no bloqueado» cuando la cuenta está bloqueada manualmente | Medio | Documentado en §4 y probado en §11: el estado desambigua. La interfaz debe leer `status` primero y `lockedUntil` después, nunca al revés |
| La respuesta crece con el número de roles y de permisos, sin paginar | Bajo | Es el perfil de **una** persona. `spec.md` §13 de `RF-SP-039` acepta lo mismo por el mismo motivo: partirlo obligaría a pedirlo en trozos para pintar una pantalla |
| Este endpoint expone el alcance completo de cualquier persona a quien tenga `users:read` | Medio | Consecuencia asumida en `spec.md` §14, resolución 1: recae sobre un actor que puede ver la lista entera de todos modos. Se revisa con **D-22** |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con `V18` a `V23` aplicadas más las columnas de `RF-SP-034`) y **API** (extremo a extremo por HTTP, con autenticación). No hay nivel unitario: este requerimiento no toca `domain`.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-212` | Integración + API | Una persona con roles devuelve `200` con la lista completa, cada uno con su estado, y con su propio estado de cuenta |
| `CA-SP-213` | Integración + API | Con dos roles activos que comparten un permiso, `effectivePermissions` lo trae **una sola vez**; la lista está ordenada y su contenido es la unión exacta |
| `CA-SP-214` | Integración + API | Con un rol `INACTIVO` que declara un permiso que ningún otro concede, ese permiso **no** aparece; el rol sí, marcado como inactivo |
| `CA-SP-215` | Integración + API | `FA-001`: una persona sin roles devuelve las dos listas **vacías**, nunca `null` ni campo ausente |
| `CA-SP-216` | Integración + API | Con membresía, se devuelve con su nivel; sin ella, `membership` es `null` |
| `CA-SP-217` | Integración + API | Una cuenta bloqueada **por intentos fallidos** devuelve `lockedUntil` con el momento de expiración |
| `CA-SP-346` | Integración + API | La respuesta **no** contiene el número de intentos fallidos, ni siquiera en cero, sobre una cuenta que acumula tres |
| `CA-SP-218` | Integración + API | La respuesta no contiene ningún dato de la credencial. Se verifica **buscando el literal del hash almacenado** en el cuerpo completo |
| `CA-SP-219` | Integración + API | Un usuario con `deleted_at` no nulo devuelve `404` con `EX-001`, con **el mismo cuerpo** que un identificador inexistente |
| `CA-SP-220` | API | Un actor autenticado sin `users:read` recibe `403`, no obtiene dato alguno y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| **El detalle coincide con lo que la autorización concede** | Integración | Sobre la misma persona, el conjunto devuelto en `effectivePermissions` es **idéntico** al que el filtro de autorización admite. Es la prueba que detecta una segunda implementación de `RN-SEG-009` |
| Permiso concedido por dos roles, uno de ellos inactivo | Integración | El permiso **sigue apareciendo**: lo aporta el rol activo. Es el caso que hace ver por qué la lista debe resolverse y no deducirse |
| `FA-002`: todos los roles inactivos | Integración + API | Los roles se devuelven marcados como inactivos y `effectivePermissions` llega **vacía** |
| Rol eliminado lógicamente | Integración | No aparece en `roles` ni aporta permisos, aunque su fila de `user_roles` siga existiendo |
| **Bloqueo manual** | Integración | `status = BLOQUEADO` con `lockedUntil` nulo. Distinguible del bloqueo automático solo por ese campo |
| Membresía vencida | Integración | Se devuelve con `current: false`, su `endsAt` y su `level`; distinguible de `membership: null` |
| Usuario con membresía y sin rol consumidor | Integración | La consulta **lo muestra tal cual**, sin ocultarlo. Es un defecto de datos que `RN-SP-013` y `RN-SP-015` impiden, y la consulta debe hacerlo visible (`spec.md` §13) |
| El actor se consulta a sí mismo | API | Devuelve exactamente lo mismo que sobre cualquier otra persona. **Esta consulta no tiene dos comportamientos** |
| El actor se consulta a sí mismo **sin** `users:read` | API | `403`. No hay excepción para el titular: ese caso es `RF-SP-039` |
| Identificador no canónico | API | `1-1-1-1-1` devuelve `400` con `VAL-001` y campo `id`, **no** `404` |
| Identificador que no es UUID | API | `abc` devuelve `400` con `VAL-001`, no un `404` de manejador ausente |
| **Número de sentencias por petición** | Integración | **Dos**, con independencia del número de roles y de permisos, y **una** cuando el usuario no existe |
| Persona con muchos roles | Integración | Cincuenta roles se devuelven completos en la misma segunda sentencia, en orden estable entre dos llamadas |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento. Se añade una: **`application` no puede importar el adaptador de caché de `shared/security`**, solo su puerto. Sin esa regla, el atajo de inyectar la caché directamente en el servicio es el más corto de escribir y deja al caso de uso conociendo la infraestructura que `architecture.md` §5.2 le prohíbe.
