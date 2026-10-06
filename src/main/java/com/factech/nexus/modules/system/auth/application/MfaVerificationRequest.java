package com.factech.nexus.modules.system.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * La reverificación antes de una operación sensible (`RF-SP-073`): <b>uno</b> de los dos códigos.
 *
 * @param code los seis dígitos de la app autenticadora
 * @param recoveryCode uno de los códigos de recuperación, en lugar de {@code code}
 */
@Schema(name = "MfaVerificationRequest")
public record MfaVerificationRequest(String code, String recoveryCode) {}
