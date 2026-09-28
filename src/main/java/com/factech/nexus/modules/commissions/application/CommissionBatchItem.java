package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchQueryRepository.BatchRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un lote de comisión en un listado (`RF-CM-010`, `RF-CM-012`).
 *
 * <p><b>{@code periodEnd} nulo</b> es un lote {@code ABIERTO}: sigue creciendo, y {@code
 * totalAmount} es el de ahora. <b>{@code paidAmount}</b> es lo que se abonó en la billetera,
 * redondeado a la moneda; el total conserva sus cuatro decimales (`RN-CM-030`).
 */
@Schema(name = "CommissionBatchItem")
public record CommissionBatchItem(
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
    BigDecimal paidAmount) {

  public static CommissionBatchItem from(BatchRow f) {
    return new CommissionBatchItem(
        f.id(),
        f.code(),
        new BatchPerson(f.userId(), f.username(), nombre(f.firstName(), f.lastName())),
        new BatchCurrency(f.currencyId(), f.currencyCode()),
        f.periodStart(),
        f.periodEnd(),
        f.status().name(),
        f.totalAmount(),
        f.commissionsCount(),
        f.paidAt(),
        f.movementId(),
        f.paidAmount());
  }

  private static String nombre(String nombre, String apellido) {
    return ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
  }

  /** De quién es el lote. */
  @Schema(name = "CommissionBatchPerson")
  public record BatchPerson(UUID id, String username, String fullName) {}

  /** En qué moneda se debe. */
  @Schema(name = "CommissionBatchCurrency")
  public record BatchCurrency(UUID id, String code) {}
}
