# PLAN — `RF-SP-024` Registrar usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-024` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026, enmendada el 21 y el 22-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |
| Reabierto el | 22-08-2026 — `V18` incorpora `deleted_at`, ver §2.1 (Art. I.7) |
| Reaprobado el | 22-08-2026 — Responsable del proyecto, verificado contra `architecture.md` §6.4 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide cuatro cosas de las que depende todo lo demás del módulo: **qué tablas nacen aquí y por qué no pueden repartirse**, **cómo se sostiene una unicidad que no se libera nunca**, **por dónde entra y por dónde no sale la credencial**, y **dónde vive `RN-SEG-010` para que tres requerimientos no puedan divergir**.

---

## 1. Enfoque

Este plan crea el **sujeto** del módulo. Hasta aquí `SP` tenía roles, permisos, membresías y catálogos; ninguno tenía a quién aplicarse, y los cuatro registros de auditoría apuntaban a un `actor_id` que no resolvía a ninguna fila. Eso lo cambia este requerimiento, y por eso es el más caro de los veinticuatro planes escritos hasta hoy.

Cuatro decisiones lo gobiernan:

1. **El alta crea las cuatro tablas que escribe, no solo `users`.** `RN-SP-018` y `RN-SP-019` convirtieron el alta en una operación que, según qué roles conceda, escribe también en `user_roles`, `user_memberships` y `user_supervisors` **en la misma transacción**. No existe forma de repartir esas tablas entre este requerimiento y los posteriores: el alta de un vendedor con superior no puede esperar a que `RF-SP-041` cree la tabla donde escribirlo. Es la corrección de lo que los planes de `RF-SP-003` y `RF-SP-009` dieron por hecho (§8).
2. **La unicidad no se libera nunca, y eso cambia qué restricción la sostiene.** `RN-SP-016` prohíbe reutilizar el nombre de usuario y el correo de un usuario eliminado. La consecuencia técnica es que `uq_users_username` y `uq_users_email` son restricciones **totales**, no índices únicos parciales como los de `roles`. Es la asimetría explícita con `uq_roles_code`, y conviene entenderla al revés de como suena: aquí la restricción es más simple **porque** la regla es más dura.
3. **La credencial entra en claro una sola vez, no llega al dominio y no sale nunca.** El texto plano vive entre el DTO y el puerto de cifrado; el agregado `User` solo conoce un `PasswordHash`. Ningún registro, ninguna respuesta y ningún evento de auditoría la contienen en forma alguna, ni siquiera transformada (`CA-SP-196`).
4. **`RN-SEG-010` se extrae a un componente único.** La resolución 5 de `spec.md` §14 lo exige por escrito: el alta que asigna roles concede privilegios igual que `RF-SP-030`, y las dos comprobaciones no pueden envejecer por separado. Hoy la regla vive dentro de `Role.grantPermissions` (`RF-SP-005` §3); este plan la saca a `domain/security` y deja a los tres requerimientos llamando al mismo sitio.

Hay una quinta cosa que este plan tiene que resolver y que no parece de él: **el primer usuario**. `spec.md` §13 lo deja dicho —el superadministrador inicial no se crea por esta funcionalidad, porque haría falta un actor autenticado con `users:create` y no existiría ninguno—, y lo remite a una migración. Esa migración es de aquí: es la primera que puede escribir en `users`.

## 2. Cambios de esquema

Cinco migraciones. `V17__create_country_search_index.sql` (`RF-SP-021`) es la última comprometida.

**Se declaran en migraciones separadas y no en una sola** por el mismo criterio con el que `V5__create_roles.sql` y `V6__create_role_permissions.sql` se separaron: cada una crea una entidad con su propio conjunto de restricciones, y una migración que falla a mitad es más fácil de leer cuando su nombre dice qué estaba creando.

### 2.1 `V18__create_users.sql`

| Tabla | Cambio | Detalle |
|---|---|---|
| `users` | Crea | `id uuid PRIMARY KEY`, `username varchar(50) NOT NULL`, `email varchar(255) NOT NULL`, `first_name varchar(100) NOT NULL`, `last_name varchar(100) NOT NULL`, `password_hash varchar(255) NOT NULL`, `must_change_password boolean NOT NULL DEFAULT false`, `status varchar(20) NOT NULL DEFAULT 'ACTIVO'`, `created_at timestamptz NOT NULL DEFAULT now()`, `updated_at timestamptz NOT NULL DEFAULT now()`, `deleted_at timestamptz NULL` |

| Nombre | Definición | Por qué |
|---|---|---|
| `uq_users_username` | `CREATE UNIQUE INDEX uq_users_username ON users (lower(username))` | `RN-SP-016` y `VAL-006`. **Total**, no parcial: un usuario eliminado no libera su nombre. Va sobre `lower(username)` para que `JPerez` y `jperez` no puedan coexistir; ver abajo |
| `uq_users_email` | `UNIQUE (email)` | `RN-SP-016` y `VAL-007`. Restricción corriente y no índice funcional, porque el correo **se persiste ya normalizado** y `ck_users_email_normalized` lo garantiza |
| `ck_users_email_normalized` | `CHECK (email = lower(btrim(email)))` | Sin él, un `INSERT` directo —una migración, una corrección manual— mete `Juan@X.com` y `uq_users_email` deja de significar lo que dice |
| `ck_users_email_format` | `CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$')` | `VAL-002`. Es una comprobación de forma mínima, no una validación de correo: la buena está en el DTO. Aquí solo impide que entre por `INSERT` directo algo que no es una dirección |
| `ck_users_username_no_at` | `CHECK (position('@' in username) = 0)` | `VAL-010` y `CA-SP-341`. **Es la restricción que sostiene el inicio de sesión con ambas identidades**: sin ella, un nombre de usuario podría parecerse a un correo y `RF-SP-034` tendría que decidir cuál de las dos columnas consultar |
| `ck_users_username_format` | `CHECK (username ~ '^[A-Za-z0-9._-]{3,50}$')` | Acota a un alfabeto sin espacios ni acentos. Un nombre de usuario con espacio al final es indistinguible del mismo sin él en cualquier pantalla, y es permanente |
| `ck_users_names_not_blank` | `CHECK (length(btrim(first_name)) > 0 AND length(btrim(last_name)) > 0)` | `VAL-003`. Un nombre de un solo espacio pasa el `NOT NULL` |
| `ck_users_status` | `CHECK (status IN ('ACTIVO','INACTIVO','BLOQUEADO','PENDIENTE'))` | `security.md` §9 lo exige. Los cuatro estados del catálogo, aunque este requerimiento solo produzca `ACTIVO` y `PENDIENTE` esté declarado y sin usar (`spec.md` §14, resolución 1) |

**Por qué la unicidad del nombre de usuario es sobre `lower(username)` y la del correo no.**

Son dos datos con naturalezas distintas y merecen tratamientos distintos, aunque el efecto buscado sea el mismo.

El **correo** tiene forma canónica: en la práctica, todo proveedor trata el buzón como insensible a mayúsculas. Se normaliza al recibirlo —recorte y minúsculas— y se persiste normalizado, de modo que una restricción única corriente basta y el dato almacenado es ya el comparable. *La salvedad honesta:* el RFC 5321 permite que la parte local sea sensible a mayúsculas, de modo que normalizar es una decisión de producto —dos direcciones que solo difieran en caja son la misma persona— y no una verdad del protocolo. Se asume conscientemente porque la alternativa, tratarlas como distintas, produce cuentas duplicadas que nadie puede fusionar (`RN-SP-016`).

