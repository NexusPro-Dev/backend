package com.factech.nexus.modules.system.brokers.domain.repository;

import com.factech.nexus.modules.system.brokers.domain.models.BrokerAccountKind;
import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import java.util.Optional;
import java.util.UUID;

/**
 * Las escrituras de las cuentas de broker (`RF-SP-053`, `RF-SP-080`, `RF-SP-081`).
 *
 * <p><b>La unicidad del par broker + identificador la decide el esquema</b> (`RN-SP-038`,
 * `uq_user_brokers_cuenta`): {@link #insert} y {@link #updateAccountId} la traducen a {@code
 * EX-009} por el nombre de la restricción. Una comprobación previa dejaría pasar dos altas
 * simultáneas.
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
      BrokerAccountKind kind) {}

  /** Declara la cuenta, en {@code REGISTER}, sin nombre de usuario y con su tipo. */
  void insert(UUID id, UUID userId, UUID brokerId, String accountId, BrokerAccountKind kind);

  /**
   * El tipo que tendría una cuenta de esa persona (`RN-SP-068`): {@code VENDEDOR} si porta un rol
   * de ese tipo, aunque porte también uno consumidor; si no, {@code CONSUMIDOR} si porta uno de ese
   * tipo; vacío si no porta ninguno de los dos.
   */
  Optional<BrokerAccountKind> kindFor(UUID userId);

  /**
   * La cuenta <b>de esa persona</b>, bloqueada hasta el fin de la transacción.
   *
   * <p>Vacío si no existe <b>o si es de otra persona</b>: las dos cosas responden igual.
   */
  Optional<LockedAccount> lock(UUID brokerAccountId, UUID userId);

  void updateAccountId(UUID brokerAccountId, String accountId);

  void delete(UUID brokerAccountId);
}
