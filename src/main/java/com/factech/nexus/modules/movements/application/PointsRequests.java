package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Los cuerpos de la etapa 3 (`requirements/mv.md` §4.4), agrupados como {@link WithdrawalRequests}.
 */
public final class PointsRequests {

  private PointsRequests() {}

  /** `RF-MV-025`: fijar la tasa de una moneda. */
  public record SetRate(UUID currencyId, BigDecimal pointsPerUnit) {}

  /** `RF-MV-027`: comprar puntos. */
  public record Purchase(UUID currencyId, BigDecimal amount, UUID paymentMethodId) {}

  /** `RF-MV-052`: ajustar los puntos de una persona. */
  @Schema(name = "PointsAdjustmentRequest")
  public record Adjustment(
      @Schema(description = "A quién.") UUID userId,
      @Schema(description = "En qué cuenta de puntos: los puntos son de una moneda.")
          UUID currencyId,
      @Schema(
              description =
                  "Distintos de cero, con dos decimales como mucho. Positivos suman; negativos"
                      + " restan, y nunca por debajo de cero.")
          BigDecimal points,
      @Schema(description = "Por qué. Obligatorio, hasta 500 caracteres.") String concept,
      @Schema(
              types = {"string", "null"},
              description =
                  "La referencia del comprobante —el número de la consignación—. Opcional; si"
                      + " viene, con contenido y hasta 120 caracteres.")
          String reference) {}
}