El **nombre de usuario** no tiene forma canónica: `JPerez` es como esa persona quiere que la vean, y la auditoría lo mostrará durante años. Se persiste **tal como se escribió** y es la unicidad la que ignora la caja. La consecuencia es una obligación para `RF-SP-034`, declarada en §8: el inicio de sesión debe comparar el nombre de usuario **de forma insensible a mayúsculas**, o alguien podrá registrarse como `JPerez` y no poder entrar escribiendo `jperez`.

**Columnas que este plan deliberadamente NO crea**, y quién las crea:

| Columna | Requerimiento que la introduce | Por qué no aquí |
|---|---|---|
| `failed_attempts`, `locked_until` | `RF-SP-034` (bloqueo por intentos) y `RF-SP-028` (bloqueo manual) | El alta no cuenta intentos ni bloquea nada. Crearlas ahora sería mantener columnas que ninguna sentencia de este requerimiento lee ni escribe |
| `last_login_at` | `RF-SP-034` | Ídem. `RF-SP-039` lo devuelve, pero quien lo escribe es el inicio de sesión |
| ~~`deleted_at`~~ | ~~`RF-SP-029`~~ | **Corregido el 22-08-2026 (Art. I.7): se crea aquí.** Ver el aviso de abajo |

!!! warning "Corrección del 22-08-2026 — `deleted_at` nace con la tabla"

    Este plan dejaba la columna a `RF-SP-029`, con el mismo argumento que las tres de control de acceso. **No se sostiene, y la diferencia es quién la lee:**

    - `architecture.md` §6.4 declara `deleted_at` **columna obligatoria de toda tabla de negocio**, junto a `id`, `created_at` y `updated_at`. `users` sin ella era una excepción que este plan no declaró.
    - **Diez requerimientos la leen antes de que `RF-SP-029` la escriba.** `RF-SP-003` §2 la daba ya por existente y la atribuía —correctamente— a este requerimiento; `RF-SP-009` la necesita para su conteo; y `RF-SP-025`, `RF-SP-026` y `RF-SP-027` no serían implementables sin ella, porque tratar al eliminado como inexistente es la mitad de su contrato.

    El criterio que este plan invoca —«una columna disponible antes de que exista la regla que la gobierna se acaba usando por un camino que nadie diseñó»— vale para `failed_attempts`, `locked_until` y `last_login_at`, **que nadie lee** hasta `RF-SP-034`. No vale para esta.

    **Lo que no cambia es la unicidad.** `uq_users_username` y `uq_users_email` siguen siendo **totales**, sin cláusula `WHERE`, aunque ahora la columna exista: `RN-SP-016` no libera nada al eliminar, y esa es la asimetría deliberada con `uq_roles_code`. La prueba de §11 que hoy dice «columna que aún no existe, se simula en la prueba de `RF-SP-029`» pasa a ser directamente ejecutable.

    Lo que sigue siendo de `RF-SP-029` es **escribirla**: es el único requerimiento que la pone a un valor distinto de nulo.

Es el mismo criterio que `RF-SP-020` §2 aplicó a `countries`: el esquema no lleva columnas «por si acaso», porque una columna preparada para una operación que ningún requerimiento contempla acaba usándose por un camino que nadie diseñó. `security.md` §9 enumera esas columnas en su modelo **lógico**, y ese mismo documento declara que las columnas exactas las fija la migración (Art. V.3); §8 recoge la aclaración.

**No se crea `ix_users_busqueda`.** La búsqueda por fragmento de nombre o correo es de `RF-SP-025`, que es quien decide su forma. Mismo reparto que `RF-SP-020` hizo con `ix_countries_busqueda`.

### 2.2 `V19__create_user_roles.sql`

| Tabla | Cambio | Detalle |
|---|---|---|
| `user_roles` | Crea | `user_id uuid NOT NULL`, `role_id uuid NOT NULL`, `created_at timestamptz NOT NULL DEFAULT now()`. **Clave primaria compuesta `(user_id, role_id)`** |

| Nombre | Definición | Por qué |
|---|---|---|
| `pk_user_roles` | `PRIMARY KEY (user_id, role_id)` | `security.md` §9. La unicidad del par es toda la información que la fila contiene, de modo que no lleva clave sustituta (`requirements/sp.md` §10.7, nota) |
| `fk_user_roles_user` | `(user_id) → users(id)` `ON DELETE RESTRICT` | Un usuario no se elimina físicamente (`security.md` §3.1); si alguien lo intentara, esto lo impide |
| `fk_user_roles_role` | `(role_id) → roles(id)` `ON DELETE RESTRICT` | `RN-SEG-008`: no se elimina un rol que alguien porta. `RF-SP-009` lo comprueba antes y da un mensaje; esta restricción es la red debajo |

**No lleva `updated_at`, y no es un olvido del Art. V.7.** Una asignación no se modifica: se crea y se borra. No hay ninguna columna que pudiera cambiar, de modo que una marca de última modificación sería siempre igual a la de creación. Es el mismo criterio de `role_permissions` (`RF-SP-005` §2).

**No se crea `ix_user_roles_role_id`.** Lo declara `RF-SP-030` §2, y así lo anticipa el plan de `RF-SP-003` §2. Este requerimiento nunca consulta por `role_id`: escribe filas y las lee por `user_id`, que es el prefijo de la clave primaria.

### 2.3 `V20__create_user_memberships.sql`

| Tabla | Cambio | Detalle |
|---|---|---|
| `user_memberships` | Crea | `user_id uuid PRIMARY KEY`, `membership_id uuid NOT NULL`, `started_at timestamptz NOT NULL DEFAULT now()`, `ends_at timestamptz NULL`, `created_at timestamptz NOT NULL DEFAULT now()`, `updated_at timestamptz NOT NULL DEFAULT now()` |

| Nombre | Definición | Por qué |
|---|---|---|
| `pk_user_memberships` | `PRIMARY KEY (user_id)` | **`RN-SP-014`: una membresía por usuario.** La regla se declara en el esquema y no solo en el dominio (Art. V.6): con `user_id` como clave primaria, «dos membresías a la vez» es imposible por construcción, y `RF-SP-032` sustituye con un `UPDATE` en lugar de insertar |
| `fk_user_memberships_user` | `(user_id) → users(id)` `ON DELETE RESTRICT` | Ídem `user_roles` |
| `fk_user_memberships_membership` | `(membership_id) → memberships(id)` `ON DELETE RESTRICT` | Obligación que el plan de `RF-SP-016` §8 dejó declarada: una membresía no se elimina |
| `ck_user_memberships_periodo` | `CHECK (ends_at IS NULL OR ends_at > started_at)` | Una vigencia que termina antes de empezar no es un caso de negocio, es un dato corrupto |

**Lleva `updated_at` y `user_roles` no**, y la diferencia es real: aquí sí hay algo que se modifica. `RF-SP-032` cambia la membresía o su fecha de fin sobre la misma fila.

