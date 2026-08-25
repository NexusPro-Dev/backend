package com.factech.nexus.modules.system.users.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Correo: la segunda identidad con la que una persona inicia sesión.
 *
 * <p><b>Se normaliza —recorte y minúsculas— y se persiste normalizado</b>, al revés que el nombre
 * de usuario. El motivo es que el correo <b>sí tiene forma canónica</b>: en la práctica todo
 * proveedor trata el buzón como insensible a mayúsculas, de modo que el dato almacenado ya es el
 * comparable y basta una restricción única corriente.
 *
 * <p><b>La salvedad honesta:</b> el RFC 5321 permite que la parte local sea sensible a mayúsculas.
 * Normalizar es por tanto una decisión de producto —dos direcciones que solo difieran en caja son
 * la misma persona— y no una verdad del protocolo. Se asume a conciencia porque la alternativa
 * produce cuentas duplicadas que nadie puede fusionar (`RN-SP-016`).
 *
 * <p>La comprobación de forma es deliberadamente laxa: un correo válido según el RFC admite cosas
 * que ninguna expresión razonable acepta, y la única verificación de verdad es enviarle un mensaje.
 * Aquí solo se impide lo que evidentemente no es una dirección.
 */
public record Email(String value) {

  private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private static final int LONGITUD_MAXIMA = 255;

  public Email {
    if (value == null || value.isBlank()) {
      throw error("VAL-001", "El correo es obligatorio.");
    }
    value = value.trim().toLowerCase(Locale.ROOT);

    if (value.length() > LONGITUD_MAXIMA) {
      throw error("VAL-002", "El correo no puede exceder " + LONGITUD_MAXIMA + " caracteres.");
    }
    if (!FORMATO.matcher(value).matches()) {
      throw error("VAL-002", "El correo no tiene un formato válido.");
    }
  }

  /** Lo que va antes de la arroba. Lo usa la política de contraseña. */
  public String localPart() {
    return value.substring(0, value.indexOf('@'));
  }

  private static ValidationException error(String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError("email", codigo, mensaje)));
  }

  @Override
  public String toString() {
    return value;
  }
}
