package com.factech.nexus.modules.movements;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Siembra y limpieza de la etapa 3 (`RF-MV-025` a `RF-MV-031`).
 *
 * <p><b>Las tasas se siembran con SQL</b> salvo en la suite que prueba fijarlas: lo que las demás
 * prueban es qué se hace con una tasa, no cómo llega.
 */
public final class PointsFixtures {

  /** Tarjeta de crédito: {@code PUBLICO} y activa (V9). */
  public static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";

  public static final String PSE = "01a061ba-3400-7003-9c4f-5e7ad7000022";
  public static final String POINTS = "01a061ba-3400-7003-9c4f-5e7ad7000023";
  public static final String GRATIS = "01a08646-7a00-7001-9c4f-5e7adb000001";

  private PointsFixtures() {}

  /**
   * Una tasa que rige desde hace un minuto, fijada por {@code quien}.
   *
   * <p><b>Cada llamada es su propia transacción</b>, de modo que {@code now()} crece de una a otra:
   * la última tasa sembrada es la vigente, que es lo que las suites suponen.
   */
  public static UUID tasa(JdbcTemplate jdbc, String moneda, String valor, UUID quien) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO points_rates (id, currency_id, points_per_unit, valid_from, created_by)"
            + " VALUES (?, CAST(? AS uuid), CAST(? AS numeric), now() - interval '1 minute', ?)",
        id,
        moneda,
        valor,
        quien);
    return id;
  }

  /** Una moneda de prueba, de código {@code ZZ?} y dos decimales. */
  public static UUID moneda(JdbcTemplate jdbc, String codigo, boolean activa) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, decimal_places, is_default, is_active)"
            + " VALUES (?, ?, ?, 2, false, ?)",
        id,
        codigo,
        "Moneda " + codigo,
        activa);
    return id;
  }

  /**
   * Lo de la etapa 3, <b>después</b> de los movimientos (que referencian tasas) y de las cuentas
   * (que referencian monedas): las tasas y las monedas de prueba.
   */
  public static void limpiar(JdbcTemplate jdbc) {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM points_rates");
    jdbc.update("DELETE FROM currencies WHERE code LIKE 'ZZ%'");
  }
}
