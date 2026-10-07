package com.factech.nexus.modules.indicators.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * El resumen de lotes de comisiones (`RF-IN-007` §6.2): los lotes <b>como están hoy</b>, por estado
 * y en total. Sin alcance (`RN-IN-011`) y sin periodo (`RN-IN-012`): por eso no lleva {@code
 * period}.
 */
@Schema(name = "CommissionBatchesSummary")
public record CommissionBatchesSummaryResponse(
    @Schema(description = "Los lotes abiertos: siguen creciendo con cada comisión que se devenga.")
        Block open,
    @Schema(description = "Los lotes pendientes: cerrados, esperando a que se paguen.")
        Block pending,
    @Schema(description = "Los lotes pagados: abonados en la billetera de su persona.") Block paid,
    @Schema(description = "Todos los lotes, sean cuales sean sus estados.") Block total) {

  /** Los lotes de un estado. */
  @Schema(name = "CommissionBatchesBlock")
  public record Block(
      @Schema(description = "Cuántos lotes.") long batches,
      @Schema(
              description =
                  "El valor de esos lotes —la suma del total que cada uno tiene hoy—, uno por"
                      + " moneda. Nunca se suman entre monedas.")
          List<IndicatorAmount> amounts) {}
}
