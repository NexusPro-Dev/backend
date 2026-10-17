package com.factech.nexus.modules.system.brokers.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Los filtros de las cuentas que originó el actor (`RF-SP-083`).
 *
 * <p><b>Sin {@code supervisorId}, {@code userId}, {@code kind} ni {@code sellerId}</b>: el alcance
 * lo pone el sistema —lo que originó la cuenta del actor, solo {@code CONSUMIDOR}— y no un
 * parámetro (`RN-SP-084`, 16-10-2026, que retiró {@code sellerId} al dejar de traer la red).
 */
public record ListReferredBrokerAccountsRequest(
    Integer page,
    Integer size,
    String status,
    UUID brokerId,
    Boolean hasHolder,
    String search,
    OffsetDateTime from,
    OffsetDateTime to) {

  public ListReferredBrokerAccountsRequest {
    search = search == null || search.isBlank() ? null : search.trim();
    status = status == null || status.isBlank() ? null : status.trim();
  }
}
