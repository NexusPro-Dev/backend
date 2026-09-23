package com.factech.nexus.modules.system.teams.domain.repository;

import com.factech.nexus.modules.system.teams.application.ListTeamsRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las lecturas del equipo (`RF-SP-064`, `RF-SP-065`), sobre proyecciones y no sobre la entidad: ni
 * el listado ni el detalle cargan nada que después haya que descartar.
 *
 * <p><b>{@code memberCount} viaja en la misma sentencia que la fila</b>, como subconsulta
 * correlacionada sobre {@code team_members} vigentes, que entra por {@code
 * ix_team_members_team_vigente}. Ni una consulta por fila —que sería el `N+1` que `CA-SP-746`
 * existe para impedir— ni un {@code GROUP BY} que obligaría a agrupar antes de paginar.
 */
public interface TeamQueryRepository {

  /** La ficha con su recuento, <b>viva o eliminada</b>: el detalle devuelve las dos. */
  Optional<TeamRow> findDetail(UUID id);

  /**
   * La página del listado (`RF-SP-064`), con los filtros ya validados y el orden ya resuelto.
   *
   * <p>El predicado de la búsqueda usa {@code f_unaccent(lower(t.name))}, que es <b>la expresión de
   * {@code ix_teams_busqueda}</b>: si divergieran, el índice existiría y el planificador no lo
   * usaría nunca, y el defecto saldría como lentitud que nadie relaciona con esta consulta.
   */
  List<TeamRow> search(ListTeamsRequest filtros, String ordenamiento, int offset, int limit);

  /**
   * El total del mismo conjunto, exacto: los equipos de una empresa se cuentan en decenas y la
   * tabla no crece sin cota, de modo que aquí no hace falta el techo de `RF-SP-011`.
   */
  long count(ListTeamsRequest filtros);

  /** Los miembros <b>vigentes</b>, por antigüedad y con el nombre de usuario de desempate. */
  List<TeamMemberRow> findActiveMembers(UUID teamId);

  /**
   * Cuántos miembros <b>vigentes</b> tiene el equipo (`RF-SP-068`, `RN-SP-054`).
   *
   * <p>Entra por {@code ix_team_members_team_vigente} y se lee <b>dentro del bloqueo</b> de la
   * baja: una asignación simultánea entre el {@code SELECT} y el {@code UPDATE} dejaría un equipo
   * eliminado con alguien dentro. Devuelve el número y no un booleano porque el mismo predicado
   * sirve para contar, y el recuento no cuesta más que la existencia sobre este índice.
   */
  long countActiveMembers(UUID teamId);

  /**
   * Los identificadores de <b>todas</b> las personas que pasaron por el equipo, vigentes o no, para
   * la instantánea de la baja (`CA-SP-775`).
   *
   * <p>No es {@link #findActiveMembers}: al eliminar, las vigentes son cero por definición
   * (`RN-SP-054`), de modo que preguntar por ellas devolvería siempre una lista vacía y el registro
   * de baja diría «tuve gente» sin poder decir quién. Es el mismo criterio con el que `RF-AC-005`
   * guarda los cursos de una categoría retirada.
   */
  List<UUID> findAllMemberIdsEver(UUID teamId);

  /** Un equipo como sale de la tabla, con su cuenta de miembros vigentes ya hecha. */
  record TeamRow(
      UUID id,
      String name,
      String description,
      String status,
      long memberCount,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt) {

    public boolean eliminado() {
      return deletedAt != null;
    }
  }

  /**
   * Un miembro dentro del detalle de su equipo. {@code status} es el de la <b>persona</b> —una
   * pertenencia vigente no tiene estados—, y se publica porque un manager desactivado sigue en su
   * equipo (`RN-SP-055`) y quien administra necesita verlo sin abrir cada ficha.
   */
  record TeamMemberRow(
      UUID id,
      String username,
      String firstName,
      String lastName,
      String status,
      OffsetDateTime joinedAt) {}
}
