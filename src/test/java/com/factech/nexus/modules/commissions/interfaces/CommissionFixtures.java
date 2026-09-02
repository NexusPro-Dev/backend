package com.factech.nexus.modules.commissions.interfaces;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Los datos que las siete suites de `CM` necesitan sembrar.
 *
 * <p><b>Están aquí y no repetidos siete veces</b> por una razón concreta: el orden de borrado
 * depende de las claves foráneas de `V48`, y una copia que se quedara atrás no fallaría por lo que
 * cambió — fallaría con una violación de integridad en la suite que nadie tocó.
 */
final class CommissionFixtures {

  /** `MANAGER`, sembrado por `V7` con {@code role_type = 'VENDEDOR'}. */
  static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";

  /** `DIRECTOR`, el segundo vendedor: hace falta para probar dos roles sobre un producto. */
  static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";

  /** `AGENTE`, el tercero. */
  static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  /** `ADMIN`, que es funcionario: sirve para la mitad negativa de `RN-CM-001`. */
  static final String NO_VENDEDOR = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  private CommissionFixtures() {}

  static UUID sembrarPersonaConRol(JdbcTemplate jdbc, String usuario, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status)"
            + " VALUES (CAST(? AS uuid), ?, ?, 'Persona', 'De prueba', 'x', 'ACTIVO')",
        id.toString(),
        usuario,
        usuario + "@factech.co");
    if (rol != null) {
      jdbc.update(
          "INSERT INTO user_roles (user_id, role_id) VALUES (CAST(? AS uuid), CAST(? AS uuid))",
          id.toString(),
          rol);
    }
    return id;
  }

  static UUID sembrarProducto(JdbcTemplate jdbc, String codigo) {
    return sembrarProducto(jdbc, codigo, false);
  }

  static UUID sembrarProducto(JdbcTemplate jdbc, String codigo, boolean retirado) {
    UUID id = UUID.randomUUID();
    String moneda =
        jdbc.queryForObject("SELECT CAST(id AS text) FROM currencies LIMIT 1", String.class);
    jdbc.update(
        "INSERT INTO products (id, code, type, name, price, currency_id, status, deleted_at)"
            + " VALUES (CAST(? AS uuid), ?, 'BOT', ?, 10.00, CAST(? AS uuid), 'INACTIVO',"
            + " CASE WHEN ? THEN now() ELSE NULL END)",
        id.toString(),
        codigo,
        "Producto " + codigo,
        moneda,
        retirado);
    return id;
  }

  /** Una tasa de rol, escrita directamente: la suite que la usa no está probando el alta. */
  static UUID sembrarTasaDeRol(JdbcTemplate jdbc, String rol, String porcentaje) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO commission_rates (id, role_id, percentage)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS numeric))",
        id.toString(),
        rol,
        porcentaje);
    return id;
  }

  /** La asociación, que es lo único que pone una tasa en vigor. */
  static void asociar(JdbcTemplate jdbc, UUID tasa, UUID producto, String rol) {
    jdbc.update(
        "INSERT INTO product_commission_rates (product_id, role_id, commission_rate_id)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid))",
        producto.toString(),
        rol,
        tasa.toString());
  }

  static UUID sembrarTasaPersonal(
      JdbcTemplate jdbc, UUID persona, String porcentaje, String desde, String hasta) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO user_commission_rates (id, user_id, percentage, valid_from, valid_to)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS numeric),"
            + " CAST(? AS date), CAST(? AS date))",
        id.toString(),
        persona.toString(),
        porcentaje,
        desde,
        hasta);
    return id;
  }

  /**
   * Deja las tres tablas del módulo vacías, <b>en el orden que las claves foráneas imponen</b>: la
   * asociación apunta a la tasa y al producto, de modo que va la primera.
   */
  static void limpiar(JdbcTemplate jdbc, UUID superadmin) {
    jdbc.update("DELETE FROM product_commission_rates");
    jdbc.update("DELETE FROM user_commission_rates");
    jdbc.update("DELETE FROM commission_rates");
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> CAST(? AS uuid)", superadmin.toString());
    jdbc.update("DELETE FROM users WHERE id <> CAST(? AS uuid)", superadmin.toString());
  }
}
