package com.factech.nexus.modules.commissions.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros de {@code GET /api/v1/commission-accruals} (`RF-CM-014`). Las fechas, sobre el último
 * intento.
 */
public record ListCommissionAccrualsRequest(
    Integer page,
    Integer size,
    String outcome,
    UUID movementId,
    UUID productId,
    OffsetDateTime from,
    OffsetDateTime to) {}
