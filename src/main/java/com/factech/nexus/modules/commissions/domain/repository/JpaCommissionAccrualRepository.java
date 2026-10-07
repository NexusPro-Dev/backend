package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.AccrualOutcome;
import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.shared.persistence.MinorUnits;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link CommissionAccrualRepository} sobre SQL nativo.
 *
 * <p><b>Sin {@code @Transactional}</b>: cada método corre en la transacción de la línea que abre el
 * servicio, y el bloqueo de {@link #lockLine} solo sirve si dura lo que ella.
 */
@Repository
public class JpaCommissionAccrualRepository implements CommissionAccrualRepository {

  /** Espacio del bloqueo consultivo del devengo, distinto del de las tasas. */
  private static final int ESPACIO_DEVENGO = 4313;

  private final EntityManager em;
  private final UuidV7Generator ids;

  public JpaCommissionAccrualRepository(EntityManager em, UuidV7Generator ids) {
    this.em = em;
    this.ids = ids;
  }

  @Override
  public void lockLine(UUID detailId) {
    em.createNativeQuery("SELECT pg_advisory_xact_lock(:ns, hashtext(CAST(:linea AS text)))")
        .setParameter("ns", ESPACIO_DEVENGO)
        .setParameter("linea", detailId.toString())
        .getSingleResult();
  }

  @Override
  public Optional<AccrualRow> find(UUID detailId) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(
                "SELECT outcome, attempts FROM commission_accruals WHERE movement_detail_id = :id")
            .setParameter("id", detailId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new AccrualRow(
                    detailId, AccrualOutcome.valueOf((String) f[0]), ((Number) f[1]).intValue()));
  }

  @Override
  public Set<UUID> withOutcome(Collection<UUID> detailIds) {
    if (detailIds == null || detailIds.isEmpty()) {
      return Set.of();
    }
    @SuppressWarnings("unchecked")
    List<UUID> ids =
        em.createNativeQuery(
                "SELECT movement_detail_id FROM commission_accruals"
                    + " WHERE movement_detail_id IN (:ids)")
            .setParameter("ids", detailIds)
            .getResultList();
    return new HashSet<>(ids);
  }

  @Override
  public List<UUID> rejected() {
    @SuppressWarnings("unchecked")
    List<UUID> ids =
        em.createNativeQuery(
                "SELECT movement_detail_id FROM commission_accruals WHERE outcome = 'RECHAZADA'"
                    + " ORDER BY movement_detail_id")
            .getResultList();
    return ids;
  }

  @Override
  public void insertOutcome(
      UUID detailId, AccrualOutcome outcome, String reason, OffsetDateTime at) {
    em.createNativeQuery(
            """
            INSERT INTO commission_accruals
                (movement_detail_id, outcome, reason, attempts, created_at, updated_at)
            VALUES (:id, :outcome, :reason, 1, :at, :at)
            """)
        .setParameter("id", detailId)
        .setParameter("outcome", outcome.name())
        .setParameter("reason", reason)
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public void updateOutcome(
      UUID detailId, AccrualOutcome outcome, String reason, OffsetDateTime at) {
    em.createNativeQuery(
            """
            UPDATE commission_accruals
               SET outcome = :outcome, reason = :reason, attempts = attempts + 1, updated_at = :at
             WHERE movement_detail_id = :id
            """)
        .setParameter("id", detailId)
        .setParameter("outcome", outcome.name())
        .setParameter("reason", reason)
        .setParameter("at", at)
        .executeUpdate();
  }

  /**
   * Una comisión de venta: siempre {@code POR_VENTA} (`RN-CM-044`). Se declara aunque sea la única
   * clase que este camino escribe, porque `V54` retiró el valor por omisión de la columna.
   */
  @Override
  public void insertCommission(NewCommission c) {
    boolean porcentaje = c.rateType() == CommissionRateType.PORCENTAJE;
    em.createNativeQuery(
            """
            INSERT INTO commissions
                (id, batch_id, commission_kind, movement_detail_id, user_id, chain_level, source,
                 rate_id, resolved_on, rate_type, percentage, fixed_amount, unit_price, quantity,
                 commission_amount, accrued_at, created_at)
            VALUES (:id, :lote, 'POR_VENTA', :linea, :persona, :nivel, :fuente, :tasa,
                    :fecha, :tipo, CAST(:porcentaje AS numeric), CAST(:fijo AS bigint),
                    :precio, :cantidad, :importe, :at, :at)
            """)
        .setParameter("id", ids.next())
        .setParameter("lote", c.batchId())
        .setParameter("linea", c.detailId())
        .setParameter("persona", c.userId())
        .setParameter("nivel", c.chainLevel())
        .setParameter("fuente", c.source().name())
        .setParameter("tasa", c.rateId())
        .setParameter("fecha", c.resolvedOn())
        .setParameter("tipo", c.rateType().name())
        .setParameter("porcentaje", porcentaje ? c.value() : null)
        .setParameter("fijo", porcentaje ? null : MinorUnits.toMinor(c.value()))
        .setParameter("precio", MinorUnits.toMinor(c.unitPrice()))
        .setParameter("cantidad", c.quantity())
        .setParameter("importe", MinorUnits.toMinor(c.amount()))
        .setParameter("at", c.accruedAt())
        .executeUpdate();
  }

  @Override
  public boolean hasCountedFtd(UUID detailId) {
    return (Boolean)
        em.createNativeQuery(
                "SELECT EXISTS (SELECT 1 FROM afftrack_ftds WHERE movement_detail_id = :linea)")
            .setParameter("linea", detailId)
            .getSingleResult();
  }

  @Override
  public List<LiveCommission> lockLiveCommissionsOf(UUID detailId) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(
                """
                SELECT id, batch_id, user_id, commission_amount
                  FROM commissions
                 WHERE movement_detail_id = :linea
                 ORDER BY id
                   FOR UPDATE
                """)
            .setParameter("linea", detailId)
            .getResultList();
    return filas.stream()
        .map(
            f ->
                new LiveCommission(
                    (UUID) f[0], (UUID) f[1], (UUID) f[2], MinorUnits.fromMinor(f[3])))
        .toList();
  }

  @Override
  public void delete(Collection<UUID> commissionIds) {
    if (commissionIds.isEmpty()) {
      return;
    }
    em.createNativeQuery("DELETE FROM commissions WHERE id IN (:ids)")
        .setParameter("ids", new java.util.HashSet<>(commissionIds))
        .executeUpdate();
  }

  @Override
  public void deleteOutcome(UUID detailId) {
    em.createNativeQuery("DELETE FROM commission_accruals WHERE movement_detail_id = :linea")
        .setParameter("linea", detailId)
        .executeUpdate();
  }

  @Override
  public void markReattributed(UUID detailId, OffsetDateTime at) {
    em.createNativeQuery(
            """
            INSERT INTO commission_reattributions (movement_detail_id, released_at)
            VALUES (:linea, :at)
            ON CONFLICT (movement_detail_id) DO UPDATE SET released_at = EXCLUDED.released_at
            """)
        .setParameter("linea", detailId)
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public boolean isReattributed(UUID detailId) {
    return !em.createNativeQuery(
            "SELECT 1 FROM commission_reattributions WHERE movement_detail_id = :linea")
        .setParameter("linea", detailId)
        .getResultList()
        .isEmpty();
  }

  @Override
  public void clearReattribution(UUID detailId) {
    em.createNativeQuery("DELETE FROM commission_reattributions WHERE movement_detail_id = :linea")
        .setParameter("linea", detailId)
        .executeUpdate();
  }
}
