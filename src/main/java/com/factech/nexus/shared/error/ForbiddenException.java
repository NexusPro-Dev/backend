package com.factech.nexus.shared.error;

import java.util.List;

/**
 * Identidad probada sin el permiso exigido. La traduce el manejador global a {@code 403}.
 *
 * <p>Ver {@link DomainException} y `development-guide.md` §7.1.
 */
public class ForbiddenException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ForbiddenException(String errorCode, String message) {
    super(errorCode, message);
  }

  public ForbiddenException(String errorCode, String message, List<FieldError> errors) {
    super(errorCode, message, errors);
  }
}
