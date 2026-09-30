package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Una tasa de puntos (`RF-MV-025`, `RF-MV-026`). */
@Schema(name = "PointsRateResponse")
public record PointsRateResponse(
    UUID id,
    SaleResponse.Money currency,
    @Schema(
            description =
                "Cuántos puntos da una unidad de la moneda. `100.0000` es «1 = 100 puntos».")
        BigDecimal pointsPerUnit,
    @Schema(description = "Desde cuándo rige.") OffsetDateTime validFrom) {}
