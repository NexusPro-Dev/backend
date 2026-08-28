package com.factech.nexus.modules.commissions.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros de {@code GET /api/v1/commission-rates} (`RF-CM-002`).
 *
 * <p><b>No hay interruptor «solo vigentes»</b>: eso es {@code onDate} con la fecha de hoy. Un
 * interruptor y una fecha podrian contradecirse, y esa contradiccion no la detecta nada.
 *
 * <p><b>Los filtros por producto y por persona son igualdades y NO resuelven precedencia</b>:
 * devuelven las declaradas <b>para</b> ese producto o esa persona, no las que <b>le aplican</b>. Lo
 * segundo es `RF-CM-005`, y confundirlos haria que este listado empezara a resolver precedencias
 * por su cuenta.
 */
public record ListCommissionRatesRequest(
    Integer page,
    Integer size,
    UUID roleId,
    UUID productId,
    UUID userId,
    LocalDate onDate,
    Boolean includeDeleted) {}
