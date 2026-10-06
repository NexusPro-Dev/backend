package com.factech.nexus.modules.system.auth.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Credenciales de sesión (`RF-SP-034`, `RF-SP-035` y `RF-SP-072`).
 *
 * <p><b>El mismo tipo para el refresco y para el segundo paso del inicio de sesión</b>, y es
 * deliberado: el cliente trata las respuestas igual, y una forma distinta le obligaría a dos
 * caminos de código para el mismo resultado. El primer paso devuelve {@link LoginResponse}, que
 * lleva estos mismos miembros más el desafío.
 *
 * <p><b>No se devuelven los permisos efectivos ni ningún dato personal.</b> Quien los necesite los
 * consulta ya autenticado con `RF-SP-039`. Meterlos aquí los volvería una foto que envejece.
 *
 * @param expiresIn segundos de vida del token de acceso, para que el cliente sepa cuándo renovar
 *     sin tener que decodificarlo
 * @param mustChangePassword si es cierto, el resto de endpoints se le niegan hasta que la cambie
 * @param mfaEnrollmentRequired si es cierto, un rol de la persona exige el segundo factor y no lo
 *     tiene: el resto de endpoints se le niegan hasta que lo active (`RN-SP-062`)
 * @param recoveryCodesRemaining cuántos códigos de recuperación le quedan, <b>solo</b> cuando la
 *     sesión se abrió con uno (`RF-SP-072` `FA-001`); nulo en los demás casos
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SessionResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    boolean mustChangePassword,
    boolean mfaEnrollmentRequired,
    Integer recoveryCodesRemaining) {

  public static SessionResponse de(
      String accessToken, String refreshToken, long expiresIn, boolean cambioObligatorio) {
    return de(accessToken, refreshToken, expiresIn, cambioObligatorio, false, null);
  }

  public static SessionResponse de(
      String accessToken,
      String refreshToken,
      long expiresIn,
      boolean cambioObligatorio,
      boolean activacionObligatoria,
      Integer codigosRestantes) {
    return new SessionResponse(
        accessToken,
        refreshToken,
        "Bearer",
        expiresIn,
        cambioObligatorio,
        activacionObligatoria,
        codigosRestantes);
  }
}
