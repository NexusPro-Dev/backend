-- =============================================================================
-- V67 — La conversión por país (RF-MV-046 y RF-MV-047; requirements/mv.md
-- v0.75.0 §4.9, §7.6 y §7.15; 05-10-2026).
--
-- Primer paso de la integración con PayRetailers, que cobra y paga retiros en
-- la moneda de cada país: a cuánto se convierte una unidad de la moneda base
-- —la moneda por omisión al fijar, hoy USD— a la moneda local, con un precio
-- para COBRAR y otro para PAGAR RETIROS en la misma fila (RN-MV-062). Un
-- histórico, como points_rates: fijar inserta una fila y la anterior no se toca.
--
-- Los precios son proporciones y NO pasan a centésimas (ADR-006), como
-- points_rates.points_per_unit y exchange_rates.price.
--
-- NO SE SIEMBRA NINGUNA CONVERSIÓN: la primera la fija administración.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 de V58 —`01a0ef9c6800`—,
-- continuando la serie de permisos de MV: 000058 → 000059 y 000060.
-- =============================================================================

CREATE TABLE country_conversion_rates (
    id               uuid          PRIMARY KEY,
    country_id       uuid          NOT NULL,
    currency_id      uuid          NOT NULL,
    base_currency_id uuid          NOT NULL,
    pay_in_price     numeric(14,4) NOT NULL,
    payout_price     numeric(14,4) NOT NULL,
    valid_from       timestamptz   NOT NULL,
    created_by       uuid          NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_country_conversion_rates_precios
        CHECK (pay_in_price > 0 AND payout_price > 0),
    CONSTRAINT ck_country_conversion_rates_monedas CHECK (currency_id <> base_currency_id),
    CONSTRAINT uq_country_conversion_rates_vigencia UNIQUE (country_id, valid_from),
    CONSTRAINT fk_country_conversion_rates_country
        FOREIGN KEY (country_id) REFERENCES countries (id) ON DELETE RESTRICT,
    CONSTRAINT fk_country_conversion_rates_currency
        FOREIGN KEY (currency_id) REFERENCES currencies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_country_conversion_rates_base_currency
        FOREIGN KEY (base_currency_id) REFERENCES currencies (id) ON DELETE RESTRICT,
    -- CASCADE y no RESTRICT, como points_rates: una FK sin ON DELETE rompe las
    -- suites que limpian personas. En producción nadie borra personas.
    CONSTRAINT fk_country_conversion_rates_created_by
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE CASCADE
);

-- La vigente es un LIMIT 1 sobre este índice (RF-MV-046 · plan.md §2).
CREATE INDEX ix_country_conversion_rates_vigente
    ON country_conversion_rates (country_id, valid_from DESC);

COMMENT ON TABLE country_conversion_rates IS
    'RN-MV-062: a cuánto se convierte una unidad de la moneda base a la moneda local de cada país, al cobrar y al pagar retiros. Un histórico: rige la de valid_from más reciente que no sea futura.';
COMMENT ON COLUMN country_conversion_rates.pay_in_price IS
    'Al cobrar: 4150.0000 es «1 USD = 4.150 COP». Una proporción, no un importe: no va en centésimas.';
COMMENT ON COLUMN country_conversion_rates.payout_price IS
    'Al pagar un retiro, en la misma unidad que pay_in_price.';
COMMENT ON COLUMN country_conversion_rates.base_currency_id IS
    'La moneda por omisión en el momento de fijar, copiada: si cambia, la fila sigue diciendo de qué convertía.';

-- ---------------------------------------------------------------------------
-- Los dos permisos: fijar a SUPERADMIN y ADMIN explícito; consultar por tipo
-- de rol (RN-SEG-015), como V58.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0ef9c-6800-7020-9c4f-5e7ad7000059', 'movements:set-conversion-rate', 'movements', 'set-conversion-rate',
 'Fijar la conversión de un país',
 'Fijar el precio de cobro y el de retiro de un país, por POST /movements/conversion-rates (RF-MV-046). Rige desde que se fija; las anteriores se conservan.'),
('01a0ef9c-6800-7021-9c4f-5e7ad7000060', 'movements:read-conversion-rates', 'movements', 'read-conversion-rates',
 'Consultar las conversiones por país',
 'Ver la conversión vigente de cada país, por GET /movements/conversion-rates (RF-MV-047).');

INSERT INTO role_permissions (role_id, permission_id) VALUES
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a0ef9c-6800-7020-9c4f-5e7ad7000059'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a0ef9c-6800-7020-9c4f-5e7ad7000059')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE p.id = '01a0ef9c-6800-7021-9c4f-5e7ad7000060'
   AND r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- ---------------------------------------------------------------------------
-- Guardas.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    faltan    integer;
    sin_padre integer;
BEGIN
    SELECT count(*) INTO faltan
      FROM unnest(ARRAY['movements:set-conversion-rate',
                        'movements:read-conversion-rates']) AS esperado(code)
     WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = esperado.code);
    IF faltan <> 0 THEN
        RAISE EXCEPTION 'V67: faltan % de los dos permisos de la conversión por país', faltan;
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
        RAISE EXCEPTION 'V67: % asociaciones rompen la contencion de RN-SEG-003', sin_padre;
    END IF;
END $$;
