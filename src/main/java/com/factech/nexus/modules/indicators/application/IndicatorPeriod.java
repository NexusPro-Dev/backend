package com.factech.nexus.modules.indicators.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Qué se contó: los dos días efectivos —los pedidos o los de por defecto— y la zona en que se
 * interpretan (`RF-IN-001` §6.2).
 */
@Schema(name = "IndicatorPeriod")
public record IndicatorPeriod(
    @Schema(description = "Primer día del periodo, incluido.", example = "2026-09-01")
        LocalDate from,
    @Schema(description = "Último día del periodo, incluido entero.", example = "2026-09-30")
        LocalDate to,
    @Schema(description = "Zona en que se cortan los días.", example = "America/Bogota")
        String zone) {}
