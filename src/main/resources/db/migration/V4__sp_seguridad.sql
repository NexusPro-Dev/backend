-- =============================================================================
-- V4 — Seguridad y personas (SP): permisos, roles, usuarios, sus asignaciones,
-- sus sesiones y sus cuentas de broker.
--
-- Lo que este bloque decide y conviene no perder:
--   · Los PERMISOS son un catálogo cerrado que solo cambia por migración
--     (RN-SP-004). Se siembran en V8 con identificador literal (Art. V.11).
--   · Los ROLES forman un árbol con UNA raíz; `parent_role_id` ACOTA privilegios
--     (RN-SEG-003: nadie concede lo que no tiene) y no concede herencia.
--   · `user_roles.role_type` es una COPIA de `roles.role_type` atada por clave
--     foránea compuesta: es lo que permite que «una persona tiene como mucho un
--     rol vendedor» (RN-SP-025) viva en el motor como índice único parcial.
--   · `user_memberships` es HISTORIAL: la fila abierta (closed_at nulo) es la
--     actual y hay como mucho una; vigente = abierta Y dentro de fecha.
--   · Los tokens y los permisos de recuperación guardan SOLO el hash.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Permisos. `code` es `resource:action` y va como columna propia para poder
-- referenciarlo directamente; `ck_permissions_code_matches` impide que diverja.
-- ---------------------------------------------------------------------------

