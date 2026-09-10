package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.modules.system.users.domain.models.ContactDetails;
import com.factech.nexus.modules.system.users.domain.models.DocumentIdentity;
import com.factech.nexus.shared.patch.Patchable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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
 * @param documentTypeId <b>obligatorio</b> (`RN-SP-035`). Va por identificador, como los roles y el
 *     país, y el cliente lo tiene de `RF-SP-051`. <b>Ese catálogo solo publica documentos de mayor
 *     de edad</b>, de modo que un desplegable alimentado por él no puede ofrecer el de un menor — y
 *     no hay ningún otro sitio donde esa validación ocurra
 * @param documentNumber <b>obligatorio</b>. Se normaliza a mayúsculas en {@link DocumentIdentity};
 *     único junto con el tipo entre todas las personas, incluidas las eliminadas
 * @param phone <b>obligatorio</b> (`RN-SP-037`). Es el <b>personal</b>, y se normaliza en {@link
 *     ContactDetails}
 * @param companyPhone opcional (`RN-SP-037`, 10-09-2026). El de la <b>empresa</b>: misma forma y
 *     misma normalización que el personal. Es opcional porque exigirlo bloquearía el alta de todo
 *     el que no tenga una, y es el único dato de contacto que se puede <b>vaciar de vuelta</b>
 *     después (`RF-SP-027`, `RF-SP-044`)
 * @param addressLine1 opcional
 * @param addressLine2 opcional, y el único que lo es <b>por naturaleza</b>: una dirección puede no
 *     tener complemento
 * @param city opcional, y <b>texto libre</b>: no hay catálogo de ciudades
 * @param countryId <b>obligatorio</b> (`RN-SP-034`, 07-09-2026). Va por identificador y no por
 *     código, que es el criterio del cuerpo entero — no mezclar dos espacios de identificación—, y
 *     el cliente los tiene de `RF-SP-021`, que además <b>solo publica los países activos</b>: un
 *     selector alimentado por él no puede ofrecer uno que el alta vaya a rechazar. Es obligatorio
 *     <b>sin condición</b>, al contrario que {@code membershipId} y {@code supervisorId}: no
 *     depende de qué roles se concedan
 * @param roleIds admite entre 1 y 100. <b>Al menos uno es obligatorio</b> desde `RN-SP-023`
 *     (24-08-2026): un usuario sin roles se autentica y no puede hacer nada, de modo que
 *     registrarlo así solo reservaría un nombre de usuario y un correo que `RN-SP-016` no libera
 *     nunca. El techo acota el coste de una petición que dispara una verificación por elemento
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
    @NotNull(message = "VAL-014: El país es obligatorio.") UUID countryId,
    @NotNull(message = "VAL-015: El tipo de documento es obligatorio.") UUID documentTypeId,
    @NotBlank(message = "VAL-015: El número de documento es obligatorio.")
        @Size(max = 30, message = "VAL-016: El número de documento no puede exceder 30 caracteres.")
        String documentNumber,
    @NotBlank(message = "VAL-017: El teléfono es obligatorio.")
        @Size(max = 25, message = "VAL-017: El teléfono no puede exceder 25 caracteres.")
        String phone,
    @Size(max = 25, message = "VAL-017: El teléfono no puede exceder 25 caracteres.")
        String companyPhone,
    @Size(max = 150, message = "VAL-008: La dirección no puede exceder 150 caracteres.")
        String addressLine1,
    @Size(max = 150, message = "VAL-008: El complemento no puede exceder 150 caracteres.")
        String addressLine2,
    @Size(max = 100, message = "VAL-008: La ciudad no puede exceder 100 caracteres.") String city,
    @NotEmpty(message = "VAL-013: Debe indicar al menos un rol.")
        @Size(max = 100, message = "VAL-004: No se admiten más de 100 roles en una sola petición.")
        List<UUID> roleIds,
    UUID membershipId,
    UUID supervisorId) {

  public RegisterUserRequest {
    firstName = firstName == null ? null : firstName.trim();
    lastName = lastName == null ? null : lastName.trim();
    // Las tres líneas de dirección SOLO se recortan. No se comparan con nada ni
    // participan en ninguna restricción de forma: transformarlas más sería
    // cambiar en silencio lo que la persona escribió. El documento y el teléfono
    // sí se normalizan, y lo hacen sus propios tipos de dominio.
    addressLine1 = vacioComoNulo(addressLine1);
    addressLine2 = vacioComoNulo(addressLine2);
    city = vacioComoNulo(city);
    // Los roles duplicados se colapsan sin error, igual que en el alta de rol:
    // pedir dos veces lo mismo no es una petición inválida.
    roleIds =
        roleIds == null
            ? List.of()
            : List.copyOf(
                new LinkedHashSet<>(roleIds.stream().filter(java.util.Objects::nonNull).toList()));
  }

  /** El blanco es «no lo declaro», no un valor: el esquema rechazaría una dirección de espacios. */
  private static String vacioComoNulo(String valor) {
    if (valor == null) {
      return null;
    }
    String contenido = valor.trim();
    return contenido.isEmpty() ? null : contenido;
  }

  public RegisterUserCommand toCommand() {
    Set<UUID> roles = new LinkedHashSet<>(roleIds);
    return new RegisterUserCommand(
        username,
        email,
        firstName,
        lastName,
        password,
        countryId,
        new DocumentIdentity(documentTypeId, documentNumber),
        new ContactDetails(
            Optional.ofNullable(phone),
            Patchable.de(companyPhone),
            Patchable.de(addressLine1),
            Patchable.de(addressLine2),
            Patchable.de(city)),
        roles,
        membershipId,
        supervisorId);
  }
}
