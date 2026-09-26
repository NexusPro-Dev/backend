package com.factech.nexus.modules.academy.application;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de la visibilidad por membresía (`RF-AC-020` §11): la membresía, y solo ella. <b>Una
 * pareja por petición</b>; «este nivel y los superiores» se añaden uno a uno (`ac.md` §5.2.2).
 */
public record GrantCourseMembershipRequest(
    @NotNull(message = "VAL-002: La membresía es obligatoria.") UUID membershipId) {}
