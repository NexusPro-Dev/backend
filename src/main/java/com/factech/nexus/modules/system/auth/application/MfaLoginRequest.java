package com.factech.nexus.modules.system.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * El segundo paso del inicio de sesión (`RF-SP-072`): el desafío y <b>uno</b> de los dos códigos.
 *
 * @param challengeToken el que entregó {@code POST /auth/login}
 * @param code los seis dígitos de la app autenticadora
 * @param recoveryCode uno de los códigos de recuperación, en lugar de {@code code}
 */
@Schema(name = "MfaLoginRequest")
public record MfaLoginRequest(String challengeToken, String code, String recoveryCode) {}
