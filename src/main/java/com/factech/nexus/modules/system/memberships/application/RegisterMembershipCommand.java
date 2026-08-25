package com.factech.nexus.modules.system.memberships.application;

import java.util.UUID;

/**
 * Entrada del caso de uso de alta de membresía (`RF-SP-016`).
 *
 * <p><b>Sin {@code level} ni {@code parentMembershipId}.</b> El nivel lo calcula el sistema
 * (`CA-SP-115`) y la superior se deduce de la hija indicada. No admitirlos como argumento es lo que
 * hace verificable que la posición no se pueda forzar desde fuera.
 *
 * @param childMembershipId membresía que quedará por debajo de la nueva; {@code null} para el
 *     extremo inferior (`FA-002`)
 */
public record RegisterMembershipCommand(
    String code, String name, String description, UUID childMembershipId) {}
