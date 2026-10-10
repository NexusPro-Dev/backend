package com.factech.nexus.modules.system.brokers.domain.repository;

import com.factech.nexus.modules.system.brokers.domain.models.BrokerAccountKind;
import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import java.util.Optional;
import java.util.UUID;

/**
 * Las escrituras de las cuentas de broker (`RF-SP-053`, `RF-SP-080`, `RF-SP-081`, `RF-SP-082`).
 *
 * <p><b>Las unicidades las decide el esquema</b> y se traducen por el nombre de la restricción:
 * {@code uq_user_brokers_cuenta} a {@code EX-009} (`RN-SP-038`), {@code uq_user_brokers_afftrack} a
 * {@code EX-014} (`RN-SP-071`), {@code uq_user_brokers_vendedor_por_broker} a {@code EX-015}
 * (`RN-SP-070`) y {@code fk_user_brokers_origen}, al borrar, a {@code EX-012}. Una comprobación
 * previa dejaría pasar dos escrituras simultáneas.
 */
public interface BrokerAccountWriter {

  /** Una cuenta, tal como está en la fila, para decidir sobre ella y para auditarla. */
  record LockedAccount(
      UUID id,
      UUID userId,
      UUID brokerId,
      String brokerName,
      String accountId,
      String brokerUsername,
      UserBrokerStatus status,
      BrokerAccountKind kind,
      String afftrack,
      UUID referrerAccountId) {}

  /**
   * Declara la cuenta, en {@code REGISTER} y sin nombre de usuario, con su tipo, su {@code
   * afftrack} —solo {@code VENDEDOR}— y su origen —solo {@code CONSUMIDOR}—.
   */
  void insert(
      UUID id,
      UUID userId,
      UUID brokerId,
      String accountId,
      BrokerAccountKind kind,
      String afftrack,
      UUID referrerAccountId);

  /**
   * El tipo que tendría una cuenta de esa persona (`RN-SP-068`): {@code VENDEDOR} si porta un rol
   * de ese tipo, aunque porte también uno consumidor; si no, {@code CONSUMIDOR} si porta uno de ese
   * tipo; vacío si no porta ninguno de los dos.
   */
  Optional<BrokerAccountKind> kindFor(UUID userId);

  /** El vendedor principal de la persona: su fila {@code REGISTRO} de {@code client_sellers}. */
  Optional<UUID> principalSellerOf(UUID userId);

  /**
   * La cuenta {@code VENDEDOR} de ese vendedor en ese broker, que es una como mucho (`RN-SP-070`).
   */
  Optional<UUID> vendorAccount(UUID sellerId, UUID brokerId);

  /**
   * Asocia a {@code userId} la cuenta {@code accountId} de ese broker <b>solo si no tiene titular y
   * su origen es la cuenta {@code VENDEDOR} de {@code sellerId}</b> (`RN-SP-072`). Devuelve la
   * cuenta asociada, o vacío si no se cumplió: entonces la cuenta no cambia.
   */
  Optional<UUID> claim(UUID brokerId, String accountId, UUID userId, UUID sellerId);

  /**
   * La cuenta <b>de esa persona</b>, bloqueada hasta el fin de la transacción.
   *
   * <p>Vacío si no existe <b>o si es de otra persona</b>: las dos cosas responden igual.
   */
  Optional<LockedAccount> lock(UUID brokerAccountId, UUID userId);

  /** La cuenta, sea de quien sea o de nadie, bloqueada (`RF-SP-082`). */
  Optional<LockedAccount> lockAny(UUID brokerAccountId);

  void updateAccountId(UUID brokerAccountId, String accountId);

  /** {@code null} lo borra. */
  void updateAfftrack(UUID brokerAccountId, String afftrack);

  void assignUser(UUID brokerAccountId, UUID userId);

  void delete(UUID brokerAccountId);

  /** La cuenta {@code VENDEDOR} de ese broker con ese {@code afftrack}, sin mayúsculas. */
  Optional<UUID> vendorAccountByAfftrack(UUID brokerId, String afftrack);

  /**
   * El aviso de registro (`RN-SP-072`): crea la cuenta {@code CONSUMIDOR} sin titular, o nada si
   * ese número ya existe en ese broker. Devuelve la creada.
   */
  Optional<UUID> insertFromBroker(UUID id, UUID brokerId, String accountId, UUID referrerAccountId);

  /**
   * Pone el origen a la {@code CONSUMIDOR} de ese número <b>solo si no tenía</b>. Devuelve la
   * cuenta tocada.
   */
  Optional<UUID> fillReferrer(UUID brokerId, String accountId, UUID referrerAccountId);

  /** La cuenta de ese número en ese broker, sea de quien sea o de nadie, bloqueada. */
  Optional<LockedAccount> lockByNumber(UUID brokerId, String accountId);

  /**
   * Pasa la {@code CONSUMIDOR} a {@code FIRST_DEPOSIT} con su momento (`RN-SP-073`), <b>solo si
   * estaba en {@code REGISTER}</b>. Devuelve si la movió.
   */
  boolean markFirstDeposit(UUID brokerAccountId);

  /** Cuenta una operación más (`RN-SP-074`): el total, la primera y la última. */
  void countOperation(UUID brokerAccountId);
}
