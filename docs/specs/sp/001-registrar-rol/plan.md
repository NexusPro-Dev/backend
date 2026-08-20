# PLAN — `RF-SP-001` Registrar rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-001` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 20-08-2026 |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobado por | — |
| Fecha de aprobación | — |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento se limita a decidir el esquema, los componentes, el contrato y la mecánica transaccional que lo sostienen.

---

## 1. Enfoque

`RF-SP-001` es el primer requerimiento que escribe en base de datos, de modo que su alcance técnico excede al del caso de uso: crea el esquema de los cuatro registros de auditoría (`V3`), el de roles (`V4`), el de la asociación rol–permiso (`V5`) y el poblado de los roles de sistema (`V6`), incluido el rol raíz que la API no puede crear.

El caso de uso se implementa como un servicio transaccional en `application` que orquesta: resolución del rol padre y del catálogo de permisos por sus puertos, construcción del agregado `Role` en `domain` —donde viven `RN-SEG-003`, `RN-SEG-010`, `RN-SP-002` y `RN-SP-003`, verificables sin Spring ni base de datos—, persistencia por el adaptador JPA y emisión del evento de `audit_change_log` en la misma transacción. El evento de `audit_security_log` se emite en transacción independiente, después del commit.

Todo lo que puede declararse en el esquema se declara en el esquema (Art. V.6): unicidad parcial, clave foránea al padre, `CHECK` sobre los dominios cerrados y sobre el formato del código. Las validaciones en Java complementan esas restricciones; no las sustituyen, y en el caso de la unicidad es la restricción de base de datos la que resuelve el alta concurrente.

## 2. Cambios de esquema

Cuatro migraciones. `V1__create_permissions.sql` y `V2__seed_permissions.sql` pertenecen a `RF-SP-010` y son prerrequisito: se dan por aplicadas.

### `V3__create_audit_logs.sql`

Crea los cuatro registros del Art. V.8 conforme a `architecture.md` §6.6 y `security.md` §8.2. Va primero porque `V6` ya debe poder auditar el poblado de los roles de sistema.

| Tabla | Cambio | Detalle |
|---|---|---|
| `audit_change_log` | Crea | Núcleo común + `module varchar(10) NOT NULL`, `entity varchar(50) NOT NULL`, `entity_id uuid NOT NULL`, `action varchar(20) NOT NULL`, `changes jsonb NOT NULL`. `ck_audit_change_log_action CHECK (action IN ('CREATE','UPDATE'))` |
| `audit_deletion_log` | Crea | Núcleo común + `module`, `entity`, `entity_id`, `deletion_type varchar(20) NOT NULL`, `reason text NULL`, `snapshot jsonb NOT NULL`. `ck_audit_deletion_log_type CHECK (deletion_type IN ('LOGICAL','PHYSICAL','ASSOCIATION'))` y `ck_deletion_reason` según `architecture.md` §6.6.3 |
| `audit_error_log` | Crea | Núcleo común + `resource varchar(100) NOT NULL`, `entity_id uuid NULL`, `operation varchar(100) NOT NULL`, `error_code varchar(50) NOT NULL`, `error_type varchar(20) NOT NULL`, `http_status smallint NOT NULL`, `severity varchar(20) NOT NULL`, `message text NOT NULL`. `CHECK` sobre `error_type` (`BUSINESS_RULE`, `INTEGRATION`, `UNHANDLED`) y sobre `severity` (`MEDIA`, `ALTA`) |
| `audit_security_log` | Crea | Núcleo común + `event_type varchar(50) NOT NULL`, `severity varchar(20) NOT NULL`, `outcome varchar(10) NOT NULL`, `target_user_id uuid NULL`, `detail jsonb NULL`. `CHECK` sobre el catálogo cerrado de `security.md` §8.1, sobre `severity` (`INFORMATIVA`, `MEDIA`, `ALTA`) y sobre `outcome` (`SUCCESS`, `FAILURE`) |
| `v_audit_timeline` | Crea | Vista de solo lectura de `architecture.md` §6.6.6 |

Núcleo común de las cuatro (`architecture.md` §6.6.1): `id uuid PRIMARY KEY`, `occurred_at timestamptz NOT NULL`, `actor_id uuid NULL`, `correlation_id uuid NULL`, `ip_address inet NULL`, `user_agent text NULL`, más `ck_<tabla>_origen` con la correspondencia entre `correlation_id` e `ip_address`.

Índices en cada tabla, según `architecture.md` §6.6.6: `(entity, entity_id, occurred_at DESC)` —en `audit_error_log`, `(resource, entity_id, occurred_at DESC)`; en `audit_security_log`, `(target_user_id, occurred_at DESC)`—, `(actor_id, occurred_at DESC)`, `(correlation_id)` e `(ip_address)`.

Tres decisiones sobre estas tablas:

