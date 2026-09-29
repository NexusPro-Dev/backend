package com.factech.nexus.modules.commissions.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/user-afftrack-rates} (`RF-CM-019`).
 *
 * <p>El límite viaja como decimal y se exige entero, por lo mismo que en {@link
 * RegisterAfftrackRateRequest}. Que el fin no sea anterior al inicio mira dos campos y lo comprueba
 * el caso de uso (`VAL-004`).
 */
@Schema(name = "RegisterUserAfftrackRateRequest")
public record RegisterUserAfftrackRateRequest(
    @NotNull(message = "VAL-001: La persona es obligatoria.") UUID userId,
    @NotNull(message = "VAL-001: El producto es obligatorio.") UUID productId,
    @NotNull(message = "VAL-001: El límite es obligatorio.")
        @Positive(message = "VAL-002: El límite debe ser un número entero mayor que cero.")
        @Digits(
            integer = 9,
            fraction = 0,
            message = "VAL-002: El límite debe ser un número entero mayor que cero.")
        BigDecimal threshold,
    @NotNull(message = "VAL-001: El valor por FTD es obligatorio.")
        @DecimalMin(value = "0.0000", message = "VAL-003: El valor por FTD no puede ser negativo.")
        @Digits(
            integer = 10,
            fraction = 4,
            message = "VAL-003: El valor por FTD admite como mucho cuatro decimales.")
        BigDecimal amountPerFtd,
    @NotNull(message = "VAL-001: El inicio de vigencia es obligatorio.") LocalDate validFrom,
    LocalDate validTo) {

  public int limite() {
    return threshold.intValueExact();
  }
}
