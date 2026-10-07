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
 *
 * <p><b>Las fechas eligen por solape</b> (`RF-IN-007` 0.2.0): el periodo del lote es semiabierto
 * —termina donde empieza el siguiente—, y uno abierto no tiene fin.
 */
@Repository
public class JpaCommissionBatchFigures implements CommissionBatchFigures {

  private static final String POR_ESTADO =
      """
      SELECT b.status, b.currency_id, c.code, count(*), sum(b.total_amount)
        FROM commission_batches b
        JOIN currencies c ON c.id = b.currency_id
       WHERE (CAST(:moneda AS uuid) IS NULL OR b.currency_id = CAST(:moneda AS uuid))
         AND (CAST(:persona AS uuid) IS NULL OR b.user_id = CAST(:persona AS uuid))
         AND (CAST(:hasta AS timestamptz) IS NULL OR b.period_start < CAST(:hasta AS timestamptz))
         AND (CAST(:desde AS timestamptz) IS NULL
              OR b.period_end IS NULL
              OR b.period_end > CAST(:desde AS timestamptz))
       GROUP BY b.status, b.currency_id, c.code
       ORDER BY b.status, c.code
      """;

  /**
   * Las comisiones de una persona por el estado de su lote hoy (`RF-IN-008`). La moneda es la del
   * lote: una comisión no tiene otra (`RN-CM-028`). Las fechas, sobre {@code accrued_at}, como
   * `RF-CM-026`; {@code ix_commissions_user} sirve el filtro.
   */
  private static final String COMISIONES_POR_ESTADO =
      """
      SELECT b.status, b.currency_id, c.code, count(*), sum(k.commission_amount)
        FROM commissions k
        JOIN commission_batches b ON b.id = k.batch_id
        JOIN currencies c ON c.id = b.currency_id
       WHERE k.user_id = :persona
         AND (CAST(:moneda AS uuid) IS NULL OR b.currency_id = CAST(:moneda AS uuid))
         AND (CAST(:cliente AS uuid) IS NULL
              OR EXISTS (SELECT 1
                           FROM movement_details d
                           JOIN movements m ON m.id = d.movement_id
                          WHERE d.id = k.movement_detail_id
                            AND m.user_id = CAST(:cliente AS uuid)))
         AND (CAST(:desde AS timestamptz) IS NULL OR k.accrued_at >= CAST(:desde AS timestamptz))
         AND (CAST(:hasta AS timestamptz) IS NULL OR k.accrued_at < CAST(:hasta AS timestamptz))
       GROUP BY b.status, b.currency_id, c.code
       ORDER BY b.status, c.code
      """;

  private final EntityManager em;

  public JpaCommissionBatchFigures(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<StatusTotals> byStatus(BatchFilter filter) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(POR_ESTADO)
            .setParameter("moneda", filter.currencyId())
            .setParameter("persona", filter.userId())
            .setParameter("desde", filter.from())
            .setParameter("hasta", filter.to())
            .getResultList();
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

  @Override
  @Transactional(readOnly = true)
  public List<CommissionTotals> commissionsByStatus(CommissionFilter filter) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(COMISIONES_POR_ESTADO)
            .setParameter("persona", filter.userId())
            .setParameter("moneda", filter.currencyId())
            .setParameter("cliente", filter.clientId())
            .setParameter("desde", filter.from())
            .setParameter("hasta", filter.to())
            .getResultList();
    List<CommissionTotals> cifras = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      cifras.add(
          new CommissionTotals(
              (String) f[0],
              (UUID) f[1],
              (String) f[2],
              ((Number) f[3]).longValue(),
              MinorUnits.fromMinor(f[4])));
    }
    return cifras;
  }
}
