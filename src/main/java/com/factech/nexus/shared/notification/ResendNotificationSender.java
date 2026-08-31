package com.factech.nexus.shared.notification;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
 * <p><b>Es el único sitio que sabe que las plantillas existen.</b> Viven alojadas en Resend y se
 * eligen por configuración, de modo que quien pide el envío dice de qué tipo es su mensaje y no con
 * qué se dibuja. Añadir una plantilla nueva es declarar su variable de entorno; no se toca esta
 * clase.
 *
 * <p><b>Sin plantilla configurada sale el texto plano, y eso es una garantía y no un descuido.</b>
 * Un despliegue al que le falte la variable manda un correo sin estilos; uno que fallara aquí no
 * mandaría ninguno, y quien esperase su código para entrar se quedaría fuera por un problema de
 * maquetación. El proveedor <b>rechaza</b> una petición que lleve plantilla y texto a la vez, así
 * que es lo uno o lo otro y nunca los dos.
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
 * <p><b>Nada del contenido llega al registro.</b> Ni el cuerpo, ni el permiso, ni el destinatario,
 * ni las variables: un mensaje de recuperación lleva la llave de una cuenta, y los registros se
 * copian a sitios que quien los escribe no controla. Se registra qué falló, a qué operación
 * pertenecía y con qué plantilla se intentó, que no es secreto y es lo primero que hay que mirar.
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

    Optional<String> plantilla = ajustes.plantillaDe(notificacion.tipo());

    try {
      http.post()
          .uri("/emails")
          .header("Authorization", "Bearer " + ajustes.apiKey())
          .contentType(MediaType.APPLICATION_JSON)
          .body(cuerpoDe(notificacion, plantilla))
          .retrieve()
          .toBodilessEntity();

    } catch (RuntimeException fallo) {
      // El mensaje del proveedor SÍ se registra —dice si es la clave, el
      // dominio sin verificar, la plantilla inexistente o la cuota— y el
      // contenido NO.
      LOG.error(
          "No se pudo enviar la notificación «{}» (plantilla={}). El proveedor respondió: {}",
          notificacion.asunto(),
          plantilla.orElse("ninguna, texto plano"),
          fallo.getMessage());
    }
  }

  /**
   * La petición: con plantilla o con texto, nunca con las dos.
   *
   * <p>El asunto se manda siempre y también cuando hay plantilla: el que trae la plantilla sirve de
   * respaldo, pero dejar que lo decida el proveedor pondría el texto de un correo del sistema fuera
   * del alcance de quien despliega.
   */
  private Map<String, Object> cuerpoDe(Notification notificacion, Optional<String> plantilla) {
    Map<String, Object> peticion = new LinkedHashMap<>();
    peticion.put("from", ajustes.from());
    peticion.put("to", new String[] {notificacion.destinatario()});
    peticion.put("subject", notificacion.asunto());

    plantilla.ifPresentOrElse(
        id -> peticion.put("template", Map.of("id", id, "variables", notificacion.variables())),
        () -> peticion.put("text", notificacion.cuerpo()));

    return peticion;
  }
}
