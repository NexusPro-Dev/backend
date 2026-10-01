package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;

/** Confirmar un pago pendiente (`RF-MV-044`). El cuerpo es opcional. */
public record ConfirmPaymentRequest(
    @Schema(
            description =
                "La referencia del extracto o de la transferencia. Opcional, hasta 120 caracteres;"
                    + " en blanco equivale a no darla.")
        String providerReference) {}
