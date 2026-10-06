package com.factech.nexus.modules.indicators.application;

import com.factech.nexus.modules.indicators.application.IndicatorAmount.IndicatorCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * La evolución de lo confirmado (`RF-IN-002` §6.2): <b>todos</b> los tramos del periodo, en orden,
 * y en cada uno <b>un importe por cada moneda del periodo</b>, en el orden de {@code currencies},
 * con cero donde no vendió en ella. Así cada moneda es una serie completa que se dibuja sin
 * rellenar nada.
 */
@Schema(name = "SalesSeries")
public record SalesSeriesResponse(
    IndicatorPeriod period,
    @Schema(description = "DAY, WEEK o MONTH.", example = "WEEK") String granularity,
    @Schema(description = "Las monedas con ventas en el periodo, por código.")
        List<IndicatorCurrency> currencies,
    List<Bucket> buckets) {

  /** Un tramo. */
  @Schema(name = "SalesSeriesBucket")
  public record Bucket(
      @Schema(
              description =
                  "Día en que empieza el tramo en el calendario; el primero y el último pueden"
                      + " estar recortados por el periodo.",
              example = "2026-09-07")
          LocalDate start,
      long sales,
      long lines,
      long units,
      List<IndicatorAmount> amounts) {}
}
