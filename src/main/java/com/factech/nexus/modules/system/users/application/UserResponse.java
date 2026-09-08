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
    CountryRef country,
    MembershipRef membership,
    SupervisorRef supervisor,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RoleRef(UUID id, String code, String name) {}

  /**
   * El país de la persona (`RN-SP-034`), resuelto y no como identificador suelto.
   *
   * <p>Mismo trato que {@code RoleRef} y por el mismo motivo: quien acaba de registrar o de editar
   * tiene que poder comprobar <b>qué</b> quedó escrito sin una segunda llamada al catálogo — que
   * además exige {@code countries:read}, un permiso que quien tiene {@code users:create} no
   * necesariamente porta.
   *
   * <p><b>Es el único de los tres objetos anidados de esta respuesta que nunca es nulo.</b> La
   * membresía y el superior son condicionales; el país entra siempre. Esa es la razón de que el
   * alta lo devuelva y no devuelva los otros dos: una salida que a veces trae un campo y a veces no
   * obliga al cliente a llamar al detalle de todas formas.
   *
   * <p>Se devuelve <b>aunque el país esté inactivo</b>, y sin decir que lo está: desactivar un país
   * lo retira de los selectores del alta (`RF-SP-022`), no cambia dónde está quien ya lo tenía.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CountryRef(UUID id, String code, String name) {}

  /** {@code endsAt} nulo significa <b>indefinida</b>, no «sin fecha conocida». */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record MembershipRef(UUID id, String code, String name, OffsetDateTime endsAt) {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record SupervisorRef(UUID id, String username, String firstName, String lastName) {}

  public static UserResponse from(
      User usuario,
      List<RoleRef> roles,
      CountryRef pais,
      MembershipRef membresia,
      SupervisorRef superior) {
    return new UserResponse(
        usuario.getId(),
        usuario.getUsername(),
        usuario.getEmail(),
        usuario.getFirstName(),
        usuario.getLastName(),
        usuario.getStatus().name(),
        usuario.isMustChangePassword(),
        roles,
        pais,
        membresia,
        superior,
        enUtc(usuario.getCreatedAt()),
        enUtc(usuario.getUpdatedAt()));
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
