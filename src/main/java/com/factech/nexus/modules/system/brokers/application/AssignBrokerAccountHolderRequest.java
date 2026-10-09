package com.factech.nexus.modules.system.brokers.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A quién se le da una cuenta de broker sin titular (`RF-SP-082`). */
public record AssignBrokerAccountHolderRequest(
    @Schema(description = "La persona, consumidor, que pasa a ser la titular de la cuenta.")
        UUID userId) {}
