package com.factech.nexus.modules.indicators.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * El resumen de líneas de venta (`RF-IN-006` §6.2): sobre <b>todas</b> las líneas de venta, por
 * estado —ventas, líneas, <b>unidades</b> e importe por moneda—, el total, y aparte <b>lo sin
 * vendedor</b> de las ventas no anuladas. Sin alcance (`RN-IN-011`).
 */
@Schema(name = "SaleLinesSummary")
public record SaleLinesSummaryResponse(
    IndicatorPeriod period,
    Total total,
    Block confirmed,
    Block pending,
    Block voided,
    @Schema(
            description =
                "Las líneas sin vendedor de las ventas no anuladas —lo que falta por atribuir—:"
                    + " ventas con alguna, esas líneas, sus unidades y su importe.")
        Block unassigned,
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "El tramo pedido —DAY, WEEK o MONTH—, o nulo si no se pidió.")
        String granularity,
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(
            description =
                "Los mismos bloques por tramo, todos los tramos presentes; nulo si no se pidió"
                    + " tramo. La suma de los tramos es el total.")
        List<Bucket> buckets) {

  /** Todas las ventas, sea cual sea su estado. */
  @Schema(name = "SaleLinesTotal")
  public record Total(
      long sales,
      long lines,
      @Schema(description = "Productos vendidos: la suma de las cantidades.") long units) {}

  /** Las cifras de un estado, o de lo sin vendedor. */
  @Schema(name = "SaleLinesBlock")
  public record Block(
      long sales,
      long lines,
      @Schema(description = "Productos vendidos: la suma de las cantidades.") long units,
      List<IndicatorAmount> amounts) {}

  /** Los bloques de un tramo. */
  @Schema(name = "SaleLinesBucket")
  public record Bucket(
      @Schema(description = "Día en que empieza el tramo en el calendario.", example = "2026-09-07")
          LocalDate start,
      Total total,
      Block confirmed,
      Block pending,
      Block voided,
      Block unassigned) {}
}
