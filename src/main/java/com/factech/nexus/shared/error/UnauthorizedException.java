package com.factech.nexus.shared.error;

import java.util.List;

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
}
