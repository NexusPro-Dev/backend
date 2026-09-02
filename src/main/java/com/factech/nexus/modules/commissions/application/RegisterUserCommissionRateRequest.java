package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.models.CommissionValue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/user-commission-rates} (`RF-CM-006`).
 *
 * <p><b>No es el mismo alta que la de rol, y por eso es otro endpoint.</b> Hasta el 01-09-2026 las
 * dos eran una sola operación con campos opcionales; ahora una escribe en un catálogo sin fechas y
 * la otra registra una excepción con vigencia y con la exigencia de que no haya otra viva.
 * Fundirlas obligaría a un endpoint cuyas validaciones dependen de qué campo llegó.
 *
 * <p><b>No hay {@code roleId}, y no falta</b>: la tasa es de la persona y punto (`cm.md` §7.2).
 * Quien la tiene gana lo mismo venda lo que venda — y sigue ganándolo aunque deje de vender.
 *
 * <p><b>Tampoco hay {@code productId}</b>, y eso sí es una regla: una tasa personalizada <b>no se
 * acota a un producto</b> (`RN-CM-014`).
 *
 * <h2>La forma es la misma elección que en una tasa de rol, y aquí pesa más</h2>
 *
 * <p>Los motivos para declararla en vez de deducirla están en {@link RegisterCommissionRateRequest}
 * y no se repiten. Lo propio de esta pieza es lo que un {@code FIJO} significa aquí: como no se
 * asocia a ningún producto, <b>rige sobre todo el catálogo</b> y su importe se interpreta en tantas
 * monedas como haya (`RN-CM-017`). No hay ninguna validación que lo advierta, y no puede haberla.
 *
 * <p><b>La ausencia del valor de la otra forma no significa nada</b>, al revés que la del fin de
 * vigencia: un fin vacío declara «indefinidamente»; un porcentaje vacío en una tasa {@code FIJO} es
 * la consecuencia mecánica de haber elegido la otra forma.
 */
public record RegisterUserCommissionRateRequest(
    @NotNull(message = "VAL-001: La persona de la tasa es obligatoria.") UUID userId,
    @NotNull(message = "VAL-002: La forma de la comisión es obligatoria: porcentaje o valor fijo.")
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
    @DecimalMin(value = "0.0000", message = "VAL-012: El valor fijo no puede ser negativo.")
        @Digits(
            integer = 10,
            fraction = 4,
            message = "VAL-012: El valor fijo admite como mucho cuatro decimales.")
        BigDecimal fixedAmount,
    @NotNull(message = "VAL-004: El inicio de vigencia es obligatorio.") LocalDate validFrom,
    LocalDate validTo) {

  /** La forma y el valor, construidos juntos. Ver {@link RegisterCommissionRateRequest#valor()}. */
  public CommissionValue valor() {
    return CommissionValue.of(rateType, percentage, fixedAmount);
  }
}
