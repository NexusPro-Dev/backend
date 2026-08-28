package com.factech.nexus.modules.products.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;

/**
 * El motivo con el que se retira un producto del catálogo (`RF-PM-006`, Art. V.13).
 *
 * <p><b>No admite construirse vacío</b>, y esa es la parte que importa. Con un {@code String}
 * suelto, un motivo de solo espacios llega hasta el registro de eliminación y queda ahí para
 * siempre como una fila que no explica nada: reconstruir por qué se retiró un producto se perdería
 * no porque falte el campo, sino porque está y está vacío.
 *
 * <p>Se recorta <b>antes</b> de comprobar: un motivo que solo tiene espacios <b>es</b> un motivo
 * ausente.
 *
 * <p><b>El motivo demasiado largo se rechaza, no se recorta</b> (`spec.md` §13). Un motivo truncado
 * dice algo distinto de lo que se escribió, y lo dice sin avisar a nadie.
 *
 * <h2>Por qué no se reutiliza el de `SP`</h2>
 *
 * <p>`SP` tiene su {@code ChangeReason} con la misma regla, y traerlo aquí <b>cruzaría la frontera
 * que D-25 acaba de fijar</b>: {@code PM} no importa nada del dominio de `SP`. Son diez líneas de
 * recorte y validación; compartirlas exigiría promoverlas a {@code shared/}, y eso se hace cuando
 * haya un tercer cliente — no antes, porque una abstracción con dos usos se moldea sobre los dos y
 * se rompe con el tercero.
 */
public record DeletionReason(String value) {

  /**
   * El mismo tope que `SP` usa en {@code ChangeReason}: la columna del registro es {@code text}.
   */
  private static final int LONGITUD_MAXIMA = 500;

  public DeletionReason {
    String limpio = value == null ? "" : value.trim();

    if (limpio.isEmpty()) {
      String mensaje = "El motivo de la eliminación es obligatorio.";
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
