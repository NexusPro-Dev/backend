package com.factech.nexus.modules.movements.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La configuración de la pasarela local (`deployment.md` §6.5.2). <b>Encendida solo con las tres
 * credenciales</b>; sin ellas, `PSE` se comporta como antes del 05-10-2026.
 *
 * @param baseUrl por omisión, el sandbox: un entorno mal configurado prueba, no cobra
 * @param reconcileAfter cuánto espera un cobro sin noticias antes de que el barrido pregunte
 */
@ConfigurationProperties(prefix = "nexus.payretailers")
public record PayRetailersSettings(
    String shopId,
    String secretKey,
    String subscriptionKey,
    String baseUrl,
    String notificationUrl,
    String returnUrl,
    boolean testMode,
    Duration timeout,
    Duration reconcileAfter,
    int reconcileBatch,
    boolean processInline) {

  public PayRetailersSettings {
    baseUrl =
        baseUrl == null || baseUrl.isBlank()
            ? "https://api-sandbox.payretailers.com/payments/v2"
            : baseUrl;
    timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
    reconcileAfter = reconcileAfter == null ? Duration.ofMinutes(10) : reconcileAfter;
    reconcileBatch = reconcileBatch <= 0 ? 50 : reconcileBatch;
  }

  public boolean encendida() {
    return presente(shopId) && presente(secretKey) && presente(subscriptionKey);
  }

  private static boolean presente(String valor) {
    return valor != null && !valor.isBlank();
  }
}
