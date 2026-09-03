package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
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
 *
 * <p><b>Ganó {@code rateType} el 02-09-2026</b>, por decisión del responsable del proyecto y no por
 * necesidad técnica: ninguna operación lo requiere. Responde a la pregunta que nace el día que
 * conviven las dos formas —«enséñame las que pagan importe fijo»— y que hasta entonces no tenía
 * sentido. Ausente, no filtra.
 *
 * <p><b>El listado de personalizadas NO lo gana</b>, y no es un olvido: allí se filtra por persona,
 * y una persona tiene <b>una</b> tasa vigente (`RN-CM-006`). Filtrar por forma sobre un historial
 * de una sola línea no responde a ninguna pregunta.
 */
public record ListCommissionRatesRequest(
    Integer page, Integer size, UUID roleId, CommissionRateType rateType, Boolean includeDeleted) {}
