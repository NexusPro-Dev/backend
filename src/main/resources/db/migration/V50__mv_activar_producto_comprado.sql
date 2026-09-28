-- ---------------------------------------------------------------------------
-- V50 — Activar un producto comprado de implementacion manual (RF-MV-010,
-- RN-MV-048; requirements/mv.md v0.48.0, 28-09-2026).
--
-- Solo el permiso. La linea ya tiene su estado de entrega desde V16 y la
-- posesion su tabla desde V38: activar es pasar una linea manual de PENDIENTE
-- a ENTREGADA, que es lo que confirmar ya hacia con una automatica.
--
-- Por tipo de rol, como movements:read-own-products (RN-SEG-015): es sobre lo
-- propio, y el alcance lo pone la consulta —la linea de otra persona no existe
-- para quien pregunta—, no el permiso. movements:implement, declarado el
-- 07-09-2026 para que un funcionario autorizara la entrega, no se siembra:
-- desaparece.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0f6c0-8800-7010-9c4f-5e7ad7000040', 'movements:activate-own-product', 'movements', 'activate-own-product',
 'Activar un producto comprado',
 'Activar un producto propio de implementacion manual de una venta confirmada, por POST /movements/mine/products/{lineId}/activation (RF-MV-010). La vigencia corre desde la activacion.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a0f6c0-8800-7010-9c4f-5e7ad7000040'
  FROM roles r
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

COMMENT ON COLUMN movement_details.delivery_status IS
    'RN-MV-030: PENDIENTE, ENTREGADA o RETENIDA. Una linea MANUAL pasa a ENTREGADA cuando quien la compro la activa (RF-MV-010, RN-MV-048).';
