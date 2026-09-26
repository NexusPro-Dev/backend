package com.factech.nexus.modules.movements.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;

/**
 * Por qué no entró un cobro (`RF-MV-004`) o por qué se niega un retiro (`RF-MV-021`): con contenido
 * y acotado. La forma de {@link VoidReason}, con el mismo tope.
 */
public record RejectionReason(String value) {

  public static final int LONGITUD_MAXIMA = 500;

  public RejectionReason {
    String limpio = value == null ? "" : value.trim();
    if (limpio.isEmpty()) {
      String mensaje = "El motivo del rechazo es obligatorio.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("reason", "VAL-002", mensaje)));
    }
    if (limpio.length() > LONGITUD_MAXIMA) {
      String mensaje = "El motivo no puede exceder " + LONGITUD_MAXIMA + " caracteres.";
      throw new ValidationException(
          "VAL-003", mensaje, List.of(new FieldError("reason", "VAL-003", mensaje)));
    }
    value = limpio;
  }
}
