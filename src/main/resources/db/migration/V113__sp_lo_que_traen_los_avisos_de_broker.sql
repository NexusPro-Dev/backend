-- ---------------------------------------------------------------------------
-- Lo que traen los avisos de los brokers, en la cuenta (RN-SP-077, RF-SP-054,
-- tripleta 078 v0.7.0, 15-10-2026).
--
-- Se retira el nombre de usuario en el broker (RN-SP-040): ningún aviso lo
-- trajo. Su índice de trigramas (V100) cae con la columna.
--
-- En su lugar, lo que los avisos sí traen: cuándo llegó el registro, el país
-- y los depósitos, el primero y el total. Importes en centésimas (ADR-006).
-- Sin relleno: reinterpretar lo ya aplicado es otra decisión.
-- ---------------------------------------------------------------------------

ALTER TABLE user_brokers DROP COLUMN broker_username;

ALTER TABLE user_brokers
    ADD COLUMN registered_at        timestamptz NULL,
    ADD COLUMN country_code         varchar(2)  NULL,
    ADD COLUMN first_deposit_amount bigint      NULL,
    ADD COLUMN deposits_total       bigint      NOT NULL DEFAULT 0,
    ADD COLUMN deposit_currency     varchar(3)  NULL,
    ADD CONSTRAINT ck_user_brokers_pais
        CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
    ADD CONSTRAINT ck_user_brokers_moneda
        CHECK (deposit_currency IS NULL OR deposit_currency ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_user_brokers_primer_deposito
        CHECK (first_deposit_amount IS NULL
               OR (first_deposit_amount > 0 AND first_deposit_at IS NOT NULL)),
    ADD CONSTRAINT ck_user_brokers_total_depositado
        CHECK (deposits_total >= coalesce(first_deposit_amount, 0));

COMMENT ON COLUMN user_brokers.registered_at IS
    'Cuándo llegó el aviso de registro de este número (RN-SP-077): el received_at del aviso. Se escribe una vez. NULL si el broker no ha avisado el registro.';
COMMENT ON COLUMN user_brokers.country_code IS
    'País del primer aviso de la cuenta que lo trajo, dos letras en mayúsculas (RN-SP-077). No cambia después.';
COMMENT ON COLUMN user_brokers.first_deposit_amount IS
    'Importe del primer depósito, en centésimas: el del aviso que pasó la cuenta a FIRST_DEPOSIT (RN-SP-077). NULL si ese aviso no lo trajo.';
COMMENT ON COLUMN user_brokers.deposits_total IS
    'Suma de los depósitos avisados, el primero incluido, en centésimas y en deposit_currency (RN-SP-077). Un depósito en otra moneda no suma.';
COMMENT ON COLUMN user_brokers.deposit_currency IS
    'Moneda del primer depósito que la trajo, tres letras en mayúsculas (RN-SP-077).';
