package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.PaymentMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de {@code PUT /api/v1/commission-closings/next/payment-mode} (`RF-CM-029`).
 *
 * <p><b>El modo viaja como texto y se valida con {@code @Pattern}</b>: declarado como enumerado, un
 * valor desconocido fallaría al leer el JSON, con otro error y sin el código {@code VAL-001}.
 */
@Schema(name = "PaymentModeRequest")
public record PaymentModeRequest(
    @Schema(allowableValues = {"AUTOMATICO", "MANUAL"})
        @NotNull(message = "VAL-001: El modo de pago debe ser AUTOMATICO o MANUAL.")
        @Pattern(
            regexp = "AUTOMATICO|MANUAL",
            message = "VAL-001: El modo de pago debe ser AUTOMATICO o MANUAL.")
        String paymentMode) {

  /** El modo, ya validado. */
  public PaymentMode modo() {
    return PaymentMode.valueOf(paymentMode);
  }
}
