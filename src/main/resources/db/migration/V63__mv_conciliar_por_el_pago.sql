-- =============================================================================
-- V63 — Se concilia el pago, no el movimiento (RF-MV-044, RF-MV-045).
--
-- requirements/mv.md v0.67.0 §4.8 · specs/mv/044-confirmar-pago/plan.md §2.
--
-- Confirmar y rechazar entran por POST /movements/payments/{paymentId}/…, para
-- la venta y la compra de puntos. Un permiso por operación (RN-SEG-014):
--
--   - NACE  movements:confirm-payment.
--   - SE CONSERVA movements:reject-payment —mismo id y portadores—, que pasa a
--     cubrir los dos tipos; cambia su nombre y su descripción.
--   - SE RETIRAN movements:confirm, movements:confirm-points-purchase y
--     movements:reject-points-purchase, que se quedan sin ruta.
--
-- EL REPARTO ES POR POSESIÓN, NO POR NOMBRE DE ROL: quien portaba alguno de los
-- retirados recibe el que lo sustituye antes de que aquel se borre, de modo que
-- un rol personalizado no pierde la facultad. Y la contención (RN-SEG-003) se
-- conserva sola: si un hijo portaba el viejo, su padre también.
--
-- Este número era de RF-MV-043 (consultar los pagos), que no está construido y
-- pasa a V64: Flyway no aplica migraciones fuera de orden.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0ef9c-6800-701f-9c4f-5e7ad7000058', 'movements:confirm-payment', 'movements',
 'confirm-payment',
 'Confirmar un pago pendiente',
 'Dar por entrado el pago pendiente de una venta o de una compra de puntos, con cualquier método, por POST /movements/payments/{paymentId}/confirmation (RF-MV-044). El movimiento se confirma con él (RN-MV-061). Rechazar es movements:reject-payment.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, CAST('01a0ef9c-6800-701f-9c4f-5e7ad7000058' AS uuid)
  FROM role_permissions rp
  JOIN permissions p ON p.id = rp.permission_id
 WHERE p.code IN ('movements:confirm', 'movements:confirm-points-purchase')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, CAST('01a0f6c0-8800-7002-9c4f-5e7ad700000c' AS uuid)
  FROM role_permissions rp
  JOIN permissions p ON p.id = rp.permission_id
 WHERE p.code = 'movements:reject-points-purchase'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

UPDATE permissions
   SET name = 'Rechazar un pago pendiente',
       description = 'Dar por no entrado el pago pendiente de una venta o de una compra de puntos, con motivo, por POST /movements/payments/{paymentId}/rejection (RF-MV-045). La venta sigue pendiente; la compra de puntos queda rechazada. Confirmar es movements:confirm-payment.'
 WHERE code = 'movements:reject-payment';

-- EN ESTE ORDEN: fk_role_permissions_permissions es ON DELETE RESTRICT (V38 §5).
DELETE FROM role_permissions
 WHERE permission_id IN (SELECT id FROM permissions
                          WHERE code IN ('movements:confirm', 'movements:confirm-points-purchase',
                                         'movements:reject-points-purchase'));

DELETE FROM permissions
 WHERE code IN ('movements:confirm', 'movements:confirm-points-purchase',
                'movements:reject-points-purchase');


DO $$
DECLARE
    sin_padre integer;
BEGIN
    IF EXISTS (SELECT 1 FROM permissions
                WHERE code IN ('movements:confirm', 'movements:confirm-points-purchase',
                               'movements:reject-points-purchase')) THEN
        RAISE EXCEPTION 'V63: quedó alguno de los permisos retirados';
    END IF;

    IF (SELECT count(*) FROM role_permissions rp
          JOIN permissions p ON p.id = rp.permission_id
         WHERE p.code IN ('movements:confirm-payment', 'movements:reject-payment')
           AND rp.role_id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                              '01a02a33-4c00-7002-9c4f-5e7ad1000002')) <> 4 THEN
        RAISE EXCEPTION 'V63: SUPERADMIN o ADMIN no portan los dos permisos de conciliar';
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
        RAISE EXCEPTION 'V63: % asociaciones rompen la contencion de RN-SEG-003', sin_padre;
    END IF;
END $$;
