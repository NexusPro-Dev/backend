-- =============================================================================
-- V94 — Las clases en vivo por Zoom (RF-AC-044 · T-01; requirements/ac.md
-- v0.23.1 §5.2.15 y §8.10 a §8.13, RN-AC-025 a RN-AC-030; security.md §4.4
-- v0.129.0; 09-10-2026).
--
-- Una clase en vivo, su reunión de Zoom —de la que solo se guarda el
-- identificador: ni el enlace general ni la contraseña (RN-AC-026)—, sus dos
-- listas de acceso, iguales a las del curso (RN-AC-025), y quién se registró
-- para entrar con su enlace personal (RN-AC-027).
--
-- Se programa con INICIO Y FIN, por decisión del responsable del proyecto; que
-- haya terminado se calcula con ends_at. SIN deleted_at: cancelar conserva la
-- fila con fecha y motivo (RN-AC-029).
--
-- Y los trece permisos (RN-SEG-014, RN-SEG-015):
--   * seis de administración a SUPERADMIN y ADMIN;
--   * cinco propios a todo rol que porte courses:teach (RN-AC-028);
--   * dos del alumno a todo rol que porte courses:learn, como V47.
-- Y un tipo de evento de seguridad: la entrega del enlace de anfitrión
-- (RN-AC-030).
--
-- GUARDAS: 232 en el catálogo / SUPERADMIN 232 / ADMIN 230 / contención.
-- =============================================================================

CREATE TABLE live_sessions (
    id                   uuid         PRIMARY KEY,
    course_id            uuid         NULL,
    title                varchar(150) NOT NULL,
    description          text         NULL,
    starts_at            timestamptz  NOT NULL,
    ends_at              timestamptz  NOT NULL,
    zoom_meeting_id      bigint       NOT NULL,
    status               varchar(20)  NOT NULL DEFAULT 'PROGRAMADA',
    cancelled_at         timestamptz  NULL,
    cancellation_reason  varchar(500) NULL,
    created_by           uuid         NOT NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_live_sessions_course       FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_live_sessions_created_by   FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT uq_live_sessions_zoom_meeting UNIQUE (zoom_meeting_id),
    CONSTRAINT ck_live_sessions_status       CHECK (status IN ('PROGRAMADA', 'CANCELADA')),
    CONSTRAINT ck_live_sessions_span         CHECK (ends_at >= starts_at + interval '15 minutes'
                                                    AND ends_at <= starts_at + interval '10 hours'),
    CONSTRAINT ck_live_sessions_cancellation CHECK ((status = 'CANCELADA')
                                                    = (cancelled_at IS NOT NULL
                                                       AND cancellation_reason IS NOT NULL))
);

CREATE INDEX ix_live_sessions_starts_at ON live_sessions (starts_at);
CREATE INDEX ix_live_sessions_course ON live_sessions (course_id);

CREATE TABLE live_session_memberships (
    live_session_id  uuid NOT NULL,
    membership_id    uuid NOT NULL,
    CONSTRAINT pk_live_session_memberships PRIMARY KEY (live_session_id, membership_id),
    CONSTRAINT fk_live_session_memberships_session
        FOREIGN KEY (live_session_id) REFERENCES live_sessions (id),
    CONSTRAINT fk_live_session_memberships_membership
        FOREIGN KEY (membership_id) REFERENCES memberships (id)
);

