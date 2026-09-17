package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * El color con el que el frontend pinta una categoría (`RN-AC-003`): seis dígitos hexadecimales,
 * <b>sin {@code #}</b>, normalizados a mayúsculas.
 *
 * <p>Es la forma de `RN-SP-024` —el color de la membresía— <b>sin su unicidad</b>: las categorías
 * no son una cadena que haya que distinguir por el color, y dos cajones del mismo tono no confunden
 * a nadie. El {@code #} no se guarda porque es notación de CSS y no parte del valor; devolverlo
 * obligaría a todo consumidor que no sea una hoja de estilos a quitárselo.
 *
 * <p><b>La normalización ocurre aquí, antes de comparar y antes de escribir</b>: {@code 1e88e5} y
 * {@code 1E88E5} son el mismo color, y la corrección que envíe uno sobre el otro no es un cambio —
 * y no se audita como tal. {@code ck_course_categories_color_format} rechaza en el esquema lo que
 * llegue en minúsculas por cualquier otra vía.
 *
 * <p>Objeto de valor y no un {@code String} en la entidad porque la forma se comprueba en dos
 * sitios —el alta y la corrección— y el día que otra cosa de Academia lleve color, será este.
 */
public record CategoryColor(String value) {

  private static final Pattern FORMA = Pattern.compile("^[0-9A-F]{6}$");

  public CategoryColor {
    String normalizado = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    if (normalizado == null || !FORMA.matcher(normalizado).matches()) {
      throw rechazo();
    }
    value = normalizado;
  }

  /** El mismo rechazo para el alta (`VAL-002`) y para la corrección (`VAL-003` de `RF-AC-004`). */
  static ValidationException rechazo() {
    String mensaje =
        "El color es obligatorio y debe ser seis dígitos hexadecimales sin el símbolo #.";
    return new ValidationException(
        "VAL-002", mensaje, List.of(new FieldError("color", "VAL-002", mensaje)));
  }
}
