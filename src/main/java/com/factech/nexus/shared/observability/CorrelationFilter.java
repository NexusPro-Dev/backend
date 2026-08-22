package com.factech.nexus.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Publica el contexto de origen de cada petición (`architecture.md` §6.6.1, §9).
 *
 * <p>Corre <b>antes que la cadena de seguridad</b> —de ahí el orden más alto— porque una denegación
 * de autorización es de los eventos que hay que auditar, y su fila necesita la correlación y la IP
 * igual que las demás. Un filtro colocado después dejaría precisamente los rechazos sin origen.
 *
 * <p><b>La correlación se acepta del cliente si viene, y se genera si no.</b> Aceptarla permite
 * seguir una operación que atraviesa varios sistemas con un solo identificador; que sea el cliente
 * quien la proponga no es un riesgo, porque no concede nada: es una etiqueta de traza, no una
 * credencial. Un valor que no sea un UUID se descarta y se genera uno nuevo, para que la columna
 * {@code correlation_id} de la auditoría —que es de tipo {@code uuid}— no pueda recibir basura.
 *
 * <p>El identificador viaja de vuelta en la respuesta para que quien reporte un error pueda citarlo
 * y el equipo localizarlo en {@code request_log} (Art. XV.1), y se publica en el {@code MDC} para
 * que toda línea de log de la petición lo lleve.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationFilter extends OncePerRequestFilter {

  /** Nombre de la cabecera, de entrada y de salida. */
  public static final String CABECERA = "X-Correlation-Id";

  /** Clave en el contexto de diagnóstico del log. */
  public static final String CLAVE_MDC = "correlationId";

  private final ClientIpResolver ips;

  public CorrelationFilter(ClientIpResolver ips) {
    this.ips = ips;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
      throws ServletException, IOException {

    UUID correlacion = leerOGenerar(peticion.getHeader(CABECERA));

    RequestContext.bind(
        new RequestContext(correlacion, ips.resolve(peticion), peticion.getHeader("User-Agent")));
    MDC.put(CLAVE_MDC, correlacion.toString());
    respuesta.setHeader(CABECERA, correlacion.toString());

    try {
      cadena.doFilter(peticion, respuesta);
    } finally {
      // Los hilos del contenedor se reutilizan: un contexto olvidado le
      // atribuiría a la siguiente petición la correlación y la IP de esta.
      MDC.remove(CLAVE_MDC);
      RequestContext.unbind();
    }
  }

  private static UUID leerOGenerar(String recibida) {
    if (recibida == null || recibida.isBlank()) {
      return UUID.randomUUID();
    }
    try {
      return UUID.fromString(recibida.trim());
    } catch (IllegalArgumentException noEsUuid) {
      return UUID.randomUUID();
    }
  }
}
