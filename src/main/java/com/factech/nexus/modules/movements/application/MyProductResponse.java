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
     * <p><b>Sus enlaces dependen de la línea</b> (`RN-MV-032`, 28-09-2026): desde que la venta está
     * pagada —{@link PurchasedProductState#estaPagado()}— son <b>todos</b> los del producto, los de
     * entrega incluidos ({@code CUPON_BOT}, {@code DESCARGA}), para que el cupón sirva para
     * activar; sin pagar, <b>los de la oferta</b>, aunque el producto declare los de entrega. Hasta
     * el 28-09-2026 el cupón viajaba aparte, en {@code couponUrl}, y solo con la línea entregada.
     *
     * <p>Se leen <b>del catálogo de hoy</b> y no se copiaron en la línea —única excepción declarada
     * a `RN-MV-002`—: si la dirección del bot o de la descarga cambia, quien compró recibe la
     * nueva.
     */
    @Schema(
            description =
                "El producto como en /products/available, leído del catálogo de hoy. Sus links:"
                    + " TODOS los del producto —CUPON_BOT y DESCARGA incluidos— desde que la venta"
                    + " está pagada (PENDIENTE_ACTIVACION, ACTIVO, VENCIDO, CANCELADO, RETENIDO);"
                    + " en PENDIENTE_PAGO, RECHAZADO y ANULADO, los mismos que la oferta.")
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
        String deliveryNote) {}
