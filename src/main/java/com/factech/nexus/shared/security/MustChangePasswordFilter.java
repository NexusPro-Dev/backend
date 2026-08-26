package com.factech.nexus.shared.security;

import com.factech.nexus.shared.error.ProblemKind;
import com.factech.nexus.shared.observability.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Retiene a quien tiene pendiente el cambio obligatorio de contraseña (`RF-SP-034` · `FA-002` ·
 * `T-12`).
 *
 * <p>`RF-SP-034` autentica a esa persona a propósito —rechazarla la dejaría sin poder cambiar la
 * contraseña, porque necesita una sesión para hacerlo— y le entrega un token con el claim {@code
 * mcp} en verdadero. <b>Este filtro es la otra mitad de esa decisión</b>: sin él, el claim viaja,
 * la respuesta del inicio de sesión advierte, y no restringe absolutamente nada.
 *
 * <p><b>Se decide con el claim y no con la base de datos.</b> Comprobar {@code
 * users.provisional_password_expires_at} en cada petición es exactamente la consulta por petición
 * que la decisión D-08 existe para evitar. El coste está declarado en `plan.md` §5 y es acotado:
 * quien cambia la contraseña conserva un token con {@code mcp} hasta quince minutos, y `RF-SP-037`
 * lo neutraliza revocando todas las sesiones — de modo que ese token ya no puede renovarse.
 *
 * <p><b>Corre dentro de la cadena de seguridad y no en la del servlet</b>, porque necesita el token
 * ya validado: fuera de ella el contexto está vacío y no habría claim que leer. Y por eso <b>no se
 * anota como {@code @Component}</b>, a diferencia de {@link ActorCaptureFilter}: Spring Boot
 * registra todo bean de tipo {@code Filter} también en la cadena del servlet, y ahí este correría
 * antes de la autenticación, dejaría pasar toda petición y volvería a correr después. Un filtro que
 * <b>deniega</b> ejecutándose dos veces por petición, una de ellas siempre en vano, es la clase de
 * cosa que se lee mal en un volcado de pila seis meses después. Lo construye {@link
 * SecurityConfig}, que es quien lo coloca.
 *
 * <p><b>La lista de excepciones es la parte que hay que revisar</b>, y por eso lleva el motivo al
 * lado. Sin ella la cuenta queda sin salida: la persona no podría ni ver que le toca cambiar la
 * contraseña ni cambiarla, y la única forma de recuperarla sería tocar la base de datos a mano.
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

  /**
   * Lo que sigue siendo alcanzable con la marca puesta, y por qué.
   *
   * <p>La llave es {@code MÉTODO ruta}, con la misma forma que la lista blanca de {@code
   * EndpointPermissionsIT}: son dos listas del mismo tipo —excepciones declaradas con su motivo— y
   * leerlas con la misma forma es lo que permite compararlas de un vistazo.
   *
   * <p><b>Las tres rutas públicas figuran a propósito.</b> No portan token y por tanto no llegarían
   * aquí marcadas… salvo que el cliente adjunte igualmente su {@code Authorization}, que es lo que
   * hace todo cliente que guarda el token y lo pone en cada petición. Sin estas entradas, quien
   * tiene la marca puesta <b>no podría cerrar su sesión</b>, que es justo lo contrario de lo que
   * `RF-SP-036` declara al hacer público ese endpoint.
   */
  static final Map<String, String> ALCANZABLE_CON_LA_MARCA =
      Map.of(
          "POST /api/v1/auth/password",
          "`RF-SP-037`: es quien limpia la marca. Sin esta entrada la cuenta no tiene salida",
          "GET /api/v1/users/me",
          "`RF-SP-039`: quien no puede leer su propio perfil no puede saber POR QUÉ lo rechazan;"
              + " el indicador de cambio obligatorio viaja ahí",
          "POST /api/v1/auth/login",
          "Público: obtener una credencial no puede exigir haber usado la anterior",
          "POST /api/v1/auth/refresh",
          "Público: el refresco RECALCULA la marca (`FA-002` punto 4). Negarlo dejaría a la"
              + " persona esperando a que su token de acceso caduque para volver a entrar",
          "POST /api/v1/auth/logout",
          "Público (`RF-SP-036`): retener a alguien EN una sesión que quiere cerrar es lo"
              + " contrario de lo que ese requerimiento persigue",
          "POST /api/v1/auth/password-recovery",
          "Público (`RF-SP-040`): declarada por adelantado, como la política de límite de tasa"
              + " que le corresponde. El día que exista no dependerá de que alguien lo recuerde",
          "POST /api/v1/auth/password-recovery/confirmation",
          "Público (`RF-SP-040`): la segunda mitad de la recuperación, por lo mismo");

  private final ObjectMapper json;

  public MustChangePasswordFilter(ObjectMapper json) {
    this.json = json;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
      throws ServletException, IOException {

    if (!marcada() || esAlcanzable(peticion)) {
      cadena.doFilter(peticion, respuesta);
      return;
    }

    rechazar(peticion, respuesta);
  }

  /**
   * ¿La petición viene con la marca puesta?
   *
   * <p>Solo un token de acceso puede traerla. Una petición anónima, o autenticada por otro medio,
   * no está marcada: el filtro no es quien decide si hay que autenticar — de eso ya se ocupó la
   * cadena, y {@code anyRequest().authenticated()} rechazó lo que no debía pasar.
   */
  private static boolean marcada() {
    Authentication actor = SecurityContextHolder.getContext().getAuthentication();
    if (!(actor instanceof JwtAuthenticationToken conToken)) {
      return false;
    }
    // `getClaimAsBoolean` devuelve nulo si el claim no está, que es lo que
    // ocurre con un token emitido antes de que `mcp` existiera. Ausente se
    // trata como falso: negar el acceso a media plantilla por un token en
    // vuelo sería peor que la ventana de quince minutos que ya está declarada.
    return Boolean.TRUE.equals(
        conToken.getToken().getClaimAsBoolean(AccessTokenIssuer.CLAIM_CAMBIO_OBLIGATORIO));
  }

  private static boolean esAlcanzable(HttpServletRequest peticion) {
    return ALCANZABLE_CON_LA_MARCA.containsKey(firmaDe(peticion));
  }

  /** {@code MÉTODO ruta}, la misma forma con la que se declaran las excepciones. */
  static String firmaDe(HttpServletRequest peticion) {
    return peticion.getMethod() + " " + peticion.getRequestURI();
  }

  /**
   * Escribe el {@code 403} con la forma del resto de los errores de la API.
   *
   * <p>La escribe este filtro por lo mismo que {@code RateLimitFilter} escribe el suyo: una
   * excepción lanzada aquí no llega al {@code @RestControllerAdvice}, porque estamos fuera del
   * despachador. Lo que sí se conserva es el contrato — mismo {@code type}, mismo {@code
   * correlationId}, mismo {@code errors} — para que el cliente no tenga que distinguir dos formatos
   * según quién produjo el error.
   *
   * <p><b>No se audita, y es deliberado.</b> Esto no es un intento de saltarse un permiso: es una
   * persona que todavía no ha hecho lo único que el sistema le pide. Escribir un evento de
   * seguridad por rechazo llenaría {@code audit_security_log} con el ruido de cualquier interfaz
   * que reintente o consulte en bucle, y sepultaría los eventos que sí sirven para investigar — el
   * mismo razonamiento por el que el límite de tasa avisa una vez por ventana y no una vez por
   * petición. Lo que sí queda registrado es la petición misma, con su {@code 403}, en {@code
   * request_log}.
   */
  private void rechazar(HttpServletRequest peticion, HttpServletResponse respuesta)
      throws IOException {

    ProblemKind forma = ProblemKind.CAMBIO_DE_CONTRASENA_REQUERIDO;

    respuesta.setStatus(forma.status().value());
    respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());

    Map<String, Object> problema = new LinkedHashMap<>();
    problema.put("type", forma.type());
    problema.put("title", forma.title());
    problema.put("status", forma.status().value());
    problema.put(
        "detail",
        "Su contraseña la fijó otra persona. Cámbiela antes de usar el resto de la aplicación.");
    problema.put("instance", peticion.getRequestURI());
    problema.put("correlationId", correlacion());
    problema.put("errorCode", "PASSWORD_CHANGE_REQUIRED");
    // La ruta que resuelve el bloqueo viaja en la respuesta y no en el mensaje:
    // quien la lee es la interfaz, para llevar a la persona allí sin tener que
    // llevar esa ruta escrita a mano en su propio código.
    problema.put("changePasswordPath", "/api/v1/auth/password");
    problema.put("errors", List.of());

    respuesta.getWriter().write(json.writeValueAsString(problema));
  }

  private static String correlacion() {
    return RequestContext.current()
        .map(contexto -> contexto.correlationId().toString())
        .orElse(null);
  }
}
