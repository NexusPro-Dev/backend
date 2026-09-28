package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.application.SupervisorChain;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SupervisorChain} sobre una {@code WITH RECURSIVE} que sube por {@code user_supervisors}
 * con las filas vigentes en un instante (`RF-CM-013` · `T-06`).
 *
 * <p><b>Dos guardas</b>, porque `RN-SP-020` prohíbe los ciclos y el esquema no los impide: la ruta
 * recorrida viaja en un arreglo y un superior que ya está en ella corta la recursión; y un tope de
 * profundidad que ninguna estructura comercial real alcanza. Sin ellas, un ciclo colgaría el
 * devengo de todas las ventas de esa red.
 */
@Repository
public class JpaSupervisorChain implements SupervisorChain {

  private static final int PROFUNDIDAD_MAXIMA = 64;

  private static final String CADENA =
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
      SELECT user_id FROM cadena ORDER BY nivel
      """;

  private final EntityManager em;

  public JpaSupervisorChain(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> chainAt(UUID userId, OffsetDateTime at) {
    if (userId == null || at == null) {
      return List.of();
    }
    @SuppressWarnings("unchecked")
    List<UUID> filas =
        em.createNativeQuery(CADENA)
            .setParameter("persona", userId)
            .setParameter("instante", at)
            .setParameter("tope", PROFUNDIDAD_MAXIMA)
            .getResultList();
    return new ArrayList<>(filas);
  }
}
