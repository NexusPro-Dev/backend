package com.factech.nexus.shared.video;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La configuración de los proveedores de video (`architecture.md` §11).
 *
 * <p><b>Las dos credenciales son opcionales</b>: sin la de un proveedor, sus lecciones exigen la
 * duración a mano (`RN-AC-017`). <b>Vimeo se lee por su API con token</b> —también los videos
 * privados de la cuenta del token— y, sin token, por su oEmbed público, que no es fiable: respondió
 * {@code 404} a un video público el 25-09-2026 (`ac.md` §5.2.11). Las direcciones son configurables
 * para que las pruebas las apunten a otro sitio; por omisión, las reales. <b>El plazo es corto</b>
 * a propósito: la consulta ocurre mientras administración espera la respuesta de su formulario.
 */
@ConfigurationProperties(prefix = "nexus.video")
public record VideoSettings(
    String youtubeApiKey,
    String youtubeBaseUrl,
    String vimeoAccessToken,
    String vimeoApiBaseUrl,
    String vimeoBaseUrl,
    Duration timeout) {

  public VideoSettings {
    youtubeBaseUrl = conOmision(youtubeBaseUrl, "https://www.googleapis.com/youtube/v3");
    vimeoApiBaseUrl = conOmision(vimeoApiBaseUrl, "https://api.vimeo.com");
    vimeoBaseUrl = conOmision(vimeoBaseUrl, "https://vimeo.com");
    timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
  }

  public boolean hayClaveDeYoutube() {
    return youtubeApiKey != null && !youtubeApiKey.isBlank();
  }

  public boolean hayTokenDeVimeo() {
    return vimeoAccessToken != null && !vimeoAccessToken.isBlank();
  }

  private static String conOmision(String valor, String omision) {
    return valor == null || valor.isBlank() ? omision : valor;
  }
}
