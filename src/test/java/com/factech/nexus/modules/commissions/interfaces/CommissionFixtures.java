package com.factech.nexus.modules.commissions.interfaces;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Los datos que las siete suites de `CM` necesitan sembrar.
 *
 * <p><b>Están aquí y no repetidos siete veces</b> por una razón concreta: el orden de borrado
 * depende de las claves foráneas de `V49`, y una copia que se quedara atrás no fallaría por lo que
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
          "INSERT INTO user_roles (user_id, role_id, role_type) SELECT CAST(? AS uuid), r.id, r.role_type FROM roles r WHERE r.id = CAST(? AS uuid)",
          id.toString(),
          rol);
    }
    return id;
  }

  static UUID sembrarProducto(JdbcTemplate jdbc, String codigo) {
    return sembrarProducto(jdbc, codigo, false);
  }

  /**
   * Un producto del catálogo.
   *
   * <p><b>La moneda no se elige</b>, y es deliberado: <b>ninguna consulta de este módulo la
   * mira</b>. La sentencia de resolución une las tres tablas de `CM` y no toca {@code products}, de
   * modo que un importe fijo se devuelve igual sea cual sea la moneda del producto — no porque
   * alguien lo compruebe, sino porque no hay por dónde enterarse (`RN-CM-017`).
   */
  static UUID sembrarProducto(JdbcTemplate jdbc, String codigo, boolean retirado) {
    return sembrarProducto(jdbc, codigo, retirado, "10.00");
  }

  /**
   * Un producto con un precio elegido, para las pruebas de `RN-CM-019` que convierten un valor fijo
   * contra ese precio.
   */
  static UUID sembrarProducto(JdbcTemplate jdbc, String codigo, boolean retirado, String precio) {
    UUID id = UUID.randomUUID();
    String monedaId =
        jdbc.queryForObject("SELECT CAST(id AS text) FROM currencies LIMIT 1", String.class);
    jdbc.update(
        "INSERT INTO products (id, code, type, name, price, currency_id, status, deleted_at)"
            + " VALUES (CAST(? AS uuid), ?, 'BOT', ?, CAST(? AS numeric), CAST(? AS uuid),"
            + " 'INACTIVO', CASE WHEN ? THEN now() ELSE NULL END)",
        id.toString(),
        codigo,
        "Producto " + codigo,
        precio,
        monedaId,
        retirado);
    return id;
  }

  /** Una tasa de rol <b>en porcentaje</b>, escrita directamente: quien la usa no prueba el alta. */
  static UUID sembrarTasaDeRol(JdbcTemplate jdbc, String rol, String porcentaje) {
    return sembrarTasaDeRol(jdbc, rol, "PORCENTAJE", porcentaje);
  }

  /**
   * Una tasa de rol en la forma que se pida.
   *
   * <p><b>La forma va explícita en el {@code INSERT}</b>, y tiene que ir: {@code V50} le quita el
   * valor por defecto a {@code rate_type} precisamente para que omitirla falle. Una fixture que la
   * omitiera dejaría de compilar contra el esquema — que es lo que se quiere.
   */
  static UUID sembrarTasaDeRol(JdbcTemplate jdbc, String rol, String forma, String valor) {
    UUID id = UUID.randomUUID();
    boolean esPorcentaje = "PORCENTAJE".equals(forma);
    jdbc.update(
        "INSERT INTO commission_rates (id, role_id, rate_type, percentage, fixed_amount)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), ?,"
            + " CAST(? AS numeric), CAST(? AS numeric))",
        id.toString(),
        rol,
        forma,
        esPorcentaje ? valor : null,
        esPorcentaje ? null : valor);
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
    return sembrarTasaPersonal(jdbc, persona, "PORCENTAJE", porcentaje, desde, hasta);
  }

  /** La personalizada en la forma que se pida. Ver {@link #sembrarTasaDeRol}. */
  static UUID sembrarTasaPersonal(
      JdbcTemplate jdbc, UUID persona, String forma, String valor, String desde, String hasta) {
    UUID id = UUID.randomUUID();
    boolean esPorcentaje = "PORCENTAJE".equals(forma);
    jdbc.update(
        "INSERT INTO user_commission_rates"
            + " (id, user_id, rate_type, percentage, fixed_amount, valid_from, valid_to)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), ?, CAST(? AS numeric),"
            + " CAST(? AS numeric), CAST(? AS date), CAST(? AS date))",
        id.toString(),
        persona.toString(),
        forma,
        esPorcentaje ? valor : null,
        esPorcentaje ? null : valor,
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
