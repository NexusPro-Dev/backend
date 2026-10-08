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
      String ipAddress);
}
