-- ---------------------------------------------------------------------------
-- V36 — Estados por tipo de movimiento, y el permiso para asignar los
-- vendedores de una venta (RF-MV-016; requirements/mv.md §7.2.2, RN-MV-033 a
-- RN-MV-035, security.md §4.4, 23-09-2026).
--
-- Tres cosas, y van juntas porque ninguna sirve sin las otras:
--
--   1. El CATÁLOGO. Cada tipo declara sus estados; la venta, dos:
--      VALIDAR_COMISIONES —falta decidir a qué vendedor se atribuye alguna
--      línea— y VALIDADO —todas tienen vendedor—. Es un eje APARTE de
--      `status` (el pago) y de `delivery_status` (la entrega).
--
--   2. La COLUMNA. `movements.type_status_id`, atada a su tipo por una clave
--      foránea COMPUESTA: el esquema, y no el caso de uso, impide que una
--      venta lleve el estado de otro tipo. Lo ya vendido pasa a VALIDADO,
--      porque hasta hoy toda línea de venta tenía vendedor. SIN DEFAULT: una
--      escritura que olvide el estado no puede producir una venta validada.
--
--   3. El PERMISO `movements:assign-sellers`, el 134, a SUPERADMIN y ADMIN
--      EXPLÍCITOS y a nadie más, con la forma de V34.
--
-- IDENTIFICADORES LITERALES (Art. V.11): los estados siguen la serie de los
-- catálogos de MV —`VENTA` es …7000011, los métodos de pago …7000021 a 23—;
-- el permiso, la de `movements:list-sales` (V32, …700c/…7000008).
-- ---------------------------------------------------------------------------

CREATE TABLE movement_type_statuses (
    id               uuid         PRIMARY KEY,
    movement_type_id uuid         NOT NULL,
    code             varchar(50)  NOT NULL,
    name             varchar(100) NOT NULL,
    created_at       timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_movement_type_statuses_code UNIQUE (movement_type_id, code),
    -- Lo que la clave compuesta de `movements` referencia.
    CONSTRAINT uq_movement_type_statuses_tipo UNIQUE (id, movement_type_id),
    CONSTRAINT ck_movement_type_statuses_code
        CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT fk_movement_type_statuses_type
        FOREIGN KEY (movement_type_id) REFERENCES movement_types (id) ON DELETE RESTRICT
);

COMMENT ON TABLE movement_type_statuses IS
    'RN-MV-033: los estados que declara cada tipo de movimiento. Sembrado por migracion, sin API (como movement_types, RN-MV-017). Un eje aparte del pago (movements.status) y de la entrega (movement_details.delivery_status).';

INSERT INTO movement_type_statuses (id, movement_type_id, code, name) VALUES
('01a061ba-3400-7004-9c4f-5e7ad7000031', '01a061ba-3400-7001-9c4f-5e7ad7000011',
 'VALIDAR_COMISIONES', 'Validar comisiones'),
('01a061ba-3400-7005-9c4f-5e7ad7000032', '01a061ba-3400-7001-9c4f-5e7ad7000011',
 'VALIDADO', 'Validado');

ALTER TABLE movements ADD COLUMN type_status_id uuid NULL;

UPDATE movements
   SET type_status_id = '01a061ba-3400-7005-9c4f-5e7ad7000032'
 WHERE movement_type_id = '01a061ba-3400-7001-9c4f-5e7ad7000011';

ALTER TABLE movements ALTER COLUMN type_status_id SET NOT NULL;

ALTER TABLE movements
    ADD CONSTRAINT fk_movements_type_status
        FOREIGN KEY (type_status_id, movement_type_id)
        REFERENCES movement_type_statuses (id, movement_type_id) ON DELETE RESTRICT;

CREATE INDEX ix_movements_type_status ON movements (type_status_id);

COMMENT ON COLUMN movements.type_status_id IS
    'RN-MV-033: el estado del TIPO (en una venta, VALIDAR_COMISIONES o VALIDADO). Clave compuesta con movement_type_id: no puede llevar el estado de otro tipo. Sin DEFAULT a proposito.';
COMMENT ON COLUMN movement_details.seller_id IS
    'RN-MV-003 y RN-MV-034: quien le vendio ESTA linea; a el se le creara la comision. NULL en los tipos que no venden nada y, desde V36, en una VENTA mientras este VALIDAR_COMISIONES. Se asigna y se corrige hasta confirmar (RF-MV-016).';

-- ---------------------------------------------------------------------------
-- El permiso.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0c143-2c00-7015-9c4f-5e7ad7000009', 'movements:assign-sellers', 'movements', 'assign-sellers',
 'Asignar los vendedores de una venta',
 'Asignar o corregir el vendedor de las lineas de una venta, eligiendolo entre los vendedores del cliente (RF-MV-016, RN-MV-035). Cuando ninguna linea queda sin vendedor, la venta pasa a VALIDADO. Lo asignado solo se corrige mientras la venta no este confirmada.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a0c143-2c00-7015-9c4f-5e7ad7000009'
  FROM roles r
 WHERE r.id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',   -- SUPERADMIN
                '01a02a33-4c00-7002-9c4f-5e7ad1000002')   -- ADMIN
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas     integer;
    de_raiz   integer;
    de_admin  integer;
    de_otros  integer;
    sin_padre integer;
    sin_estado integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 134 THEN
        RAISE EXCEPTION 'V36: el catálogo debe tener 134 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 134 OR de_admin <> 128 THEN
        RAISE EXCEPTION 'V36: SUPERADMIN debe portar 134 permisos y ADMIN 128 (la reserva son seis); tienen % y %', de_raiz, de_admin;
    END IF;

    -- Y a nadie más: decidir a quién se le paga una venta es de administración.
    SELECT count(*) INTO de_otros
      FROM role_permissions
     WHERE permission_id = '01a0c143-2c00-7015-9c4f-5e7ad7000009'
       AND role_id NOT IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                           '01a02a33-4c00-7002-9c4f-5e7ad1000002');
    IF de_otros <> 0 THEN
        RAISE EXCEPTION 'V36: ningún rol distinto de SUPERADMIN y ADMIN debe portar movements:assign-sellers; hay % filas', de_otros;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V36: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;

    SELECT count(*) INTO sin_estado FROM movements WHERE type_status_id IS NULL;
    IF sin_estado <> 0 THEN
        RAISE EXCEPTION 'V36: % movimientos quedaron sin estado del tipo', sin_estado;
    END IF;
END $$;
