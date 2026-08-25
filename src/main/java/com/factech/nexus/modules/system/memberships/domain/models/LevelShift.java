package com.factech.nexus.modules.system.memberships.domain.models;

import java.util.UUID;

/**
 * Una membresía que el reordenamiento desplaza de nivel.
 *
 * <p>Lleva el nivel <b>antes</b> y <b>después</b> porque `CA-SP-118` exige una fila de auditoría
 * por cada membresía tocada, y `architecture.md` §6.6.2 exige que un {@code UPDATE} registre el
 * diff de lo que cambió. Sin el valor anterior, ese diff no se puede construir después: la fila ya
 * se escribió.
 */
public record LevelShift(UUID id, int antes, int despues) {}
