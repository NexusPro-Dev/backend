package com.factech.nexus.modules.system.teams.application;

import com.factech.nexus.modules.system.teams.domain.repository.TeamQueryRepository.TeamRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila del listado de equipos (`RF-SP-064`): quién es, en qué estado está y <b>cuántos miembros
 * vigentes tiene hoy</b>.
 *
 * <p><b>Sin descripción y sin miembros</b>, que son el detalle (`RF-SP-065`). La descripción son
 * hasta 500 caracteres multiplicados por el tamaño de la página, y publicar los miembros por fila
 * haría que la respuesta creciera con el producto de dos cardinalidades; la lista se recorre para
 * elegir, no para leer.
 *
 * <p>{@code memberCount} cuenta <b>los vigentes</b> (`RN-SP-052`) y no depende del estado: un
 * equipo `INACTIVO` con tres managers dice tres, porque desactivarlo no los saca (`RN-SP-053`). Un
 * equipo eliminado dice cero, y no por una regla aparte: `RN-SP-054` impide eliminarlo con
 * vigentes.
 *
 * <p>{@code deletedAt} solo aparece en un equipo eliminado, que únicamente sale con {@code
 * includeDeleted=true}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TeamItem(
    UUID id,
    String name,
    String status,
    long memberCount,
    OffsetDateTime createdAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime deletedAt) {

  public static TeamItem from(TeamRow fila) {
    return new TeamItem(
        fila.id(),
        fila.name(),
        fila.status(),
        fila.memberCount(),
        fila.createdAt(),
        fila.deletedAt());
  }
}
