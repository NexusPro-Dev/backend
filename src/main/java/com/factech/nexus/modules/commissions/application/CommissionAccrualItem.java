package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.repository.CommissionAccrualQueryRepository.AccrualRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Qué pasó con una línea de venta a efectos de comisión (`RF-CM-014`, `RN-CM-032`).
 *
 * <p><b>{@code reason}</b> solo existe en una {@code RECHAZADA}: dice cuánto sumaba la cadena y
 * cuánto valía la línea, para saber qué tasa corregir. <b>{@code attempts}</b> sube con cada cierre
 * que la reintenta.
 */
@Schema(name = "CommissionAccrualItem")
public record CommissionAccrualItem(
    UUID movementDetailId,
    UUID movementId,
    String movementCode,
    UUID productId,
    String productName,
    UUID sellerId,
    String outcome,
    String reason,
    int attempts,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static CommissionAccrualItem from(AccrualRow f) {
    return new CommissionAccrualItem(
        f.movementDetailId(),
        f.movementId(),
        f.movementCode(),
        f.productId(),
        f.productName(),
        f.sellerId(),
        f.outcome().name(),
        f.reason(),
        f.attempts(),
        f.createdAt(),
        f.updatedAt());
  }
}
