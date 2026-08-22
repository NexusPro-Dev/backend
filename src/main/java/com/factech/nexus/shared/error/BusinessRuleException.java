package com.factech.nexus.shared.error;

import java.util.List;

/**
 * Regla de negocio violada sobre datos que existen. La traduce el manejador global a {@code 409}.
 *
 * <p>Ver {@link DomainException} y `development-guide.md` §7.1.
 */
public class BusinessRuleException extends DomainException {

  private static final long serialVersionUID = 1L;

  public BusinessRuleException(String errorCode, String message) {
    super(errorCode, message);
  }

  public BusinessRuleException(String errorCode, String message, List<FieldError> errors) {
    super(errorCode, message, errors);
  }
}
