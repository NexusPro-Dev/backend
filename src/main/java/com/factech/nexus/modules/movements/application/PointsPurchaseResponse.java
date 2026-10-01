package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Una compra de puntos (`RF-MV-027` a `RF-MV-029`, `RF-MV-031`). La misma forma en las cuatro: la
 * que registra, la que confirma, la que rechaza y la que lista.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "PointsPurchaseResponse")
public record PointsPurchaseResponse(
    UUID id,
    @Schema(description = "El comprobante, con prefijo `PTS`.") String code,
    @Schema(
            description =
                "PENDIENTE —esperando a que se confirme el pago—, CONFIRMADA —los puntos ya están"
                    + " en la cuenta— o RECHAZADA.")
        String status,
    SaleResponse.Money currency,
    @Schema(description = "Lo que se paga, en la moneda.") BigDecimal amount,
    @Schema(description = "La tasa con que se compró, congelada.") Rate pointsRate,
    @Schema(
            description =
                "Los puntos que da: `amount × pointsPerUnit`, redondeados hacia abajo. Confirmar"
                    + " abona exactamente estos.")
        BigDecimal points,
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
            description = "Por qué se rechazó el pago. Nulo salvo en una compra rechazada.")
        String rejectionReason,
    List<PaymentResponse> payments,
    @Schema(
            types = {"object", "null"},
            description =
                "El cobro abierto en la pasarela, solo al comprar con tarjeta (`RF-MV-040`, desde"
                    + " el 01-10-2026). NULO en todo lo demás.")
        CardChargeResponse cardCharge) {

  /** La misma compra, con el cobro abierto; nulo no cambia nada. */
  public PointsPurchaseResponse conCobro(CardChargeResponse cobro) {
    if (cobro == null) {
      return this;
    }
    return new PointsPurchaseResponse(
        id,
        code,
        status,
        currency,
        amount,
        pointsRate,
        points,
        occurredAt,
        confirmedAt,
        rejectedAt,
        rejectionReason,
        payments,
        cobro);
  }

  @Schema(name = "PointsPurchaseRate")
  public record Rate(UUID id, BigDecimal pointsPerUnit) {}
}
