package com.factech.nexus.modules.system.teams.application;

import com.factech.nexus.modules.system.teams.domain.repository.TeamQueryRepository.TeamMemberRow;
import com.factech.nexus.modules.system.teams.domain.repository.TeamQueryRepository.TeamRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * El equipo como lo ve administración (`RF-SP-063`, `RF-SP-065`, y después la corrección, el cambio
 * de estado y las dos operaciones de miembros).
 *
 * <p>Es la respuesta de todas ellas para que el frontend trate «acabo de crearlo», «lo abrí» y
 * «acabo de moverle gente» con un solo modelo. <b>{@code description} y {@code members} siempre
 * presentes</b> —nula y vacía cuando no hay—; {@code deletedAt} y {@code deletionReason}, solo en
 * un equipo eliminado.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TeamDetailResponse(
    UUID id,
    String name,
    String description,
    String status,
    long memberCount,
    List<TeamMemberItem> members,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime deletedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) String deletionReason) {

  /**
   * Un miembro dentro de su equipo: quién es, en qué estado está y desde cuándo pertenece.
   *
   * <p><b>Sin sus roles</b>, al contrario que `RF-SP-042`: a un equipo solo pertenecen managers
   * (`RN-SP-051`) y publicar el rol en cada fila sería repetir la regla. <b>Sin su red</b>: los
   * directores y agentes que cuelgan de cada manager se consultan con `RF-SP-042` sobre esa
   * persona.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record TeamMemberItem(
      UUID id,
      String username,
      String firstName,
      String lastName,
      String status,
      OffsetDateTime joinedAt) {

    static TeamMemberItem from(TeamMemberRow fila) {
      return new TeamMemberItem(
          fila.id(),
          fila.username(),
          fila.firstName(),
          fila.lastName(),
          fila.status(),
          fila.joinedAt());
    }
  }

  public static TeamDetailResponse from(TeamRow fila, List<TeamMemberRow> miembros, String motivo) {
    return new TeamDetailResponse(
        fila.id(),
        fila.name(),
        fila.description(),
        fila.status(),
        fila.memberCount(),
        miembros.stream().map(TeamMemberItem::from).toList(),
        fila.createdAt(),
        fila.updatedAt(),
        fila.deletedAt(),
        motivo);
  }
}
