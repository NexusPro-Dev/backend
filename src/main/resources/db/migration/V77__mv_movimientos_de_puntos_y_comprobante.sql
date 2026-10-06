-- =============================================================================
-- V77 — Los movimientos de puntos en una sola consulta, y el comprobante del
-- ajuste (RF-MV-055 a RF-MV-057; requirements/mv.md v0.88.0 §4.12, §6 y §7.16;
-- RN-MV-077; 06-10-2026).
--
--   1. points_adjustment_receipts: el comprobante de un ajuste, uno por ajuste
--      (la clave primaria es el movimiento). En la base, como las portadas
--      (V44): mismo tope de 5 MB. Que el movimiento sea un AJUSTE_PUNTOS lo
--      sostiene el caso de uso: un CHECK no consulta movement_types.
--   2. Los índices de las dos listas, parciales por los DOS tipos de puntos con
--      sus identificadores literales (V58 y V72). ix_movements_puntos sustituye
--      a ix_movements_ajustes (V73), cuyo listado se retira.
--   3. Se RENOMBRAN los permisos de los dos listados retirados —conserva sus
--      asignaciones, también las hechas a mano— y nacen cinco: detalle y
--      descarga en cada alcance, y adjuntar. Los propios por tipo de rol
--      (RN-SEG-015, como V58); los de administración a SUPERADMIN y ADMIN.
--      Ninguno es sensible: requires_recent_mfa queda en false (V75).
--
-- IDENTIFICADORES LITERALES (Art. V.11): marca v7 del 06-10-2026
-- (01a10e829000), secuencia 7201..7205, y la serie de permisos de MV donde V73
-- la dejó (000064 → 000065..000069).
--
-- GUARDAS: 202 en el catálogo / SUPERADMIN 202 / ADMIN 200 / los dos códigos
-- viejos no existen / contención de RN-SEG-003.
-- =============================================================================

CREATE TABLE points_adjustment_receipts (
    movement_id   uuid         NOT NULL,
    file_name     varchar(255) NOT NULL,
    content_type  varchar(30)  NOT NULL,
    size_bytes    integer      NOT NULL,
    sha256        char(64)     NOT NULL,
    content       bytea        NOT NULL,
    uploaded_by   uuid         NULL,
    uploaded_at   timestamptz  NOT NULL,
    CONSTRAINT pk_points_adjustment_receipts PRIMARY KEY (movement_id),
    CONSTRAINT fk_points_adjustment_receipts_movement
        -- CASCADE: en producción un movimiento no se borra (RN-MV-001); sí lo hacen las suites,
        -- y una FK sin ON DELETE las rompe lejos de aquí.
        FOREIGN KEY (movement_id) REFERENCES movements (id) ON DELETE CASCADE,
    CONSTRAINT fk_points_adjustment_receipts_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_points_adjustment_receipts_type
        CHECK (content_type IN ('application/pdf', 'image/png', 'image/jpeg')),
    CONSTRAINT ck_points_adjustment_receipts_size
        CHECK (size_bytes BETWEEN 1 AND 5242880 AND octet_length(content) = size_bytes),
    CONSTRAINT ck_points_adjustment_receipts_name CHECK (length(btrim(file_name)) > 0)
);

COMMENT ON TABLE points_adjustment_receipts IS
    'RN-MV-077: el comprobante de un ajuste de puntos (PDF, PNG o JPG hasta 5 MB, reconocido por su contenido). Reemplazar es un UPDATE de la misma fila; la auditoría guarda el resumen del anterior.';

DROP INDEX ix_movements_ajustes;

CREATE INDEX ix_movements_puntos ON movements (occurred_at DESC, id DESC)
    WHERE movement_type_id IN ('01a0ef9c-6800-7001-9c4f-5e7ad7000015',
                               '01a0ef9c-6800-7025-9c4f-5e7ad7000016');

CREATE INDEX ix_movements_puntos_persona ON movements (user_id, occurred_at DESC, id DESC)
    WHERE movement_type_id IN ('01a0ef9c-6800-7001-9c4f-5e7ad7000015',
                               '01a0ef9c-6800-7025-9c4f-5e7ad7000016');

