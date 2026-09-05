package com.factech.nexus.modules.system.users.application;

import java.util.Set;
import java.util.UUID;

/**
 * Entrada del caso de uso de alta de persona (`RF-SP-024`).
 *
 * <p><b>Sin estado ni marca de cambio obligatorio</b>: la cuenta nace `ACTIVO` y marcada, y no
 * admitirlos como argumento es lo que deja un solo camino hacia cada valor.
 *
 * @param membershipId <b>opcional desde el 05-09-2026</b>. Ausente, la persona nace en el nivel de
 *     arranque (`RN-SP-018`). Hasta entonces era condicional en los dos sentidos —exigido si había
 *     rol consumidor, prohibido si no—, y esa exigencia murió con `RN-SP-013`
 * @param supervisorId condicional en los dos sentidos, que es lo que {@code membershipId} dejó de
 *     ser: exigido si hay rol vendedor, prohibido si no (`RN-SP-019`)
 */
public record RegisterUserCommand(
    String username,
    String email,
    String firstName,
    String lastName,
    String password,
    Set<UUID> roleIds,
    UUID membershipId,
    UUID supervisorId) {}
