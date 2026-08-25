package com.factech.nexus.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registra <b>toda</b> petición en {@code request_log} (Art. XV.2 y XV.4, issue #23).
 *
 * <p><b>Toda</b> significa toda: la que triunfa, la que devuelve `404`, la que se rechaza por
 * formato, la que topa con el límite de tasa y la que revienta. Ese era el hueco — el manejador
 * global decide <b>no</b> auditar los `404`, los `400` de formato y las peticiones mal dirigidas
 * «porque `request_log` ya lo cubre», y la tabla no existía: no las cubría nadie. Un barrido de
 * rutas, que es el reconocimiento previo a un ataque, no dejaba rastro en ninguna parte.
 *
 * <p><b>Dónde va en la cadena, y por qué ahí.</b> Después de {@link CorrelationFilter}, para que la
 * fila lleve la correlación que el cliente recibe en la respuesta y la IP ya resuelta; y
 * <b>envolviendo</b> al límite de tasa, para que un rechazo por ráfaga también quede registrado —
 * si fuera al revés, el ataque más ruidoso sería el único invisible.
 *
 * <p><b>Se escribe después de la respuesta y nunca puede tumbarla</b> (Art. XV.7). Un fallo al
 * registrar se anota en el log de aplicación con su correlación y ahí termina: la petición ya se
 * respondió, y convertir un apunte fallido en un `500` sería exactamente lo que ese artículo
 * prohíbe.
 *
 * <p><b>Ni cuerpo ni cabeceras.</b> Ahí viajan contraseñas y tokens, y ningún saneador es de fiar
 * sobre un contenido arbitrario: la única forma segura de no registrar un secreto es no registrar
 * el cuerpo (Art. VI.5).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class RequestLogFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(RequestLogFilter.class);

  private final RequestLogWriter registro;

  public RequestLogFilter(RequestLogWriter registro) {
    this.registro = registro;
  }

  /**
   * La salud queda fuera.
   *
   * <p>La sonda del contenedor la llama cada diez segundos: registrarla llenaría la tabla que más
   * crece del sistema con ocho mil filas diarias que no responden ninguna pregunta. No es una
   * petición de nadie, es el orquestador comprobando que el proceso vive.
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest peticion) {
    return peticion.getRequestURI().startsWith("/actuator/health");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
      throws ServletException, IOException {

    long inicio = System.nanoTime();
    try {
      cadena.doFilter(peticion, respuesta);
    } finally {
      long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
      anotar(peticion, respuesta, duracionMs);
      // Los hilos del contenedor se reutilizan: un actor olvidado le
      // atribuiria a la siguiente peticion el de esta.
      RequestActor.unbind();
    }
  }

  /**
   * El apunte, en el {@code finally}: también cuando la petición terminó en excepción.
   *
   * <p>El actor <b>no se le pregunta al contexto de seguridad</b>, y ese detalle costó una prueba
   * en rojo: Spring Security limpia el suyo en su propio {@code finally}, que corre <b>antes</b>
   * que este por estar por dentro. Preguntándole aquí, toda petición del sistema quedaría
   * registrada como anónima — con filas que existen y parecen correctas. Lo apunta {@code
   * ActorCaptureFilter} mientras todavía se conoce, y aquí solo se recoge.
   */
  private void anotar(HttpServletRequest peticion, HttpServletResponse respuesta, long duracionMs) {
    try {
      RequestContext contexto = RequestContext.current().orElse(null);
      UUID correlacion = contexto == null ? UUID.randomUUID() : contexto.correlationId();

      registro.registrar(
          correlacion,
          RequestActor.current().orElse(null),
          peticion.getMethod(),
          peticion.getRequestURI(),
          peticion.getQueryString(),
          respuesta.getStatus(),
          duracionMs,
          contexto == null ? null : contexto.ipAddress(),
          contexto == null ? null : contexto.userAgent());

    } catch (RuntimeException fallo) {
      // Art. XV.7: registrar no puede alterar el resultado de la operación. La
      // respuesta ya salió; lo único sensato es dejar constancia de que el
      // registro dejó de escribir, que es lo que permite detectarlo.
      LOG.error(
          "No se pudo registrar la petición {} {}",
          peticion.getMethod(),
          peticion.getRequestURI(),
          fallo);
    }
  }
}
