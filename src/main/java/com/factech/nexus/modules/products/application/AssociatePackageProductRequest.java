package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.DiscountType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpo de la asociación de un producto a un paquete (`RF-PM-023` §11).
 *
 * <p>Los tres obligatorios (`VAL-002`). <b>Forma y valor van juntos</b> porque son un solo dato: un
 * porcentaje sin cifra o una cifra sin forma no significan nada. La forma del valor —porcentaje de
 * cero a cien con dos decimales, fijo con los decimales de la moneda— la comprueba {@code
 * DiscountValue}, que es donde vive, porque necesita la moneda del paquete.
 */
public record AssociatePackageProductRequest(
    @NotNull(message = "VAL-002: El producto, la forma del descuento y su valor son obligatorios.")
        UUID productId,
    @NotNull(message = "VAL-002: El producto, la forma del descuento y su valor son obligatorios.")
        DiscountType discountType,
    @NotNull(message = "VAL-002: El producto, la forma del descuento y su valor son obligatorios.")
        BigDecimal discountValue) {}
