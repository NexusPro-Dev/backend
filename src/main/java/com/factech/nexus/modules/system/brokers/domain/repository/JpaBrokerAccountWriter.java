package com.factech.nexus.modules.system.brokers.domain.repository;

import com.factech.nexus.modules.system.brokers.domain.models.BrokerAccountKind;
import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link BrokerAccountWriter} en SQL nativo, como el resto del submódulo.
 *
 * <p><b>El {@code flush} es explícito</b> por lo mismo que en {@code JpaBrokerAccountRegistrar}:
 * sin él, la violación de una restricción saldría al confirmar, fuera de este {@code catch}, y el
 * actor recibiría un {@code 500} sobre una regla que el sistema sabe explicar.
 */
@Repository
public class JpaBrokerAccountWriter implements BrokerAccountWriter {

  /** Restricción → código, campo y mensaje. */
  private static final Map<String, String[]> TRADUCCIONES =
      Map.of(
          "uq_user_brokers_cuenta",
          new String[] {"EX-009", "accountId", "Esa cuenta de broker ya está declarada."},
          "uq_user_brokers_afftrack",
          new String[] {"EX-014", "afftrack", "Ese afftrack ya es de otra cuenta en ese broker."},
          "uq_user_brokers_vendedor_por_broker",
          new String[] {
            "EX-015", "brokerId", "El vendedor ya tiene su cuenta de vendedor en ese broker."
          },
          "fk_user_brokers_origen",
          new String[] {
            "EX-012",
            "brokerAccountId",
            "Esta cuenta de vendedor originó cuentas de consumidores y no se puede borrar."
          });

  private static final String COLUMNAS =
      """
      SELECT ub.id, ub.user_id, ub.broker_id, b.name AS broker_name, ub.external_id,
             ub.broker_username, ub.status, ub.kind, ub.afftrack, ub.referrer_account_id
        FROM user_brokers ub
        JOIN brokers b ON b.id = ub.broker_id
      """;

  private final EntityManager em;

  public JpaBrokerAccountWriter(EntityManager em) {
    this.em = em;
  }

