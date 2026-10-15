package com.factech.nexus.modules.system.brokers.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de {@code PATCH /api/v1/brokers/{id}} (`RF-SP-084`).
 *
 * <p><b>Solo los dos campos que se editan</b>: el nombre, el {@code advertiser} y el estado no
 * están, de modo que enviarlos es {@code 400} por {@code FAIL_ON_UNKNOWN_PROPERTIES}, como en la
 * edición de una entidad de cobro (`RF-MV-034`).
 *
 * <p><b>Ausente o nulo no se toca; {@code ""} vacía el campo.</b>
 */
@Schema(name = "UpdateBrokerRequest")
public record UpdateBrokerRequest(
    @Schema(
            types = {"string", "null"},
            description =
                "Enlace de registro, con http:// o https://, hasta 500 caracteres. \"\" lo vacía.")
        String url,
    @Schema(
            types = {"string", "null"},
            description = "Modelo, texto libre hasta 120 caracteres. \"\" lo vacía.")
        String model) {}
