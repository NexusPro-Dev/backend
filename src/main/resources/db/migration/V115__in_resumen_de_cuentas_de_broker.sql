-- =============================================================================
-- V115 — El resumen de cuentas de broker y de mi personal (RF-IN-010 · T-01;
-- requirements/in.md v0.22.0; security.md v0.142.0; 15-10-2026).
--
--   * Nace indicators:read-broker-accounts-summary, a los roles FUNCIONARIO y
--     VENDEDOR por su TIPO, como V101: el alcance lo pone RN-IN-002.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V79, secuencia 701e, y la
-- serie de indicators donde V101 la dejó (000009 → 00000a).
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-701e-9c4f-5e7ad800000a', 'indicators:read-broker-accounts-summary', 'indicators',
 'read-broker-accounts-summary',
 'Consultar el resumen de cuentas de broker y de mi personal',
 'Ver los totales de cuentas de broker de consumidor —registros, FTD, sin titular, sin vendedor de origen, y registros y FTD de hoy— y el personal por rol con lo que originó cada uno, por GET /indicators/broker-accounts/summary (RF-IN-010). Administración ve todo; un vendedor, su red.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a10e82-9000-701e-9c4f-5e7ad800000a'
  FROM roles r
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM roles r
              WHERE r.role_type IN ('VENDEDOR', 'FUNCIONARIO')
                AND NOT EXISTS (SELECT 1 FROM role_permissions rp
                                 WHERE rp.role_id = r.id
                                   AND rp.permission_id = '01a10e82-9000-701e-9c4f-5e7ad800000a'))
     OR EXISTS (SELECT 1 FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                 WHERE rp.permission_id = '01a10e82-9000-701e-9c4f-5e7ad800000a'
                   AND r.role_type NOT IN ('VENDEDOR', 'FUNCIONARIO')) THEN
    RAISE EXCEPTION 'V115: indicators:read-broker-accounts-summary tiene que ir a todo rol VENDEDOR o FUNCIONARIO, y a ningún otro';
  END IF;
END $$;
