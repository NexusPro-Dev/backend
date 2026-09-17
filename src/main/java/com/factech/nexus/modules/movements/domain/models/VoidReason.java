package com.factech.nexus.modules.movements.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;

/**
 * Por qué una venta no debía existir (`RF-MV-005`): con contenido y acotado.
 *
 * <p>Es lo que separa «anulada» de «desaparecida», y por eso es <b>obligatorio</b> y se verifica
 * <b>antes</b> de tocar la venta: un motivo vacío no cuesta ni una consulta. La misma forma que el
 * motivo de una eliminación de `PM`, con el mismo tope, para que el frontend no aprenda dos.
 */
public record VoidReason(String value) {

  public static final int LONGITUD_MAXIMA = 500;

  public VoidReason {
    String limpio = value == null ? "" : value.trim();
    if (limpio.isEmpty()) {
      String mensaje = "El motivo de la anulación es obligatorio.";
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
