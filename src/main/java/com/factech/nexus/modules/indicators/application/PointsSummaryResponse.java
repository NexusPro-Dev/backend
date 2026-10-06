package com.factech.nexus.modules.indicators.application;

import com.factech.nexus.modules.indicators.application.IndicatorAmount.IndicatorCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * El resumen de puntos (`RF-IN-005` §6.2): por moneda, lo comprado, lo redimido y los ajustes del
 * periodo, y el saldo de hoy. Todo en positivo; el sentido lo da el nombre de la cifra.
 */
@Schema(name = "PointsSummary")
public record PointsSummaryResponse(IndicatorPeriod period, List<Currency> currencies) {

  /** Las cifras de una moneda. */
  @Schema(name = "PointsSummaryCurrency")
  public record Currency(
      IndicatorCurrency currency,
      @Schema(description = "Compras de puntos cobradas en el periodo.") Flow purchased,
      @Schema(description = "Puntos gastados al pagar ventas en el periodo.") Flow redeemed,
      @Schema(description = "Ajustes a mano que sumaron, en el periodo.") Flow added,
      @Schema(description = "Ajustes a mano que restaron, en el periodo.") Flow removed,
      @Schema(description = "El saldo de HOY, sea cual sea el periodo.", example = "1150.00")
          BigDecimal balance) {}

  /** Una clase de movimiento de puntos. */
  @Schema(name = "PointsFlow")
  public record Flow(
      @Schema(description = "Puntos, en positivo.", example = "1500.00") BigDecimal points,
      @Schema(description = "Cuántos movimientos los produjeron.") long count) {}
}
