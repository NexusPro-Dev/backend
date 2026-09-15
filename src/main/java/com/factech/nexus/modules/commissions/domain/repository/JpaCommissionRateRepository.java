package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
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

  /** `RN-CM-013` en el esquema desde `V94`: un solo rol vivo por producto. */
  private static final String UQ_PRODUCTO_ROL = "uq_commission_rates_product_role";

  @Override
  public CommissionRate save(CommissionRate tasa) {
    try {
      em.persist(tasa);
      em.flush();
      return tasa;
    } catch (PersistenceException fallo) {
      // La CARRERA: dos altas simultáneas del mismo rol sobre el mismo producto.
      // La verificación previa del caso de uso ya dio el mensaje accionable en
      // el camino normal; aquí solo se traduce la violación al mismo `409`, por
      // nombre de restricción y nunca por texto del driver.
      if (UQ_PRODUCTO_ROL.equals(nombreDeRestriccion(fallo))) {
        String mensaje = "Ya hay una tasa viva de ese rol sobre ese producto.";
        throw new BusinessRuleException(
            "EX-007", mensaje, List.of(new FieldError("roleId", "EX-007", mensaje)));
      }
      throw fallo;
    }
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
  public boolean existsAlive(UUID productId, UUID roleId) {
    return !em.createQuery(
            "SELECT 1 FROM CommissionRate t WHERE t.productId = :producto AND t.roleId = :rol"
                + " AND t.deletedAt IS NULL",
            Integer.class)
        .setParameter("producto", productId)
        .setParameter("rol", roleId)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  @Override
  public void flushChanges() {
    em.flush();
  }
}
