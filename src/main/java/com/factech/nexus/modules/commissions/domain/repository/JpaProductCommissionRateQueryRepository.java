package com.factech.nexus.modules.commissions.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de {@link ProductCommissionRateQueryRepository}. */
@Repository
public class JpaProductCommissionRateQueryRepository
    implements ProductCommissionRateQueryRepository {

  /**
   * Desde el 15-09-2026 se lee {@code commission_rates} directamente (`RN-CM-021`): la tasa lleva
   * su producto, y {@code created_at} —que antes era el de la asociación— es el de la propia tasa,
   * que es cuando empezó a regir.
   */
  private static final String COLUMNAS =
      """
      c.product_id AS product_id, p.code AS product_code, p.name AS product_name,
      c.role_id AS role_id, r.code AS role_code, r.name AS role_name,
      c.id AS rate_id,
      c.rate_type AS rate_type, c.percentage AS percentage, c.fixed_amount AS fixed_amount,
      c.created_at AS created_at
      """;

  private static final String TABLAS =
      """
      commission_rates c
      LEFT JOIN products p ON p.id = c.product_id
      LEFT JOIN roles    r ON r.id = c.role_id
      """;

  private final EntityManager em;

  public JpaProductCommissionRateQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AssociationRow> findByProduct(UUID productId) {
    if (productId == null) {
      return List.of();
    }
    return leer(
        "SELECT "
            + COLUMNAS
            + " FROM "
            + TABLAS
            + " WHERE c.product_id = :clave AND c.deleted_at IS NULL ORDER BY r.code ASC",
        productId);
  }

  private List<AssociationRow> leer(String sql, UUID clave) {
    List<Tuple> filas =
        em.createNativeQuery(sql, Tuple.class).setParameter("clave", clave).getResultList();

    List<AssociationRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(comoFila(fila));
    }
    return resultado;
  }

  private static AssociationRow comoFila(Tuple fila) {
    return new AssociationRow(
        (UUID) fila.get("product_id"),
        (String) fila.get("product_code"),
        (String) fila.get("product_name"),
        (UUID) fila.get("role_id"),
        (String) fila.get("role_code"),
        (String) fila.get("role_name"),
        (UUID) fila.get("rate_id"),
        CommissionRows.forma(fila.get("rate_type")),
        (BigDecimal) fila.get("percentage"),
        (BigDecimal) fila.get("fixed_amount"),
        CommissionRows.momento(fila.get("created_at")));
  }
}
