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
 * Una línea de la venta, tal como quedó (`RF-MV-001` · §6.2).
 *
 * <p><b>{@code unitPrice} y {@code validityDays} son lo que se copió</b>, no lo que el catálogo
 * dice hoy (`RN-MV-002`). Es el campo que hace observable la diferencia: corregir el precio del
 * producto mañana no cambia este número, y esa es la razón de ser de la copia.
 *
 * <p><b>{@code validityDays} nulo significa que lo adquirido no caduca</b> (`RN-PM-015`), no «sin
 * dato». Viaja siempre, también cuando es nulo: omitir la clave se leería como que esta versión no
 * la registraba.
 *
 * <p><b>{@code seller} es a quién se atribuye ESTA línea</b> (`RN-MV-003`, desde el 16-09-2026), y
 * está aquí y no en la cabecera porque la comisión se devenga por línea y cada una puede tener el
 * suyo. En una venta <b>nunca es nulo</b> —quien no cuelga de nadie es su propio vendedor—; se
 * declara nulable porque el contrato es del libro y un depósito lo llevará vacío. Y viaja en nulo y
 * no ausente, por lo mismo que en {@link SaleResponse}.
 *
 * <p><b>{@code lineDiscount} y {@code discounts} son el descuento de la línea</b> (`RN-MV-027`,
 * 16-09-2026). La suma en dinero y las rebajas que la explican viajan juntas: la primera es lo que
 * se restó, las segundas por qué. Hoy la suma es cero y la lista va vacía, y <b>viajan igual</b>:
 * la lista nunca es nula. <b>El paquete no está aquí</b>, sino en la cabecera ({@link
 * SaleResponse#packageId()}), porque una venta lleva uno y nada más (`RN-MV-028`).
 *
 * <p><b>{@code productName} y {@code productDescription} son copias</b> desde el 16-09-2026
 * (`RN-MV-002`): lo que el catálogo decía el día de la venta, y no lo que diga hoy. {@code
 * productCode} <b>no</b> lo es — se lee del catálogo, que `RN-PM-013` declara inmutable—, y esa
 * asimetría es el mismo criterio: se copia lo que puede cambiar.
 *
 * <p><b>La membresía destino no viaja</b>, y su ausencia es coherente con que no se copie: quien
 * necesite saber a qué nivel lleva un upgrade lo pregunta al catálogo, donde `RF-PM-004` garantiza
 * que no ha cambiado.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SaleLineResponse(
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
    BigDecimal lineAmount,
    Integer validityDays,
    @Schema(description = "Lo que se rebajó a la línea: quantity × la suma de sus rebajas.")
        BigDecimal lineDiscount,
    @Schema(description = "Las rebajas que explican lineDiscount. VACÍA —nunca nula— si no hubo.")
        List<SaleDiscountResponse> discounts,
    // LA NULABILIDAD SE DECLARA A MANO, y hay que hacerlo: springdoc no la
    // deduce. Un campo de un `record` sale como un `$ref` pelado —sin
    // `required` y sin tipo—, de modo que el contrato publicado NO DIRÍA que
    // una línea puede no tener vendedor, y el cliente generado lo trataría
    // como «puede no venir», que es otra cosa. Sin esto, un cambio de
    // comportamiento quedaría acordado fuera del contrato — Art. VIII.7.
    //
    // Y se declara con `types` y no con `nullable`, que es lo que uno escribe
    // primero: este contrato se publica como OPENAPI 3.1, donde `nullable`
    // DEJÓ DE SER UNA PALABRA CLAVE y springdoc la descarta EN SILENCIO. La
    // anotación se aplicaba, el contrato salía igual, y nada avisaba.
    @Schema(
            types = {"object", "null"},
            description =
                "A quién se atribuye ESTA línea, y a quién se le creará la comisión por ella."
                    + " En una venta nunca es nulo: quien compra sin colgar de nadie es su propio"
                    + " vendedor. NULO solo en los tipos de movimiento que no venden nada.")
        SaleResponse.Party seller) {

  static SaleLineResponse de(MovementLine linea, SaleResponse.Party vendedor) {
    List<SaleDiscountResponse> rebajas = new ArrayList<>(linea.getDiscounts().size());
    for (LineDiscount rebaja : linea.getDiscounts()) {
      rebajas.add(SaleDiscountResponse.de(rebaja));
    }
    return new SaleLineResponse(
        linea.getProductId(),
        linea.getProductCode(),
        linea.getProductName(),
        linea.getProductDescription(),
        linea.getQuantity(),
        linea.getUnitPrice(),
        linea.getLineAmount(),
        linea.getValidityDays(),
        linea.getLineDiscount(),
        rebajas,
        vendedor);
  }
}
