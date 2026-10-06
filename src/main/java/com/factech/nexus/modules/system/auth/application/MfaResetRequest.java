package com.factech.nexus.modules.system.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;

/** Restablecer el segundo factor de otra persona (`RF-SP-076`): el motivo, obligatorio. */
@Schema(name = "MfaResetRequest")
public record MfaResetRequest(
    @Schema(
            description = "Por qué se restablece. Con contenido, hasta 500 caracteres.",
            example = "Perdió el teléfono y los códigos; identidad comprobada por teléfono.")
        String reason) {}
