package com.factech.nexus.modules.commissions.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros de {@code GET /api/v1/user-commission-rates} (`RF-CM-002`).
 *
 * <p><b>No hay interruptor «solo vigentes»</b>: eso es {@code onDate} con la fecha de hoy. Un
 * interruptor y una fecha podrían contradecirse, y esa contradicción no la detecta nada.
 *
 * <p><b>Filtrar por persona devuelve las declaradas PARA esa persona</b>, incluido su historial —
 * no la que <b>le aplica</b> hoy sobre un producto. Lo segundo es `RF-CM-005`, y confundirlos haría
 * que este listado empezara a resolver precedencias por su cuenta.
 */
public record ListUserCommissionRatesRequest(
    Integer page, Integer size, UUID userId, LocalDate onDate, Boolean includeDeleted) {}
