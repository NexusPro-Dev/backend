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

  /**
   * Borra las monedas que no son la de casa, <b>y antes sus tasas</b>: una tasa que sobreviva a su
   * moneda deja el `DELETE FROM currencies` de la siguiente suite con una violación de integridad.
   */
  static void limpiarMonedasDePrueba(JdbcTemplate jdbc) {
    jdbc.update(
        "DELETE FROM exchange_rates WHERE source_currency_id IN"
            + " (SELECT id FROM currencies WHERE is_default = false)"
            + " OR target_currency_id IN (SELECT id FROM currencies WHERE is_default = false)");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
  }

  /**
   * Deja el catálogo entero limpio —paquetes, productos, membresías— y levanta la cadena de tres
   * membresías que las pruebas de paquetes necesitan para hablar de ORÍGENES: {@code ORO} (1),
   * {@code PLATINO} (2) y {@code BECA} (3).
   */
  static Membresias limpiarCatalogoYSembrarMembresias(JdbcTemplate jdbc) {
    limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM product_comments");
    jdbc.update("DELETE FROM product_images");
    jdbc.update("DELETE FROM products");
    // Antes que las membresías: `user_products` las referencia (`V57`).
    jdbc.update("DELETE FROM user_products");
    jdbc.update("DELETE FROM memberships");
    UUID oro = membresia(jdbc, "ORO", 1, null);
    UUID platino = membresia(jdbc, "PLATINO", 2, oro);
    UUID beca = membresia(jdbc, "BECA", 3, platino);
    return new Membresias(oro, platino, beca);
  }

  record Membresias(UUID oro, UUID platino, UUID beca) {}

  static UUID membresia(JdbcTemplate jdbc, String codigo, int nivel, UUID superior) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (CAST(? AS uuid), ?, ?, CAST(? AS uuid), ?,"
            + " upper(lpad(to_hex(? * 4919), 6, '0')))",
        id.toString(),
        codigo,
        "Membresía " + codigo,
        superior == null ? null : superior.toString(),
        nivel,
        nivel);
    return id;
  }

  /** Un upgrade activo en USD desde `origen` hacia `destino`. */
  static UUID upgrade(JdbcTemplate jdbc, String codigo, String precio, UUID origen, UUID destino) {
    return producto(jdbc, codigo, "UPGRADE_MEMBRESIA", precio, origen, destino, "ACTIVO");
  }

  /** Un paquete directo en la tabla, en el estado y con la descripción que se pidan. */
  static UUID paquete(
      JdbcTemplate jdbc, String codigo, String descripcion, String estado, String alcance) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO product_packages (id, code, name, description, currency_id, status, scope,"
            + " valid_from) VALUES (?, ?, ?, ?, CAST(? AS uuid), ?, ?, (now() AT TIME ZONE 'UTC')::date)",
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
