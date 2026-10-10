package com.factech.nexus.modules.system.brokers.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Los filtros de las cuentas que originó la red del actor (`RF-SP-083`).
 *
 * <p><b>Sin {@code supervisorId}, {@code userId} ni {@code kind}</b>: el alcance lo pone el sistema
 * —la red del actor, solo cuentas {@code CONSUMIDOR}— y no un parámetro (`RN-SP-075`). {@code
 * sellerId} acota a un vendedor <b>dentro</b> de esa red; uno de fuera da la página vacía.
 */
public record ListReferredBrokerAccountsRequest(
    Integer page,
    Integer size,
    String status,
    UUID brokerId,
    UUID sellerId,
    Boolean hasHolder,
    String search,
    OffsetDateTime from,
    OffsetDateTime to) {

  public ListReferredBrokerAccountsRequest {
    search = search == null || search.isBlank() ? null : search.trim();
    status = status == null || status.isBlank() ? null : status.trim();
  }
}
