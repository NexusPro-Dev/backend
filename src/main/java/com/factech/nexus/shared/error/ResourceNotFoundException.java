package com.factech.nexus.shared.error;

import java.util.List;

/**
 * El recurso de la ruta no existe. La traduce el manejador global a {@code 404}.
 *
 * <p>Ver {@link DomainException} y `development-guide.md` §7.1.
 */
public class ResourceNotFoundException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ResourceNotFoundException(String errorCode, String message) {
    super(errorCode, message);
  }

  public ResourceNotFoundException(String errorCode, String message, List<FieldError> errors) {
    super(errorCode, message, errors);
  }
}