- **`actor_id` no lleva clave foránea a `users`.** La tabla es propiedad de `USR` y todavía no existe; además, una clave foránea impediría conservar el evento si el usuario se elimina, que es justo lo contrario de lo que se busca. La integridad referencial se sacrifica aquí de forma consciente y documentada.
- **No hay `created_at`, `updated_at` ni `deleted_at`.** No son tablas de negocio: son append-only y `occurred_at` es su única marca temporal.
- **El carácter append-only no se fuerza todavía en la base de datos.** Revocar `UPDATE` y `DELETE` al usuario de la aplicación exige un modelo de usuarios de base de datos por entorno que hoy no está definido. Queda pendiente y se anota en §10.

### `V4__create_roles.sql`

Campos tomados de `requirements/sp.md` §10.2, restricciones de §10.7.

| Tabla | Cambio | Detalle |
|---|---|---|
| `roles` | Crea | `id uuid PRIMARY KEY`, `code varchar(50) NOT NULL`, `name varchar(100) NOT NULL`, `description text NULL`, `role_type varchar(20) NOT NULL`, `parent_role_id uuid NULL`, `status varchar(20) NOT NULL DEFAULT 'ACTIVO'`, `is_system boolean NOT NULL DEFAULT false`, `created_at timestamptz NOT NULL DEFAULT now()`, `updated_at timestamptz NOT NULL DEFAULT now()`, `deleted_at timestamptz NULL` |

Restricciones e índices:

| Nombre | Definición | Por qué |
|---|---|---|
| `uq_roles_code` | `CREATE UNIQUE INDEX … ON roles (code) WHERE deleted_at IS NULL` | `RN-SEG-001`. Parcial, no total: una restricción única corriente bloquearía para siempre el código de un rol eliminado y haría imposible `CA-SP-006` |
| `uq_roles_name` | `CREATE UNIQUE INDEX … ON roles (name) WHERE deleted_at IS NULL` | Igual que el anterior |
| `fk_roles_parent` | `FOREIGN KEY (parent_role_id) REFERENCES roles(id) ON DELETE RESTRICT` | `RN-SEG-008`: la base de datos impide dejar hijos huérfanos aunque la aplicación falle |
| `ck_roles_status` | `CHECK (status IN ('ACTIVO','INACTIVO'))` | `RN-SEG-002` |
| `ck_roles_type` | `CHECK (role_type IN ('FUNCIONARIO','VENDEDOR','CONSUMIDOR'))` | `RN-SP-003` |
| `ck_roles_code_format` | `CHECK (code ~ '^[A-Z][A-Z0-9_]*$')` | `VAL-008`. En el esquema, la garantía vale también para las migraciones de poblado y para cualquier punto de entrada futuro; en Java la validación solo cubre la API |
| `ck_roles_parent_not_self` | `CHECK (parent_role_id IS NULL OR parent_role_id <> id)` | Ciclo de longitud uno. No lo ejercita este requerimiento —el rol aún no existe— pero sí `RF-SP-008`, y cuesta una línea declararlo junto con la tabla |
| `uq_roles_single_root` | `CREATE UNIQUE INDEX … ON roles ((parent_role_id IS NULL)) WHERE parent_role_id IS NULL AND deleted_at IS NULL` | `RN-SEG-007`. Garantiza **como máximo** un rol raíz; el «exactamente uno» lo aporta `V6`. La API siempre exige padre (`VAL-004`), así que es defensa en profundidad |
| `ix_roles_parent_role_id` | `(parent_role_id)` | PostgreSQL no indexa las columnas de clave foránea por su cuenta, y la verificación de `ON DELETE RESTRICT` y los filtros de `RF-SP-002` la recorren |

`RN-SEG-003`, `RN-SEG-006` y `RN-SEG-010` no son expresables como restricción declarativa (`requirements/sp.md` §10.7) y se verifican en `domain`.

### `V5__create_role_permissions.sql`

Campos de `requirements/sp.md` §10.3.

| Tabla | Cambio | Detalle |
|---|---|---|
| `role_permissions` | Crea | `role_id uuid NOT NULL`, `permission_id uuid NOT NULL`, `created_at timestamptz NOT NULL DEFAULT now()`. `PRIMARY KEY (role_id, permission_id)` |

- Clave primaria **compuesta**, excepción declarada al Art. V.11: la unicidad del par es la restricción que importa y una clave sustituta añadiría una columna sin significado.
- `fk_role_permissions_roles` → `roles(id)` `ON DELETE RESTRICT`: el borrado del rol es lógico (`RF-SP-009`), de modo que no hay cascada que declarar; una eliminación física accidental debe fallar.
- `fk_role_permissions_permissions` → `permissions(id)` `ON DELETE RESTRICT`: el catálogo solo se modifica por migración (`RN-SP-004`), y quien lo modifique debe encontrarse con las asociaciones vigentes.
- `ix_role_permissions_permission_id` sobre `(permission_id)`: la clave primaria compuesta solo sirve consultas que empiezan por `role_id`; la pregunta inversa —«qué roles declaran este permiso»— la necesita `RN-SEG-005` en `RF-SP-006`.
- Sin `updated_at` ni `deleted_at`: la asociación no se edita y su retiro es físico (`RN-SP-005`).

