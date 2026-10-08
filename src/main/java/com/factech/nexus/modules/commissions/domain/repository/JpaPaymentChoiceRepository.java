package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.PaymentMode;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link PaymentChoiceRepository} sobre SQL nativo, como {@link JpaCommissionClosingRepository}.
 *
 * <p><b>Sin {@code @Transactional}</b>: el bloqueo del turno solo sirve dentro de la transacción de
 * quien lo toma.
 */
@Repository
public class JpaPaymentChoiceRepository implements PaymentChoiceRepository {

  /**
   * Espacio del bloqueo del turno. Distinto del del cierre (4309), que se toma con la clave fija
   * {@code 0}: aquí la clave es el turno, en minutos desde la época, y no choca con él.
   */
  private static final int ESPACIO_TURNO = 4310;

  private final EntityManager em;

  public JpaPaymentChoiceRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Optional<PaymentChoice> find(OffsetDateTime scheduledFor) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(
                """
                SELECT scheduled_for, payment_mode, chosen_by, chosen_at
                  FROM commission_payment_choices
                 WHERE scheduled_for = :turno
                """)
            .setParameter("turno", scheduledFor)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new PaymentChoice(
                    instante(f[0]),
                    PaymentMode.valueOf((String) f[1]),
                    (UUID) f[2],
                    instante(f[3])));
  }

  @Override
  public UUID upsert(
      UUID id, OffsetDateTime scheduledFor, PaymentMode mode, UUID chosenBy, OffsetDateTime at) {
    return (UUID)
        em.createNativeQuery(
                """
            INSERT INTO commission_payment_choices
                   (id, scheduled_for, payment_mode, chosen_by, chosen_at, created_at)
            VALUES (:id, :turno, :modo, :persona, :at, :at)
            ON CONFLICT (scheduled_for) DO UPDATE
               SET payment_mode = EXCLUDED.payment_mode,
                   chosen_by    = EXCLUDED.chosen_by,
                   chosen_at    = EXCLUDED.chosen_at
            RETURNING id
            """)
            .setParameter("id", id)
            .setParameter("turno", scheduledFor)
            .setParameter("modo", mode.name())
            .setParameter("persona", chosenBy)
            .setParameter("at", at)
            .getSingleResult();
  }

  @Override
  public void lockTurn(OffsetDateTime scheduledFor) {
    em.createNativeQuery("SELECT pg_advisory_xact_lock(:ns, :clave)")
        .setParameter("ns", ESPACIO_TURNO)
        .setParameter("clave", (int) (scheduledFor.toEpochSecond() / 60))
        .getSingleResult();
  }

  private static OffsetDateTime instante(Object valor) {
    if (valor instanceof OffsetDateTime odt) {
      return odt;
    }
    if (valor instanceof java.time.Instant i) {
      return i.atOffset(ZoneOffset.UTC);
    }
    if (valor instanceof java.sql.Timestamp t) {
      return t.toInstant().atOffset(ZoneOffset.UTC);
    }
    throw new IllegalStateException("Tipo temporal inesperado: " + valor);
  }
}
