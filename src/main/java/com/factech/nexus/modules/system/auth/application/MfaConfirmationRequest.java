package com.factech.nexus.modules.system.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;

/** El primer código de la app, que activa el factor pendiente (`RF-SP-071`). */
@Schema(name = "MfaConfirmationRequest")
public record MfaConfirmationRequest(
    @Schema(
            description = "Los seis dígitos que muestra la app en este momento.",
            example = "123456")
        String code) {}
