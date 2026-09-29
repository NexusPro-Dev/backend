package com.factech.nexus.modules.commissions.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Los filtros de {@code GET /api/v1/user-afftrack-rates} (`RF-CM-019`). Con {@code onDate} se
 * devuelven exactamente los escalones que el cierre de ese día aplicaría (`CA-CM-236`), y por eso
 * {@code includeDeleted} no cuenta: un retirado no rige.
 */
public record ListUserAfftrackRatesRequest(
    Integer page,
    Integer size,
    UUID userId,
    UUID productId,
    LocalDate onDate,
    Boolean includeDeleted) {}
