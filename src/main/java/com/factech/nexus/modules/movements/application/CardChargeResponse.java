package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * El cobro con tarjeta abierto en la pasarela (`RF-MV-040`, `RF-MV-042`): lo que la app necesita
 * para pedir la tarjeta con el formulario de la pasarela y confirmarlo <b>ante ella</b>. <b>No
 * confirma nada</b>: lo confirma la notificación de la pasarela (`RF-MV-041`).
 */
@Schema(name = "CardCharge")
public record CardChargeResponse(
    @Schema(description = "El pago al que pertenece el cobro.") UUID paymentId,
    @Schema(description = "La pasarela: STRIPE.") String gateway,
    @Schema(
            description =
                "El secreto de cliente del cobro (el `client_secret` del PaymentIntent de Stripe)."
                    + " Se usa con la clave publicable en Stripe Elements; no se guarda en ningún"
                    + " sitio y se puede volver a pedir con `POST /movements/mine/{id}/card-charge`.")
        String clientSecret) {}
