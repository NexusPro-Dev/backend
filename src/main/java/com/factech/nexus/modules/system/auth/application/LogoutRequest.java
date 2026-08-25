package com.factech.nexus.modules.system.auth.application;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo del cierre de sesión (`RF-SP-036`).
 *
 * <p>{@code allSessions} es opcional y por omisión falso. Es un dato de entrada y no una ruta
 * aparte porque la variante comparte actor, autorización y reglas con el cierre simple; lo único
 * que cambia es el alcance de la revocación.
 */
public record LogoutRequest(
    @NotBlank(message = "VAL-001: El refresh token es obligatorio.") String refreshToken,
    Boolean allSessions) {

  public boolean todasLasSesiones() {
    return Boolean.TRUE.equals(allSessions);
  }
}
