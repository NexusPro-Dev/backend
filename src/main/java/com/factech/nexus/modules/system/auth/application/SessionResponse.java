package com.factech.nexus.modules.system.auth.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Credenciales de sesión (`RF-SP-034` y `RF-SP-035`).
 *
 * <p><b>El mismo tipo para el inicio de sesión y para el refresco</b>, y es deliberado: el cliente
 * trata las dos respuestas igual, y una forma distinta le obligaría a dos caminos de código para el
 * mismo resultado.
 *
 * <p><b>No se devuelven los permisos efectivos ni ningún dato personal.</b> Quien los necesite los
 * consulta ya autenticado con `RF-SP-039`. Meterlos aquí los volvería una foto que envejece.
 *
 * @param expiresIn segundos de vida del token de acceso, para que el cliente sepa cuándo renovar
 *     sin tener que decodificarlo
 * @param mustChangePassword si es cierto, el resto de endpoints se le niegan hasta que la cambie
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SessionResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    boolean mustChangePassword) {

  public static SessionResponse de(
      String accessToken, String refreshToken, long expiresIn, boolean cambioObligatorio) {
    return new SessionResponse(accessToken, refreshToken, "Bearer", expiresIn, cambioObligatorio);
  }
}
