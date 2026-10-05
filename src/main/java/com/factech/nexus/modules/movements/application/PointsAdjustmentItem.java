package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Una fila del listado de ajustes de puntos (`RF-MV-053`). Sin el documento de la persona. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "PointsAdjustmentItem")
public record PointsAdjustmentItem(
    UUID id,
    @Schema(description = "El comprobante, con prefijo `AJP`.") String code,
    @Schema(description = "Siempre CONFIRMADA.") String status,
    @Schema(description = "A quién se le ajustaron los puntos.") Person user,
    SaleResponse.Money currency,
    @Schema(description = "Con su signo: positivos se sumaron, negativos se restaron.")
        BigDecimal points,
    String concept,
    @Schema(types = {"string", "null"}) String reference,
    OffsetDateTime occurredAt,
    OffsetDateTime confirmedAt,
    @Schema(
            types = {"object", "null"},
            description =
                "Quién hizo el ajuste. NULO en los ajustes anteriores a que se guardara (V73).")
        Actor adjustedBy) {

  @Schema(name = "PointsAdjustmentPerson")
  public record Person(UUID id, String fullName, String username, String email) {}

  @Schema(name = "PointsAdjustmentActor")
  public record Actor(UUID id, String fullName) {}
}