CREATE TABLE live_session_products (
    live_session_id  uuid NOT NULL,
    product_id       uuid NOT NULL,
    CONSTRAINT pk_live_session_products PRIMARY KEY (live_session_id, product_id),
    CONSTRAINT fk_live_session_products_session
        FOREIGN KEY (live_session_id) REFERENCES live_sessions (id),
    CONSTRAINT fk_live_session_products_product
        FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE live_session_registrations (
    live_session_id     uuid          NOT NULL,
    user_id             uuid          NOT NULL,
    zoom_registrant_id  varchar(64)   NOT NULL,
    join_url            varchar(1000) NOT NULL,
    registered_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_live_session_registrations PRIMARY KEY (live_session_id, user_id),
    CONSTRAINT fk_live_session_registrations_session
        FOREIGN KEY (live_session_id) REFERENCES live_sessions (id),
    CONSTRAINT fk_live_session_registrations_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

COMMENT ON TABLE live_sessions IS
    'Una clase en vivo por Zoom (AC, RN-AC-025 a RN-AC-030). De Zoom solo se guarda el identificador de la reunion: ni el enlace general ni la contrasena, que dejarian entrar sin pasar por la plataforma. Cancelar conserva la fila con fecha y motivo; terminada se calcula con ends_at.';

COMMENT ON COLUMN live_session_registrations.join_url IS
    'El enlace PERSONAL que Zoom dio a esta persona al registrarla (RN-AC-027). Solo se le devuelve a ella.';

-- -----------------------------------------------------------------------------
-- Los permisos.
-- -----------------------------------------------------------------------------
INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a11a5c-4000-7001-9c4f-5e7adc000034', 'live-sessions:list', 'live-sessions', 'list',
 'Consultar las clases en vivo', 'Listar todas las clases en vivo por GET /live-sessions (RF-AC-042).'),
('01a11a5c-4000-7002-9c4f-5e7adc000035', 'live-sessions:read', 'live-sessions', 'read',
 'Ver una clase en vivo', 'El detalle de cualquier clase con sus registrados por GET /live-sessions/{id} (RF-AC-043).'),
('01a11a5c-4000-7003-9c4f-5e7adc000036', 'live-sessions:create', 'live-sessions', 'create',
 'Programar una clase en vivo', 'Programar cualquier clase y crear su reunion de Zoom por POST /live-sessions (RF-AC-044).'),
('01a11a5c-4000-7004-9c4f-5e7adc000037', 'live-sessions:update', 'live-sessions', 'update',
 'Corregir una clase en vivo', 'Corregir cualquier clase por PATCH /live-sessions/{id} (RF-AC-045).'),
('01a11a5c-4000-7005-9c4f-5e7adc000038', 'live-sessions:cancel', 'live-sessions', 'cancel',
 'Cancelar una clase en vivo', 'Cancelar cualquier clase y borrar su reunion por POST /live-sessions/{id}/cancellation (RF-AC-046).'),
('01a11a5c-4000-7006-9c4f-5e7adc000039', 'live-sessions:host', 'live-sessions', 'host',
 'Iniciar una clase en vivo como anfitrion', 'El enlace de anfitrion de cualquier clase por POST /live-sessions/{id}/host-link (RF-AC-047). Se audita.'),
('01a11a5c-4000-7007-9c4f-5e7adc00003a', 'live-sessions:list-own', 'live-sessions', 'list-own',
 'Consultar mis clases en vivo', 'Listar las clases de los cursos que dicta por GET /live-sessions/mine (RF-AC-048, RN-AC-028).'),
('01a11a5c-4000-7008-9c4f-5e7adc00003b', 'live-sessions:create-own', 'live-sessions', 'create-own',
 'Programar una clase en vivo de mi curso', 'Programar una clase de un curso que dicta por POST /live-sessions/mine (RF-AC-049).'),
('01a11a5c-4000-7009-9c4f-5e7adc00003c', 'live-sessions:update-own', 'live-sessions', 'update-own',
 'Corregir una clase en vivo de mi curso', 'Corregir una clase de un curso que dicta por PATCH /live-sessions/mine/{id} (RF-AC-050).'),
('01a11a5c-4000-700a-9c4f-5e7adc00003d', 'live-sessions:cancel-own', 'live-sessions', 'cancel-own',
 'Cancelar una clase en vivo de mi curso', 'Cancelar una clase de un curso que dicta por POST /live-sessions/mine/{id}/cancellation (RF-AC-051).'),
('01a11a5c-4000-700b-9c4f-5e7adc00003e', 'live-sessions:host-own', 'live-sessions', 'host-own',
 'Iniciar como anfitrion una clase de mi curso', 'El enlace de anfitrion de una clase de un curso que dicta por POST /live-sessions/mine/{id}/host-link (RF-AC-052). Se audita.'),
('01a11a5c-4000-700c-9c4f-5e7adc00003f', 'live-sessions:learn', 'live-sessions', 'learn',
 'Ver las clases en vivo como alumno', 'Las clases programadas que no han terminado, con lo que se le abre, por GET /live-sessions/available (RF-AC-053).'),
('01a11a5c-4000-700d-9c4f-5e7adc000040', 'live-sessions:join', 'live-sessions', 'join',
 'Entrar a una clase en vivo', 'Registrarse en Zoom y recibir el enlace personal por POST /live-sessions/available/{id}/registration (RF-AC-054).');

-- Administración: SUPERADMIN y ADMIN.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
 CROSS JOIN permissions p
 WHERE p.code IN ('live-sessions:list', 'live-sessions:read', 'live-sessions:create',
                  'live-sessions:update', 'live-sessions:cancel', 'live-sessions:host')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- Lo propio del instructor: donde está courses:teach.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
  FROM role_permissions rp
  JOIN permissions t ON t.id = rp.permission_id AND t.code = 'courses:teach'
 CROSS JOIN permissions p
 WHERE p.code IN ('live-sessions:list-own', 'live-sessions:create-own',
                  'live-sessions:update-own', 'live-sessions:cancel-own',
                  'live-sessions:host-own')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- El alumno: donde está courses:learn.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
  FROM role_permissions rp
  JOIN permissions t ON t.id = rp.permission_id AND t.code = 'courses:learn'
 CROSS JOIN permissions p
 WHERE p.code IN ('live-sessions:learn', 'live-sessions:join')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- -----------------------------------------------------------------------------
-- El evento de seguridad de la entrega del enlace de anfitrión (RN-AC-030).
-- -----------------------------------------------------------------------------
ALTER TABLE audit_security_log DROP CONSTRAINT ck_audit_security_log_event_type;
ALTER TABLE audit_security_log ADD CONSTRAINT ck_audit_security_log_event_type
    CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCOUNT_LOCKED', 'REFRESH_TOKEN_REUSE', 'LOGOUT',
        'AUTHORIZATION_DENIED',
        'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED', 'ROLE_PERMISSIONS_CHANGED',
        'USER_CREATED', 'EMAIL_CHANGED', 'USER_ROLES_ASSIGNED', 'USER_ROLES_REVOKED',
        'USER_STATUS_CHANGED', 'USER_DELETED', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'SECURITY_AUDIT_READ', 'RATE_LIMIT_EXCEEDED', 'SESSION_TOKENS_PURGED',
        'MFA_ENABLED', 'MFA_DISABLED', 'MFA_RESET', 'MFA_VERIFICATION_FAILED',
        'MFA_RECOVERY_CODE_USED', 'MFA_RECOVERY_CODES_REGENERATED',
        'ROLE_MFA_REQUIREMENT_CHANGED',
        'LIVE_SESSION_HOST_LINK_ISSUED'));

-- -----------------------------------------------------------------------------
-- Guardas.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    filas      integer;
    de_raiz    integer;
    de_admin   integer;
    sin_padre  integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 232 THEN
        RAISE EXCEPTION 'V94: el catálogo debe tener 232 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 232 OR de_admin <> 230 THEN
        RAISE EXCEPTION 'V94: SUPERADMIN debe portar 232 permisos y ADMIN 230; tienen % y %', de_raiz, de_admin;
    END IF;

    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V94: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
