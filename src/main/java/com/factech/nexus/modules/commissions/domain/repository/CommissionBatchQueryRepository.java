package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de lectura de los lotes de comisión (`RF-CM-010`, `RF-CM-012`). */
public interface CommissionBatchQueryRepository {

  List<BatchRow> search(BatchFilter filtro, int offset, int limit);

  long count(BatchFilter filtro);

  /**
   * Un lote, si existe <b>y es de {@code owner}</b> cuando se pide: el lote ajeno y el inexistente
   * responden igual (`RF-CM-012` `EX-001`).
   */
  Optional<BatchRow> find(UUID id, UUID owner);

  /** Sus comisiones, por venta, línea y nivel. */
  List<CommissionRow> commissionsOf(UUID batchId);

  /**
   * Las comisiones <b>retiradas de este lote</b>, estén donde estén (`RN-CM-046`, `RF-CM-023`): el
   * pendiente las lista porque es desde él desde donde se devuelven.
   */
  List<WithdrawnRow> withdrawnFrom(UUID batchId);

  /** Un nulo no filtra. {@code owner} lo fija el token en `RF-CM-012`, nunca la petición. */
  record BatchFilter(
      BatchStatus status, UUID userId, UUID currencyId, OffsetDateTime from, OffsetDateTime to) {}

  record BatchRow(
      UUID id,
      String code,
      UUID userId,
      String username,
      String firstName,
      String lastName,
      UUID currencyId,
      String currencyCode,
      OffsetDateTime periodStart,
      OffsetDateTime periodEnd,
      BatchStatus status,
      BigDecimal totalAmount,
      long commissionsCount,
      OffsetDateTime paidAt,
      UUID movementId,
      BigDecimal paidAmount) {}

  record CommissionRow(
      UUID id,
      UUID movementDetailId,
      UUID movementId,
      String movementCode,
      UUID productId,
      String productName,
      Integer chainLevel,
      String source,
      UUID rateId,
      String rateType,
      BigDecimal percentage,
      BigDecimal fixedAmount,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal commissionAmount,
      LocalDate resolvedOn,
      OffsetDateTime accruedAt,
      String commissionKind,
      UUID afftrackSettlementId,
      UUID withdrawnFromId,
      String withdrawnFromCode) {}

  /** Una comisión retirada, con el lote en que está ahora. */
  record WithdrawnRow(
      CommissionRow commission, UUID currentId, String currentCode, BatchStatus currentStatus) {}
}