**`ends_at` nace nulo en esta operación.** `spec.md` §6.1 no admite fecha de fin en el alta: la membresía que concede el alta es indefinida, y quien quiera acotarla ejecuta `RF-SP-032` a continuación. Es una asimetría consciente con `RF-SP-030`, que sí la admite, y se declara en §10 como riesgo menor: el alta de un consumidor con membresía temporal cuesta dos peticiones.

**Ninguna vigencia se retira sola.** `RN-SP-014` es explícita: la vigencia se evalúa al consultarla y ningún proceso retira la fila vencida. El esquema no lleva nada que sugiera lo contrario —ni columna de estado, ni marca de caducado—, y ese vacío es deliberado.

### 2.4 `V21__create_user_supervisors.sql`

Campos y restricciones tomados de `requirements/sp.md` §10.7 y §10.8, sin variación:

| Tabla | Cambio | Detalle |
|---|---|---|
| `user_supervisors` | Crea | `id uuid PRIMARY KEY`, `user_id uuid NOT NULL`, `supervisor_id uuid NOT NULL`, `started_at timestamptz NOT NULL DEFAULT now()`, `ended_at timestamptz NULL`, `created_at`, `updated_at` |

| Nombre | Definición |
|---|---|
| `fk_user_supervisors_user` | `(user_id) → users(id)` `ON DELETE RESTRICT` |
| `fk_user_supervisors_supervisor` | `(supervisor_id) → users(id)` `ON DELETE RESTRICT` — `RN-SP-022` |
| `uq_user_supervisors_vigente` | `CREATE UNIQUE INDEX … ON user_supervisors (user_id) WHERE ended_at IS NULL` — `RN-SP-021` |
| `ck_user_supervisors_no_self` | `CHECK (user_id <> supervisor_id)` |
| `ck_user_supervisors_periodo` | `CHECK (ended_at IS NULL OR ended_at > started_at)` |

Es la **única** tabla parcialmente única de este plan, y por el motivo contrario al de `roles`: allí la unicidad es parcial para que el borrado libere el nombre; aquí lo es para que el historial no compita con la asignación vigente.

**`ix_user_supervisors_supervisor_id` no se crea aquí.** La consulta por superior —«quién está a cargo de esta persona»— es de `RF-SP-042`, y `RN-SP-022` la usa desde `RF-SP-028`, `RF-SP-029` y `RF-SP-031`. Ninguno de ellos existe todavía; el primero que lo necesite lo declara.

### 2.5 `V22__seed_superadmin.sql`

`spec.md` §13 lo exige y `RN-SP-001` lo convierte en obligación permanente: el sistema no puede quedarse sin un superadministrador activo, y el primero no puede crearse por esta API.

```sql
INSERT INTO users (id, username, email, first_name, last_name,
                   password_hash, must_change_password, status)
VALUES ('...uuid v7 fijo...', 'superadmin', ${superadmin_email},
        'Super', 'Administrador', ${superadmin_password_hash}, true, 'ACTIVO');

INSERT INTO user_roles (user_id, role_id)
SELECT '...uuid v7 fijo...', id FROM roles WHERE code = 'SUPERADMIN';
```

Tres decisiones:

- **El hash y el correo entran por marcador de posición de Flyway** (`spring.flyway.placeholders.*`), no como literal en el archivo. Un hash escrito en el repositorio es una credencial en el repositorio, y `RNF-SEG-003` lo prohíbe. Flyway **falla la migración** si el marcador no tiene valor, que es exactamente el comportamiento buscado: un despliegue sin credencial inicial declarada no arranca, en lugar de arrancar con una conocida.
- **Nace marcado para cambio obligatorio.** Es la misma defensa que el alta corriente (`spec.md` §14, resolución 2): quien preparó el despliegue conoce la credencial, y la ventana se cierra en el primer inicio de sesión.
- **El identificador es fijo y está escrito en la migración**, no generado. Es la única fila de `users` que puede tener un identificador conocido, y hace falta que lo sea: las pruebas de integración de todos los requerimientos posteriores necesitan un actor con `users:create` y deben poder referirlo sin consultarlo.

Depende de `V7__seed_system_roles.sql`, que es donde nace el rol `SUPERADMIN`. Si ese rol no existiera, el `INSERT … SELECT` no insertaría fila alguna y el superadministrador quedaría sin permisos: la migración debe verificarlo y fallar, no continuar en silencio.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2; `domain` no importa Spring ni JPA, y la prueba de ArchUnit de `RF-SP-001` lo verifica.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `User` | Nuevo | Agregado. Identidad, credencial ya cifrada, estado y roles. **Nace `ACTIVO` y marcado para cambio de contraseña**: el constructor no recibe ninguna de las dos cosas |
| `domain` | `Username` | Nuevo | Objeto de valor. Recorta, valida formato y **rechaza la arroba** (`VAL-010`). Expone la forma de comparación —minúsculas— sin transformar el valor mostrado |
| `domain` | `Email` | Nuevo | Objeto de valor. **Recorta y pasa a minúsculas al construirse**: la forma normalizada *es* el valor |
| `domain` | `PersonName` | Nuevo | Nombre y apellidos, recortados y no vacíos (`VAL-003`) |
| `domain` | `PasswordPolicy` | Nuevo | `RN` de credencial: longitud mínima, ausencia de la lista de comunes y **ausencia del nombre de usuario o de la parte local del correo dentro de la contraseña**, sin distinguir mayúsculas (`security.md` §3.2, ampliada por este plan). **Devuelve qué regla incumple**, no un booleano, porque `EX-002` exige decirlo |
| `domain` | `PasswordHash` | Nuevo | Envoltura del hash. Su `toString()` devuelve una máscara: es lo que hace difícil filtrarlo por descuido en un registro |
| `domain` | `PrivilegeContainment` | **Nuevo, extraído** | `RN-SEG-010` en un solo sitio. Recibe los permisos que se conceden y los efectivos del actor, y devuelve `PermissionContainmentViolation`. **Lo usan `RF-SP-005`, `RF-SP-024` y `RF-SP-030`** (`spec.md` §14, resolución 5) |
| `domain` | `Role` | Modificado | `grantPermissions` delega `RN-SEG-010` en `PrivilegeContainment` en lugar de contenerla. `RN-SEG-003` se queda donde está: es una cota del rol padre, no del actor |
| `domain` | `CommercialStructure` | Nuevo | `RN-SP-019` y `RN-SP-020`: decide si el conjunto de roles concedido exige superior, si es la cúspide, y si el superior propuesto porta el rol padre inmediato del rol vendedor de mayor rango. Sin Spring ni base de datos (Art. VI.3) |
| `domain` | `UserRepository` | Nuevo | Puerto: `save`, `existsUsername`, `existsEmail`, `findActiveById`. `RF-SP-025` a `RF-SP-033` le añadirán los suyos |
| `application` | `RegisterUserService` | Nuevo | Caso de uso. `@Transactional`, orquesta el orden de verificación de §4 y emite las dos auditorías |
| `application` | `RegisterUserCommand` | Nuevo | Entrada del caso de uso, sin tipos de HTTP y **con la contraseña en claro**: es el último punto donde existe |
| `application` | `PasswordHasher` | Nuevo | Puerto de cifrado. El dominio no conoce Argon2 ni ninguna biblioteca |
| `application` | `CommonPasswordCatalog` | Nuevo | Puerto de consulta de la lista de contraseñas comunes |
| `application` | `AuthenticatedActor` | Modificado | Puerto de `RF-SP-001`. Ya declara los permisos efectivos del actor leídos de base de datos; aquí se usan tal cual para `RN-SEG-010` |
| `application` | `RoleCatalog` | Modificado | Puerto de `RF-SP-001`. Gana la lectura **con bloqueo compartido** de los roles a conceder (§7) |
| `application` | `UserChangeAuditor` | Nuevo | Puerto hacia `shared/audit` para el evento de cambio. Lo reutilizarán `RF-SP-027` a `RF-SP-033` |
| `application` | `UserSecurityAuditor` | Nuevo | Puerto hacia `shared/audit` para el evento de seguridad del alta (§6) |
| `infrastructure` | `JpaUserRepository` | Nuevo | Adaptador. **Traduce la violación de índice único distinguiendo cuál de los dos se violó** |
| `infrastructure` | `UserEntity`, `UserRoleEntity`, `UserMembershipEntity`, `UserSupervisorEntity` | Nuevos | Mapeo JPA. El agregado no se anota |
| `infrastructure` | `UserJpaMapper` | Nuevo | Conversión entidad ↔ agregado |
| `infrastructure` | `Argon2PasswordHasher` | Nuevo | Adaptador sobre `Argon2PasswordEncoder` de Spring Security (§10) |
| `infrastructure` | `ResourceCommonPasswordCatalog` | Nuevo | Lee la lista de contraseñas comunes de un recurso del empaquetado, en memoria y una sola vez |
| `api` | `UserController` | Nuevo | `POST /api/v1/users`. De él colgarán `RF-SP-025` a `RF-SP-033`, `RF-SP-038`, `RF-SP-041` y `RF-SP-042` |
| `api` | `RegisterUserRequest` | Nuevo | DTO de entrada con Bean Validation (`VAL-001` a `VAL-004`, `VAL-008`, `VAL-010`) |
| `api` | `UserResponse` | Nuevo | DTO de salida del alta. `RF-SP-026` definirá su propio `UserDetailResponse`, como `RF-SP-003` hizo con `RoleDetailResponse` |

