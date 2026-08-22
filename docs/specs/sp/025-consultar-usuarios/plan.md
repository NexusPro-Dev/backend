# PLAN — `RF-SP-025` Consultar usuarios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-025` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide cuatro cosas: **cuántas sentencias cuesta una página con roles**, **qué índice sostiene una búsqueda sobre cuatro columnas**, **cómo se filtra por rol y por membresía sin multiplicar filas**, y **qué significa exactamente «membresía vigente» en una consulta**.

---

## 1. Enfoque

Hereda entera la forma de [`RF-SP-002`](../002-consultar-roles/plan.md): proyección en lugar de agregado, envoltura `PageResponse<T>`, lista blanca de ordenamiento resuelta contra un enum, búsqueda por trigramas sobre `f_unaccent(lower(…))` y predicado construido solo con los filtros presentes. Lo que allí está argumentado —por qué GIN y no B-tree, por qué la normalización la hace la base de datos y no Java, por qué se descarta el patrón `(:filtro IS NULL OR …)`, por qué el ordenamiento lleva `id` como desempate— **no se repite**.

Tres cosas difieren, y son las que este plan resuelve:

1. **Cada fila trae una colección.** `spec.md` §14, resolución 1, decidió que el listado devuelve los **roles completos** de cada persona y no su conteo. Es la decisión contraria a la de `RF-SP-002`, y la que gobierna el diseño de la consulta: una colección por fila es exactamente el patrón que produce `N+1` si se resuelve navegando. Se resuelve con **una segunda sentencia sobre los identificadores de la página** (§4).
2. **La membresía se devuelve y además se filtra, y en ambos casos hay que decidir qué es «vigente».** `RN-SP-014` fija que la vigencia se evalúa al consultarla y que ningún proceso retira la fila vencida. Esta consulta es, por tanto, uno de los sitios donde esa evaluación ocurre de verdad.
3. **La búsqueda es sobre cuatro columnas y una de ellas no existe**: `spec.md` §4.1 pide buscar por «nombre de la persona», que en el esquema son dos columnas separadas. Un índice sobre cada una no encuentra `juan perez` escrito de corrido (§2).

`domain` no participa: `spec.md` §5 declara que ninguna regla de negocio gobierna esta consulta y que el alcance de los datos es global. Todo lo que la especificación llama validación (`VAL-001` a `VAL-004`) es formato de parámetros y se resuelve en `api` antes de construir consulta alguna.

## 2. Cambios de esquema

**Migración:** `V23__create_user_query_indexes.sql`

Las cuatro tablas que esta consulta lee —`users`, `user_roles`, `user_memberships` y `roles`— ya existen: las crean `V18` a `V21` (`RF-SP-024`) y `V5` (`RF-SP-001`). **No hay cambios de columnas ni de restricciones**: la consulta no altera la forma de los datos, solo añade las dos estructuras de acceso que sus filtros exigen.

| Tabla | Cambio | Detalle |
|---|---|---|
| `users` | Altera (índice) | `ix_users_busqueda`, GIN de trigramas sobre cuatro expresiones normalizadas |
| `user_memberships` | Altera (índice) | `ix_user_memberships_membership_id`, B-tree sobre la columna por la que se filtra |

```sql
CREATE INDEX ix_users_busqueda ON users USING gin (
    f_unaccent(lower(username))                          gin_trgm_ops,
    f_unaccent(lower(email))                             gin_trgm_ops,
    f_unaccent(lower(first_name || ' ' || last_name))    gin_trgm_ops
);

CREATE INDEX ix_user_memberships_membership_id
    ON user_memberships (membership_id);
```

**Las dos van en la misma migración a propósito**, y es la única desviación respecto del criterio de `RF-SP-024` §2, que separó una migración por entidad. Aquí no se crean entidades: se crean los dos accesos que sirven **a la misma consulta**, y separarlos produciría dos migraciones que solo tienen sentido aplicadas juntas. El nombre lo dice: no es «el índice de `users`», es «los índices de la consulta de usuarios».

Cuatro decisiones sostienen esas líneas.

**Por qué el nombre va como una sola expresión concatenada y no como dos columnas.** `spec.md` §4.1 pide buscar por «nombre de la persona», y una persona se llama `Juan Pérez`, no `Juan` y `Pérez` por separado. Con dos expresiones independientes, teclear `juan perez` no encuentra a nadie: ningún valor de `first_name` contiene ese texto, y ninguno de `last_name` tampoco. Concatenando con un espacio, los trigramas del nombre completo quedan indexados y la coincidencia por contención funciona escribiendo el nombre entero, solo el nombre, solo el apellido o el final de uno y el principio del otro. **El coste es que el índice guarda una tercera representación del mismo dato**, y se acepta porque es la única forma de que la búsqueda encuentre lo que la especificación dice que debe encontrar.

**Por qué no se indexa `last_name` por separado además.** Los trigramas de `Pérez` están contenidos en los de `Juan Pérez`: la expresión concatenada ya sirve la búsqueda por apellido suelto. Un cuarto elemento en el índice lo haría más grande sin responder ninguna consulta nueva.

