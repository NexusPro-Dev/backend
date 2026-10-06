package com.factech.nexus.modules.indicators.application;

import com.factech.nexus.modules.indicators.application.IndicatorAmount.IndicatorCurrency;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * El resumen de puntos (`RF-IN-005` §6.2, enmendado el 06-10-2026): <b>la suma de las filas de la
 * lista de los movimientos de puntos</b> de administración (`RF-MV-056`), con sus nombres —compras,
 * gastos, ajustes—, por moneda, y el saldo de hoy. Todo en positivo; el sentido lo da el nombre. Si
 * se pide un tramo, además lo mismo <b>por tramo</b>, sin saldo (`RN-IN-010`).
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
                "Lo mismo por tramo y moneda, todos los tramos presentes; nulo si no se pidió"
                    + " tramo. El saldo no se parte: va solo en currencies.")
        List<Bucket> buckets) {

  /** Las cifras de una moneda. */
  @Schema(name = "PointsSummaryCurrency")
  public record Currency(
      IndicatorCurrency currency,
      @Schema(description = "Las compras de puntos (COMPRA_PUNTOS), por estado.")
          Purchases purchases,
      @Schema(description = "Las ventas pagadas con puntos (GASTO_PUNTOS).") Flow spent,
      @Schema(description = "Los ajustes a mano (AJUSTE_PUNTOS).") Adjustments adjustments,
      @Schema(
              description = "El saldo de HOY, sea cual sea el periodo o el filtro.",
              example = "1150.00")
          BigDecimal balance) {}

  /** Las compras de puntos de una moneda, por estado. */
  @Schema(name = "PointsPurchases")
  public record Purchases(Purchase confirmed, Purchase pending, Purchase rejected) {}

  /** Las compras de un estado. */
  @Schema(name = "PointsPurchase")
  public record Purchase(
      @Schema(description = "Cuántas.") long count,
      @Schema(
              description =
                  "Puntos, en positivo; en una pendiente o rechazada, los que daría o habría dado.",
              example = "1500.00")
          BigDecimal points,
      @Schema(description = "Lo pagado por ellas, en la moneda.", example = "15.00")
          BigDecimal amount) {}

  /** Los ajustes de una moneda, por sentido. */
  @Schema(name = "PointsAdjustments")
  public record Adjustments(Flow added, Flow removed) {}

  /** Una clase de movimiento de puntos. */
  @Schema(name = "PointsFlow")
  public record Flow(
      @Schema(description = "Cuántos movimientos.") long count,
      @Schema(description = "Puntos, en positivo.", example = "1500.00") BigDecimal points) {}

  /** Un tramo: sus cifras por moneda. */
  @Schema(name = "PointsSummaryBucket")
  public record Bucket(
      @Schema(description = "Día en que empieza el tramo en el calendario.", example = "2026-09-07")
          LocalDate start,
      List<BucketCurrency> currencies) {}

  /** Las cifras de una moneda en un tramo, sin saldo. */
  @Schema(name = "PointsSummaryBucketCurrency")
  public record BucketCurrency(
      IndicatorCurrency currency, Purchases purchases, Flow spent, Adjustments adjustments) {}
}