Tres decisiones de reparto:

**`PrivilegeContainment` es un componente de dominio, no un servicio de aplicación.** La regla no necesita base de datos: recibe dos conjuntos de códigos de permiso y compara. Quien los obtiene —el actor desde `AuthenticatedActor`, los concedidos desde los roles cargados— es la capa de aplicación, y esa separación es lo que permite probar `RN-SEG-010` sin Spring, como exige el Art. VI.3.

**`CommercialStructure` es un componente aparte y no un método de `User`.** `RN-SP-020` compara la jerarquía de **roles** con la jerarquía de **personas**: necesita el rol del subordinado, el rol del superior y la relación padre entre ambos. Meterlo en el agregado obligaría a que `User` conociera el árbol de roles completo. `RF-SP-030`, `RF-SP-031` y `RF-SP-041` lo reutilizan entero.

**La contraseña en claro no cruza a `domain`.** `PasswordPolicy` sí la recibe —tiene que medirla—, pero `User` se construye con un `PasswordHash` ya calculado. El orden es: DTO → comando → política → puerto de cifrado → agregado. Ese trayecto es corto a propósito.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/users` | Registra una persona en el sistema |

**Petición**

```json
{
  "username": "jperez",
  "email": "juan.perez@factech.co",
  "firstName": "Juan",
  "lastName": "Pérez",
  "password": "···",
  "roleIds": ["018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01"],
  "membershipId": null,
  "supervisorId": null
}
```

- **Los roles, la membresía y el superior van por identificador y no por código.** Es el criterio de `RF-SP-001` y `RF-SP-005` §4: no mezclar dos espacios de identificación en el mismo cuerpo. El cliente tiene los identificadores de `RF-SP-002`, `RF-SP-017` y `RF-SP-025`.
- **No existen los campos `status` ni `mustChangePassword`.** El usuario nace `ACTIVO` y marcado, y el cuerpo se deserializa con `FAIL_ON_UNKNOWN_PROPERTIES` activo: enviarlos devuelve `400` en lugar de ignorarse. Es lo que deja un solo camino hacia cada estado —`RF-SP-028` para el estado, `RF-SP-037` para la marca— y un solo lugar donde auditarlo.
- **`membershipId` y `supervisorId` son condicionales en los dos sentidos.** Indicarlos sin el rol que los exige es `409`, no un dato que se ignora (`EX-005`, `EX-006`). Es lo que impide que una petición copiada de otra deje una membresía colgando de quien no es consumidor.
- **`roleIds` admite entre 0 y 100 elementos.** Cero es `FA-001` y es válido. El techo es el de `RF-SP-005` §4, por el mismo motivo: acotar el coste de una petición que dispara una verificación por elemento.
- **`password` no se recorta ni se transforma.** Un espacio al principio o al final es parte de la contraseña. Recortarla, como se hace con los demás campos, cambiaría silenciosamente lo que la persona escribió y haría fallar su primer inicio de sesión.

**Respuesta `201`**

Con cabecera `Location: /api/v1/users/{id}`, que **sí resuelve**: `RF-SP-026` publica el detalle. Es la diferencia con `RF-SP-020` §4, donde la cabecera se omitió porque no existe endpoint de detalle de país.

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d40",
  "username": "jperez",
  "email": "juan.perez@factech.co",
  "firstName": "Juan",
  "lastName": "Pérez",
  "status": "ACTIVO",
  "mustChangePassword": true,
  "roles": [
    { "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01", "code": "ASESOR", "name": "Asesor comercial" }
  ],
  "createdAt": "2026-08-22T14:32:11Z",
  "updatedAt": "2026-08-22T14:32:11Z"
}
```

- **Se devuelve el correo ya normalizado y el nombre de usuario tal como se escribió.** Es la única forma de que el actor vea qué quedó registrado, y refleja exactamente la asimetría de §2.
- **No se devuelven la membresía ni el superior**, aunque el alta los haya escrito. `spec.md` §6.2 fija la salida y no los incluye; quien los necesite tiene `RF-SP-026`. Añadirlos aquí crearía dos formas del mismo recurso que habría que mantener sincronizadas.
- **No existe `password` ni ningún campo derivado de ella**, ni siquiera su longitud (`CA-SP-196`).
- **No existe `createdBy`**: el actor no vive en la tabla de negocio (Art. V.7). Quién registró a la persona se responde con `RF-SP-011`.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Campo obligatorio ausente, formato inválido o longitud excedida (`VAL-001` a `VAL-004`, `VAL-008`) | El `VAL-…` correspondiente |
| `400` | Nombre de usuario con arroba o fuera del alfabeto (`VAL-010`) | `VAL-010` |
| `400` | Ningún rol informado (`EX-008`, `RN-SP-023`) | `VAL-013` |
| `400` | Cuerpo con campo desconocido, incluidos `status` y `mustChangePassword` | `VAL-001` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `users:create` | `AUTH-002` |
| `409` | Nombre de usuario o correo ya en uso (`EX-001`) | `RN-SP-016` |
| `409` | Rol consumidor sin membresía, o membresía sin rol consumidor (`EX-005`) | `RN-SP-018` |
| `409` | Rol vendedor sin superior, o superior sin rol vendedor (`EX-006`) | `RN-SP-019` |
| `409` | El superior no porta el rol padre inmediato, no existe o no está `ACTIVO` (`EX-007`) | `RN-SP-020` |
| `409` | Algún rol excede los privilegios del actor (`EX-004`) | `RN-SEG-010` |
| `422` | Algún rol no existe, está eliminado o está inactivo (`EX-003`) | `EX-003` |
| `500` | Fallo no controlado | `ERR-500` |

