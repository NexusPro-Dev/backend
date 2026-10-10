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
    Map<UUID, String> notificationTokens,
    String notificationToken,
    String registrationEvent,
    String depositEvent,
    String operationEvent,
    Fields fields) {

  public BrokerNotificationSettings {
    notificationTokens = notificationTokens == null ? Map.of() : Map.copyOf(notificationTokens);
    fields = fields == null ? new Fields(null, null, null, null) : fields;
  }

  /**
   * El valor del campo del evento que hace de un aviso el DE REGISTRO (`RN-SP-072`), o vacío si no
   * está configurado: entonces ningún aviso crea cuentas.
   */
  public Optional<String> registration() {
    return configurado(registrationEvent);
  }

  /** El evento del DEPÓSITO (`RN-SP-073`), o vacío: entonces ningún aviso confirma un FTD. */
  public Optional<String> deposit() {
    return configurado(depositEvent);
  }

  /** El evento de la OPERACIÓN (`RN-SP-074`), o vacío: entonces no se cuenta ninguna. */
  public Optional<String> operation() {
    return configurado(operationEvent);
  }

  private static Optional<String> configurado(String evento) {
    return evento == null || evento.isBlank() ? Optional.empty() : Optional.of(evento.trim());
  }

  /** Cómo llama el broker a cada dato del aviso. */
  public record Fields(String event, String account, String afftrack, String eventId) {
    public Fields {
      event = event == null || event.isBlank() ? "postback_name" : event.trim();
      account = account == null || account.isBlank() ? "trader_id" : account.trim();
      afftrack = afftrack == null || afftrack.isBlank() ? "afftrack" : afftrack.trim();
      eventId = eventId == null || eventId.isBlank() ? "event_id" : eventId.trim();
    }
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
