-- =============================================================================
-- V58 — La etapa 3 de MV: comprar puntos y pagar con ellos (RF-MV-025 a
-- RF-MV-031; requirements/mv.md v0.56.0 §4.4, §7.6 y §7.10; 30-09-2026).
--
-- `V57` la tomó la semilla de países el mismo día. Lo que trae, en este orden:
--
--   1. `points_rates`: a cuánto se venden los puntos en cada moneda, con
--      histórico (RN-MV-050). Sin `valid_to`: la sustituye la fila siguiente.
--   2. `movements.points_rate_id` y `points_amount`: la tasa y los puntos que
--      una compra congela (RN-MV-051), juntas o ninguna.
--   3. `ck_accounts_kind` y `ck_movement_entries_event` AMPLIADOS: la cuenta
--      de empresa `PUNTOS_EMITIDOS` y el evento `PAGO` (RN-MV-052). Se amplían,
--      no se estrechan: ninguna fila existente los viola.
--   4. El tipo `COMPRA_PUNTOS` con su estado del tipo `REGISTRADO`, como los
--      tres de V49.
--   5. Los seis permisos de la etapa, repartidos como V49.
--   6. Los pagos `POINTS` que seguían PENDIENTES se RECHAZAN (RF-MV-030,
--      decisión del responsable del proyecto del 30-09-2026): pagar con puntos
--      no descontaba nada, y confirmarlos entregaría gratis. La venta NO se
--      toca: sigue PENDIENTE y quien compró la vuelve a pagar (RF-MV-018).
--
-- NO SE SIEMBRA NINGUNA TASA: la primera la fija administración, y hasta
-- entonces ninguna moneda vende puntos ni acepta pagos con ellos.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 del 30-09-2026 a
-- medianoche UTC —`01a0ef9c6800`—, continuando las series de V49 y V56: tipos
-- 000014 → 000015, estados del tipo 000035 → 000036, permisos 000041 → 000042
-- a 000047.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Las tasas.
-- ---------------------------------------------------------------------------

CREATE TABLE points_rates (
    id              uuid          PRIMARY KEY,
    currency_id     uuid          NOT NULL,
    points_per_unit numeric(12,4) NOT NULL,
    valid_from      timestamptz   NOT NULL,
    created_by      uuid          NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_points_rates_valor CHECK (points_per_unit > 0),
    CONSTRAINT uq_points_rates_vigencia UNIQUE (currency_id, valid_from),
    CONSTRAINT fk_points_rates_currency
        FOREIGN KEY (currency_id) REFERENCES currencies (id) ON DELETE RESTRICT,
    -- CASCADE y no RESTRICT: una FK sin ON DELETE rompe las suites que limpian
    -- personas, lejos de aquí. En producción nadie borra personas.
    CONSTRAINT fk_points_rates_created_by
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE CASCADE
);

-- La vigente es un LIMIT 1 sobre este índice (RF-MV-025 · plan.md §2).
CREATE INDEX ix_points_rates_vigente ON points_rates (currency_id, valid_from DESC);

COMMENT ON TABLE points_rates IS
    'RN-MV-050: cuántos puntos da una unidad de cada moneda. Un histórico: fijar inserta una fila y la anterior no se toca. Rige la de valid_from más reciente que no sea futura.';

-- ---------------------------------------------------------------------------
-- 2. La tasa y los puntos que una compra congela.
-- ---------------------------------------------------------------------------

ALTER TABLE movements
    ADD COLUMN points_rate_id uuid          NULL,
    ADD COLUMN points_amount  numeric(14,2) NULL,
    ADD CONSTRAINT fk_movements_points_rate
        FOREIGN KEY (points_rate_id) REFERENCES points_rates (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_movements_points
        CHECK ((points_rate_id IS NULL) = (points_amount IS NULL)
               AND (points_amount IS NULL OR points_amount > 0));

COMMENT ON COLUMN movements.points_amount IS
    'RN-MV-051: los puntos que da una COMPRA_PUNTOS, a su tasa, redondeados hacia abajo. Confirmar abona exactamente estos.';

-- ---------------------------------------------------------------------------
-- 3. La cuenta de la empresa y el evento nuevos.
-- ---------------------------------------------------------------------------

ALTER TABLE accounts DROP CONSTRAINT ck_accounts_kind;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_kind
    CHECK ((kind IN ('BILLETERA', 'RETENIDO', 'PUNTOS') AND user_id IS NOT NULL)
        OR (kind IN ('COMISIONES', 'BONOS', 'RETIROS', 'PUNTOS_EMITIDOS') AND user_id IS NULL));

ALTER TABLE movement_entries DROP CONSTRAINT ck_movement_entries_event;
ALTER TABLE movement_entries ADD CONSTRAINT ck_movement_entries_event
    CHECK (event IN ('SOLICITUD', 'APROBACION', 'RECHAZO', 'ABONO', 'PAGO'));

-- ---------------------------------------------------------------------------
-- 4. El tipo, con su estado del tipo.
-- ---------------------------------------------------------------------------

INSERT INTO movement_types (id, code, name, prefix) VALUES
('01a0ef9c-6800-7001-9c4f-5e7ad7000015', 'COMPRA_PUNTOS', 'Compra de puntos', 'PTS');

INSERT INTO movement_type_statuses (id, movement_type_id, code, name) VALUES
('01a0ef9c-6800-7002-9c4f-5e7ad7000036', '01a0ef9c-6800-7001-9c4f-5e7ad7000015', 'REGISTRADO', 'Registrado');

-- ---------------------------------------------------------------------------
-- 5. Los seis permisos. Tres de administración, a SUPERADMIN y ADMIN
--    explícito; tres sobre uno mismo, por tipo de rol (RN-SEG-015), como V49.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0ef9c-6800-7003-9c4f-5e7ad7000042', 'movements:set-points-rate', 'movements', 'set-points-rate',
 'Fijar la tasa de puntos',
 'Fijar cuántos puntos da una unidad de una moneda, por POST /movements/points-rates (RF-MV-025). Rige desde que se fija; las anteriores se conservan.'),
