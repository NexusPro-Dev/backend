package com.factech.nexus.modules.system.users.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * El perfil de quien pregunta (`RF-SP-039`).
 *
 * <p><b>DTO propio y no el detalle de `RF-SP-026`</b>, aunque se parezcan: aquel es la ficha
 * administrativa de un tercero y este es lo que alguien necesita saber <b>de sí mismo</b>.
 * Compartir el registro habría atado dos contratos que evolucionan por motivos distintos, y habría
 * arrastrado aquí campos que en esta pantalla no significan nada — las fechas de creación y
 * modificación, o la expiración del bloqueo, que quien está autenticado no tiene.
 *
 * <p><b>{@code permissions} es lo que hace útil este endpoint</b> y la razón de que exista: cierra
 * el hallazgo `DF-04` del frontend. Sin él, la interfaz tenía que deducir del listado de roles qué
 * puede hacer la persona, duplicando en el navegador una regla que vive en el servidor — y la copia
 * del navegador se quedaba atrás.
 *
 * <p><b>No se pagina.</b> Es el perfil de una sola persona; partirlo obligaría a pedirlo en trozos
 * para poder pintar un menú.
 *
 * <p><b>No lleva identificador</b>: quien pregunta ya sabe quién es. Y no lleva equipo a cargo —
 * eso es la consulta de estructura comercial, con su propio permiso y su propia paginación.
 *
 * <p>{@code membership} y {@code supervisor} van <b>ausentes</b>, no en nulo, cuando no aplican: la
 * interfaz distingue «no tiene» de «no se pudo resolver».
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OwnProfileResponse(
    String username,
    String email,
    String firstName,
    String lastName,
    String status,
    List<RoleRef> roles,
    List<String> permissions,
    MembershipRef membership,
    OffsetDateTime lastLoginAt,
    SupervisorRef supervisor,
    boolean mustChangePassword) {

  /** Con su estado: es lo que explica que un rol asignado no aparezca en {@code permissions}. */
  public record RoleRef(String code, String name, String status) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MembershipRef(String code, short level, OffsetDateTime endsAt) {}

  /**
   * <b>Solo el superior, nunca el equipo.</b>
   *
   * <p>A quién reporta uno es un dato del propio actor; quiénes dependen de uno es un conjunto de
   * terceros. Es la distinción que sostiene la reserva de <b>D-22</b>, y devolver el equipo aquí la
   * adelantaría sin que nadie la haya decidido.
   */
  public record SupervisorRef(
      String username, String firstName, String lastName, String roleCode) {}
}
