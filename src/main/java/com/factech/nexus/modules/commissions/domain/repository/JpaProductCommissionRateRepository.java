package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.ProductCommissionRate;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de {@link ProductCommissionRateRepository}. */
@Repository
public class JpaProductCommissionRateRepository implements ProductCommissionRateRepository {

  /** El nombre exacto de la clave primaria de `V48`. Si cambia allí, cambia aquí. */
  private static final String PK_ASOCIACION = "pk_product_commission_rates";

  /** `SQLState` estándar de «violación de unicidad». */
  private static final String ESTADO_UNICIDAD = "23505";

  private final EntityManager em;

  public JpaProductCommissionRateRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public ProductCommissionRate save(ProductCommissionRate asociacion) {
    try {
      em.persist(asociacion);
      em.flush();
      return asociacion;
    } catch (RuntimeException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<ProductCommissionRate> find(UUID commissionRateId, UUID productId) {
    if (commissionRateId == null || productId == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            """
            SELECT a FROM ProductCommissionRate a
             WHERE a.commissionRateId = :tasa AND a.productId = :producto
            """,
            ProductCommissionRate.class)
        .setParameter("tasa", commissionRateId)
        .setParameter("producto", productId)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public void remove(ProductCommissionRate asociacion) {
    em.remove(asociacion);
    em.flush();
  }

  /**
   * `RN-CM-013`, traducida.
   *
   * <p><b>El conflicto no es «esta tasa ya está asociada»</b> sino «este rol ya tiene UNA tasa
   * asociada a este producto», que puede ser otra distinta. Decir lo primero mandaría a buscar el
   * problema en la tasa que se está asociando, cuando está en la que ya estaba.
   */
  private static RuntimeException traducir(RuntimeException fallo) {
    if (esConflictoDeClave(fallo)) {
      String mensaje =
          "Ese rol ya tiene una tasa de comisión asociada a ese producto. Retire la asociación"
              + " existente antes de declarar otra.";
      return new BusinessRuleException(
          "EX-004", mensaje, List.of(new FieldError("productId", "EX-004", mensaje)));
    }
    return fallo;
  }

  private static boolean esConflictoDeClave(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
          && PK_ASOCIACION.equals(violacion.getConstraintName())) {
        return true;
      }
      if (causa instanceof java.sql.SQLException sql && ESTADO_UNICIDAD.equals(sql.getSQLState())) {
        return true;
      }
    }
    return false;
  }
}
