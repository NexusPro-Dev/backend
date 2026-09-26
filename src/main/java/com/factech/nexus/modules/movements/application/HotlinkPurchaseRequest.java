package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Lo que se envía para comprar por el hotlink de un vendedor (`RF-MV-011`).
 *
 * <p><b>El método de pago y nada más.</b> Ni el producto —va en la ruta, que es la del enlace—, ni
 * el cliente —es quien tiene la sesión—, ni la cantidad: un enlace vende <b>uno</b>.
 *
 * <p><b>Y sobre todo, NO lleva el vendedor</b> (`spec.md` §6). Esa ausencia es el requerimiento
 * entero: la atribución sale de la ruta, que es la prueba de qué enlace se usó, y no de un campo
 * que quien compra podría rellenar con cualquiera. Con el vendedor en el cuerpo, acreditarle una
 * venta a alguien sería tan fácil como escribir su identificador.
 */
public record HotlinkPurchaseRequest(
    @Schema(
            description =
                "Método de pago. **Opcional**: sin él, la venta nace con el que el sistema"
                    + " resuelva por el país de quien compra (`RN-MV-019`), igual que en la"
                    + " compra de un paquete.")
        UUID paymentMethodId) {}
