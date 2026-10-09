-- =============================================================================
-- V96 — Cuentas de broker sin titular, afftrack y cuenta de origen
-- (RF-SP-053 · T-13, RF-SP-078 · T-15, RF-SP-082 · T-01; requirements/sp.md
-- v1.118.0, RN-SP-070 a RN-SP-072; modelo-datos.md v0.114.0; security.md
-- v0.131.0; 09-10-2026).
--
-- Los dos casos del responsable del proyecto:
--   1. La cuenta nace en la plataforma (registro o alta): tiene titular, y si
--      es CONSUMIDOR apunta a la VENDEDOR de su vendedor principal.
--   2. La cuenta nace en el broker por el enlace del vendedor: el aviso de
--      registro la crea SIN TITULAR, apuntando a la VENDEDOR de su afftrack, y
--      se asocia después.
--
--   * user_id admite NULL, solo en CONSUMIDOR.
--   * afftrack: el código de afiliado de una VENDEDOR; único por broker.
--   * Una VENDEDOR por vendedor y broker: «la de su vendedor» no es ambigua.
--   * referrer_account_id: la VENDEDOR de origen, del MISMO broker (clave
--     foránea compuesta), sin ON DELETE: un origen no se borra (EX-012).
--   * El permiso broker-accounts:assign-user (RF-SP-082) a SUPERADMIN y ADMIN.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V79, secuencia 701b, y la
-- serie de broker-accounts donde V90 la dejó (00000c → 00000d).
-- =============================================================================

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM user_brokers WHERE kind = 'VENDEDOR'
              GROUP BY user_id, broker_id HAVING count(*) > 1) THEN
    RAISE EXCEPTION 'V96: hay vendedores con más de una cuenta VENDEDOR en el mismo broker; resuélvalo antes de migrar';
  END IF;
END $$;

ALTER TABLE user_brokers ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE user_brokers
    ADD COLUMN afftrack            varchar(80) NULL,
    ADD COLUMN referrer_account_id uuid        NULL,
    ADD CONSTRAINT ck_user_brokers_titular_solo_consumidor
        CHECK (user_id IS NOT NULL OR kind = 'CONSUMIDOR'),
    ADD CONSTRAINT ck_user_brokers_afftrack_solo_vendedor
        CHECK (afftrack IS NULL OR kind = 'VENDEDOR'),
    ADD CONSTRAINT ck_user_brokers_origen_solo_consumidor
        CHECK (referrer_account_id IS NULL OR kind = 'CONSUMIDOR'),
    ADD CONSTRAINT uq_user_brokers_id_broker UNIQUE (id, broker_id);

ALTER TABLE user_brokers
    ADD CONSTRAINT fk_user_brokers_origen
        FOREIGN KEY (referrer_account_id, broker_id)
        REFERENCES user_brokers (id, broker_id);

CREATE UNIQUE INDEX uq_user_brokers_afftrack
    ON user_brokers (broker_id, lower(afftrack)) WHERE afftrack IS NOT NULL;
CREATE UNIQUE INDEX uq_user_brokers_vendedor_por_broker
    ON user_brokers (user_id, broker_id) WHERE kind = 'VENDEDOR';
CREATE INDEX ix_user_brokers_origen
    ON user_brokers (referrer_account_id) WHERE referrer_account_id IS NOT NULL;

COMMENT ON COLUMN user_brokers.user_id IS
    'El titular. NULL = la cuenta llegó del broker antes que la persona (RN-SP-072); solo CONSUMIDOR.';
COMMENT ON COLUMN user_brokers.afftrack IS
    'Código de afiliado de una cuenta VENDEDOR (RN-SP-071). Único por broker sin distinguir mayúsculas.';
COMMENT ON COLUMN user_brokers.referrer_account_id IS
    'La cuenta VENDEDOR que originó esta CONSUMIDOR, del mismo broker (RN-SP-070). NULL = sin origen conocido.';

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-701b-9c4f-5e7ada00000d', 'broker-accounts:assign-user', 'broker-accounts',
 'assign-user',
 'Asignar titular a una cuenta de broker',
 'Dar titular a una cuenta de broker que llegó del broker sin él, por PATCH /broker-accounts/{id}/holder (RF-SP-082, RN-SP-072).');

INSERT INTO role_permissions (role_id, permission_id) VALUES
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a10e82-9000-701b-9c4f-5e7ada00000d'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a10e82-9000-701b-9c4f-5e7ada00000d')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
BEGIN
  IF (SELECT count(*) FROM role_permissions
       WHERE permission_id = '01a10e82-9000-701b-9c4f-5e7ada00000d') <> 2 THEN
    RAISE EXCEPTION 'V96: broker-accounts:assign-user tiene que ir a SUPERADMIN y ADMIN, y a nadie más';
  END IF;
END $$;