El formato de error es el de `architecture.md` §7.3.

**`422` para el rol inexistente y `409` para el rol que excede al actor**, igual que `RF-SP-005` §4: el primero es una referencia que no resuelve, el segundo es una petición bien formada que una regla de negocio rechaza. Mantener la misma correspondencia en los dos requerimientos es lo que permite a un cliente tratarlos igual.

**Los cuerpos de `409` deben enumerar qué elemento incumple.** `EX-001` exige decir cuál de las dos identidades está en uso; `EX-003` y `EX-004`, qué roles; `EX-007`, qué rol debería portar el superior. Sin ese detalle el actor no puede corregir la petición.

**El `409` de `EX-001` no distingue si el conflicto es con un usuario vigente o con uno eliminado.** `spec.md` §10 lo exige de forma explícita: distinguirlo informaría de la existencia de una cuenta. La consecuencia es que el mensaje dice «ese nombre de usuario ya está en uso» y nada más — y esa es la razón, no un mensaje pobre.

**Orden de verificación.** Determina qué error recibe una petición que incumple varias cosas a la vez:

1. **Formato y obligatoriedad** (`VAL-001` a `VAL-004`, `VAL-008`, `VAL-010`). Se evalúan **todas** y se devuelven juntas en `errors`.
2. **Normalización**: recorte de todos los campos de texto, minúsculas en el correo. La contraseña no se toca.
3. **Política de contraseña** (`EX-002`). Antes que nada que consulte la base de datos: es la única verificación que no necesita ir a buscar nada, y fallar aquí ahorra el resto.
4. **Unicidad** de nombre de usuario y correo (`EX-001`), por consulta para el mensaje y en última instancia por el índice.
5. **Existencia y estado de los roles** (`EX-003`), leyéndolos **con bloqueo compartido** (§7).
6. **Contención de privilegios** (`EX-004`, `RN-SEG-010`).
7. **Coherencia consumidor–membresía** (`EX-005`) y **vendedor–superior** (`EX-006`), que son comprobaciones sobre los roles ya cargados.
8. **Validez del superior** (`EX-007`), leyéndolo con bloqueo compartido.
9. Escritura y auditoría.

Dos cosas de este orden importan y conviene dejarlas dichas. **La política de contraseña va antes que la unicidad** para que una petición con una contraseña mala no revele, por el orden del error, si el correo estaba libre. Y **`RN-SEG-010` va después de comprobar que los roles existen**: no se puede decidir si un rol excede al actor sin conocer sus permisos.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/users` | `users:create` |

- El permiso **ya existe**: lo siembra `V3__seed_permissions.sql` (`RF-SP-010`), que pobló el bloque `users:` completo, y `V7__seed_system_roles.sql` lo asocia a `SUPERADMIN` y `ADMIN`.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **`RN-SEG-010` se evalúa contra los permisos efectivos leídos de la base de datos, nunca contra la caché de `security.md` §4.5.** Es la decisión que `RF-SP-005` §5 fijó y aquí pesa igual: en una lectura, una entrada de caché obsoleta muestra un dato viejo; aquí concedería un privilegio que ya no correspondía, y el resultado persiste en `user_roles`. La resolución del permiso de acceso al endpoint —`users:create`— sí puede usar la caché: es una comprobación sobre el actor, no una concesión.
- El `403` por falta de `users:create` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento (§6). `CA-SP-202` se satisface ahí.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Alta del usuario | `audit_change_log` | `module = 'SP'`, `entity = 'users'`, `entity_id` del nuevo, `action = 'CREATE'`, `changes` con el estado inicial: `username`, `email` **normalizado**, nombre, `status`, `must_change_password`, los roles concedidos y, cuando existan, la membresía y el superior. **Ningún campo derivado de la credencial** |
| Alta del usuario | `audit_security_log` | `event_type = 'USER_CREATED'`, `severity = 'ALTA'`, `outcome = 'SUCCESS'`, `target_user_id` del creado, `detail` con los códigos de rol concedidos |
| Rechazo por `EX-001` a `EX-007` | `audit_error_log` | `resource = 'users'`, `operation = 'POST /api/v1/users'`, `error_code` de la tabla de §4, `error_type = 'BUSINESS_RULE'`, `http_status`, `message` saneado. Severidad **Alta** para `EX-004` —es un intento de escalada de privilegios y debe poder encontrarse buscando por severidad, igual que en `RF-SP-005` §6—; **Media** para el resto |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_deletion_log` | No aplica: el alta no elimina nada |

Tres decisiones:

**El alta emite un evento de seguridad, y para eso hay que ampliar un catálogo cerrado.** `security.md` §8.1 no incluye hoy la creación de un usuario: incluye «creación, modificación o eliminación de un **rol**» y «asignación o retiro de roles a un usuario», pero no el alta. Y `spec.md` §7 y `CA-SP-200` la exigen. **Se añade `Alta de un usuario — Alta — SUCCESS` al catálogo** (§8). Que faltara es coherente con cuándo se escribió aquel documento —antes de que `SP` absorbiera los usuarios—, pero dejarlo así obligaría a elegir entre incumplir `CA-SP-200` o emitir un evento que el catálogo cerrado no contempla.

**Un solo evento de seguridad, no dos.** Cuando el alta concede roles podría argumentarse que ocurre también «asignación de roles a un usuario». No se emiten los dos: es una sola operación atómica, y dos eventos harían que cualquier recuento de asignaciones contase de más. Los roles concedidos viajan en el `detail` del evento del alta. `RF-SP-030` sí emite el suyo, porque allí la asignación es la operación.

**`changes` guarda el correo normalizado, no el enviado.** La auditoría refleja lo que quedó en la tabla. Cómo llegó exactamente la petición es asunto del `request_log` (Art. XV.3). Y **las validaciones de formato (`400`) no se auditan** (`architecture.md` §6.6.4): son ruido de formulario, y `ck_audit_error_log_status` (`RF-SP-013`) rechazaría la fila.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| `INSERT` en `users`, `user_roles`, `user_memberships` y `user_supervisors`, más su evento en `audit_change_log` | **La misma** (Art. V.14). `CA-SP-373` y `CA-SP-397` lo exigen de forma explícita: no existe un instante en que el consumidor esté sin membresía ni el vendedor sin superior |
| `audit_security_log` del alta | **Independiente**, `REQUIRES_NEW`, **enganchada al commit** (`AFTER_COMMIT`) |
| `audit_error_log` de un rechazo o un fallo | **Independiente**, `REQUIRES_NEW` |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`@Transactional` vive sobre `RegisterUserService`, en `application`; nunca en el controlador ni en el repositorio.

