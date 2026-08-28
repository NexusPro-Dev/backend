package com.factech.nexus.modules.system.countries.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del alta de un país (`RF-SP-020`).
 *
 * <p><b>No existe campo de estado.</b> El país nace activo, y el cuerpo se deserializa con {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} activo: enviarlo devuelve {@code 400} y no se ignora en silencio. Es
 * lo mismo que hizo el alta de rol, y es lo que deja un único camino hacia el estado inactivo.
 *
 * <p><b>El código NO se valida aquí con un patrón.</b> Lo normaliza y lo valida {@code
 * CountryCode}, porque {@code "col"} y {@code " COL"} deben acabar siendo {@code "COL"} y no ser
 * rechazados —al revés que el código de un rol—. Poner aquí un {@code @Pattern} de mayúsculas
 * rechazaría precisamente lo que se quiere aceptar.
 *
 * <p>El nombre se recorta antes de validar; el interior <b>no se toca</b>, porque los nombres
 * compuestos llevan espacios legítimos.
 */
public record RegisterCountryRequest(
    @NotBlank(message = "VAL-001: El código del país es obligatorio.")
        @Size(max = 10, message = "VAL-002: El código del país debe tener exactamente tres letras.")
        String code,
    @NotBlank(message = "VAL-003: El nombre del país es obligatorio.")
        @Size(max = 100, message = "VAL-003: El nombre del país no puede exceder 100 caracteres.")
        String name) {

  public RegisterCountryRequest {
    name = name == null ? null : name.trim();
  }

  public RegisterCountryCommand toCommand() {
    return new RegisterCountryCommand(code, name);
  }
}
