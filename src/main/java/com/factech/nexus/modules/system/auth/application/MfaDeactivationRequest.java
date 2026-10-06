package com.factech.nexus.modules.system.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;

/** Desactivar el propio segundo factor (`RF-SP-075`): la contraseña vigente. */
@Schema(name = "MfaDeactivationRequest")
public record MfaDeactivationRequest(
    @Schema(description = "La contraseña vigente. Nunca se registra.") String currentPassword) {}
