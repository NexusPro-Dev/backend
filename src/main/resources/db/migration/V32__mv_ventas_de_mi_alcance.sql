-- =============================================================================
-- V32 — Las ventas de mi alcance (RF-MV-015, RN-MV-031, requirements/mv.md
-- §6, security.md §4.4, 21-09-2026).
--
-- Nace `movements:list-sales`, el permiso de GET /movements/sales: la vista de
-- ventas de CUALQUIERA. Lo que cada uno ve por ella no lo decide el permiso
-- sino RN-MV-031 —el consumidor lo suyo, el vendedor lo suyo y toda su red
-- hacia abajo, quien administra todo—, y por eso el reparto es el más ancho
-- posible: a TODO ROL, de sistema o creado a mano, POR SU TIPO, los tres
-- tipos. Es la misma lógica de V31 (los de alcance propio) y no la de V28 (los
-- hijos de un padre): no es hijo de nadie, la operación no existía.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 del 21-09-2026 (01a0c1432c00)
-- continuando la secuencia de V31 (700c) y la serie de MV (5e7ad7) donde V31
-- la dejó (000007 → 000008).
--
-- GUARDAS: 125 en el catálogo / SUPERADMIN 125 / ADMIN 119 / cero filas que
-- rompan la contención de RN-SEG-003. Aborta si no cuadra.
--
-- SIN AUDITORÍA, como V8, V22 y V28 a V31: nadie concedió estas filas.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0c143-2c00-700c-9c4f-5e7ad7000008', 'movements:list-sales', 'movements', 'list-sales',
 'Consultar las ventas de mi alcance',
 'Ver las ventas por GET /movements/sales (RF-MV-015). El permiso abre la ruta; lo que se ve lo decide RN-MV-031: el consumidor las suyas, el vendedor las suyas y las de toda su red, quien administra todas. El libro entero con todos los filtros es movements:read.');

-- A TODO ROL, por tipo: los tres tipos. ON CONFLICT por si alguien lo concedió
-- a mano entre dos arranques.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a0c143-2c00-700c-9c4f-5e7ad7000008'
  FROM roles r
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas     integer;
    de_raiz   integer;
    de_admin  integer;
    sin_padre integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 125 THEN
        RAISE EXCEPTION 'V32: el catálogo debe tener 125 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 125 OR de_admin <> 119 THEN
        RAISE EXCEPTION 'V32: SUPERADMIN debe portar 125 permisos y ADMIN 119 (la reserva son seis); tienen % y %', de_raiz, de_admin;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V32: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
