package com.factech.nexus.modules.system.auth.application;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo del refresco (`RF-SP-035`).
 *
 * <p><b>En el cuerpo y no en una cabecera.</b> El refresh token se envía únicamente a este
 * endpoint, y el cuerpo es lo que lo mantiene fuera de los registros de acceso de cualquier
 * intermediario, que sí registran las URL y a menudo las cabeceras.
 */
public record RefreshRequest(
    @NotBlank(message = "VAL-001: El refresh token es obligatorio.") String refreshToken) {}
