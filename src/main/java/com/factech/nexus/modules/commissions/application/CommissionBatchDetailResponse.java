package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.application.CommissionBatchItem.BatchCurrency;
import com.factech.nexus.modules.commissions.application.CommissionBatchItem.BatchPerson;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchQueryRepository.CommissionRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Un lote con cada comisión, línea a línea y nivel a nivel (`RF-CM-010`, `RF-CM-012`, `RF-CM-011`).
 *
 * <p><b>Cada comisión muestra lo copiado</b> —la forma, el valor, la base y la tasa exacta—, no lo
 * que dice hoy la tasa (`RN-CM-008`): corregirla después no cambia lo que aquí se lee.
 */
@Schema(name = "CommissionBatchDetailResponse")
public record CommissionBatchDetailResponse(
    UUID id,
    String code,
    BatchPerson user,
    BatchCurrency currency,
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    String status,
    BigDecimal totalAmount,
    long commissionsCount,
    OffsetDateTime paidAt,
    UUID movementId,
    BigDecimal paidAmount,
    List<CommissionLine> commissions) {

  public static CommissionBatchDetailResponse from(
      CommissionBatchItem lote, List<CommissionRow> filas) {
    return new CommissionBatchDetailResponse(
        lote.id(),
        lote.code(),
        lote.user(),
        lote.currency(),
        lote.periodStart(),
        lote.periodEnd(),
        lote.status(),
        lote.totalAmount(),
        lote.commissionsCount(),
        lote.paidAt(),
        lote.movementId(),
        lote.paidAmount(),
        filas.stream().map(CommissionLine::from).toList());
  }

  /**
   * Una comisión, <b>de una de dos clases</b> (`RN-CM-044`, 29-09-2026).
   *
   * <p><b>{@code POR_VENTA}</b>: un nivel de la cadena sobre una línea de venta, con todo lo de
   * siempre. <b>{@code POR_AFFTRACK}</b>: un escalón pagado en un cierre —<b>sin venta, línea,
   * nivel ni precio unitario</b>—; {@code productId} es el producto FTD, {@code quantity} los FTD
   * pagados, {@code fixedAmount} el valor por FTD y {@code afftrackSettlementId} la liquidación de
   * la que sale (`RF-CM-021`).
   */
  @Schema(name = "CommissionLine")
  public record CommissionLine(
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
      UUID afftrackSettlementId) {

    static CommissionLine from(CommissionRow f) {
      return new CommissionLine(
          f.id(),
          f.movementDetailId(),
          f.movementId(),
          f.movementCode(),
          f.productId(),
          f.productName(),
          f.chainLevel(),
          f.source(),
          f.rateId(),
          f.rateType(),
          f.percentage(),
          f.fixedAmount(),
          f.unitPrice(),
          f.quantity(),
          f.commissionAmount(),
          f.resolvedOn(),
          f.accruedAt(),
          f.commissionKind(),
          f.afftrackSettlementId());
    }
  }
}