**El evento de seguridad se engancha al commit y el de cambio no.** Es la asimetría que `RF-SP-001` §7 fijó: el de cambio pertenece a la transacción de negocio —si el alta se revierte, su evento también—, mientras que el de seguridad se emite `AFTER_COMMIT` para no producir eventos fantasma de altas que no ocurrieron. Un evento de seguridad que documenta una creación revertida es peor que ninguno: en una investigación, es un dato falso.

**Bloqueo compartido sobre los roles y sobre el superior.** `spec.md` §13 exige que el alta y una desactivación simultánea se serialicen —«el alta o ve el rol activo o lo rechaza, pero no crea un usuario con un rol inactivo»—, y lo mismo para el superior. Se resuelve leyendo esas filas con bloqueo compartido (`LockModeType.PESSIMISTIC_READ`, que PostgreSQL ejecuta como `SELECT … FOR SHARE`): el alta no impide otras altas simultáneas sobre el mismo rol, pero sí bloquea a quien intente cambiar el estado de esa fila o eliminarla hasta que la transacción termine. Es la contraparte del `FOR UPDATE` que `RF-SP-009` §7 toma sobre `roles` para eliminar.

**Las filas bloqueadas se leen en orden ascendente de identificador.** Dos altas simultáneas que concedan los mismos dos roles en distinto orden podrían bloquearse mutuamente. Ordenar la lectura elimina el ciclo, que es la única forma de evitar un interbloqueo sin depender de que la base de datos lo detecte y mate una de las dos transacciones.

