-- =============================================================================
-- V75 — El segundo factor (RF-SP-071 · T-01; requirements/sp.md v1.98.0
-- §10.1, §10.2, §10.22 a §10.24; security.md §3.3, §4.4, §8.1; 06-10-2026).
--
-- UNA migración para los siete requerimientos del segundo factor, y no una
-- por requerimiento: la retención de RF-SP-072 necesita roles.requires_mfa
-- desde el primer inicio de sesión, y esa columna es de RF-SP-077
-- (071 · plan.md §1).
--
-- Trae:
--   · user_mfa_factors, mfa_recovery_codes, mfa_challenges;
--   · roles.requires_mfa (SUPERADMIN y ADMIN obligados, la raíz siempre);
--   · permissions.requires_recent_mfa y las DIECIOCHO operaciones sensibles;
--   · refresh_tokens.mfa_verified_at;
--   · los siete permisos: cinco de alcance propio a TODO rol por su tipo
--     (RN-SEG-015, como V31) y dos de administración a SUPERADMIN y ADMIN;
--   · los siete eventos de seguridad nuevos (de 21 a 28).
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 del 06-10-2026
-- (01a10e829000) y la serie de permisos de SP, 5e7ad0, que sigue en 0x34.
--
-- GUARDAS: 196 en el catálogo / SUPERADMIN 196 / ADMIN 194 / dieciocho
-- sensibles / dos roles obligados / cero filas que rompan RN-SEG-003.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. El authenticator de cada persona (requirements/sp.md §10.22)
-- -----------------------------------------------------------------------------
CREATE TABLE user_mfa_factors (
    id                 uuid         PRIMARY KEY,
    user_id            uuid         NOT NULL,
    factor_type        varchar(20)  NOT NULL DEFAULT 'TOTP',
    secret_ciphertext  text         NOT NULL,
    status             varchar(20)  NOT NULL DEFAULT 'PENDIENTE',
    last_used_step     bigint       NULL,
    pending_expires_at timestamptz  NULL,
    confirmed_at       timestamptz  NULL,
    retired_at         timestamptz  NULL,
    retired_reason     varchar(30)  NULL,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_user_mfa_factors_type
        CHECK (factor_type IN ('TOTP')),
    CONSTRAINT ck_user_mfa_factors_status
        CHECK (status IN ('PENDIENTE', 'ACTIVO', 'RETIRADO')),
    CONSTRAINT ck_user_mfa_factors_retirado
        CHECK ((status = 'RETIRADO') = (retired_at IS NOT NULL AND retired_reason IS NOT NULL)),
    CONSTRAINT ck_user_mfa_factors_retired_reason
        CHECK (retired_reason IS NULL OR retired_reason IN
            ('REEMPLAZADO', 'DESACTIVADO', 'RESTABLECIDO', 'CADUCADO')),
    CONSTRAINT ck_user_mfa_factors_activo
        CHECK (status <> 'ACTIVO' OR confirmed_at IS NOT NULL),
    CONSTRAINT ck_user_mfa_factors_pendiente
        CHECK (status <> 'PENDIENTE' OR pending_expires_at IS NOT NULL),
    CONSTRAINT ck_user_mfa_factors_secreto
        CHECK (secret_ciphertext LIKE 'v1:%'),
    -- CASCADE y no RESTRICT, al revés que refresh_tokens: una persona no se
    -- borra físicamente nunca (Art. V.10), pero las suites sí borran sus
    -- personas de prueba, y una fila huérfana de un factor rompería suites
    -- lejanas (memoria del proyecto: una FK sin ON DELETE rompe suites lejos).
    CONSTRAINT fk_user_mfa_factors_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- RN-SP-058 en el motor: uno activo y uno pendiente por persona, como mucho.
CREATE UNIQUE INDEX uq_user_mfa_factors_activo
    ON user_mfa_factors (user_id) WHERE status = 'ACTIVO';
CREATE UNIQUE INDEX uq_user_mfa_factors_pendiente
    ON user_mfa_factors (user_id) WHERE status = 'PENDIENTE';

COMMENT ON TABLE user_mfa_factors IS
    'El authenticator de cada persona (RN-SP-058). Las filas retiradas no se borran: cuándo tuvo alguien segundo factor es una pregunta de seguridad.';
COMMENT ON COLUMN user_mfa_factors.secret_ciphertext IS
    'Secreto TOTP cifrado con AES-256-GCM (MFA_ENCRYPTION_KEY, user_id como dato asociado), formato v1:<base64>. Cifrado y no resumido: hay que recalcular el código. Nunca sale por la API.';
COMMENT ON COLUMN user_mfa_factors.last_used_step IS
    'Periodo TOTP del último código aceptado (RN-SP-060): un código sirve una sola vez.';

-- -----------------------------------------------------------------------------
-- 2. Los códigos de recuperación (§10.23)
-- -----------------------------------------------------------------------------
CREATE TABLE mfa_recovery_codes (
    id            uuid         PRIMARY KEY,
    factor_id     uuid         NOT NULL,
    code_hash     varchar(255) NOT NULL,
    used_at       timestamptz  NULL,
    superseded_at timestamptz  NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_mfa_recovery_codes_final
        CHECK (used_at IS NULL OR superseded_at IS NULL),
    CONSTRAINT fk_mfa_recovery_codes_factor
        FOREIGN KEY (factor_id) REFERENCES user_mfa_factors (id) ON DELETE CASCADE
);

CREATE INDEX ix_mfa_recovery_codes_vigentes
    ON mfa_recovery_codes (factor_id) WHERE used_at IS NULL AND superseded_at IS NULL;

COMMENT ON TABLE mfa_recovery_codes IS
    'Códigos de recuperación (RN-SP-061): diez por factor, Argon2id, de un solo uso. Cuelgan del factor: retirarlo los deja sin efecto.';

-- -----------------------------------------------------------------------------
-- 3. Los desafíos entre la contraseña y el código (§10.24)
-- -----------------------------------------------------------------------------
CREATE TABLE mfa_challenges (
    id              uuid         PRIMARY KEY,
    user_id         uuid         NOT NULL,
    challenge_hash  varchar(255) NOT NULL,
    expires_at      timestamptz  NOT NULL,
    failed_attempts smallint     NOT NULL DEFAULT 0,
    consumed_at     timestamptz  NULL,
    requested_ip    inet         NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_mfa_challenges_hash UNIQUE (challenge_hash),
    CONSTRAINT ck_mfa_challenges_periodo
        CHECK (expires_at > created_at),
    CONSTRAINT ck_mfa_challenges_attempts
        CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT fk_mfa_challenges_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

COMMENT ON TABLE mfa_challenges IS
    'Desafíos de un solo uso entre la contraseña y el código (RN-SP-059): cinco minutos, cinco intentos. Solo el resumen. Sin purga, como password_reset_permits (security.md §5.5.2).';

-- -----------------------------------------------------------------------------
-- 4. Columnas nuevas
-- -----------------------------------------------------------------------------
ALTER TABLE roles
    ADD COLUMN requires_mfa boolean NOT NULL DEFAULT false;

UPDATE roles SET requires_mfa = true
 WHERE id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',   -- SUPERADMIN
              '01a02a33-4c00-7002-9c4f-5e7ad1000002');  -- ADMIN

-- La raíz lo exige siempre (RN-SP-062): un CHECK y no solo el caso de uso.
ALTER TABLE roles
    ADD CONSTRAINT ck_roles_root_requires_mfa
        CHECK (parent_role_id IS NOT NULL OR requires_mfa);

COMMENT ON COLUMN roles.requires_mfa IS
    'Sus portadores están obligados a usar el authenticator (RN-SP-062). La escribe RF-SP-077.';

ALTER TABLE permissions
    ADD COLUMN requires_recent_mfa boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN permissions.requires_recent_mfa IS
    'Operación sensible (RN-SP-063): exige un segundo factor verificado hace cinco minutos o menos.';

ALTER TABLE refresh_tokens
    ADD COLUMN mfa_verified_at timestamptz NULL;

COMMENT ON COLUMN refresh_tokens.mfa_verified_at IS
    'Cuándo verificó el segundo factor esta familia. La rotación lo copia y no lo renueva (security.md §5.2).';

-- -----------------------------------------------------------------------------
-- 5. Los siete permisos (security.md §4.4)
-- -----------------------------------------------------------------------------
INSERT INTO permissions (id, code, resource, action, name, description, requires_recent_mfa) VALUES
('01a10e82-9000-7101-9c4f-5e7ad0000034', 'users:start-own-mfa', 'users', 'start-own-mfa',
 'Iniciar la activación del propio segundo factor',
 'Generar el secreto de la app autenticadora por POST /users/me/mfa/totp (RF-SP-071). Con un factor ya activo exige además verificación reciente.',
 false),
('01a10e82-9000-7102-9c4f-5e7ad0000035', 'users:confirm-own-mfa', 'users', 'confirm-own-mfa',
 'Confirmar el propio segundo factor',
 'Activar el factor pendiente con el primer código por POST /users/me/mfa/totp/confirmation (RF-SP-071), y recibir los códigos de recuperación.',
 false),
('01a10e82-9000-7103-9c4f-5e7ad0000036', 'users:verify-own-mfa', 'users', 'verify-own-mfa',
 'Reverificar el propio segundo factor',
 'Presentar el código otra vez antes de una operación sensible por POST /auth/mfa/verification (RF-SP-073).',
 false),
('01a10e82-9000-7104-9c4f-5e7ad0000037', 'users:regenerate-own-recovery-codes', 'users', 'regenerate-own-recovery-codes',
 'Regenerar los propios códigos de recuperación',
 'Obtener diez códigos nuevos y anular los anteriores por POST /users/me/mfa/recovery-codes (RF-SP-074).',
 true),
('01a10e82-9000-7105-9c4f-5e7ad0000038', 'users:disable-own-mfa', 'users', 'disable-own-mfa',
 'Desactivar el propio segundo factor',
 'Retirar el propio authenticator por POST /users/me/mfa/deactivation (RF-SP-075), si ningún rol propio lo exige.',
 true),
('01a10e82-9000-7106-9c4f-5e7ad0000039', 'users:reset-mfa', 'users', 'reset-mfa',
 'Restablecer el segundo factor de un usuario',
 'Retirar el authenticator de otra persona con motivo por POST /users/{id}/mfa/reset (RF-SP-076). Nunca sobre uno mismo ni sobre quien tiene más privilegios.',
 true),
('01a10e82-9000-7107-9c4f-5e7ad000003a', 'roles:require-mfa', 'roles', 'require-mfa',
 'Exigir el segundo factor a los portadores de un rol',
 'Marcar o desmarcar un rol por PATCH /roles/{id}/mfa-requirement (RF-SP-077).',
 true);

-- Los cinco de alcance propio, a TODO rol por su tipo (RN-SEG-015): proteger
-- la propia cuenta no se le niega a nadie, y un rol que exija el factor sin
-- conceder los dos primeros retendría a sus personas sin salida.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
   AND p.code IN ('users:start-own-mfa', 'users:confirm-own-mfa', 'users:verify-own-mfa',
                  'users:regenerate-own-recovery-codes', 'users:disable-own-mfa')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- Los dos de administración, a SUPERADMIN y ADMIN explícitos.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE r.id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a02a33-4c00-7002-9c4f-5e7ad1000002')
   AND p.code IN ('users:reset-mfa', 'roles:require-mfa')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- -----------------------------------------------------------------------------
-- 6. Las operaciones sensibles existentes (security.md §4.4)
-- -----------------------------------------------------------------------------
UPDATE permissions SET requires_recent_mfa = true
 WHERE code IN ('roles:assign-permissions', 'roles:revoke-permissions',
                'users:assign-roles', 'users:revoke-roles',
                'users:reset-password', 'users:delete',
                'movements:confirm-payment', 'movements:reject-payment',
                'movements:approve-withdrawal', 'movements:adjust-points',
                'movements:set-conversion-rate', 'movements:set-points-rate',
                'commission-batches:pay', 'commission-batches:pay-batches');

-- -----------------------------------------------------------------------------
-- 7. Los siete eventos de seguridad (security.md §8.1): de 21 a 28
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
        'ROLE_MFA_REQUIREMENT_CHANGED'));

-- -----------------------------------------------------------------------------
-- 8. Guardas
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    filas       integer;
    de_raiz     integer;
    de_admin    integer;
    sensibles   integer;
    obligados   integer;
    sin_padre   integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 196 THEN
        RAISE EXCEPTION 'V75: el catálogo debe tener 196 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 196 OR de_admin <> 194 THEN
        RAISE EXCEPTION 'V75: SUPERADMIN debe portar 196 permisos y ADMIN 194; tienen % y %', de_raiz, de_admin;
    END IF;

    SELECT count(*) INTO sensibles FROM permissions WHERE requires_recent_mfa;
    IF sensibles <> 18 THEN
        RAISE EXCEPTION 'V75: deben quedar 18 operaciones sensibles; hay %', sensibles;
    END IF;

    SELECT count(*) INTO obligados FROM roles WHERE requires_mfa;
    IF obligados <> 2 THEN
        RAISE EXCEPTION 'V75: deben quedar obligados SUPERADMIN y ADMIN; hay % roles obligados', obligados;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions pp
                        WHERE pp.role_id = r.parent_role_id
                          AND pp.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V75: % filas rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
