package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;

/** Rechazar un pago pendiente —de una venta o de una compra de puntos— (`RF-MV-045`). */
public record RejectPaymentRequest(
    @Schema(description = "Por qué no entró el cobro. Obligatorio, hasta 500 caracteres.")
        String reason) {}