**`ix_user_memberships_membership_id` es de este requerimiento y no de `RF-SP-024`.** Aquella migración creó la tabla con `user_id` como clave primaria, que es lo que `RN-SP-014` exige, y **no** un índice sobre la otra columna: el alta escribe una fila por usuario y nunca consulta por membresía. Este requerimiento es el primero que pregunta «quiénes tienen esta membresía», y es quien declara el acceso, con el mismo criterio que `RF-SP-030` aplica a `ix_user_roles_role_id`.

**El índice de búsqueda no es parcial.** No lleva `WHERE deleted_at IS NULL`, por el mismo motivo que `ix_roles_busqueda` (`RF-SP-002` §2): dejaría sin cobertura la consulta que sí incluye los eliminados (`CA-SP-204`). La distinción se deja al predicado.

**La función `f_unaccent` y las extensiones no se crean aquí**: las crea `V1__create_shared_functions.sql` (`RF-SP-010`), donde también vive la justificación de por qué `unaccent` no es indexable directamente. Este es el tercer índice que la usa, tras `ix_roles_busqueda` e `ix_countries_busqueda`, y con él la obligación que `RF-SP-002` §8 declaró se vuelve más pesada: **cualquier cambio en el diccionario `unaccent` obliga a reindexar los tres**.

**Recordatorios de la plantilla que no aplican:** esta migración no crea tablas, de modo que no hay clave primaria UUID v7, ni `created_at`/`updated_at`, ni columnas de actor que omitir, ni integridad declarativa que añadir. El nombre `ix_users_busqueda` se escribe en español por coherencia con `ix_roles_busqueda` e `ix_countries_busqueda`, que `requirements/sp.md` §10.8 ya fijó así, y `development-guide.md` §4.1 admite esa excepción.

### Lo que este requerimiento da por existente y quién lo crea

| Objeto | De dónde viene | Para qué lo usa esta consulta |
|---|---|---|
| `users.deleted_at` | `V18__create_users.sql` (`RF-SP-024`) | Excluir los eliminados por defecto e incluirlos bajo petición (`CA-SP-204`) |
| `ix_user_roles_role_id` | `RF-SP-030` | Filtro por rol asignado. Sin él, cada consulta filtrada recorre la tabla de asignaciones entera |
| `pk_user_memberships` | `V20__create_user_memberships.sql` (`RF-SP-024`) | Resolver la membresía de cada fila por su clave primaria |
| `f_unaccent`, `pg_trgm` | `V1__create_shared_functions.sql` (`RF-SP-010`) | Búsqueda insensible a mayúsculas y acentos |

**`deleted_at` existe desde la creación de `users`**, y conviene decir de dónde viene esa certeza: el `plan.md` de `RF-SP-024` la dejaba a `RF-SP-029`, y sus `tasks.md` la corrigieron (Art. I.7) porque `architecture.md` §6.4 la declara columna obligatoria de toda tabla de negocio y porque `RF-SP-003` §2 ya la daba por existente. **Sin esa corrección, este requerimiento no sería implementable**: `CA-SP-204` es la mitad de su contrato.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: `spec.md` §5 no declara ninguna regla `RN-…` |
| `application` | `ListUsersService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)`. Traduce la consulta al puerto, adjunta los roles y arma el resultado paginado |
| `application` | `ListUsersQuery` | Nuevo | Criterios ya validados y normalizados. Sin tipos de HTTP |
| `application` | `UserSortField` | Nuevo | Enum cerrado de campos ordenables. Es la lista blanca de §4, y lo que hace verificable `EX-002` |
| `application` | `UserListItem` | Nuevo | Modelo de lectura de una fila: escalares del usuario, membresía embebida y la lista de roles que se adjunta después |
| `application` | `UserRoleItem` | Nuevo | Modelo de lectura de un rol asignado: `id`, `code`, `name`. Lo reutiliza `RF-SP-026` |
| `application` | `UserQueryRepository` | Nuevo | Puerto de consulta: recibe `ListUsersQuery` y devuelve la página de `UserListItem` con su total; y los roles de un conjunto de identificadores |
| `infrastructure` | `JpaUserQueryRepository` | Nuevo | Adaptador. Construye predicado, proyección y conteo con la API de criterios |
| `infrastructure` | `UserEntity`, `UserRoleEntity`, `UserMembershipEntity` | Sin cambios | Solo como metamodelo para nombrar columnas; no se instancian |
| `infrastructure` | `UserJpaMapper` | Sin cambios | No interviene: la proyección se construye en la propia consulta, sin pasar por el agregado |
| `api` | `UserController` | **Modificado** | Añade `GET /api/v1/users`. Es el segundo método del controlador que creó `RF-SP-024` |
| `api` | `ListUsersRequest` | Nuevo | Parámetros de consulta con Bean Validation (`VAL-001` a `VAL-004`) |
| `api` | `UserListItemResponse` | Nuevo | DTO de salida de cada fila |
| `api` | `RoleSummaryResponse` | Sin cambios | Definido en `RF-SP-001` (`id`, `code`, `name`). Se reutiliza tal cual para los roles de cada persona |
| `api` | `MembershipSummaryResponse` | Nuevo | DTO de la membresía embebida, con su vigencia. Lo reutiliza `RF-SP-026` |
| `shared/api` | `PageResponse<T>`, `PageRequestFactory` | Sin cambios | Creados en `RF-SP-002`. `totalIsExact` vale siempre `true` aquí |

Tres decisiones de reparto:

