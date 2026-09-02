package com.factech.nexus.modules.commissions.application;

import java.util.UUID;

/**
 * Filtros de {@code GET /api/v1/commission-rates} (`RF-CM-002`).
 *
 * <p><b>Perdió el filtro por producto y por persona</b>, y no es una simplificación: en esta tabla
 * ya no hay ni producto ni persona. «Qué tasas rigen sobre este producto» se pregunta en {@code GET
 * /api/v1/commission-rates/by-product/{productId}}, y las personalizadas viven en su propio
 * listado.
 *
 * <p><b>Y perdió {@code onDate}</b>, por lo mismo: las tasas de rol no tienen vigencia. Preguntar
 * qué regía una fecha concreta <b>ya no tiene respuesta aquí</b> — el catálogo solo sabe lo que
 * dice hoy.
 */
public record ListCommissionRatesRequest(
    Integer page, Integer size, UUID roleId, Boolean includeDeleted) {}
