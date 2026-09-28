package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.ClosingOrigin;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de los cierres del periodo (`RF-CM-009`). */
public interface CommissionClosingRepository {

  /**
   * Toma el turno programado: escribe la constancia con {@code scheduled_for} único.
   *
   * @return falso si otra instancia ya lo tomó (`RN-CM-035`)
   */
  boolean openScheduled(UUID id, OffsetDateTime turno, OffsetDateTime at);

  void openManual(UUID id, UUID actor, OffsetDateTime at);

  /** Bloqueo consultivo de transacción del cierre, esperando a quien lo tenga. */
  void lock();

  /** El mismo bloqueo, sin esperar. */
  boolean tryLock();

  /**
   * Pasa a {@code PENDIENTE} todo lote {@code ABIERTO} nacido antes de {@code at}, con {@code at}
   * como fin de periodo y este cierre como el que lo cerró.
   *
   * @return cuántos lotes cerró
   */
  int closeOpenBatches(UUID closingId, OffsetDateTime at);

  void finish(
      UUID closingId, OffsetDateTime at, int batches, int swept, int retried, int recovered);

  Optional<ClosingRow> find(UUID id);

  List<ClosingRow> search(ClosingFilter filtro, int offset, int limit);

  long count(ClosingFilter filtro);

  record ClosingFilter(ClosingOrigin origin, OffsetDateTime from, OffsetDateTime to) {}

  record ClosingRow(
      UUID id,
      ClosingOrigin origin,
      OffsetDateTime scheduledFor,
      UUID triggeredBy,
      OffsetDateTime startedAt,
      OffsetDateTime closedAt,
      int batchesClosed,
      int linesSwept,
      int linesRetried,
      int linesRecovered) {}
}
