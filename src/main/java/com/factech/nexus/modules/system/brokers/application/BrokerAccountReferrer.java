package com.factech.nexus.modules.system.brokers.application;

import java.util.UUID;

/**
 * La cuenta de vendedor que originó una de consumidor (`RN-SP-070`): su identificador, su {@code
 * afftrack} y su titular —el vendedor—, con nombre y apellido desde el 10-10-2026 (`RF-SP-057`
 * v0.5.0).
 */
public record BrokerAccountReferrer(
    UUID id, String afftrack, UUID userId, String username, String firstName, String lastName) {}
