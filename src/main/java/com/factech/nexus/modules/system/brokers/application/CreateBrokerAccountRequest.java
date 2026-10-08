package com.factech.nexus.modules.system.brokers.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Declarar una cuenta de broker (`RF-SP-053`): <b>el broker y el identificador</b>, lo que la
 * persona conoce (`RN-SP-040`). El nombre de usuario en el broker y el estado no viajan: los pone
 * el broker.
 *
 * <p>{@code accountId} se llama igual que en el registro por enlace y en la respuesta de
 * `RF-SP-055`: es el identificador <b>en el broker</b>, no el de la fila.
 */
public record CreateBrokerAccountRequest(
    @Schema(description = "El broker, del catálogo de `GET /api/v1/brokers`. Debe estar activo.")
        UUID brokerId,
    @Schema(
            description =
                "El identificador de la cuenta en el broker. Se guarda sin espacios a los lados;"
                    + " entre 1 y 80 caracteres.",
            example = "70000001")
        String accountId) {}
