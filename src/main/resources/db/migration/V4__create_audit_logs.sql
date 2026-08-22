-- =============================================================================
-- RF-SP-001 · T-01 — Los cuatro registros de auditoría del Art. V.8.
--
-- Van antes que `roles` porque V7 ya debe poder auditar el poblado de los roles
-- de sistema: sin estas tablas, los únicos roles del sistema serían también los
-- únicos sin respuesta a «quién los creó» (Art. V.7, V.8).
--
-- Son CUATRO tablas y no una (architecture.md §6.6). Cada una responde una
-- pregunta que las demás no pueden responder, y cada una declara NOT NULL lo
-- que en su contexto es obligatorio: un registro único no lo permite, porque lo
-- obligatorio de un caso es inaplicable en otro.
--
-- Ninguna lleva `created_at`, `updated_at` ni `deleted_at`. No son tablas de
-- negocio: son append-only y `occurred_at` es su única marca temporal.
--
-- El carácter append-only NO se fuerza todavía en la base de datos. Revocar
-- UPDATE y DELETE al usuario de la aplicación exige un modelo de usuarios por
-- entorno que hoy no está definido (`plan.md` §10, bloqueo 2 de `tasks.md`).
-- Debe resolverse antes del primer despliegue productivo: una auditoría
-- modificable no es evidencia.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- audit_change_log — quién creó qué, y quién editó qué
-- -----------------------------------------------------------------------------
CREATE TABLE audit_change_log (
    -- Núcleo común de las cuatro (architecture.md §6.6.1)
    id             uuid        PRIMARY KEY,
    occurred_at    timestamptz NOT NULL,
    actor_id       uuid        NULL,
    correlation_id uuid        NULL,
    ip_address     inet        NULL,
    user_agent     text        NULL,

    -- Columnas propias (architecture.md §6.6.2)
    module    varchar(10) NOT NULL,
    entity    varchar(50) NOT NULL,
    entity_id uuid        NOT NULL,
    action    varchar(20) NOT NULL,
    changes   jsonb       NOT NULL,

    CONSTRAINT ck_audit_change_log_action CHECK (action IN ('CREATE', 'UPDATE')),

    -- Las tres columnas de origen son nulables a la vez y por la misma razón:
    -- existen operaciones sin petición HTTP detrás (migraciones, tareas
    -- programadas). Con esta restricción, una fila sin IP significa
    -- inequívocamente «no vino de la red», y nunca «se olvidó registrarla»
    -- (Art. V.15).
    CONSTRAINT ck_audit_change_log_origen CHECK (
        (correlation_id IS NULL     AND ip_address IS NULL)
     OR (correlation_id IS NOT NULL AND ip_address IS NOT NULL)
    )
);

COMMENT ON TABLE audit_change_log IS
    'Auditoría de altas y ediciones (Art. V.8, architecture.md §6.6.2). Append-only.';
COMMENT ON COLUMN audit_change_log.changes IS
    'En CREATE, el estado inicial completo. En UPDATE, solo los campos modificados, con before/after.';
COMMENT ON COLUMN audit_change_log.actor_id IS
    'Sin clave foránea a users a propósito: el evento debe sobrevivir a la eliminación de la persona.';

