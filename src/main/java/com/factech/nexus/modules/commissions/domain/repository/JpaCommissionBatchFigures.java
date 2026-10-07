package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.application.CommissionBatchFigures;
import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CommissionBatchFigures} sobre una sentencia nativa (`RF-IN-007` · `T-02`).
 *
 * <p><b>Suma {@code total_amount} y no las comisiones vivas</b>: es el valor que el lote tiene y el
 * que se paga, y retirar, devolver o revertir lo ajustan en la misma transacción (`RN-CM-046`,
 * `RN-CM-047`). Las centésimas se convierten <b>una vez, al mapear</b> ({@link MinorUnits}).
 */
@Repository
public class JpaCommissionBatchFigures implements CommissionBatchFigures {

  private static final String POR_ESTADO =
      """
      SELECT b.status, b.currency_id, c.code, count(*), sum(b.total_amount)
        FROM commission_batches b
        JOIN currencies c ON c.id = b.currency_id
       WHERE (CAST(:moneda AS uuid) IS NULL OR b.currency_id = CAST(:moneda AS uuid))
       GROUP BY b.status, b.currency_id, c.code
       ORDER BY b.status, c.code
      """;

  private final EntityManager em;

  public JpaCommissionBatchFigures(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<StatusTotals> byStatus(UUID currencyId) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(POR_ESTADO).setParameter("moneda", currencyId).getResultList();
    List<StatusTotals> cifras = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      cifras.add(
          new StatusTotals(
              (String) f[0],
              (UUID) f[1],
              (String) f[2],
              ((Number) f[3]).longValue(),
              MinorUnits.fromMinor(f[4])));
    }
    return cifras;
  }
}
