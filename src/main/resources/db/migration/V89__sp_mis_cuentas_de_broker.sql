-- =============================================================================
-- V89 — El permiso de mis cuentas de broker (RF-SP-079 · T-01;
-- requirements/sp.md v1.110.0, security.md §4.4 v0.125.0; 08-10-2026).
--
-- Nace `broker-accounts:read-own`, el de GET /users/me/broker-accounts. Hasta
-- hoy el titular no veía sus cuentas por ninguna vía (RF-SP-055 lo dejó fuera
-- el 10-09-2026), y el responsable del proyecto pidió abrirlo.
--
-- REPARTO A TODO ROL POR SU TIPO, los tres —FUNCIONARIO, VENDEDOR y
-- CONSUMIDOR—, como el segundo factor de V75: el titular de una cuenta de
-- broker puede ser cualquiera, y los clientes son quienes más declaran
-- (RN-SP-042). Por tipo y no por código: alcanza a los roles creados a mano.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V79 (01a10e829000),
-- secuencia 7014, y la serie de broker-accounts donde V31 la dejó
-- (000005 → 000006).
--
-- GUARDAS: 210 en el catálogo / SUPERADMIN 210 / ADMIN 208 / CLIENTE lo porta
-- / cero filas que rompan RN-SEG-003.
--
-- SIN AUDITORÍA, como V31 y V75: nadie concede estas filas.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7014-9c4f-5e7ada000006', 'broker-accounts:read-own', 'broker-accounts',
 'read-own',
 'Consultar mis cuentas de broker',
 'Ver las propias cuentas de broker y su estado por GET /users/me/broker-accounts (RF-SP-079). Las de una persona a cargo son broker-accounts:read-team-member; todas, broker-accounts:read.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a10e82-9000-7014-9c4f-5e7ada000006'
  FROM roles r
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas       integer;
    de_raiz     integer;
    de_admin    integer;
    de_cliente  integer;
    sin_padre   integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 210 THEN
        RAISE EXCEPTION 'V89: el catálogo debe tener 210 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 210 OR de_admin <> 208 THEN
        RAISE EXCEPTION 'V89: SUPERADMIN debe portar 210 permisos y ADMIN 208; tienen % y %', de_raiz, de_admin;
    END IF;

    SELECT count(*) INTO de_cliente
      FROM role_permissions
     WHERE role_id = '01a02a33-4c00-7008-9c4f-5e7ad1000008'
       AND permission_id = '01a10e82-9000-7014-9c4f-5e7ada000006';
    IF de_cliente <> 1 THEN
        RAISE EXCEPTION 'V89: CLIENTE debe portar broker-accounts:read-own';
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V89: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
