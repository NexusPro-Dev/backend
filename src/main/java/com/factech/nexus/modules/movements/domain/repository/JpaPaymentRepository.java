package com.factech.nexus.modules.movements.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de los intentos de pago. SQL nativo, como {@link JpaMovementRepository}. */
@Repository
public class JpaPaymentRepository implements PaymentRepository {

  private final EntityManager em;

  public JpaPaymentRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<KeyedPayment> findByKey(String idempotencyKey) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT id, movement_id, payment_method_id FROM payments"
                    + " WHERE idempotency_key = :clave",
                Tuple.class)
            .setParameter("clave", idempotencyKey)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new KeyedPayment(
                    (UUID) f.get("id"),
                    (UUID) f.get("movement_id"),
                    (UUID) f.get("payment_method_id")));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RetryTarget> findOwnSale(UUID movementId, UUID actorId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT m.id AS id, m.status AS status, m.payable_amount AS importe
                  FROM movements m
                  JOIN movement_types t ON t.id = m.movement_type_id
                 WHERE m.id = :id AND m.user_id = :actor AND t.code = 'VENTA'
                """,
                Tuple.class)
            .setParameter("id", movementId)
            .setParameter("actor", actorId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new RetryTarget(
                    (UUID) f.get("id"), (String) f.get("status"), (BigDecimal) f.get("importe")));
  }

  @Override
  @Transactional
  public boolean open(
      UUID paymentId,
      UUID movementId,
      UUID paymentMethodId,
      BigDecimal amount,
      String idempotencyKey,
      OffsetDateTime at) {
    int filas =
        em.createNativeQuery(
                """
                INSERT INTO payments (id, movement_id, payment_method_id, status, amount,
                                      idempotency_key, occurred_at, created_at)
                VALUES (:id, :movimiento, :metodo, 'PENDIENTE', :importe, :clave, :ahora, :ahora)
                ON CONFLICT DO NOTHING
                """)
            .setParameter("id", paymentId)
            .setParameter("movimiento", movementId)
            .setParameter("metodo", paymentMethodId)
            .setParameter("importe", amount)
            .setParameter("clave", idempotencyKey)
            .setParameter("ahora", at)
            .executeUpdate();
    return filas == 1;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasPending(UUID movementId) {
    Object hay =
        em.createNativeQuery(
                "SELECT EXISTS (SELECT 1 FROM payments WHERE movement_id = :id"
                    + " AND status = 'PENDIENTE')")
            .setParameter("id", movementId)
            .getSingleResult();
    return Boolean.TRUE.equals(hay);
  }

  @Override
  @Transactional
  public boolean rejectPendingOfSale(UUID movementId, OffsetDateTime at, String reason) {
    // LA VENTA SE BLOQUEA PRIMERO: es la fila que confirmar actualiza antes de
    // tocar el pago. Con las dos operaciones haciendo cola en la misma fila, una
    // ve el resultado de la otra y ninguna trabaja sobre un pago ya resuelto.
    @SuppressWarnings("unchecked")
    List<Object> pendiente =
        em.createNativeQuery(
                """
                SELECT m.id FROM movements m
                  JOIN movement_types t ON t.id = m.movement_type_id
                 WHERE m.id = :id AND m.status = 'PENDIENTE' AND t.code = 'VENTA'
                 FOR UPDATE OF m
                """)
            .setParameter("id", movementId)
            .getResultList();
    if (pendiente.isEmpty()) {
      return false;
    }
    int filas =
        em.createNativeQuery(
                """
                UPDATE payments
                   SET status = 'RECHAZADO', rejected_at = :ahora, rejection_reason = :motivo
                 WHERE movement_id = :id AND status = 'PENDIENTE'
                """)
            .setParameter("id", movementId)
            .setParameter("ahora", at)
            .setParameter("motivo", reason)
            .executeUpdate();
    return filas == 1;
  }
}
