package com.factech.nexus.modules.system.teams.domain.repository;

import com.factech.nexus.modules.system.teams.application.SellerTeamLookup;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SellerTeamLookup} sobre la {@code WITH RECURSIVE} de {@code JpaSupervisorChain}, con un
 * {@code JOIN} a {@code team_members} que <b>para en el primer miembro</b> (`RF-MV-001` plan §2.8).
 *
 * <p><b>Las mismas dos guardas</b>, porque `RN-SP-020` prohíbe los ciclos y el esquema no los
 * impide: la ruta recorrida viaja en un arreglo y un superior que ya está en ella corta la
 * recursión; y un tope de 64 niveles que ninguna estructura comercial real alcanza.
 *
 * <p><b>En lote, cada vendedor es su propio origen</b>: la semilla lleva una fila por vendedor y
 * {@code DISTINCT ON (origen)} se queda con el eslabón más cercano de cada uno. Una ida a la base
 * para todos, y no una por vendedor.
 */
@Repository
public class JpaSellerTeamLookup implements SellerTeamLookup {

  private static final int PROFUNDIDAD_MAXIMA = 64;

  private static final String EN_UN_INSTANTE =
      """
      WITH RECURSIVE cadena (user_id, nivel, ruta) AS (
          SELECT CAST(:persona AS uuid), 0, ARRAY[CAST(:persona AS uuid)]
          UNION ALL
          SELECT us.supervisor_id, c.nivel + 1, c.ruta || us.supervisor_id
            FROM cadena c
            JOIN user_supervisors us ON us.user_id = c.user_id
           WHERE us.started_at <= :instante
             AND (us.ended_at IS NULL OR us.ended_at > :instante)
             AND NOT us.supervisor_id = ANY (c.ruta)
             AND c.nivel < :tope
      )
      SELECT tm.team_id
        FROM cadena c
        JOIN team_members tm ON tm.user_id = c.user_id
                            AND tm.started_at <= :instante
                            AND (tm.ended_at IS NULL OR tm.ended_at > :instante)
       ORDER BY c.nivel
       LIMIT 1
      """;

  private static final String EN_LOTE =
      """
      WITH RECURSIVE cadena (origen, user_id, nivel, ruta) AS (
          SELECT u.id, u.id, 0, ARRAY[u.id]
            FROM users u
           WHERE u.id IN (:vendedores)
          UNION ALL
          SELECT c.origen, us.supervisor_id, c.nivel + 1, c.ruta || us.supervisor_id
            FROM cadena c
            JOIN user_supervisors us ON us.user_id = c.user_id
           WHERE us.started_at <= :instante
             AND (us.ended_at IS NULL OR us.ended_at > :instante)
             AND NOT us.supervisor_id = ANY (c.ruta)
             AND c.nivel < :tope
      )
      SELECT DISTINCT ON (c.origen) c.origen, tm.team_id
        FROM cadena c
        JOIN team_members tm ON tm.user_id = c.user_id
                            AND tm.started_at <= :instante
                            AND (tm.ended_at IS NULL OR tm.ended_at > :instante)
       ORDER BY c.origen, c.nivel
      """;

  private final EntityManager em;
  private final Clock reloj;

  @Autowired
  public JpaSellerTeamLookup(EntityManager em) {
    this(em, Clock.systemUTC());
  }

  JpaSellerTeamLookup(EntityManager em, Clock reloj) {
    this.em = em;
    this.reloj = reloj;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> teamAt(UUID sellerId, OffsetDateTime instant) {
    if (sellerId == null || instant == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<UUID> filas =
        em.createNativeQuery(EN_UN_INSTANTE)
            .setParameter("persona", sellerId)
            .setParameter("instante", instant)
            .setParameter("tope", PROFUNDIDAD_MAXIMA)
            .getResultList();
    return filas.stream().findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, UUID> currentTeamsOf(Collection<UUID> sellerIds) {
    if (sellerIds == null || sellerIds.isEmpty()) {
      return Map.of();
    }
    Set<UUID> vendedores =
        new LinkedHashSet<>(sellerIds.stream().filter(Objects::nonNull).toList());
    if (vendedores.isEmpty()) {
      return Map.of();
    }
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(EN_LOTE)
            .setParameter("vendedores", vendedores)
            .setParameter("instante", OffsetDateTime.now(reloj))
            .setParameter("tope", PROFUNDIDAD_MAXIMA)
            .getResultList();
    Map<UUID, UUID> oficinas = new HashMap<>();
    for (Object[] fila : filas) {
      oficinas.put((UUID) fila[0], (UUID) fila[1]);
    }
    return oficinas;
  }
}
