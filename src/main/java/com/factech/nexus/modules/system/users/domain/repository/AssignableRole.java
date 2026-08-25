package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.roles.domain.models.RoleType;
import java.util.Set;
import java.util.UUID;

/**
 * Un rol del catálogo, con lo que hace falta para decidir si puede asignarse.
 *
 * <p><b>{@code deleted} y {@code active} van por separado</b> desde el 24-08-2026. Antes existía un
 * solo {@code usable} que los fundía, porque `RF-SP-024` §4 los trata igual a propósito: al dar de
 * alta a una persona, distinguir «ese rol no existe» de «ese rol está inactivo» le diría a quien
 * pregunta qué roles hay y en qué estado están.
 *
 * <p>`RF-SP-030` §4 los separa: su tabla de errores asigna `EX-002` al rol inexistente o eliminado
 * y `EX-003` al inactivo, con códigos distintos en la respuesta. El contexto es otro —quien asigna
 * roles a alguien que ya existe porta {@code users:assign-roles} y ya puede consultar el catálogo—,
 * y sin la distinción no puede saber si debe corregir el identificador o activar el rol.
 *
 * <p>{@code usable()} se conserva como derivada para que quien no necesite la distinción no tenga
 * que reconstruirla.
 */
public record AssignableRole(
    UUID id,
    String code,
    String name,
    RoleType type,
    boolean deleted,
    boolean active,
    UUID parentRoleId,
    Set<String> permissionCodes) {

  /** Existe, no está eliminado y está activo: se puede conceder. */
  public boolean usable() {
    return !deleted && active;
  }

  public boolean esVendedor() {
    return type == RoleType.VENDEDOR;
  }

  public boolean esConsumidor() {
    return type == RoleType.CONSUMIDOR;
  }
}
