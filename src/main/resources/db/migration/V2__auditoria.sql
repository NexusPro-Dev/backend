-- =============================================================================
-- V2 — Los cuatro registros de auditoría y la línea de tiempo (Art. V.8,
-- architecture.md §6.6).
--
-- APPEND-ONLY: ninguna fila se edita ni se borra, y por eso no hay updated_at
-- ni deleted_at. Comparten el núcleo —quién, cuándo, desde dónde— y cada uno
-- añade lo suyo. `actor_id` NO lleva clave foránea a `users` a propósito: el
-- evento debe sobrevivir a la eliminación de la persona.
--
-- `ck_*_origen`: correlation_id e ip_address van juntos o no van. Un evento
-- que nace de una petición HTTP tiene los dos; uno que nace de una migración
-- o de un trabajo programado, ninguno. Medio origen sería un origen inventado.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Cambios: altas y ediciones (§6.6.2). En CREATE, `changes` lleva el estado
-- inicial completo; en UPDATE, solo los campos modificados con before/after.
-- ---------------------------------------------------------------------------

CREATE TABLE audit_change_log (
    id             uuid        PRIMARY KEY,
    occurred_at    timestamptz NOT NULL,
    actor_id       uuid        NULL,
    correlation_id uuid        NULL,
    ip_address     inet        NULL,
    user_agent     text        NULL,
    module         varchar(10) NOT NULL,
    entity         varchar(50) NOT NULL,
    entity_id      uuid        NOT NULL,
    action         varchar(20) NOT NULL,
    changes        jsonb       NOT NULL,

    CONSTRAINT ck_audit_change_log_action
        CHECK (action IN ('CREATE', 'UPDATE')),
    CONSTRAINT ck_audit_change_log_origen
        CHECK ((correlation_id IS NULL AND ip_address IS NULL)
            OR (correlation_id IS NOT NULL AND ip_address IS NOT NULL))
);

COMMENT ON TABLE audit_change_log IS
    'Auditoría de altas y ediciones (Art. V.8, architecture.md §6.6.2). Append-only.';
COMMENT ON COLUMN audit_change_log.actor_id IS
    'Sin clave foránea a users a propósito: el evento debe sobrevivir a la eliminación de la persona.';
COMMENT ON COLUMN audit_change_log.changes IS
    'En CREATE, el estado inicial completo. En UPDATE, solo los campos modificados, con before/after.';

