package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.shared.pagination.PageResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * La posición de una persona en la estructura comercial.
 *
 * <p><b>Compartido por `RF-SP-041` y `RF-SP-042`</b>, y con partes opcionales en lugar de dos DTO:
 * la reasignación devuelve el superior nuevo y el <b>anterior con su fecha de cierre</b>; la
 * consulta devuelve el superior vigente y el <b>equipo paginado</b>. Duplicar el registro haría que
 * la misma persona se describiera de dos formas distintas según el endpoint que la devuelva.
 *
 * <p><b>{@code supervisor} va AUSENTE, no en nulo</b>, cuando la persona es la cúspide comercial.
 * Es lo que permite a la interfaz distinguir «no depende de nadie» de «no se pudo resolver» —
 * `CA-SP-445` lo exige—. Por eso este registro usa {@code NON_NULL} y no {@code ALWAYS}, al revés
 * que los demás del módulo: aquí la ausencia <b>significa</b> algo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommercialStructureResponse(
    Person user,
    Person supervisor,
    Person previousSupervisor,
    OffsetDateTime previousSupervisorEndedAt,
    PageResponse<Person> team) {

  /**
   * Lo justo para nombrar a alguien y ver su posición.
   *
   * <p>No es un perfil: no hay correo, ni fechas, ni membresía. La restricción impide que este
   * endpoint se convierta en un listado de usuarios con otro permiso — `RF-SP-025` ya existe.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Person(
      UUID id,
      String username,
      String firstName,
      String lastName,
      String roleCode,
      String status,
      OffsetDateTime since) {

    public static Person de(
        UUID id, String username, String nombre, String apellido, String rol, String estado) {
      return new Person(id, username, nombre, apellido, rol, estado, null);
    }
  }
}
