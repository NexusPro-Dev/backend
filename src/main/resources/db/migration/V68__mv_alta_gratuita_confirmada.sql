-- =============================================================================
-- V68 — La venta del alta gratuita nace confirmada (RN-MV-075, RN-SP-057).
--
-- requirements/mv.md v0.76.0 · specs/mv/001-registrar-venta/plan.md §2.7 y
-- tasks.md T-47.
--
-- Desde el 05-10-2026 la venta que anota el alta de una cuenta FTD_PENDIENTE
-- nace CONFIRMADA, con su pago GRATIS CONFIRMADO, y sus líneas esperan a que el
-- primer depósito las active. Las que ya existen nacieron PENDIENTE y nada las
-- iba a confirmar nunca: esta migración las lleva al estado que tendrían hoy.
--
-- SOLO las de cuentas que SIGUEN en FTD_PENDIENTE. Una cuenta ya ACTIVO salió
-- de la espera antes de esta regla: activar ahora su línea fecharía su FTD
-- (RN-CM-036) en el día de la migración, que no es cuándo depositó.
--
-- confirmed_at = created_at de la venta, y no now(): por la regla nueva, esa
-- venta se confirma en el mismo instante del alta. Fecharla hoy inventaría días
-- de espera y la sacaría como «confirmada hoy» en todo listado por fecha.
--
-- LAS LÍNEAS NO SE TOCAN: siguen PENDIENTE de entrega, que es lo que la regla
-- nueva les pide. Y la membresía que el alta concedió con el producto (sin
-- línea) tampoco: al activarse, la entrega cierra esa fila abierta y abre la
-- de la línea, como hace con cualquier nivel vigente.
-- =============================================================================

WITH altas AS (
    SELECT m.id AS movement_id, m.created_at
      FROM users u
      JOIN client_sellers cs
        ON cs.client_id = u.id AND cs.origin = 'REGISTRO'
      JOIN movements m
        ON m.id = cs.first_movement_id
     WHERE u.status = 'FTD_PENDIENTE'
       AND m.status = 'PENDIENTE'
       AND EXISTS (
           SELECT 1
             FROM payments p
             JOIN payment_methods pm ON pm.id = p.payment_method_id
            WHERE p.movement_id = m.id
              AND p.status = 'PENDIENTE'
              AND pm.code = 'GRATIS')
),
pagos AS (
    UPDATE payments p
       SET status = 'CONFIRMADO',
           confirmed_at = a.created_at
      FROM altas a, payment_methods pm
     WHERE p.movement_id = a.movement_id
       AND p.status = 'PENDIENTE'
       AND pm.id = p.payment_method_id
       AND pm.code = 'GRATIS'
    RETURNING p.movement_id
)
UPDATE movements m
   SET status = 'CONFIRMADA',
       confirmed_at = a.created_at
  FROM altas a
 WHERE m.id = a.movement_id;
