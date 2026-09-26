package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;

/** Rechazar el pago pendiente de una venta (`RF-MV-004`). */
public record RejectPaymentRequest(
    @Schema(description = "Por qué no entró el cobro. Obligatorio, hasta 500 caracteres.")
        String reason) {}