### `V6__seed_system_roles.sql`

Puebla el catálogo aprobado de `requirements/sp.md` §4.1 para los roles con `is_system = true`: `SUPERADMIN` (raíz, `parent_role_id NULL`), `ADMIN`, `CONTABILIDAD`, `LIDER_ACADEMICO`, `MANAGER`, `DIRECTOR` y `AGENTE`, con la jerarquía que allí se declara. `ESTUDIANTE` y `Cliente` quedan fuera: están marcados `is_system = false`, es decir, son roles de negocio que se crean por la API, que es precisamente lo que este requerimiento habilita.

Decisiones de esta migración:

- **Identificadores UUID v7 literales**, escritos en el propio SQL y generados una sola vez al redactar la migración. Ni `gen_random_uuid()` ni ninguna generación en base de datos: el Art. V.11 lo prohíbe y, además, el identificador de `SUPERADMIN` debe ser el mismo en todos los entornos para que las pruebas y las migraciones posteriores puedan referenciarlo por constante.
- **Permisos sembrados.** `SUPERADMIN` recibe el catálogo completo (`INSERT … SELECT id FROM permissions`), como exige `RN-SEG-007`. `CONTABILIDAD` recibe `audit:read-changes` y `audit:read-deletions`, lo único que `requirements/sp.md` §4 documenta para él. **El conjunto de `ADMIN` no puede decidirse con la información disponible**: `security.md` §4.1 obliga a que posea todo permiso que cualquier rol de negocio declare, pero ningún documento aprobado dice qué permisos quedan reservados a `SUPERADMIN`. La migración lo siembra con el catálogo completo salvo la lectura de auditoría de seguridad, y el punto queda abierto en §10. `LIDER_ACADEMICO`, `MANAGER`, `DIRECTOR` y `AGENTE` se siembran **sin permisos**, a la espera de `RF-SP-005`: sembrarlos a ojo produciría un catálogo que nadie aprobó y que quedaría como referencia.
- **La migración emite sus propias filas de `audit_change_log`**, una por rol, con `action = 'CREATE'` y `actor_id`, `correlation_id` e `ip_address` en `NULL`. Es la forma correcta de decir «lo creó el sistema, no una persona» (Art. V.15) y evita que los únicos roles del sistema sean también los únicos sin respuesta a «quién los creó» (Art. V.7, V.8).

## 3. Componentes afectados

Paquete raíz del módulo: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2; `domain` no importa Spring ni JPA y una prueba de ArchUnit lo verifica.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Role` | Nuevo | Agregado. Fábrica `Role.create(...)` que aplica `RN-SP-002`, `RN-SP-003`, `RN-SEG-003` y `RN-SEG-010` y fija `status = ACTIVO` sin admitirlo como argumento |
| `domain` | `RoleCode` | Nuevo | Objeto de valor. Formato y longitud del código (`VAL-007`, `VAL-008`). No normaliza: rechaza |
| `domain` | `RoleType` | Nuevo | Enum `FUNCIONARIO`, `VENDEDOR`, `CONSUMIDOR` |
| `domain` | `RoleStatus` | Nuevo | Enum `ACTIVO`, `INACTIVO` |
| `domain` | `PermissionCode` | Nuevo | Objeto de valor `<recurso>:<acción>` |
| `domain` | `RoleRepository` | Nuevo | Puerto: `save`, `findById`, `existsActiveCode`, `existsActiveName` |
| `domain` | `PermissionCatalog` | Nuevo | Puerto: resolución de un conjunto de identificadores a permisos del catálogo |
| `application` | `CreateRoleService` | Nuevo | Caso de uso. `@Transactional`, orquesta el orden de verificación de §4 y emite la auditoría |
| `application` | `CreateRoleCommand` | Nuevo | Entrada del caso de uso, sin tipos de HTTP |
| `application` | `AuthenticatedActor` | Nuevo | Puerto: identificador y permisos efectivos del actor, para `RN-SEG-010` |
| `application` | `RoleChangeAuditor` | Nuevo | Puerto hacia `shared/audit` para el evento de cambio y el de seguridad |
| `infrastructure` | `JpaRoleRepository` | Nuevo | Adaptador de `RoleRepository`. Traduce la violación del índice único a la excepción de duplicado |
| `infrastructure` | `RoleEntity`, `RolePermissionEntity`, `RolePermissionId` | Nuevo | Mapeo JPA. Relaciones `LAZY` |
| `infrastructure` | `RoleJpaMapper` | Nuevo | Conversión entidad ↔ agregado; el agregado no se anota con JPA |
| `infrastructure` | `JpaPermissionCatalog` | Nuevo | Adaptador de `PermissionCatalog` sobre la tabla `permissions` |
| `infrastructure` | `SecurityContextActorAdapter` | Nuevo | Adaptador de `AuthenticatedActor` sobre `shared/security` |
| `api` | `RoleController` | Nuevo | `POST /api/v1/roles`. Declara el permiso, valida el DTO y devuelve `201` con `Location` |
| `api` | `CreateRoleRequest` | Nuevo | DTO de entrada con Bean Validation (`VAL-001` a `VAL-004`, `VAL-007`, `VAL-008`) |
| `api` | `RoleResponse`, `RoleSummaryResponse`, `PermissionResponse` | Nuevo | DTOs de salida. Ninguna entidad JPA cruza la frontera |
| `shared/audit` | `AuditWriter` y sus adaptadores | Nuevo | Escritura de los cuatro registros. Vive en `shared` porque **todos** los módulos emiten eventos y ninguno puede depender de la infraestructura de otro (`architecture.md` §5.1, §5.3) |
| `shared/error` | `GlobalExceptionHandler` y la jerarquía de `development-guide.md` §7.1 | Nuevo | Traducción a Problem Details. Único lugar del código que decide códigos de estado |
| `shared/persistence` | `UuidV7Generator` | Nuevo | Generación de la clave primaria en la aplicación (`architecture.md` §6.3) |

Sobre la propiedad de la auditoría hay una tensión que conviene dejar escrita: `requirements/sp.md` §10 declara a `SP` dueño de las cuatro tablas, y `architecture.md` §5.1 sitúa su implementación en `shared/audit`. Se resuelve así: la **escritura** vive en `shared/audit`, porque es transversal a todos los módulos; la **propiedad** de `SP` se materializa en que las migraciones y la API de lectura (`RF-SP-011` a `RF-SP-014`) le pertenecen. Ningún módulo escribe en tablas de otro: todos escriben en las suyas a través de un componente compartido.

Los enumerados usan como constantes exactamente los valores persistidos (`ACTIVO`, `FUNCIONARIO`), no su traducción al inglés. El ejemplo `RoleStatus.ACTIVE` de `development-guide.md` §4.2 ilustra el uso de mayúsculas, no el idioma; traducirlos obligaría a una tabla de conversión entre el enum y el `CHECK` del esquema.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/roles` | Registra un rol con sus permisos iniciales y devuelve el rol creado |

