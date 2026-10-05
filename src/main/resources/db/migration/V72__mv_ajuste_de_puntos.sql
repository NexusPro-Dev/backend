-- =============================================================================
-- V72 — El ajuste de puntos a mano (RF-MV-052, RN-MV-076; requirements/mv.md
-- v0.83.0 §4.11, §7.1 y §7.6; 05-10-2026).
--
--   1. movements.external_reference: la referencia del comprobante que soporta
--      un ajuste, opcional y con contenido.
--   2. ck_movements_points se RELAJA: el ajuste lleva puntos con signo y sin
--      tasa. Lo invariante es que una tasa exige puntos positivos y que unos
--      puntos nunca son cero. El IS NOT NULL es necesario: un CHECK que evalúa
--      a nulo pasa.
--   3. ck_movement_entries_event admite AJUSTE.
--   4. El tipo AJUSTE_PUNTOS (prefijo AJP), con su estado del tipo REGISTRADO.
--   5. movements:adjust-points, a SUPERADMIN y ADMIN explícito, como
--      movements:grant-bonus.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 de V58 —`01a0ef9c6800`—,
-- continuando las series: tipos 000015 → 000016, estados del tipo 000036 →
-- 000037, permisos de MV 000061 → 000062.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. La referencia.
-- ---------------------------------------------------------------------------

ALTER TABLE movements
    ADD COLUMN external_reference varchar(120) NULL,
    ADD CONSTRAINT ck_movements_external_reference
        CHECK (external_reference IS NULL OR btrim(external_reference) <> '');

COMMENT ON COLUMN movements.external_reference IS
    'RN-MV-076: la referencia del comprobante que soporta un ajuste de puntos —el número de la consignación—. Nula en todo lo demás.';

-- ---------------------------------------------------------------------------
-- 2. Los puntos, con signo y sin tasa en el ajuste.
-- ---------------------------------------------------------------------------

ALTER TABLE movements DROP CONSTRAINT ck_movements_points;
ALTER TABLE movements ADD CONSTRAINT ck_movements_points
    CHECK ((points_rate_id IS NULL OR (points_amount IS NOT NULL AND points_amount > 0))
       AND (points_amount IS NULL OR points_amount <> 0));

COMMENT ON COLUMN movements.points_amount IS
    'RN-MV-051: los puntos que da una COMPRA_PUNTOS, a su tasa, redondeados hacia abajo; confirmar abona exactamente estos. RN-MV-076: en un AJUSTE_PUNTOS, los puntos sumados (positivos) o restados (negativos), sin tasa.';

-- ---------------------------------------------------------------------------
-- 3. El evento.
-- ---------------------------------------------------------------------------

ALTER TABLE movement_entries DROP CONSTRAINT ck_movement_entries_event;
ALTER TABLE movement_entries ADD CONSTRAINT ck_movement_entries_event
    CHECK (event IN ('SOLICITUD', 'APROBACION', 'RECHAZO', 'ABONO', 'PAGO', 'AJUSTE'));

-- ---------------------------------------------------------------------------
-- 4. El tipo, con su estado del tipo.
-- ---------------------------------------------------------------------------

INSERT INTO movement_types (id, code, name, prefix) VALUES
('01a0ef9c-6800-7025-9c4f-5e7ad7000016', 'AJUSTE_PUNTOS', 'Ajuste de puntos', 'AJP');

INSERT INTO movement_type_statuses (id, movement_type_id, code, name) VALUES
('01a0ef9c-6800-7026-9c4f-5e7ad7000037', '01a0ef9c-6800-7025-9c4f-5e7ad7000016', 'REGISTRADO', 'Registrado');

-- ---------------------------------------------------------------------------
-- 5. El permiso.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0ef9c-6800-7027-9c4f-5e7ad7000062', 'movements:adjust-points', 'movements', 'adjust-points',
 'Ajustar los puntos de una persona',
 'Sumar o restar puntos a mano a cualquier persona, con motivo y referencia opcional, por POST /movements/points-adjustments (RF-MV-052). Nunca deja el saldo en negativo.');

INSERT INTO role_permissions (role_id, permission_id) VALUES
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a0ef9c-6800-7027-9c4f-5e7ad7000062'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a0ef9c-6800-7027-9c4f-5e7ad7000062')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;
