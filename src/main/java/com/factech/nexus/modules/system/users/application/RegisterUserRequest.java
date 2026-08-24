package com.factech.nexus.modules.system.users.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/users} (`RF-SP-024`).
 *
 * <p><b>No existen los campos de estado ni de cambio obligatorio</b>, y el cuerpo se deserializa
 * con {@code FAIL_ON_UNKNOWN_PROPERTIES} activo: enviarlos devuelve {@code 400} en lugar de
 * ignorarse.
 *
 * <p><b>La contraseña NO se recorta ni se transforma.</b> Un espacio al principio o al final es
 * parte de la contraseña; recortarla —como sí se hace con los demás campos— cambiaría en silencio
 * lo que la persona escribió y haría fallar su primer inicio de sesión.
 *
 * <p><b>El formato del nombre de usuario y del correo NO se valida aquí con anotaciones.</b> Lo
 * hacen {@code Username} y {@code Email}, que además normalizan: poner un {@code @Pattern} aquí
 * duplicaría la regla en dos sitios que divergirían, y el correo debe validarse <b>después</b> de
 * pasar a minúsculas, no antes.
 *
 * @param roleIds admite entre 0 y 100. Cero es válido —`FA-001`— y el techo acota el coste de una
 *     petición que dispara una verificación por elemento
 */
public record RegisterUserRequest(
    @NotBlank(message = "VAL-001: El nombre de usuario es obligatorio.") String username,
    @NotBlank(message = "VAL-001: El correo es obligatorio.") String email,
    @NotBlank(message = "VAL-003: El nombre es obligatorio.")
        @Size(max = 100, message = "VAL-003: El nombre no puede exceder 100 caracteres.")
        String firstName,
    @NotBlank(message = "VAL-003: El apellido es obligatorio.")
        @Size(max = 100, message = "VAL-003: El apellido no puede exceder 100 caracteres.")
        String lastName,
    @NotBlank(message = "VAL-008: La contraseña es obligatoria.") String password,
    @Size(max = 100, message = "VAL-004: No se admiten más de 100 roles en una sola petición.")
        List<UUID> roleIds,
    UUID membershipId,
    UUID supervisorId) {

  public RegisterUserRequest {
    firstName = firstName == null ? null : firstName.trim();
    lastName = lastName == null ? null : lastName.trim();
    // Los roles duplicados se colapsan sin error, igual que en el alta de rol:
    // pedir dos veces lo mismo no es una petición inválida.
    roleIds =
        roleIds == null
            ? List.of()
            : List.copyOf(
                new LinkedHashSet<>(roleIds.stream().filter(java.util.Objects::nonNull).toList()));
  }

  public RegisterUserCommand toCommand() {
    Set<UUID> roles = new LinkedHashSet<>(roleIds);
    return new RegisterUserCommand(
        username, email, firstName, lastName, password, roles, membershipId, supervisorId);
  }
}
