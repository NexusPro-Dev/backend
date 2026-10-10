-- =============================================================================
-- V101 — Los indicadores de cuentas de broker se mudan a IN (RF-IN-009 · T-01;
-- requirements/in.md v0.21.0, RN-IN-015; requirements/sp.md v1.122.0;
-- security.md v0.134.0; 10-10-2026).
--
--   * Nace indicators:read-broker-accounts-network, a los roles FUNCIONARIO y
--     VENDEDOR por su TIPO, como los de V74: el alcance lo pone RN-IN-002.
--   * Se retira broker-accounts:read-indicators con su reparto: su ruta,
--     GET /api/v1/broker-accounts/indicators (RF-SP-058), desaparece.
--
-- El catálogo no cambia de tamaño: uno entra y otro sale.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V79, secuencia 701d, y la
-- serie de indicators donde V83 la dejó (000008 → 000009).
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-701d-9c4f-5e7ad8000009', 'indicators:read-broker-accounts-network', 'indicators',
 'read-broker-accounts-network',
 'Consultar los indicadores de cuentas de broker de la red',
 'Ver el árbol de la red comercial con las cuentas de broker que originó cada vendedor por su afftrack, sus FTD, sus operaciones y las sin titular, por GET /indicators/broker-accounts/network (RF-IN-009, RN-IN-015). Administración ve el árbol entero; un vendedor, su rama.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a10e82-9000-701d-9c4f-5e7ad8000009'
  FROM roles r
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DELETE FROM role_permissions
 WHERE permission_id = (SELECT id FROM permissions WHERE code = 'broker-accounts:read-indicators');
DELETE FROM permissions WHERE code = 'broker-accounts:read-indicators';

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM roles r
              WHERE r.role_type IN ('VENDEDOR', 'FUNCIONARIO')
                AND NOT EXISTS (SELECT 1 FROM role_permissions rp
                                 WHERE rp.role_id = r.id
                                   AND rp.permission_id = '01a10e82-9000-701d-9c4f-5e7ad8000009'))
     OR EXISTS (SELECT 1 FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                 WHERE rp.permission_id = '01a10e82-9000-701d-9c4f-5e7ad8000009'
                   AND r.role_type NOT IN ('VENDEDOR', 'FUNCIONARIO')) THEN
    RAISE EXCEPTION 'V101: indicators:read-broker-accounts-network tiene que ir a todo rol VENDEDOR o FUNCIONARIO, y a ningún otro';
  END IF;
  IF EXISTS (SELECT 1 FROM permissions WHERE code = 'broker-accounts:read-indicators') THEN
    RAISE EXCEPTION 'V101: broker-accounts:read-indicators sigue en el catálogo';
  END IF;
END $$;
