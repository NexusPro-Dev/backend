package com.factech.nexus.modules.commissions.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Los filtros de {@code GET /api/v1/afftrack-settlements} (`RF-CM-021`). Todos opcionales. */
public record ListAfftrackSettlementsRequest(
    Integer page,
    Integer size,
    UUID userId,
    UUID productId,
    UUID closingId,
    OffsetDateTime from,
    OffsetDateTime to,
    Boolean paid) {}
