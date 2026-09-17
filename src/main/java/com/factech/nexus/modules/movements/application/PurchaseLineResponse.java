package com.factech.nexus.modules.movements.application;

import com.factech.nexus.modules.movements.domain.models.LineDiscount;
import com.factech.nexus.modules.movements.domain.models.MovementLine;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Una línea de la compra propia: {@link SaleLineResponse} <b>sin el vendedor</b>.
 *
 * <p>Ver {@link PurchaseResponse} para por qué es otra clase y no un campo nulo. Todo lo demás es
 * la misma copia: {@code unitPrice}, {@code validityDays}, {@code productName} y {@code
 * productDescription} son lo que el catálogo decía el día de la venta (`RN-MV-002`), y {@code
 * lineDiscount} con {@code discounts} son la rebaja de la línea como se cobró y como se pactó
 * (`RN-MV-027`).
 *
 * <p><b>El descuento viaja explicado y no solo restado</b> (`RF-MV-012` · §6.2): quien recibe esta
 * respuesta tiene que poder decirle al cliente «10 % sobre 49.99, que son 5.00 menos», y no solo
 * «pagas 44.99».
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PurchaseLineResponse(
    UUID productId,
    String productCode,
    @Schema(description = "COPIA del nombre que el producto tenía el día de la venta.")
        String productName,
    @Schema(
            types = {"string", "null"},
            description =
                "COPIA de la descripción del día de la venta. NULA si el producto no la declaraba.")
        String productDescription,
    int quantity,
    BigDecimal unitPrice,
    @Schema(description = "quantity × unitPrice − lineDiscount.") BigDecimal lineAmount,
    @Schema(
            types = {"integer", "null"},
            description = "Vigencia copiada, en días. NULA si lo adquirido no caduca.")
        Integer validityDays,
    @Schema(description = "Lo que se rebajó a la línea: quantity × la suma de sus rebajas.")
        BigDecimal lineDiscount,
    @Schema(description = "Las rebajas que explican lineDiscount. VACÍA —nunca nula— si no hubo.")
        List<SaleDiscountResponse> discounts) {

  static PurchaseLineResponse de(MovementLine linea) {
    List<SaleDiscountResponse> rebajas = new ArrayList<>(linea.getDiscounts().size());
    for (LineDiscount rebaja : linea.getDiscounts()) {
      rebajas.add(SaleDiscountResponse.de(rebaja));
    }
    return new PurchaseLineResponse(
        linea.getProductId(),
        linea.getProductCode(),
        linea.getProductName(),
        linea.getProductDescription(),
        linea.getQuantity(),
        linea.getUnitPrice(),
        linea.getLineAmount(),
        linea.getValidityDays(),
        linea.getLineDiscount(),
        rebajas);
  }
}
