package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.shared.persistence.MinorUnits;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.time.BusinessCalendar;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link CommissionBatchRepository} sobre SQL nativo.
 *
 * <p><b>La apertura va contra la restricción de exclusión y no contra una lectura</b> (`plan.md`
 * §4): dos devengos simultáneos de la misma persona y moneda leerían los dos «no hay lote abierto».
 * {@code ON CONFLICT DO NOTHING} sin columnas es la única forma que Postgres admite contra un
 * {@code EXCLUDE}, y es exactamente la que hace falta: el segundo {@code INSERT} espera al primero,
 * no inserta nada, y la relectura encuentra el del primero.
 */
@Repository
public class JpaCommissionBatchRepository implements CommissionBatchRepository {

  private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final int INTENTOS = 3;

  private final EntityManager em;
  private final UuidV7Generator ids;
  private final BusinessCalendar calendario;
  private final SecureRandom azar = new SecureRandom();

  public JpaCommissionBatchRepository(
      EntityManager em, UuidV7Generator ids, BusinessCalendar calendario) {
    this.em = em;
    this.ids = ids;
    this.calendario = calendario;
  }

  @Override
  public OpenBatch lockOpenBatch(UUID userId, UUID currencyId, OffsetDateTime at) {
    for (int intento = 0; intento < INTENTOS; intento++) {
      @SuppressWarnings("unchecked")
      List<Object[]> abierto =
          em.createNativeQuery(
                  """
                  SELECT id, period_start FROM commission_batches
                   WHERE user_id = :persona AND currency_id = :moneda AND status = 'ABIERTO'
                     FOR UPDATE
                  """)
              .setParameter("persona", userId)
              .setParameter("moneda", currencyId)
              .getResultList();
      if (!abierto.isEmpty()) {
        Object[] fila = abierto.get(0);
        return new OpenBatch((UUID) fila[0], instante(fila[1]));
      }
      // Cero filas: o lo abrió otro a la vez (el EXCLUDE), o chocó el código
      // aleatorio (uq_commission_batches_code). En los dos casos se vuelve a
      // leer, y en el segundo con otro código.
      em.createNativeQuery(
              """
              INSERT INTO commission_batches
                  (id, code, user_id, currency_id, period_start, status, total_amount,
                   created_at, updated_at)
              SELECT :id, :codigo, :persona, :moneda,
                     GREATEST(CAST(:at AS timestamptz),
                              COALESCE(max(b.period_end), CAST(:at AS timestamptz))),
                     'ABIERTO', 0, :at, :at
                FROM commission_batches b
               WHERE b.user_id = :persona AND b.currency_id = :moneda
              ON CONFLICT DO NOTHING
              """)
          .setParameter("id", ids.next())
          .setParameter("codigo", codigo(at))
          .setParameter("persona", userId)
          .setParameter("moneda", currencyId)
          .setParameter("at", at)
          .executeUpdate();
    }
    throw new IllegalStateException(
        "No se pudo abrir el lote de "
            + userId
            + " en "
            + currencyId
            + " tras "
            + INTENTOS
            + " intentos.");
  }

  @Override
  public OpenBatch lockLatestUnpaidBatch(UUID userId, UUID currencyId, OffsetDateTime at) {
    for (int intento = 0; intento < INTENTOS; intento++) {
      @SuppressWarnings("unchecked")
      List<Object> candidato =
          em.createNativeQuery(
                  """
                  SELECT id FROM commission_batches
                   WHERE user_id = :persona AND currency_id = :moneda
                     AND status IN ('ABIERTO', 'PENDIENTE')
                   ORDER BY period_start DESC
                   LIMIT 1
                  """)
              .setParameter("persona", userId)
              .setParameter("moneda", currencyId)
              .getResultList();
      if (candidato.isEmpty()) {
        return lockOpenBatch(userId, currencyId, at);
      }
      // Postgres reevalúa el filtro tras esperar un bloqueo: con FOR UPDATE y
      // LIMIT 1 en la misma sentencia, un pago concurrente dejaría cero filas
      // aunque hubiera un pendiente más antiguo sin pagar.
      @SuppressWarnings("unchecked")
      List<Object[]> bloqueado =
          em.createNativeQuery(
                  """
                  SELECT id, period_start, status FROM commission_batches
                   WHERE id = :id
                     FOR UPDATE
                  """)
              .setParameter("id", candidato.get(0))
              .getResultList();
      // Cero filas: lo borró otra transacción entre las dos sentencias, por
      // vacío (`RF-CM-027`). Se vuelve a buscar.
      if (bloqueado.isEmpty()) {
        continue;
      }
      Object[] fila = bloqueado.get(0);
      if (!"PAGADO".equals(fila[2])) {
        return new OpenBatch((UUID) fila[0], instante(fila[1]));
      }
    }
    throw new IllegalStateException(
        "No se encontró un lote sin pagar de " + userId + " en " + currencyId + ".");
  }

