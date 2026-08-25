package com.factech.nexus.shared.security.ratelimit;

import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ProblemKind;
import com.factech.nexus.shared.observability.ClientIpResolver;
import com.factech.nexus.shared.observability.RequestContext;
import com.factech.nexus.shared.security.ratelimit.RateLimitSettings.Politica;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite de tasa de los endpoints públicos de autenticación (`security.md` §5.5, issue #21).
 *
 * <p><b>Es un filtro y no una comprobación dentro del caso de uso</b>, y esa es la decisión que
 * gobierna el diseño. Lo que se quiere acotar es <b>el trabajo</b> que una ráfaga provoca, y desde
 * el servicio ya se pagaron la deserialización, la resolución de la cuenta y —en el refresco— una
 * consulta a la base de datos. Aquí se corta antes de todo eso.
 *
 * <p><b>Corre después de la correlación y antes de la seguridad.</b> Después, porque una respuesta
 * de rechazo también lleva su {@code X-Correlation-Id} y su IP resuelta; antes, porque estos
 * endpoints son públicos y no hay autenticación que esperar.
 *
 * <p><b>Qué acota, y por qué no es lo mismo que el bloqueo por intentos fallidos.</b> Aquel cuenta
 * fallos de credencial contra <b>una cuenta</b> y la protege; este cuenta <b>peticiones</b>,
 * acierten o fallen, y protege al sistema. Sin el segundo, el rociado de contraseñas —una sola
 * contraseña contra mil identidades— no dispara ningún bloqueo porque deja un solo fallo por
 * cuenta; y provocar bloqueos ajenos a propósito es una denegación de servicio contra sus
 * titulares.
 *
 * <p><b>La identidad se lee del cuerpo</b> en el inicio de sesión, y por eso la petición viaja
 * envuelta ({@link CachedBodyRequest}): un {@code InputStream} solo se lee una vez, y quien lo
 * consuma aquí se lo quita al controlador. Se envuelve <b>solo</b> en las rutas que lo necesitan y
 * con un tope de tamaño, para no cargar en memoria el cuerpo de cualquier petición del sistema.
 *
 * <p><b>El evento de auditoría se emite una vez por ventana y no por petición rechazada.</b> Una
 * ráfaga de mil peticiones por segundo escribiría mil filas por segundo en {@code
 * audit_security_log}: la defensa se convertiría en el ataque, y el registro que sirve para
 * investigar quedaría sepultado justo cuando hace falta leerlo.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

  private static final String LOGIN = "/api/v1/auth/login";
  private static final String REFRESH = "/api/v1/auth/refresh";
  private static final String RECOVERY = "/api/v1/auth/password-recovery";

  /** Un cuerpo de autenticación son decenas de bytes; esto es holgura, no un límite funcional. */
  private static final int TOPE_DEL_CUERPO = 8 * 1024;

  private final RateLimitSettings ajustes;
  private final RateLimitLedger contador;
  private final ClientIpResolver ips;
  private final AuditWriter auditoria;
  private final ObjectMapper json;

  public RateLimitFilter(
      RateLimitSettings ajustes,
      RateLimitLedger contador,
      ClientIpResolver ips,
      AuditWriter auditoria,
      ObjectMapper json) {

    this.ajustes = ajustes;
    this.contador = contador;
    this.ips = ips;
    this.auditoria = auditoria;
    this.json = json;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
      throws ServletException, IOException {

    Politica politica = politicaDe(peticion);
    if (!ajustes.enabled() || politica == null) {
      cadena.doFilter(peticion, respuesta);
      return;
    }

    HttpServletRequest envuelta =
        politica.acotaPorIdentidad() ? new CachedBodyRequest(peticion, TOPE_DEL_CUERPO) : peticion;

    String ruta = peticion.getRequestURI();
    String origen = ips.resolve(peticion);

    // El origen primero: es la cota que protege del rociado de contraseñas, y
    // no exige leer el cuerpo.
    if (politica.acotaPorOrigen() && origen != null) {
      RateLimitLedger.Veredicto veredicto =
          contador.registrar(llave(ruta, "ip", origen), politica.porOrigen(), politica.ventana());

      if (!veredicto.admitida()) {
        rechazar(envuelta, respuesta, veredicto, ruta, "origen", origen, politica.ventana());
        return;
      }
    }

    if (politica.acotaPorIdentidad()) {
      String identidad = identidadDe(envuelta);
      if (identidad != null) {
        RateLimitLedger.Veredicto veredicto =
            contador.registrar(
                llave(ruta, "id", identidad), politica.porIdentidad(), politica.ventana());

        if (!veredicto.admitida()) {
          // La identidad NO viaja al registro ni a la respuesta: decir «esta
          // cuenta está limitada» confirmaría que existe. Lo que se registra es
          // que hubo una ráfaga contra una identidad, no cuál.
          rechazar(envuelta, respuesta, veredicto, ruta, "identidad", origen, politica.ventana());
          return;
        }
      }
    }

    cadena.doFilter(envuelta, respuesta);
  }

  /**
   * Qué política aplica a esta petición.
   *
   * <p>La de recuperación está declarada y hoy no casa con nada: su endpoint no existe todavía
   * (`RF-SP-040`, bloqueado por **D-23**). Se deja escrita para que el día que exista no dependa de
   * que alguien recuerde añadirla.
   */
  private Politica politicaDe(HttpServletRequest peticion) {
    if (!"POST".equalsIgnoreCase(peticion.getMethod())) {
      return null;
    }
    String ruta = peticion.getRequestURI();
    if (LOGIN.equals(ruta)) {
      return ajustes.login();
    }
    if (REFRESH.equals(ruta)) {
      return ajustes.refresh();
    }
    if (RECOVERY.equals(ruta)) {
      return ajustes.recovery();
    }
    return null;
  }

  /**
   * El identificador que la petición declara, en minúsculas.
   *
   * <p>En minúsculas porque {@code JPEREZ} y {@code jperez} son la misma cuenta para el inicio de
   * sesión: sin normalizar, alternar la caja duplicaría la cota.
   *
   * <p>Un cuerpo ilegible no es un error de este filtro — lo rechazará la validación del
   * controlador con un {@code 400}—, de modo que aquí simplemente no hay identidad que acotar.
   */
  private String identidadDe(HttpServletRequest peticion) {
    try {
      byte[] cuerpo = peticion.getInputStream().readAllBytes();
      if (cuerpo.length == 0) {
        return null;
      }
      var arbol = json.readTree(cuerpo);
      for (String campo : List.of("identifier", "email", "username")) {
        var valor = arbol.get(campo);
        if (valor != null && valor.isTextual() && !valor.asText().isBlank()) {
          return valor.asText().trim().toLowerCase(Locale.ROOT);
        }
      }
      return null;
    } catch (IOException | RuntimeException cuerpoIlegible) {
      return null;
    }
  }

  private static String llave(String ruta, String tipo, String valor) {
    return ruta + "|" + tipo + "|" + valor;
  }

  /**
   * Escribe el {@code 429} con la misma forma que el resto de los errores de la API.
   *
   * <p>La escribe este filtro y no {@code GlobalExceptionHandler} porque aquí no hay excepción que
   * propagar: estamos fuera del despachador, y una excepción lanzada desde un filtro no llega al
   * {@code @RestControllerAdvice}. Lo que sí se conserva es el contrato — mismo {@code type}, mismo
   * {@code correlationId}, mismo {@code errors} — para que el cliente no tenga que distinguir dos
   * formatos de error según quién lo produjo.
   */
  private void rechazar(
      HttpServletRequest peticion,
      HttpServletResponse respuesta,
      RateLimitLedger.Veredicto veredicto,
      String ruta,
      String eje,
      String origen,
      Duration ventana)
      throws IOException {

    respuesta.setStatus(ProblemKind.DEMASIADAS_PETICIONES.status().value());
    respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
    respuesta.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(veredicto.esperaSegundos()));

    Map<String, Object> problema = new LinkedHashMap<>();
    problema.put("type", ProblemKind.DEMASIADAS_PETICIONES.type());
    problema.put("title", ProblemKind.DEMASIADAS_PETICIONES.title());
    problema.put("status", ProblemKind.DEMASIADAS_PETICIONES.status().value());
    problema.put("detail", "Ha realizado demasiadas peticiones. Espere antes de reintentar.");
    problema.put("instance", ruta);
    problema.put("correlationId", correlacion());
    // El cliente DESCUENTA los segundos; un texto con «vuelva en dos minutos»
    // es cierto al serializarse y deja de serlo enseguida.
    problema.put("retryAfterSeconds", veredicto.esperaSegundos());
    problema.put("errors", List.of());

    respuesta.getWriter().write(json.writeValueAsString(problema));

    auditar(ruta, eje, origen, ventana);
  }

  /**
   * Un evento por ventana y por llave, no uno por petición rechazada.
   *
   * <p>Y con transacción propia ({@code recordSecurity}), porque aquí no hay ninguna transacción de
   * negocio a la que unirse: la petición no llegó a ejecutarse.
   */
  private void auditar(String ruta, String eje, String origen, Duration ventana) {
    if (!contador.debeAvisar(llave(ruta, eje, origen == null ? "-" : origen), ventana)) {
      return;
    }

    try {
      auditoria.recordSecurity(
          new SecurityEvent(
              SecurityEventType.RATE_LIMIT_EXCEEDED,
              Severity.ALTA,
              Outcome.FAILURE,
              null,
              Map.of("operation", "POST " + ruta, "limit", eje)));
    } catch (RuntimeException fallo) {
      // Un fallo al auditar no debe convertir un 429 legible en un 500 opaco.
      // No se ignora: se registra con su correlación, que es lo que permite
      // detectar que la auditoría dejó de escribir.
      LOG.error("No se pudo registrar el rechazo por límite de tasa en {}", ruta, fallo);
    }
  }

  private static String correlacion() {
    return RequestContext.current()
        .map(contexto -> contexto.correlationId().toString())
        .orElse(null);
  }
}
