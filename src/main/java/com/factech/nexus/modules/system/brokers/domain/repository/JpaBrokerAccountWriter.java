package com.factech.nexus.modules.system.brokers.domain.repository;

import com.factech.nexus.modules.system.brokers.domain.models.BrokerAccountKind;
import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link BrokerAccountWriter} en SQL nativo, como el resto del submódulo.
 *
 * <p><b>El {@code flush} es explícito</b> por lo mismo que en {@code JpaBrokerAccountRegistrar}:
 * sin él, la violación de {@code uq_user_brokers_cuenta} saldría al confirmar, fuera de este {@code
 * catch}, y el actor recibiría un {@code 500} sobre una regla que el sistema sabe explicar.
 */
@Repository
public class JpaBrokerAccountWriter implements BrokerAccountWriter {

  private static final String UQ_CUENTA = "uq_user_brokers_cuenta";

  private final EntityManager em;

  public JpaBrokerAccountWriter(EntityManager em) {
    this.em = em;
  }

  @Override
  public void insert(
      UUID id, UUID userId, UUID brokerId, String accountId, BrokerAccountKind kind) {
    try {
      em.createNativeQuery(
              """
              INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind)
              VALUES (CAST(:id AS uuid), CAST(:usuario AS uuid), CAST(:broker AS uuid), :cuenta,
                      :tipo)
              """)
          .setParameter("id", id)
          .setParameter("usuario", userId)
          .setParameter("broker", brokerId)
          .setParameter("cuenta", accountId)
          .setParameter("tipo", kind.name())
          .executeUpdate();
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<BrokerAccountKind> kindFor(UUID userId) {
    List<?> tipos =
        em.createNativeQuery(
                """
                SELECT DISTINCT role_type FROM user_roles
                 WHERE user_id = CAST(:usuario AS uuid)
                   AND role_type IN ('VENDEDOR', 'CONSUMIDOR')
                """)
            .setParameter("usuario", userId)
            .getResultList();
    if (tipos.contains(BrokerAccountKind.VENDEDOR.name())) {
      return Optional.of(BrokerAccountKind.VENDEDOR);
    }
    if (tipos.contains(BrokerAccountKind.CONSUMIDOR.name())) {
      return Optional.of(BrokerAccountKind.CONSUMIDOR);
    }
    return Optional.empty();
  }

  @Override
  public Optional<LockedAccount> lock(UUID brokerAccountId, UUID userId) {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT ub.id, ub.user_id, ub.broker_id, b.name AS broker_name, ub.external_id,
                       ub.broker_username, ub.status, ub.kind
                  FROM user_brokers ub
                  JOIN brokers b ON b.id = ub.broker_id
                 WHERE ub.id = CAST(:id AS uuid) AND ub.user_id = CAST(:usuario AS uuid)
                   FOR UPDATE OF ub
                """,
                Tuple.class)
            .setParameter("id", brokerAccountId)
            .setParameter("usuario", userId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new LockedAccount(
                    (UUID) f.get("id"),
                    (UUID) f.get("user_id"),
                    (UUID) f.get("broker_id"),
                    (String) f.get("broker_name"),
                    (String) f.get("external_id"),
                    (String) f.get("broker_username"),
                    UserBrokerStatus.valueOf((String) f.get("status")),
                    BrokerAccountKind.valueOf((String) f.get("kind"))));
  }

  @Override
  public void updateAccountId(UUID brokerAccountId, String accountId) {
    try {
      em.createNativeQuery(
              """
              UPDATE user_brokers SET external_id = :cuenta, updated_at = now()
               WHERE id = CAST(:id AS uuid)
              """)
          .setParameter("id", brokerAccountId)
          .setParameter("cuenta", accountId)
          .executeUpdate();
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public void delete(UUID brokerAccountId) {
    em.createNativeQuery("DELETE FROM user_brokers WHERE id = CAST(:id AS uuid)")
        .setParameter("id", brokerAccountId)
        .executeUpdate();
  }

  private static RuntimeException traducir(PersistenceException fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
          && UQ_CUENTA.equals(violacion.getConstraintName())) {
        String mensaje = "Esa cuenta de broker ya está declarada.";
        return new BusinessRuleException(
            "EX-009", mensaje, List.of(new FieldError("accountId", "EX-009", mensaje)));
      }
    }
    return fallo;
  }
}