CREATE INDEX ix_audit_change_log_occurred_at ON audit_change_log (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_change_log_entity      ON audit_change_log (entity, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_change_log_actor       ON audit_change_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_change_log_correlation ON audit_change_log (correlation_id);
CREATE INDEX ix_audit_change_log_ip          ON audit_change_log (ip_address);

-- ---------------------------------------------------------------------------
-- Eliminaciones (§6.6.3). El motivo es obligatorio salvo en ASSOCIATION
-- (Art. V.13): quitar una fila de asociación —un producto de un paquete, una
-- tasa de un producto— no es eliminar una entidad. La instantánea SIEMPRE va:
-- sin ella la fila dice que un uuid fue eliminado y nadie recuerda qué era.
-- ---------------------------------------------------------------------------

CREATE TABLE audit_deletion_log (
    id             uuid        PRIMARY KEY,
    occurred_at    timestamptz NOT NULL,
    actor_id       uuid        NULL,
    correlation_id uuid        NULL,
    ip_address     inet        NULL,
    user_agent     text        NULL,
    module         varchar(10) NOT NULL,
    entity         varchar(50) NOT NULL,
    entity_id      uuid        NOT NULL,
    deletion_type  varchar(20) NOT NULL,
    reason         text        NULL,
    snapshot       jsonb       NOT NULL,

    CONSTRAINT ck_audit_deletion_log_type
        CHECK (deletion_type IN ('LOGICAL', 'PHYSICAL', 'ASSOCIATION')),
    CONSTRAINT ck_deletion_reason
        CHECK (deletion_type = 'ASSOCIATION'
            OR (reason IS NOT NULL AND char_length(btrim(reason)) > 0)),
    CONSTRAINT ck_audit_deletion_log_origen
        CHECK ((correlation_id IS NULL AND ip_address IS NULL)
            OR (correlation_id IS NOT NULL AND ip_address IS NOT NULL))
);

COMMENT ON TABLE audit_deletion_log IS
    'Auditoría de eliminaciones (Art. V.8, V.13, architecture.md §6.6.3). Append-only.';
COMMENT ON COLUMN audit_deletion_log.snapshot IS
    'Estado completo al eliminarse. Sin él la fila dice que un uuid fue eliminado y nadie recuerda qué era.';

CREATE INDEX ix_audit_deletion_log_occurred_at ON audit_deletion_log (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_deletion_log_entity      ON audit_deletion_log (entity, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_deletion_log_actor       ON audit_deletion_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_deletion_log_correlation ON audit_deletion_log (correlation_id);
CREATE INDEX ix_audit_deletion_log_ip          ON audit_deletion_log (ip_address);
-- Búsqueda por texto sobre el motivo (RF-SP-012); parcial porque ASSOCIATION no lleva.
CREATE INDEX ix_audit_deletion_log_reason_busqueda
    ON audit_deletion_log USING gin (f_unaccent(lower(reason)) gin_trgm_ops)
    WHERE reason IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Errores (§6.6.4): fallos no controlados y rechazos por regla de negocio.
-- Los 400/401/403/404 NO se registran (ck_audit_error_log_status): son parte
-- del uso normal de la API y llenarían el registro de ruido. El mensaje va
-- saneado (Art. VI.5): sin trazas, SQL, rutas ni versiones.
-- ---------------------------------------------------------------------------

CREATE TABLE audit_error_log (
    id             uuid         PRIMARY KEY,
    occurred_at    timestamptz  NOT NULL,
    actor_id       uuid         NULL,
    correlation_id uuid         NULL,
    ip_address     inet         NULL,
    user_agent     text         NULL,
    resource       varchar(100) NOT NULL,
    entity_id      uuid         NULL,
    operation      varchar(100) NOT NULL,
    error_code     varchar(50)  NOT NULL,
    error_type     varchar(20)  NOT NULL,
    http_status    smallint     NOT NULL,
    severity       varchar(20)  NOT NULL,
    message        text         NOT NULL,

    CONSTRAINT ck_audit_error_log_type
        CHECK (error_type IN ('BUSINESS_RULE', 'INTEGRATION', 'UNHANDLED')),
    CONSTRAINT ck_audit_error_log_severity
        CHECK (severity IN ('MEDIA', 'ALTA')),
    CONSTRAINT ck_audit_error_log_status
        CHECK (http_status NOT IN (400, 401, 403, 404)),
    CONSTRAINT ck_audit_error_log_origen
        CHECK ((correlation_id IS NULL AND ip_address IS NULL)
            OR (correlation_id IS NOT NULL AND ip_address IS NOT NULL))
);

COMMENT ON TABLE audit_error_log IS
    'Auditoría de fallos y rechazos por regla de negocio (Art. V.8, architecture.md §6.6.4). Append-only.';
COMMENT ON COLUMN audit_error_log.message IS
    'Mensaje saneado: sin trazas, SQL, rutas ni versiones (Art. VI.5). El detalle técnico va al log.';

CREATE INDEX ix_audit_error_log_occurred_at ON audit_error_log (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_error_log_resource    ON audit_error_log (resource, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_error_log_error_code  ON audit_error_log (error_code, occurred_at DESC);
CREATE INDEX ix_audit_error_log_actor       ON audit_error_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_error_log_correlation ON audit_error_log (correlation_id);
CREATE INDEX ix_audit_error_log_ip          ON audit_error_log (ip_address);

-- ---------------------------------------------------------------------------
-- Seguridad (security.md §8): autenticación y autorización. El catálogo de
-- tipos de evento es CERRADO y ampliarlo exige migración. `target_user_id` es
-- el usuario OBJETO del evento, distinto del actor: sin él, un bloqueo no
-- dice sobre quién recayó. La severidad no está ligada al tipo:
-- AUTHORIZATION_DENIED es MEDIA desde la capa de seguridad y ALTA desde
-- RN-SEG-011 (alguien operando sobre su propio rol).
-- ---------------------------------------------------------------------------

CREATE TABLE audit_security_log (
    id             uuid        PRIMARY KEY,
    occurred_at    timestamptz NOT NULL,
    actor_id       uuid        NULL,
    correlation_id uuid        NULL,
    ip_address     inet        NULL,
    user_agent     text        NULL,
    event_type     varchar(50) NOT NULL,
    severity       varchar(20) NOT NULL,
    outcome        varchar(10) NOT NULL,
    target_user_id uuid        NULL,
    detail         jsonb       NULL,

    CONSTRAINT ck_audit_security_log_event_type
        CHECK (event_type IN (
            'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCOUNT_LOCKED', 'REFRESH_TOKEN_REUSE', 'LOGOUT',
            'AUTHORIZATION_DENIED',
            'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED', 'ROLE_PERMISSIONS_CHANGED',
            'USER_CREATED', 'EMAIL_CHANGED', 'USER_ROLES_ASSIGNED', 'USER_ROLES_REVOKED',
            'USER_STATUS_CHANGED', 'USER_DELETED', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
            'SECURITY_AUDIT_READ', 'RATE_LIMIT_EXCEEDED', 'SESSION_TOKENS_PURGED')),
    CONSTRAINT ck_audit_security_log_severity
        CHECK (severity IN ('INFORMATIVA', 'MEDIA', 'ALTA')),
    CONSTRAINT ck_audit_security_log_outcome
        CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT ck_audit_security_log_origen
        CHECK ((correlation_id IS NULL AND ip_address IS NULL)
            OR (correlation_id IS NOT NULL AND ip_address IS NOT NULL))
);

COMMENT ON TABLE audit_security_log IS
    'Auditoría del control de acceso (Art. V.8, security.md §8). Append-only y sin purga silenciosa.';
COMMENT ON COLUMN audit_security_log.event_type IS
    'Catálogo cerrado de veintiún códigos (security.md §8.1). Ampliarlo exige migración.';
COMMENT ON COLUMN audit_security_log.severity IS
    'No está ligada a event_type: AUTHORIZATION_DENIED es MEDIA desde la capa de seguridad y ALTA desde RN-SEG-011.';
COMMENT ON COLUMN audit_security_log.target_user_id IS
    'Usuario OBJETO del evento, distinto del actor. Sin él, un bloqueo no dice sobre quién recayó.';

CREATE INDEX ix_audit_security_log_occurred_at ON audit_security_log (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_security_log_actor       ON audit_security_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_security_log_target      ON audit_security_log (target_user_id, occurred_at DESC);
CREATE INDEX ix_audit_security_log_correlation ON audit_security_log (correlation_id);
CREATE INDEX ix_audit_security_log_ip          ON audit_security_log (ip_address, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- La línea de tiempo: los cuatro registros en una sola lectura, para quien
-- porta los cuatro permisos de auditoría (security.md §4.4). Solo lectura.
-- ---------------------------------------------------------------------------

CREATE VIEW v_audit_timeline AS
    SELECT 'CHANGE'   AS audit_type, id, occurred_at, actor_id, correlation_id, ip_address,
           entity, entity_id, action AS summary
      FROM audit_change_log
    UNION ALL
    SELECT 'DELETION', id, occurred_at, actor_id, correlation_id, ip_address,
           entity, entity_id, deletion_type
      FROM audit_deletion_log
    UNION ALL
    SELECT 'ERROR', id, occurred_at, actor_id, correlation_id, ip_address,
           resource, entity_id, error_code
      FROM audit_error_log
    UNION ALL
    SELECT 'SECURITY', id, occurred_at, actor_id, correlation_id, ip_address,
           'security'::varchar, target_user_id, event_type
      FROM audit_security_log;

COMMENT ON VIEW v_audit_timeline IS
    'Solo lectura. Consultarla exige los cuatro permisos de auditoría (security.md §4.4).';

-- ---------------------------------------------------------------------------
-- Qué se le pidió al sistema y qué respondió (Art. XV.2). No es auditoría de
-- negocio: es observabilidad. Sin cuerpo ni cabeceras. `status` nulo cuando la
-- petición se abortó sin respuesta —un cero fingido diría que el sistema
-- respondió cero— y `duration_ms` es lo que hace verificables los umbrales
-- p95 del Art. XV.9.
-- ---------------------------------------------------------------------------

CREATE TABLE request_log (
    id             uuid          PRIMARY KEY,
    occurred_at    timestamptz   NOT NULL,
    correlation_id uuid          NOT NULL,
    actor_id       uuid          NULL,
    method         varchar(10)   NOT NULL,
    path           varchar(2048) NOT NULL,
    query_string   text          NULL,
    status         smallint      NULL,
    duration_ms    integer       NOT NULL,
    ip_address     inet          NULL,
    user_agent     text          NULL,

    CONSTRAINT ck_request_log_status
        CHECK (status IS NULL OR (status >= 100 AND status <= 599)),
    CONSTRAINT ck_request_log_duration
        CHECK (duration_ms >= 0)
);

COMMENT ON TABLE request_log IS
    'Qué se le pidió al sistema y qué respondió (Art. XV.2). Append-only. Sin cuerpo ni cabeceras.';
COMMENT ON COLUMN request_log.actor_id IS
    'Nulo = anónimo. Sin clave foránea a users: la fila debe sobrevivir a la eliminación de la persona.';
COMMENT ON COLUMN request_log.status IS
    'Nulo cuando la petición se abortó sin respuesta. Un cero fingido diría que el sistema respondió cero.';
COMMENT ON COLUMN request_log.duration_ms IS
    'Lo que permite medir los umbrales p95 del Art. XV.9, que hasta ahora eran inverificables.';

CREATE INDEX ix_request_log_occurred_at    ON request_log (occurred_at DESC, id DESC);
CREATE INDEX ix_request_log_correlation_id ON request_log (correlation_id);
CREATE INDEX ix_request_log_actor          ON request_log (actor_id, occurred_at DESC);
