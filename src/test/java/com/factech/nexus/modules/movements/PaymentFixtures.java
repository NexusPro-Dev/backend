package com.factech.nexus.modules.movements;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El pago de una venta sembrada a mano en una prueba (`RF-MV-018`, `V48`).
 *
 * <p>Desde el 26-09-2026 el método de pago no está en {@code movements}: es de cada intento, en
 * {@code payments}. Las pruebas que insertan ventas directamente contra la base le dan su pago con
 * esto, con el estado que le corresponde al de la venta —el mismo traslado que hizo {@code V48}—.
 */
public final class PaymentFixtures {

  private PaymentFixtures() {}

  /** Un pago, con el estado que corresponde al de la venta ya insertada. */
  public static void pagoDe(JdbcTemplate jdbc, Object movimiento, Object metodo) {
    jdbc.update(
        """
        INSERT INTO payments (id, movement_id, payment_method_id, status, amount,
                              idempotency_key, occurred_at, confirmed_at, rejected_at,
                              rejection_reason)
        SELECT gen_random_uuid(), m.id, CAST(? AS uuid),
               CASE m.status WHEN 'CONFIRMADA' THEN 'CONFIRMADO'
                             WHEN 'PENDIENTE'  THEN 'PENDIENTE'
                             ELSE 'RECHAZADO' END,
               m.payable_amount, 'prueba-' || m.id, m.occurred_at, m.confirmed_at,
               CASE WHEN m.status IN ('ANULADA', 'RECHAZADA') THEN COALESCE(m.voided_at, now()) END,
               CASE WHEN m.status IN ('ANULADA', 'RECHAZADA') THEN 'Venta anulada' END
          FROM movements m
         WHERE m.id = CAST(? AS uuid)
        """,
        metodo.toString(),
        movimiento.toString());
  }

  /** Cambia el método del último pago de la venta: lo que antes era cambiar el de la cabecera. */
  public static void cambiarMetodo(JdbcTemplate jdbc, Object movimiento, Object metodo) {
    jdbc.update(
        "UPDATE payments SET payment_method_id = CAST(? AS uuid) WHERE movement_id = CAST(? AS uuid)",
        metodo.toString(),
        movimiento.toString());
  }
}
