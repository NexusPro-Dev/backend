package com.factech.nexus.shared.error;

import java.util.List;

/**
 * Formato u obligatoriedad incumplidos (serie {@code VAL-nnn}). La traduce el manejador global a
 * {@code 400}.
 *
 * <p>Ver {@link DomainException} y `development-guide.md` §7.1.
 */
public class ValidationException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ValidationException(String errorCode, String message) {
    super(errorCode, message);
  }

  public ValidationException(String errorCode, String message, List<FieldError> errors) {
    super(errorCode, message, errors);
  }
}
