package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Un movimiento <b>que no vende</b> (`RN-MV-046`): un retiro, un bono o el pago de una comisión.
 * Sin líneas y sin vendedor; el importe es el de la cabecera.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "LedgerMovement")
public record LedgerMovementResponse(
    UUID id,
    String code,
    @Schema(description = "RETIRO, BONO o PAGO_COMISION.") String type,
    @Schema(
            description =
                "Un retiro: PENDIENTE, CONFIRMADA (aprobado) o RECHAZADA (negado). Un bono o un"
                    + " pago de comisión nacen CONFIRMADA.")
        String status,
    BigDecimal amount,
    SaleResponse.Money currency,
    @Schema(
            types = {"string", "null"},
            description = "El motivo de un bono; el lote citado en un pago de comisión.")
        String concept,
    OffsetDateTime occurredAt,
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
            description = "Por qué se negó un retiro. NULO en todo lo demás.")
        String rejectionReason,
    @Schema(
            description =
                "El pago que lo liquida, en un retiro aprobado. Vacío en todo lo demás: un bono y"
                    + " un abono no mueven dinero por fuera.")
        List<PaymentResponse> payments) {}
