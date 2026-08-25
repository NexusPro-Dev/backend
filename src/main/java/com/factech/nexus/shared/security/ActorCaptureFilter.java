package com.factech.nexus.shared.security;

import com.factech.nexus.shared.observability.RequestActor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Apunta quién resultó ser el actor, mientras el contexto de seguridad todavía existe (issue #23).
 *
 * <p><b>Vive dentro de la cadena de seguridad y no fuera</b>, y ese es todo el motivo de que
 * exista: Spring Security limpia su contexto en su propio {@code finally}, de modo que un filtro
 * que la envuelva —como el que escribe {@code request_log}— ya no encuentra al actor cuando le toca
 * escribir. Registraría toda petición como anónima, con filas que existen y parecen correctas.
 *
 * <p><b>Qué queda anónimo, y es lo correcto:</b> lo que la cadena rechaza antes de llegar aquí. Un
 * {@code 401} no tiene actor porque, en efecto, no lo hubo.
 *
 * <p>No decide nada ni puede fallar: solo copia un dato que ya está resuelto.
 */
@Component
public class ActorCaptureFilter extends OncePerRequestFilter {

  private final CurrentActor actor;

  public ActorCaptureFilter(CurrentActor actor) {
    this.actor = actor;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
      throws ServletException, IOException {

    actor.currentActorId().ifPresent(RequestActor::bind);
    cadena.doFilter(peticion, respuesta);
  }
}
