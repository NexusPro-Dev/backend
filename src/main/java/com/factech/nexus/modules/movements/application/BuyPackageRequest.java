package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/packages/{id}/purchases} (`RF-MV-012`).
 *
 * <h2>Un campo, y esa escasez es el diseño</h2>
 *
 * <p><b>El paquete no está aquí: va en la ruta.</b> El recurso es lo que se compra, y lo que se
 * produce —una venta— viaja en la respuesta y en el {@code Location} (`plan.md` §4).
 *
 * <p><b>No hay productos, ni cantidades, ni precios, ni descuentos</b> (`RN-MV-028`, `CA-MV-058`).
 * El paquete se compra <b>entero</b> —no se elige qué llevarse—, <b>uno</b> —`RN-PM-038` ya decidió
 * que dentro del paquete no hay cantidad— y <b>solo</b> —sin productos sueltos ni un segundo
 * paquete—. Y el descuento es el que el paquete declara (`RN-PM-037`): uno que llegara en la
 * petición sería un descuento que elige quien compra. Ninguno de esos datos <b>tiene dónde
 * caer</b>: no existen en esta representación, que es lo que hace verificable que el cliente no
 * puede negociar nada.
 *
 * <p><b>No hay cliente ni fecha</b>, por las razones de `RF-MV-002`: el cliente es quien pide y la
 * fecha es ahora.
 *
 * @param paymentMethodId <b>condicional en los dos sentidos</b> (`RN-MV-022`), como en `RF-MV-001`:
 *     <b>prohibido</b> si el paquete vale cero —se registra con el pago gratuito, que asigna el
 *     sistema— y <b>obligatorio</b> si tiene importe. No lleva {@code @NotNull} porque la exigencia
 *     depende del importe, y el importe no se conoce mirando el cuerpo
 */
@Schema(
    description =
        "Con qué se paga, y nada más: el paquete va en la ruta, y ni productos, ni cantidad, ni"
            + " precio, ni descuento se admiten. Vacío (`{}`) cuando el paquete vale cero.")
public record BuyPackageRequest(
    @Schema(
            types = {"string", "null"},
            format = "uuid",
            description =
                "Obligatorio si el paquete tiene importe; PROHIBIDO si vale cero, que se registra"
                    + " como gratuito.")
        UUID paymentMethodId) {}
