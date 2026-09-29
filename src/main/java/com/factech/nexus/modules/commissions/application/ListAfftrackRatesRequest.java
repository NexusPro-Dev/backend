package com.factech.nexus.modules.commissions.application;

import java.util.UUID;

/** Los filtros de {@code GET /api/v1/afftrack-rates} (`RF-CM-016`). Todos opcionales. */
public record ListAfftrackRatesRequest(
    Integer page, Integer size, UUID productId, UUID roleId, Boolean includeDeleted) {}