  @Override
  public void addToTotal(UUID batchId, BigDecimal amount, OffsetDateTime at) {
    em.createNativeQuery(
            """
            UPDATE commission_batches
               SET total_amount = total_amount + :importe, updated_at = :at
             WHERE id = :id
            """)
        .setParameter("id", batchId)
        .setParameter("importe", MinorUnits.toMinor(amount))
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public java.util.Optional<BatchToPay> lockForPayment(UUID batchId) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(
                """
                SELECT id, code, user_id, currency_id, total_amount, status
                  FROM commission_batches
                 WHERE id = :id
                   FOR UPDATE
                """)
            .setParameter("id", batchId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new BatchToPay(
                    (UUID) f[0],
                    (String) f[1],
                    (UUID) f[2],
                    (UUID) f[3],
                    MinorUnits.fromMinor(f[4]),
                    (String) f[5]));
  }

  @Override
  public void markPaid(UUID batchId, OffsetDateTime at, UUID movementId) {
    em.createNativeQuery(
            """
            UPDATE commission_batches
               SET status = 'PAGADO', paid_at = :at, movement_id = :movimiento, updated_at = :at
             WHERE id = :id AND status = 'PENDIENTE'
            """)
        .setParameter("id", batchId)
        .setParameter("at", at)
        .setParameter("movimiento", movementId)
        .executeUpdate();
  }

  @Override
  public java.util.Optional<LockedCommission> lockCommission(UUID commissionId) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(
                """
                SELECT id, batch_id, commission_amount, withdrawn_from_batch_id
                  FROM commissions
                 WHERE id = :id
                   FOR UPDATE
                """)
            .setParameter("id", commissionId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new LockedCommission(
                    (UUID) f[0], (UUID) f[1], MinorUnits.fromMinor(f[2]), (UUID) f[3]));
  }

  @Override
  public List<LockedBatch> lockBatches(java.util.Collection<UUID> batchIds) {
    if (batchIds.isEmpty()) {
      return List.of();
    }
    // ORDER BY id con FOR UPDATE: Postgres toma los bloqueos en el orden en que
    // devuelve las filas, que es el que evita el interbloqueo (plan.md §1).
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(
                """
                SELECT id, code, user_id, currency_id, status
                  FROM commission_batches
                 WHERE id IN (:ids)
                 ORDER BY id
                   FOR UPDATE
                """)
            .setParameter("ids", new java.util.HashSet<>(batchIds))
            .getResultList();
    return filas.stream()
        .map(
            f ->
                new LockedBatch(
                    (UUID) f[0], (String) f[1], (UUID) f[2], (UUID) f[3], (String) f[4]))
        .toList();
  }

  @Override
  public void moveCommission(UUID commissionId, UUID toBatchId, UUID withdrawnFromBatchId) {
    em.createNativeQuery(
            """
            UPDATE commissions
               SET batch_id = :lote, withdrawn_from_batch_id = CAST(:origen AS uuid)
             WHERE id = :id
            """)
        .setParameter("id", commissionId)
        .setParameter("lote", toBatchId)
        .setParameter("origen", withdrawnFromBatchId)
        .executeUpdate();
  }

  @Override
  public boolean hasLiveCommissions(UUID batchId) {
    return (Boolean)
        em.createNativeQuery("SELECT EXISTS (SELECT 1 FROM commissions WHERE batch_id = :lote)")
            .setParameter("lote", batchId)
            .getSingleResult();
  }

  @Override
  public java.util.Optional<DeletedBatch> deleteIfEmpty(UUID batchId) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(
                """
                DELETE FROM commission_batches b
                 WHERE b.id = :lote AND b.status <> 'PAGADO'
                   AND NOT EXISTS (SELECT 1 FROM commissions c WHERE c.batch_id = b.id)
                RETURNING b.id, b.code, b.user_id, b.currency_id, b.status,
                          b.period_start, b.period_end
                """)
            .setParameter("lote", batchId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new DeletedBatch(
                    (UUID) f[0],
                    (String) f[1],
                    (UUID) f[2],
                    (UUID) f[3],
                    (String) f[4],
                    instante(f[5]),
                    f[6] == null ? null : instante(f[6])));
  }

  @Override
  public List<UUID> findEmptyUnpaidBatchIds() {
    @SuppressWarnings("unchecked")
    List<UUID> ids =
        em.createNativeQuery(
                """
                SELECT b.id FROM commission_batches b
                 WHERE b.status IN ('ABIERTO', 'PENDIENTE')
                   AND NOT EXISTS (SELECT 1 FROM commissions c WHERE c.batch_id = b.id)
                 ORDER BY b.id
                """)
            .getResultList();
    return ids;
  }

  @Override
  public void lockWithdrawnFrom(java.util.Collection<UUID> batchIds) {
    if (batchIds.isEmpty()) {
      return;
    }
    em.createNativeQuery(
            """
            SELECT id FROM commissions
             WHERE withdrawn_from_batch_id IN (:ids)
             ORDER BY id
               FOR UPDATE
            """)
        .setParameter("ids", new java.util.HashSet<>(batchIds))
        .getResultList();
  }

  private static OffsetDateTime instante(Object valor) {
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

  /**
   * {@code LOT-<día del negocio>-<seis>}: la forma de {@code MovementCode}, sin nombre de nadie.
   */
  private String codigo(OffsetDateTime at) {
    StringBuilder sufijo = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      sufijo.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
    }
    return "LOT-" + calendario.diaDe(at).format(DIA) + "-" + sufijo;
  }
}
