package com.factech.nexus.modules.indicators.application;

import com.factech.nexus.modules.indicators.application.IndicatorAmount.IndicatorCurrency;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * El resumen de puntos (`RF-IN-005` §6.2): por moneda, lo comprado, lo redimido y los ajustes del
 * periodo, y el saldo de hoy. Todo en positivo; el sentido lo da el nombre de la cifra. Si se pide
 * un tramo, además las cuatro clases <b>por tramo</b>, sin saldo (`RN-IN-010`).
 */
@Schema(name = "PointsSummary")
public record PointsSummaryResponse(
    IndicatorPeriod period,
    List<Currency> currencies,
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "El tramo pedido —DAY, WEEK o MONTH—, o nulo si no se pidió.")
        String granularity,
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(
            description =
                "Las cuatro clases por tramo y moneda, todos los tramos presentes; nulo si no se"
                    + " pidió tramo. El saldo no se parte: va solo en currencies.")
        List<Bucket> buckets) {

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

  /** Un tramo: sus clases por moneda (06-10-2026). */
  @Schema(name = "PointsSummaryBucket")
  public record Bucket(
      @Schema(description = "Día en que empieza el tramo en el calendario.", example = "2026-09-07")
          LocalDate start,
      List<BucketCurrency> currencies) {}

  /** Las cuatro clases de una moneda en un tramo, sin saldo. */
  @Schema(name = "PointsSummaryBucketCurrency")
  public record BucketCurrency(
      IndicatorCurrency currency, Flow purchased, Flow redeemed, Flow added, Flow removed) {}
}
