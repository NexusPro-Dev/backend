package com.factech.nexus.modules.indicators.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * El resumen de líneas de venta (`RF-IN-006` §6.2, enmendado el 07-10-2026): sobre <b>todas</b> las
 * líneas de venta, <b>lo vendido</b> —solo lo confirmado— y <b>lo sin vendedor</b> —sin las
 * anuladas—, cada uno en total y <b>por tipo de producto</b>. Sin alcance (`RN-IN-011`).
 */
@Schema(name = "SaleLinesSummary")
public record SaleLinesSummaryResponse(
    IndicatorPeriod period,
    @Schema(description = "Lo vendido: las líneas de las ventas confirmadas.") Group sold,
    @Schema(
            description =
                "Lo sin vendedor: las líneas sin vendedor de las ventas no anuladas —lo que falta"
                    + " por atribuir—.")
        Group unassigned,
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "El tramo pedido —DAY, WEEK o MONTH—, o nulo si no se pidió.")
        String granularity,
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(
            description =
                "Los dos bloques por tramo, todos los tramos presentes; nulo si no se pidió"
                    + " tramo. La suma de los tramos es el total.")
        List<Bucket> buckets) {

  /** Un bloque: en total y por tipo de producto. */
  @Schema(name = "SaleLinesGroup")
  public record Group(
      Block total,
      @Schema(
              description =
                  "Por tipo de producto, los que tienen líneas, por nombre. Una venta con líneas de"
                      + " dos tipos cuenta en cada uno: las ventas por tipo pueden sumar más que"
                      + " el total; las líneas, unidades e importes, no.")
          List<TypeBlock> byType) {}

  /** Las cifras de un conjunto de líneas. */
  @Schema(name = "SaleLinesBlock")
  public record Block(
      long sales,
      long lines,
      @Schema(description = "Productos vendidos: la suma de las cantidades.") long units,
      List<IndicatorAmount> amounts) {}

  /** Las cifras de un tipo de producto. */
  @Schema(name = "SaleLinesTypeBlock")
  public record TypeBlock(
      @Schema(description = "El tipo del producto de la línea.", example = "BOT") String type,
      long sales,
      long lines,
      @Schema(description = "Productos vendidos: la suma de las cantidades.") long units,
      List<IndicatorAmount> amounts) {}

  /** Los bloques de un tramo. */
  @Schema(name = "SaleLinesBucket")
  public record Bucket(
      @Schema(description = "Día en que empieza el tramo en el calendario.", example = "2026-09-07")
          LocalDate start,
      Group sold,
      Group unassigned) {}
}
