package com.factech.nexus.modules.system.teams.application;

import java.util.UUID;

/**
 * La oficina madre de un equipo (`RN-SP-051`, 15-10-2026): el equipo del primero que lo tiene
 * subiendo por los superiores vigentes de su encargado. Nula, la oficina es <b>principal</b>; con
 * valor, es una <b>suboficina</b> de esta.
 */
public record ParentTeam(UUID id, String name) {

  static ParentTeam de(UUID id, String name) {
    return id == null ? null : new ParentTeam(id, name);
  }
}
