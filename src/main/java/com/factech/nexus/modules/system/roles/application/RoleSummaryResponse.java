package com.factech.nexus.modules.system.roles.application;

import com.factech.nexus.modules.system.roles.domain.models.Role;
import java.util.UUID;

/**
 * Un rol referenciado desde otro (`RF-SP-001` · `T-16`).
 *
 * <p>Tres campos y no el rol entero: quien pide un rol quiere saber <b>cuál</b> es su padre, no el
 * estado completo del padre. Devolverlo entero anidaría a su vez el abuelo y la respuesta crecería
 * con la profundidad de la jerarquía; el detalle del padre se consulta con `RF-SP-003`.
 */
public record RoleSummaryResponse(UUID id, String code, String name) {

  public static RoleSummaryResponse from(Role rol) {
    return new RoleSummaryResponse(rol.getId(), rol.getCode().value(), rol.getName());
  }
}
