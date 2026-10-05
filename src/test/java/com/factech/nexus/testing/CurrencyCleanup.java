package com.factech.nexus.testing;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * <b>Borrar las monedas que no son la por omisión</b>, que es lo que muchas suites hacen al empezar
 * para quedarse con USD sola.
 *
 * <p>Desde el 05-10-2026 hay una segunda moneda sembrada, <b>COP</b> (`V9`), y la conversión de
 * Colombia la referencia (`V70`, `country_conversion_rates`, `RESTRICT`). Un {@code DELETE FROM
 * currencies WHERE is_default = false} a secas falla entonces por la clave foránea. Aquí se sueltan
 * antes los cobros en moneda local y se borran las conversiones que la usan.
 */
public final class CurrencyCleanup {

  private CurrencyCleanup() {}

  public static void noPorOmision(JdbcTemplate jdbc) {
    jdbc.update(
        "UPDATE payments SET charge_currency_id = NULL, charge_amount = NULL,"
            + " conversion_rate_id = NULL, checkout_url = NULL"
            + " WHERE charge_currency_id IN (SELECT id FROM currencies WHERE is_default = false)");
    jdbc.update(
        "DELETE FROM country_conversion_rates"
            + " WHERE currency_id IN (SELECT id FROM currencies WHERE is_default = false)"
            + " OR base_currency_id IN (SELECT id FROM currencies WHERE is_default = false)");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
  }
}
