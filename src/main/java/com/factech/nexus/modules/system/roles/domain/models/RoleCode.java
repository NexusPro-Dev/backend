package com.factech.nexus.modules.system.roles.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Código de un rol: identificador corto, estable e inmutable (`VAL-007`, `VAL-008`).
 *
 * <p><b>No normaliza: rechaza.</b> Un código en minúsculas no se convierte en silencio a
 * mayúsculas, se devuelve como error de formato. La razón está en `spec.md` §13: el actor debe ver
 * exactamente qué código quedó registrado. Normalizar en silencio significa que quien escribió
 * {@code contabilidad} descubre más tarde, y por otro camino, que el rol se llama {@code
 * CONTABILIDAD}.
 *
 * <p><b>Lo escribe el actor, no lo deriva el sistema</b> (`spec.md` §14, pregunta 1). El código es
 * inmutable y el nombre no: derivarlo del nombre ataría un dato estable a uno que `RF-SP-004`
 * permite cambiar.
 *
 * <p>La misma restricción vive en el esquema como {@code ck_roles_code_format}, y esa duplicación
 * es deliberada: en el esquema la garantía vale también para las migraciones de poblado y para
 * cualquier punto de entrada futuro; en Java la validación solo cubre la API.
 *
 * @param value el código, tal como lo escribió el actor
 */
public record RoleCode(String value) {

  /** Mayúsculas, dígitos y guion bajo, empezando por letra. Es el formato del catálogo aprobado. */
  private static final Pattern FORMATO = Pattern.compile("^[A-Z][A-Z0-9_]*$");

  private static final int LONGITUD_MAXIMA = 50;

  public RoleCode {
    if (value == null || value.isEmpty()) {
      throw new ValidationException(
          "VAL-001",
          "El código del rol es obligatorio.",
          List.of(new FieldError("code", "VAL-001", "El código del rol es obligatorio.")));
    }
    if (value.length() > LONGITUD_MAXIMA) {
      throw new ValidationException(
          "VAL-007",
          "El campo excede la longitud permitida.",
          List.of(
              new FieldError(
                  "code",
                  "VAL-007",
                  "El código del rol no puede exceder " + LONGITUD_MAXIMA + " caracteres.")));
    }
    if (!FORMATO.matcher(value).matches()) {
      throw new ValidationException(
          "VAL-008",
          "El código solo admite letras mayúsculas, dígitos y guion bajo, y debe empezar por letra.",
          List.of(
              new FieldError(
                  "code",
                  "VAL-008",
                  "El código solo admite letras mayúsculas, dígitos y guion bajo, y debe empezar"
                      + " por letra.")));
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
