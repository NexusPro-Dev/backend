-- =============================================================================
-- V116 — Las cuentas de broker de mi red (RF-SP-057; requirements/sp.md
-- v1.134.0, RN-SP-081; security.md v0.143.0; 16-10-2026).
--
--   * broker-accounts:read pasa también a los roles VENDEDOR por su TIPO:
--     GET /broker-accounts acota a un vendedor a él y su red, y a
--     administración no la acota. Sin permiso nuevo: el catálogo no cambia.
--   * GET /users/{id}/broker-accounts deja de abrirse con solo este permiso:
--     ahí lo exige junto al alcance entero (FUNCIONARIO), en el servicio.
-- =============================================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a0889d-3800-7001-9c4f-5e7ada000002'
  FROM roles r
 WHERE r.role_type = 'VENDEDOR'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM roles r
              WHERE r.role_type = 'VENDEDOR'
                AND NOT EXISTS (SELECT 1 FROM role_permissions rp
                                 WHERE rp.role_id = r.id
                                   AND rp.permission_id = '01a0889d-3800-7001-9c4f-5e7ada000002')) THEN
    RAISE EXCEPTION 'V116: broker-accounts:read tiene que ir a todo rol VENDEDOR';
  END IF;
END $$;