**Petición**

```json
{
  "code": "CONTABILIDAD",
  "name": "Contabilidad",
  "description": "Rol del área contable.",
  "roleType": "FUNCIONARIO",
  "parentRoleId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01",
  "permissionIds": [
    "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d02",
    "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d03"
  ]
}
```

- **No existe el campo `status`.** El rol nace activo (`spec.md` §4.1) y el DTO se deserializa con `FAIL_ON_UNKNOWN_PROPERTIES` activo: enviar `"status": "INACTIVO"` devuelve `400` y no se ignora en silencio. Es lo que hace verificable a `CA-SP-146`.
- **No existe `isSystem`.** Un rol creado por la API nunca es de sistema; el valor lo fija el `DEFAULT false` del esquema y solo `V6` lo pone en `true`.
- **Los permisos se declaran por identificador, no por código.** El resto del contrato ya referencia entidades por UUID (`parentRoleId`), y mezclar dos espacios de identificación en el mismo cuerpo obliga al cliente a decidir cuál usar. El catálogo se consulta antes con `RF-SP-010`, que devuelve ambos.
- `permissionIds` admite ausencia, `null` y lista vacía, los tres con el mismo significado: alta sin permisos (`FA-001`). Los duplicados se colapsan a una sola ocurrencia antes de validar (`spec.md` §13).
- `name` y `description` se recortan de espacios al inicio y al final antes de validar y persistir. Sin ese recorte, `"Contabilidad "` y `"Contabilidad"` serían dos nombres distintos para `uq_roles_name` y la unicidad se burlaría con un espacio. `code` no se toca: se rechaza si no cumple el formato, para que el actor vea exactamente qué código quedó registrado.

**Respuesta `201`**

