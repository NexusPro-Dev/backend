package com.factech.nexus.modules.products.interfaces;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lo que comparten las suites de paquetes: limpieza y siembra directa por SQL.
 *
 * <p><b>La limpieza no es higiene: es lo que impide romper a otras clases.</b> {@code
 * product_package_items} referencia {@code products} y {@code product_packages} referencia {@code
 * currencies}; decenas de suites empiezan con {@code DELETE FROM products} o borrando las monedas
 * que no son la de casa, y una fila de paquete que sobreviva a su clase las tumba con una violación
 * de integridad que no dice de dónde vino.
 */
final class PackageTestSupport {

  /** La moneda sembrada por `V15`, estable en todos los entornos. */
  static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private PackageTestSupport() {}

  static void limpiarPaquetes(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM product_package_items");
    jdbc.update("DELETE FROM product_packages");
  }

  /** Un paquete directo en la tabla, en el estado y con la descripción que se pidan. */
  static UUID paquete(
      JdbcTemplate jdbc, String codigo, String descripcion, String estado, String alcance) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO product_packages (id, code, name, description, currency_id, status, scope)"
            + " VALUES (?, ?, ?, ?, CAST(? AS uuid), ?, ?)",
        id,
        codigo,
        "Paquete " + codigo,
        descripcion,
        USD,
        estado,
        alcance);
    return id;
  }

  /** Un bot activo en USD, directo en la tabla. */
  static UUID bot(JdbcTemplate jdbc, String codigo, String precio) {
    return producto(jdbc, codigo, "BOT", precio, null, null, "ACTIVO");
  }

  static UUID producto(
      JdbcTemplate jdbc,
      String codigo,
      String tipo,
      String precio,
      UUID origen,
      UUID destino,
      String estado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO products (id, code, type, name, description, icon, source_membership_id,
                              target_membership_id, price, currency_id, status, scope, implementation)
        VALUES (?, ?, ?, ?, 'Descripción', ?, ?, ?, ?, CAST(? AS uuid), ?, 'AMBOS', 'AUTOMATICA')
        """,
        id,
        codigo,
        tipo,
        "Producto " + codigo,
        "UPGRADE_MEMBRESIA".equals(tipo) ? "crown" : null,
        origen,
        destino,
        new BigDecimal(precio),
        USD,
        estado);
    return id;
  }

  /** Una fila de asociación directa en la tabla. */
  static void asociar(JdbcTemplate jdbc, UUID paquete, UUID producto, String forma, String valor) {
    jdbc.update(
        "INSERT INTO product_package_items (package_id, product_id, discount_type, discount_value)"
            + " VALUES (?, ?, ?, ?)",
        paquete,
        producto,
        forma,
        new BigDecimal(valor));
  }
}
