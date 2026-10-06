-- =============================================================================
-- V74 — Los permisos del módulo IN — Indicadores (RF-IN-001 · T-01;
-- requirements/in.md v0.2.0 §5.2.4, security.md §4.4; 06-10-2026).
--
-- Nacen los CUATRO de la primera tanda, no solo el del resumen: se decidieron
-- juntos y sembrarlos de uno en uno serían cuatro migraciones para una
-- decisión. Un indicador, un permiso (RN-IN-001): ese permiso ES el reparto
-- por rol, y lo que cada uno ve por él lo decide su alcance (RN-IN-002).
--
-- REPARTO POR TIPO DE ROL (RN-SEG-015): FUNCIONARIO —con SUPERADMIN y ADMIN
-- dentro— y VENDEDOR. A CONSUMIDOR NO: con su alcance vería lo que él vendió,
-- que es nada.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 del 06-10-2026
-- (01a10e829000) y la serie de IN, 5e7ad8, que estrena esta migración.
--
-- GUARDAS: 189 en el catálogo / SUPERADMIN 189 / ADMIN 187 / ningún rol
-- CONSUMIDOR con ninguno / cero filas que rompan la contención de RN-SEG-003.
--
-- SIN AUDITORÍA, como V31 y V32: nadie concedió estas filas.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7001-9c4f-5e7ad8000001', 'indicators:read-sales-summary', 'indicators',
 'read-sales-summary',
 'Consultar el resumen de ventas',
 'Ver lo confirmado, lo pendiente y lo anulado de un periodo por GET /indicators/sales/summary (RF-IN-001). El permiso abre el indicador; las cifras las acota el alcance de quien pregunta.'),
('01a10e82-9000-7002-9c4f-5e7ad8000002', 'indicators:read-sales-series', 'indicators',
 'read-sales-series',
 'Consultar la evolución de las ventas',
 'Ver lo confirmado por día, semana o mes por GET /indicators/sales/series (RF-IN-002), acotado al alcance de quien pregunta.'),
('01a10e82-9000-7003-9c4f-5e7ad8000003', 'indicators:read-sales-by-product', 'indicators',
 'read-sales-by-product',
 'Consultar las ventas por producto',
 'Ver lo confirmado por producto por GET /indicators/sales/by-product (RF-IN-003), acotado al alcance de quien pregunta.'),
('01a10e82-9000-7004-9c4f-5e7ad8000004', 'indicators:read-sales-by-seller', 'indicators',
 'read-sales-by-seller',
 'Consultar las ventas por vendedor',
 'Ver lo confirmado por vendedor por GET /indicators/sales/by-seller (RF-IN-004), acotado al alcance de quien pregunta.');

-- Por tipo, FUNCIONARIO y VENDEDOR. ON CONFLICT por si alguien los concedió a
-- mano entre dos arranques.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR')
   AND p.code LIKE 'indicators:%'
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
    IF filas <> 189 THEN
        RAISE EXCEPTION 'V74: el catálogo debe tener 189 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 189 OR de_admin <> 187 THEN
        RAISE EXCEPTION 'V74: SUPERADMIN debe portar 189 permisos y ADMIN 187 (la reserva son dos); tienen % y %', de_raiz, de_admin;
    END IF;

    SELECT count(*) INTO consumidor
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
      JOIN permissions p ON p.id = rp.permission_id
     WHERE r.role_type = 'CONSUMIDOR' AND p.code LIKE 'indicators:%';
    IF consumidor <> 0 THEN
        RAISE EXCEPTION 'V74: % filas dan un indicador a un rol CONSUMIDOR', consumidor;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V74: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
