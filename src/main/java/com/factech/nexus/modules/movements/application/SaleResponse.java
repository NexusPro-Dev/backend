package com.factech.nexus.modules.movements.application;

import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementLine;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La venta registrada (`RF-MV-001` · §6.2).
 *
 * <h2>La cabecera lleva UN sujeto, y el vendedor va en cada línea</h2>
 *
 * <p>{@code user} es <b>a nombre de quién</b> es el movimiento (`RN-MV-026`): en una venta, quien
 * compra. Hasta el 16-09-2026 se llamó {@code client}, y el nombre cambió con la columna: este
 * objeto es el del libro entero, y en un depósito o en una comisión «cliente» sería un nombre
 * falso.
 *
 * <p>El vendedor <b>se devuelve en cada línea</b> ({@link SaleLineResponse#seller()}), y no es un
 * adorno: quien registra la venta <b>no lo eligió</b> —sale de quien compra y se congela,
 * `RN-MV-003`— y esta respuesta es el único momento en que puede ver a quién acaba de atribuirse lo
 * que vendió. Va en la línea porque la comisión se devenga por línea y porque cada una puede tener
 * el suyo; hoy todas las de una venta llevan el mismo.
 *
 * <p><b>En una venta nunca viene en nulo</b>: quien no cuelga de nadie es su propio vendedor. La
 * clave se declara nulable de todos modos, porque el contrato es del libro y no de la venta, y un
 * depósito la llevará vacía. Y viaja <b>en nulo y no ausente</b>: por eso este registro lleva
 * {@code @JsonInclude(ALWAYS)} y se aparta del {@code non_null} global de {@code application.yml}.
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
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SaleResponse(
    UUID id,
    String code,
    String status,
    @Schema(
            description =
                "El SUJETO del movimiento: a nombre de quién es. En una venta, quien compra."
                    + " Nunca quien la registró desde oficina.")
        Party user,
    Money currency,
    String paymentMethod,
    @Schema(description = "Las líneas, cada una con el vendedor al que se atribuye.")
        List<SaleLineResponse> lines,
    BigDecimal totalAmount,
    BigDecimal discountAmount,
    BigDecimal payableAmount,
    OffsetDateTime occurredAt,
    OffsetDateTime createdAt) {

  @Schema(name = "SaleParty")
  public record Party(UUID id, String username, String name) {}

  @Schema(name = "SaleCurrency")
  public record Money(UUID id, String code) {}

  /**
   * @param vendedores los vendedores de las líneas, por identificador. Los resuelve el caso de uso,
   *     que es quien tiene el catálogo de personas; aquí solo se casan con cada línea
   */
  public static SaleResponse de(
      Movement venta,
      Party sujeto,
      Map<UUID, Party> vendedores,
      Money moneda,
      String metodoDePago) {
    List<SaleLineResponse> lineas = new ArrayList<>(venta.getLines().size());
    for (MovementLine linea : venta.getLines()) {
      lineas.add(SaleLineResponse.de(linea, vendedores.get(linea.getSellerId())));
    }
    return new SaleResponse(
        venta.getId(),
        venta.getCode(),
        venta.getStatus().name(),
        sujeto,
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
