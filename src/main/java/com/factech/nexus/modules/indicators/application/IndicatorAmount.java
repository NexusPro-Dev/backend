package com.factech.nexus.modules.indicators.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un importe en una moneda. <b>Nunca se suman entre sí</b> (`RN-IN-004`): sumar pesos con dólares
 * exige escoger una tasa, y esa decisión no es de un indicador.
 */
@Schema(name = "IndicatorAmount")
public record IndicatorAmount(
    IndicatorCurrency currency,
    @Schema(description = "En decimales, como el resto de la API.", example = "1250.00")
        BigDecimal amount) {

  /** La moneda de un importe. */
  @Schema(name = "IndicatorCurrency")
  public record IndicatorCurrency(UUID id, @Schema(example = "USD") String code) {}
}