UPDATE permissions
   SET code = 'movements:list-own-points-movements', action = 'list-own-points-movements',
       name = 'Consultar mis movimientos de puntos',
       description = 'Ver las compras de puntos propias y los ajustes recibidos, por GET /movements/mine/points-movements (RF-MV-055). Hasta V77 era movements:list-own-points-purchases (RF-MV-031).'
 WHERE id = '01a0ef9c-6800-7008-9c4f-5e7ad7000047';

UPDATE permissions
   SET code = 'movements:list-points-movements', action = 'list-points-movements',
       name = 'Consultar los movimientos de puntos',
       description = 'Ver las compras y los ajustes de puntos de cualquier persona, con quién hizo cada ajuste, por GET /movements/points-movements (RF-MV-056). Hasta V77 era movements:list-points-adjustments (RF-MV-053).'
 WHERE id = '01a0ef9c-6800-7028-9c4f-5e7ad7000063';

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7201-9c4f-5e7ad7000065', 'movements:read-own-points-movement', 'movements',
 'read-own-points-movement',
 'Consultar el detalle de un movimiento de puntos propio',
 'Ver una compra de puntos o un ajuste propio con sus pagos o su comprobante, por GET /movements/mine/points-movements/{id} (RF-MV-055).'),
('01a10e82-9000-7202-9c4f-5e7ad7000066', 'movements:download-own-points-receipt', 'movements',
 'download-own-points-receipt',
 'Descargar el comprobante de un ajuste propio',
 'Descargar el comprobante de un ajuste de puntos propio, por GET /movements/mine/points-movements/{id}/receipt (RF-MV-055, RN-MV-077).'),
('01a10e82-9000-7203-9c4f-5e7ad7000067', 'movements:read-points-movement', 'movements',
 'read-points-movement',
 'Consultar el detalle de un movimiento de puntos',
 'Ver cualquier compra de puntos o ajuste con sus pagos o su comprobante, por GET /movements/points-movements/{id} (RF-MV-056).'),
('01a10e82-9000-7204-9c4f-5e7ad7000068', 'movements:download-points-receipt', 'movements',
 'download-points-receipt',
 'Descargar el comprobante de un ajuste',
 'Descargar el comprobante de cualquier ajuste de puntos, por GET /movements/points-movements/{id}/receipt (RF-MV-056, RN-MV-077).'),
('01a10e82-9000-7205-9c4f-5e7ad7000069', 'movements:attach-points-receipt', 'movements',
 'attach-points-receipt',
 'Adjuntar el comprobante de un ajuste',
 'Adjuntar o reemplazar el comprobante —PDF, PNG o JPG— de un ajuste de puntos ya hecho, por PUT /movements/points-adjustments/{id}/receipt (RF-MV-057, RN-MV-077).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE p.id IN ('01a10e82-9000-7201-9c4f-5e7ad7000065',
                '01a10e82-9000-7202-9c4f-5e7ad7000066')
   AND r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id) VALUES
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a10e82-9000-7203-9c4f-5e7ad7000067'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a10e82-9000-7203-9c4f-5e7ad7000067'),
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a10e82-9000-7204-9c4f-5e7ad7000068'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a10e82-9000-7204-9c4f-5e7ad7000068'),
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a10e82-9000-7205-9c4f-5e7ad7000069'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a10e82-9000-7205-9c4f-5e7ad7000069')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas      integer;
    de_raiz    integer;
    de_admin   integer;
    viejos     integer;
    sin_padre  integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 202 THEN
        RAISE EXCEPTION 'V77: el catálogo debe tener 202 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 202 OR de_admin <> 200 THEN
        RAISE EXCEPTION 'V77: SUPERADMIN debe portar 202 permisos y ADMIN 200; tienen % y %', de_raiz, de_admin;
    END IF;

    SELECT count(*) INTO viejos FROM permissions
     WHERE code IN ('movements:list-own-points-purchases', 'movements:list-points-adjustments');
    IF viejos <> 0 THEN
        RAISE EXCEPTION 'V77: siguen % permisos con el código retirado', viejos;
    END IF;

    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V77: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
