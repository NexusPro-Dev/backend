package com.factech.nexus.modules.system.roles.domain.models;

/**
 * Estado de un rol (`RN-SEG-002`, {@code ck_roles_status}).
 *
 * <p>Un rol <b>nace siempre {@code ACTIVO}</b> y el alta no lo recibe como dato (`CA-SP-146`). Eso
 * deja un único camino hacia {@code INACTIVO}, `RF-SP-007`, y un solo lugar donde auditarlo.
 */
public enum RoleStatus {
  ACTIVO,
  INACTIVO
}
