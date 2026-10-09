package com.factech.nexus.modules.system.brokers.infrastructure;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Los secretos con los que cada broker autentica sus avisos (`RF-SP-078` · `T-02`, `RN-SP-066`).
 *
 * <p><b>Uno por broker, por identificador y en el entorno</b>: los identificadores los fija `V9`,
 * iguales en todos los entornos, y los secretos llegan por variable. <b>No van a la base</b> porque
 * el catálogo se puebla por migración (`RN-SP-039`) y un secreto en una migración acaba en el
 * repositorio. Un secreto vacío es un broker <b>sin configurar</b>, que no puede avisar.
 */
@ConfigurationProperties(prefix = "nexus.brokers")
public record BrokerNotificationSettings(
    Map<UUID, String> notificationTokens, String notificationToken) {

  public BrokerNotificationSettings {
    notificationTokens = notificationTokens == null ? Map.of() : Map.copyOf(notificationTokens);
  }

  /**
   * El secreto de la dirección común (`RN-SP-069`), o vacío si no está configurado. Uno para todos
   * los brokers: el broker lo dice el `advertiser` del aviso, no el secreto.
   */
  public Optional<String> commonToken() {
    return notificationToken == null || notificationToken.isBlank()
        ? Optional.empty()
        : Optional.of(notificationToken);
  }

  /** El secreto de ese broker, o vacío si no está configurado. */
  public Optional<String> tokenOf(UUID brokerId) {
    String token = notificationTokens.get(brokerId);
    return token == null || token.isBlank() ? Optional.empty() : Optional.of(token);
  }
}
