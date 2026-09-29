package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.AfftrackRate;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de {@link AfftrackRateRepository}. */
@Repository
public class JpaAfftrackRateRepository implements AfftrackRateRepository {

  /** `RN-CM-039` en el esquema desde `V54`: un valor vivo por producto, rol y límite. */
  private static final String UQ_LIMITE = "uq_afftrack_rates_product_role_threshold";

  private final EntityManager em;

  public JpaAfftrackRateRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public AfftrackRate save(AfftrackRate escalon) {
    try {
      em.persist(escalon);
      em.flush();
      return escalon;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public void flushChanges() {
    try {
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  /**
   * La CARRERA: dos altas —o dos correcciones— que llegan al mismo límite a la vez. La comprobación
   * previa del caso de uso ya dio el mensaje en el camino normal; aquí se traduce por nombre de
   * restricción y nunca por texto del driver.
   */
  private static RuntimeException traducir(PersistenceException fallo) {
    if (UQ_LIMITE.equals(nombreDeRestriccion(fallo))) {
      return yaHayUnoConEseLimite();
    }
    return fallo;
  }

  /** El mismo {@code 409} que da el caso de uso antes de escribir. */
  public static BusinessRuleException yaHayUnoConEseLimite() {
    String mensaje =
        "Ya hay una comisión afftrack viva de ese rol sobre ese producto con ese límite.";
    return new BusinessRuleException(
        "EX-006", mensaje, List.of(new FieldError("threshold", "EX-006", mensaje)));
  }

  private static String nombreDeRestriccion(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion) {
        return violacion.getConstraintName();
      }
    }
    return null;
  }

  @Override
  public Optional<AfftrackRate> findAliveForUpdate(UUID id) {
    return findAnyForUpdate(id).filter(e -> !e.estaRetirado());
  }

  @Override
  public Optional<AfftrackRate> findAnyForUpdate(UUID id) {
    return id == null
        ? Optional.empty()
        : Optional.ofNullable(em.find(AfftrackRate.class, id, LockModeType.PESSIMISTIC_WRITE));
  }

  @Override
  public boolean existsAlive(UUID productId, UUID roleId, int threshold, UUID exceptoId) {
    // Dos sentencias y no un `:excepto IS NULL OR …`: un parámetro nulo sin tipo
    // es justo lo que el driver de Postgres no sabe enlazar.
    var consulta =
        em.createQuery(
                "SELECT 1 FROM AfftrackRate e WHERE e.productId = :producto AND e.roleId = :rol"
                    + " AND e.threshold = :limite AND e.deletedAt IS NULL"
                    + (exceptoId == null ? "" : " AND e.id <> :excepto"),
                Integer.class)
            .setParameter("producto", productId)
            .setParameter("rol", roleId)
            .setParameter("limite", threshold);
    if (exceptoId != null) {
      consulta.setParameter("excepto", exceptoId);
    }
    return !consulta.setMaxResults(1).getResultList().isEmpty();
  }
}
