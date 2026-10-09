package com.factech.nexus.modules.system.brokers.application;

import java.util.UUID;

/**
 * La cuenta de vendedor que originó una de consumidor (`RN-SP-070`): su identificador, su {@code
 * afftrack} y su titular, con lo justo para nombrarlo.
 */
public record BrokerAccountReferrer(UUID id, String afftrack, UUID userId, String username) {}
