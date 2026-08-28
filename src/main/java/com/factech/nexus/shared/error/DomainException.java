package com.factech.nexus.shared.error;

import java.util.List;
import java.util.Map;

/**
 * Raíz de la jerarquía de errores de negocio (`development-guide.md` §7.1, `RF-SP-001` · `T-06`).
 *
 * <p><b>No conoce HTTP.</b> Ni esta clase ni ninguna de sus descendientes menciona un código de
 * estado: la traducción vive en {@link GlobalExceptionHandler}, que es el <b>único</b> lugar del
 * código que decide códigos de estado. Sin esa separación, el dominio acabaría importando tipos de
 * Spring y las reglas de negocio dejarían de poder probarse sin levantar el contenedor.
 *
 * <p><b>Los miembros de extensión son parte del contrato, no un añadido.</b> RFC 9457 §3.2 admite
 * que la respuesta lleve campos propios junto a los estándar, y es lo que permite devolver un dato
 * —cuántos intentos quedan, cuándo se levanta un bloqueo— sin inventar un formato de error paralelo
 * ni obligar al cliente a extraerlo del texto del mensaje. Un cliente que tuviera que leer el
 * mensaje para saber el número se rompería con el primer retoque de redacción.
 *
 * <p><b>Todo error de negocio lleva su código.</b> Al lanzar una violación de regla se incluye el
 * identificador de la regla —{@code RN-SEG-003}, no un texto libre—, y eso es lo que permite ir del
 * error devuelto al requerimiento sin intermediarios (`development-guide.md` §7.2).
 */
public abstract class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String errorCode;
  private final transient List<FieldError> errors;
  private final transient Map<String, Object> extensions;

  protected DomainException(String errorCode, String message) {
    this(errorCode, message, List.of(), Map.of());
  }

  protected DomainException(String errorCode, String message, List<FieldError> errors) {
    this(errorCode, message, errors, Map.of());
  }

  protected DomainException(
      String errorCode, String message, List<FieldError> errors, Map<String, Object> extensions) {
    super(message);
    this.errorCode = errorCode;
    this.errors = List.copyOf(errors);
    // `Map.copyOf` RECHAZA los valores nulos, y aquí eso es la regla y no un
    // estorbo: un miembro de extensión que no aplica —el bloqueo manual no
    // tiene momento de expiración— se OMITE, no se envía como `null`. Con las
    // dos formas admitidas, el cliente tendría que tratar «ausente» y «nulo»
    // como sinónimos y acabaría comprobando solo una.
    this.extensions = Map.copyOf(extensions);
  }

  /** Código de la regla incumplida o de la excepción declarada en la especificación. */
  public String errorCode() {
    return errorCode;
  }

  /** Detalle por campo. Vacío cuando el error no se refiere a ningún campo en concreto. */
  public List<FieldError> errors() {
    return errors;
  }

  /** Miembros de extensión de RFC 9457 §3.2. Vacío cuando el error no acompaña ningún dato. */
  public Map<String, Object> extensions() {
    return extensions;
  }
}
