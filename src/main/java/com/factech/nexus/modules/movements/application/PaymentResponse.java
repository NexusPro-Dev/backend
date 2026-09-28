package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un intento de pago de un movimiento (`RN-MV-039`), tal como lo publica el detalle (`RN-MV-047`).
 *
 * <p><b>No lleva la clave de idempotencia</b>: es del cliente que la mandó, y a quien lee no le
 * dice nada.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "MovementPayment")
public record PaymentResponse(
    UUID id,
    @Schema(description = "Con qué se intentó pagar.") Method paymentMethod,
    @Schema(
            description =
                "PENDIENTE —nadie lo ha resuelto—, CONFIRMADO —el dinero entró— o RECHAZADO. En"
                    + " masculino: es el estado de un pago, no de la venta.")
        String status,
    BigDecimal amount,
    @Schema(
            types = {"string", "null"},
            description =
                "La referencia de quien cobra, o de la transferencia en un retiro. NULA si nadie"
                    + " la dio.")
        String providerReference,
    @Schema(description = "Cuándo se intentó.") OffsetDateTime occurredAt,
    @Schema(
            types = {"string", "null"},
            format = "date-time")
        OffsetDateTime confirmedAt,
    @Schema(
            types = {"string", "null"},
            format = "date-time")
        OffsetDateTime rejectedAt,
    @Schema(
            types = {"string", "null"},
            description =
                "Por qué no entró el cobro. En el pago de una venta anulada, el motivo de la"
                    + " anulación.")
        String rejectionReason) {

  @Schema(name = "MovementPaymentMethod")
  public record Method(UUID id, String code, String name) {}
}
