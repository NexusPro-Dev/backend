package com.factech.nexus.modules.commissions.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros de {@code GET /api/v1/commission-batches} (`RF-CM-010`). Las fechas van sobre el inicio
 * del periodo. En {@code /mine} (`RF-CM-012`) {@code userId} <b>se ignora</b>: la persona la pone
 * el token.
 */
public record ListCommissionBatchesRequest(
    Integer page,
    Integer size,
    String status,
    UUID userId,
    UUID currencyId,
    OffsetDateTime from,
    OffsetDateTime to) {}
