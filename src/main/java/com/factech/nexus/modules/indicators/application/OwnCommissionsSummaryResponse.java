package com.factech.nexus.modules.indicators.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * El resumen de mis comisiones (`RF-IN-008` §6.2): las comisiones <b>de quien pregunta</b>, por el
 * estado de su lote hoy y en total (`RN-IN-012`, `RN-IN-013`).
 */
@Schema(name = "OwnCommissionsSummary")
public record OwnCommissionsSummaryResponse(
    @Schema(
            description =
                "Qué comisiones se contaron: las nacidas estos días. Sin fechas, desde el principio"
                    + " hasta hoy.")
        IndicatorPeriod period,
    @Schema(description = "Las comisiones en mi lote abierto: sigue creciendo.") Block open,
    @Schema(description = "Las comisiones en lotes cerrados que esperan el pago.") Block pending,
    @Schema(description = "Las comisiones en lotes ya pagados.") Block paid,
    @Schema(description = "Todas mis comisiones, sea cual sea el estado de su lote.") Block total) {

  /** Las comisiones de un estado. */
  @Schema(name = "OwnCommissionsBlock")
  public record Block(
      @Schema(description = "Cuántas comisiones.") long commissions,
      @Schema(
              description =
                  "Lo que suman, uno por moneda —la del lote de cada comisión—. Nunca se suman"
                      + " entre monedas.")
          List<IndicatorAmount> amounts) {}
}
