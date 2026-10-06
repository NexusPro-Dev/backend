package com.factech.nexus.modules.indicators.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Qué se contó: los dos días efectivos —los pedidos o los de por defecto— y la zona en que se
 * interpretan (`RF-IN-001` §6.2).
 */
@Schema(name = "IndicatorPeriod")
public record IndicatorPeriod(
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(
            description =
                "Primer día del periodo, incluido; nulo si no se pidió: desde el principio.",
            example = "2026-09-01")
        LocalDate from,
    @Schema(description = "Último día del periodo, incluido entero.", example = "2026-09-30")
        LocalDate to,
    @Schema(description = "Zona en que se cortan los días.", example = "America/Bogota")
        String zone) {}
