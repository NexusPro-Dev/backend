-- =============================================================================
-- V112 — Editar el enlace y el modelo de un broker (RF-SP-084; RN-SP-039
-- enmendada; requirements/sp.md v1.127.0; security.md v0.141.0; 15-10-2026).
--
-- Solo el permiso: url y model ya existen (V93, V111). El nombre, el
-- advertiser y el estado siguen siendo de migración. Desde hoy los cambios de
-- url y model por API se auditan; los de migración, como siempre, no.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a11a5c-4000-7026-9c4f-5e7ada00000f', 'brokers:update', 'brokers', 'update',
 'Editar el enlace y el modelo de un broker',
 'Cambiar url y model de un broker del catalogo por PATCH /brokers/{id} (RF-SP-084).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a11a5c-4000-7026-9c4f-5e7ada00000f'
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- -----------------------------------------------------------------------------
-- Guardas.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 251 THEN
        RAISE EXCEPTION 'V112: el catálogo debe tener 251 permisos; tiene %', filas;
    END IF;
    IF (SELECT count(*) FROM role_permissions
         WHERE permission_id = '01a11a5c-4000-7026-9c4f-5e7ada00000f') <> 2 THEN
        RAISE EXCEPTION 'V112: brokers:update va a SUPERADMIN y ADMIN, y a nadie más';
    END IF;
END $$;
