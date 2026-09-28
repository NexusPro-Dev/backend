package com.factech.nexus.modules.commissions.application;

import java.time.OffsetDateTime;

/**
 * Filtros de {@code GET /api/v1/commission-closings} (`RF-CM-009`). Las fechas van sobre el inicio
 * del cierre, que existe siempre; el fin falta en el que falló.
 */
public record ListCommissionClosingsRequest(
    Integer page, Integer size, String origin, OffsetDateTime from, OffsetDateTime to) {}
