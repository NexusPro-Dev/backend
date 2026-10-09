package com.factech.nexus.shared.zoom;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La configuración de Zoom (`architecture.md` §11): la app <b>Server-to-Server OAuth</b> de la
 * cuenta de pago —tres credenciales— y el usuario anfitrión de las reuniones.
 *
 * <p><b>Las credenciales son opcionales</b>: sin ellas, lo que llama a Zoom responde {@code 503} y
 * lo demás funciona. Las direcciones son configurables para que las pruebas las apunten a un
 * servidor simulado; por omisión, las reales. <b>La zona</b> es la que se le da a Zoom y la que se
 * supone a una hora que llega sin zona (`RN-AC-029`).
 */
@ConfigurationProperties(prefix = "nexus.zoom")
public record ZoomSettings(
    String accountId,
    String clientId,
    String clientSecret,
    String hostUser,
    String apiBaseUrl,
    String oauthUrl,
    String timeZone,
    Duration timeout) {

  public ZoomSettings {
    hostUser = conOmision(hostUser, "me");
    apiBaseUrl = conOmision(apiBaseUrl, "https://api.zoom.us/v2");
    oauthUrl = conOmision(oauthUrl, "https://zoom.us/oauth/token");
    timeZone = conOmision(timeZone, "America/Bogota");
    timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
  }

  public boolean configurado() {
    return lleno(accountId) && lleno(clientId) && lleno(clientSecret);
  }

  public ZoneId zona() {
    return ZoneId.of(timeZone);
  }

  private static boolean lleno(String valor) {
    return valor != null && !valor.isBlank();
  }

  private static String conOmision(String valor, String omision) {
    return valor == null || valor.isBlank() ? omision : valor;
  }
}
