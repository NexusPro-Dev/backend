package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.roles.domain.models.RoleType;
import java.util.Set;
import java.util.UUID;

/**
 * Un rol visto desde el alta de una persona: lo justo para decidir si puede concederse.
 *
 * <p>No es la entidad {@code Role}: el alta necesita saber si el rol <b>sirve</b>, de qué
 * clasificación es, de quién cuelga y qué permisos declara — y nada más. Traer el agregado entero
 * acoplaría este caso de uso a los cambios del otro.
 *
 * @param usable activo y no eliminado; los dos casos comparten respuesta porque distinguirlos le
 *     diría a quien pregunta qué roles existen y en qué estado están
 * @param permissionCodes lo que el rol concede, para verificar `RN-SEG-010` contra el actor
 */
public record AssignableRole(
    UUID id,
    String code,
    String name,
    RoleType type,
    boolean usable,
    UUID parentRoleId,
    Set<String> permissionCodes) {

  public boolean esVendedor() {
    return type == RoleType.VENDEDOR;
  }

  public boolean esConsumidor() {
    return type == RoleType.CONSUMIDOR;
  }
}