Con cabecera `Location: /api/v1/roles/{id}`.

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d10",
  "code": "CONTABILIDAD",
  "name": "Contabilidad",
  "description": "Rol del área contable.",
  "roleType": "FUNCIONARIO",
  "status": "ACTIVO",
  "isSystem": false,
  "parentRole": {
    "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01",
    "code": "ADMIN",
    "name": "Administrador"
  },
  "permissions": [
    { "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d02", "code": "roles:read", "name": "Consultar roles" },
    { "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d03", "code": "audit:read-changes", "name": "Consultar auditoría de cambios" }
  ],
  "createdAt": "2026-08-20T14:32:11Z",
  "updatedAt": "2026-08-20T14:32:11Z"
}
```

La respuesta **no** incluye `createdBy` ni equivalente: el actor no vive en la tabla de negocio (Art. V.7). Quién creó el rol se responde consultando `audit_change_log` con `RF-SP-011`.

**Orden de verificación.** Determina qué error recibe una petición que incumple varias cosas a la vez, así que se fija aquí:

1. Formato y obligatoriedad (`VAL-001` a `VAL-004`, `VAL-007`, `VAL-008`). Se evalúan **todas** y se devuelven juntas en `errors`, porque son independientes entre sí y devolverlas de a una obliga a corregir el formulario campo por campo.
2. Reglas de negocio, en este orden y deteniéndose en la primera que falla: unicidad de código y nombre (`EX-001`), rol padre existente y activo (`EX-002`), existencia de los permisos en el catálogo (`EX-005`), contención en el padre (`EX-003`) y contención en el actor (`EX-004`). Las tres últimas no son evaluables sin haber resuelto antes el padre y los permisos: el orden no es preferencia, es dependencia.

**Errores**

| Caso | Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|---|
| `VAL-001` | `400` | Falta el código o viene en blanco | `VAL-001` | `code` |
| `VAL-002` | `400` | Falta el nombre o viene en blanco | `VAL-002` | `name` |
| `VAL-003` | `400` | Falta la clasificación o no está en el dominio cerrado | `VAL-003` | `roleType` |
| `VAL-004` | `400` | Falta el rol padre | `VAL-004` | `parentRoleId` |
| `VAL-007` | `400` | Código, nombre o descripción exceden la longitud | `VAL-007` | El campo que excede |
| `VAL-008` | `400` | El código no cumple `^[A-Z][A-Z0-9_]*$` | `VAL-008` | `code` |
| `EX-001` / `VAL-005` / `VAL-006` | `409` | Existe un rol no eliminado con ese código o ese nombre; el detalle dice cuál de los dos | `RN-SEG-001` | `code` o `name` |
| `EX-002` | `422` | El rol padre no existe, está eliminado o está inactivo | `EX-002` | `parentRoleId` |
| `EX-003` | `409` | Uno o más permisos no están entre los del rol padre | `RN-SEG-003` | `permissionIds`, una entrada por permiso infractor |
| `EX-004` | `409` | Uno o más permisos exceden los permisos efectivos del actor | `RN-SEG-010` | `permissionIds`, una entrada por permiso infractor |
| `EX-005` | `422` | Uno o más permisos no están en el catálogo | `EX-005` | `permissionIds`, una entrada por permiso inexistente |
| — | `401` | Token ausente o inválido | `AUTH-001` | — |
| — | `403` | Autenticado sin `roles:create` | `AUTH-002` | — |
| — | `500` | Fallo no controlado | `ERR-500` | — |

Criterios detrás de esa tabla:

- **El `error_code` es el identificador de la regla incumplida cuando existe una**, y el de la excepción de la especificación cuando no la hay. Es la convención de `development-guide.md` §7.2 y de `architecture.md` §6.6.4, y es lo que permite ir del error devuelto al requerimiento sin intermediarios. `VAL-005` y `VAL-006` enuncian como validación lo mismo que `EX-001`; no producen un tercer código.
- **`422` y no `404` para el padre o los permisos inexistentes.** El recurso de la petición es la colección `/api/v1/roles`, que existe; un `404` diría que el endpoint no está. La petición es sintácticamente válida y semánticamente rechazada, que es la definición de `422` en `architecture.md` §7.2. Exige añadir una excepción a la jerarquía de `development-guide.md` §7.1, que hoy no cubre `422`; se anota en §8.
- **`409` para `EX-003` y `EX-004`**, porque son violaciones de regla de negocio y esa es la traducción declarada de `BusinessRuleException`.
- **`EX-003`, `EX-004` y `EX-005` enumeran todos los permisos infractores de una vez**, no el primero: la especificación exige informar *qué* permisos lo incumplen, y devolverlos de a uno convierte una corrección en varias vueltas.
- El formato es el de `architecture.md` §7.3, con `correlationId` siempre presente. Los `type` que este requerimiento estrena en el módulo son `…/errors/validacion`, `…/errors/regla-de-negocio`, `…/errors/entidad-no-procesable`, `…/errors/no-autenticado`, `…/errors/sin-permiso` y `…/errors/interno`.
- Este endpoint no devuelve colecciones, de modo que la paginación de §7.4 no le aplica.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/roles` | `roles:create` |

