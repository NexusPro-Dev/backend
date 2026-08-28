package com.factech.nexus.shared.security;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rechaza el token de acceso cuya persona perdió el acceso después de emitirlo (`RF-SP-028`
 * `plan.md` §7).
 *
 * <p><b>Es un validador del token y no un filtro</b>, y esa es la decisión de diseño de esta clase.
 * Un token cortado no es un token de alguien sin permiso: es un token que **ya no vale**, igual que
 * uno caducado o mal firmado. Puesto aquí comparte camino con {@code JwtTimestampValidator},
 * produce el mismo {@code 401} por el mismo mecanismo —el punto de entrada de la cadena— y, sobre
 * todo, <b>corta antes de que {@code JwtActorConverter} consulte los permisos en la base</b>. Un
 * filtro posterior habría pagado esa consulta para descartar la petición justo después.
 *
 * <p><b>Cuesta una consulta a un mapa en memoria por petición autenticada.</b> Es el precio de que
 * `security.md` §4.5 sea cierto: sin esto, quien acaba de ser desactivado, eliminado o cuya
 * contraseña acaba de restablecerse conserva hasta quince minutos de acceso con el token que ya
 * tenía en la mano.
 *
 * <p><b>Un {@code sub} que no es un UUID no es asunto de esta clase.</b> Lo emite {@link
 * AccessTokenIssuer} y siempre lo es; si algún día dejara de serlo, quien debe protestar es quien
 * convierte el token en actor, no quien comprueba una revocación. Aquí se deja pasar y se decide
 * más adelante, para no convertir un error de emisión en un {@code 401} que apunta al sitio
 * equivocado.
 */
public class AccessRevocationValidator implements OAuth2TokenValidator<Jwt> {

  /**
   * El motivo <b>no viaja al cliente</b>. `security.md` unifica el rechazo de credencial:
   * distinguir «su acceso fue retirado» de «su token caducó» le diría a quien robó un token que la
   * cuenta existe y que alguien reaccionó.
   */
  private static final OAuth2Error CREDENCIAL_NO_VALIDA =
      new OAuth2Error("invalid_token", "La credencial no es válida.", null);

  private final AccessRevocationRegistry cortes;

  public AccessRevocationValidator(AccessRevocationRegistry cortes) {
    this.cortes = cortes;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    UUID usuario;
    try {
      usuario = UUID.fromString(token.getSubject());
    } catch (IllegalArgumentException | NullPointerException noEsUnUsuario) {
      return OAuth2TokenValidatorResult.success();
    }

    Instant emitidoEn = token.getIssuedAt();
    if (emitidoEn == null) {
      // Sin `iat` no hay con qué comparar el corte. Se rechaza, y no se deja
      // pasar: un token sin instante de emisión es inmune a toda revocación, y
      // esa es exactamente la propiedad que un token robado querría tener.
      return OAuth2TokenValidatorResult.failure(CREDENCIAL_NO_VALIDA);
    }

    return cortes.estaCortado(usuario, emitidoEn)
        ? OAuth2TokenValidatorResult.failure(CREDENCIAL_NO_VALIDA)
        : OAuth2TokenValidatorResult.success();
  }
}