CREATE INDEX ix_audit_change_log_entity      ON audit_change_log (entity, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_change_log_actor       ON audit_change_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_change_log_correlation ON audit_change_log (correlation_id);
CREATE INDEX ix_audit_change_log_ip          ON audit_change_log (ip_address);


-- -----------------------------------------------------------------------------
-- audit_deletion_log — quién eliminó qué, y por qué
-- -----------------------------------------------------------------------------
CREATE TABLE audit_deletion_log (
    id             uuid        PRIMARY KEY,
    occurred_at    timestamptz NOT NULL,
    actor_id       uuid        NULL,
    correlation_id uuid        NULL,
    ip_address     inet        NULL,
    user_agent     text        NULL,

    module        varchar(10) NOT NULL,
    entity        varchar(50) NOT NULL,
    entity_id     uuid        NOT NULL,
    deletion_type varchar(20) NOT NULL,
    reason        text        NULL,
    snapshot      jsonb       NOT NULL,

    CONSTRAINT ck_audit_deletion_log_type
        CHECK (deletion_type IN ('LOGICAL', 'PHYSICAL', 'ASSOCIATION')),

    -- El motivo es obligatorio salvo en ASSOCIATION (Art. V.13), y no basta
    -- con enviarlo en blanco. La restricción exige CONTENIDO, no longitud: un
    -- motivo de un solo carácter la satisface. Es deliberado —se decidió no
    -- elevar el mínimo para no imponer fricción a quien sí redacta un motivo
    -- útil— y quedó relajado desde diez caracteres al aprobarse el plan de
    -- `RF-SP-009` (architecture.md §6.6.3).
    -- Un OR con NULL no es FALSE: si el motivo viniera en nulo, la segunda
    -- rama daría NULL, el CHECK aceptaría la fila y la obligación no existiría.
    -- Por eso la presencia se exige aparte del contenido.
    CONSTRAINT ck_deletion_reason CHECK (
        deletion_type = 'ASSOCIATION'
     OR (reason IS NOT NULL AND char_length(btrim(reason)) > 0)
    ),

    CONSTRAINT ck_audit_deletion_log_origen CHECK (
        (correlation_id IS NULL     AND ip_address IS NULL)
     OR (correlation_id IS NOT NULL AND ip_address IS NOT NULL)
    )
);

COMMENT ON TABLE audit_deletion_log IS
    'Auditoría de eliminaciones (Art. V.8, V.13, architecture.md §6.6.3). Append-only.';
COMMENT ON COLUMN audit_deletion_log.snapshot IS
    'Estado completo al eliminarse. Sin él la fila dice que un uuid fue eliminado y nadie recuerda qué era.';

CREATE INDEX ix_audit_deletion_log_entity      ON audit_deletion_log (entity, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_deletion_log_actor       ON audit_deletion_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_deletion_log_correlation ON audit_deletion_log (correlation_id);
CREATE INDEX ix_audit_deletion_log_ip          ON audit_deletion_log (ip_address);


-- -----------------------------------------------------------------------------
-- audit_error_log — a quién le falló qué, sobre qué recurso
-- -----------------------------------------------------------------------------
CREATE TABLE audit_error_log (
    id             uuid        PRIMARY KEY,
    occurred_at    timestamptz NOT NULL,
    actor_id       uuid        NULL,
    correlation_id uuid        NULL,
    ip_address     inet        NULL,
    user_agent     text        NULL,

    resource    varchar(100) NOT NULL,
    entity_id   uuid         NULL,
    operation   varchar(100) NOT NULL,
    error_code  varchar(50)  NOT NULL,
    error_type  varchar(20)  NOT NULL,
    http_status smallint     NOT NULL,
    severity    varchar(20)  NOT NULL,
    message     text         NOT NULL,

    CONSTRAINT ck_audit_error_log_type
        CHECK (error_type IN ('BUSINESS_RULE', 'INTEGRATION', 'UNHANDLED')),

    CONSTRAINT ck_audit_error_log_severity CHECK (severity IN ('MEDIA', 'ALTA')),

    -- Qué estados NO puede registrar este registro (architecture.md §6.6.4,
    -- declarado al aprobarse el plan de `RF-SP-013`). Quedan fuera la
    -- validación de formato (400), el 401, el 404 y —la frontera que más
    -- importa— la denegación de autorización (403), que va a
    -- audit_security_log porque no es un fallo del sistema sino el sistema
    -- funcionando.
    --
    -- Con esta restricción, escribir por descuido un 403 aquí deja de producir
    -- un dato incorrecto que nadie nota y pasa a ser un INSERT que falla.
    --
    -- Los estados admitidos NO se enumeran a propósito: 409 y 422 de regla de
    -- negocio, 5xx no controlados, y el 200 o el 503 de un fallo de
    -- integración. Enumerarlos obligaría a alterar la restricción cada vez que
    -- un requerimiento estrenara un estado legítimo.
    CONSTRAINT ck_audit_error_log_status CHECK (http_status NOT IN (400, 401, 403, 404)),

    CONSTRAINT ck_audit_error_log_origen CHECK (
        (correlation_id IS NULL     AND ip_address IS NULL)
     OR (correlation_id IS NOT NULL AND ip_address IS NOT NULL)
    )
);

COMMENT ON TABLE audit_error_log IS
    'Auditoría de fallos y rechazos por regla de negocio (Art. V.8, architecture.md §6.6.4). Append-only.';
COMMENT ON COLUMN audit_error_log.message IS
    'Mensaje saneado: sin trazas, SQL, rutas ni versiones (Art. VI.5). El detalle técnico va al log.';

-- En esta tabla el eje de entidad es `resource`, no `entity`.
CREATE INDEX ix_audit_error_log_resource    ON audit_error_log (resource, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_error_log_actor       ON audit_error_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_error_log_correlation ON audit_error_log (correlation_id);
CREATE INDEX ix_audit_error_log_ip          ON audit_error_log (ip_address);


-- -----------------------------------------------------------------------------
-- audit_security_log — quién intentó qué contra el control de acceso
-- -----------------------------------------------------------------------------
CREATE TABLE audit_security_log (
    id             uuid        PRIMARY KEY,
    occurred_at    timestamptz NOT NULL,
    actor_id       uuid        NULL,
    correlation_id uuid        NULL,
    ip_address     inet        NULL,
    user_agent     text        NULL,

    -- Columnas propias (security.md §8.2)
    event_type     varchar(50) NOT NULL,
    severity       varchar(20) NOT NULL,
    outcome        varchar(10) NOT NULL,
    target_user_id uuid        NULL,
    detail         jsonb       NULL,

    -- Dominio cerrado de DIECINUEVE códigos, fijado por `RF-SP-014` §2.
    --
    -- V4 lo declaraba en su plan por referencia a `security.md` §8.1, que está
    -- escrito en prosa. Un dominio cerrado que ningún literal fija no es un
    -- dominio cerrado: cada requerimiento que emitiera un evento inventaría su
    -- propia forma de escribirlo —LOGIN_FAILED, login_failure, FALLO_LOGIN— y
    -- el filtro de `RF-SP-014` devolvería resultados incompletos sin que nada
    -- fallara.
    --
    -- Se codifican también los eventos de requerimientos aún sin plan: un valor
    -- que nadie escribe es inerte, y una migración de alteración por
    -- requerimiento no lo es.
    CONSTRAINT ck_audit_security_log_event_type CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCOUNT_LOCKED', 'REFRESH_TOKEN_REUSE',
        'LOGOUT', 'AUTHORIZATION_DENIED',
        'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED', 'ROLE_PERMISSIONS_CHANGED',
        'USER_CREATED', 'EMAIL_CHANGED',
        'USER_ROLES_ASSIGNED', 'USER_ROLES_REVOKED', 'USER_STATUS_CHANGED', 'USER_DELETED',
        'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'SECURITY_AUDIT_READ'
    )),

    CONSTRAINT ck_audit_security_log_severity
        CHECK (severity IN ('INFORMATIVA', 'MEDIA', 'ALTA')),

    CONSTRAINT ck_audit_security_log_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE')),

    CONSTRAINT ck_audit_security_log_origen CHECK (
        (correlation_id IS NULL     AND ip_address IS NULL)
     OR (correlation_id IS NOT NULL AND ip_address IS NOT NULL)
    )
);

COMMENT ON TABLE audit_security_log IS
    'Auditoría del control de acceso (Art. V.8, security.md §8). Append-only y sin purga silenciosa.';
COMMENT ON COLUMN audit_security_log.target_user_id IS
    'Usuario OBJETO del evento, distinto del actor. Sin él, un bloqueo no dice sobre quién recayó.';
COMMENT ON COLUMN audit_security_log.severity IS
    'No está ligada a event_type: AUTHORIZATION_DENIED es MEDIA desde la capa de seguridad y ALTA desde RN-SEG-011.';

-- El eje de entidad de esta tabla es el usuario afectado.
CREATE INDEX ix_audit_security_log_target      ON audit_security_log (target_user_id, occurred_at DESC);
CREATE INDEX ix_audit_security_log_actor       ON audit_security_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_security_log_correlation ON audit_security_log (correlation_id);

-- Índice compuesto y no simple sobre la IP (`RF-SP-014` §2): un intento de
-- fuerza bruta se reconoce por el origen, y en un LOGIN_FAILURE el actor es
-- nulo —no hay identidad probada todavía—, de modo que la dirección de red es
-- el único identificador disponible y siempre se consulta acotada por fecha.
CREATE INDEX ix_audit_security_log_ip ON audit_security_log (ip_address, occurred_at DESC);


-- -----------------------------------------------------------------------------
-- v_audit_timeline — consulta transversal (architecture.md §6.6.6)
--
-- Las cuatro tablas están separadas PARA ESCRIBIR. Para leer hay dos preguntas
-- legítimas y frecuentes que las cruzan: «todo lo que le pasó a esta entidad» y
-- «todo lo que hizo esta persona».
--
-- La vista es de SOLO LECTURA: nada se escribe a través de ella. Cada tabla se
-- escribe por su propio camino, con sus propias obligaciones.
-- -----------------------------------------------------------------------------
CREATE VIEW v_audit_timeline AS
    SELECT 'CHANGE' AS audit_type, id, occurred_at, actor_id, correlation_id,
           ip_address, entity, entity_id, action AS summary
      FROM audit_change_log
    UNION ALL
    SELECT 'DELETION', id, occurred_at, actor_id, correlation_id,
           ip_address, entity, entity_id, deletion_type
      FROM audit_deletion_log
    UNION ALL
    SELECT 'ERROR', id, occurred_at, actor_id, correlation_id,
           ip_address, resource, entity_id, error_code
      FROM audit_error_log
    UNION ALL
    SELECT 'SECURITY', id, occurred_at, actor_id, correlation_id,
           ip_address, 'security', target_user_id, event_type
      FROM audit_security_log;

COMMENT ON VIEW v_audit_timeline IS
    'Solo lectura. Consultarla exige los cuatro permisos de auditoría (security.md §4.4).';