**El puerto de consulta vive en `application`, no en `domain`.** `UserListItem` no es el agregado `User`: le falta la credencial —a propósito— y le sobra la forma de la membresía. Es el mismo criterio de `RF-SP-002` §3 y `RF-SP-003` §3, y aquí tiene una consecuencia adicional que conviene notar: **`UserRepository`, el puerto de `domain` que creó `RF-SP-024`, no se toca**. Ese devuelve el agregado y lo protege; este devuelve un modelo de lectura y su servicio declara que no escribe.

**`UserResponse` de `RF-SP-024` no se reutiliza.** Aquel DTO devuelve `mustChangePassword`, que es un dato de la credencial y `spec.md` §4.2 lo excluye del listado; y no devuelve membresía ni marca de eliminación, que aquí son obligatorios. Reutilizarlo obligaría a que el alta devolviera campos que no tiene y a que el listado devolviera uno que no debe.

**`UserRoleItem` y `MembershipSummaryResponse` nacen aquí y los hereda `RF-SP-026`.** Son la forma en que una persona muestra sus roles y su nivel, y esa forma debe ser la misma en el listado y en el detalle: si divergieran, la interfaz tendría que pintar dos veces lo mismo de dos maneras.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/users` | Listado paginado de usuarios con filtros, búsqueda y ordenamiento |

**Petición**

```
GET /api/v1/users?page=0&size=20&sort=lastName,asc
                 &status=ACTIVO
                 &roleId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01
                 &membershipId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9d05
                 &search=perez
                 &includeDeleted=false
