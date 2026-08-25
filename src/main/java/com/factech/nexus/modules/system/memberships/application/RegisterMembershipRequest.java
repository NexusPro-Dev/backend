package com.factech.nexus.modules.system.memberships.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/memberships} (`RF-SP-016`).
 *
 * <p><b>No existe `level` ni `parentMembershipId`</b>, y el cuerpo se deserializa con {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} activo: enviar cualquiera de los dos devuelve {@code 400} y no se
 * ignora en silencio. Es lo mismo que `RF-SP-001` hizo con {@code status} e {@code isSystem}.
 *
 * <p><b>La membresía se indica por su hija y no por su superior</b>, porque así lo fija
 * `RN-SP-007`. La razón se lee mejor desde el negocio: al crear un nivel intermedio se sabe a quién
 * quiere uno dejar por debajo.
 *
 * @param childMembershipId admite ausencia y {@code null} con el mismo significado: extremo
 *     inferior
 */
public record RegisterMembershipRequest(
    @NotBlank(message = "VAL-001: El código de la membresía es obligatorio.")
        @Size(max = 50, message = "VAL-001: El código no puede exceder 50 caracteres.")
        @Pattern(
            regexp = "^[A-Z][A-Z0-9_]*$",
            message =
                "VAL-006: El código solo admite letras mayúsculas, dígitos y guion bajo, y debe"
                    + " empezar por letra.")
        String code,
    @NotBlank(message = "VAL-002: El nombre de la membresía es obligatorio.")
        @Size(max = 100, message = "VAL-002: El nombre no puede exceder 100 caracteres.")
        String name,
    @Size(max = 500, message = "VAL-002: La descripción no puede exceder 500 caracteres.")
        String description,
    UUID childMembershipId) {

  /**
   * Recorta antes de que corran las validaciones.
   *
   * <p>Jackson construye el registro por este constructor y Bean Validation se ejecuta después, de
   * modo que aquí el recorte llega a tiempo para importar. El código <b>no</b> se recorta: se
   * persiste tal como llegó para que el actor vea exactamente qué quedó registrado.
   */
  public RegisterMembershipRequest {
    name = name == null ? null : name.trim();
    description = description == null ? null : description.trim();
  }

  public RegisterMembershipCommand toCommand() {
    return new RegisterMembershipCommand(code, name, description, childMembershipId);
  }
}
