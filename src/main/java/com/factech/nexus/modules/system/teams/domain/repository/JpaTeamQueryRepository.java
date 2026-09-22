package com.factech.nexus.modules.system.teams.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link TeamQueryRepository} sobre SQL nativo.
 *
 * <p><b>Un solo bloque de columnas</b> ({@link #COLUMNAS}) para el detalle y —cuando llegue
 * `RF-SP-064`— para el listado, con la cuenta de miembros vigentes como columna más. La cuenta es
 * una <b>subconsulta correlacionada</b> y no un {@code JOIN … GROUP BY}: aquella entra por {@code
 * ix_team_members_team_vigente} y cuesta lo mismo con un equipo que con veinte; el {@code GROUP BY}
 * obligaría a agrupar por todas las columnas publicadas y, con el {@code LIMIT} de la página, a
 * agrupar antes de paginar.
 */
@Repository
public class JpaTeamQueryRepository implements TeamQueryRepository {

  /** Los vigentes, que es lo que significa «los miembros de este equipo» (`RN-SP-052`). */
  private static final String CUENTA_DE_MIEMBROS =
      "(SELECT count(*) FROM team_members m WHERE m.team_id = t.id AND m.ended_at IS NULL)";

  private static final String COLUMNAS =
      """
      t.id AS id, t.name AS name, t.description AS description, t.status AS status,
      """
          + CUENTA_DE_MIEMBROS
          + """
           AS member_count,
          t.created_at AS created_at, t.updated_at AS updated_at, t.deleted_at AS deleted_at
          """;

  private final EntityManager em;

  public JpaTeamQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TeamRow> findDetail(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    // Sin filtrar por `deleted_at`: el detalle devuelve también el eliminado,
    // con su motivo, porque el listado ya lo enseña con `includeDeleted` y
    // abrirlo desde ahí para recibir un 404 sería incoherente (`RF-SP-065`).
    List<Tuple> filas =
        em.createNativeQuery("SELECT " + COLUMNAS + " FROM teams t WHERE t.id = :id", Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return filas.stream().map(JpaTeamQueryRepository::equipo).findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public List<TeamMemberRow> findActiveMembers(UUID teamId) {
    if (teamId == null) {
      return List.of();
    }
    // Una sola sentencia con el JOIN a `users`: el estado de cada persona viaja
    // con la fila y no cuesta una consulta por miembro. Por antigüedad, con el
    // nombre de usuario de desempate —dos asignados en la misma petición
    // comparten `started_at`, que es el caso normal de `RF-SP-069`.
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT u.id AS id, u.username AS username, u.first_name AS first_name,
                       u.last_name AS last_name, u.status AS status, m.started_at AS joined_at
                  FROM team_members m
                  JOIN users u ON u.id = m.user_id
                 WHERE m.team_id = :equipo
                   AND m.ended_at IS NULL
                 ORDER BY m.started_at, u.username
                """,
                Tuple.class)
            .setParameter("equipo", teamId)
            .getResultList();
    return filas.stream().map(JpaTeamQueryRepository::miembro).toList();
  }

  private static TeamRow equipo(Tuple fila) {
    return new TeamRow(
        (UUID) fila.get("id"),
        (String) fila.get("name"),
        (String) fila.get("description"),
        (String) fila.get("status"),
        ((Number) fila.get("member_count")).longValue(),
        momento(fila.get("created_at")),
        momento(fila.get("updated_at")),
        momento(fila.get("deleted_at")));
  }

  private static TeamMemberRow miembro(Tuple fila) {
    return new TeamMemberRow(
        (UUID) fila.get("id"),
        (String) fila.get("username"),
        (String) fila.get("first_name"),
        (String) fila.get("last_name"),
        (String) fila.get("status"),
        momento(fila.get("joined_at")));
  }

  private static OffsetDateTime momento(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime instante -> instante;
      case Instant instante -> instante.atOffset(ZoneOffset.UTC);
      case Timestamp marca -> marca.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }
}
