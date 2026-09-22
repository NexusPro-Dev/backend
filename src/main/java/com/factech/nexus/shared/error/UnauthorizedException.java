package com.factech.nexus.shared.error;

import java.util.List;
import java.util.Map;

/**
 * No hay identidad probada. La traduce el manejador global a {@code 401}.
 *
 * <p>Ver {@link DomainException} y `development-guide.md` §7.1.
 */
public class UnauthorizedException extends DomainException {

  private static final long serialVersionUID = 1L;

  public UnauthorizedException(String errorCode, String message) {
    super(errorCode, message);
  }

  public UnauthorizedException(String errorCode, String message, List<FieldError> errors) {
    super(errorCode, message, errors);
  }

  /**
   * Con miembros de extensión.
   *
   * <p>Lo usa el inicio de sesión para acompañar el rechazo con los intentos que quedan
   * (`RF-SP-034`, `EX-001`). El dato viaja como campo propio y no dentro del mensaje, para que el
   * cliente no dependa de la redacción.
   */
  public UnauthorizedException(String errorCode, String message, Map<String, Object> extensions) {
    super(errorCode, message, List.of(), extensions);
  }
}
