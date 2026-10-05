-- =============================================================================
-- V70 — La conversión de Colombia (RN-MV-062; requirements/mv.md §4.9;
-- 05-10-2026).
--
-- Por decisión del responsable del proyecto, la primera conversión nace por
-- migración y EN TODOS LOS ENTORNOS, para que la pasarela local (PayRetailers)
-- pueda cobrar en Colombia desde el primer arranque:
--
--     1 USD = 3.400 COP al cobrar · 1 USD = 3.200 COP al pagar un retiro
--
-- Moneda local COP (sembrada en V9), moneda base USD —la por omisión—, y a
-- nombre del superadministrador, que es la única persona que existe al migrar.
-- Desde aquí, una conversión nueva la fija un ADMIN por la API
-- (POST /movements/conversion-rates): inserta otra fila y esta queda en la
-- historia, porque explicará los cobros y retiros que se hagan con ella.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 de V58 —`01a0ef9c6800`—.
-- =============================================================================

INSERT INTO country_conversion_rates (id, country_id, currency_id, base_currency_id,
                                      pay_in_price, payout_price, valid_from, created_by,
                                      created_at)
VALUES ('01a0ef9c-6800-7023-9c4f-5e7ad7000101',
        '01a07bbd-5200-7001-9c4f-5e7ad3000101',   -- COL
        '01a03336-6d00-7002-9c4f-5e7ad3000002',   -- COP
        '01a03336-6d00-7001-9c4f-5e7ad3000001',   -- USD
        3400.0000, 3200.0000,
        TIMESTAMPTZ '2026-10-05 00:00:00+00',
        '01a033a4-4a00-7001-9c4f-5e7ad4000001',   -- el superadministrador
        now());

INSERT INTO audit_change_log (
    id, occurred_at, actor_id, correlation_id, ip_address, user_agent,
    module, entity, entity_id, action, changes
)
SELECT
    '01a0ef9c-6800-7024-9c4f-5e7ad7000102'::uuid,
    now(), NULL, NULL, NULL, NULL,
    'MV', 'country_conversion_rates', r.id, 'CREATE',
    jsonb_build_object(
        'before', jsonb_build_object('currency_id', NULL, 'pay_in_price', NULL,
                                     'payout_price', NULL),
        'after',  jsonb_build_object(
            'country_id',       r.country_id,
            'currency_id',      r.currency_id,
            'base_currency_id', r.base_currency_id,
            'pay_in_price',     r.pay_in_price,
            'payout_price',     r.payout_price,
            'valid_from',       r.valid_from))
  FROM country_conversion_rates r
 WHERE r.id = '01a0ef9c-6800-7023-9c4f-5e7ad7000101'::uuid;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM country_conversion_rates
                    WHERE country_id = '01a07bbd-5200-7001-9c4f-5e7ad3000101'
                      AND pay_in_price = 3400 AND payout_price = 3200) THEN
        RAISE EXCEPTION 'V70: falta la conversión de Colombia';
    END IF;
END $$;
