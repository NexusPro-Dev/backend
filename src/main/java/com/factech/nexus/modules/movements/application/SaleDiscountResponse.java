package com.factech.nexus.modules.movements.application;

import com.factech.nexus.modules.movements.domain.models.LineDiscount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Una rebaja de una línea, como se pactó y como se cobró (`RN-MV-027`).
 *
 * <p>{@code type} y {@code value} son la declaración —{@code PORCENTAJE} {@code 10}, o {@code FIJO}
 * {@code 5.00}— y {@code discountValue} es lo que valió <b>en dinero y por unidad</b> el día de la
 * venta. Viajan las tres porque «¿qué le prometieron?» y «¿cuánto le rebajaron?» son preguntas
 * distintas, y un porcentaje solo no sobrevive a una corrección del precio del producto.
 */
@Schema(name = "SaleDiscount")
public record SaleDiscountResponse(
    @Schema(description = "PORCENTAJE o FIJO.") String type,
    @Schema(description = "Lo pactado: 10 si es porcentaje, 5.00 si es fijo.") BigDecimal value,
    @Schema(description = "Lo que valió en dinero POR UNIDAD, congelado el día de la venta.")
        BigDecimal discountValue) {

  static SaleDiscountResponse de(LineDiscount rebaja) {
    return new SaleDiscountResponse(
        rebaja.getType().name(), rebaja.getValue(), rebaja.getDiscountValue());
  }
}
