package com.factech.nexus.shared.security;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

/**
 * El permiso que declara una operación en su {@code @PreAuthorize}.
 *
 * <p><b>Un solo análisis para dos lectores</b> (`RF-SP-073` · `plan.md` §1): el contrato, que
 * publica {@code x-required-permission} ({@link RequiredPermissionCustomizer}), y el interceptor de
 * las operaciones sensibles ({@link RecentMfaInterceptor}). Escrito dos veces, uno acabaría leyendo
 * una forma que el otro no entiende y la operación quedaría sin protección sin que nada lo dijera.
 *
 * <p>Solo se admite {@code hasAuthority('recurso:acción')}: el contrato se niega a generarse con
 * cualquier otra expresión, y eso es lo que hace que el interceptor pueda fiarse de esta lectura.
 */
public final class PreAuthorizePermission {

  private static final Pattern HAS_AUTHORITY =
      Pattern.compile("hasAuthority\\('([a-z-]+:[a-z-]+)'\\)");

  private static final String IS_AUTHENTICATED = "isAuthenticated()";

  private PreAuthorizePermission() {}

  /** La anotación del método, o la de su clase. */
  public static Optional<PreAuthorize> regla(HandlerMethod metodo) {
    PreAuthorize regla = metodo.getMethodAnnotation(PreAuthorize.class);
    if (regla == null) {
      regla = metodo.getBeanType().getAnnotation(PreAuthorize.class);
    }
    return Optional.ofNullable(regla);
  }

  /**
   * El código del permiso, o vacío si la operación no declara ninguno.
   *
   * @throws IllegalStateException si declara una expresión que no es un {@code hasAuthority} simple
   */
  public static Optional<String> de(HandlerMethod metodo) {
    Optional<PreAuthorize> regla = regla(metodo);
    if (regla.isEmpty() || IS_AUTHENTICATED.equals(regla.get().value())) {
      return Optional.empty();
    }
    Matcher permiso = HAS_AUTHORITY.matcher(regla.get().value());
    if (!permiso.matches()) {
      throw new IllegalStateException(
          "No se sabe leer la regla «"
              + regla.get().value()
              + "» de "
              + metodo.getShortLogMessage()
              + ": amplíe PreAuthorizePermission antes de usarla");
    }
    return Optional.of(permiso.group(1));
  }
}
