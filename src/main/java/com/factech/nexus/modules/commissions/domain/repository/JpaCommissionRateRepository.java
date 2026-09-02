package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link CommissionRateRepository}.
 *
 * <p><b>Ya no traduce ninguna violación</b>, al revés que hasta el 01-09-2026: la única restricción
 * que esta tabla podía violar era el no solapamiento, y desapareció con la vigencia. Lo que queda
 * es persistencia sin sorpresas.
 */
@Repository
public class JpaCommissionRateRepository implements CommissionRateRepository {

  private final EntityManager em;

  public JpaCommissionRateRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public CommissionRate save(CommissionRate tasa) {
    em.persist(tasa);
    em.flush();
    return tasa;
  }

  @Override
  public Optional<CommissionRate> findAlive(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT t FROM CommissionRate t WHERE t.id = :id AND t.deletedAt IS NULL",
            CommissionRate.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<CommissionRate> findAny(UUID id) {
    return id == null ? Optional.empty() : Optional.ofNullable(em.find(CommissionRate.class, id));
  }

  @Override
  public boolean tieneAsociaciones(UUID id) {
    Number cuantas =
        (Number)
            em.createNativeQuery(
                    "SELECT count(*) FROM product_commission_rates WHERE commission_rate_id = :id")
                .setParameter("id", id)
                .getSingleResult();
    return cuantas.longValue() > 0;
  }

  @Override
  public void flushChanges() {
    em.flush();
  }
}
