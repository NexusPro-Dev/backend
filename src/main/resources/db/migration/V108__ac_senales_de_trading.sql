-- =============================================================================
-- V108 — Las señales de trading (RF-AC-067 · T-01;
-- requirements/ac.md v0.30.0 §5.2.21, §8.17 y §8.18, RN-AC-035; 14-10-2026).
--
--   * Nace signals: par de divisas, hora de la señal (hora de Bogotá), tipo
--     numérico y suscripción FREE, PREMIUM o PERSONALIZED. visible_until se
--     calcula al crearla —la próxima vez que llega esa hora en Bogotá— y no
--     cambia: la señal no se corrige. Baja lógica con deleted_at.
--   * Nace signal_memberships: las membresías de una PERSONALIZED, una o más;
--     las otras dos no llevan ninguna.
--   * Seis permisos: listar, registrar, registrar varias, importar y retirar
--     a SUPERADMIN y ADMIN explícitos; ver las vigentes, donde está
--     live-sessions:learn —el alumno—.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V94, secuencias 7020 a
-- 7025, y la serie de AC donde V107 la dejó (000050 → 000051 a 000056).
--
-- GUARDAS: 250 en el catálogo; las cinco de administración, solo a los dos.
-- =============================================================================

CREATE TABLE signals (
    id                 uuid         PRIMARY KEY,
    currency_pair      varchar(20)  NOT NULL,
    signal_time        time         NOT NULL,
    signal_type        integer      NOT NULL,
    subscription_type  varchar(20)  NOT NULL,
    visible_until      timestamptz  NOT NULL,
    created_by         uuid         NULL,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    deleted_at         timestamptz  NULL,
    CONSTRAINT ck_signals_signal_type CHECK (signal_type >= 0),
    CONSTRAINT ck_signals_subscription_type
        CHECK (subscription_type IN ('FREE', 'PREMIUM', 'PERSONALIZED')),
    CONSTRAINT ck_signals_visible_until CHECK (visible_until > created_at)
);

CREATE INDEX ix_signals_visible ON signals (visible_until) WHERE deleted_at IS NULL;
CREATE INDEX ix_signals_created ON signals (created_at DESC, id DESC);

COMMENT ON TABLE signals IS
    'Las senales de trading (AC, RN-AC-035): se muestran hasta visible_until, la proxima vez que llega signal_time en Bogota despues de crearse. No se corrigen; se retiran con deleted_at.';

CREATE TABLE signal_memberships (
    signal_id      uuid NOT NULL,
    membership_id  uuid NOT NULL,
    CONSTRAINT pk_signal_memberships PRIMARY KEY (signal_id, membership_id),
    CONSTRAINT fk_signal_memberships_signal
        FOREIGN KEY (signal_id) REFERENCES signals (id) ON DELETE CASCADE,
    CONSTRAINT fk_signal_memberships_membership
        FOREIGN KEY (membership_id) REFERENCES memberships (id)
);

CREATE INDEX ix_signal_memberships_membership ON signal_memberships (membership_id);

COMMENT ON TABLE signal_memberships IS
    'Las membresias que ven una senal PERSONALIZED (RN-AC-035); las FREE y PREMIUM no tienen filas.';

-- -----------------------------------------------------------------------------
-- Permisos.
-- -----------------------------------------------------------------------------
INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a11a5c-4000-7020-9c4f-5e7adc000051', 'signals:list', 'signals', 'list',
 'Consultar las senales',
 'Todas las senales, paginadas y con filtros, por GET /signals (RF-AC-067).'),
('01a11a5c-4000-7021-9c4f-5e7adc000052', 'signals:create', 'signals', 'create',
 'Registrar una senal',
 'Registrar una senal por POST /signals (RF-AC-068).'),
('01a11a5c-4000-7022-9c4f-5e7adc000053', 'signals:create-batch', 'signals', 'create-batch',
 'Registrar varias senales',
 'Registrar varias senales a la vez, todas o ninguna, por POST /signals/batch (RF-AC-069).'),
('01a11a5c-4000-7023-9c4f-5e7adc000054', 'signals:import', 'signals', 'import',
 'Importar senales desde CSV',
 'Registrar las senales de un archivo CSV, todas o ninguna, por POST /signals/import (RF-AC-070).'),
('01a11a5c-4000-7024-9c4f-5e7adc000055', 'signals:delete', 'signals', 'delete',
 'Retirar una senal',
 'Retirar una senal, con motivo, por POST /signals/{id}/deletion (RF-AC-071).'),
('01a11a5c-4000-7025-9c4f-5e7adc000056', 'signals:view', 'signals', 'view',
 'Ver mis senales vigentes',
 'Las senales vigentes que abre la membresia de quien mira, por GET /signals/available (RF-AC-072).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
 CROSS JOIN permissions p
 WHERE p.code IN ('signals:list', 'signals:create', 'signals:create-batch', 'signals:import',
                  'signals:delete')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
  FROM role_permissions rp
  JOIN permissions t ON t.id = rp.permission_id AND t.code = 'live-sessions:learn'
 CROSS JOIN permissions p
 WHERE p.code = 'signals:view'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- -----------------------------------------------------------------------------
-- Guardas.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 250 THEN
        RAISE EXCEPTION 'V108: el catálogo debe tener 250 permisos; tiene %', filas;
    END IF;
    IF (SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
         WHERE p.code IN ('signals:list', 'signals:create', 'signals:create-batch',
                          'signals:import', 'signals:delete')) <> 10 THEN
        RAISE EXCEPTION 'V108: las cinco de administración van a SUPERADMIN y ADMIN, y a nadie más';
    END IF;
END $$;
