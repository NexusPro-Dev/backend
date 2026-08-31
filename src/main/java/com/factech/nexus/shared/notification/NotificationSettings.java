package com.factech.nexus.shared.notification;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del envío saliente (**D-23**, cerrada el 26-08-2026 con Resend).
 *
 * <p><b>La clave llega por variable de entorno y no tiene valor por omisión</b> (Art. IX.1, IX.5).
 * Sin ella el envío queda apagado y lo dice al arrancar, en lugar de fallar en silencio la primera
 * vez que alguien olvide su contraseña — que es cuando nadie está mirando.
 *
 * @param enabled si el envío está activo. Apagado en las pruebas, que verifican el permiso y no el
 *     correo
 * @param apiKey credencial de Resend, {@code re_...}
 * @param from remitente. Debe pertenecer a un dominio verificado en Resend, o el proveedor rechaza
 *     el envío con un {@code 403} que no se descubre hasta producción
 * @param baseUrl del API. Configurable para poder apuntarlo a un doble en las pruebas
 * @param timeout tope de espera. Corto a propósito: el envío corre fuera de la respuesta, pero no
 *     debe dejar un hilo colgado indefinidamente
 * @param templates qué plantilla alojada le toca a cada tipo de mensaje, por su código. Añadir una
 *     plantilla nueva es añadir una entrada aquí y su variable de entorno, sin tocar código
 */
@ConfigurationProperties(prefix = "nexus.notification")
public record NotificationSettings(
    boolean enabled,
    String apiKey,
    String from,
    String baseUrl,
    Duration timeout,
    Map<String, String> templates) {

  public NotificationSettings {
    baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.resend.com" : baseUrl;
    timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    templates = templates == null ? Map.of() : Map.copyOf(templates);
  }

  /** ¿Hay con qué enviar? Enabled sin credencial es una configuración a medias, no un envío. */
  public boolean utilizable() {
    return enabled && apiKey != null && !apiKey.isBlank() && from != null && !from.isBlank();
  }

  /**
   * La plantilla que le toca a este tipo, si hay alguna configurada.
   *
   * <p><b>Una entrada en blanco cuenta como ausente</b>, y esa es la razón de que esto no sea un
   * {@code get} pelado: cada plantilla se declara en `application.yml` con un valor por omisión
   * vacío, de modo que en un entorno que no la configure la clave <b>existe</b> y su valor es la
   * cadena vacía. Tratarla como presente mandaría al proveedor una plantilla llamada «», y el
   * correo se perdería con un {@code 422} que nadie mira — en lugar de salir en texto plano, que es
   * lo que debe pasar.
   */
  public Optional<String> plantillaDe(NotificationKind tipo) {
    if (tipo == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(templates.get(tipo.codigo())).filter(id -> !id.isBlank());
  }
}
