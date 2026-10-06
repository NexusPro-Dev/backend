package com.factech.nexus.modules.system.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Lo que la app autenticadora necesita para vincularse (`RF-SP-071`).
 *
 * @param otpauthUri la URI que el cliente pinta como código QR
 * @param secret el mismo secreto escrito, para quien no pueda escanear
 * @param expiresAt hasta cuándo puede confirmarse
 */
@Schema(name = "MfaEnrollmentResponse")
public record MfaEnrollmentResponse(String otpauthUri, String secret, OffsetDateTime expiresAt) {}
