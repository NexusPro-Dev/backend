package com.factech.nexus.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Firma y verificación del token de acceso (decisión D-08, `security.md` §5.2).
 *
 * <p><b>Clave simétrica y no un par asimétrico</b>, porque quien firma y quien verifica son el
 * mismo servicio: no hay ningún tercero que deba validar el token sin poder emitirlo. Un par de
 * claves añadiría gestión de material criptográfico sin resolver ningún problema que hoy exista. El
 * día que otro servicio necesite verificar sin emitir, este es el único punto que cambia.
 *
 * <p><b>El secreto llega por variable de entorno y no tiene valor por omisión en ningún entorno</b>
 * (Art. IX.1, IX.5): {@code application.yml} lo declara sin respaldo, de modo que la aplicación
 * falla al arrancar si falta en lugar de firmar con algo conocido.
 *
 * <p><b>HS256 exige al menos 256 bits de secreto.</b> Un secreto más corto hace fallar el arranque,
 * que es el comportamiento correcto: un token firmado con material débil es peor que ninguno,
 * porque aparenta protección.
 */
@Configuration
public class JwtSupport {

  private final SecretKey clave;

  public JwtSupport(@Value("${nexus.security.jwt-secret}") String secreto) {
    byte[] material = secreto.getBytes(StandardCharsets.UTF_8);
    if (material.length < 32) {
      throw new IllegalStateException(
          "JWT_SECRET debe tener al menos 32 bytes para firmar con HS256; tiene "
              + material.length
              + ". Un token firmado con material débil aparenta una protección que no da.");
    }
    this.clave = new SecretKeySpec(material, "HmacSHA256");
  }

  @Bean
  public JwtEncoder jwtEncoder() {
    return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
  }

  /**
   * Verifica firma, vigencia y <b>revocación</b>.
   *
   * <p>No hay margen de tolerancia sobre la expiración: quince minutos ya son un margen, y aceptar
   * un token vencido «por poco» convierte una vida declarada en una vida aproximada.
   *
   * <p><b>La revocación se comprueba aquí y no en un filtro aparte</b> (`RF-SP-028` `plan.md` §7).
   * Un token cuya persona perdió el acceso no vale, igual que uno caducado, y decidirlo en el mismo
   * sitio produce el mismo {@code 401} por el mismo camino. Además corta <b>antes</b> de que se
   * resuelvan los permisos contra la base, en lugar de pagar esa consulta para descartar la
   * petición justo después.
   *
   * <p>El validador por defecto —{@code JwtTimestampValidator}, que {@code build()} instala— se
   * conserva: se delega en los dos y no se sustituye uno por el otro.
   */
  @Bean
  public JwtDecoder jwtDecoder(AccessRevocationRegistry cortes) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();

    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(), new AccessRevocationValidator(cortes)));

    return decoder;
  }
}