```

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `page` | entero | `0` | Base cero. Negativo → `VAL-001` |
| `size` | entero | `20` | Entre 1 y 100. Fuera de rango → `VAL-002`; **no se recorta** |
| `sort` | `campo,sentido` | `lastName,asc` | Solo los campos de la lista blanca. Otro → `VAL-003` |
| `status` | enum | — | `ACTIVO`, `INACTIVO`, `BLOQUEADO` o `PENDIENTE`. Otro → `VAL-004` |
| `roleId` | UUID | — | Rol asignado. **No se valida que exista** |
| `membershipId` | UUID | — | Membresía **vigente**. No se valida que exista |
| `search` | texto | — | Sobre nombre de usuario, correo y nombre completo. Recortado; en blanco equivale a ausente |
| `includeDeleted` | booleano | `false` | `true` incorpora los usuarios con `deleted_at` no nulo |

- **El orden por defecto es `lastName,asc` y no `username`.** Es la lista desde la que se administra el acceso de personas, y quien la mira busca a alguien por su apellido. Es la única diferencia deliberada con `RF-SP-002`, cuyo defecto es `code,asc`.
- **`PENDIENTE` se admite en el filtro aunque hoy ninguna fila lo tenga.** El estado está declarado en `ck_users_status` y sin usar (`RF-SP-024`, resolución 1); excluirlo del dominio del filtro obligaría a ampliarlo el día que exista el flujo de activación, y devolver colección vacía es la respuesta correcta mientras tanto.
- **Ni `roleId` ni `membershipId` se validan contra su catálogo.** `spec.md` §13 lo exige para el rol —«filtro por rol inexistente: devuelve colección vacía; no es un error»— y se aplica igual a la membresía, por el mismo argumento de `RF-SP-002` §4: validarlo añadiría una consulta por petición para producir un fallo que la especificación no quiere.

**Respuesta `200`**

```json
{
  "content": [
    {
      "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d40",
      "username": "jperez",
      "email": "juan.perez@factech.co",
      "firstName": "Juan",
      "lastName": "Pérez",
      "status": "ACTIVO",
      "roles": [
        { "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01", "code": "ASESOR", "name": "Asesor comercial" }
      ],
      "membership": {
        "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d05",
        "code": "ORO",
        "name": "Membresía Oro",
        "endsAt": "2027-01-31T23:59:59Z",
        "current": true
      },
      "deletedAt": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8,
  "totalIsExact": true
}
```

Decisiones del contrato:

- **`roles` va completa por fila, y vacía cuando la persona no tiene ninguno** (`CA-SP-343`). Nunca `null` ni campo ausente: una persona sin roles es un estado válido tras `RF-SP-024`, y distinguirlo con la ausencia del campo obligaría al cliente a tratar dos formas.
- **`membership` es nula cuando la persona no tiene ninguna**, y **no es nula cuando la tiene vencida**. Es la distinción que `CA-SP-366` de `RF-SP-032` exige y que este endpoint es el primero en publicar: vencer no es lo mismo que no tener. El campo `current` dice cuál de los dos casos es, y `endsAt` dice hasta cuándo fue.
- **`current` se calcula, no se almacena.** Vale `true` cuando `ends_at` es nulo o posterior al momento de la consulta. Ese momento es **el de la transacción de la base de datos**, no el del reloj de la aplicación (§7).
- **`deletedAt` está siempre presente y vale `null` en los usuarios vigentes.** `spec.md` §6.2 lo declara «presente solo cuando se piden los eliminados»; se interpreta como que es entonces cuando **informa**, no como que el campo aparece y desaparece. Es el mismo criterio de `RF-SP-002` §4, y evita que el cliente tenga que tratar dos formas del mismo recurso según qué parámetro envió. `CA-SP-204` se satisface igual.
- **No existe ningún campo derivado de la credencial**, ni `mustChangePassword`, ni la antigüedad del hash, ni su longitud (`CA-SP-208`). No es una omisión de redacción: `UserListItem` no tiene esos campos y la proyección no los selecciona, que es lo único que hace verificable el criterio.
- **No existen los permisos efectivos** (`CA-SP-209`). Resolverlos por fila costaría una unión de permisos por persona; la pregunta se responde en `RF-SP-026`.
- **No existe `lockedUntil`** (`CA-SP-345`). Es nulo en la inmensa mayoría de las filas y solo el detalle lo devuelve. Quien quiera saber quién no puede entrar filtra por `status=BLOQUEADO`, que es la pregunta operativa real.
- **No existe `createdBy` ni equivalente**: el actor no vive en la tabla de negocio (Art. V.7).

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | `page` negativa | `VAL-001` | `page` |
| `400` | `size` fuera de `[1, 100]` | `VAL-002` | `size` |
| `400` | Campo de ordenamiento fuera de la lista blanca (`EX-002`) | `VAL-003` | `sort` |
| `400` | `status` fuera de su dominio | `VAL-004` | `status` |
| `400` | `roleId` o `membershipId` no son UUID canónicos | `VAL-004` | El parámetro |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `users:read` | `AUTH-002` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **No hay `404` ni `422`.** Un filtro sin coincidencias devuelve `200` con la colección vacía (`FA-001`, `CA-SP-207`), y una página más allá de la última hace lo mismo.
- **Los `400` se evalúan juntos y se devuelven juntos** en `errors`, igual que en `RF-SP-002`.
- **`EX-001` y `EX-002` no producen códigos propios**: se enuncian como `VAL-001`/`VAL-002` y `VAL-003` respectivamente. Un solo hecho, un solo código.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento (§6). `CA-SP-211` se satisface ahí.

**Cómo se acota el ordenamiento.** El campo recibido se resuelve contra `UserSortField`, que asocia cada nombre público a un atributo del metamodelo; un valor no reconocido produce `VAL-003` **antes** de construir la consulta, de modo que la cadena del cliente nunca llega a la sentencia. La lista blanca es:

`username`, `email`, `firstName`, `lastName`, `status`, `createdAt`, `updatedAt`.

Quedan fuera, y cada exclusión tiene su motivo:

| Campo excluido | Por qué |
|---|---|
| `passwordHash`, `mustChangePassword` | `spec.md` §10, `EX-002`, es explícita: **no se admite ordenar por ningún campo de la credencial**. Ordenar por el hash no responde nada y ordenar por la marca agrupa a quien no ha cambiado su contraseña inicial, que es una lista que nadie debería poder pedir |
| `roles`, `membership` | No son campos escalares del usuario, y `spec.md` §6.1 acota el ordenamiento a «campos del propio usuario». Ordenar por una colección obliga a decidir por cuál de sus elementos |
| `deletedAt` | Ordenar por la marca de eliminación agrupa a los eliminados, que es lo mismo que ya hace el filtro |
| `lastLoginAt`, `lockedUntil`, `failedAttempts` | No se devuelven (`CA-SP-345`, `CA-SP-346`), y ordenar por un campo invisible produce un orden que el cliente no puede explicar. Además no existen hasta `RF-SP-034` |

**Cómo se aplican los filtros.** Cada uno presente añade su condición; los ausentes no añaden nada (`RF-SP-002` §4). Los dos que no son del propio usuario se resuelven con `EXISTS` y **no con `JOIN`**:

```sql
-- filtro por rol
EXISTS (SELECT 1 FROM user_roles ur
         WHERE ur.user_id = u.id AND ur.role_id = :rol)

-- filtro por membresía vigente
EXISTS (SELECT 1 FROM user_memberships um
         WHERE um.user_id = u.id AND um.membership_id = :membresia
           AND (um.ends_at IS NULL OR um.ends_at > now()))
```

**Por qué `EXISTS` y no `JOIN`.** Un `JOIN` a `user_roles` multiplica la fila del usuario por cada asignación que cumpla el predicado. Con un solo `roleId` el predicado deja una sola fila y el resultado parece correcto, pero `totalElements` se calcula sobre la misma sentencia y **contaría asignaciones en lugar de personas** en cuanto alguien añadiera un segundo valor al filtro. `EXISTS` corta en la primera coincidencia y no puede duplicar. La membresía tiene clave primaria `user_id` y no podría multiplicar, pero se escribe igual por simetría y para que el predicado de vigencia quede en un solo sitio.

**La membresía devuelta, en cambio, sí va por `LEFT JOIN`** en la sentencia principal: es a lo sumo una fila por usuario —lo garantiza `pk_user_memberships`— y traerla aparte costaría una tercera sentencia para un dato que el `JOIN` resuelve gratis.

**Cómo se aplica la búsqueda.** El término se recorta; si queda vacío, no se añade predicado (`spec.md` §13). Si no, se escapan `\`, `%` y `_`, se envía **como parámetro enlazado** envuelto en comodines de contención y con `ESCAPE` explícito:

```sql
WHERE (f_unaccent(lower(u.username)) LIKE f_unaccent(lower(:t)) ESCAPE '\'
    OR f_unaccent(lower(u.email))    LIKE f_unaccent(lower(:t)) ESCAPE '\'
    OR f_unaccent(lower(u.first_name || ' ' || u.last_name))
                                     LIKE f_unaccent(lower(:t)) ESCAPE '\')
```

La normalización la hace **la base de datos con la misma función que alimenta el índice**, por el motivo de `RF-SP-002` §4: normalizar en Java produce un resultado parecido y no idéntico, y cualquier divergencia se manifiesta como una persona indexada que no aparece en su propia búsqueda.

**La búsqueda por fragmento de correo es deliberada** (`CA-SP-344`) y convierte el listado en una forma de comprobar si una dirección está registrada. `spec.md` §14, resolución 2, lo asume: el endpoint exige `users:read`, que es un permiso de administración, y quien lo tiene puede ver la lista entera de todos modos. La prohibición de `security.md` §5.5 alcanza a los endpoints **públicos** de autenticación, no a este.

**Cuántas sentencias cuesta una página.** Tres como máximo, y ninguna depende del número de filas:

```sql
-- 1: la página de usuarios, con su membresía
SELECT u.id, u.username, u.email, u.first_name, u.last_name, u.status, u.deleted_at,
       m.id, m.code, m.name, um.ends_at
  FROM users u
  LEFT JOIN user_memberships um ON um.user_id = u.id
  LEFT JOIN memberships m       ON m.id = um.membership_id
 WHERE …predicado…
 ORDER BY …orden…, u.id
 LIMIT :size OFFSET :offset;

-- 2: los roles de esa página, en una sola pasada
SELECT ur.user_id, r.id, r.code, r.name
  FROM user_roles ur
  JOIN roles r ON r.id = ur.role_id
 WHERE ur.user_id IN (:ids)
 ORDER BY ur.user_id, r.code;

-- 3: el conteo, con el mismo predicado
SELECT count(*) FROM users u WHERE …predicado…;
```

Cuatro puntos:

- **La segunda sentencia se ejecuta una vez, no una por fila.** Recibe los identificadores de la página ya leída —veinte como mucho— y el servicio agrupa el resultado por `user_id`. Es la forma de traer una colección por fila sin `N+1`, y la alternativa —cargar `UserEntity` y navegar a sus roles— produce veintiuna consultas y es exactamente el patrón que `development-guide.md` §11 prohíbe.
- **Si la página viene vacía, la segunda sentencia no se ejecuta.** Un `IN` con lista vacía es sintácticamente incómodo y semánticamente inútil.
- **Los roles se ordenan por `code` dentro de cada persona**, para que la respuesta sea estable entre llamadas. Sin `ORDER BY` explícito, PostgreSQL no garantiza orden alguno y dos peticiones idénticas devolverían las mismas listas en distinto orden.
- **La lista de roles incluye los inactivos y los eliminados no**, con la misma semántica que `RF-SP-003` §4 fijó para el conteo: un rol inactivo sigue asignado y sigue siendo lo que explica por qué esa persona aparece al filtrar por él; un rol eliminado no existe. El estado del rol **no se devuelve aquí** —eso es `RF-SP-026`—, y esa asimetría se declara en §10 como riesgo menor.

**El conteo** sigue las tres decisiones de `RF-SP-002` §4: se omite cuando la página no se llena, no incluye los `LEFT JOIN` de la membresía, y usa **la misma función** que genera el predicado de los datos para que ambos no puedan divergir. A diferencia de `roles`, `users` **sí crece sin límite**, y por eso se deja anotado: el `COUNT(*)` sobre cientos de miles de filas con un filtro poco selectivo es un recorrido secuencial por petición. Hoy no lo es; el disparador de revisión está en §10.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/users` | `users:read` |

- El permiso **ya existe** en el catálogo: lo siembra `V3__seed_permissions.sql` (`RF-SP-010`), y `V7__seed_system_roles.sql` lo asocia a `SUPERADMIN` y `ADMIN`.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **Es el mismo permiso que `RF-SP-026` y `RF-SP-042`.** Listado, detalle y estructura comercial responden la misma pregunta con distinto grano; exigir permisos propios obligaría a concederlos siempre juntos.
- **No hay techo de privilegios que verificar**, de modo que la resolución del permiso **sí** puede usar la caché de `security.md` §4.5: aquí solo se decide acceso, no una concesión.
- **No hay filtrado por alcance de datos**, y `spec.md` §5 lo dice de forma explícita. La consulta **no recibe el actor** y no lo usa en el predicado. Es la decisión con mayor deuda futura de este plan: cuando se resuelva **D-22**, este endpoint es el primero que habrá que revisar —lo dice la propia especificación— y hoy no tiene ningún punto donde insertar esa restricción. Se deja escrito para que el día que llegue no se busque dónde estaba el hueco.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso (§6).

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita.** Ver abajo |
| Rechazo `400` por `VAL-001` a `VAL-004` | — | **No se audita**: son validaciones de formato (`architecture.md` §6.6.4) |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'users'`, `operation = 'GET /api/v1/users'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log`, `audit_deletion_log` | No aplican: la consulta no altera el estado (`spec.md` §7) |

Dos decisiones, porque la ausencia de auditoría es tan decisión como su presencia:

- **Una consulta exitosa no produce evento de seguridad**, aunque sea la que más datos personales expone de una sola vez. El catálogo de `security.md` §8.1 es cerrado y no incluye la lectura de usuarios; el único registro de lectura que sí contempla es `SECURITY_AUDIT_READ`, y `RF-SP-014` lo justificó por lo que se lee allí —los intentos de escalada—, no por el volumen. Auditar cada listado añadiría una fila por pulsación de un administrador y sepultaría la búsqueda de eventos reales. **Quién consultó qué lo aporta `request_log`**, que registra toda petición con su actor, su correlación y su IP.
- **Se deja anotada la condición de disparo.** Si alguna vez el negocio necesita responder «¿quién estuvo mirando los datos de las personas?», la respuesta no es auditar aquí por su cuenta: es ampliar el catálogo cerrado de `security.md` §8.1, como hicieron `RF-SP-024` y `RF-SP-027`. Registrar un evento que el catálogo no declara lo rechazaría `ck_audit_security_log_event_type` dentro de la transacción de auditoría.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Las tres sentencias de lectura | **Una sola**, `@Transactional(readOnly = true)` sobre `ListUsersService` (`development-guide.md` §10) |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`readOnly = true` no es decorativo, por el motivo de `RF-SP-002` §7: evita el registro de entidades para comprobación de cambios y marca la transacción como de solo lectura en PostgreSQL, de modo que un defecto no pueda escribir desde un camino de consulta.

**La vigencia de la membresía se evalúa con el reloj de la base de datos.** El predicado usa `now()`, que en PostgreSQL es el instante de **inicio de la transacción** y no del enunciado: las tres sentencias comparten por tanto el mismo momento, y no puede ocurrir que una membresía cuente como vigente en el conteo y como vencida en la fila. Enviar el instante desde Java habría funcionado igual de bien salvo por una cosa: el reloj de la aplicación y el del motor pueden diferir, y entonces el listado y `RF-SP-026` —que evalúa lo mismo— darían respuestas distintas sobre la misma persona en el mismo segundo. Un solo reloj, el del motor.

Bajo `READ COMMITTED` cada sentencia toma su propia instantánea, de modo que `totalElements` puede no corresponder exactamente a la página, y los roles adjuntados pueden ser de un instante distinto al de la página. Se acepta con el mismo razonamiento de `RF-SP-002` §7: elevar el aislamiento en todo listado cuesta más que el desfase que evita. **Sobre `users` el desfase es más probable que sobre `roles`** —hay más escrituras—, y su síntoma es benigno: una persona a la que le acaban de asignar un rol puede aparecer sin él durante una petición.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `RF-SP-024` | Comparte `UserController`. Su `plan.md` §8 anunciaba que este requerimiento crea `ix_users_busqueda` y decide el filtro por defecto sobre los eliminados: ambas cosas quedan resueltas aquí (§2, §4) |
| **`RF-SP-024`** | **Corrección heredada:** este plan depende de que `V18__create_users.sql` incluya `deleted_at`, que sus `tasks.md` incorporaron (Art. I.7). Sin esa columna, `CA-SP-204` no es implementable |
| `RF-SP-030` | Debe declarar `ix_user_roles_role_id`, del que depende el filtro por rol. Ya estaba comprometido para `RF-SP-003` y `RF-SP-009`; aquí gana un tercer consumidor |
| `RF-SP-026` | Hereda `UserRoleItem` y `MembershipSummaryResponse`, y **amplía** el primero con el estado del rol, que el listado no devuelve (§10) |
| `RF-SP-032` | Su `CA-SP-366` —una membresía vencida se distingue de no tener ninguna— se verifica por primera vez **aquí**, sobre `membership.current` |
| `RF-SP-003` | Su `assignedUserCount` responde «cuántos» y remite a este endpoint para saber «quiénes». La correspondencia es exacta: ambos excluyen a los eliminados por defecto |
| `RF-SP-042` | Comparte permiso y comparte la forma de paginar un conjunto de personas. **No** comparte consulta: allí el conjunto lo determina la estructura comercial |
| `RF-SP-021` | Comparte `f_unaccent`. Con este requerimiento son **tres** los índices que dependen de ella, y la obligación de reindexar si el diccionario cambia alcanza a los tres |
| `requirements/sp.md` | §10.8 gana `ix_users_busqueda` e `ix_user_memberships_membership_id`. Enmienda de este plan (Art. I.7) |
| **D-22** | Este endpoint es el **primero** que habrá que revisar cuando se resuelva el alcance de datos, y hoy no tiene punto donde insertar la restricción (§5) |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Devolver el **conteo** de roles por fila en lugar de la lista | Es la decisión de `RF-SP-002` y `spec.md` §14, resolución 1, la descartó explícitamente para este listado: el filtro por rol devolvería una lista que no explica por qué cada fila está en ella. La asimetría es deliberada y su motivo es el coste: allí el dato costaba una consulta por fila, aquí sale de una tabla que ya se recorre |
| Traer los roles con un `JOIN` en la sentencia principal | Multiplica la fila del usuario por cada rol, rompe `LIMIT`/`OFFSET` —veinte filas dejan de ser veinte personas— y obliga a deduplicar en memoria un resultado leído multiplicado. Es el mismo argumento por el que `RF-SP-003` §4 separa los permisos |
| Cargar `UserEntity` y navegar a sus roles | Veintiuna consultas por página, y basta con que un mapeador o la serialización recorra la colección `LAZY` para que aparezcan. La proyección hace imposible por construcción lo que el mapeo deja a la disciplina |
| Filtrar por rol con `JOIN` en lugar de `EXISTS` | Duplica filas y hace que `totalElements` cuente asignaciones en cuanto el filtro admita más de un valor. Hoy no se nota; el día que se note, el número lleva tiempo siendo incorrecto |
| Indexar `first_name` y `last_name` por separado | Teclear `juan perez` no encontraría a nadie: ese texto no está contenido en ninguna de las dos columnas. La concatenación es la única forma de servir la búsqueda que `spec.md` §4.1 describe |
| Añadir una columna generada `search_text` con el nombre completo normalizado | Necesita igualmente una función `IMMUTABLE`, de modo que no evita el envoltorio, y denormaliza una tabla de negocio para acomodar una necesidad de consulta. Mismo argumento que `RF-SP-002` §9 |
| Devolver los permisos efectivos de cada persona | `spec.md` §4.2 los excluye y `CA-SP-209` verifica la ausencia. Serían una unión de permisos por fila: la consulta más cara del módulo, repetida veinte veces por página |
| Devolver `lockedUntil` en cada fila | `spec.md` §14, resolución 3, lo resolvió: es nulo en casi todas las filas y el filtro por estado ya responde la pregunta operativa. `CA-SP-345` verifica la ausencia |
| Omitir `deletedAt` cuando `includeDeleted=false` | Dos formas del mismo recurso según el parámetro enviado. El cliente tendría que comprobar si el campo llegó antes de leerlo |
| Devolver la membresía **solo si está vigente** | Haría indistinguible «no tiene» de «la tiene vencida», que es justo lo que `CA-SP-366` exige distinguir, y ocultaría la plaza ocupada que `RN-SP-014` conserva a propósito |
| Evaluar la vigencia con el reloj de la aplicación | El motor y la aplicación pueden diferir, y entonces este listado y `RF-SP-026` responderían distinto sobre la misma persona en el mismo segundo (§7) |
| Un proceso que retire las membresías vencidas para no tener que evaluarlas | Es exactamente lo que `spec.md` §4.2 de `RF-SP-032` excluye, y `RN-SP-014` lo prohíbe: la vigencia se evalúa al consultarla |
| Admitir ordenar por `mustChangePassword` | Produce la lista de quienes conservan su credencial inicial, que es un mapa de cuentas más fáciles de tomar. `EX-002` lo prohíbe sin nombrarlo así |
| Paginación por cursor | Elimina el conteo y no degrada en páginas altas, pero no puede dar `totalElements`, `totalPages` ni salto a una página arbitraria, que es lo que `spec.md` §6.2 exige |
| Validar que `roleId` y `membershipId` existen | `spec.md` §13 pide colección vacía, no error. Validarlo añade una consulta por petición para producir un fallo que nadie quiere |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| El conteo exacto degrada cuando `users` crezca: un `COUNT(*)` con filtro poco selectivo es un recorrido secuencial por petición | Medio | Declarado en §4. **Disparador de revisión:** cuando el p95 de este endpoint se acerque a los 500 ms del `RNF-PERF-001` medidos sobre `request_log`. La salida entonces es `totalIsExact = false` con un techo de conteo, que la envoltura ya admite desde `RF-SP-011`, **no** cambiar la paginación |
| Se implementa la lista de roles con navegación y vuelve el `N+1` en una refactorización posterior | **Alto** | Prueba de conteo de sentencias en §11: **tres como máximo, con independencia del número de filas y de roles**. Es la única forma de que no vuelva |
| Un término de búsqueda de menos de tres caracteres no puede usar el índice de trigramas y degrada a recorrido secuencial | Medio | En `roles` era irrelevante; **aquí no**, porque `users` crece. Se acepta hoy y se anota el disparador: si la búsqueda de dos caracteres aparece en el p95, la salida es exigir una longitud mínima al término, que es un cambio en `ListUsersRequest` y nada más |
| `f_unaccent` se declara `IMMUTABLE` sin serlo del todo: si el diccionario cambia, el índice conserva valores obsoletos y la búsqueda devuelve resultados incorrectos **sin error** | Medio | Heredado de `RF-SP-002` §10. Con este requerimiento son tres los índices afectados, y cualquier cambio en el diccionario obliga a `REINDEX` de los tres |
| El listado devuelve los roles sin su estado y alguien deduce que todos conceden | Bajo | Declarado en §4 y en §8: el estado del rol es de `RF-SP-026`, que es la pantalla donde se decide retirar uno. Aquí la lista explica **por qué la fila aparece al filtrar**, no qué puede hacer la persona |
| El filtro por correo parcial convierte el listado en un verificador de direcciones registradas | Bajo | Consecuencia asumida en `spec.md` §14, resolución 2. Acotada por el permiso: quien puede preguntarlo puede ver la lista entera |
| `totalElements` no corresponde exactamente a la página bajo escrituras concurrentes | Bajo | Aceptado, con el razonamiento de `RF-SP-002` §7. Más probable aquí que allí, y de síntoma benigno |
| La búsqueda ignora los acentos pero el ordenamiento no: `Álvarez` y `Alvarez` se encuentran igual y se ordenan según la colación | Bajo | Se acepta, igual que en `RF-SP-002` §10. Aquí es **más visible**, porque el orden por defecto es por apellido y los apellidos llevan acentos. Si el negocio lo pide, es un cambio localizado en `UserSortField` |
| Este endpoint expone en una sola respuesta el nombre, el correo y los roles de todas las personas del sistema, sin alcance por persona | Medio | Es el estado declarado por `spec.md` §5 mientras **D-22** siga abierta, no un descuido. Acotado por `users:read` y registrado en §5 y §8 como el primer endpoint a revisar |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con `V18` a `V23` aplicadas) y **API** (extremo a extremo por HTTP, con autenticación). No hay nivel unitario salvo el de la lista blanca: este requerimiento no toca `domain` y su lógica es una consulta.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-203` | Integración + API | Con más usuarios que el tamaño de página, la respuesta trae exactamente `size` elementos, el `totalElements` real y el `totalPages` calculado; la segunda página no repite ni omite ninguna fila |
| `CA-SP-204` | Integración + API | Un usuario con `deleted_at` no nulo no aparece por defecto ni cuenta en el total; con `includeDeleted=true` aparece con `deletedAt` informado |
| `CA-SP-205` | Integración + API | Cada filtro por separado y los tres combinados devuelven solo las filas que cumplen. El filtro por membresía **no** devuelve a quien la tiene vencida |
| `CA-SP-206` | Integración + API | Con `Pérez` en la tabla, buscar `perez`, `PEREZ` y `Perez` lo encuentra; **`peres` no lo encuentra**. Exige PostgreSQL real: `unaccent` no es simulable |
| `CA-SP-207` | API | Un filtro sin coincidencias devuelve `200` con `content` vacío y la paginación en cero. Nunca `404` ni `204` |
| `CA-SP-208` | Integración + API | El cuerpo no contiene `password`, `passwordHash`, `mustChangePassword` ni ningún campo derivado. Se verifica **buscando el literal del hash almacenado** en la respuesta completa |
| `CA-SP-209` | API | Ninguna fila trae permisos efectivos, y la traza de sentencias no incluye ninguna consulta a `role_permissions` |
| `CA-SP-343` | Integración + API | Una persona con tres roles los trae los tres, ordenados por `code`; una sin ninguno trae la lista **vacía**, no `null` ni ausente |
| `CA-SP-344` | Integración + API | Buscando `perez@fac` se encuentra a `juan.perez@factech.co`. Es la prueba del fragmento de correo |
| `CA-SP-345` | API | Ninguna fila contiene `lockedUntil`, ni siquiera nulo, sobre una persona bloqueada |
| `CA-SP-210` | Unitaria + API | `PageRequestFactory` rechaza `size = 101`; el endpoint devuelve `400` con `VAL-002` y **no** una página de cien elementos, que es como se manifestaría el recorte silencioso |
| `CA-SP-211` | API | Un actor autenticado sin `users:read` recibe `403`, no obtiene dato alguno y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| **Número de sentencias por petición** | Integración | Una página de veinte personas con roles y membresía ejecuta **tres** sentencias como máximo, y **las mismas tres** con una página de cien. Es la prueba que impide que el `N+1` vuelva |
| Página vacía | Integración | La segunda sentencia **no se ejecuta**: dos sentencias, no tres |
| Página más allá de la última | API | `200` con `content` vacío y `totalElements` intacto, no `404` |
| Búsqueda con `%`, `_` y `\` | Integración | Se tratan como texto literal: un término con `%` no devuelve el padrón entero |
| Búsqueda vacía o solo espacios | API | Equivale a no filtrar: mismo resultado que la consulta sin el parámetro |
| **Búsqueda por nombre completo** | Integración | `juan perez` encuentra a `Juan` `Pérez`; `perez juan` **no** lo encuentra, y eso es correcto: el índice guarda el nombre en su orden |
| Filtro por rol inexistente | API | Colección vacía y `200`; no se consulta la existencia del rol |
| Filtro por rol que no multiplica | Integración | Una persona con tres roles, uno de ellos el filtrado, aparece **una sola vez** y cuenta **uno** en `totalElements` |
| **Membresía vencida** | Integración | Se devuelve con `current: false` y su `endsAt`; el filtro por esa membresía **no** la trae. Distinguible de no tener ninguna, donde `membership` es `null` |
| Membresía que vence entre dos peticiones | Integración | La respuesta cambia sola sin que nada se haya escrito. No es un defecto: es `RN-SP-014` |
| Persona con rol eliminado lógicamente | Integración | El rol **no** aparece en su lista; los inactivos sí |
| Campo de ordenamiento arbitrario | API | `sort=password_hash,asc`, `sort=(select 1),asc` y `sort=roles.code,asc` devuelven `400` con `VAL-003`; ninguno llega a la base de datos |
| Ordenamiento por un campo con valores repetidos | Integración | Recorrer todas las páginas ordenando por `status` devuelve a cada persona exactamente una vez: verifica el desempate por `id` |
| Uso efectivo del índice | Integración | El `EXPLAIN` con término de búsqueda muestra el recorrido de `ix_users_busqueda`, y el del filtro por membresía, el de `ix_user_memberships_membership_id` |
| Filtro por `PENDIENTE` | API | Devuelve colección vacía y `200`, no `400`: el estado es válido en el dominio del filtro aunque ninguna fila lo tenga |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento: `api` no accede a `infrastructure` y `domain` no importa Spring ni JPA. No se añaden reglas nuevas, porque este requerimiento no toca `domain`.
