package com.factech.nexus.modules.movements.application;

import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementLine;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * La venta <b>como la ve quien la compró</b> (`RF-MV-002` · §4.3, `RF-MV-012` · §6.2): la misma de
 * {@link SaleResponse}, <b>sin el vendedor</b>.
 *
 * <h2>Es otra clase, y no un campo nulo</h2>
 *
 * <p>`RF-MV-001` devuelve el vendedor y una compra propia no (`RF-MV-002` · `plan.md` §3.2). Se
 * resuelve con <b>dos representaciones de salida</b> y no con un campo que a veces viene vacío: un
 * campo opcional obligaría a cada consumidor a preguntarse <b>por qué</b> falta —¿no hay vendedor?
 * ¿no se pudo resolver?— cuando la respuesta es «porque a ti no se te enseña». Dos formas distintas
 * no dejan esa pregunta abierta.
 *
 * <p>Todo lo demás es idéntico, <b>y se construye desde el mismo agregado</b>: el paquete en la
 * cabecera (`RN-MV-028`), las tres cifras sumadas por {@link Movement}, y cada línea con lo copiado
 * y sus rebajas explicadas (`RN-MV-027`). Es lo que hace cierto `CA-MV-059`: la venta registrada es
 * indistinguible de cualquier otra; solo cambia quién la mira.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PurchaseResponse(
    UUID id,
    String code,
    String status,
    @Schema(
            description =
                "El estado del TIPO de movimiento (`RN-MV-033`), aparte del pago. En una venta:"
                    + " VALIDAR_COMISIONES —alguna línea no tiene vendedor, porque quien compra"
                    + " tenía varios y falta elegir (`RF-MV-016`)— o VALIDADO —todas lo tienen—.")
        String typeStatus,
    @Schema(
            description =
                "Quien compró: el sujeto del movimiento, que aquí es también quien pidió.")
        SaleResponse.Party user,
    @Schema(
            types = {"string", "null"},
            format = "uuid",
            description =
                "El paquete que se compró. NULO en toda venta que no sea de un paquete. Con el"
                    + " productId de cada línea identifica qué asociación le dio su descuento.")
        UUID packageId,
    SaleResponse.Money currency,
    String paymentMethod,
    @Schema(description = "Las líneas, una por producto, cada una con su rebaja explicada.")
        List<PurchaseLineResponse> lines,
    @Schema(description = "Lo que valdría lo vendido sin rebaja: la suma de quantity × unitPrice.")
        BigDecimal totalAmount,
    @Schema(description = "La suma de lo que cada línea rebajó.") BigDecimal discountAmount,
    @Schema(
            description =
                "Lo que se cobra: totalAmount − discountAmount, que es la suma de las líneas"
                    + " rebajadas. En un paquete coincide con el `price` que el catálogo publica.")
        BigDecimal payableAmount,
    OffsetDateTime occurredAt,
    @Schema(
            types = {"string", "null"},
            format = "date-time",
            description = "Cuándo entró el dinero. NULO mientras la venta no esté confirmada.")
        OffsetDateTime confirmedAt,
    OffsetDateTime createdAt) {

  public static PurchaseResponse de(
      Movement venta, SaleResponse.Party sujeto, SaleResponse.Money moneda, String metodoDePago) {
    List<PurchaseLineResponse> lineas = new ArrayList<>(venta.getLines().size());
    for (MovementLine linea : venta.getLines()) {
      lineas.add(PurchaseLineResponse.de(linea));
    }
    return new PurchaseResponse(
        venta.getId(),
        venta.getCode(),
        venta.getStatus().name(),
        venta.getTypeStatus().code(),
        sujeto,
        venta.getPackageId(),
        moneda,
        metodoDePago,
        lineas,
        venta.getTotalAmount(),
        venta.getDiscountAmount(),
        venta.getPayableAmount(),
        venta.getOccurredAt(),
        // Acaba de comprarse: nadie ha confirmado nada.
        null,
        venta.getCreatedAt());
  }
}
