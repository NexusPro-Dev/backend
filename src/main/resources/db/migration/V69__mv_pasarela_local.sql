-- =============================================================================
-- V69 — La pasarela local, PayRetailers: el cobro (RF-MV-048 a RF-MV-051;
-- requirements/mv.md v0.79.0 §4.10, §7.6 y §7.7; 05-10-2026).
--
--   1. payments gana el cobro en moneda local: la moneda, el importe convertido
--      (centésimas, ADR-006), la conversión usada y la página de pago. Las
--      cuatro juntas o ninguna (RN-MV-063).
--   2. La incidencia COBRO_TARDIO: un cobro aprobado sobre un pago que ya no
--      lo esperaba (RN-MV-064). Es la PRIMERA incidencia sobre un pago
--      RECHAZADO: las de Stripe son todas de un pago CONFIRMADO.
--   3. payment_methods.gateway admite PAYRETAILERS, y PSE («Múltiples
--      métodos de pago») pasa a cobrarlo la pasarela local. Los PSE pendientes
--      que ya existen no se tocan: no tienen cobro abierto y los confirma una
--      persona, como hasta hoy.
--   4. El índice del barrido (RF-MV-050).
--   5. El permiso movements:pay-pending-locally (RF-MV-051), por tipo de rol,
--      como movements:pay-pending-by-card.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 de V58 —`01a0ef9c6800`—,
-- continuando la serie de permisos de MV: 000060 → 000061.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. El cobro en moneda local.
-- ---------------------------------------------------------------------------

ALTER TABLE payments
    ADD COLUMN charge_currency_id uuid         NULL,
    ADD COLUMN charge_amount      bigint       NULL,
    ADD COLUMN conversion_rate_id uuid         NULL,
    ADD COLUMN checkout_url       varchar(500) NULL,
    ADD CONSTRAINT fk_payments_charge_currency
        FOREIGN KEY (charge_currency_id) REFERENCES currencies (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_payments_conversion_rate
        FOREIGN KEY (conversion_rate_id) REFERENCES country_conversion_rates (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_payments_cobro_local CHECK (
        (charge_currency_id IS NULL AND charge_amount IS NULL
            AND conversion_rate_id IS NULL AND checkout_url IS NULL)
        OR (charge_currency_id IS NOT NULL AND charge_amount IS NOT NULL
            AND conversion_rate_id IS NOT NULL AND checkout_url IS NOT NULL
            AND charge_amount > 0));

COMMENT ON COLUMN payments.charge_amount IS
    'RN-MV-063: el importe cobrado en moneda local, en centésimas, ya redondeado hacia arriba a unidad entera.';
COMMENT ON COLUMN payments.checkout_url IS
    'RF-MV-051: la página de pago de la pasarela local, para volver a ella sin preguntar.';

-- ---------------------------------------------------------------------------
-- 2. La incidencia COBRO_TARDIO, la única que admite un pago RECHAZADO.
-- ---------------------------------------------------------------------------

ALTER TABLE payments
    DROP CONSTRAINT ck_payments_incident,
    ADD CONSTRAINT ck_payments_incident CHECK (
        (incident IS NULL
            OR incident IN ('REEMBOLSADO', 'EN_DISPUTA', 'DISPUTA_GANADA', 'DISPUTA_PERDIDA',
                            'COBRO_TARDIO'))
        AND ((incident IS NULL) = (incident_at IS NULL))
        AND (refunded_amount IS NULL OR (incident = 'REEMBOLSADO' AND refunded_amount > 0))
        AND (incident IS NULL
            OR (incident = 'COBRO_TARDIO' AND status = 'RECHAZADO')
            OR (incident <> 'COBRO_TARDIO' AND status = 'CONFIRMADO')));

-- ---------------------------------------------------------------------------
-- 3. La pasarela local cobra PSE.
-- ---------------------------------------------------------------------------

ALTER TABLE payment_methods
    DROP CONSTRAINT ck_payment_methods_gateway,
    ADD CONSTRAINT ck_payment_methods_gateway
        CHECK (gateway IS NULL OR gateway IN ('STRIPE', 'PAYRETAILERS'));

UPDATE payment_methods SET gateway = 'PAYRETAILERS' WHERE code = 'PSE';

-- ---------------------------------------------------------------------------
-- 4. El barrido pregunta por los cobros locales pendientes (RF-MV-050).
-- ---------------------------------------------------------------------------

CREATE INDEX ix_payments_cobro_local_pendiente ON payments (occurred_at)
    WHERE status = 'PENDIENTE' AND charge_currency_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 5. El permiso de pagar un pendiente propio por la pasarela local.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0ef9c-6800-7022-9c4f-5e7ad7000061', 'movements:pay-pending-locally', 'movements',
 'pay-pending-locally',
 'Pagar por la pasarela local un pendiente propio',
 'Abrir o retomar el cobro por la pasarela local de una compra propia pendiente con PSE, por POST /movements/mine/{id}/local-charge (RF-MV-051).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE p.id = '01a0ef9c-6800-7022-9c4f-5e7ad7000061'
   AND r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- ---------------------------------------------------------------------------
-- Guardas.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    sin_padre integer;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM payment_methods
                    WHERE code = 'PSE' AND gateway = 'PAYRETAILERS') THEN
        RAISE EXCEPTION 'V69: PSE no quedó cobrado por la pasarela local';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'movements:pay-pending-locally') THEN
        RAISE EXCEPTION 'V69: falta movements:pay-pending-locally';
    END IF;

    -- Contención (RN-SEG-003).
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id
                          AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V69: % asociaciones rompen la contencion de RN-SEG-003', sin_padre;
    END IF;
END $$;
