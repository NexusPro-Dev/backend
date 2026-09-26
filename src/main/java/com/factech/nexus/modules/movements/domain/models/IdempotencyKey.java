package com.factech.nexus.modules.movements.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * La clave con la que un cliente identifica <b>una</b> petición que puede tener que repetir
 * (`RN-MV-040`).
 *
 * <p>Entre 8 y 80 caracteres visibles, sin espacios (`RF-MV-018` · `VAL-002`). Viaja en la cabecera
 * {@code Idempotency-Key}, que es donde la ponen las pasarelas.
 */
public record IdempotencyKey(String value) {

  public static final String CABECERA = "Idempotency-Key";
  private static final Pattern FORMA = Pattern.compile("[!-~]{8,80}");

  public IdempotencyKey {
    if (value == null || !FORMA.matcher(value).matches()) {
      String mensaje =
          "La cabecera "
              + CABECERA
              + " es obligatoria: entre 8 y 80 caracteres visibles, sin espacios.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError(CABECERA, "VAL-002", mensaje)));
    }
  }

  /**
   * La de una compra cuyo cliente no mandó ninguna (`RF-MV-018` · `spec.md` §2.2): la pone el
   * sistema, y con ella la petición no se puede reconocer si se repite.
   */
  public static IdempotencyKey generada() {
    return new IdempotencyKey("srv-" + UUID.randomUUID());
  }

  /** La opcional de las compras: ausente o en blanco, la pone el sistema. */
  public static IdempotencyKey opcional(String value) {
    return value == null || value.isBlank() ? generada() : new IdempotencyKey(value);
  }
}
