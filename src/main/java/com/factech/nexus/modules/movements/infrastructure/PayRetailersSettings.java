package com.factech.nexus.modules.movements.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La configuración de la pasarela local (`deployment.md` §6.5.2). <b>Las tiendas no están aquí</b>:
 * PayRetailers da una por país, y su {@code shopId} y su clave secreta —cifrada— viven en la
 * conversión del país (`RN-MV-063`), que fija un ADMIN. Aquí solo lo que es de la cuenta: la
 * Subscription Key de la API y la llave maestra que cifra las claves. <b>Encendida solo con las
 * dos</b>; sin ellas, `PSE` se comporta como antes del 05-10-2026.
 *
 * @param encryptionKey la llave maestra, 32 bytes en Base64 (AES-256)
 * @param baseUrl por omisión, el sandbox: un entorno mal configurado prueba, no cobra
 * @param reconcileAfter cuánto espera un cobro sin noticias antes de que el barrido pregunte
 */
@ConfigurationProperties(prefix = "nexus.payretailers")
public record PayRetailersSettings(
    String subscriptionKey,
    String encryptionKey,
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

  public boolean conSubscriptionKey() {
    return subscriptionKey != null && !subscriptionKey.isBlank();
  }
}
