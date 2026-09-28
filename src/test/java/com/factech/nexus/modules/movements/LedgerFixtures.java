package com.factech.nexus.modules.movements;

import com.factech.nexus.modules.movements.application.WithdrawalRequests;
import com.factech.nexus.modules.movements.domain.service.CreditService;
import com.factech.nexus.testing.CommissionCleanup;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Saldos para las suites de la etapa 6 (`RF-MV-019` a `RF-MV-024`).
 *
 * <p><b>Se llena la billetera por el camino de verdad</b> —un bono, `RF-MV-023`— y no con un {@code
 * UPDATE} a la cuenta: el saldo tiene que ser la copia de sus asientos, y un saldo sembrado a mano
 * no la tendría.
 */
public final class LedgerFixtures {

  public static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private LedgerFixtures() {}

  public static void llenarBilletera(CreditService abonos, UUID persona, String importe) {
    abonos.grantBonus(
        new WithdrawalRequests.Bonus(
            persona, UUID.fromString(USD), new BigDecimal(importe), "Saldo de prueba"),
        "semilla-" + UUID.randomUUID());
  }

  public static BigDecimal saldo(JdbcTemplate jdbc, UUID persona, String cuenta) {
    return jdbc
        .query(
            "SELECT balance FROM accounts WHERE user_id = ? AND kind = ?"
                + " AND currency_id = CAST(? AS uuid)",
            (fila, n) -> fila.getBigDecimal(1),
            persona,
            cuenta,
            USD)
        .stream()
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  /** Limpia lo de la etapa 6: los movimientos arrastran pagos y asientos; luego las cuentas. */
  public static void limpiar(JdbcTemplate jdbc) {
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM accounts");
  }
}
