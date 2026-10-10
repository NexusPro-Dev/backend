-- =============================================================================
-- V100 — Las cuentas de broker que originó mi red (RF-SP-083 · T-01, RF-SP-057
-- · T-15; requirements/sp.md v1.121.0, RN-SP-075; security.md v0.133.0;
-- modelo-datos.md v0.116.0; 10-10-2026).
--
--   * El permiso broker-accounts:read-own-referred, a los roles VENDEDOR y
--     FUNCIONARIO por su TIPO, como broker-accounts:read-own-team (V31): un
--     CONSUMIDOR no tiene enlace que origine cuentas.
--   * ix_user_brokers_busqueda_usuario: el `search` de RF-SP-057 y RF-SP-083
--     busca también el nombre de usuario en el broker, con la expresión del
--     predicado, como ix_user_brokers_busqueda.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V79, secuencia 701c, y la
-- serie de broker-accounts donde V96 la dejó (00000d → 00000e).
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-701c-9c4f-5e7ada00000e', 'broker-accounts:read-own-referred', 'broker-accounts',
 'read-own-referred',
 'Consultar las cuentas de broker que originó mi red',
 'Ver las cuentas de consumidor creadas con el afftrack propio y con el de toda la red hacia abajo, por GET /users/me/referred-broker-accounts (RF-SP-083, RN-SP-075). Todas las cuentas son broker-accounts:read.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a10e82-9000-701c-9c4f-5e7ada00000e'
  FROM roles r
 WHERE r.role_type IN ('VENDEDOR', 'FUNCIONARIO')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM roles r
              WHERE r.role_type IN ('VENDEDOR', 'FUNCIONARIO')
                AND NOT EXISTS (SELECT 1 FROM role_permissions rp
                                 WHERE rp.role_id = r.id
                                   AND rp.permission_id = '01a10e82-9000-701c-9c4f-5e7ada00000e'))
     OR EXISTS (SELECT 1 FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                 WHERE rp.permission_id = '01a10e82-9000-701c-9c4f-5e7ada00000e'
                   AND r.role_type NOT IN ('VENDEDOR', 'FUNCIONARIO')) THEN
    RAISE EXCEPTION 'V100: broker-accounts:read-own-referred tiene que ir a todo rol VENDEDOR o FUNCIONARIO, y a ningún otro';
  END IF;
END $$;

CREATE INDEX ix_user_brokers_busqueda_usuario
    ON user_brokers USING gin (f_unaccent(lower(broker_username)) gin_trgm_ops);

COMMENT ON INDEX ix_user_brokers_busqueda_usuario IS
    'El search de RF-SP-057 y RF-SP-083 por nombre de usuario en el broker. La expresión es la del predicado.';
