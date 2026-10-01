package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.application.CommissionBatchItem.BatchCurrency;
import com.factech.nexus.modules.commissions.application.CommissionBatchItem.BatchPerson;
import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchQueryRepository.CommissionRow;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchQueryRepository.WithdrawnRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Un lote con cada comisión, línea a línea y nivel a nivel (`RF-CM-010`, `RF-CM-012`, `RF-CM-011`).
 *
 * <p><b>Desde el 30-09-2026 muestra también lo que salió</b> (`RN-CM-046`, `RN-CM-047`): las
 * comisiones <b>revertidas</b> siguen entre las del lote, con {@code revertedAt}, y fuera del
 * total; y {@code withdrawn} lista las <b>retiradas de este lote</b>, cada una con el lote en que
 * está y si aún se puede devolver (`RF-CM-023`).
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
    List<CommissionLine> commissions,
    List<WithdrawnCommission> withdrawn) {

  public static CommissionBatchDetailResponse from(
      CommissionBatchItem lote, List<CommissionRow> filas, List<WithdrawnRow> retiradas) {
    boolean pendiente = BatchStatus.PENDIENTE.name().equals(lote.status());
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
        filas.stream().map(CommissionLine::from).toList(),
        retiradas.stream().map(r -> WithdrawnCommission.from(r, pendiente)).toList());
  }

  /** Un lote nombrado desde otro sitio: su identificador y su código. */
  @Schema(name = "CommissionBatchRef")
  public record BatchRef(UUID id, String code) {}

  /**
   * Una comisión <b>retirada de este lote</b> (`RN-CM-046`), con el lote en que está. <b>{@code
   * returnable}</b>: este lote sigue {@code PENDIENTE}, el suyo {@code ABIERTO} y ella viva — lo
   * que `RF-CM-023` exige para devolverla.
   */
  @Schema(name = "WithdrawnCommission")
  public record WithdrawnCommission(
      CommissionLine commission, BatchRef currentBatch, String currentStatus, boolean returnable) {

    static WithdrawnCommission from(WithdrawnRow r, boolean origenPendiente) {
      CommissionLine linea = CommissionLine.from(r.commission());
      return new WithdrawnCommission(
          linea,
          new BatchRef(r.currentId(), r.currentCode()),
          r.currentStatus().name(),
          origenPendiente
              && r.currentStatus() == BatchStatus.ABIERTO
              && linea.revertedAt() == null);
    }
  }

  /**
   * Una comisión, <b>de una de dos clases</b> (`RN-CM-044`, 29-09-2026).
   *
   * <p><b>{@code POR_VENTA}</b>: un nivel de la cadena sobre una línea de venta, con todo lo de
   * siempre. <b>{@code POR_AFFTRACK}</b>: un escalón pagado en un cierre —<b>sin venta, línea,
   * nivel ni precio unitario</b>—; {@code productId} es el producto FTD, {@code quantity} los FTD
   * pagados, {@code fixedAmount} el valor por FTD y {@code afftrackSettlementId} la liquidación de
   * la que sale (`RF-CM-021`).
   *
   * <p><b>{@code revertedAt}</b> presente: se revirtió al corregirse el vendedor de su línea
   * (`RN-CM-047`) y no cuenta en el total. <b>{@code withdrawnFrom}</b>: el lote pendiente del que
   * se retiró (`RN-CM-046`), o nulo.
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
      UUID afftrackSettlementId,
      OffsetDateTime revertedAt,
      UUID revertedBy,
      BatchRef withdrawnFrom) {

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
          f.afftrackSettlementId(),
          f.revertedAt(),
          f.revertedBy(),
          f.withdrawnFromId() == null
              ? null
              : new BatchRef(f.withdrawnFromId(), f.withdrawnFromCode()));
    }
  }
}
