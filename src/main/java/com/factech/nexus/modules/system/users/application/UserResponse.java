package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.modules.system.users.domain.models.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Una persona en el contrato de la API (`RF-SP-024`).
 *
 * <p><b>No existe la contraseña ni ningún campo derivado de ella</b>, ni siquiera su longitud
 * (`CA-SP-196`). La forma más segura de no filtrar un dato es no tener dónde ponerlo.
 *
 * <p><b>El correo va normalizado y el nombre de usuario tal como se escribió.</b> Es la única forma
 * de que el actor vea qué quedó registrado, y refleja la asimetría deliberada entre las dos
 * identidades.
 *
 * <p><b>No se devuelven la membresía ni el superior</b>, aunque el alta los haya escrito: `spec.md`
 * §6.2 fija la salida y no los incluye. Añadirlos crearía dos formas del mismo recurso que habría
 * que mantener sincronizadas; quien los necesite tiene `RF-SP-026`.
 *
 * <p><b>No existe {@code createdBy}</b>: el actor no vive en la tabla de negocio (Art. V.7).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserResponse(
    UUID id,
    String username,
    String email,
    String firstName,
    String lastName,
    String status,
    boolean mustChangePassword,
    List<RoleRef> roles,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  /** Un rol referenciado desde una persona: identificador, código y nombre. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RoleRef(UUID id, String code, String name) {}

  public static UserResponse from(User usuario, List<RoleRef> roles) {
    return new UserResponse(
        usuario.getId(),
        usuario.getUsername(),
        usuario.getEmail(),
        usuario.getFirstName(),
        usuario.getLastName(),
        usuario.getStatus().name(),
        usuario.isMustChangePassword(),
        roles,
        enUtc(usuario.getCreatedAt()),
        enUtc(usuario.getUpdatedAt()));
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
