package com.factech.nexus.shared.error;

import java.util.List;

/**
 * Raíz de la jerarquía de errores de negocio (`development-guide.md` §7.1, `RF-SP-001` · `T-06`).
 *
 * <p><b>No conoce HTTP.</b> Ni esta clase ni ninguna de sus descendientes menciona un código de
 * estado: la traducción vive en {@link GlobalExceptionHandler}, que es el <b>único</b> lugar del
 * código que decide códigos de estado. Sin esa separación, el dominio acabaría importando tipos de
 * Spring y las reglas de negocio dejarían de poder probarse sin levantar el contenedor.
 *
 * <p><b>Todo error de negocio lleva su código.</b> Al lanzar una violación de regla se incluye el
 * identificador de la regla —{@code RN-SEG-003}, no un texto libre—, y eso es lo que permite ir del
 * error devuelto al requerimiento sin intermediarios (`development-guide.md` §7.2).
 */
public abstract class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String errorCode;
  private final transient List<FieldError> errors;

  protected DomainException(String errorCode, String message) {
    this(errorCode, message, List.of());
  }

  protected DomainException(String errorCode, String message, List<FieldError> errors) {
    super(message);
    this.errorCode = errorCode;
    this.errors = List.copyOf(errors);
  }

  /** Código de la regla incumplida o de la excepción declarada en la especificación. */
  public String errorCode() {
    return errorCode;
  }

  /** Detalle por campo. Vacío cuando el error no se refiere a ningún campo en concreto. */
  public List<FieldError> errors() {
    return errors;
  }
}