**La violación de índice único salta en el `flush` del `INSERT`, dentro del caso de uso.** Las restricciones son inmediatas y no diferidas —no hay ninguna operación multifila que lo exija, a diferencia de `RF-SP-016`—, de modo que el adaptador la captura y la traduce distinguiendo por el nombre de la restricción, y `GlobalExceptionHandler` no necesita cambios.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `requirements/sp.md` | **§10 gana tres secciones nuevas** —`users`, `user_roles` y `user_memberships`—, que hoy aparecen en la tabla de entidades sin campos declarados, y **§10.8 gana sus restricciones**: `uq_users_username` sobre `lower(username)`, `uq_users_email`, `ck_users_email_normalized`, `ck_users_email_format`, `ck_users_username_no_at`, `ck_users_username_format`, `ck_users_names_not_blank`, `ck_users_status`, `pk_user_roles`, las tres claves foráneas y `pk_user_memberships` con `ck_user_memberships_periodo`. Enmiendas de este plan (Art. I.7) |
| `security.md` | **§3.2 amplía la política mínima de contraseña**: se añade la prohibición de que la contraseña contenga el nombre de usuario o la parte local del correo, sin distinguir mayúsculas. Decidido el 22-08-2026 al aprobar este plan, y se declara **allí y no aquí** para que `RF-SP-037`, `RF-SP-038` y `RF-SP-040` la hereden sin repetirla. **§8.1 gana «Alta de un usuario — Alta — `SUCCESS`»** (§6). Y **§9 se precisa**: las columnas que enumera para `users` son el modelo lógico completo, no el esquema inicial; `failed_attempts`, `locked_until`, `last_login_at` y `deleted_at` las crean `RF-SP-034`, `RF-SP-028` y `RF-SP-029` |
| `RF-SP-003` y `RF-SP-009` | **Corrección**: ambos planes dicen que `user_roles` la crea `RF-SP-030`. La crea este requerimiento, porque el alta escribe asignaciones. Lo que sigue siendo de `RF-SP-030` es `ix_user_roles_role_id`, que es lo que esos dos planes necesitan de verdad. El orden de implementación de `requirements/sp.md` §6.1 no cambia |
| `RF-SP-005` | `Role.grantPermissions` deja de contener `RN-SEG-010` y la delega en `PrivilegeContainment`. Sin cambio de comportamiento ni de contrato: la misma regla, en un sitio donde tres requerimientos la comparten (`spec.md` §14, resolución 5) |
| `RF-SP-030`, `RF-SP-032`, `RF-SP-033`, `RF-SP-041` | Heredan las cuatro tablas y `CommercialStructure`. Ninguno crea esquema nuevo salvo el índice de `RF-SP-030` y, si lo necesita, `ix_user_supervisors_supervisor_id` |
| `RF-SP-034` | **Obligación declarada:** el inicio de sesión compara el nombre de usuario **de forma insensible a mayúsculas** —`lower(username)`, que es sobre lo que va el índice— y el correo por igualdad directa, porque se almacena normalizado. Si comparase el nombre de usuario por igualdad exacta, quien se registró como `JPerez` no entraría escribiendo `jperez` y el sistema diría que la credencial es inválida |
| `RF-SP-025` | Crea `ix_users_busqueda`, que este plan deliberadamente no crea, y decide el filtro por defecto sobre los eliminados |
| `RF-SP-026` | Define `UserDetailResponse`. Es quien hace que la cabecera `Location` de §4 resuelva |
| `RF-SP-029` | **Escribe** `deleted_at`, que desde la corrección del 22-08-2026 crea `V18` y no él. **Debe saber que las restricciones únicas de `users` son totales**: eliminar a alguien no libera su nombre de usuario ni su correo, y esa es la conducta que `RN-SP-016` exige, no un efecto colateral que haya que corregir con un índice parcial |
| `RF-SP-011` | Su consulta responde ahora también por la entidad `users`. Ninguna adaptación: el registro es genérico por diseño |
| `RF-SP-014` | Empieza a recibir eventos con `target_user_id` poblado, que hasta ahora ningún requerimiento emitía |
| `shared/audit` | Sin cambios estructurales. Gana un cliente que emite un evento de cambio y uno de seguridad bajo el mismo identificador de correlación |
| `modelo-datos.md` | Incorpora las cuatro tablas y la relación `user_supervisors` entre dos usuarios, que es la primera del modelo |
| **Todo módulo futuro** | `users(id)` es la referencia permanente de una persona. Quien la referencie declara su clave foránea `ON DELETE RESTRICT`: un usuario no se elimina físicamente, y un `SET NULL` dejaría auditoría sin sujeto |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Crear solo `users` aquí y dejar las tres asociaciones a `RF-SP-030`, `RF-SP-032` y `RF-SP-041` | El alta de un vendedor consumidor escribe en las cuatro tablas dentro de una transacción. Sin ellas, este requerimiento no se puede implementar; y con ellas creadas después, `CA-SP-373` y `CA-SP-397` no se podrían probar |
| Unicidad parcial sobre `deleted_at`, como en `roles` | `RN-SEG-001` libera el código de un rol eliminado; `RN-SP-016` **no** libera nada. Copiar el índice parcial permitiría registrar a alguien con el nombre de usuario de una persona eliminada, y su actividad sería indistinguible en la auditoría |
| Normalizar también el nombre de usuario a minúsculas | Es la opción más simple, y el coste es que la auditoría muestre `jperez` donde la persona escribió `JPerez` durante los próximos diez años. Se prefiere preservar el dato y que sea la unicidad la que ignore la caja |
| Dejar el nombre de usuario sensible a mayúsculas, sin `lower()` | `JPerez` y `jperez` serían dos cuentas distintas, indistinguibles en cualquier pantalla y permanentes, porque ninguna de las dos libera su nombre |
| Tratar el correo como sensible a mayúsculas, que es lo que permite el RFC 5321 | Produciría cuentas duplicadas de la misma persona que nadie puede fusionar. Se asume la decisión de producto: dos correos que solo difieren en caja son el mismo buzón |
| Confiar la unicidad solo a la comprobación previa del servicio | Dos altas simultáneas la pasan las dos. Solo el índice decide el empate, y `CA-SP-201` exige que el segundo reciba `409` y no `500` |
| Confiar la unicidad solo al índice, sin comprobación previa | Funcionaría, pero el mensaje saldría de traducir una violación de integridad incluso en el caso más común, que es teclear un correo ya registrado. La comprobación previa existe para el mensaje, no para la garantía |
| Recibir los roles por código en lugar de por identificador | Mezclaría dos espacios de identificación en el mismo cuerpo, que ya lleva identificadores de membresía y de superior. `RF-SP-001` y `RF-SP-005` fijaron el criterio |
| Devolver la membresía y el superior en la respuesta del alta | `spec.md` §6.2 fija la salida y no los incluye. Añadirlos crearía dos formas del recurso que mantener sincronizadas; el detalle completo es de `RF-SP-026` |
| Aceptar `status` en la petición | Permitiría registrar a alguien ya inactivo o bloqueado: un segundo camino hacia esos estados y un segundo lugar donde auditarlos. `RF-SP-028` es el único |
| Aceptar fecha de fin de membresía en el alta, como hace `RF-SP-030` | `spec.md` §6.1 no la declara. Añadirla aquí sería ampliar el contrato por cuenta del plan; el alta concede la membresía indefinida y `RF-SP-032` la acota. Anotado en §10 |
| Marcar `must_change_password` solo cuando el actor no es el titular | Aquí el actor **nunca** es el titular: el alta es administrativa (`spec.md` §4.2). La distinción no tiene caso que la use, y `RF-SP-040` es quien la introduce cuando lo tenga |
| Dejar `RN-SEG-010` duplicada en `Role.grantPermissions` y en el alta | Es exactamente lo que la resolución 5 de `spec.md` §14 prohíbe. Dos copias de una regla de escalada de privilegios divergen, y la que se quede atrás es un agujero |
| Verificar `RN-SEG-010` contra la caché de permisos por eficiencia | Una entrada obsoleta concede un privilegio que ya no correspondía, y el resultado queda escrito. Mismo riesgo que `RF-SP-005` §10 registró |
| Bloquear los roles con `FOR UPDATE` en lugar de `FOR SHARE` | Serializaría todas las altas que concedan el mismo rol, que es la operación más común del sistema. El bloqueo compartido impide el cambio de estado sin impedir las altas concurrentes |
| No bloquear nada y confiar en la transacción | `spec.md` §13 lo exige explícitamente para el rol y para el superior. Sin bloqueo, el alta puede leer un rol activo que se desactiva un milisegundo después y crear justo lo que la regla prohíbe |
| Sembrar el superadministrador con una contraseña conocida escrita en la migración | Es una credencial en el repositorio, y `RNF-SEG-003` lo prohíbe. Además, la contraseña conocida de un despliegue acaba siendo la de todos |
| Crear el superadministrador desde un componente de arranque de la aplicación en lugar de una migración | Repartiría la creación del estado inicial entre Flyway y el código, y dejaría el sistema sin superadministrador si el arranque fallara a la mitad. `spec.md` §13 ya decidió que es una migración |
| Crear `failed_attempts`, `locked_until`, `last_login_at` y `deleted_at` ahora, «porque la tabla se crea una sola vez» | Ninguna sentencia de este requerimiento las lee ni las escribe, y una columna disponible antes de que exista la regla que la gobierna se acaba usando por un camino que nadie diseñó. Añadir una columna es una migración corriente (`spec.md` §14, resolución 3, ya lo argumentó para los datos personales) |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La política mínima no impedía una contraseña igual o casi igual al nombre de usuario o al correo | **Resuelto** | `spec.md` §13 lo señalaba y `security.md` §3.2 no lo cubría: el alta habría admitido `jperez2026` para `jperez`. **Decidido el 22-08-2026 al aprobar este plan:** `PasswordPolicy` rechaza la contraseña que contenga el nombre de usuario o la parte local del correo, sin distinguir mayúsculas, y `security.md` §3.2 se enmienda para que `RF-SP-037`, `RF-SP-038` y `RF-SP-040` hereden la misma regla (§8). La comparación es en memoria y no añade ninguna consulta |
| El actor conoce la credencial de la persona que registra | Medio | Consecuencia asumida de `spec.md` §14, resolución 1. La acota el cambio obligatorio: la ventana dura hasta el primer inicio de sesión, y `CA-SP-342` lo verifica de extremo a extremo |
| El coste de memoria de Argon2id degrada el alta bajo carga | Medio | Los parámetros se declaran en configuración, no en código: `m = 19456 KiB`, `t = 2`, `p = 1` como punto de partida (recomendación OWASP), ajustables por entorno sin desplegar. El alta no es una operación de alta frecuencia; **el inicio de sesión sí lo es**, y `RF-SP-034` hereda el mismo coste por verificación: es allí donde el parámetro habrá que medir |
| `Argon2PasswordEncoder` de Spring Security exige BouncyCastle en el classpath | Bajo | Es una dependencia declarada, no opcional: sin ella el adaptador no arranca. Se descubre en el primer arranque, no en producción |
| Un despliegue nuevo sin marcador de posición para la credencial inicial no arranca | Bajo | **Es el comportamiento buscado**, no un fallo. La alternativa era arrancar con una credencial conocida. Debe estar documentado en el procedimiento de despliegue, o el primer intento fallará sin que nadie entienda por qué |
| Interbloqueo entre dos altas que conceden los mismos roles | Medio | Las filas a bloquear se leen ordenadas por identificador (§7). Sin ese orden, el interbloqueo depende de la detección de PostgreSQL, que resuelve matando una transacción: el actor recibiría un `500` esporádico e irreproducible |
| El alta de un consumidor con membresía temporal cuesta dos peticiones | Bajo | Consecuencia de que `spec.md` §6.1 no admita fecha de fin. `RF-SP-032` la acota inmediatamente después. Si aparece como fricción real, es una enmienda de la spec, no del plan |
| `RF-SP-034` compara el nombre de usuario por igualdad exacta y quien se registró con mayúsculas no entra | **Alto** | Declarado en §8 como obligación de aquel requerimiento, y probado allí. Es el riesgo que introduce preservar la caja del nombre de usuario, y el motivo por el que se declara aquí y no se descubre después |
| Se implementa la eliminación física de un usuario por parecer lo natural | Medio | Las tres claves foráneas son `ON DELETE RESTRICT` y `security.md` §3.1 lo prohíbe. La prueba de ausencia de cascadas de `RF-SP-012` §11 recorre el esquema completo y alcanza a estas tablas |
| El evento `USER_CREATED` se emite sin ampliar el catálogo de `security.md` §8.1 | Medio | La enmienda está en §8 y es parte de este plan. Sin ella, el evento sería un tipo que el documento cerrado no reconoce, y `RF-SP-014` lo devolvería sin que nada lo explique |

## 11. Estrategia de prueba

