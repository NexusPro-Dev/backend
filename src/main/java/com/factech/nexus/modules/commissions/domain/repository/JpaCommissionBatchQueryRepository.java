package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link CommissionBatchQueryRepository} sobre SQL nativo.
 *
 * <p><b>Una sentencia por lectura, con los datos ajenos unidos en ella</b> —la persona, la moneda,
 * la venta y el producto de cada comisión—, por el mismo precedente que {@code
 * JpaUserCommissionRateQueryRepository}: leer por el catálogo de cada módulo sería una ida por
 * módulo y página, y un {@code JOIN} de lectura no cambia nada de nadie.
 *
 * <p><b>El nombre del producto es el copiado en la línea</b> (`RN-MV-002`), no el de hoy: la
 * comisión se explica con lo que se vendió.
 */
@Repository
public class JpaCommissionBatchQueryRepository implements CommissionBatchQueryRepository {

  private static final String COLUMNAS =
      """
      SELECT b.id, b.code, b.user_id, u.username, u.first_name, u.last_name,
             b.currency_id, c.code AS currency_code, b.period_start, b.period_end, b.status,
             b.total_amount,
             (SELECT count(*) FROM commissions k
               WHERE k.batch_id = b.id) AS comisiones,
             b.paid_at, b.movement_id, pm.total_amount AS abonado
        FROM commission_batches b
        JOIN users u ON u.id = b.user_id
        JOIN currencies c ON c.id = b.currency_id
        LEFT JOIN movements pm ON pm.id = b.movement_id
      """;

  private static final String FILTRO =
      """
       WHERE (CAST(:estado AS varchar) IS NULL OR b.status = CAST(:estado AS varchar))
         AND (CAST(:persona AS uuid) IS NULL OR b.user_id = CAST(:persona AS uuid))
         AND (CAST(:moneda AS uuid) IS NULL OR b.currency_id = CAST(:moneda AS uuid))
         AND (CAST(:desde AS timestamptz) IS NULL OR b.period_start >= CAST(:desde AS timestamptz))
         AND (CAST(:hasta AS timestamptz) IS NULL OR b.period_start <= CAST(:hasta AS timestamptz))
      """;

  private final EntityManager em;

