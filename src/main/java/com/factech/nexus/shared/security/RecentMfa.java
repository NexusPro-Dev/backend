package com.factech.nexus.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * ¿La petición prueba un segundo factor verificado hace poco? (`RN-SP-063`, `security.md` §5.2).
 *
 * <p>La prueba viaja en el claim {@code mfa} del token —el instante, en segundos desde la época—, y
 * se compara con la ventana de {@code nexus.security.mfa.recent-window}. <b>No se consulta la
 * base</b>: es la misma decisión que con {@code mcp}.
 *
 * <p><b>Una sola lectura para dos usos</b>: el interceptor de las operaciones marcadas
 * (`RF-SP-073`) y el cambio de teléfono (`RF-SP-071` `EX-003`), que es sensible por el estado de la
 * persona y no por el permiso. Con dos lecturas, dos ventanas acabarían distintas.
 *
 * <p><b>Sin {@code Jwt} no hay nada que comprobar</b>, y se responde que sí: en producción toda
 * petición autenticada lleva uno; en las pruebas, {@code with(user(...))} no, y es lo que mantiene
 * fuera de esto las suites que lo usan —igual que {@code MustChangePasswordFilter}—.
 */
@Component
public class RecentMfa {

  /** Nombre del claim. Constante porque lo escribe {@link AccessTokenIssuer}. */
  public static final String CLAIM = "mfa";

  private final Duration ventana;
  private final Clock reloj;

  @Autowired
  public RecentMfa(@Value("${nexus.security.mfa.recent-window:PT5M}") Duration ventana) {
    this(ventana, Clock.systemUTC());
  }

  RecentMfa(Duration ventana, Clock reloj) {
    this.ventana = ventana;
    this.reloj = reloj;
  }

  public boolean esReciente() {
    return esReciente(SecurityContextHolder.getContext().getAuthentication());
  }

  public boolean esReciente(Authentication autenticacion) {
    if (!(autenticacion instanceof JwtAuthenticationToken jwt)) {
      return true;
    }
    Object valor = jwt.getToken().getClaims().get(CLAIM);
    if (!(valor instanceof Number segundos)) {
      return false;
    }
    Instant verificado = Instant.ofEpochSecond(segundos.longValue());
    return !verificado.plus(ventana).isBefore(Instant.now(reloj));
  }

  /** Hasta cuándo vale una verificación hecha en {@code instante}. */
  public Instant validaHasta(Instant instante) {
    return instante.plus(ventana);
  }
}