  @Override
  public void insert(
      UUID id,
      UUID userId,
      UUID brokerId,
      String accountId,
      BrokerAccountKind kind,
      String afftrack,
      UUID referrerAccountId) {
    escribir(
        em.createNativeQuery(
                """
                INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind, afftrack,
                                          referrer_account_id)
                VALUES (CAST(:id AS uuid), CAST(:usuario AS uuid), CAST(:broker AS uuid), :cuenta,
                        :tipo, :afftrack, CAST(:origen AS uuid))
                """)
            .setParameter("id", id)
            .setParameter("usuario", userId)
            .setParameter("broker", brokerId)
            .setParameter("cuenta", accountId)
            .setParameter("tipo", kind.name())
            .setParameter("afftrack", afftrack)
            .setParameter("origen", referrerAccountId));
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
  public Optional<UUID> principalSellerOf(UUID userId) {
    return primero(
        em.createNativeQuery(
                "SELECT seller_id FROM client_sellers"
                    + " WHERE client_id = CAST(:persona AS uuid) AND origin = 'REGISTRO'")
            .setParameter("persona", userId)
            .getResultList());
  }

  @Override
  public Optional<UUID> vendorAccount(UUID sellerId, UUID brokerId) {
    if (sellerId == null) {
      return Optional.empty();
    }
    return primero(
        em.createNativeQuery(
                """
                SELECT id FROM user_brokers
                 WHERE user_id = CAST(:vendedor AS uuid) AND broker_id = CAST(:broker AS uuid)
                   AND kind = 'VENDEDOR'
                """)
            .setParameter("vendedor", sellerId)
            .setParameter("broker", brokerId)
            .getResultList());
  }

  @Override
  public Optional<UUID> claim(UUID brokerId, String accountId, UUID userId, UUID sellerId) {
    if (sellerId == null) {
      return Optional.empty();
    }
    // Un solo UPDATE con las dos condiciones: comprobar y luego escribir dejaría
    // que dos personas se asociaran la misma cuenta a la vez.
    List<?> filas =
        em.createNativeQuery(
                """
                UPDATE user_brokers ub SET user_id = CAST(:persona AS uuid), updated_at = now()
                  FROM user_brokers origen
                 WHERE ub.broker_id = CAST(:broker AS uuid) AND ub.external_id = :cuenta
                   AND ub.user_id IS NULL
                   AND origen.id = ub.referrer_account_id
                   AND origen.user_id = CAST(:vendedor AS uuid)
                RETURNING ub.id
                """)
            .setParameter("persona", userId)
            .setParameter("broker", brokerId)
            .setParameter("cuenta", accountId)
            .setParameter("vendedor", sellerId)
            .getResultList();
    return primero(filas);
  }

  @Override
  public Optional<LockedAccount> lock(UUID brokerAccountId, UUID userId) {
    return bloqueada(
        em.createNativeQuery(
                COLUMNAS
                    + " WHERE ub.id = CAST(:id AS uuid) AND ub.user_id = CAST(:usuario AS uuid)"
                    + " FOR UPDATE OF ub",
                Tuple.class)
            .setParameter("id", brokerAccountId)
            .setParameter("usuario", userId)
            .getResultList());
  }

  @Override
  public Optional<LockedAccount> lockAny(UUID brokerAccountId) {
    return bloqueada(
        em.createNativeQuery(
                COLUMNAS + " WHERE ub.id = CAST(:id AS uuid) FOR UPDATE OF ub", Tuple.class)
            .setParameter("id", brokerAccountId)
            .getResultList());
  }

  @Override
  public void updateAccountId(UUID brokerAccountId, String accountId) {
    escribir(
        em.createNativeQuery(
                """
                UPDATE user_brokers SET external_id = :cuenta, updated_at = now()
                 WHERE id = CAST(:id AS uuid)
                """)
            .setParameter("id", brokerAccountId)
            .setParameter("cuenta", accountId));
  }

  @Override
  public void updateAfftrack(UUID brokerAccountId, String afftrack) {
    escribir(
        em.createNativeQuery(
                """
                UPDATE user_brokers SET afftrack = :afftrack, updated_at = now()
                 WHERE id = CAST(:id AS uuid)
                """)
            .setParameter("id", brokerAccountId)
            .setParameter("afftrack", afftrack));
  }

  @Override
  public void assignUser(UUID brokerAccountId, UUID userId) {
    escribir(
        em.createNativeQuery(
                """
                UPDATE user_brokers SET user_id = CAST(:persona AS uuid), updated_at = now()
                 WHERE id = CAST(:id AS uuid)
                """)
            .setParameter("id", brokerAccountId)
            .setParameter("persona", userId));
  }

  @Override
  public void delete(UUID brokerAccountId) {
    escribir(
        em.createNativeQuery("DELETE FROM user_brokers WHERE id = CAST(:id AS uuid)")
            .setParameter("id", brokerAccountId));
  }

  @Override
  public Optional<UUID> vendorAccountByAfftrack(UUID brokerId, String afftrack) {
    return primero(
        em.createNativeQuery(
                """
                SELECT id FROM user_brokers
                 WHERE broker_id = CAST(:broker AS uuid) AND lower(afftrack) = lower(:afftrack)
                   AND kind = 'VENDEDOR'
                """)
            .setParameter("broker", brokerId)
            .setParameter("afftrack", afftrack)
            .getResultList());
  }

  @Override
  public Optional<UUID> insertFromBroker(
      UUID id, UUID brokerId, String accountId, UUID referrerAccountId) {
    // `ON CONFLICT`: el broker reenvía, y el número pudo declararse antes en la
    // plataforma. Ninguno de los dos casos es un error del aviso.
    return primero(
        em.createNativeQuery(
                """
                INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind,
                                          referrer_account_id)
                VALUES (CAST(:id AS uuid), NULL, CAST(:broker AS uuid), :cuenta, 'CONSUMIDOR',
                        CAST(:origen AS uuid))
                ON CONFLICT ON CONSTRAINT uq_user_brokers_cuenta DO NOTHING
                RETURNING id
                """)
            .setParameter("id", id)
            .setParameter("broker", brokerId)
            .setParameter("cuenta", accountId)
            .setParameter("origen", referrerAccountId)
            .getResultList());
  }

  @Override
  public Optional<UUID> fillReferrer(UUID brokerId, String accountId, UUID referrerAccountId) {
    return primero(
        em.createNativeQuery(
                """
                UPDATE user_brokers SET referrer_account_id = CAST(:origen AS uuid),
                                        updated_at = now()
                 WHERE broker_id = CAST(:broker AS uuid) AND external_id = :cuenta
                   AND kind = 'CONSUMIDOR' AND referrer_account_id IS NULL
                RETURNING id
                """)
            .setParameter("origen", referrerAccountId)
            .setParameter("broker", brokerId)
            .setParameter("cuenta", accountId)
            .getResultList());
  }

  private void escribir(Query sentencia) {
    try {
      sentencia.executeUpdate();
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  private static Optional<UUID> primero(List<?> filas) {
    return filas.stream().findFirst().map(UUID.class::cast);
  }

  private static Optional<LockedAccount> bloqueada(List<?> filas) {
    return filas.stream()
        .findFirst()
        .map(Tuple.class::cast)
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
                    BrokerAccountKind.valueOf((String) f.get("kind")),
                    (String) f.get("afftrack"),
                    (UUID) f.get("referrer_account_id")));
  }

  /** Por el NOMBRE de la restricción, nunca por el texto del driver. */
  static RuntimeException traducir(PersistenceException fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
          && violacion.getConstraintName() != null) {
        String[] t = TRADUCCIONES.get(violacion.getConstraintName());
        if (t != null) {
          return new BusinessRuleException(t[0], t[2], List.of(new FieldError(t[1], t[0], t[2])));
        }
      }
    }
    return fallo;
  }
}
