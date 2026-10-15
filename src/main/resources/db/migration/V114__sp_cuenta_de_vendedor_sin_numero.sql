-- ---------------------------------------------------------------------------
-- Una cuenta VENDEDOR puede no tener número en el broker (RN-SP-078,
-- RF-SP-053; requirements/sp.md v1.130.0; 15-10-2026).
--
-- Decisión del responsable del proyecto: el vendedor se identifica en el
-- broker por su afftrack; el número de cuenta es opcional para él y
-- obligatorio para el consumidor, que es a quien se le atribuye el depósito.
--
-- La unicidad no cambia: (broker_id, external_id) y (broker_id, afftrack).
-- Postgres no compara nulos en un UNIQUE, de modo que varias VENDEDOR sin
-- número conviven en el mismo broker.
-- ---------------------------------------------------------------------------

ALTER TABLE user_brokers
    ALTER COLUMN external_id DROP NOT NULL,
    ADD CONSTRAINT ck_user_brokers_numero_solo_vendedor
        CHECK (external_id IS NOT NULL OR kind = 'VENDEDOR');

COMMENT ON CONSTRAINT ck_user_brokers_numero_solo_vendedor ON user_brokers IS
    'RN-SP-078: solo una cuenta VENDEDOR puede no tener número en el broker.';
