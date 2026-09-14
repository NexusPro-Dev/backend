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
 *
 * <p><b>Filtrar por producto devuelve las ASOCIADAS a ese producto</b> (12-09-2026), de cualquier
 * persona: es la respuesta a «quién tiene excepción aquí». Se combina con los demás — persona y
 * producto juntos responden «¿tiene esta persona excepción en este producto?», con su historial.
 */
public record ListUserCommissionRatesRequest(
    Integer page,
    Integer size,
    UUID userId,
    UUID productId,
    LocalDate onDate,
    Boolean includeDeleted) {}
