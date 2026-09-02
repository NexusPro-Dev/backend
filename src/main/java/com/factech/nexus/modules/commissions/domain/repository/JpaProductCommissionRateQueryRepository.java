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

  private static final String COLUMNAS =
      """
      a.product_id AS product_id, p.code AS product_code, p.name AS product_name,
      a.role_id AS role_id, r.code AS role_code, r.name AS role_name,
      a.commission_rate_id AS rate_id,
      c.rate_type AS rate_type, c.percentage AS percentage, c.fixed_amount AS fixed_amount,
      a.created_at AS created_at
      """;

  /**
   * <b>{@code commission_rates} entra por su clave compuesta y no solo por el identificador.</b> Es
   * la misma pareja de columnas que declara la clave foránea, y unirla así hace que la consulta
   * <b>no pueda</b> leer el porcentaje de una tasa cuyo rol no sea el copiado — ni siquiera si
   * algún día alguien lograra escribir esa fila.
   */
  private static final String TABLAS =
      """
      product_commission_rates a
      LEFT JOIN products         p ON p.id = a.product_id
      LEFT JOIN roles            r ON r.id = a.role_id
      LEFT JOIN commission_rates c ON c.id = a.commission_rate_id AND c.role_id = a.role_id
      """;

  private final EntityManager em;

  public JpaProductCommissionRateQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AssociationRow> findByRate(UUID commissionRateId) {
    if (commissionRateId == null) {
      return List.of();
    }
    return leer(
        "SELECT "
            + COLUMNAS
            + " FROM "
            + TABLAS
            + " WHERE a.commission_rate_id = :clave ORDER BY p.code ASC",
        commissionRateId);
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
            + " WHERE a.product_id = :clave ORDER BY r.code"
            + " ASC",
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
