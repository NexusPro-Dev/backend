package com.factech.nexus.modules.movements.application;

import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementLine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * La venta registrada (`RF-MV-001` · §6.2).
 *
 * <h2>El vendedor se devuelve siempre, y no es un adorno</h2>
 *
 * <p>Quien registra la venta <b>no lo eligió</b>: sale del cliente y se congela (`RN-MV-003`). Esta
 * respuesta es el único momento en que puede ver a quién acaba de atribuirse lo que vendió — y si
 * es el equivocado, el problema está en la estructura comercial y no en esta venta.
 *
 * <h2>El descuento se devuelve aunque valga siempre cero</h2>
 *
 * <p>Omitirlo obligaría a añadirlo al contrato el día que exista, y a que todos los consumidores lo
 * trataran como opcional para siempre.
 *
 * <p><b>{@code status} es siempre {@code PENDIENTE}</b> y viaja igualmente. Quien consuma esta
 * respuesta no debe deducir el estado de que la operación haya tenido éxito: lo debe leer, porque
 * `RF-MV-003` a `RF-MV-005` devolverán el mismo objeto con otro valor.
 */
public record SaleResponse(
    UUID id,
    String code,
    String status,
    Party client,
    Party seller,
    Money currency,
    String paymentMethod,
    List<SaleLineResponse> lines,
    BigDecimal totalAmount,
    BigDecimal discountAmount,
    BigDecimal payableAmount,
    OffsetDateTime occurredAt,
    OffsetDateTime createdAt) {

  /**
   * Una persona de la venta, <b>resuelta</b>.
   *
   * <p>Con su nombre y no solo su identificador: quien mira una venta necesita saber a quién se le
   * vendió y a quién se le atribuye, y devolver dos {@code uuid} obligaría a una consulta más para
   * responder a la pregunta que la operación acaba de contestar.
   */
  @Schema(name = "SaleParty")
  public record Party(UUID id, String username, String name) {}

  /** La moneda de la venta, resuelta. Es una sola para toda ella (`RN-MV-012`). */
  @Schema(name = "SaleCurrency")
  public record Money(UUID id, String code) {}

  /** Arma la respuesta a partir del agregado y de lo que se resolvió para construirlo. */
  public static SaleResponse de(
      Movement venta, Party cliente, Party vendedor, Money moneda, String metodoDePago) {

    List<SaleLineResponse> lineas = new ArrayList<>(venta.getLines().size());
    for (MovementLine linea : venta.getLines()) {
      lineas.add(SaleLineResponse.de(linea));
    }

    return new SaleResponse(
        venta.getId(),
        venta.getCode(),
        venta.getStatus().name(),
        cliente,
        vendedor,
        moneda,
        metodoDePago,
        lineas,
        venta.getTotalAmount(),
        venta.getDiscountAmount(),
        venta.getPayableAmount(),
        venta.getOccurredAt(),
        venta.getCreatedAt());
  }
}
