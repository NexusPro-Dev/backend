-- =============================================================================
-- V76 — El permiso del resumen de puntos (RF-IN-005 · T-01; requirements/in.md
-- v0.6.0, security.md §4.4; 06-10-2026).
--
-- Nace `indicators:read-points-summary`, el de GET /indicators/points/summary.
-- Permiso propio y no uno de ventas (RN-IN-001): se puede querer dar las cifras
-- de ventas sin las de puntos. Lo que cada uno ve por él lo decide su alcance
-- (RN-IN-002), como en los cuatro de ventas de V74.
--
-- REPARTO POR TIPO DE ROL (RN-SEG-015), el de V74: FUNCIONARIO —con SUPERADMIN
-- y ADMIN dentro— y VENDEDOR. A CONSUMIDOR no. No es una operación sensible:
-- requires_recent_mfa queda en su valor por omisión, false (V75).
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 del 06-10-2026 de V74
-- (01a10e829000), secuencia 7005, y la serie de IN donde V74 la dejó
-- (000004 → 000005).
--
-- GUARDAS: 197 en el catálogo / SUPERADMIN 197 / ADMIN 195 / ningún rol
-- CONSUMIDOR con ningún indicators: / cero filas que rompan RN-SEG-003.
--
-- SIN AUDITORÍA, como V74.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7005-9c4f-5e7ad8000005', 'indicators:read-points-summary', 'indicators',
 'read-points-summary',
 'Consultar el resumen de puntos',
 'Ver los puntos comprados, redimidos y ajustados de un periodo, y el saldo de hoy, por GET /indicators/points/summary (RF-IN-005). El permiso abre el indicador; las cifras las acota el alcance de quien pregunta.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a10e82-9000-7005-9c4f-5e7ad8000005'
  FROM roles r
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas       integer;
    de_raiz     integer;
    de_admin    integer;
    consumidor  integer;
    sin_padre   integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 197 THEN
        RAISE EXCEPTION 'V76: el catálogo debe tener 197 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 197 OR de_admin <> 195 THEN
        RAISE EXCEPTION 'V76: SUPERADMIN debe portar 197 permisos y ADMIN 195; tienen % y %', de_raiz, de_admin;
    END IF;

    SELECT count(*) INTO consumidor
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
      JOIN permissions p ON p.id = rp.permission_id
     WHERE r.role_type = 'CONSUMIDOR' AND p.code LIKE 'indicators:%';
    IF consumidor <> 0 THEN
        RAISE EXCEPTION 'V76: % filas dan un indicador a un rol CONSUMIDOR', consumidor;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V76: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
