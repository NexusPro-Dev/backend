package com.factech.nexus.modules.system.brokers.domain.repository;

import java.util.UUID;

/** Los avisos de los brokers, guardados sin interpretar (`RF-SP-078` · `T-03`). */
public interface BrokerNotificationRepository {

  /** ¿Existe ese broker en el catálogo y está activo? */
  boolean isActive(UUID brokerId);

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
