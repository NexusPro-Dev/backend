package com.factech.nexus.modules.commissions.interfaces;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La siembra común de las suites afftrack (`RF-CM-015` a `RF-CM-021`).
 *
 * <p><b>Un producto FTD es un {@code UPGRADE_MEMBRESIA} de {@code BECA} a {@code BECA}</b>
 * (`RN-CM-036`), y la membresía se busca por código —como hace `PM`—: su identificador literal lo
 * repone {@code IntegrationTestBase.reponerElSuelo}, que la suite llama antes. Todo lo que se
 * siembra aquí lleva el prefijo {@code AF_}.
 */
final class AfftrackFixtures {

  private AfftrackFixtures() {}

  /**
   * El producto FTD: la membresía gratuita a la gratuita, de precio cero.
   *
   * <p><b>Inactivo</b>: {@code uq_products_upgrade_target} admite un solo upgrade activo por pareja
   * de membresías, y las suites siembran varios {@code BECA → BECA}. Ser FTD no depende del estado
   * (`RN-CM-036`), y las ventas de prueba se escriben por SQL.
   */
  static UUID productoFtd(JdbcTemplate jdbc, String codigo, boolean retirado) {
    return upgrade(jdbc, codigo, suelo(jdbc), retirado);
  }

  /** Un upgrade que NO es FTD: del suelo a otra membresía. */
  static UUID upgradeNoFtd(JdbcTemplate jdbc, String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, source_membership_id,"
            + " target_membership_id, price, currency_id, status)"
            + " VALUES ('TIENDA', 'MANUAL', ?, ?, 'UPGRADE_MEMBRESIA', ?, ?, ?, 50.00,"
            + " (SELECT id FROM currencies LIMIT 1), 'INACTIVO')",
        id,
        "AF_" + codigo,
        "Producto " + codigo,
        suelo(jdbc),
        otraMembresia(jdbc));
    return id;
  }

  private static UUID upgrade(JdbcTemplate jdbc, String codigo, UUID destino, boolean retirado) {
    UUID id = UUID.randomUUID();
    UUID origen = suelo(jdbc);
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, source_membership_id,"
            + " target_membership_id, price, currency_id, status, deleted_at)"
            + " VALUES ('HOTLINK', 'MANUAL', ?, ?, 'UPGRADE_MEMBRESIA', ?, ?, ?, 0.00,"
            + " (SELECT id FROM currencies LIMIT 1), 'INACTIVO', CASE WHEN ? THEN now() END)",
        id,
        "AF_" + codigo,
        "Producto " + codigo,
        origen,
        destino,
        retirado);
    return id;
  }

  static UUID suelo(JdbcTemplate jdbc) {
    return jdbc.queryForObject("SELECT id FROM memberships WHERE code = 'BECA'", UUID.class);
  }

  /**
   * Una membresía que no es el suelo. Si otra suite dejó la cadena en solo {@code BECA}, se siembra
   * {@code AF_TOPE} por encima de ella.
   */
  private static UUID otraMembresia(JdbcTemplate jdbc) {
    var otras =
        jdbc.queryForList(
            "SELECT id FROM memberships WHERE code <> 'BECA' ORDER BY level LIMIT 1", UUID.class);
    if (!otras.isEmpty()) {
      return otras.get(0);
    }
    // Colgada del suelo y con el nivel siguiente: el padre nulo y el nivel 1 ya
    // los ocupa BECA, y las dos unicidades lo rechazarían al confirmar.
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, description, parent_membership_id, level, color)"
            + " VALUES (?, 'AF_TOPE', 'Tope', 'De prueba.', ?,"
            + " (SELECT max(level) + 1 FROM memberships), '000000')",
        id,
        suelo(jdbc));
    return id;
  }

  static void limpiar(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM user_afftrack_rates");
    jdbc.update("DELETE FROM afftrack_rates");
    // Las suites de tasas por venta registran alguna sobre el upgrade que no es FTD.
    for (String tabla : new String[] {"commission_rates", "user_commission_rates"}) {
      jdbc.update(
          "DELETE FROM "
              + tabla
              + " WHERE product_id IN (SELECT id FROM products WHERE code LIKE 'AF\\_%')");
    }
    jdbc.update("DELETE FROM products WHERE code LIKE 'AF\\_%'");
    jdbc.update("DELETE FROM memberships WHERE code = 'AF_TOPE'");
  }
}
