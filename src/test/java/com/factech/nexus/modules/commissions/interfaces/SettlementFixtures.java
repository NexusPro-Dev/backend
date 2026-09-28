package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.CommissionCleanup;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La siembra común de las suites de la liquidación (`RF-CM-009` a `RF-CM-014`).
 *
 * <p>Ventas por SQL, <b>pendientes y con su pago pendiente</b>, para confirmarlas por la API de
 * `MV` y que el devengo ocurra de verdad; personas con su rol; la cadena en {@code
 * user_supervisors}. Todo lo que siembra lleva el prefijo {@code st-} o {@code ST_}, y {@link
 * #limpiar} borra solo eso, en el orden que las claves imponen.
 */
final class SettlementFixtures {

  static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private SettlementFixtures() {}

  record Linea(UUID producto, UUID vendedor, int cantidad, String precio) {}

  static Linea linea(UUID producto, UUID vendedor, int cantidad, String precio) {
    return new Linea(producto, vendedor, cantidad, precio);
  }

  static UUID persona(JdbcTemplate jdbc, String usuario, String rol) {
    return CommissionFixtures.sembrarPersonaConRol(jdbc, "st-" + usuario, rol);
  }

  static void superior(JdbcTemplate jdbc, UUID persona, UUID jefe) {
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)"
            + " VALUES (?, ?, ?, '2026-01-01T00:00:00Z')",
        UUID.randomUUID(),
        persona,
        jefe);
  }

  static UUID producto(JdbcTemplate jdbc, String codigo, String precio) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, price, currency_id,"
            + " status) VALUES ('TIENDA', 'MANUAL', ?, ?, 'BOT', ?, CAST(? AS numeric),"
            + " CAST(? AS uuid), 'ACTIVO')",
        id,
        "ST_" + codigo,
        "Producto " + codigo,
        precio,
        USD);
    return id;
  }

  /** Una venta PENDIENTE de {@code cliente}, con su pago pendiente. */
  static UUID venta(JdbcTemplate jdbc, UUID cliente, OffsetDateTime vendidaEl, Linea... lineas) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id, code,
                               status, total_amount, discount_amount, payable_amount, occurred_at)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                ?, CAST(? AS uuid), ?, 'PENDIENTE', 100.00, 0, 100.00, CAST(? AS timestamptz))
        """,
        id,
        VENTA,
        VENTA,
        cliente,
        USD,
        "VTA-ST" + id.toString().substring(0, 6).toUpperCase(),
        vendidaEl.toString());
    PaymentFixtures.pagoDe(jdbc, id, TARJETA);
    for (Linea l : lineas) {
      jdbc.update(
          """
          INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                        quantity, unit_price, line_amount, implementation)
          SELECT ?, ?, p.id, ?, p.name, ?, CAST(? AS numeric), ? * CAST(? AS numeric),
                 p.implementation
            FROM products p WHERE p.id = ?
          """,
          UUID.randomUUID(),
          id,
          l.vendedor(),
          l.cantidad(),
          l.precio(),
          l.cantidad(),
          l.precio(),
          l.producto());
    }
    return id;
  }

  static UUID lineaDe(JdbcTemplate jdbc, UUID venta) {
    return jdbc.queryForObject(
        "SELECT id FROM movement_details WHERE movement_id = ?", UUID.class, venta);
  }

  static void limpiar(JdbcTemplate jdbc) {
    CommissionCleanup.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM movements WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'st-%') OR code LIKE 'VTA-ST%'");
    jdbc.update(
        "DELETE FROM commission_rates WHERE product_id IN (SELECT id FROM products WHERE code"
            + " LIKE 'ST\\_%')");
    jdbc.update(
        "DELETE FROM user_commission_rates WHERE product_id IN (SELECT id FROM products WHERE"
            + " code LIKE 'ST\\_%')");
    jdbc.update("DELETE FROM products WHERE code LIKE 'ST\\_%'");
    for (String tabla :
        new String[] {"user_supervisors", "user_products", "user_roles", "accounts"}) {
      jdbc.update(
          "DELETE FROM "
              + tabla
              + " WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'st-%')");
    }
    jdbc.update("DELETE FROM users WHERE username LIKE 'st-%'");
  }
}
