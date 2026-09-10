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
    DocumentRef document,
    ContactRef contact,
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

  /**
   * La identidad documental (`RN-SP-035`), con el tipo <b>resuelto</b>.
   *
   * <p>Mismo trato que {@code CountryRef} y por lo mismo: quien acaba de registrar o de editar
   * tiene que poder comprobar qué quedó escrito sin llamar al catálogo — que además exige {@code
   * document-types:read}, un permiso que quien tiene {@code users:create} no necesariamente porta.
   *
   * <p><b>A diferencia de {@code CountryRef}, este objeto SÍ puede ser nulo</b>: las personas
   * registradas antes del 08-09-2026 no tienen documento, y el esquema lo admite a propósito porque
   * inventarles uno sería escribir algo falso sobre su identidad.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record DocumentRef(DocumentTypeRef type, String number) {}

  /** El tipo, con su abreviación —que es su código— y su nombre. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record DocumentTypeRef(UUID id, String abbreviation, String name) {}

  /**
   * Los datos de contacto (`RN-SP-037`).
   *
   * <p><b>El objeto está siempre presente aunque sus cuatro campos vengan nulos.</b> Un objeto que
   * aparece y desaparece obliga al cliente a comprobar dos cosas antes de leer un teléfono.
   *
   * <p><b>Va agrupado y no como cuatro campos sueltos en la raíz</b>, igual que {@code document}, y
   * esa es la única decisión de forma de esta enmienda: son dos conjuntos con significados
   * distintos —identidad y contacto— que se editan por caminos distintos —`RF-SP-027` el primero,
   * también `RF-SP-044` el segundo—, y agruparlos deja esa diferencia <b>visible en el contrato</b>
   * en lugar de escrita solo en una regla.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ContactRef(String phone, String addressLine1, String addressLine2, String city) {}

  /** {@code endsAt} nulo significa <b>indefinida</b>, no «sin fecha conocida». */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record MembershipRef(UUID id, String code, String name, OffsetDateTime endsAt) {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record SupervisorRef(UUID id, String username, String firstName, String lastName) {}

  public static UserResponse from(
      User usuario,
      List<RoleRef> roles,
      CountryRef pais,
      DocumentRef documento,
      ContactRef contacto,
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
        documento,
        contacto,
        membresia,
        superior,
        enUtc(usuario.getCreatedAt()),
        enUtc(usuario.getUpdatedAt()));
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