CREATE TABLE permissions (
    id          uuid         NOT NULL,
    code        varchar(100) NOT NULL,
    resource    varchar(50)  NOT NULL,
    action      varchar(50)  NOT NULL,
    name        varchar(100) NOT NULL,
    description text         NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uq_permissions_code UNIQUE (code),
    CONSTRAINT ck_permissions_code_format
        CHECK (code ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$'),
    CONSTRAINT ck_permissions_code_matches
        CHECK (code = resource || ':' || action),
    CONSTRAINT ck_permissions_description_length
        CHECK (description IS NULL OR length(description) <= 500)
);

COMMENT ON TABLE permissions IS
    'Catálogo de permisos del sistema. Inmutable por API (RN-SP-004): solo se modifica mediante migración Flyway. RF-SP-010.';
COMMENT ON COLUMN permissions.code IS
    'Concatenación resource:action. Columna propia para poder referenciarla directamente; ck_permissions_code_matches garantiza que no diverja.';

-- ---------------------------------------------------------------------------
-- Roles. `role_type` clasifica —FUNCIONARIO, VENDEDOR, CONSUMIDOR— y NO ES
-- EDITABLE, que es lo que hace legítima la copia en `user_roles`. La unicidad
-- de código y nombre es entre los vivos; `uq_roles_single_root` garantiza una
-- sola raíz viva; `uq_roles_id_role_type` existe para la clave foránea
-- compuesta de `user_roles`.
-- ---------------------------------------------------------------------------

CREATE TABLE roles (
    id             uuid         PRIMARY KEY,
    code           varchar(50)  NOT NULL,
    name           varchar(100) NOT NULL,
    description    text         NULL,
    role_type      varchar(20)  NOT NULL,
    parent_role_id uuid         NULL,
    status         varchar(20)  NOT NULL DEFAULT 'ACTIVO',
    is_system      boolean      NOT NULL DEFAULT false,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz  NULL,

    CONSTRAINT uq_roles_id_role_type UNIQUE (id, role_type),
    CONSTRAINT ck_roles_code_format
        CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_roles_type
        CHECK (role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')),
    CONSTRAINT ck_roles_status
        CHECK (status IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_roles_description_length
        CHECK (description IS NULL OR length(description) <= 500),
    CONSTRAINT ck_roles_parent_not_self
        CHECK (parent_role_id IS NULL OR parent_role_id <> id),
    CONSTRAINT fk_roles_parent
        FOREIGN KEY (parent_role_id) REFERENCES roles (id) ON DELETE RESTRICT
);

COMMENT ON TABLE roles IS
    'Roles del sistema. parent_role_id acota privilegios (RN-SEG-003); no concede herencia.';
COMMENT ON COLUMN roles.parent_role_id IS
    'Nulo únicamente en el rol raíz (RN-SEG-007, RN-SP-002). Expresa además el orden comercial (RN-SP-011).';
COMMENT ON COLUMN roles.is_system IS
    'Solo lo pone en true la migración de poblado. Un rol creado por la API nunca es de sistema.';

CREATE UNIQUE INDEX uq_roles_code ON roles (code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_roles_name ON roles (name) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_roles_single_root
    ON roles ((parent_role_id IS NULL)) WHERE parent_role_id IS NULL AND deleted_at IS NULL;
CREATE INDEX ix_roles_parent_role_id ON roles (parent_role_id);
CREATE INDEX ix_roles_busqueda
    ON roles USING gin (f_unaccent(lower(code)) gin_trgm_ops, f_unaccent(lower(name)) gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- Permisos que declara cada rol. Sin updated_at ni deleted_at: no se edita y
-- su retiro es físico (RN-SP-005).
-- ---------------------------------------------------------------------------

CREATE TABLE role_permissions (
    role_id       uuid        NOT NULL,
    permission_id uuid        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_roles
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_permissions_permissions
        FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE RESTRICT
);

COMMENT ON TABLE role_permissions IS
    'Permisos que declara cada rol. Sin updated_at ni deleted_at: no se edita y su retiro es físico (RN-SP-005).';

CREATE INDEX ix_role_permissions_permission_id ON role_permissions (permission_id);

-- ---------------------------------------------------------------------------
-- Personas. Dos identidades —nombre de usuario y correo— y ambas sirven para
-- entrar. El correo se guarda normalizado; el nombre de usuario se guarda TAL
-- COMO SE ESCRIBIÓ y la unicidad ignora la caja. País obligatorio (RN-SP-034);
-- documento opcional pero POR PAREJA —tipo y número van juntos o no van—, y
-- la clave foránea al catálogo de documentos ES la validación de mayoría de
-- edad. `FTD_PENDIENTE` autentica y NO opera: espera el primer depósito
-- (RF-SP-045). Los teléfonos son nulables en el esquema aunque el personal sea
-- obligatorio en la API (RN-SP-037).
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id                              uuid         PRIMARY KEY,
    username                        varchar(50)  NOT NULL,
    email                           varchar(255) NOT NULL,
    first_name                      varchar(100) NOT NULL,
    last_name                       varchar(100) NOT NULL,
    password_hash                   varchar(255) NOT NULL,
    must_change_password            boolean      NOT NULL DEFAULT false,
    status                          varchar(20)  NOT NULL DEFAULT 'ACTIVO',
    failed_attempts                 smallint     NOT NULL DEFAULT 0,
    locked_until                    timestamptz  NULL,
    last_login_at                   timestamptz  NULL,
    provisional_password_expires_at timestamptz  NULL,
    country_id                      uuid         NOT NULL,
    document_type_id                uuid         NULL,
    document_number                 varchar(30)  NULL,
    address_line1                   varchar(150) NULL,
    address_line2                   varchar(150) NULL,
    city                            varchar(100) NULL,
    phone                           varchar(20)  NULL,
    company_phone                   varchar(20)  NULL,
    created_at                      timestamptz  NOT NULL DEFAULT now(),
    updated_at                      timestamptz  NOT NULL DEFAULT now(),
    deleted_at                      timestamptz  NULL,

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_username_format
        CHECK (username ~ '^[A-Za-z0-9._-]{3,50}$'),
    CONSTRAINT ck_users_username_no_at
        CHECK (position('@' IN username) = 0),
    CONSTRAINT ck_users_email_format
        CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    CONSTRAINT ck_users_email_normalized
        CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_users_names_not_blank
        CHECK (length(btrim(first_name)) > 0 AND length(btrim(last_name)) > 0),
    CONSTRAINT ck_users_status
        CHECK (status IN ('ACTIVO', 'INACTIVO', 'BLOQUEADO', 'FTD_PENDIENTE')),
    CONSTRAINT ck_users_provisional_expiry
        CHECK (provisional_password_expires_at IS NULL OR must_change_password),
    CONSTRAINT ck_users_document_pair
        CHECK ((document_type_id IS NULL) = (document_number IS NULL)),
    CONSTRAINT ck_users_document_number_format
        CHECK (document_number IS NULL OR document_number ~ '^[A-Z0-9][A-Z0-9.-]{2,29}$'),
    CONSTRAINT ck_users_document_number_normalized
        CHECK (document_number IS NULL OR document_number = upper(btrim(document_number))),
    CONSTRAINT ck_users_contacto_not_blank
        CHECK ((address_line1 IS NULL OR length(btrim(address_line1)) > 0)
           AND (address_line2 IS NULL OR length(btrim(address_line2)) > 0)
           AND (city IS NULL OR length(btrim(city)) > 0)),
    CONSTRAINT ck_users_phone_format
        CHECK (phone IS NULL OR phone ~ '^\+?[0-9]{7,15}$'),
    CONSTRAINT ck_users_company_phone_format
        CHECK (company_phone IS NULL OR company_phone ~ '^\+?[0-9]{7,15}$'),
    CONSTRAINT fk_users_country
        FOREIGN KEY (country_id) REFERENCES countries (id),
    CONSTRAINT fk_users_document_type
        FOREIGN KEY (document_type_id) REFERENCES document_types (id)
);

COMMENT ON TABLE users IS
    'Personas del sistema. Dos identidades —nombre de usuario y correo— y ambas sirven para entrar.';
COMMENT ON COLUMN users.username IS
    'Inmutable y sin arroba (RN-SP-016). Se guarda TAL COMO SE ESCRIBIÓ; la unicidad ignora la caja.';
COMMENT ON COLUMN users.password_hash IS
    'Argon2id. Nunca en texto plano ni con hash reversible (security.md §3.2).';
COMMENT ON COLUMN users.status IS
    'ACTIVO | INACTIVO | BLOQUEADO | FTD_PENDIENTE. El último autentica y NO opera: espera el primer depósito (RF-SP-045).';
COMMENT ON COLUMN users.locked_until IS
    'Nulo = no bloqueada. Bloqueo automático por intentos; el manual va en status.';
COMMENT ON COLUMN users.last_login_at IS
    'Dato informativo, no señal de intrusión: RF-SP-034 lo sobrescribe en cada entrada.';
COMMENT ON COLUMN users.provisional_password_expires_at IS
    'Hasta cuándo vale la credencial que fijó otra persona (RF-SP-038). Nula = la credencial es del titular.';
COMMENT ON COLUMN users.country_id IS
    'Dónde está la persona (RN-SP-034). Obligatorio. Solo se asigna un país activo, y esa mitad de la regla vive en el caso de uso: declararla en el esquema haría fallar RF-SP-022.';
COMMENT ON COLUMN users.document_type_id IS
    'Tipo de documento (RN-SP-035). El catálogo solo lleva los de mayor de edad: esta FK ES la validación.';
COMMENT ON COLUMN users.document_number IS
    'Normalizado en mayúsculas. Único CON el tipo, y no se libera al eliminar (RN-SP-035).';
COMMENT ON COLUMN users.address_line2 IS
    'Complemento. Opcional POR NATURALEZA: una dirección puede no tenerlo, y eso no es un dato que falte.';
COMMENT ON COLUMN users.phone IS
    'Obligatorio en la API (RN-SP-037), nulable en el esquema por las filas anteriores a V71.';
COMMENT ON COLUMN users.company_phone IS
    'Teléfono de la empresa (RN-SP-037, 10-09-2026). OPCIONAL SIEMPRE: el nulo es un hecho, no un dato pendiente. Nunca NOT NULL.';

CREATE UNIQUE INDEX uq_users_username ON users (lower(username));
CREATE UNIQUE INDEX uq_users_document ON users (document_type_id, document_number) WHERE document_number IS NOT NULL;
CREATE INDEX ix_users_country_id ON users (country_id);
CREATE INDEX ix_users_busqueda
    ON users USING gin (
        f_unaccent(lower(username)) gin_trgm_ops,
        f_unaccent(lower(email)) gin_trgm_ops,
        f_unaccent(lower(first_name || ' ' || last_name)) gin_trgm_ops);
COMMENT ON INDEX ix_users_busqueda IS
    'Búsqueda por fragmento del listado de personas (RF-SP-025). Las expresiones son las del predicado.';

-- ---------------------------------------------------------------------------
-- Asignación persona-rol. Sin updated_at: no se modifica, se crea y se borra.
-- ---------------------------------------------------------------------------

CREATE TABLE user_roles (
    user_id    uuid        NOT NULL,
    role_id    uuid        NOT NULL,
    role_type  varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id, role_type) REFERENCES roles (id, role_type) ON DELETE RESTRICT
);

COMMENT ON TABLE user_roles IS
    'Asignación persona-rol. Sin updated_at: una asignación no se modifica, se crea y se borra.';
COMMENT ON COLUMN user_roles.role_type IS
    'COPIA de roles.role_type, atada por la FK compuesta (role_id, role_type). Existe para que `RN-SP-025` viva en el motor: un CHECK no consulta otra tabla y un indice unico no puede unir user_roles con roles. Es legitima porque roles.role_type NO ES EDITABLE, de modo que nunca hay que actualizarla.';

CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);
COMMENT ON INDEX ix_user_roles_role_id IS
    'Portadores de un rol. La clave primaria no sirve esta dirección (RF-SP-030 §2).';
-- RN-SP-025: como mucho un rol VENDEDOR por persona.
CREATE UNIQUE INDEX uq_user_roles_vendedor ON user_roles (user_id) WHERE role_type = 'VENDEDOR';

-- ---------------------------------------------------------------------------
-- Historial de membresías (RN-SP-014). `ends_at` es hasta cuándo se PAGÓ y
-- `closed_at` cuándo dejó de ser la actual; no son lo mismo, y retirar no toca
-- la primera. La EXCLUDE impide que dos filas de la misma persona se solapen.
-- ---------------------------------------------------------------------------

CREATE TABLE user_memberships (
    id            uuid        NOT NULL,
    user_id       uuid        NOT NULL,
    membership_id uuid        NOT NULL,
    started_at    timestamptz NOT NULL DEFAULT now(),
    ends_at       timestamptz NULL,
    closed_at     timestamptz NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_memberships PRIMARY KEY (id),
    CONSTRAINT ck_user_memberships_periodo
        CHECK (ends_at IS NULL OR ends_at > started_at),
    CONSTRAINT ck_user_memberships_cierre
        CHECK (closed_at IS NULL OR closed_at >= started_at),
    CONSTRAINT fk_user_memberships_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_memberships_membership
        FOREIGN KEY (membership_id) REFERENCES memberships (id) ON DELETE RESTRICT,
    CONSTRAINT ex_user_memberships_sin_solape
        EXCLUDE USING gist (
            user_id WITH =,
            tstzrange(started_at, COALESCE(LEAST(ends_at, closed_at), 'infinity'::timestamptz)) WITH &&)
);

COMMENT ON TABLE user_memberships IS
    'HISTORIAL de membresias. Una fila por membresia que alguien tuvo; la abierta (closed_at IS NULL) es la actual y hay como mucho una. Vigente = abierta Y dentro de fecha — no son lo mismo (RN-SP-014).';
COMMENT ON COLUMN user_memberships.ends_at IS
    'Nula = indefinida. Hasta cuando se PAGO, no cuando se cerro. Retirar NO la toca (RF-SP-033).';
COMMENT ON COLUMN user_memberships.closed_at IS
    'Nula = es la fila ABIERTA (la actual). Poblada = historial. NO es una marca de caducado: nadie la escribe por el paso del tiempo, solo al conceder otra o al retirar (RN-SP-014).';

CREATE UNIQUE INDEX uq_user_memberships_abierta ON user_memberships (user_id) WHERE closed_at IS NULL;
CREATE INDEX ix_user_memberships_membership_id ON user_memberships (membership_id) WHERE closed_at IS NULL;

-- ---------------------------------------------------------------------------
-- Estructura comercial con historial: quién es superior de quién y desde
-- cuándo. La fila cerrada se conserva porque dice a quién se atribuía cada
-- resultado; como mucho una vigente por persona.
-- ---------------------------------------------------------------------------

CREATE TABLE user_supervisors (
    id            uuid        PRIMARY KEY,
    user_id       uuid        NOT NULL,
    supervisor_id uuid        NOT NULL,
    started_at    timestamptz NOT NULL DEFAULT now(),
    ended_at      timestamptz NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_user_supervisors_no_self
        CHECK (user_id <> supervisor_id),
    CONSTRAINT ck_user_supervisors_periodo
        CHECK (ended_at IS NULL OR ended_at > started_at),
    CONSTRAINT fk_user_supervisors_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_supervisors_supervisor
        FOREIGN KEY (supervisor_id) REFERENCES users (id) ON DELETE RESTRICT
);

COMMENT ON TABLE user_supervisors IS
    'Estructura comercial con historial. La fila cerrada se conserva: dice a quién se atribuía cada resultado.';

CREATE UNIQUE INDEX uq_user_supervisors_vigente ON user_supervisors (user_id) WHERE ended_at IS NULL;
CREATE INDEX ix_user_supervisors_supervisor_vigente ON user_supervisors (supervisor_id) WHERE ended_at IS NULL;
COMMENT ON INDEX ix_user_supervisors_supervisor_vigente IS
    'Equipo vigente de una persona (RN-SP-022, RF-SP-042). Parcial: el historial cerrado no se consulta por esta vía.';

-- ---------------------------------------------------------------------------
-- Cuenta de una persona en un broker (RF-SP-045, RF-SP-053 a RF-SP-057). Sin
-- deleted_at: desvincular no está decidido. `broker_username` y `status` los
-- rellena el webhook del broker (RF-SP-054), que no existe todavía.
-- ---------------------------------------------------------------------------

CREATE TABLE user_brokers (
    id              uuid         PRIMARY KEY,
    user_id         uuid         NOT NULL,
    broker_id       uuid         NOT NULL,
    external_id     varchar(80)  NOT NULL,
    broker_username varchar(120) NULL,
    status          varchar(20)  NOT NULL DEFAULT 'REGISTER',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_brokers_cuenta UNIQUE (broker_id, external_id),
    CONSTRAINT ck_user_brokers_status
        CHECK (status IN ('REGISTER', 'FIRST_DEPOSIT')),
    CONSTRAINT fk_user_brokers_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_brokers_broker
        FOREIGN KEY (broker_id) REFERENCES brokers (id)
);

COMMENT ON TABLE user_brokers IS
    'Cuenta de una persona en un broker. Sin `deleted_at`: desvincular no está decidido.';
COMMENT ON COLUMN user_brokers.broker_username IS
    'NULL = el broker aún no lo ha confirmado (RN-SP-040). Lo rellena RF-SP-054.';
COMMENT ON COLUMN user_brokers.status IS
    'REGISTER | FIRST_DEPOSIT (RN-SP-045). Nace en REGISTER; lo mueve el webhook de RF-SP-054, que no existe todavía.';

CREATE INDEX ix_user_brokers_persona ON user_brokers (user_id);
CREATE INDEX ix_user_brokers_busqueda ON user_brokers USING gin (f_unaccent(lower(external_id)) gin_trgm_ops);
COMMENT ON INDEX ix_user_brokers_busqueda IS
    'Búsqueda por fragmento del número de cuenta (RF-SP-057). Las expresiones son las del predicado.';

-- ---------------------------------------------------------------------------
-- Sesiones renovables (RF-SP-034 a RF-SP-036). Solo el hash. Los tokens de una
-- misma sesión forman una FAMILIA; reutilizar uno ya rotado (ROTACION) es la
-- única señal de robo, y revoca la familia entera. Las demás razones son
-- revocaciones deliberadas.
-- ---------------------------------------------------------------------------

CREATE TABLE refresh_tokens (
    id                uuid         PRIMARY KEY,
    user_id           uuid         NOT NULL,
    token_hash        varchar(255) NOT NULL,
    family_id         uuid         NOT NULL,
    family_started_at timestamptz  NOT NULL,
    expires_at        timestamptz  NOT NULL,
    revoked_at        timestamptz  NULL,
    revoked_reason    varchar(20)  NULL,
    replaced_by_id    uuid         NULL,
    ip_address        inet         NULL,
    user_agent        text         NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_refresh_tokens_periodo
        CHECK (expires_at > created_at),
    CONSTRAINT ck_refresh_tokens_revocacion
        CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),
    CONSTRAINT ck_refresh_tokens_motivo
        CHECK (revoked_reason IS NULL OR revoked_reason IN
            ('ROTACION', 'CIERRE', 'ACCESO_RETIRADO', 'CAMBIO_CONTRASENA', 'SESION_AGOTADA', 'REUTILIZACION')),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id) ON DELETE RESTRICT
);

