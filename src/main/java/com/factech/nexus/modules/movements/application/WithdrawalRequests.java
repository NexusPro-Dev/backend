package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/** Las peticiones de la etapa 6 que llevan cuerpo (`RF-MV-019` a `RF-MV-023`). */
public final class WithdrawalRequests {

  private WithdrawalRequests() {}

  /** `RF-MV-019`: pedir un retiro de la billetera propia. */
  @Schema(name = "WithdrawalRequest")
  public record Request(
      @Schema(description = "De qué billetera se retira.") UUID currencyId,
      @Schema(description = "Cuánto. Mayor que cero, con los decimales de la moneda.")
          BigDecimal amount) {}

  /** `RF-MV-020`: aprobar un retiro. */
  @Schema(name = "WithdrawalApprovalRequest")
  public record Approval(
      @Schema(
              types = {"string", "null"},
              description =
                  "La referencia de la transferencia o del comprobante bancario, si la hay. Hasta"
                      + " 120 caracteres.")
          String providerReference) {}

  /** `RF-MV-021`: negar un retiro. */
  @Schema(name = "WithdrawalRejectionRequest")
  public record Rejection(
      @Schema(description = "Por qué se niega. Obligatorio, hasta 500 caracteres.")
          String reason) {}

  /** `RF-MV-023`: otorgar un bono. */
  @Schema(name = "GrantBonusRequest")
  public record Bonus(
      @Schema(description = "Quién lo recibe.") UUID userId,
      UUID currencyId,
      @Schema(description = "Mayor que cero, con los decimales de la moneda.") BigDecimal amount,
      @Schema(description = "Por qué. Obligatorio, hasta 500 caracteres.") String concept) {}
}
