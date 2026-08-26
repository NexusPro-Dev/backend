package com.factech.nexus.shared.notification;

import java.time.Duration;
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
 */
@ConfigurationProperties(prefix = "nexus.notification")
public record NotificationSettings(
    boolean enabled, String apiKey, String from, String baseUrl, Duration timeout) {

  public NotificationSettings {
    baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.resend.com" : baseUrl;
    timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
  }

  /** ¿Hay con qué enviar? Enabled sin credencial es una configuración a medias, no un envío. */
  public boolean utilizable() {
    return enabled && apiKey != null && !apiKey.isBlank() && from != null && !from.isBlank();
  }
}
