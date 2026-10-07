-- =============================================================================
-- V79 — El permiso del resumen de lotes de comisiones (RF-IN-007 · T-01;
-- requirements/in.md v0.14.0, security.md §4.4; 07-10-2026).
--
-- Nace `indicators:read-commission-batches-summary`, el de
-- GET /indicators/commissions/batches/summary. Es un indicador de
-- ADMINISTRACIÓN y SIN ALCANCE (RN-IN-011), como el de V78: quien porte el
-- permiso ve los lotes de todas las personas. Por eso se da solo a SUPERADMIN
-- y ADMIN, explícitos, y no por tipo de rol.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 del 06-10-2026 de V74
-- (01a10e829000), secuencia 7007, y la serie de IN donde V78 la dejó
-- (000006 → 000007).
--
-- GUARDAS: 204 en el catálogo / SUPERADMIN 204 / ADMIN 202 / ningún otro rol
-- con él / cero filas que rompan la contención de RN-SEG-003.
--
-- SIN AUDITORÍA, como V74, V76 y V78.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7007-9c4f-5e7ad8000007', 'indicators:read-commission-batches-summary', 'indicators',
 'read-commission-batches-summary',
 'Consultar el resumen de lotes de comisiones',
 'Ver cuántos lotes de comisiones hay hoy abiertos, pendientes de pago y pagados, y por cuánto en cada moneda, por GET /indicators/commissions/batches/summary (RF-IN-007). Sin alcance: quien lo porte ve los lotes de todas las personas.');

INSERT INTO role_permissions (role_id, permission_id) VALUES
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a10e82-9000-7007-9c4f-5e7ad8000007'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a10e82-9000-7007-9c4f-5e7ad8000007');

DO $$
DECLARE
    filas      integer;
    de_raiz    integer;
    de_admin   integer;
    otros      integer;
    sin_padre  integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 204 THEN
        RAISE EXCEPTION 'V79: el catálogo debe tener 204 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 204 OR de_admin <> 202 THEN
        RAISE EXCEPTION 'V79: SUPERADMIN debe portar 204 permisos y ADMIN 202; tienen % y %', de_raiz, de_admin;
    END IF;

    SELECT count(*) INTO otros
      FROM role_permissions
     WHERE permission_id = '01a10e82-9000-7007-9c4f-5e7ad8000007'
       AND role_id NOT IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a02a33-4c00-7002-9c4f-5e7ad1000002');
    IF otros <> 0 THEN
        RAISE EXCEPTION 'V79: % roles además de SUPERADMIN y ADMIN portan el permiso', otros;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V79: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
