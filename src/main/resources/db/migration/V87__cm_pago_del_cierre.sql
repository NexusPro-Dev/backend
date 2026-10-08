-- =============================================================================
-- V87 — El cierre paga solo, salvo que se elija lo contrario (RF-CM-028 · T-01,
-- RF-CM-029, RF-CM-009 §16; requirements/cm.md v0.43.0 §5.12, §7.8 y §7.14,
-- RN-CM-053 y RN-CM-054; security.md v0.124.0 §4.4; 08-10-2026).
--
--   1. `commission_payment_choices`: cómo se paga cada cierre programado, una
--      fila por turno ELEGIDO. Sin fila, AUTOMATICO. No referencia
--      commission_closings, que se escribe al empezar el turno, después de
--      elegir: los une `scheduled_for`.
--
--   2. Tres columnas en `commission_closings`: el modo que el cierre leyó al
--      abrir su turno —presente si y solo si PROGRAMADO: el cierre a mano no
--      paga— y cuántos lotes pagó y cuántos no. Los programados que ya
--      existen quedan MANUAL con cero: así se pagaron.
--
--   3. Dos permisos de ADMINISTRACIÓN, a SUPERADMIN y ADMIN explícitos, como
--      V86. IDENTIFICADORES LITERALES (Art. V.11): la marca v7 de V79
--      (01a10e829000), secuencias 7011 y 7012 —las siguientes a la 7010 de
--      V86— y la serie de permisos de CM donde V86 la dejó (000033 → 000035).
--
-- GUARDA: 209 en el catálogo.
--
-- SIN AUDITORÍA, como V86.
-- =============================================================================

CREATE TABLE commission_payment_choices (
    id            uuid        PRIMARY KEY,
    scheduled_for timestamptz NOT NULL,
    payment_mode  varchar(20) NOT NULL,
    chosen_by     uuid        NOT NULL,
    chosen_at     timestamptz NOT NULL,
    created_at    timestamptz NOT NULL,

    CONSTRAINT uq_commission_payment_choices_turno UNIQUE (scheduled_for),
    CONSTRAINT ck_commission_payment_choices_mode
        CHECK (payment_mode IN ('AUTOMATICO', 'MANUAL')),
    CONSTRAINT fk_commission_payment_choices_chosen_by
        FOREIGN KEY (chosen_by) REFERENCES users (id) ON DELETE RESTRICT
);

COMMENT ON TABLE commission_payment_choices IS
    'RN-CM-054: como se paga cada cierre programado, una fila por turno elegido. Sin fila, AUTOMATICO (RN-CM-053). Elegir otra vez reescribe la fila; la auditoria guarda cada eleccion.';

ALTER TABLE commission_closings
    ADD COLUMN payment_mode     varchar(20) NULL,
    ADD COLUMN batches_paid     integer     NOT NULL DEFAULT 0,
    ADD COLUMN batches_not_paid integer     NOT NULL DEFAULT 0;

UPDATE commission_closings SET payment_mode = 'MANUAL' WHERE origin = 'PROGRAMADO';

ALTER TABLE commission_closings
    ADD CONSTRAINT ck_commission_closings_payment_mode CHECK (
        (payment_mode IS NULL OR payment_mode IN ('AUTOMATICO', 'MANUAL'))
        AND ((origin = 'PROGRAMADO') = (payment_mode IS NOT NULL))),
    ADD CONSTRAINT ck_commission_closings_payment_counts CHECK (
        batches_paid >= 0 AND batches_not_paid >= 0
        AND (payment_mode = 'AUTOMATICO' OR (batches_paid = 0 AND batches_not_paid = 0)));

COMMENT ON COLUMN commission_closings.payment_mode IS
    'RN-CM-053: como se pago lo que cerro, leido de commission_payment_choices al abrir el turno. Solo en los PROGRAMADO: el cierre a mano no paga.';

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7011-9c4f-5e7ad6000034', 'commission-closings:read-next',
 'commission-closings', 'read-next',
 'Consultar el próximo cierre de comisiones',
 'Consultar cuándo es el próximo cierre programado, si la ventana para elegir cómo se paga está abierta y qué hay elegido, por GET /commission-closings/next (RF-CM-028).'),
('01a10e82-9000-7012-9c4f-5e7ad6000035', 'commission-closings:set-payment-mode',
 'commission-closings', 'set-payment-mode',
 'Elegir cómo se paga el próximo cierre',
 'Elegir si el pago del próximo cierre programado es automático o manual, en las 48 horas anteriores, por PUT /commission-closings/next/payment-mode (RF-CM-029, RN-CM-054).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
  CROSS JOIN permissions p
 WHERE p.id IN ('01a10e82-9000-7011-9c4f-5e7ad6000034', '01a10e82-9000-7012-9c4f-5e7ad6000035')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 209 THEN
        RAISE EXCEPTION 'V87: el catálogo debe tener 209 permisos; tiene %', filas;
    END IF;
END $$;