Niveles: **Unitaria** (dominio, sin Spring ni base de datos), **Integración** (Testcontainers sobre PostgreSQL real, con `V18` a `V22` aplicadas) y **API** (extremo a extremo por HTTP, con autenticación).

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-192` | Integración + API | El alta con datos válidos devuelve `201` con `Location`, la fila queda `ACTIVO`, y la persona autentica después **con su correo y con su nombre de usuario** |
| `CA-SP-341` | Unitaria + Integración + API | `Username` rechaza `juan@x.com`; el endpoint devuelve `400` con `VAL-010`; `ck_users_username_no_at` lo rechaza también por `INSERT` directo |
| `CA-SP-342` | Integración + API | La fila nace con `must_change_password = true`, la respuesta lo devuelve, y tras ejecutar `RF-SP-037` la marca queda en `false` |
| `CA-SP-193` | Integración + API | Nombre de usuario y correo duplicados devuelven `409` con `RN-SP-016`, **indicando cuál de los dos** |
| `CA-SP-194` | Integración + API | Con un usuario marcado como eliminado, el alta con su nombre de usuario o su correo devuelve **la misma respuesta** que en el caso vigente: mismo código, mismo mensaje, sin nada que permita distinguirlos |
| `CA-SP-195` | Unitaria + API | `PasswordPolicy` señala **qué** regla incumple; el endpoint devuelve `400` con `VAL-005` y ese detalle. Cubre las tres reglas: longitud, lista de comunes y **contraseña que contiene el nombre de usuario o la parte local del correo** —`jperez2026` para `jperez`, `JPerez!` con distinta caja— |
| `CA-SP-196` | Integración + API | La contraseña no aparece en la respuesta, ni en `audit_change_log`, ni en `audit_security_log`, ni en los registros de operación. Se verifica **buscando el literal enviado** en la respuesta y en las cuatro tablas |
| `CA-SP-197` | Integración + API | Alta sin `roleIds`: `201`, sin filas en `user_roles`, y la resolución de permisos efectivos devuelve el conjunto vacío |
| `CA-SP-198` | Integración + API | Un rol inexistente, eliminado o inactivo devuelve `422` con `EX-003` y **no se escribe ninguna fila**, ni en `users` ni en `user_roles` |
| `CA-SP-199` | Unitaria + API | `PrivilegeContainment` detecta el exceso; el endpoint devuelve `409` con `RN-SEG-010` **enumerando los roles** que lo incumplen |
| `CA-SP-200` | Integración | Una fila en `audit_change_log` con `action = 'CREATE'` y una en `audit_security_log` con `event_type = 'USER_CREATED'`, ambas bajo el mismo identificador de correlación |
| `CA-SP-201` | Integración | Dos altas simultáneas con el mismo nombre de usuario: una `201`, la otra `409` con `RN-SP-016`. **Nunca `500`.** Ídem con el mismo correo |
| `CA-SP-372` | Integración + API | Rol `CONSUMIDOR` sin `membershipId` → `409` con `RN-SP-018`; `membershipId` sin rol de consumidor → `409` con el mismo código |
| `CA-SP-373` | Integración | El alta de un consumidor deja fila en `users`, en `user_roles` y en `user_memberships`; forzando el fallo del último `INSERT`, **no queda ninguna de las tres** |
| `CA-SP-395` | Integración + API | Rol `VENDEDOR` sin `supervisorId` → `409` con `RN-SP-019`; `supervisorId` sin rol vendedor → `409` con el mismo código |
| `CA-SP-396` | Unitaria + Integración + API | `CommercialStructure` rechaza al superior que no porta el rol padre inmediato; el endpoint devuelve `409` con `RN-SP-020` **diciendo qué rol debería portar**. Ídem con superior inexistente o no `ACTIVO` |
| `CA-SP-397` | Integración | El alta de un vendedor deja fila en `users`, en `user_roles` y en `user_supervisors` con `ended_at` nulo; forzando el fallo, no queda ninguna |
| `CA-SP-398` | Unitaria + API | El rol vendedor cuyo rol padre no es `VENDEDOR` se acepta **sin** `supervisorId` y se rechaza con él |
| `CA-SP-202` | API | Un actor autenticado sin `users:create` recibe `403`, no se crea nada, y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Correo con mayúsculas y espacios | Integración + API | `" Juan.Perez@X.CO "` se registra como `juan.perez@x.co`, la respuesta lo devuelve así, y un alta posterior con `juan.perez@x.co` devuelve `409`. Es la prueba de que se normaliza **antes** de comprobar |
| Nombre de usuario con distinta caja | Integración + API | Registrado `JPerez`, un alta de `jperez` devuelve `409`; y la fila conserva `JPerez`, **no** `jperez` |
| `INSERT` directo con correo sin normalizar | Integración | `ck_users_email_normalized` lo rechaza. La defensa no depende de que la petición pase por el DTO |
| Contraseña con espacios al principio o al final | Integración | Se cifra tal cual y la autenticación posterior con el mismo literal —espacios incluidos— funciona |
| Alta concurrente con rol que se desactiva a la vez | Integración | Con el bloqueo compartido, el alta o registra al usuario con el rol activo o devuelve `422`; **nunca** queda un usuario con un rol inactivo. Se ejecuta con las dos transacciones cruzadas en ambos órdenes |
| Alta concurrente con superior que se desactiva a la vez | Integración | Ídem sobre la fila del superior: `409` con `RN-SP-020` o alta correcta, nunca alguien a cargo de una cuenta sin acceso |
| Alta con los mismos dos roles desde dos peticiones simultáneas | Integración | Las dos terminan; **no se produce interbloqueo**. Es la prueba del orden de bloqueo de §7 |
| Alta con rol `VENDEDOR` y `CONSUMIDOR` a la vez | Integración + API | Exige `supervisorId` **y** `membershipId`; con los dos, quedan escritas las cuatro tablas |
| Rechazo de `status` en la petición | API | Un cuerpo con `status` o `mustChangePassword` devuelve `400` por campo desconocido, no se ignora |
| Campos en el límite de longitud | Integración | 50 caracteres de nombre de usuario y 255 de correo se aceptan; uno más devuelve `400` |
| `RN-SEG-010` contra la base de datos, no contra la caché | Integración | Con la caché precargada con un permiso que el actor acaba de perder, el alta que lo concedería se **rechaza**. Es la prueba que hace verificable la decisión de §5 |
| Semilla del superadministrador | Integración | Tras `V22`, existe `superadmin` con el rol `SUPERADMIN`, `must_change_password = true`, y sus permisos efectivos incluyen `users:create`. Sin el marcador de posición, **la migración falla** |
| Ausencia de columnas no creadas | Integración | `users` no tiene `failed_attempts`, `locked_until` ni `last_login_at` —las tres son de `RF-SP-034`—, y **sí tiene `deleted_at`**. Documenta el reparto de §2 y hace que la aparición futura de las otras tres sea una decisión y no un descuido |
| Restricciones únicas totales | Integración | Con un usuario **marcado como eliminado**, el índice sigue rechazando el duplicado del nombre de usuario y del correo. Se verifica además que los dos índices **no** llevan cláusula `WHERE`: es la asimetría deliberada con `uq_roles_code` |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento —en particular que `domain` no importe Spring, que es lo que hace real la separación de `PasswordHasher`—, y la prueba de ausencia de cascadas de `RF-SP-012` §11 se ejecuta sobre el esquema completo, que ahora incluye las cuatro tablas nuevas.
