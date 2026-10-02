package com.factech.nexus.shared.error;

import java.util.List;

/**
 * {@code 503}: un proveedor externo del que depende la operación no respondió, o no está
 * configurado en este entorno. <b>Nada se escribió</b> y la petición se puede reintentar.
 */
public class ServiceUnavailableException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ServiceUnavailableException(String errorCode, String message) {
    super(errorCode, message);
  }

  public ServiceUnavailableException(String errorCode, String message, List<FieldError> errors) {
    super(errorCode, message, errors);
  }
}
