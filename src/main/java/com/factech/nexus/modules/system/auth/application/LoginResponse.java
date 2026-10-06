package com.factech.nexus.modules.system.auth.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo que responde el primer paso del inicio de sesión (`RF-SP-034`, enmendado por `RF-SP-072`).
 *
 * <p><b>Un solo esquema con un discriminador, y no un {@code oneOf}</b> (`072` · `plan.md` §4): un
 * cliente que hoy lee {@code accessToken} sigue funcionando con las cuentas sin segundo factor sin
 * cambiar una línea, y uno nuevo mira {@code mfaRequired} antes de nada.
 *
 * <ul>
 *   <li>{@code mfaRequired} en falso: los miembros de {@link SessionResponse}, como siempre.
 *   <li>En verdadero: {@code challengeToken} y su vida en segundos, y <b>ningún token</b>. La
 *       sesión nace en {@code POST /auth/login/mfa}.
 * </ul>
 */
@Schema(name = "LoginResponse")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoginResponse(
    boolean mfaRequired,
    String challengeToken,
    Long challengeExpiresIn,
    String accessToken,
    String refreshToken,
    String tokenType,
    Long expiresIn,
    boolean mustChangePassword,
    boolean mfaEnrollmentRequired) {

  public static LoginResponse sesion(SessionResponse sesion) {
    return new LoginResponse(
        false,
        null,
        null,
        sesion.accessToken(),
        sesion.refreshToken(),
        sesion.tokenType(),
        sesion.expiresIn(),
        sesion.mustChangePassword(),
        sesion.mfaEnrollmentRequired());
  }

  public static LoginResponse desafio(String challengeToken, long segundos) {
    return new LoginResponse(true, challengeToken, segundos, null, null, null, null, false, false);
  }
}
