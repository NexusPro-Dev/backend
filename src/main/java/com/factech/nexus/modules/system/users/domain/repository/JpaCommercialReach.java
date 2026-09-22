package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.application.CommercialReach;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CommercialReach} sobre dos sentencias nativas: los tipos de rol vivos del actor y, si es
 * vendedor, su subárbol (`RF-MV-015` · `T-03`).
 *
 * <p><b>La precedencia va en Java y no en SQL</b>: es la lista de `RN-MV-031`, y escrita como tres
 * {@code if} se lee igual que la regla. Los tipos de rol salen con el mismo predicado de «rol vivo»
 * que {@link JpaEffectivePermissions#ROLES_QUE_CONCEDEN} —no retirado, activo, persona no
 * eliminada— para que «puede» y «alcanza» no tengan dos definiciones de rol.
 *
 * <p><b>El subárbol es la {@code WITH RECURSIVE} de `RF-SP-057`</b>, con sus dos decisiones: solo
 * la relación <b>vigente</b> ({@code ended_at} nulo), y {@code UNION} y no {@code UNION ALL}, para
 * que un ciclo —que `RN-SP-020` prohíbe y el esquema no impide— no cuelgue la consulta. La raíz se
 * añade en Java: `RN-MV-031` la incluye y la recursiva de `RF-SP-057` no.
 */
@Repository
public class JpaCommercialReach implements CommercialReach {

  private static final String TIPOS_DE_ROL_VIVOS =
      """
      SELECT DISTINCT r.role_type
        FROM user_roles ur
        JOIN roles r ON r.id = ur.role_id
        JOIN users u ON u.id = ur.user_id
       WHERE ur.user_id = :usuario
         AND u.deleted_at IS NULL
         AND r.deleted_at IS NULL
         AND r.status = 'ACTIVO'
      """;

  private static final String SUBARBOL =
      """
      WITH RECURSIVE red AS (
          SELECT us.user_id
            FROM user_supervisors us
           WHERE us.supervisor_id = :raiz
             AND us.ended_at IS NULL
          UNION
          SELECT us.user_id
            FROM user_supervisors us
            JOIN red r ON us.supervisor_id = r.user_id
           WHERE us.ended_at IS NULL
      )
      SELECT user_id FROM red
      """;

  private final EntityManager em;

  public JpaCommercialReach(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Reach reachOf(UUID actorId) {
    if (actorId == null) {
      return Reach.own();
    }
    @SuppressWarnings("unchecked")
    List<String> tipos =
        em.createNativeQuery(TIPOS_DE_ROL_VIVOS).setParameter("usuario", actorId).getResultList();

    if (tipos.contains("FUNCIONARIO")) {
      return Reach.everything();
    }
    if (tipos.contains("VENDEDOR")) {
      @SuppressWarnings("unchecked")
      List<UUID> subordinados =
          em.createNativeQuery(SUBARBOL).setParameter("raiz", actorId).getResultList();
      Set<UUID> red = new LinkedHashSet<>();
      red.add(actorId);
      red.addAll(subordinados);
      return Reach.network(red);
    }
    return Reach.own();
  }
}
