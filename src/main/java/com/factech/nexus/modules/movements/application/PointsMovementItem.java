package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila de las dos listas de movimientos de puntos (`RF-MV-055`, `RF-MV-056`): una compra de
 * puntos o un ajuste, en la misma forma. Lo que no aplica a uno va nulo —una compra no tiene
 * motivo; un ajuste no tiene importe—. <b>La misma forma en los dos alcances</b>: en el propio,
 * {@code adjustedBy} va siempre nulo (`RF-MV-055` `spec.md` §2.1).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "PointsMovementItem")
public record PointsMovementItem(
    UUID id,
    @Schema(description = "El comprobante: prefijo `PTS` en una compra, `AJP` en un ajuste.")
        String code,
    @Schema(description = "COMPRA_PUNTOS o AJUSTE_PUNTOS.") String type,
    @Schema(description = "PENDIENTE, CONFIRMADA o RECHAZADA. Un ajuste es siempre CONFIRMADA.")
        String status,
    @Schema(description = "De quién son los puntos.") Person user,
    SaleResponse.Money currency,
    @Schema(
            description =
                "Con su signo. Una compra suma siempre —pendiente, los que dará; rechazada, los que"
                    + " habría dado—; un ajuste suma o resta.")
        BigDecimal points,
    @Schema(
            types = {"number", "null"},
            description = "Lo que se paga, en la moneda. Solo en una compra.")
        BigDecimal amount,
    @Schema(
            types = {"string", "null"},
            description = "Por qué. Solo en un ajuste.")
        String concept,
    @Schema(
            types = {"string", "null"},
            description = "La referencia del comprobante. Solo en un ajuste, si se indicó.")
        String reference,
    OffsetDateTime occurredAt,
    @Schema(
            types = {"string", "null"},
            format = "date-time")
        OffsetDateTime confirmedAt,
    @Schema(
            types = {"string", "null"},
            format = "date-time")
        OffsetDateTime rejectedAt,
    @Schema(description = "Si el ajuste tiene comprobante adjunto. Siempre false en una compra.")
        boolean hasReceipt,
    @Schema(
            types = {"object", "null"},
            description =
                "Quién hizo el ajuste. NULO en una compra, en los ajustes anteriores a que se"
                    + " guardara y SIEMPRE en la lista propia.")
        Actor adjustedBy) {

  @Schema(name = "PointsMovementPerson")
  public record Person(UUID id, String fullName, String username, String email) {}

  @Schema(name = "PointsMovementActor")
  public record Actor(UUID id, String fullName) {}
}
