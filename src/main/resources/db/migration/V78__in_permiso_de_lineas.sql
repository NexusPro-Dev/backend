-- =============================================================================
-- V78 — El permiso del resumen de líneas de venta (RF-IN-006 · T-01;
-- requirements/in.md v0.9.0, security.md §4.4; 06-10-2026).
--
-- Nace `indicators:read-sale-lines-summary`, el de
-- GET /indicators/sales/lines/summary. Es un indicador de ADMINISTRACIÓN y SIN
-- ALCANCE (RN-IN-011): quien porte el permiso ve las cifras de toda la
-- plataforma, incluidas las líneas sin vendedor. Por eso se da solo a
-- SUPERADMIN y ADMIN, explícitos, y no por tipo de rol: dárselo a un rol
-- vendedor es darle esa vista entera, y esa decisión es de quien administra.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 del 06-10-2026 de V74
-- (01a10e829000), secuencia 7006, y la serie de IN donde V76 la dejó
-- (000005 → 000006).
--
-- GUARDAS: 203 en el catálogo / SUPERADMIN 203 / ADMIN 201 / ningún otro rol
-- con él / cero filas que rompan la contención de RN-SEG-003.
--
-- SIN AUDITORÍA, como V74 y V76.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7006-9c4f-5e7ad8000006', 'indicators:read-sale-lines-summary', 'indicators',
 'read-sale-lines-summary',
 'Consultar el resumen de líneas de venta',
 'Ver, sobre todas las líneas de venta de la plataforma, las unidades vendidas, las ventas por estado y lo que no tiene vendedor, por GET /indicators/sales/lines/summary (RF-IN-006). Sin alcance: quien lo porte ve las cifras enteras.');

INSERT INTO role_permissions (role_id, permission_id) VALUES
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a10e82-9000-7006-9c4f-5e7ad8000006'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a10e82-9000-7006-9c4f-5e7ad8000006');

DO $$
DECLARE
    filas      integer;
    de_raiz    integer;
    de_admin   integer;
    otros      integer;
    sin_padre  integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 203 THEN
        RAISE EXCEPTION 'V78: el catálogo debe tener 203 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 203 OR de_admin <> 201 THEN
        RAISE EXCEPTION 'V78: SUPERADMIN debe portar 203 permisos y ADMIN 201; tienen % y %', de_raiz, de_admin;
    END IF;

    SELECT count(*) INTO otros
      FROM role_permissions
     WHERE permission_id = '01a10e82-9000-7006-9c4f-5e7ad8000006'
       AND role_id NOT IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a02a33-4c00-7002-9c4f-5e7ad1000002');
    IF otros <> 0 THEN
        RAISE EXCEPTION 'V78: % roles además de SUPERADMIN y ADMIN portan el permiso', otros;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V78: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
