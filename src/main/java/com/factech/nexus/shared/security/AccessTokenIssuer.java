package com.factech.nexus.shared.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Emite el token de acceso (`security.md` §5.2).
 *
 * <p><b>Los claims son exactamente los declarados y ni uno más</b>: {@code iss}, {@code sub},
 * {@code jti}, {@code iat}, {@code exp}, los códigos de rol y {@code mcp}. <b>Ningún dato
 * personal</b> —ni nombre, ni correo—: un JWT va <b>firmado, no cifrado</b>, y cualquiera que lo
 * posea puede leer su contenido con una herramienta de línea de comandos.
 *
 * <p><b>Por qué {@code mcp} y por qué no contradice lo anterior.</b> Indica que la cuenta tiene
 * pendiente el cambio obligatorio de contraseña. Sin él, negar el resto de endpoints mientras la
 * marca esté puesta obliga a leer {@code users.must_change_password} <b>en cada petición</b>, que
 * es justo la consulta por petición que la decisión D-08 existe para evitar. No identifica a nadie
 * ni dice nada de la persona más allá de que le toca cambiar la contraseña, y su único lector
 * posible es quien ya porta el token: su propio titular.
 *
 * <p><b>Los códigos de rol viajan y los permisos no.</b> Los permisos se resuelven al autorizar,
 * contra la base: así, retirar un rol surte efecto de inmediato en lugar de esperar a que el token
 * expire.
 */
@Component
public class AccessTokenIssuer {

  /** Nombre del claim del cambio obligatorio de contraseña. */
  public static final String CLAIM_CAMBIO_OBLIGATORIO = "mcp";

  /** Nombre del claim con los códigos de rol. */
  public static final String CLAIM_ROLES = "roles";

  private final JwtEncoder encoder;
  private final String emisor;
  private final Duration vida;

  public AccessTokenIssuer(
      JwtEncoder encoder,
      @Value("${nexus.security.jwt.issuer:nexus}") String emisor,
      @Value("${nexus.security.jwt.access-token-ttl:PT15M}") Duration vida) {
    this.encoder = encoder;
    this.emisor = emisor;
    this.vida = vida;
  }

  /**
   * Firma un token para esa persona.
   *
   * @param roles códigos de rol vigentes
   * @param cambioObligatorio si su credencial la fijó alguien que no es ella
   */
  public String emitir(UUID usuario, List<String> roles, boolean cambioObligatorio, Instant ahora) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(emisor)
            .subject(usuario.toString())
            // `jti` permite referirse a un token concreto en la auditoría sin
            // guardar su valor.
            .id(UUID.randomUUID().toString())
            .issuedAt(ahora)
            .expiresAt(ahora.plus(vida))
            .claim(CLAIM_ROLES, roles)
            .claim(CLAIM_CAMBIO_OBLIGATORIO, cambioObligatorio)
            .build();

    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  /** Vida del token en segundos, que es lo que la respuesta publica como {@code expiresIn}. */
  public long vidaEnSegundos() {
    return vida.toSeconds();
  }
}