- El permiso **ya existe** en el catálogo: lo crea `V2__seed_permissions.sql` (`RF-SP-010`), prerrequisito de este requerimiento. No hace falta migración de permisos aquí.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público.
- El `403` por falta de permiso lo produce la capa de seguridad compartida antes de entrar al caso de uso, y es ella la que emite el evento de `audit_security_log` (§6). `CA-SP-008` se satisface ahí, no en `CreateRoleService`.
- `RN-SEG-010` no se resuelve con el permiso de acceso: `roles:create` habilita a crear roles, no a decidir con qué alcance. El techo lo pone el conjunto de permisos efectivos del actor, y se verifica en `domain`.
- **Los permisos efectivos del actor se leen de la base de datos, no de la caché** de `security.md` §4.5. La caché sirve a la resolución en tiempo de autorización, que es de lectura y de altísima frecuencia; aquí se decide un techo de privilegios en una operación poco frecuente, y una entrada obsoleta se traduciría en una concesión que el actor ya no tenía derecho a hacer.
- `RN-SEG-011` (sin autoconcesión) no aplica a esta operación: el rol se está creando y nadie puede tenerlo asignado todavía.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Alta del rol | `audit_change_log` | `module = 'SP'`, `entity = 'roles'`, `entity_id` del rol, `action = 'CREATE'`, `changes` con el estado inicial completo: `code`, `name`, `description`, `role_type`, `parent_role_id`, `status`, `is_system` y la lista de códigos de permiso declarados |
| Alta del rol | `audit_security_log` | `event_type` de creación de rol, `severity = 'ALTA'`, `outcome = 'SUCCESS'`, `target_user_id = NULL`, `detail` con el identificador y el código del rol y los permisos concedidos |
| Rechazo por `EX-001` a `EX-005` | `audit_error_log` | `resource = 'roles'`, `operation = 'POST /api/v1/roles'`, `error_code` de la tabla de §4, `error_type = 'BUSINESS_RULE'`, `http_status`, `severity` y `message` saneado |
| Denegación `403` | `audit_security_log` | `event_type` de denegación de autorización, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_deletion_log` | No aplica: este requerimiento no elimina nada |

Decisiones:

- **Un solo evento de cambio para el alta, no uno por permiso.** Las filas de `role_permissions` son parte del estado inicial del agregado y viajan dentro de `changes`. Una fila de auditoría por permiso fragmentaría la línea de tiempo del rol en tantas entradas como permisos tenga, sin responder ninguna pregunta que el evento único no responda. Las altas y bajas posteriores de permisos sí emiten su propio evento, y son `RF-SP-005` y `RF-SP-006`.
- **`changes` en un `CREATE` lleva el estado inicial**, no un diff con `before` en `null` (`architecture.md` §6.6.2).
- **Dos eventos, no uno** (`security.md` §8.1): el de cambio responde qué quedó registrado y se lee con `audit:read-changes`; el de seguridad responde que alguien amplió la superficie de privilegios del sistema y se lee con `audit:read-security`.
- **Las validaciones de formato (`400`) no se auditan** (`architecture.md` §6.6.4): son ruido de formulario y `request_log` ya las cubre.
- **`severity` de los rechazos:** `ALTA` para `EX-003` y `EX-004`, porque declarar permisos por encima del padre o del propio actor es un intento de escalada de privilegios y debe encontrarse buscando por severidad; `MEDIA` para `EX-001`, `EX-002` y `EX-005`, que son errores de operación.
- **Limitación conocida:** `audit_security_log` no tiene columna para la entidad afectada —solo `target_user_id`, pensada para usuarios—, de modo que el rol creado viaja en `detail`. Consultar «qué eventos de seguridad afectaron a este rol» exige entonces un filtro sobre `jsonb`. Se acepta para no alterar el esquema definido en `security.md` §8.2, y se anota en §10.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Inserción en `roles`, inserción en `role_permissions` y su fila de `audit_change_log` | **La misma** (Art. V.14). Si el alta se revierte, su evento también; si el evento falla, el alta falla |
| `audit_security_log` del alta correcta | **Independiente**, `REQUIRES_NEW`, disparada **después del commit** de la transacción de negocio |
| `audit_error_log` de un rechazo o un fallo | **Independiente**, `REQUIRES_NEW` |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`@Transactional` vive sobre `CreateRoleService`, en `application`; nunca en el controlador ni en el repositorio.

El matiz que no es obvio es **por qué el evento de seguridad se emite después del commit**. `REQUIRES_NEW` es obligatorio, pero por sí solo no basta aquí: emitido antes del commit, una reversión posterior dejaría un evento `SUCCESS` de creación de un rol que no existe, y ese evento no se puede retirar porque su transacción ya cerró. Engancharlo al commit de la transacción de negocio conserva la independencia exigida y elimina el evento fantasma. La contrapartida es la inversa —si esa escritura falla, el rol existe sin evento de seguridad— y se trata en §10.

La inversión no aplica a `audit_error_log` ni a la denegación `403`: ahí el evento se emite precisamente mientras la transacción se revierte, que es el caso para el que `REQUIRES_NEW` existe.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `shared/audit` | Se crea con este requerimiento: las cuatro tablas, el puerto de escritura y la mecánica transaccional. Todo módulo posterior que escriba depende de él |
| `shared/error` | Se estrena la jerarquía de excepciones y el manejador global. Requiere **añadir** un tipo para `422`, que `development-guide.md` §7.1 no contempla; conviene enmendar ese documento en el mismo Pull Request para que código y documentación no diverjan (Art. XII.3) |
| `shared/security` | Debe publicar hacia `application` el actor autenticado y sus permisos efectivos. Es la primera vez que se necesita: hasta ahora la seguridad solo decidía acceso, no alimentaba una regla de negocio |
| `SP` (resto del módulo) | `RF-SP-002` a `RF-SP-009` operan sobre el esquema creado aquí; `RF-SP-011` a `RF-SP-014` leen las tablas de `V3` |
| `USR` | Consume el catálogo de roles por la interfaz publicada de `SP`, nunca por sus tablas (`architecture.md` §5.3). Un rol nuevo es asignable de inmediato. La caché de `security.md` §4.5 no necesita invalidarse por un alta —no hay entrada previa que quede obsoleta—, pero **no debe cachear la ausencia** de un rol, o el recién creado sería invisible hasta que expire la entrada negativa |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Restricción única corriente sobre `roles(code)` y `roles(name)` | Bloquearía el código de un rol eliminado lógicamente para siempre, y `CA-SP-006` exige poder reutilizarlo. La variante de renombrar el código al eliminar (`CONTABILIDAD_20260820`) mantiene la restricción simple, pero corrompe un dato de negocio para acomodar una limitación técnica |
| Verificar la unicidad solo con un `SELECT` previo en el servicio | No resuelve el alta concurrente del caso límite de `spec.md` §13: dos peticiones simultáneas pasan ambas la verificación y la segunda muere con un error de integridad convertido en `500`. Se hace lo contrario: la verificación previa existe **para poder dar un mensaje preciso**, y la violación del índice único se captura y se traduce a `409` distinguiendo por nombre de restricción cuál de los dos índices se violó. La restricción decide; el `SELECT` solo redacta |
| Auditar con un listener de JPA o un trigger de base de datos, en lugar de emitir el evento en el caso de uso | Ni el trigger ni el listener conocen al actor, el `correlation_id`, la IP ni el agente de usuario, que son la mitad del núcleo común de `architecture.md` §6.6.1. Habría que inyectarlos por variables de sesión, y quedarían fuera del alcance de una prueba unitaria. Además, un listener audita por fila —tres filas de `role_permissions`, tres eventos— y no por operación de negocio |
| Validar la contención recorriendo toda la cadena de ancestros | `RN-SEG-004` lo prohíbe, y la razón es aritmética: la contención es transitiva, así que validar contra el padre inmediato garantiza el invariante en toda la cadena. Recorrerla añade consultas y ventana de inconsistencia sin cambiar el resultado |
| Resolver la contención en SQL, con un `INSERT … SELECT` que filtre los permisos válidos | El rol se crearía con los permisos que pasaron el filtro en vez de rechazarse, que es lo contrario de `EX-003`. Y llevaría `RN-SEG-003` a la base de datos, donde no puede probarse sin levantar PostgreSQL (Art. VI.3) |
| Crear el rol raíz por la API con una marca de excepción | Obligaría a que el endpoint aceptara `parentRoleId` nulo y a añadir una rama que se ejecuta una sola vez en la vida del sistema, pero queda expuesta para siempre y hay que defenderla de abuso en cada revisión. `RN-SP-002` exige padre y `RN-SEG-007` admite uno solo sin él: es un dato de instalación y su lugar es una migración |
| Declarar los permisos por código (`"permissions": ["roles:read"]`) en lugar de por identificador | Introduce un segundo espacio de identificación en un cuerpo que ya usa UUID para el rol padre, y obliga al servidor a resolver códigos a identificadores con las mismas consultas. La legibilidad que aporta se recupera en la respuesta, que devuelve identificador, código y nombre de cada permiso |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| El evento de `audit_security_log` falla después del commit: el rol existe sin constancia de seguridad | Medio | El fallo no se propaga (`architecture.md` §8), pero se registra como `ERROR` en el log de aplicación con su `correlation_id`, y la ausencia de eventos de seguridad se monitorea. Es la contrapartida consciente de no producir eventos fantasma |
| La traducción de la violación de índice único mapea el error equivocado —código por nombre— | Bajo | El adaptador decide por el **nombre** de la restricción (`uq_roles_code` / `uq_roles_name`), nunca por el texto del mensaje del driver, que cambia entre versiones |
| El conjunto de permisos de `ADMIN` en `V6` no está aprobado por ningún documento | Alto | Se siembra el mínimo defendible y se marca como punto abierto. Corregirlo después es una migración de datos, no de esquema; peor sería sembrar un catálogo inventado que nadie revise y que quede como referencia |
| El código `Cliente` de `requirements/sp.md` §4.1 no cumple el formato de `VAL-008` ni `ck_roles_code_format` | Medio | No afecta a `V6` —no se siembra, por no ser rol de sistema—, pero **impedirá crearlo por la API**. O el catálogo tiene una errata por `CLIENTE`, o el formato aprobado es más estrecho de lo que el catálogo asume. Debe resolverse antes de implementar; no se decide aquí |
| Las tablas de auditoría admiten `UPDATE` y `DELETE` desde la aplicación | Medio | Revocar esos privilegios exige un modelo de usuarios de base de datos por entorno que no está definido. Queda pendiente y debe resolverse antes del primer despliegue productivo: una auditoría modificable no es evidencia |
| La consulta de eventos de seguridad por rol depende de un filtro sobre `detail` (`jsonb`) | Bajo | Se acepta mientras el volumen sea bajo. Si `RF-SP-014` necesita ese filtro de forma habitual, se resuelve con un índice GIN, no añadiendo columnas al esquema de `security.md` §8.2 |
| La longitud máxima de `description` no está definida en ningún documento aprobado: la columna es `text`, sin límite | Bajo | **No se decide aquí.** Se propone 500 caracteres para poder implementar `VAL-007`; si se confirma, la restricción debe declararse también en el esquema con un `CHECK`, no solo en el DTO |
| `uq_roles_name` distingue mayúsculas: `Contabilidad` y `contabilidad` podrían coexistir | Bajo | El índice está fijado por `requirements/sp.md` §10.7 y no se reabre aquí. Se deja constancia de que `RN-SEG-001` no impide variantes de capitalización; si el negocio las considera duplicados, la corrección es un índice sobre `lower(name)` y una migración con la tabla ya en uso |

## 11. Estrategia de prueba

Niveles: **Unitaria** (dominio, sin Spring ni base de datos), **Integración** (repositorio y esquema con Testcontainers sobre PostgreSQL real) y **API** (extremo a extremo por HTTP, con autenticación).

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-001` | Unitaria + API | `Role.create` acepta datos válidos con permisos contenidos en el padre; el endpoint devuelve `201`, `Location` y el rol con sus permisos y su padre |
| `CA-SP-002` | Integración + API | Los índices únicos parciales rechazan el duplicado en base de datos; el endpoint devuelve `409` con `RN-SEG-001` e indica si el duplicado es de código o de nombre |
| `CA-SP-003` | Unitaria + API | `RN-SEG-003` rechaza el permiso no contenido en el padre, aislada de la infraestructura; el endpoint devuelve `409` enumerando **todos** los permisos infractores |
| `CA-SP-004` | Unitaria + API | `RN-SEG-010` rechaza el permiso fuera de los permisos efectivos del actor; el endpoint devuelve `409` con los infractores, usando un actor que posee `roles:create` pero no el permiso declarado |
| `CA-SP-005` | Unitaria + API | `FA-001`: el agregado se construye sin permisos y se omiten las verificaciones de contención; el endpoint devuelve `201` con `permissions` vacío, para las tres formas de entrada (ausente, `null`, lista vacía) |
| `CA-SP-006` | Integración + API | Con un rol de código `X` y `deleted_at` no nulo, el índice parcial admite insertar otro rol con código `X`; el endpoint devuelve `201` |
| `CA-SP-007` | Integración + API | Tras un alta correcta existe una fila en `audit_change_log` con `action = 'CREATE'` y el estado inicial en `changes`, y una en `audit_security_log` con `outcome = 'SUCCESS'` y `severity = 'ALTA'`; ambas con el `correlation_id` de la petición y la IP de origen |
| `CA-SP-008` | API | Un actor autenticado sin `roles:create` recibe `403`, el rol no se crea y queda el evento de denegación en `audit_security_log` |
| `CA-SP-144` | Unitaria + Integración + API | `RoleCode` rechaza minúsculas, guion medio, espacios y códigos que empiezan por dígito; `ck_roles_code_format` los rechaza también en base de datos; el endpoint devuelve `400` con `VAL-008` |
| `CA-SP-145` | Unitaria + API | Un rol `CONSUMIDOR` bajo un padre `FUNCIONARIO` se crea sin error: la clasificación no se compara con la del padre |
| `CA-SP-146` | API + Integración | El alta devuelve `status = "ACTIVO"` y la fila persiste `ACTIVO`; una petición que incluya el campo `status` devuelve `400` por campo desconocido, de modo que no existe camino hacia `INACTIVO` en el alta |

Casos límite de `spec.md` §13 que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Rol padre eliminado lógicamente | API | Se trata como inexistente: `422` con `EX-002`, no `201` |
| Permisos duplicados en la petición | API | Se normalizan a una ocurrencia sin error, y `role_permissions` recibe una sola fila por permiso |
| Alta concurrente con el mismo código | Integración | Dos altas simultáneas: una `201`, la otra `409` con `RN-SEG-001`. Nunca `500` |
| Rol raíz | Integración | `uq_roles_single_root` impide una segunda raíz; tras `V6` existe exactamente una |

Además, una prueba de ArchUnit verifica que `domain` no importa Spring, JPA ni `jakarta.servlet`, y que `api` no accede a `infrastructure` (`architecture.md` §5.2, `development-guide.md` §6.3). Sin ella, las reglas de capa dependen de la disciplina de cada revisión.