  public JpaCommissionBatchQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<BatchRow> search(BatchFilter filtro, int offset, int limit) {
    Query consulta =
        em.createNativeQuery(
            COLUMNAS
                + FILTRO
                + " ORDER BY b.period_start DESC, b.id DESC OFFSET :offset LIMIT :limite");
    parametros(consulta, filtro);
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        consulta.setParameter("offset", offset).setParameter("limite", limit).getResultList();
    List<BatchRow> lotes = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      lotes.add(lote(f));
    }
    return lotes;
  }

  @Override
  public long count(BatchFilter filtro) {
    Query consulta = em.createNativeQuery("SELECT count(*) FROM commission_batches b" + FILTRO);
    parametros(consulta, filtro);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  @Override
  public Optional<BatchRow> find(UUID id, UUID owner) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(
                COLUMNAS
                    + " WHERE b.id = :id"
                    + " AND (CAST(:duenio AS uuid) IS NULL OR b.user_id = CAST(:duenio AS uuid))")
            .setParameter("id", id)
            .setParameter("duenio", owner)
            .getResultList();
    return filas.stream().findFirst().map(JpaCommissionBatchQueryRepository::lote);
  }

  /**
   * Las columnas de una comisión, comunes a las del lote y a las retiradas de él. <b>{@code o} es
   * el lote del que salió</b> (`RN-CM-046`) y <b>{@code a} el lote en que está</b>.
   */
  private static final String COMISION =
      """
      SELECT k.id, k.movement_detail_id, d.movement_id, m.code,
             COALESCE(d.product_id, s.product_id), COALESCE(d.product_name, p.name),
             k.chain_level, k.source, k.rate_id, k.rate_type,
             k.percentage, k.fixed_amount, k.unit_price, k.quantity,
             k.commission_amount, k.resolved_on, k.accrued_at,
             k.commission_kind, k.afftrack_settlement_id,
             o.id, o.code,
             a.id, a.code, a.status
        FROM commissions k
        JOIN commission_batches a ON a.id = k.batch_id
        LEFT JOIN commission_batches o ON o.id = k.withdrawn_from_batch_id
        LEFT JOIN movement_details d ON d.id = k.movement_detail_id
        LEFT JOIN movements m ON m.id = d.movement_id
        LEFT JOIN afftrack_settlements s ON s.id = k.afftrack_settlement_id
        LEFT JOIN products p ON p.id = s.product_id
      """;

  private static final String ORDEN_COMISIONES =
      " ORDER BY COALESCE(m.occurred_at, k.accrued_at), d.id, k.chain_level, k.id";

  @Override
  public List<CommissionRow> commissionsOf(UUID batchId) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(COMISION + " WHERE k.batch_id = :lote" + ORDEN_COMISIONES)
            .setParameter("lote", batchId)
            .getResultList();
    return filas.stream().map(JpaCommissionBatchQueryRepository::comision).toList();
  }

  @Override
  public List<WithdrawnRow> withdrawnFrom(UUID batchId) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(
                COMISION + " WHERE k.withdrawn_from_batch_id = :lote" + ORDEN_COMISIONES)
            .setParameter("lote", batchId)
            .getResultList();
    return filas.stream()
        .map(
            f ->
                new WithdrawnRow(
                    comision(f), (UUID) f[21], (String) f[22], BatchStatus.valueOf((String) f[23])))
        .toList();
  }

  private static CommissionRow comision(Object[] f) {
    return new CommissionRow(
        (UUID) f[0],
        (UUID) f[1],
        (UUID) f[2],
        (String) f[3],
        (UUID) f[4],
        (String) f[5],
        f[6] == null ? null : ((Number) f[6]).intValue(),
        (String) f[7],
        (UUID) f[8],
        (String) f[9],
        (BigDecimal) f[10],
        MinorUnits.fromMinor(f[11]),
        MinorUnits.fromMinor(f[12]),
        ((Number) f[13]).intValue(),
        MinorUnits.fromMinor(f[14]),
        fecha(f[15]),
        instante(f[16]),
        (String) f[17],
        (UUID) f[18],
        (UUID) f[19],
        (String) f[20]);
  }

  private static void parametros(Query consulta, BatchFilter filtro) {
    consulta
        .setParameter("estado", filtro.status() == null ? null : filtro.status().name())
        .setParameter("persona", filtro.userId())
        .setParameter("moneda", filtro.currencyId())
        .setParameter("desde", filtro.from())
        .setParameter("hasta", filtro.to());
  }

  private static BatchRow lote(Object[] f) {
    return new BatchRow(
        (UUID) f[0],
        (String) f[1],
        (UUID) f[2],
        (String) f[3],
        (String) f[4],
        (String) f[5],
        (UUID) f[6],
        (String) f[7],
        instante(f[8]),
        instante(f[9]),
        BatchStatus.valueOf((String) f[10]),
        MinorUnits.fromMinor(f[11]),
        ((Number) f[12]).longValue(),
        instante(f[13]),
        (UUID) f[14],
        MinorUnits.fromMinor(f[15]));
  }

  private static LocalDate fecha(Object valor) {
    if (valor instanceof LocalDate d) {
      return d;
    }
    return ((java.sql.Date) valor).toLocalDate();
  }

  private static OffsetDateTime instante(Object valor) {
    if (valor == null) {
      return null;
    }
    if (valor instanceof OffsetDateTime odt) {
      return odt;
    }
    if (valor instanceof java.time.Instant i) {
      return i.atOffset(java.time.ZoneOffset.UTC);
    }
    if (valor instanceof java.sql.Timestamp t) {
      return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
    throw new IllegalStateException("Tipo temporal inesperado: " + valor.getClass());
  }
}
