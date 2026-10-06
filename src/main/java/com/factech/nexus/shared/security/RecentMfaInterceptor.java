package com.factech.nexus.shared.security;

import com.factech.nexus.shared.error.RecentMfaRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Exige un segundo factor verificado hace poco en las operaciones sensibles (`RN-SP-063`,
 * `RF-SP-073`).
 *
 * <p><b>Un interceptor y no un filtro</b> (`plan.md` §1): el permiso de una operación se evalúa en
 * {@code @PreAuthorize}, en el proxy del controlador, después de toda la cadena de filtros; un
 * filtro no sabe qué operación es. Pero el interceptor corre <b>antes</b> que
 * {@code @PreAuthorize}, y la spec exige que la falta de permiso gane (`EX-002`): por eso
 * <b>comprueba él mismo la autoridad</b> y, si falta, deja pasar para que el {@code 403} sea el de
 * siempre. Al revés, el aviso de reverificación delataría qué operaciones existen a quien no puede
 * hacerlas.
 *
 * <p><b>Sin {@code Jwt} no se comprueba</b>, como en los filtros de retención: lo decide {@link
 * RecentMfa}. Una persona <b>sin factor activo</b> recibe el mismo {@code 403}: no tiene con qué
 * reverificar, y una operación sensible exige authenticator lo exija o no su rol.
 */
@Component
public class RecentMfaInterceptor implements HandlerInterceptor {

  private final SensitivePermissions sensibles;
  private final RecentMfa reciente;

  public RecentMfaInterceptor(SensitivePermissions sensibles, RecentMfa reciente) {
    this.sensibles = sensibles;
    this.reciente = reciente;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
    if (!(manejador instanceof HandlerMethod metodo)) {
      return true;
    }
    Optional<String> permiso = PreAuthorizePermission.de(metodo);
    if (permiso.isEmpty() || !sensibles.esSensible(permiso.get())) {
      return true;
    }
    Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
    if (autenticacion == null || !tiene(autenticacion, permiso.get())) {
      // Que decida @PreAuthorize: su `403` es el de la falta de permiso.
      return true;
    }
    if (!reciente.esReciente(autenticacion)) {
      throw new RecentMfaRequiredException(
          "Esta operación exige verificar de nuevo su segundo factor.");
    }
    return true;
  }

  private static boolean tiene(Authentication autenticacion, String permiso) {
    return autenticacion.getAuthorities().stream()
        .anyMatch(autoridad -> permiso.equals(autoridad.getAuthority()));
  }
}
