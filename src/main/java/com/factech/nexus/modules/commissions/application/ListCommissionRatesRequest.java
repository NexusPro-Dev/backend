package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import java.util.UUID;

/**
 * Filtros de {@code GET /api/v1/commission-rates} (`RF-CM-002`).
 *
 * <p><b>Recuperó el filtro por producto el 15-09-2026</b> (`RN-CM-021`): la tasa vuelve a tener
 * producto —uno, y suyo—, y «qué paga este producto» se responde aquí con {@code productId}. La
 * persona sigue fuera: las personalizadas viven en su propio listado.
 *
 * <p><b>Perdió {@code onDate}</b> el 01-09-2026: las tasas de rol no tienen vigencia. Preguntar qué
 * regía una fecha concreta <b>ya no tiene respuesta aquí</b> — el catálogo solo sabe lo que dice
 * hoy.
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
    Integer page,
    Integer size,
    UUID productId,
    UUID roleId,
    CommissionRateType rateType,
    Boolean includeDeleted) {}
