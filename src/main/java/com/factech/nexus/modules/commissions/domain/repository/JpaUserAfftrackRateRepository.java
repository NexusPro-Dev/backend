package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.UserAfftrackRate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link UserAfftrackRateRepository}.
 *
 * <p><b>La violación de un {@code EXCLUDE} no trae nombre de restricción</b>: se traduce por estado
 * SQL —{@code 23P01}, y {@code 40P01} cuando dos altas simultáneas se esperan la una a la otra—,
 * como {@link JpaUserCommissionRateRepository}. Sin bloqueo consultivo: la carrera la cierra el
 * motor.
 */
@Repository
public class JpaUserAfftrackRateRepository implements UserAfftrackRateRepository {

  private static final String EX_VIGENTE = "ex_user_afftrack_rates_vigente";
  private static final String ESTADO_EXCLUSION = "23P01";
  private static final String ESTADO_INTERBLOQUEO = "40P01";

  private final EntityManager em;

  public JpaUserAfftrackRateRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public UserAfftrackRate save(UserAfftrackRate escalon) {
    try {
      em.persist(escalon);
      em.flush();
      return escalon;
    } catch (RuntimeException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public void flushChanges() {
    try {
      em.flush();
    } catch (RuntimeException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<UserAfftrackRate> findAnyForUpdate(UUID id) {
    return id == null
        ? Optional.empty()
        : Optional.ofNullable(em.find(UserAfftrackRate.class, id, LockModeType.PESSIMISTIC_WRITE));
  }

  @Override
  public boolean overlaps(
      UUID userId,
      UUID productId,
      int threshold,
      LocalDate validFrom,
      LocalDate validTo,
      UUID excluido) {
    return !em.createNativeQuery(
            """
            SELECT 1
              FROM user_afftrack_rates t
             WHERE t.deleted_at IS NULL
               AND t.user_id = :persona
               AND t.product_id = :producto
               AND t.threshold = :limite
               AND daterange(t.valid_from, t.valid_to, '[]')
                   && daterange(CAST(:desde AS date), CAST(:hasta AS date), '[]')
               AND (CAST(:excluido AS uuid) IS NULL OR t.id <> CAST(:excluido AS uuid))
             LIMIT 1
            """)
        .setParameter("persona", userId)
        .setParameter("producto", productId)
        .setParameter("limite", threshold)
        .setParameter("desde", validFrom.toString())
        .setParameter("hasta", validTo == null ? null : validTo.toString())
        .setParameter("excluido", excluido == null ? null : excluido.toString())
        .getResultList()
        .isEmpty();
  }

  private static RuntimeException traducir(RuntimeException fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
          && EX_VIGENTE.equals(violacion.getConstraintName())) {
        return UserAfftrackRateRepository.solapamiento();
      }
      if (causa instanceof java.sql.SQLException sql
          && (ESTADO_EXCLUSION.equals(sql.getSQLState())
              || ESTADO_INTERBLOQUEO.equals(sql.getSQLState()))) {
        return UserAfftrackRateRepository.solapamiento();
      }
    }
    return fallo;
  }
}
