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

/** SQL nativo, como {@link JpaPaymentRepository}: los importes en centésimas (`ADR-006`). */
@Repository
public class JpaLocalChargeRepository implements LocalChargeRepository {

  private final EntityManager em;

  public JpaLocalChargeRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void setCharge(
      UUID paymentId,
      String reference,
      UUID currencyId,
      BigDecimal chargeAmount,
      UUID conversionRateId,
      String checkoutUrl) {
    em.createNativeQuery(
            """
            UPDATE payments
               SET provider_reference = :referencia, charge_currency_id = :moneda,
                   charge_amount = :importe, conversion_rate_id = :conversion,
                   checkout_url = :pagina
             WHERE id = :id
            """)
        .setParameter("id", paymentId)
        .setParameter("referencia", reference)
        .setParameter("moneda", currencyId)
        .setParameter("importe", MinorUnits.toMinor(chargeAmount))
        .setParameter("conversion", conversionRateId)
        .setParameter("pagina", checkoutUrl)
        .executeUpdate();
  }

  @Override
  public Optional<OpenCharge> findOpenCharge(UUID movementId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS pago, p.provider_reference AS referencia, p.checkout_url AS pagina,
                       c.id AS moneda, c.code AS codigo, p.charge_amount AS importe
                  FROM payments p
                  JOIN currencies c ON c.id = p.charge_currency_id
                 WHERE p.movement_id = :id AND p.status = 'PENDIENTE'
                   AND p.charge_currency_id IS NOT NULL
                """,
                Tuple.class)
            .setParameter("id", movementId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new OpenCharge(
                    (UUID) f.get("pago"),
                    (String) f.get("referencia"),
                    (String) f.get("pagina"),
                    (UUID) f.get("moneda"),
                    ((String) f.get("codigo")).trim(),
                    MinorUnits.fromMinor(f.get("importe"))));
  }

  @Override
  public Optional<PayerRow> findPayer(UUID movementId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT u.id AS persona, u.first_name AS nombre, u.last_name AS apellido,
                       u.email AS correo, u.document_number AS documento, u.phone AS telefono,
                       c.id AS pais, c.code AS codigo
                  FROM movements m
                  JOIN users u ON u.id = m.user_id
                  JOIN countries c ON c.id = u.country_id
                 WHERE m.id = :id
                """,
                Tuple.class)
            .setParameter("id", movementId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new PayerRow(
                    (UUID) f.get("persona"),
                    (String) f.get("nombre"),
                    (String) f.get("apellido"),
                    (String) f.get("correo"),
                    (String) f.get("documento"),
                    (String) f.get("telefono"),
                    (UUID) f.get("pais"),
                    ((String) f.get("codigo")).trim()));
  }

  @Override
  public Optional<ReconcileTarget> lockForReconcile(UUID paymentId) {
    // El movimiento primero y en el mismo orden que la conciliación de siempre
    // (`RF-MV-044`): dos caminos que bloquean al revés se interbloquean.
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS pago, m.id AS movimiento, t.code AS tipo, p.status AS estado,
                       pm.gateway AS pasarela, p.charge_amount AS importe, c.code AS moneda,
                       c.decimal_places AS decimales
                  FROM payments p
                  JOIN movements m ON m.id = p.movement_id
                  JOIN movement_types t ON t.id = m.movement_type_id
                  JOIN payment_methods pm ON pm.id = p.payment_method_id
                  LEFT JOIN currencies c ON c.id = p.charge_currency_id
                 WHERE p.id = :id
                   FOR UPDATE OF m, p
                """,
                Tuple.class)
            .setParameter("id", paymentId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new ReconcileTarget(
                    (UUID) f.get("pago"),
                    (UUID) f.get("movimiento"),
                    (String) f.get("tipo"),
                    (String) f.get("estado"),
                    (String) f.get("pasarela"),
                    f.get("importe") == null ? null : MinorUnits.fromMinor(f.get("importe")),
                    f.get("moneda") == null ? null : ((String) f.get("moneda")).trim(),
                    f.get("decimales") == null ? 0 : ((Number) f.get("decimales")).intValue()));
  }

  @Override
  public List<UUID> toReconcile(OffsetDateTime openedBefore, int limit) {
    @SuppressWarnings("unchecked")
    List<UUID> ids =
        em.createNativeQuery(
                """
                SELECT p.id
                  FROM payments p
                 WHERE p.status = 'PENDIENTE' AND p.charge_currency_id IS NOT NULL
                   AND p.occurred_at < :antes
                 ORDER BY p.occurred_at
                 LIMIT :lote
                """)
            .setParameter("antes", openedBefore)
            .setParameter("lote", limit)
            .getResultList();
    return ids;
  }

  @Override
  public boolean markLateCharge(UUID paymentId, OffsetDateTime at) {
    return em.createNativeQuery(
                """
                UPDATE payments SET incident = 'COBRO_TARDIO', incident_at = :ahora
                 WHERE id = :id AND status = 'RECHAZADO' AND incident IS NULL
                """)
            .setParameter("id", paymentId)
            .setParameter("ahora", at)
            .executeUpdate()
        == 1;
  }
}
