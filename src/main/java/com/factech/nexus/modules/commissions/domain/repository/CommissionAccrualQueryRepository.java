package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.AccrualOutcome;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Puerto de lectura de los desenlaces de las líneas (`RF-CM-014`). */
public interface CommissionAccrualQueryRepository {

  List<AccrualRow> search(AccrualFilter filtro, int offset, int limit);

  long count(AccrualFilter filtro);

  /** Un nulo no filtra. Las fechas van sobre el último intento. */
  record AccrualFilter(
      AccrualOutcome outcome,
      UUID movementId,
      UUID productId,
      OffsetDateTime from,
      OffsetDateTime to) {}

  record AccrualRow(
      UUID movementDetailId,
      UUID movementId,
      String movementCode,
      UUID productId,
      String productName,
      UUID sellerId,
      AccrualOutcome outcome,
      String reason,
      int attempts,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
