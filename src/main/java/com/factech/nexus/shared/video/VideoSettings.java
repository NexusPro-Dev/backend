package com.factech.nexus.shared.video;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La configuración de los proveedores de video (`architecture.md` §11).
 *
 * <p><b>La clave de YouTube es opcional</b>: sin ella la consulta a YouTube falla con su motivo y
 * la lección exige la duración a mano (`RN-AC-017`). Las direcciones son configurables para que las
 * pruebas y un entorno aislado puedan apuntarlas a otro sitio; por omisión, las reales. <b>El plazo
 * es corto</b> a propósito: la consulta ocurre mientras administración espera la respuesta de su
 * formulario.
 */
@ConfigurationProperties(prefix = "nexus.video")
public record VideoSettings(
    String youtubeApiKey, String youtubeBaseUrl, String vimeoBaseUrl, Duration timeout) {

  public VideoSettings {
    youtubeBaseUrl =
        youtubeBaseUrl == null || youtubeBaseUrl.isBlank()
            ? "https://www.googleapis.com/youtube/v3"
            : youtubeBaseUrl;
    vimeoBaseUrl =
        vimeoBaseUrl == null || vimeoBaseUrl.isBlank() ? "https://vimeo.com" : vimeoBaseUrl;
    timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
  }

  public boolean hayClaveDeYoutube() {
    return youtubeApiKey != null && !youtubeApiKey.isBlank();
  }
}
