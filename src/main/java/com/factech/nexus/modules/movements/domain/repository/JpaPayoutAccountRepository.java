package com.factech.nexus.modules.movements.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de las cuentas de cobro. SQL nativo, como el resto de `MV`. */
@Repository
public class JpaPayoutAccountRepository implements PayoutAccountRepository {

  /**
   * El espacio del bloqueo consultivo por persona. Distinto de los de `CM` (4309, el cierre; 4313,
   * el devengo de una línea).
   */
  private static final int ESPACIO_CUENTAS = 4321;

  private static final String SELECCION =
      """
      SELECT a.id AS id, a.user_id AS dueno, i.id AS entidad, i.code AS entidad_codigo,
             i.name AS entidad_nombre, i.kind AS entidad_tipo, i.is_active AS entidad_activa,
             a.account_type AS tipo, a.number AS numero, a.is_principal AS principal,
             a.created_at AS creada, a.deleted_at AS baja
        FROM payout_accounts a
        JOIN payout_institutions i ON i.id = a.institution_id
      """;

  private final EntityManager em;

  public JpaPayoutAccountRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void lockOwner(UUID userId) {
    em.createNativeQuery("SELECT pg_advisory_xact_lock(:ns, hashtext(CAST(:persona AS text)))")
        .setParameter("ns", ESPACIO_CUENTAS)
        .setParameter("persona", userId.toString())
        .getSingleResult();
  }

  @Override
  public List<AccountRow> of(UUID userId, boolean includeDeleted) {
    String sql =
        SELECCION
            + " WHERE a.user_id = :persona"
            + (includeDeleted ? "" : " AND a.deleted_at IS NULL")
            + " ORDER BY a.deleted_at IS NOT NULL, a.is_principal DESC,"
            + " coalesce(a.deleted_at, a.created_at) DESC, a.id DESC";
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(sql, Tuple.class).setParameter("persona", userId).getResultList();
    return filas.stream().map(JpaPayoutAccountRepository::fila).toList();
  }

  @Override
  public Optional<AccountRow> findLiveOwn(UUID id, UUID userId) {
    if (id == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                SELECCION + " WHERE a.id = :id AND a.user_id = :persona AND a.deleted_at IS NULL",
                Tuple.class)
            .setParameter("id", id)
            .setParameter("persona", userId)
            .getResultList();
    return filas.stream().findFirst().map(JpaPayoutAccountRepository::fila);
  }

  @Override
  public boolean existsLive(UUID userId, UUID institutionId, String number, UUID except) {
    // Dos sentencias y no un parámetro nulo: un uuid nulo no le dice al driver su tipo.
    var consulta =
        em.createNativeQuery(
                "SELECT 1 FROM payout_accounts WHERE user_id = :persona"
                    + " AND institution_id = :entidad AND number = :numero"
                    + " AND deleted_at IS NULL"
                    + (except == null ? "" : " AND id <> :excepto")
                    + " LIMIT 1")
            .setParameter("persona", userId)
            .setParameter("entidad", institutionId)
            .setParameter("numero", number);
    if (except != null) {
      consulta.setParameter("excepto", except);
    }
    return !consulta.getResultList().isEmpty();
  }

  @Override
  public boolean hasLive(UUID userId) {
    return !em.createNativeQuery(
            "SELECT 1 FROM payout_accounts WHERE user_id = :persona AND deleted_at IS NULL"
                + " LIMIT 1")
        .setParameter("persona", userId)
        .getResultList()
        .isEmpty();
  }

  @Override
  public void insert(
      UUID id,
      UUID userId,
      UUID institutionId,
      String accountType,
      String number,
      boolean principal,
      OffsetDateTime at) {
    em.createNativeQuery(
            """
            INSERT INTO payout_accounts (id, user_id, institution_id, account_type, number,
                                         is_principal, created_at, updated_at)
            VALUES (:id, :persona, :entidad, :tipo, :numero, :principal, :ahora, :ahora)
            """)
        .setParameter("id", id)
        .setParameter("persona", userId)
        .setParameter("entidad", institutionId)
        .setParameter("tipo", accountType)
        .setParameter("numero", number)
        .setParameter("principal", principal)
        .setParameter("ahora", at)
        .executeUpdate();
  }

  @Override
  public void unmarkPrincipal(UUID userId, OffsetDateTime at) {
    em.createNativeQuery(
            "UPDATE payout_accounts SET is_principal = false, updated_at = :ahora"
                + " WHERE user_id = :persona AND is_principal AND deleted_at IS NULL")
        .setParameter("persona", userId)
        .setParameter("ahora", at)
        .executeUpdate();
  }

  @Override
  public void update(
      UUID id, String accountType, String number, boolean principal, OffsetDateTime at) {
    em.createNativeQuery(
            "UPDATE payout_accounts SET account_type = :tipo, number = :numero,"
                + " is_principal = :principal, updated_at = :ahora WHERE id = :id")
        .setParameter("id", id)
        .setParameter("tipo", accountType)
        .setParameter("numero", number)
        .setParameter("principal", principal)
        .setParameter("ahora", at)
        .executeUpdate();
  }

  @Override
  public void softDelete(UUID id, OffsetDateTime at) {
    em.createNativeQuery(
            "UPDATE payout_accounts SET deleted_at = :ahora, is_principal = false,"
                + " updated_at = :ahora WHERE id = :id")
        .setParameter("id", id)
        .setParameter("ahora", at)
        .executeUpdate();
  }

  @Override
  public Optional<UUID> promoteOldest(UUID userId, OffsetDateTime at) {
    @SuppressWarnings("unchecked")
    List<Object> ids =
        em.createNativeQuery(
                """
                UPDATE payout_accounts SET is_principal = true, updated_at = :ahora
                 WHERE id = (SELECT id FROM payout_accounts
                              WHERE user_id = :persona AND deleted_at IS NULL
                              ORDER BY created_at, id LIMIT 1)
                RETURNING id
                """)
            .setParameter("persona", userId)
            .setParameter("ahora", at)
            .getResultList();
    return ids.stream().findFirst().map(UUID.class::cast);
  }

  @Override
  public Optional<AccountRow> findForWithdrawal(UUID userId, UUID id) {
    String condicion = id == null ? " AND a.is_principal" : " AND a.id = :id";
    var consulta =
        em.createNativeQuery(
                SELECCION
                    + " WHERE a.user_id = :persona AND a.deleted_at IS NULL"
                    + condicion
                    + " FOR SHARE OF a, i",
                Tuple.class)
            .setParameter("persona", userId);
    if (id != null) {
      consulta.setParameter("id", id);
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    return filas.stream().findFirst().map(JpaPayoutAccountRepository::fila);
  }

  private static AccountRow fila(Tuple f) {
    return new AccountRow(
        (UUID) f.get("id"),
        (UUID) f.get("dueno"),
        (UUID) f.get("entidad"),
        (String) f.get("entidad_codigo"),
        (String) f.get("entidad_nombre"),
        (String) f.get("entidad_tipo"),
        (Boolean) f.get("entidad_activa"),
        (String) f.get("tipo"),
        (String) f.get("numero"),
        (Boolean) f.get("principal"),
        PayoutTimes.instante(f.get("creada")),
        PayoutTimes.instante(f.get("baja")));
  }
}
