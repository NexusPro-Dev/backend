package com.factech.nexus.modules.system.users.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;

/**
 * El motivo de un cambio administrativo sobre una persona.
 *
 * <p>Generaliza lo que `RF-SP-028` iba a llamar {@code StatusChangeReason}: el mismo tipo sirve
 * para el cambio de estado, la eliminación y la reasignación de superior comercial, porque en los
 * tres la regla es idéntica — <b>recorta, exige contenido y no admite construirse vacío</b>.
 *
 * <p><b>No admite construirse vacío</b> es la parte que importa. Con un {@code String} suelto, un
 * motivo de solo espacios llega hasta la fila de auditoría y queda ahí para siempre como un
 * registro que no explica nada; el trabajo de reconstruir por qué se reorganizó una zona se pierde
 * no porque falte el campo, sino porque está y está vacío.
 *
 * <p>Se recorta antes de comprobar: un motivo que solo tiene espacios <b>es</b> un motivo ausente.
 */
public record ChangeReason(String value) {

  private static final int LONGITUD_MAXIMA = 500;

  public ChangeReason {
    String limpio = value == null ? "" : value.trim();

    if (limpio.isEmpty()) {
      String mensaje = "El motivo es obligatorio y no puede estar vacío.";
      throw new ValidationException(
          "VAL-008", mensaje, List.of(new FieldError("reason", "VAL-008", mensaje)));
    }
    if (limpio.length() > LONGITUD_MAXIMA) {
      String mensaje = "El motivo no puede exceder " + LONGITUD_MAXIMA + " caracteres.";
      throw new ValidationException(
          "VAL-008", mensaje, List.of(new FieldError("reason", "VAL-008", mensaje)));
    }
    value = limpio;
  }
}
