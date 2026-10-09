package com.factech.nexus.modules.system.brokers.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corregir una cuenta de broker (`RF-SP-080`): <b>solo el identificador</b>. El broker no se cambia
 * —se borra la cuenta y se declara otra—, y el nombre de usuario y el estado los pone el broker.
 */
public record UpdateBrokerAccountRequest(
    @Schema(
            description =
                "El identificador nuevo de la cuenta en el broker. Sin espacios a los lados; entre 1"
                    + " y 80 caracteres.",
            example = "70000001")
        String accountId,
    @Schema(
            description =
                "Solo en una cuenta de VENDEDOR (`RN-SP-071`): el código de afiliado nuevo; vacío"
                    + " lo borra. Va al menos uno de los dos campos.",
            example = "DIEGOIQ")
        String afftrack) {}
