package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de {@code POST /api/v1/movements/{id}/voiding} (`RF-MV-005`).
 *
 * <p>Por {@code POST} con cuerpo y no en la URL: en la <i>query string</i> el motivo acabaría
 * escrito en los registros de acceso de cualquier proxy. Es lo que `DeleteProductRequest` ya dejó
 * escrito, y vale igual aquí.
 */
public record VoidSaleRequest(
    @Schema(description = "Por qué la venta no debía existir. Obligatorio, hasta 500 caracteres.")
        String reason) {}
