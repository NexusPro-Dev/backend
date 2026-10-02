package com.factech.nexus.modules.movements.application;

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
}
