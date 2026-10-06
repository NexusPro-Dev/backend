package com.factech.nexus.modules.system.auth.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Un token de acceso con la prueba del segundo factor en el instante actual (`RF-SP-073`).
 *
 * <p><b>Solo el token de acceso</b>: el refresh token no cambia, porque la sesión es la misma.
 *
 * @param mfaValidUntil hasta cuándo valen las operaciones sensibles con este token
 * @param recoveryCodesRemaining cuántos códigos de recuperación quedan, si se usó uno; nulo si no
 */
@Schema(name = "MfaVerificationResponse")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MfaVerificationResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    OffsetDateTime mfaValidUntil,
    Integer recoveryCodesRemaining) {}
