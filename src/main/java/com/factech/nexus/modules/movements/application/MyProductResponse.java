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
        String deliveryNote,
    /**
     * El <b>cupón del bot</b>: dónde registra su cuenta quien ya compró (`RN-MV-032`, `RN-PM-050`).
     *
     * <p><b>Solo si la línea está entregada</b> —{@code ACTIVO} o {@code VENCIDO}—, y nulo en los
     * otros cinco estados <b>aunque el producto lo declare</b>: publicarlo en una {@code
     * PENDIENTE_AUTORIZACION} sería entregar lo comprado sin la autorización que `RN-MV-021` exige,
     * hecho por una consulta en lugar de por una escritura.
     *
     * <p><b>Es el único sitio del sistema, fuera de administración, donde ese enlace se ve.</b>
     *
     * <p>Llega <b>resuelto</b> y se lee <b>del catálogo de hoy</b>: no se copió en la línea, que es
     * la única excepción declarada a `RN-MV-002` — el cupón no es un término de la venta sino el
     * medio de la entrega, de modo que si la dirección del bot cambia, quien compró tiene que
     * recibir la nueva.
     */
    @Schema(
            types = {"string", "null"},
            description =
                "El cupón del bot, ya resuelto. Solo si la línea está entregada (ACTIVO o"
                    + " VENCIDO) y el producto lo declara; NULO en cualquier otro caso.")
        String couponUrl) {

  /** El producto, con el nombre <b>tal como se compró</b> (la copia de la línea, `RN-MV-002`). */
  @Schema(name = "MyProductRef")
  public record ProductRef(UUID id, String code, String name) {}
}
