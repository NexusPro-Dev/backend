package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.shared.pagination.PageResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
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
   *
   * <h2>{@code roles} sustituye a {@code roleCode} (10-09-2026, `CA-SP-624`)</h2>
   *
   * <p><b>El campo viejo mentía.</b> Devolvía <b>un solo</b> rol y solo si era de clasificación
   * {@code VENDEDOR}, de modo que desde que `RF-SP-045` cuelga a los clientes de esta misma
   * estructura <b>la cartera llegaba con el rol en nulo</b> y un cliente era indistinguible de un
   * vendedor sin rol. `requirements/sp.md` §10.7 llevaba desde el 01-09-2026 afirmando lo
   * contrario.
   *
   * <p><b>Va siempre presente, aunque vaya vacía</b>, pese al {@code NON_NULL} de este registro:
   * nunca se construye en nulo. Una persona sin roles es un estado válido, y distinguirlo con la
   * ausencia del campo obligaría al cliente a tratar dos formas del mismo recurso — mismo criterio
   * que {@link UserListItem#roles()}.
   *
   * <p>Es el <b>mismo</b> {@link UserResponse.RoleRef} que publica el listado de personas, y no un
   * tipo propio: en el contrato los esquemas viven en un espacio de nombres plano, y dos registros
   * con los mismos tres campos obligarían al cliente generado a tener dos clases para lo mismo.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Person(
      UUID id,
      String username,
      String firstName,
      String lastName,
      List<UserResponse.RoleRef> roles,
      String status,
      OffsetDateTime since) {

    public static Person de(
        UUID id,
        String username,
        String nombre,
        String apellido,
        List<UserResponse.RoleRef> roles,
        String estado) {
      return new Person(id, username, nombre, apellido, roles, estado, null);
    }
  }
}
