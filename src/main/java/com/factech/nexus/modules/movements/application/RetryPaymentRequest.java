package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Volver a pagar una venta propia pendiente (`RF-MV-018`). */
public record RetryPaymentRequest(
    @Schema(
            types = {"string", "null"},
            format = "uuid",
            description =
                "Con qué se intenta pagar ahora. Obligatorio si la venta cobra algo; PROHIBIDO si"
                    + " su importe es cero, porque entonces lo pone el sistema (`RN-MV-022`).")
        UUID paymentMethodId) {}
