-- =============================================================================
-- V62 — Etapa 4 de MV para la tarjeta: la pasarela Stripe (RF-MV-040 a
-- RF-MV-042; requirements/mv.md v0.64.0 §4.6, §7.4, §7.6, §7.7 y §7.14;
-- 01-10-2026).
--
-- La carga RF-MV-040 por toda la etapa, como V61 las cuentas de cobro. Trae:
--
--   1. `payment_methods.gateway`: qué pasarela cobra cada método. `STRIPE` en
--      `CREDIT_CARD`; nulo en los demás, que confirma una persona o el sistema.
--   2. La incidencia de `payments` —reembolso y disputa— (RN-MV-060), que NO
--      cambia el estado del pago.
--   3. `gateway_events`: cada notificación de la pasarela, guardada entera antes
--      de interpretarse y única por (gateway, external_id) (RN-MV-059).
--   4. El permiso `movements:pay-pending-by-card` (RF-MV-042), por tipo de rol.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 de V61 —`01a0ef9c6800`—,
-- continuando la serie de permisos de MV: 000055 → 000056.
-- =============================================================================

ALTER TABLE payment_methods
    ADD COLUMN gateway varchar(20) NULL,
    ADD CONSTRAINT ck_payment_methods_gateway CHECK (gateway IS NULL OR gateway IN ('STRIPE'));

UPDATE payment_methods SET gateway = 'STRIPE' WHERE code = 'CREDIT_CARD';

COMMENT ON COLUMN payment_methods.gateway IS
    'Qué pasarela cobra este método (§4.6). Nulo: lo confirma una persona o el propio sistema.';


ALTER TABLE payments
    ADD COLUMN incident        varchar(20)   NULL,
    ADD COLUMN incident_at     timestamptz   NULL,
    ADD COLUMN refunded_amount numeric(14,2) NULL,
    ADD CONSTRAINT ck_payments_incident
        CHECK ((incident IS NULL OR incident IN ('REEMBOLSADO', 'EN_DISPUTA',
                                                 'DISPUTA_GANADA', 'DISPUTA_PERDIDA'))
               AND (incident IS NULL) = (incident_at IS NULL)
               AND (refunded_amount IS NULL OR (incident = 'REEMBOLSADO' AND refunded_amount > 0))
               AND (incident IS NULL OR status = 'CONFIRMADO'));

CREATE INDEX ix_payments_provider_reference
    ON payments (provider_reference) WHERE provider_reference IS NOT NULL;

COMMENT ON COLUMN payments.incident IS
    'RN-MV-060: lo que pasó DESPUÉS de confirmar (reembolso o disputa). No cambia el estado del pago ni revierte nada.';


CREATE TABLE gateway_events (
    id            uuid          PRIMARY KEY,
    gateway       varchar(20)   NOT NULL,
    external_id   varchar(100)  NOT NULL,
    type          varchar(100)  NOT NULL,
    payload       jsonb         NOT NULL,
    payment_id    uuid          NULL,
    received_at   timestamptz   NOT NULL DEFAULT now(),
    processed_at  timestamptz   NULL,
    outcome       varchar(20)   NULL,
    error         varchar(500)  NULL,
    attempts      smallint      NOT NULL DEFAULT 0,

    CONSTRAINT uq_gateway_events_externo UNIQUE (gateway, external_id),
    CONSTRAINT ck_gateway_events_outcome
        CHECK ((outcome IS NULL OR outcome IN ('PROCESADO', 'IGNORADO', 'ERROR'))
               AND (outcome IS NULL) = (processed_at IS NULL)),
    CONSTRAINT ck_gateway_events_attempts CHECK (attempts >= 0),
    -- SET NULL: la notificación es la constancia de lo que dijo la pasarela y
    -- sobrevive a la limpieza del pago en las suites.
    CONSTRAINT fk_gateway_events_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE SET NULL
);

CREATE INDEX ix_gateway_events_pendientes ON gateway_events (received_at) WHERE processed_at IS NULL;

COMMENT ON TABLE gateway_events IS
    'RN-MV-059: lo que notifica la pasarela, tal cual. Una notificación reentregada no se procesa dos veces.';


INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0ef9c-6800-701d-9c4f-5e7ad7000056', 'movements:pay-pending-by-card', 'movements',
 'pay-pending-by-card',
 'Pagar con tarjeta un pago pendiente propio',
 'Retomar el cobro con tarjeta de una compra propia pendiente, o empezarlo si la registró otro, por POST /movements/mine/{id}/card-charge (RF-MV-042).');

-- Lo propio: por tipo de rol (RN-SEG-015).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a0ef9c-6800-701d-9c4f-5e7ad7000056'
  FROM roles r
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;


DO $$
DECLARE
    sin_padre integer;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM payment_methods WHERE code = 'CREDIT_CARD' AND gateway = 'STRIPE') THEN
        RAISE EXCEPTION 'V62: el método CREDIT_CARD no quedó con la pasarela STRIPE';
    END IF;

    IF (SELECT count(*) FROM role_permissions rp
          JOIN permissions p ON p.id = rp.permission_id
         WHERE p.code = 'movements:pay-pending-by-card'
           AND rp.role_id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                              '01a02a33-4c00-7002-9c4f-5e7ad1000002')) <> 2 THEN
        RAISE EXCEPTION 'V62: SUPERADMIN o ADMIN no portan movements:pay-pending-by-card';
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
        RAISE EXCEPTION 'V62: % asociaciones rompen la contencion de RN-SEG-003', sin_padre;
    END IF;
END $$;
