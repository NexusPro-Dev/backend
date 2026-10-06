package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * El detalle de un movimiento de puntos (`RF-MV-055`, `RF-MV-056`): la fila, y lo que solo cabe en
 * un detalle —la tasa, los pagos y el motivo del rechazo de una compra; el comprobante de un
 * ajuste—.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "PointsMovementDetail")
public record PointsMovementDetail(
    @Schema(description = "Lo mismo que la fila de la lista.") PointsMovementItem movement,
    @Schema(
            types = {"object", "null"},
            description = "La tasa con que se compró, congelada. Solo en una compra.")
        PointsPurchaseResponse.Rate pointsRate,
    @Schema(
            types = {"string", "null"},
            description = "Por qué se rechazó el pago. Solo en una compra rechazada.")
        String rejectionReason,
    @Schema(
            description =
                "Los pagos de una compra, del más antiguo al más reciente. Vacía en un ajuste.")
        List<PaymentResponse> payments,
    @Schema(
            types = {"object", "null"},
            description =
                "El comprobante de un ajuste, sin el archivo. Nulo si no lo tiene o en una compra.")
        PointsReceiptInfo receipt) {}
