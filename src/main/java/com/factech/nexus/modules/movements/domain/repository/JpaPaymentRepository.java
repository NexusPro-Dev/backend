package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.shared.persistence.MinorUnits;
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

  private static final String PENDIENTE =
      """
      SELECT p.id AS pago, m.id AS movimiento, m.code AS codigo, t.code AS tipo,
             m.status AS estado, pm.code AS metodo, pm.gateway AS pasarela, p.amount AS importe,
             c.code AS moneda, c.decimal_places AS decimales, p.idempotency_key AS clave,
             p.provider_reference AS referencia
        FROM movements m
        JOIN movement_types t ON t.id = m.movement_type_id
        JOIN payments p ON p.movement_id = m.id AND p.status = 'PENDIENTE'
        JOIN payment_methods pm ON pm.id = p.payment_method_id
        JOIN currencies c ON c.id = m.currency_id
       WHERE m.id = :id AND t.code IN ('VENTA', 'COMPRA_PUNTOS')
      """;

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
                    (UUID) f.get("id"),
                    (String) f.get("status"),
                    MinorUnits.fromMinor(f.get("importe"))));
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
            .setParameter("importe", MinorUnits.toMinor(amount))
            .setParameter("clave", idempotencyKey)
            .setParameter("ahora", at)
            .executeUpdate();
    return filas == 1;
  }

  @Override
  @Transactional
  public void insertConfirmed(
      UUID paymentId,
      UUID movementId,
      UUID paymentMethodId,
      BigDecimal amount,
      String idempotencyKey,
      String providerReference,
      OffsetDateTime at) {
    em.createNativeQuery(
            """
            INSERT INTO payments (id, movement_id, payment_method_id, status, amount,
                                  idempotency_key, provider_reference, occurred_at,
                                  confirmed_at, created_at)
            VALUES (:id, :movimiento, :metodo, 'CONFIRMADO', :importe, :clave, :referencia,
                    :ahora, :ahora, :ahora)
            """)
        .setParameter("id", paymentId)
        .setParameter("movimiento", movementId)
        .setParameter("metodo", paymentMethodId)
        .setParameter("importe", MinorUnits.toMinor(amount))
        .setParameter("clave", idempotencyKey)
        .setParameter("referencia", providerReference)
        .setParameter("ahora", at)
        .executeUpdate();
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

  // ---------------------------------------------------------------------------
  // La pasarela de la tarjeta (`RF-MV-040` a `RF-MV-042`)
  // ---------------------------------------------------------------------------

  @Override
  public void setProviderReference(UUID paymentId, String reference) {
    em.createNativeQuery("UPDATE payments SET provider_reference = :ref WHERE id = :id")
        .setParameter("id", paymentId)
        .setParameter("ref", reference)
        .executeUpdate();
  }

  @Override
  public Optional<PendingPayment> lockPendingOf(UUID movementId) {
    return pendiente(PENDIENTE + " FOR UPDATE OF m", movementId, null);
  }

  @Override
  public Optional<PendingPayment> lockPendingOwn(UUID movementId, UUID actorId) {
    return pendiente(PENDIENTE + " AND m.user_id = :actor FOR UPDATE OF m", movementId, actorId);
  }

  private Optional<PendingPayment> pendiente(String sql, UUID movementId, UUID actorId) {
    if (movementId == null) {
      return Optional.empty();
    }
    var consulta = em.createNativeQuery(sql, Tuple.class).setParameter("id", movementId);
    if (actorId != null) {
      consulta.setParameter("actor", actorId);
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new PendingPayment(
                    (UUID) f.get("pago"),
                    (UUID) f.get("movimiento"),
                    (String) f.get("codigo"),
                    (String) f.get("tipo"),
                    (String) f.get("estado"),
                    (String) f.get("metodo"),
                    (String) f.get("pasarela"),
                    MinorUnits.fromMinor(f.get("importe")),
                    ((String) f.get("moneda")).trim(),
                    ((Number) f.get("decimales")).intValue(),
                    (String) f.get("clave"),
                    (String) f.get("referencia")));
  }

  @Override
  public Optional<UUID> lockMovementOf(UUID paymentId) {
    if (paymentId == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<Object> filas =
        em.createNativeQuery(
                """
                SELECT m.id FROM movements m
                 WHERE m.id = (SELECT p.movement_id FROM payments p WHERE p.id = :pago)
                   FOR UPDATE
                """)
            .setParameter("pago", paymentId)
            .getResultList();
    return filas.stream().findFirst().map(UUID.class::cast);
  }

  @Override
  public Optional<PaymentTarget> findTarget(UUID paymentId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS pago, p.movement_id AS movimiento, t.code AS tipo,
                       p.status AS estado, pm.gateway AS pasarela, p.provider_reference AS referencia
                  FROM payments p
                  JOIN movements m ON m.id = p.movement_id
                  JOIN movement_types t ON t.id = m.movement_type_id
                  JOIN payment_methods pm ON pm.id = p.payment_method_id
                 WHERE p.id = :pago
                """,
                Tuple.class)
            .setParameter("pago", paymentId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new PaymentTarget(
                    (UUID) f.get("pago"),
                    (UUID) f.get("movimiento"),
                    (String) f.get("tipo"),
                    (String) f.get("estado"),
                    (String) f.get("pasarela"),
                    (String) f.get("referencia")));
  }

  @Override
  public boolean isOwnChargeable(UUID movementId, UUID actorId) {
    return movementId != null
        && !em.createNativeQuery(
                """
                SELECT 1 FROM movements m
                  JOIN movement_types t ON t.id = m.movement_type_id
                 WHERE m.id = :id AND m.user_id = :actor AND t.code IN ('VENTA', 'COMPRA_PUNTOS')
                """)
            .setParameter("id", movementId)
            .setParameter("actor", actorId)
            .getResultList()
            .isEmpty();
  }

  @Override
  public Optional<ReferencedPayment> findByReference(String reference) {
    if (reference == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS pago, m.id AS movimiento, t.code AS tipo, p.status AS estado,
                       p.amount AS importe, c.code AS moneda, c.decimal_places AS decimales
                  FROM payments p
                  JOIN movements m ON m.id = p.movement_id
                  JOIN movement_types t ON t.id = m.movement_type_id
                  JOIN currencies c ON c.id = m.currency_id
                 WHERE p.provider_reference = :ref
                 ORDER BY p.created_at DESC
                 LIMIT 1
                """,
                Tuple.class)
            .setParameter("ref", reference)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new ReferencedPayment(
                    (UUID) f.get("pago"),
                    (UUID) f.get("movimiento"),
                    (String) f.get("tipo"),
                    (String) f.get("estado"),
                    MinorUnits.fromMinor(f.get("importe")),
                    ((String) f.get("moneda")).trim(),
                    ((Number) f.get("decimales")).intValue()));
  }

  @Override
  public boolean setIncident(
      UUID paymentId, String incident, BigDecimal refunded, OffsetDateTime at) {
    return em.createNativeQuery(
                """
                UPDATE payments
                   SET incident = :incidencia, incident_at = :ahora,
                       refunded_amount = CAST(:devuelto AS bigint)
                 WHERE id = :id AND status = 'CONFIRMADO'
                """)
            .setParameter("id", paymentId)
            .setParameter("incidencia", incident)
            .setParameter("ahora", at)
            // En centésimas (ADR-006), y como texto por lo mismo que antes: un nulo sin tipo.
            .setParameter(
                "devuelto", refunded == null ? null : String.valueOf(MinorUnits.toMinor(refunded)))
            .executeUpdate()
        == 1;
  }
}
