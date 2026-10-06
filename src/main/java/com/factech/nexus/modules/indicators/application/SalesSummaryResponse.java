package com.factech.nexus.modules.indicators.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * El resumen de ventas (`RF-IN-001` §6.2). Lo pendiente y lo anulado no llevan líneas ni unidades:
 * son contexto de lo vendido, no lo vendido. Desde el 06-10-2026 trae además el <b>total</b> y, en
 * cada bloque, cuántas ventas fueron <b>gratuitas</b> (`RN-IN-008`); y, si se pide un tramo, los
 * mismos bloques <b>por tramo</b> (`RN-IN-010`), sin que cambien los totales.
 */
@Schema(name = "SalesSummary")
public record SalesSummaryResponse(
    IndicatorPeriod period,
    Total total,
    Confirmed confirmed,
    Other pending,
    Other voided,
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "El tramo pedido —DAY, WEEK o MONTH—, o nulo si no se pidió.")
        String granularity,
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(
            description =
                "Los mismos bloques por tramo, todos los tramos presentes; nulo si no se pidió"
                    + " tramo. La suma de los tramos es el total.")
        List<Bucket> buckets) {

  /** Todas las ventas del periodo, sea cual sea su estado (06-10-2026). */
  @Schema(name = "SalesSummaryTotal")
  public record Total(
      @Schema(description = "Confirmadas, pendientes y anuladas.") long sales,
      @Schema(description = "Cuántas de ellas fueron gratuitas: importe a pagar cero.")
          long free) {}

  /** Lo vendido: lo confirmado. */
  @Schema(name = "SalesSummaryConfirmed")
  public record Confirmed(
      long sales,
      long lines,
      long units,
      @Schema(description = "Cuántas fueron gratuitas; siguen contando en sales.") long free,
      List<IndicatorAmount> amounts) {}

  /** Lo pendiente o lo anulado. */
  @Schema(name = "SalesSummaryOther")
  public record Other(
      long sales,
      @Schema(description = "Cuántas fueron gratuitas; siguen contando en sales.") long free,
      List<IndicatorAmount> amounts) {}

  /** Los bloques de un tramo (06-10-2026). */
  @Schema(name = "SalesSummaryBucket")
  public record Bucket(
      @Schema(
              description =
                  "Día en que empieza el tramo en el calendario; el primero y el último pueden"
                      + " estar recortados por el periodo.",
              example = "2026-09-07")
          LocalDate start,
      Total total,
      Confirmed confirmed,
      Other pending,
      Other voided) {}
}
