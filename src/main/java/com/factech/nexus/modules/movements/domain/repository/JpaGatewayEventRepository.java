package com.factech.nexus.modules.movements.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de las notificaciones de la pasarela. SQL nativo, como el resto de `MV`. */
@Repository
public class JpaGatewayEventRepository implements GatewayEventRepository {

  private static final int ERROR = 500;

  private final EntityManager em;

  public JpaGatewayEventRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean insertIfNew(
      UUID id, String gateway, String externalId, String type, String payload) {
    return em.createNativeQuery(
                """
                INSERT INTO gateway_events (id, gateway, external_id, type, payload)
                VALUES (:id, :pasarela, :externo, :tipo, CAST(:cuerpo AS jsonb))
                ON CONFLICT ON CONSTRAINT uq_gateway_events_externo DO NOTHING
                """)
            .setParameter("id", id)
            .setParameter("pasarela", gateway)
            .setParameter("externo", externalId)
            .setParameter("tipo", type)
            .setParameter("cuerpo", payload)
            .executeUpdate()
        == 1;
  }

  @Override
  public Optional<StoredEvent> lockPending(UUID id) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT id, gateway, type, CAST(payload AS text) AS payload, attempts
                  FROM gateway_events
                 WHERE id = :id AND processed_at IS NULL
                 FOR UPDATE SKIP LOCKED
                """,
                Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new StoredEvent(
                    (UUID) f.get("id"),
                    (String) f.get("gateway"),
                    (String) f.get("type"),
                    (String) f.get("payload"),
                    ((Number) f.get("attempts")).intValue()));
  }

  @Override
  public List<UUID> pendingForRetry(
      String gateway, OffsetDateTime receivedBefore, int maxAttempts, int limit) {
    @SuppressWarnings("unchecked")
    List<UUID> ids =
        em.createNativeQuery(
                """
                SELECT id FROM gateway_events
                 WHERE gateway = :pasarela AND processed_at IS NULL AND received_at < :antes
                   AND attempts < :maximo
                 ORDER BY received_at
                 LIMIT :limite
                """)
            .setParameter("pasarela", gateway)
            .setParameter("antes", receivedBefore)
            .setParameter("maximo", maxAttempts)
            .setParameter("limite", limit)
            .getResultList();
    return ids;
  }

  @Override
  public void finish(UUID id, String outcome, String error, UUID paymentId, OffsetDateTime at) {
    var consulta =
        em.createNativeQuery(
                "UPDATE gateway_events SET outcome = :desenlace, error = :error,"
                    + " processed_at = :ahora"
                    + (paymentId == null ? "" : ", payment_id = :pago")
                    + " WHERE id = :id")
            .setParameter("id", id)
            .setParameter("desenlace", outcome)
            .setParameter("error", recortar(error))
            .setParameter("ahora", at);
    if (paymentId != null) {
      consulta.setParameter("pago", paymentId);
    }
    consulta.executeUpdate();
  }

  @Override
  public int failAttempt(UUID id, String error, int maxAttempts, OffsetDateTime at) {
    @SuppressWarnings("unchecked")
    List<Object> intentos =
        em.createNativeQuery(
                """
                UPDATE gateway_events
                   SET attempts = attempts + 1, error = :error,
                       outcome = CASE WHEN attempts + 1 >= :maximo THEN 'ERROR' END,
                       processed_at = CASE WHEN attempts + 1 >= :maximo THEN CAST(:ahora AS timestamptz) END
                 WHERE id = :id AND processed_at IS NULL
                RETURNING attempts
                """)
            .setParameter("id", id)
            .setParameter("error", recortar(error))
            .setParameter("maximo", maxAttempts)
            .setParameter("ahora", at)
            .getResultList();
    return intentos.isEmpty() ? maxAttempts : ((Number) intentos.get(0)).intValue();
  }

  private static String recortar(String texto) {
    return texto == null || texto.length() <= ERROR ? texto : texto.substring(0, ERROR);
  }
}
