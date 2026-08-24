package com.factech.nexus.modules.system.users.application;

import java.util.Set;
import java.util.UUID;

/**
 * Entrada del caso de uso de alta de persona (`RF-SP-024`).
 *
 * <p><b>Sin estado ni marca de cambio obligatorio</b>: la cuenta nace `ACTIVO` y marcada, y no
 * admitirlos como argumento es lo que deja un solo camino hacia cada valor.
 *
 * @param membershipId condicional en los dos sentidos: exigido si hay rol consumidor, prohibido si
 *     no lo hay (`RN-SP-018`)
 * @param supervisorId ídem con el rol vendedor (`RN-SP-019`)
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
