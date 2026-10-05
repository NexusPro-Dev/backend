package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.models.CommissionValue;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * <b>La comisión por venta directa de una tasa de rol</b> en el contrato (`RN-CM-050`, 05-10-2026):
 * la misma forma en la entrada y en la salida, y la misma que la tasa —porcentaje o fijo, nunca las
 * dos (`RN-CM-016`)—.
 *
 * <p>Los errores de forma y de rango salen con el campo bajo {@code directCommission}, para que
 * quien pinta la fila sepa que lo mal escrito es la directa y no la tasa.
 */
@Schema(
    name = "CommissionRateDirect",
    description =
        "La comisión por venta directa del rol sobre el producto (RN-CM-050): lo que cobra cuando"
            + " vende él mismo, si no es el último eslabón. Porcentaje o fijo, nunca las dos.")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DirectCommissionBody(
    @Schema(description = "PORCENTAJE o FIJO.")
        @NotNull(
            message = "VAL-002: La forma de la directa es obligatoria: porcentaje o valor fijo.")
        CommissionRateType rateType,
    @DecimalMin(value = "0.00", message = "VAL-003: El porcentaje debe estar entre cero y cien.")
        @DecimalMax(
            value = "100.00",
            message = "VAL-003: El porcentaje debe estar entre cero y cien.")
        @Digits(
            integer = 3,
            fraction = 2,
            message = "VAL-003: El porcentaje admite como mucho dos decimales.")
        BigDecimal percentage,
    @DecimalMin(value = "0.00", message = "VAL-012: El valor fijo no puede ser negativo.")
        @Digits(
            integer = 12,
            fraction = 2,
            message = "VAL-012: El valor fijo admite como mucho dos decimales.")
        BigDecimal fixedAmount) {

  private static final String PREFIJO = "directCommission.";

  /** A la forma del dominio, con los campos de los errores bajo {@code directCommission}. */
  public CommissionValue toValue() {
    try {
      return CommissionValue.of(rateType, percentage, fixedAmount);
    } catch (ValidationException e) {
      throw new ValidationException(
          e.errorCode(),
          e.getMessage(),
          e.errors().stream()
              .map(f -> new FieldError(PREFIJO + f.field(), f.code(), f.message()))
              .toList());
    }
  }

  public static DirectCommissionBody from(CommissionValue directa) {
    return directa == null
        ? null
        : new DirectCommissionBody(
            directa.getRateType(), directa.getPercentage(), directa.getFixedAmount());
  }

  public static DirectCommissionBody from(
      CommissionRateType rateType, BigDecimal percentage, BigDecimal fixedAmount) {
    return rateType == null ? null : new DirectCommissionBody(rateType, percentage, fixedAmount);
  }
}
