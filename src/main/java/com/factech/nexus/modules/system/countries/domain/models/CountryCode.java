package com.factech.nexus.modules.system.countries.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Código ISO 3166-1 alfa-3 de un país.
 *
 * <p><b>Normaliza a mayúsculas y recorta, al revés que {@code RoleCode}, que rechaza.</b> La
 * diferencia es deliberada y tiene un motivo, no es una inconsistencia: el código de un rol <b>lo
 * inventa el actor</b>, y conviene que vea exactamente cuál quedó registrado; el de un país no lo
 * inventa nadie, lo fija ISO 3166-1, y rechazar {@code "col"} sería pedantería sobre un valor que
 * solo puede escribirse de una forma.
 *
 * <p>Normalizar en un objeto de valor y no en el DTO es lo que hace que {@code "col"}, {@code "
 * COL"} y {@code "COL"} sean el mismo país <b>en todos los caminos</b> —la validación del formato,
 * la comprobación de unicidad y lo que acaba persistido—, y no solo en el que pasa por la API.
 *
 * @param value el código ya normalizado
 */
public record CountryCode(String value) {

  private static final Pattern FORMATO = Pattern.compile("^[A-Z]{3}$");

  public CountryCode {
    if (value == null || value.isBlank()) {
      throw new ValidationException(
          "VAL-001",
          "El código del país es obligatorio.",
          List.of(new FieldError("code", "VAL-001", "El código del país es obligatorio.")));
    }

    // Recorte y mayúsculas ANTES de validar: es lo que convierte « col » en un
    // código válido en lugar de en un rechazo.
    value = value.trim().toUpperCase(Locale.ROOT);

    if (!FORMATO.matcher(value).matches()) {
      throw new ValidationException(
          "VAL-002",
          "El código del país debe tener exactamente tres letras.",
          List.of(
              new FieldError(
                  "code", "VAL-002", "El código del país debe tener exactamente tres letras.")));
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
