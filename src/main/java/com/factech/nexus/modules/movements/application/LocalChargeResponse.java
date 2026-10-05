package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * El cobro abierto en la pasarela local (`RF-MV-048`, `RF-MV-051`): a dónde llevar al cliente y
 * cuánto pagará en su moneda. <b>No confirma nada</b>: lo confirma la consulta a la pasarela que
 * disparan su aviso o el barrido (`RN-MV-064`).
 */
@Schema(name = "LocalCharge")
public record LocalChargeResponse(
    @Schema(description = "El pago al que pertenece el cobro.") UUID paymentId,
    @Schema(description = "La pasarela: PAYRETAILERS.") String gateway,
    @Schema(
            description =
                "La página de pago de la pasarela. La app lleva allí al cliente, que elige el"
                    + " método —PSE, Nequi, Efecty, Bre-B— y vuelve al terminar.")
        String checkoutUrl,
    @Schema(description = "La moneda local en que se cobra.") SaleResponse.Money currency,
    @Schema(
            description =
                "Lo que se cobra en moneda local: el importe del pago por el precio de cobro de la"
                    + " conversión del país, redondeado hacia arriba a unidad entera (`RN-MV-063`).")
        BigDecimal amount) {}
