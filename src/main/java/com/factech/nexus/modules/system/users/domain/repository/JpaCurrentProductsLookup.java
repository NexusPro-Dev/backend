package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.application.CurrentProductsLookup;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CurrentProductsLookup} sobre {@code user_products}, en una sentencia.
 *
 * <p><b>Las tres condiciones de la vigencia están en el {@code WHERE}</b> —empezada, sin fin
 * pasado, sin cerrar— y el reloj es el de la base, como en el filtro por membresía de `RF-SP-025`:
 * la pregunta es sobre un conjunto y no sobre una fila, y traer las fechas para compararlas en Java
 * leería lo que después se descarta. <b>La fila de nivel sin producto no aparece</b> —el suelo de
 * `RN-SP-018` y la del superadministrador de `V9`—: no hay producto que publicar. Se apoya en
 * {@code ix_user_products_user_abierto}.
 */
@Repository
public class JpaCurrentProductsLookup implements CurrentProductsLookup {

  private final EntityManager em;

  public JpaCurrentProductsLookup(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> currentProductIdsOf(UUID userId) {
    if (userId == null) {
      return Set.of();
    }
    @SuppressWarnings("unchecked")
    List<UUID> filas =
        em.createNativeQuery(
                """
                SELECT DISTINCT up.product_id
                  FROM user_products up
                 WHERE up.user_id = :usuario
                   AND up.product_id IS NOT NULL
                   AND up.closed_at IS NULL
                   AND up.started_at <= now()
                   AND (up.ends_at IS NULL OR up.ends_at > now())
                """)
            .setParameter("usuario", userId)
            .getResultList();
    return Set.copyOf(filas);
  }
}