COMMENT ON TABLE refresh_tokens IS
    'Sesiones renovables. Solo el hash del token; el valor no se guarda nunca.';
COMMENT ON COLUMN refresh_tokens.revoked_reason IS
    'ROTACION es la única cuya reutilización significa robo. Las demás son revocaciones deliberadas.';

CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user   ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

-- ---------------------------------------------------------------------------
-- Permisos de un solo uso para recuperar la contraseña (RF-SP-040). Solo el
-- hash. Como mucho uno vigente por persona: pedir otro SUSTITUYE al anterior,
-- que es distinto de consumirlo.
-- ---------------------------------------------------------------------------

CREATE TABLE password_reset_permits (
    id            uuid         PRIMARY KEY,
    user_id       uuid         NOT NULL,
    permit_hash   varchar(255) NOT NULL,
    expires_at    timestamptz  NOT NULL,
    consumed_at   timestamptz  NULL,
    superseded_at timestamptz  NULL,
    requested_ip  inet         NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_password_reset_permits_periodo
        CHECK (expires_at > created_at),
    CONSTRAINT ck_password_reset_permits_final
        CHECK (consumed_at IS NULL OR superseded_at IS NULL),
    CONSTRAINT fk_password_reset_permits_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

COMMENT ON TABLE password_reset_permits IS
    'Permisos de un solo uso para recuperar la contraseña. Solo el hash; el valor no se guarda nunca.';
COMMENT ON COLUMN password_reset_permits.superseded_at IS
    'Se sustituyó porque la persona pidió otro. Distinto de consumed_at, que dice que completó el flujo.';

CREATE UNIQUE INDEX uq_password_reset_permits_hash ON password_reset_permits (permit_hash);
CREATE UNIQUE INDEX uq_password_reset_permits_vigente
    ON password_reset_permits (user_id) WHERE consumed_at IS NULL AND superseded_at IS NULL;
