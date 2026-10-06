package com.factech.nexus.modules.indicators.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * El resumen de ventas (`RF-IN-001` §6.2). Lo pendiente y lo anulado no llevan líneas ni unidades:
 * son contexto de lo vendido, no lo vendido.
 */
@Schema(name = "SalesSummary")
public record SalesSummaryResponse(
    IndicatorPeriod period, Confirmed confirmed, Other pending, Other voided) {

  /** Lo vendido: lo confirmado. */
  @Schema(name = "SalesSummaryConfirmed")
  public record Confirmed(long sales, long lines, long units, List<IndicatorAmount> amounts) {}

  /** Lo pendiente o lo anulado. */
  @Schema(name = "SalesSummaryOther")
  public record Other(long sales, List<IndicatorAmount> amounts) {}
}
