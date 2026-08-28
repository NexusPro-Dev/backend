package com.factech.nexus.shared.notification;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Envío por Resend (**D-23**, cerrada el 26-08-2026).
 *
 * <p><b>Por qué el API de Resend y no SMTP.</b> Los dos habrían servido, y la diferencia está en lo
 * que se paga por operarlos: SMTP obliga a gestionar credenciales, puertos salientes que muchas
 * redes bloquean, y una cola propia para los reintentos. Aquí la entrega, los reintentos y los
 * rebotes los lleva el proveedor, y lo que queda en este lado es una petición HTTP.
 *
 * <p><b>No lanza nunca</b>, y no es descuido. `RF-SP-040` `plan.md` §7 lo exige: el envío corre
 * fuera de toda transacción y fuera de la respuesta, de modo que para cuando esto se ejecuta el
 * permiso ya está emitido y la respuesta ya viajó. Propagar el fallo no lo desharía; solo rompería
 * el hilo que lo intentó.
 *
 * <p><b>Lo que sí hace es dejar constancia</b>, porque un envío que no ocurre y no se registra es
 * indistinguible de uno que sí ocurrió — y esa confusión se paga cuando alguien reporta que nunca
 * recibió el correo.
 *
 * <p><b>Nada del contenido llega al registro.</b> Ni el cuerpo, ni el permiso, ni el destinatario:
 * un mensaje de recuperación lleva la llave de una cuenta, y los registros se copian a sitios que
 * quien los escribe no controla. Se registra qué falló y a qué operación pertenecía.
 */
@Component
public class ResendNotificationSender implements NotificationSender {

  private static final Logger LOG = LoggerFactory.getLogger(ResendNotificationSender.class);

  private final NotificationSettings ajustes;
  private final RestClient http;

  public ResendNotificationSender(NotificationSettings ajustes, RestClient.Builder constructor) {
    this.ajustes = ajustes;
    this.http = constructor.baseUrl(ajustes.baseUrl()).build();

    if (!ajustes.utilizable()) {
      // Al arrancar y no al primer envío: quien despliega puede corregirlo
      // ahora, y no cuando alguien olvide su contraseña un domingo.
      LOG.warn(
          "El envío saliente está apagado o incompleto: NOTIFICATION_ENABLED, RESEND_API_KEY y"
              + " NOTIFICATION_FROM. La recuperación de contraseña emitirá permisos que nadie"
              + " recibirá.");
    }
  }

  @Override
  public void send(Notification notificacion) {
    if (!ajustes.utilizable()) {
      LOG.warn(
          "Envío omitido: el canal saliente no está configurado. asunto={}", notificacion.asunto());
      return;
    }

    try {
      http.post()
          .uri("/emails")
          .header("Authorization", "Bearer " + ajustes.apiKey())
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              Map.of(
                  "from", ajustes.from(),
                  "to", new String[] {notificacion.destinatario()},
                  "subject", notificacion.asunto(),
                  "text", notificacion.cuerpo()))
          .retrieve()
          .toBodilessEntity();

    } catch (RuntimeException fallo) {
      // El mensaje del proveedor SÍ se registra —dice si es la clave, el
      // dominio sin verificar o la cuota— y el contenido NO.
      LOG.error(
          "No se pudo enviar la notificación «{}». El proveedor respondió: {}",
          notificacion.asunto(),
          fallo.getMessage());
    }
  }
}
