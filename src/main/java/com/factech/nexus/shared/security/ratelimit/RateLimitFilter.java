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
 * Límite de tasa de los endpoints públicos (`security.md` §5.5, issue #21).
 *
 * <p><b>Eran solo los de autenticación hasta el 08-09-2026</b>, cuando entró el hotlink
 * (`RF-PM-008`): la primera ruta pública **por decisión** y no por definición, y la primera que se
 * acota en un {@code GET}. Lo que allí se corta no es una ráfaga de credenciales sino el
 * <b>recorrido a ciegas de nombres de usuario</b>, que es lo único que le queda a quien sondea un
 * endpoint cuyos rechazos son todos el mismo {@code 404}.
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
  private static final String RECOVERY_CONFIRMATION = "/api/v1/auth/password-recovery/confirmation";

  /**
   * El registro por enlace (`RF-SP-045`, 09-09-2026).
   *
   * <p><b>Se acota por lo mismo que la recuperación y no por lo mismo que el refresco</b>: no es
   * que consulte la base, es que <b>ESCRIBE</b>. Sin cota, un origen puede crear cuentas en masa —
   * cada una con su membresía, su atribución y su cuenta de broker— y ensuciar el árbol comercial
   * sobre el que `CM` comisionará.
   */
  private static final String REGISTRATION = "/api/v1/auth/registration";

  /**
   * El hotlink es un <b>prefijo</b> y no una ruta, porque lleva dos variables (`RF-PM-008`).
   *
   * <p>Y ese prefijo es además <b>la llave del contador</b>, que es la mitad que importa: contar
   * por URI daría un cubo distinto a cada nombre de usuario probado, y quien barre el padrón no
   * repite ninguno. La cota que se pretende —una por origen sobre toda la familia— solo existe si
   * las mil rutas distintas de un recorrido a ciegas caen en el mismo sitio.
   */
  private static final String HOTLINKS = "/api/v1/hotlinks/";

  /**
   * Los catálogos que se leen <b>sin token</b>: tres desde el 08-09-2026 y <b>cuatro</b> desde el
   * 09-09-2026, cuando el de métodos de pago se abrió (`RN-MV-024`).
   *
   * <p><b>Se acotan por lo mismo que el refresco y no por lo mismo que el hotlink</b>: aquí no hay
   * nada que sondear —son listas de opciones que no identifican a nadie, y la respuesta es la misma
   * para todo el mundo—, pero son rutas públicas que <b>consultan la base en cada llamada</b>. Lo
   * que se corta es el bucle.
   *
   * <p><b>Comparten política y NO comparten cubo</b>: el ámbito es la ruta, de modo que agotar el
   * de países no deja sin brokers —ni sin métodos de pago— a quien está rellenando el mismo
   * formulario. Es la razón de que el cuarto no necesite política propia: la naturaleza es idéntica
   * y lo único que hace falta es <b>otro cubo</b>.
   */
  private static final String[] CATALOGOS_PUBLICOS = {
    "/api/v1/countries", "/api/v1/document-types", "/api/v1/brokers", "/api/v1/payment-methods"
  };

  /**
   * Las reseñas de un producto (`RF-PM-012`, 14-09-2026): <b>el número de los catálogos y la llave
   * del hotlink</b>.
   *
   * <p>La política es {@code public-catalog} —120 por minuto y por origen— porque la naturaleza es
   * la misma: una ruta pública que consulta la base y no identifica a nadie que no haya decidido
   * publicar su nombre. La llave <b>no</b> es la ruta sino la <b>familia</b>: la ruta lleva el
   * identificador del producto, y contar por URI daría un cubo por producto — quien recorriera
   * identificadores al azar buscando cuáles responden {@code 200} no toparía jamás. Con la familia,
   * mil identificadores distintos caen en el mismo sitio. No hace falta política nueva: lo distinto
   * es la llave, no el número.
   */
  private static final String RESENAS_PREFIJO = "/api/v1/products/";

  private static final String RESENAS_SUFIJO = "/comments";

  private static final String RESENAS_FAMILIA = "/api/v1/products/*/comments";

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

    Regla regla = reglaDe(peticion);
    if (!ajustes.enabled() || regla == null || regla.politica() == null) {
      cadena.doFilter(peticion, respuesta);
      return;
    }

    Politica politica = regla.politica();
    HttpServletRequest envuelta =
        politica.acotaPorIdentidad() ? new CachedBodyRequest(peticion, TOPE_DEL_CUERPO) : peticion;

    // La ruta es lo que se DEVUELVE —el `instance` del problema—; el ámbito es
    // por lo que se CUENTA. Coinciden en los cuatro endpoints de sesión y no
    // en el hotlink, que es justo donde la diferencia sostiene la cota.
    String ruta = peticion.getRequestURI();
    String ambito = regla.ambito();
    String origen = ips.resolve(peticion);

    // El origen primero: es la cota que protege del rociado de contraseñas, y
    // no exige leer el cuerpo.
    if (politica.acotaPorOrigen() && origen != null) {
      RateLimitLedger.Veredicto veredicto =
          contador.registrar(
              llave(ambito, "ip", origen),
              politica.porOrigen(),
              politica.ventana(),
              politica.penalizacion());

      if (!veredicto.admitida()) {
        rechazar(
            respuesta, veredicto, peticion, ruta, ambito, "origen", origen, politica.ventana());
        return;
      }
    }

    if (politica.acotaPorIdentidad()) {
      String identidad = identidadDe(envuelta);
      if (identidad != null) {
        RateLimitLedger.Veredicto veredicto =
            contador.registrar(
                llave(ambito, "id", identidad),
                politica.porIdentidad(),
                politica.ventana(),
                politica.penalizacion());

        if (!veredicto.admitida()) {
          // La identidad NO viaja al registro ni a la respuesta: decir «esta
          // cuenta está limitada» confirmaría que existe. Lo que se registra es
          // que hubo una ráfaga contra una identidad, no cuál.
          rechazar(
              respuesta,
              veredicto,
              peticion,
              ruta,
              ambito,
              "identidad",
              origen,
              politica.ventana());
          return;
        }
      }
    }

    cadena.doFilter(envuelta, respuesta);
  }

  /**
   * Qué política aplica a esta petición.
   *
   * <p><b>La recuperación tiene dos cotas y no una, y no son iguales.</b> La solicitud se acota por
   * identidad <b>y</b> por origen: es la única operación pública que provoca un envío saliente, y
   * sin cota permite inundar de correos a una persona real. La confirmación se acota <b>solo por
   * origen</b>, porque su cuerpo no lleva identidad ninguna —lleva un permiso— y lo que hay que
   * cortar ahí es probar permisos al azar.
   */
  /**
   * {@code /api/v1/products/{id}/comments} y nada más: un solo segmento entre el prefijo y el
   * sufijo. {@code /comments/mine} no termina en el sufijo, y {@code /products/available} no lo
   * lleva.
   */
  private static boolean esListaDeResenas(String ruta) {
    if (!ruta.startsWith(RESENAS_PREFIJO) || !ruta.endsWith(RESENAS_SUFIJO)) {
      return false;
    }
    String medio =
        ruta.substring(RESENAS_PREFIJO.length(), ruta.length() - RESENAS_SUFIJO.length());
    return !medio.isEmpty() && medio.indexOf('/') < 0;
  }

  private Regla reglaDe(HttpServletRequest peticion) {
    String ruta = peticion.getRequestURI();

    // El hotlink es la única cota que NO protege un endpoint de autenticación,
    // y por eso es la única que se mira en un GET (`RF-PM-008`). Se cuenta por
    // el prefijo y no por la ruta: ver `HOTLINKS`.
    if ("GET".equalsIgnoreCase(peticion.getMethod())) {
      if (ruta.startsWith(HOTLINKS)) {
        return new Regla(HOTLINKS, ajustes.hotlink());
      }
      for (String catalogo : CATALOGOS_PUBLICOS) {
        if (catalogo.equals(ruta)) {
          // El ámbito es la ruta y no una familia: son tres cubos, para que
          // agotar uno no deje sin los otros al mismo formulario.
          return new Regla(ruta, ajustes.publicCatalog());
        }
      }
      if (esListaDeResenas(ruta)) {
        // La FAMILIA como llave, como en el hotlink: ver `RESENAS_FAMILIA`.
        return new Regla(RESENAS_FAMILIA, ajustes.publicCatalog());
      }
      return null;
    }

    if (!"POST".equalsIgnoreCase(peticion.getMethod())) {
      return null;
    }
    if (LOGIN.equals(ruta)) {
      return new Regla(ruta, ajustes.login());
    }
    if (REFRESH.equals(ruta)) {
      return new Regla(ruta, ajustes.refresh());
    }
    // El literal más largo primero: la confirmación cuelga de la ruta de
    // solicitud, y comparar al revés le aplicaría la cota equivocada.
    if (RECOVERY_CONFIRMATION.equals(ruta)) {
      return new Regla(ruta, ajustes.recoveryConfirmation());
    }
    if (REGISTRATION.equals(ruta)) {
      return new Regla(ruta, ajustes.registration());
    }
    if (RECOVERY.equals(ruta)) {
      return new Regla(ruta, ajustes.recovery());
    }
    return null;
  }

  /**
   * La cota que aplica, y <b>bajo qué llave se cuenta</b>.
   *
   * <p>Existe porque en el hotlink las dos cosas dejaron de coincidir: hasta él, contar «por la
   * ruta» era contar por el endpoint, porque cada endpoint acotado tenía una ruta fija. Con
   * variables en el camino, seguir contando por la URI convertiría la cota en una por enlace — que
   * es exactamente la que no sirve para lo que existe.
   *
   * @param ambito la llave del contador: la ruta en los cuatro endpoints de sesión, el prefijo de
   *     la familia en el hotlink
   * @param politica la cota; <b>puede ser nula</b> si la configuración no la declara, y entonces
   *     esa ruta no se acota
   */
  private record Regla(String ambito, Politica politica) {}

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
      HttpServletResponse respuesta,
      RateLimitLedger.Veredicto veredicto,
      HttpServletRequest peticion,
      String ruta,
      String ambito,
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

    auditar(peticion.getMethod(), ambito, eje, origen, ventana);
  }

  /**
   * Un evento por ventana y por llave, no uno por petición rechazada.
   *
   * <p>Y con transacción propia ({@code recordSecurity}), porque aquí no hay ninguna transacción de
   * negocio a la que unirse: la petición no llegó a ejecutarse.
   */
  private void auditar(String metodo, String ambito, String eje, String origen, Duration ventana) {
    if (!contador.debeAvisar(llave(ambito, eje, origen == null ? "-" : origen), ventana)) {
      return;
    }

    try {
      auditoria.recordSecurity(
          new SecurityEvent(
              SecurityEventType.RATE_LIMIT_EXCEEDED,
              Severity.ALTA,
              Outcome.FAILURE,
              null,
              // El ÁMBITO y no la ruta: en el hotlink la ruta lleva el nombre
              // de usuario probado, y el registro guardaría uno al azar de los
              // mil de un recorrido — un dato personal a cambio de nada.
              Map.of("operation", metodo + " " + ambito, "limit", eje)));
    } catch (RuntimeException fallo) {
      // Un fallo al auditar no debe convertir un 429 legible en un 500 opaco.
      // No se ignora: se registra con su correlación, que es lo que permite
      // detectar que la auditoría dejó de escribir.
      LOG.error("No se pudo registrar el rechazo por límite de tasa en {}", ambito, fallo);
    }
  }

  private static String correlacion() {
    return RequestContext.current()
        .map(contexto -> contexto.correlationId().toString())
        .orElse(null);
  }
}
