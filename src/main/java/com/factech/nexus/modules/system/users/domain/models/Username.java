package com.factech.nexus.modules.system.users.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Nombre de usuario: identidad inmutable de una persona (`RN-SP-016`).
 *
 * <p><b>Se recorta pero NO se pasa a minúsculas</b>, y esa es la decisión que lo separa del correo.
 * {@code JPerez} es como esa persona quiere que la vean, y la auditoría lo mostrará durante años;
 * el dato se guarda tal como se escribió y es el <b>índice</b> quien ignora la caja.
 *
 * <p>La consecuencia, declarada como obligación para `RF-SP-034`: el inicio de sesión debe comparar
 * el nombre de usuario <b>sin distinguir mayúsculas</b>, o alguien podrá registrarse como {@code
 * JPerez} y no poder entrar escribiendo {@code jperez}.
 *
 * <p><b>Sin arroba</b> (`VAL-010`). Es lo que sostiene el inicio de sesión con las dos identidades:
 * sin esa prohibición, un nombre de usuario podría parecerse a un correo y `RF-SP-034` tendría que
 * adivinar cuál de las dos columnas consultar.
 */
public record Username(String value) {

  private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");

  public Username {
    if (value == null || value.isBlank()) {
      throw error("VAL-001", "El nombre de usuario es obligatorio.");
    }
    value = value.trim();

    if (value.indexOf('@') >= 0) {
      throw error("VAL-010", "El nombre de usuario no puede contener el carácter @.");
    }
    if (!FORMATO.matcher(value).matches()) {
      throw error(
          "VAL-010",
          "El nombre de usuario admite entre 3 y 50 caracteres, sin espacios ni acentos: letras,"
              + " dígitos, punto, guion y guion bajo.");
    }
  }

  private static ValidationException error(String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError("username", codigo, mensaje)));
  }

  @Override
  public String toString() {
    return value;
  }
}
