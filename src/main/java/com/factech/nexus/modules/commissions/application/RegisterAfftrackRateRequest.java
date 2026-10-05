package com.factech.nexus.modules.commissions.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/afftrack-rates} (`RF-CM-015`).
 *
 * <p><b>Cuatro campos y todos obligatorios</b>: un escalón no tiene forma que elegir (`RN-CM-038`).
 * Lo que se valida aquí es lo que mira un campo; los decimales del valor necesitan la moneda del
 * producto y los comprueba el caso de uso (`VAL-005`).
 *
 * <p><b>El límite viaja como decimal y se exige entero con {@code @Digits}</b>: declarado como
 * {@code Integer}, Jackson convertiría {@code 10.5} en {@code 10} sin avisar, y `CA-CM-214` pide
 * rechazarlo.
 *
 * <p><b>El valor cero es válido</b>: declara que alcanzar ese límite no paga, y consume los FTD
 * igual (`spec.md` §6.1).
 */
@Schema(name = "RegisterAfftrackRateRequest")
public record RegisterAfftrackRateRequest(
    @NotNull(message = "VAL-001: El rol es obligatorio.") UUID roleId,
    @NotNull(message = "VAL-002: El producto es obligatorio.") UUID productId,
    @NotNull(message = "VAL-003: El límite debe ser un número entero mayor que cero.")
        @Positive(message = "VAL-003: El límite debe ser un número entero mayor que cero.")
        @Digits(
            integer = 9,
            fraction = 0,
            message = "VAL-003: El límite debe ser un número entero mayor que cero.")
        BigDecimal threshold,
    @NotNull(message = "VAL-004: El valor por FTD es obligatorio y no puede ser negativo.")
        @DecimalMin(
            value = "0.0000",
            message = "VAL-004: El valor por FTD es obligatorio y no puede ser negativo.")
        @Digits(
            integer = 10,
            fraction = 2,
            message = "VAL-004: El valor por FTD admite como mucho dos decimales.")
        BigDecimal amountPerFtd) {

  /** El límite, ya validado como entero positivo. */
  public int limite() {
    return threshold.intValueExact();
  }
}
