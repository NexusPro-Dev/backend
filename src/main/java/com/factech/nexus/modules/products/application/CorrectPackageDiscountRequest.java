package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.DiscountType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Cuerpo de la corrección del descuento de un producto en un paquete (`RF-PM-024` §11).
 *
 * <p><b>Forma y valor, juntos y obligatorios: no es parcial</b> (`CA-PM-317`). Son un solo dato, y
 * una corrección que trajera solo la cifra dejaría al servidor adivinando sobre qué forma aplica.
 * <b>Sin {@code productId}</b>: va en la ruta, y en el cuerpo es un campo desconocido (`VAL-005`).
 */
public record CorrectPackageDiscountRequest(
    @NotNull(message = "VAL-002: La forma del descuento y su valor son obligatorios.")
        DiscountType discountType,
    @NotNull(message = "VAL-002: La forma del descuento y su valor son obligatorios.")
        BigDecimal discountValue) {}
