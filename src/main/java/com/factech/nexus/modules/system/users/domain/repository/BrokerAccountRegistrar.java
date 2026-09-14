package com.factech.nexus.modules.system.users.domain.repository;

import java.util.Optional;
import java.util.UUID;

/**
 * La cuenta de una persona en un broker, para el registro por enlace (`RF-SP-045` · `T-19`).
 *
 * <p><b>Se declara aquí y no en el submódulo de brokers</b>, siguiendo a {@link AssignableCountry}
 * y a {@code AssignableDocumentType}: el consumidor declara lo que necesita y el dueño del dato lo
 * implementa. Lo que cruza son datos planos, sin entidad.
 *
 * <p><b>Es la primera escritura sobre {@code user_brokers}</b>, que nació el 08-09-2026 sin ningún
 * caso de uso: `RF-SP-053` —quién declara la cuenta— no estaba decidido, y la respuesta resultó ser
 * <b>el propio titular, al registrarse</b>.
 */
public interface BrokerAccountRegistrar {

  /** El broker del catálogo, o vacío si el identificador no designa ninguno. */
  Optional<BrokerRef> find(UUID brokerId);

  /**
   * Declara la cuenta.
   *
   * <p><b>El nombre de usuario en el broker NO se recibe</b> y queda nulo: lo rellena después el
   * webhook del propio broker (`RN-SP-040`, `RF-SP-054`). Pedirlo aquí obligaría a la persona a
   * inventarse un valor que sobreviviría a la confirmación.
   *
   * <p><b>Puede fallar por el índice</b> —`RN-SP-038`, una cuenta es de una sola persona—, y ese
   * fallo tiene que llegar traducido a `409`: quien lo llama está dentro de una transacción que
   * escribe otras cuatro cosas, y ninguna puede quedar.
   */
  void declare(UUID accountId, UUID userId, UUID brokerId, String externalId);

  /** Un broker del catálogo, con lo justo para verificarlo. */
  record BrokerRef(UUID id, String name, boolean active) {}
}
