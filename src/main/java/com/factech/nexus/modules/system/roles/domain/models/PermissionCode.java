package com.factech.nexus.modules.system.roles.domain.models;

import com.factech.nexus.shared.error.ValidationException;
import java.util.regex.Pattern;

/**
 * Código de un permiso, en la forma {@code <recurso>:<acción>} (`security.md` §4.4).
 *
 * <p>Existe para que la comparación de `RN-SEG-010` —los permisos declarados contra los permisos
 * efectivos del actor— no se haga sobre cadenas sueltas. El catálogo es inmutable por API
 * (`RN-SP-004`), de modo que este tipo no valida contra la base de datos: valida la <b>forma</b>, y
 * la existencia la resuelve el catálogo.
 *
 * @param value el código completo, por ejemplo {@code roles:create}
 */
public record PermissionCode(String value) {

  private static final Pattern FORMATO = Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

  public PermissionCode {
    if (value == null || !FORMATO.matcher(value).matches()) {
      throw new ValidationException(
          "VAL-008", "El código de permiso no tiene la forma <recurso>:<acción>.");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
