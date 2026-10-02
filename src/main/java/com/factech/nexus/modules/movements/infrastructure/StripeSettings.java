package com.factech.nexus.modules.movements.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La configuración de la pasarela de la tarjeta (`architecture.md` §11 y §15.4).
 *
 * <p><b>Las dos credenciales son opcionales y van juntas</b>: sin cualquiera de ellas la pasarela
 * queda apagada. La dirección es configurable para que las pruebas la apunten a un servidor
 * simulado; por omisión, la real. <b>La versión de la API se fija</b>: un cambio de Stripe no debe
 * cambiar lo que este sistema recibe sin que nadie lo decida.
 */
@ConfigurationProperties(prefix = "nexus.stripe")
public record StripeSettings(
    String secretKey,
    String webhookSecret,
    String apiBaseUrl,
    String apiVersion,
    Duration timeout,
    Duration signatureTolerance,
    boolean processInline) {

  public StripeSettings {
    apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.stripe.com" : apiBaseUrl;
    apiVersion = apiVersion == null || apiVersion.isBlank() ? "2024-06-20" : apiVersion;
    timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    signatureTolerance = signatureTolerance == null ? Duration.ofMinutes(5) : signatureTolerance;
  }

  public boolean encendida() {
    return secretKey != null
        && !secretKey.isBlank()
        && webhookSecret != null
        && !webhookSecret.isBlank();
  }
}
