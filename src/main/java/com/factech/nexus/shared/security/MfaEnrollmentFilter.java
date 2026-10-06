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
 * Retiene a quien un rol obliga a usar el segundo factor y todavía no lo tiene (`RN-SP-062`,
 * `RF-SP-072` `FA-002`).
 *
 * <p><b>La forma de {@link MustChangePasswordFilter}</b>, y por lo mismo: lee el claim {@code mer}
 * del token y no la base, responde {@code 403} con un {@code type} propio y la ruta que resuelve la
 * retención, y <b>no se audita</b>. Va <b>detrás</b> de aquel en la cadena, y eso es lo que hace
 * que la contraseña mande (`CA-SP-837`): ninguna de estas rutas está en la lista del otro, de modo
 * que con las dos marcas el primero en cortar es él. Activar un authenticator sobre una credencial
 * que otra persona conoce protegería la cuenta para las dos.
 */
public class MfaEnrollmentFilter extends OncePerRequestFilter {

  static final String RUTA_DE_ACTIVACION = "/api/v1/users/me/mfa/totp";

  static final Map<String, String> ALCANZABLE_SIN_FACTOR =
      Map.of(
          "POST " + RUTA_DE_ACTIVACION,
          "`RF-SP-071`: iniciar la activación es la salida de la retención",
          "POST /api/v1/users/me/mfa/totp/confirmation",
          "`RF-SP-071`: y confirmarla, la segunda mitad",
          "GET /api/v1/users/me",
          "`RF-SP-039`: el perfil dice por qué se le retiene",
          "POST /api/v1/auth/login",
          "Público",
          "POST /api/v1/auth/login/mfa",
          "Público",
          "POST /api/v1/auth/refresh",
          "Público: el refresco recalcula la marca, y es lo que la levanta tras activar",
          "POST /api/v1/auth/logout",
          "Público: retener a alguien EN una sesión que quiere cerrar no tiene sentido",
          "POST /api/v1/auth/password-recovery",
          "Público",
          "POST /api/v1/auth/password-recovery/confirmation",
          "Público");

  private final ObjectMapper json;

  public MfaEnrollmentFilter(ObjectMapper json) {
    this.json = json;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
      throws ServletException, IOException {

    if (!retenida() || ALCANZABLE_SIN_FACTOR.containsKey(firmaDe(peticion))) {
      cadena.doFilter(peticion, respuesta);
      return;
    }
    rechazar(peticion, respuesta);
  }

  private static boolean retenida() {
    Authentication actor = SecurityContextHolder.getContext().getAuthentication();
    if (!(actor instanceof JwtAuthenticationToken conToken)) {
      return false;
    }
    return Boolean.TRUE.equals(
        conToken.getToken().getClaimAsBoolean(AccessTokenIssuer.CLAIM_ACTIVACION_OBLIGATORIA));
  }

  private static String firmaDe(HttpServletRequest peticion) {
    return peticion.getMethod() + " " + peticion.getRequestURI();
  }

  private void rechazar(HttpServletRequest peticion, HttpServletResponse respuesta)
      throws IOException {

    ProblemKind forma = ProblemKind.ACTIVACION_DE_SEGUNDO_FACTOR_REQUERIDA;

    respuesta.setStatus(forma.status().value());
    respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());

    Map<String, Object> problema = new LinkedHashMap<>();
    problema.put("type", forma.type());
    problema.put("title", forma.title());
    problema.put("status", forma.status().value());
    problema.put(
        "detail",
        "Uno de sus roles exige el segundo factor. Actívelo antes de usar el resto de la"
            + " aplicación.");
    problema.put("instance", peticion.getRequestURI());
    problema.put(
        "correlationId",
        RequestContext.current().map(c -> c.correlationId().toString()).orElse(null));
    problema.put("enrollmentPath", RUTA_DE_ACTIVACION);
    problema.put("errors", List.of());

    respuesta.getWriter().write(json.writeValueAsString(problema));
  }
}
