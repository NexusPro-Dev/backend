package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Un ajuste de puntos (`RF-MV-052`), con el saldo de puntos en que deja a la persona. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "PointsAdjustmentResponse")
public record PointsAdjustmentResponse(
    UUID id,
    @Schema(description = "El comprobante, con prefijo `AJP`.") String code,
    @Schema(description = "A quién se le ajustaron los puntos.") UUID userId,
    @Schema(description = "Siempre CONFIRMADA: el ajuste se aplica en el acto.") String status,
    @Schema(description = "La moneda de la cuenta de puntos ajustada.") SaleResponse.Money currency,
    @Schema(description = "Los puntos, con su signo: positivos se sumaron, negativos se restaron.")
        BigDecimal points,
    @Schema(description = "Por qué.") String concept,
    @Schema(
            types = {"string", "null"},
            description = "La referencia del comprobante que lo soporta, si se indicó.")
        String reference,
    OffsetDateTime occurredAt,
    OffsetDateTime confirmedAt,
    @Schema(
            description =
                "El saldo de puntos de la persona en esa moneda, ahora. En una petición repetida es"
                    + " el de este momento, que puede haber cambiado desde el ajuste.")
        BigDecimal pointsBalance) {}
