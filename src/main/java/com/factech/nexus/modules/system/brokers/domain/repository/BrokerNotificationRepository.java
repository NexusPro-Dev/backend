package com.factech.nexus.modules.system.brokers.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Los avisos de los brokers, guardados sin interpretar (`RF-SP-078` · `T-03`). */
public interface BrokerNotificationRepository {

  /**
   * El broker activo cuyo {@code advertiser} es ese, sin distinguir mayúsculas —la expresión de
   * {@code uq_brokers_advertiser}— (`RN-SP-069`), o vacío.
   */
  Optional<UUID> findActiveByAdvertiser(String advertiser);

  /**
   * Guarda un aviso. {@code brokerId} nulo es un aviso que no se reconoce (`RN-SP-076`). {@code
   * queryParams} y {@code headers} son JSON ya armado; {@code body} es el cuerpo como texto, o
   * nulo.
   */
  void insert(
      UUID id,
      UUID brokerId,
      String advertiser,
      String method,
      String queryParams,
      String headers,
      String body,
      String contentType,
      String ipAddress,
      String eventId);

  /**
   * Si ese broker ya mandó OTRO aviso con ese {@code eventId} (`RN-SP-073`, `RN-SP-074`): una
   * reentrega, que se guarda pero no se aplica dos veces.
   */
  boolean otherWithEventId(UUID brokerId, String eventId, UUID exceptId);

  /**
   * Los avisos sin broker cuyo {@code advertiser} ya es el de un broker activo (`RN-SP-076`), en el
   * orden en que llegaron.
   */
  List<UUID> unclassifiedWithKnownAdvertiser();

  /**
   * Ese aviso, <b>bloqueado</b>, si sigue sin broker y su {@code advertiser} es el de un broker
   * activo; vacío si otro lo tomó —{@code SKIP LOCKED}— o ya no es así.
   */
  Optional<StoredNotice> lockUnclassified(UUID id);

  /** Le pone el broker a un aviso que no lo tenía. La única escritura sobre un aviso guardado. */
  void assignBroker(UUID id, UUID brokerId);

  /** Lo guardado de un aviso, con el broker que le toca, para interpretarlo después. */
  record StoredNotice(
      UUID id,
      UUID brokerId,
      String queryParams,
      String body,
      String contentType,
      String eventId) {}
}
