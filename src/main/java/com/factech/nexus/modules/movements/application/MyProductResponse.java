package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un producto comprado por quien consulta (`RF-MV-014`): <b>una fila por línea de venta</b>, con el
 * estado calculado y hasta cuándo se tiene.
 *
 * <p>Dos compras del mismo producto son dos filas, cada una con su venta, su estado y su vigencia:
 * agruparlas obligaría a decidir qué vigencia manda, y esa decisión no existe.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "MyProduct", description = "Un producto de una compra propia, con su estado.")
public record MyProductResponse(
    @Schema(description = "De qué venta viene; se abre en /movements/mine/{id}.") UUID movementId,
    String movementCode,
    String movementStatus,
    ProductRef product,
    int quantity,
    @Schema(description = "COPIA de cómo se entrega: AUTOMATICA o MANUAL.") String implementation,
    @Schema(description = "El estado, calculado de la venta, la entrega y la vigencia.")
        PurchasedProductState state,
    @Schema(description = "Cuándo se compró: la fecha de la venta.") OffsetDateTime purchasedAt,
    @Schema(
            types = {"string", "null"},
            format = "date-time",
            description = "Desde cuándo se tiene. NULO si no se ha entregado.")
        OffsetDateTime deliveredAt,
    @Schema(
            types = {"string", "null"},
            format = "date-time",
            description =
                "Hasta cuándo se tiene: la entrega más la vigencia comprada. NULO si no se ha"
                    + " entregado o si no caduca.")
        OffsetDateTime validUntil,
    @Schema(
            types = {"string", "null"},
            description = "Por qué no se entregará. Solo en RETENIDO.")
        String deliveryNote) {

  /** El producto, con el nombre <b>tal como se compró</b> (la copia de la línea, `RN-MV-002`). */
  @Schema(name = "MyProductRef")
  public record ProductRef(UUID id, String code, String name) {}
}
