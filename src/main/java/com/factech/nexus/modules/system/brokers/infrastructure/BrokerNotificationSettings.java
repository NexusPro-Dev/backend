package com.factech.nexus.modules.system.brokers.infrastructure;

import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El secreto con el que los brokers autentican sus avisos, y cómo se leen (`RF-SP-078` · `T-02`,
 * `RN-SP-066`).
 *
 * <p><b>Uno solo, en el entorno</b> (`RN-SP-069`): hasta el 13-10-2026 había además uno por broker,
 * para la ruta con su nombre, que se retiró (`plan.md` §16). <b>No va a la base</b> porque el
 * catálogo se puebla por migración (`RN-SP-039`) y un secreto en una migración acaba en el
 * repositorio. Vacío es <b>sin configurar</b>: nadie puede avisar.
 */
@ConfigurationProperties(prefix = "nexus.brokers")
public record BrokerNotificationSettings(
    String notificationToken,
    String registrationEvent,
    String depositEvent,
    String operationEvent,
    Fields fields) {

  public BrokerNotificationSettings {
    fields = fields == null ? new Fields(null, null, null, null, null, null, null) : fields;
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

  /**
   * Cómo llama el broker a cada dato del aviso. {@code country}, {@code amount} y {@code currency}
   * desde el 15-10-2026 (`RN-SP-077`): el del importe se configura porque ningún aviso real de
   * depósito lo ha mostrado todavía.
   */
  public record Fields(
      String event,
      String account,
      String afftrack,
      String eventId,
      String country,
      String amount,
      String currency) {
    public Fields {
      event = nombre(event, "postback_name");
      account = nombre(account, "trader_id");
      afftrack = nombre(afftrack, "afftrack");
      eventId = nombre(eventId, "event_id");
      country = nombre(country, "country_code");
      amount = nombre(amount, "amount");
      currency = nombre(currency, "currency");
    }

    private static String nombre(String configurado, String porOmision) {
      return configurado == null || configurado.isBlank() ? porOmision : configurado.trim();
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
}
