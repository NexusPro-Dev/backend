package com.factech.nexus.modules.system.exchangerates.application;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/exchange-rates} (`RF-SP-047`).
 *
 * <p><b>`validTo` ausente y nulo significan lo mismo</b>: la tasa es vitalicia. No hay un tercer
 * estado que distinguir, de modo que aquí no hace falta {@code Patchable} — eso es cosa del {@code
 * PATCH} de `RF-SP-049`.
 *
 * <p><b>`isActive` es opcional y por omisión verdadero.</b> Se recibe, al revés que el {@code
 * status} de `RF-PM-001`: allí una regla obliga a nacer inactivo y aquí no hay ninguna.
 *
 * <p><b>Las fechas son {@code LocalDate}</b>: una tasa rige por días. Enviar una hora se rechaza
 * por formato, y eso evita la pregunta de en qué zona se corta el día.
 */
public record RegisterExchangeRateRequest(
    @NotNull(message = "VAL-001: La moneda de origen es obligatoria.") UUID sourceCurrencyId,
    @NotNull(message = "VAL-002: La moneda de destino es obligatoria.") UUID targetCurrencyId,
    @NotNull(message = "VAL-004: El precio de la tasa es obligatorio.")
        @DecimalMin(
            value = "0.0",
            inclusive = false,
            message = "VAL-004: El precio de la tasa debe ser mayor que cero.")
        @Digits(
            integer = 10,
            fraction = 8,
            message = "VAL-005: El precio admite como mucho ocho decimales.")
        BigDecimal price,
    @NotNull(message = "VAL-006: La fecha de inicio de la vigencia es obligatoria.")
        LocalDate validFrom,
    LocalDate validTo,
    Boolean isActive) {

  /** Por omisión, la tasa nace activa: es el caso normal. */
  public boolean activa() {
    return isActive == null || isActive;
  }
}
