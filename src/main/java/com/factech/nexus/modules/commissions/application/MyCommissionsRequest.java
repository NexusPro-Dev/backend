package com.factech.nexus.modules.commissions.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros de {@code GET /api/v1/commission-batches/mine/commissions} (`RF-CM-026`). <b>Sin
 * persona</b>, como {@link MyCommissionBatchesRequest}: la pone el token. {@code status} es el del
 * lote y {@code from}/{@code to} van sobre el devengo de cada comisión. {@code clientId}, desde el
 * 07-10-2026, es el cliente de la venta: una {@code POR_AFFTRACK} no tiene y no sale con él.
 */
public record MyCommissionsRequest(
    Integer page,
    Integer size,
    String status,
    UUID currencyId,
    UUID productId,
    UUID clientId,
    String commissionKind,
    OffsetDateTime from,
    OffsetDateTime to) {}
