package com.factech.nexus.modules.indicators.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * El resumen de ventas (`RF-IN-001` §6.2). Lo pendiente y lo anulado no llevan líneas ni unidades:
 * son contexto de lo vendido, no lo vendido. Desde el 06-10-2026 trae además el <b>total</b> y, en
 * cada bloque, cuántas ventas fueron <b>gratuitas</b> (`RN-IN-008`).
 */
@Schema(name = "SalesSummary")
public record SalesSummaryResponse(
    IndicatorPeriod period, Total total, Confirmed confirmed, Other pending, Other voided) {

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
}
