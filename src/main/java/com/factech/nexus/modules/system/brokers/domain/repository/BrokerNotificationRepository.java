package com.factech.nexus.modules.system.brokers.domain.repository;

import java.util.Optional;
import java.util.UUID;

/** Los avisos de los brokers, guardados sin interpretar (`RF-SP-078` · `T-03`). */
public interface BrokerNotificationRepository {

  /**
   * El broker activo con ese nombre, sin distinguir mayúsculas ni acentos —la expresión de {@code
   * uq_brokers_name}—, o vacío si no existe o no está activo.
   */
  Optional<UUID> findActiveByName(String name);

  /**
   * El broker activo cuyo {@code advertiser} es ese, sin distinguir mayúsculas —la expresión de
   * {@code uq_brokers_advertiser}— (`RN-SP-069`), o vacío.
   */
  Optional<UUID> findActiveByAdvertiser(String advertiser);

  /**
   * Guarda un aviso. {@code queryParams} y {@code headers} son JSON ya armado; {@code body} es el
   * cuerpo como texto, o nulo.
   */
  void insert(
      UUID id,
      UUID brokerId,
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
}
