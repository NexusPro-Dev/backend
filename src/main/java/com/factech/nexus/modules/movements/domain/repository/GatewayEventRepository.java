package com.factech.nexus.modules.movements.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de las notificaciones de la pasarela (`RN-MV-059`; `RF-MV-041`). */
public interface GatewayEventRepository {

  /**
   * La guarda si no estaba. <b>{@code ON CONFLICT DO NOTHING}</b> sobre {@code (gateway,
   * external_id)}: la reentrega no inserta otra.
   *
   * @return {@code true} si es nueva
   */
  boolean insertIfNew(UUID id, String gateway, String externalId, String type, String payload);

  /**
   * La notificación <b>sin procesar</b>, con su fila bloqueada {@code FOR UPDATE SKIP LOCKED}: si
   * otro hilo la está procesando, vacío.
   */
  Optional<StoredEvent> lockPending(UUID id);

  /** Las pendientes que el barrido reintenta: recibidas antes de ese instante y con intentos. */
  List<UUID> pendingForRetry(
      String gateway, OffsetDateTime receivedBefore, int maxAttempts, int limit);

  /** El desenlace: {@code PROCESADO}, {@code IGNORADO} o {@code ERROR}. */
  void finish(UUID id, String outcome, String error, UUID paymentId, OffsetDateTime at);

  /**
   * Un intento fallido. Si con él se agotan, queda {@code ERROR} con el motivo.
   *
   * @return los intentos que lleva
   */
  int failAttempt(UUID id, String error, int maxAttempts, OffsetDateTime at);

  record StoredEvent(UUID id, String gateway, String type, String payload, int attempts) {}
}
