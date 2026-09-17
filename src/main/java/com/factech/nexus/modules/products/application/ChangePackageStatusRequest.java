package com.factech.nexus.modules.products.application;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo del cambio de estado de un paquete (`RF-PM-021` §11): {@code ACTIVO} o {@code INACTIVO}.
 */
public record ChangePackageStatusRequest(
    @NotBlank(message = "VAL-002: El estado es obligatorio y debe ser ACTIVO o INACTIVO.")
        String status) {}
