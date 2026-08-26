package com.factech.nexus.modules.system.memberships.application;

import java.util.UUID;

/**
 * Modelo de lectura del detalle de una membresía con sus dos vecinos (`RF-SP-018`).
 *
 * @param parent nulo en la superior de la cadena
 * @param child nulo en la inferior. En la única membresía del sistema ambos son nulos a la vez, y
 *     es válido (`spec.md` §13)
 */
public record MembershipDetailItem(
    UUID id,
    String code,
    String name,
    String description,
    String color,
    int level,
    MembershipNeighborItem parent,
    MembershipNeighborItem child) {}
