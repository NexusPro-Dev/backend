package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Los tres saldos de una persona en una moneda (`RN-MV-041`): lo que puede retirar, lo retenido en
 * retiros sin resolver, y los puntos, que no se retiran.
 */
@Schema(name = "Balances")
public record BalancesResponse(
    SaleResponse.Money currency,
    @Schema(description = "BILLETERA: lo que se puede retirar.") BigDecimal wallet,
    @Schema(description = "RETENIDO: lo pedido en retiros que nadie ha resuelto.") BigDecimal held,
    @Schema(description = "PUNTOS: para comprar en la tienda; no se retiran.") BigDecimal points) {}
