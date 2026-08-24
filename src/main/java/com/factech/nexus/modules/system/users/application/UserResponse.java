package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.modules.system.users.domain.models.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * La persona tal como la devuelven el alta y las operaciones sobre su estructura.
 *
 * <p><b>{@code membership} y {@code supervisor} se añadieron el 24-08-2026</b>, al implementar
 * `RF-SP-030` y `RF-SP-031`: los dos planes describen su respuesta como «la persona con su lista de
 * roles actualizada, <b>su membresía y su superior vigente</b>», y el registro original —definido
 * por `RF-SP-024`— no los llevaba. Sin ellos, la respuesta de un retiro no puede mostrar el efecto
 * más importante de la operación, que es la <b>cascada</b>: retirar el último rol de consumidor
 * borra la membresía, y retirar el último de vendedor cierra la asignación de superior. Quien
 * recibiera solo la lista de roles no vería que además perdió otras dos cosas.
 *
 * <p>Ambos son <b>nulos y presentes</b>, no ausentes: {@code ALWAYS} está puesto justamente para
 * que «no tiene membresía» se distinga de «este endpoint no informa de la membresía».
 *
 * <p>Del superior se devuelve su nombre y su nombre de usuario, y nada más. No es un perfil: es lo
 * justo para nombrarlo en una interfaz sin obligar a una segunda consulta.
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
    MembershipRef membership,
    SupervisorRef supervisor,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RoleRef(UUID id, String code, String name) {}

  /** {@code endsAt} nulo significa <b>indefinida</b>, no «sin fecha conocida». */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record MembershipRef(UUID id, String code, String name, OffsetDateTime endsAt) {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record SupervisorRef(UUID id, String username, String firstName, String lastName) {}

  public static UserResponse from(
      User usuario, List<RoleRef> roles, MembershipRef membresia, SupervisorRef superior) {
    return new UserResponse(
        usuario.getId(),
        usuario.getUsername(),
        usuario.getEmail(),
        usuario.getFirstName(),
        usuario.getLastName(),
        usuario.getStatus().name(),
        usuario.isMustChangePassword(),
        roles,
        membresia,
        superior,
        enUtc(usuario.getCreatedAt()),
        enUtc(usuario.getUpdatedAt()));
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
