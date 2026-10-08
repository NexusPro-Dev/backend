package com.factech.nexus.testing;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Limpia la liquidación de `CM` <b>antes</b> de que una suite borre {@code movements}.
 *
 * <p>Desde `V51` (`RF-CM-013`) confirmar una venta deja comisiones solas, y {@code
 * fk_commissions_detail} y {@code fk_commission_accruals_detail} son {@code RESTRICT}: una línea
 * con comisión no se borra. Sin esto, cualquier suite que confirme una venta y limpie con {@code
 * DELETE FROM movements} fallaría — y fallaría en otra suite, según el orden de ejecución. Es el
 * mismo accidente que {@code product_links} provocó el 22-09-2026, y por eso vive en un solo sitio
 * en lugar de copiado en cada suite.
 *
 * <p>El orden importa: los lotes señalan a su cierre y al movimiento que los abonó, y las
 * comisiones a su lote. <b>Desde `V54`</b> (`RF-CM-015`) entra lo afftrack: {@code afftrack_ftds}
 * es otra clave {@code RESTRICT} hacia las líneas, y una comisión afftrack señala a su liquidación,
 * que señala a su cierre.
 */
public final class CommissionCleanup {

  private CommissionCleanup() {}

  public static void limpiar(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM commission_accruals");
    jdbc.update("DELETE FROM afftrack_ftds");
    jdbc.update("DELETE FROM commissions");
    jdbc.update("DELETE FROM afftrack_settlements");
    jdbc.update("DELETE FROM commission_batches");
    jdbc.update("DELETE FROM commission_closings");
    // `V87`: las elecciones señalan a quien eligió, y las suites borran después a las personas.
    jdbc.update("DELETE FROM commission_payment_choices");
  }
}