('01a0ef9c-6800-7004-9c4f-5e7ad7000043', 'movements:read-points-rates', 'movements', 'read-points-rates',
 'Consultar las tasas de puntos',
 'Ver la tasa de puntos vigente de cada moneda, por GET /movements/points-rates (RF-MV-026).'),
('01a0ef9c-6800-7005-9c4f-5e7ad7000044', 'movements:buy-points', 'movements', 'buy-points',
 'Comprar puntos',
 'Comprar puntos para uno mismo, por POST /movements/mine/points-purchases (RF-MV-027). La compra queda pendiente hasta que se confirme su pago.'),
('01a0ef9c-6800-7006-9c4f-5e7ad7000045', 'movements:confirm-points-purchase', 'movements', 'confirm-points-purchase',
 'Confirmar una compra de puntos',
 'Declarar que el dinero de una compra de puntos entró y abonar los puntos, por POST /movements/{id}/points-purchase-confirmation (RF-MV-028).'),
('01a0ef9c-6800-7007-9c4f-5e7ad7000046', 'movements:reject-points-purchase', 'movements', 'reject-points-purchase',
 'Rechazar una compra de puntos',
 'Rechazar el pago de una compra de puntos pendiente, con motivo, por POST /movements/{id}/points-purchase-rejection (RF-MV-029).'),
('01a0ef9c-6800-7008-9c4f-5e7ad7000047', 'movements:list-own-points-purchases', 'movements', 'list-own-points-purchases',
 'Consultar mis compras de puntos',
 'Ver las compras de puntos propias y en qué quedaron, por GET /movements/mine/points-purchases (RF-MV-031).');

INSERT INTO role_permissions (role_id, permission_id) VALUES
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a0ef9c-6800-7003-9c4f-5e7ad7000042'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a0ef9c-6800-7003-9c4f-5e7ad7000042'),
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a0ef9c-6800-7006-9c4f-5e7ad7000045'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a0ef9c-6800-7006-9c4f-5e7ad7000045'),
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a0ef9c-6800-7007-9c4f-5e7ad7000046'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a0ef9c-6800-7007-9c4f-5e7ad7000046')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE p.id IN ('01a0ef9c-6800-7004-9c4f-5e7ad7000043',
                '01a0ef9c-6800-7005-9c4f-5e7ad7000044',
                '01a0ef9c-6800-7008-9c4f-5e7ad7000047')
   AND r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. Los pagos POINTS que seguían pendientes (RF-MV-030 · plan.md §2).
-- ---------------------------------------------------------------------------

UPDATE payments p
   SET status = 'RECHAZADO', rejected_at = now(),
       rejection_reason = 'Pagar con puntos no descontaba saldo antes del 30-09-2026: '
                       || 'vuelve a pagar la compra con otro método o con tus puntos.'
  FROM payment_methods pm
 WHERE pm.id = p.payment_method_id AND pm.code = 'POINTS' AND p.status = 'PENDIENTE';

-- ---------------------------------------------------------------------------
-- Guardas.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    faltan      integer;
    pendientes  integer;
    sin_padre   integer;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM movement_type_statuses s
                     JOIN movement_types t ON t.id = s.movement_type_id
                    WHERE t.code = 'COMPRA_PUNTOS' AND s.code = 'REGISTRADO') THEN
        RAISE EXCEPTION 'V58: falta el tipo COMPRA_PUNTOS con su estado REGISTRADO';
    END IF;

    SELECT count(*) INTO faltan
      FROM unnest(ARRAY['movements:set-points-rate', 'movements:read-points-rates',
                        'movements:buy-points', 'movements:confirm-points-purchase',
                        'movements:reject-points-purchase',
                        'movements:list-own-points-purchases']) AS esperado(code)
     WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = esperado.code);
    IF faltan <> 0 THEN
        RAISE EXCEPTION 'V58: faltan % de los seis permisos de la etapa 3', faltan;
    END IF;

    SELECT count(*) INTO pendientes
      FROM payments p
      JOIN payment_methods pm ON pm.id = p.payment_method_id
     WHERE pm.code = 'POINTS' AND p.status = 'PENDIENTE';
    IF pendientes <> 0 THEN
        RAISE EXCEPTION 'V58: quedan % pagos POINTS pendientes', pendientes;
    END IF;

    -- Contención (RN-SEG-003).
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id
                          AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V58: % asociaciones rompen la contencion de RN-SEG-003', sin_padre;
    END IF;
END $$;
