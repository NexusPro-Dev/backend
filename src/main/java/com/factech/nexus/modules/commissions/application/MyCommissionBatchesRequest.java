package com.factech.nexus.modules.commissions.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros de {@code GET /api/v1/commission-batches/mine} (`RF-CM-012`): los de `RF-CM-010` <b>sin
 * la persona</b>, que la pone el token. No existe un parámetro que un cliente pueda cambiar para
 * ver lo ajeno.
 */
public record MyCommissionBatchesRequest(
    Integer page,
    Integer size,
    String status,
    UUID currencyId,
    OffsetDateTime from,
    OffsetDateTime to) {

  public ListCommissionBatchesRequest comoListado() {
    return new ListCommissionBatchesRequest(page, size, status, null, currencyId, from, to);
  }
}
