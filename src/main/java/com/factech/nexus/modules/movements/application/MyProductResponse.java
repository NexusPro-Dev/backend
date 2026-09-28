package com.factech.nexus.modules.movements.application;

import com.factech.nexus.modules.products.application.OfferItem;
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
    @Schema(
            description =
                "La línea de venta: lo que se activa en"
                    + " /movements/mine/products/{lineId}/activation.")
        UUID lineId,
    @Schema(description = "De qué venta viene; se abre en /movements/mine/{id}.") UUID movementId,
    String movementCode,
    String movementStatus,
    /**
     * El producto <b>en la forma de la oferta</b> —la de {@code GET /products/available}— y <b>como
     * está hoy</b> en el catálogo (28-09-2026): un solo lector para la tienda y para lo comprado.
     * Hasta esa fecha era una referencia de tres campos con el nombre de la compra, que ahora viaja
     * en {@link #purchasedName}.
     *
     * <p>Sin {@code CUPON_BOT} entre sus enlaces, como en la oferta: el cupón viaja en {@link
     * #couponUrl} y solo cuando la línea está entregada (`RN-MV-032`).
     */
    @Schema(
            description =
                "El producto como en /products/available, leído del catálogo de hoy. Sin el"
                    + " cupón del bot: ese viaja en couponUrl.")
        OfferItem product,
    @Schema(description = "El nombre del producto el día de la compra (RN-MV-002).")
        String purchasedName,
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
     * otros estados <b>aunque el producto lo declare</b>: publicarlo en una {@code
     * PENDIENTE_ACTIVACION} sería entregar lo comprado antes de que quien lo compró lo active
     * (`RN-MV-021`, `RN-MV-048`), hecho por una consulta en lugar de por una escritura.
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
        String couponUrl) {}
