package com.factech.nexus.modules.commissions.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros de {@code GET /api/v1/commission-batches/mine/commissions} (`RF-CM-026`). <b>Sin
 * persona</b>, como {@link MyCommissionBatchesRequest}: la pone el token. {@code status} es el del
 * lote y {@code from}/{@code to} van sobre el devengo de cada comisión.
 */
public record MyCommissionsRequest(
    Integer page,
    Integer size,
    String status,
    UUID currencyId,
    UUID productId,
    String commissionKind,
    OffsetDateTime from,
    OffsetDateTime to) {}
