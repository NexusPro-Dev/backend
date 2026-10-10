package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * La respuesta del relleno de oficinas (`RF-MV-058`): cuántas líneas ganaron la suya.
 *
 * @param filled las líneas rellenadas en esta orden; cero si no quedaba ninguna por rellenar, o si
 *     ningún vendedor de las que quedan tiene hoy oficina
 */
@Schema(name = "LineTeamFillResponse")
public record LineTeamFillResponse(
    @Schema(
            description =
                "Cuántas líneas ganaron oficina en esta orden. Cero si no quedaba ninguna, o si"
                    + " ningún vendedor de las que quedan tiene hoy oficina.")
        int filled) {}
