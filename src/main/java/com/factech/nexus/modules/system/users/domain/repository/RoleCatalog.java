package com.factech.nexus.modules.system.users.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Acceso de solo lectura al catálogo de roles desde el alta de personas.
 *
 * <p><b>Es un puerto propio y no el repositorio de roles</b>, aunque lea la misma tabla: lo que
 * este caso de uso necesita —clasificación, rol padre y permisos declarados— es una proyección
 * distinta de la que usa el agregado {@code Role}, y compartir el puerto ataría cada cambio de
 * aquel a este. Roles y usuarios son dos agregados del mismo módulo, de modo que `architecture.md`
 * §5.3 —que prohíbe cruzar <b>módulos</b>— no se infringe.
 */
public interface RoleCatalog {

  /** Resuelve identificadores. Devuelve los que existen, sin fallar por los que no. */
  List<AssignableRole> findAllById(Set<UUID> ids);

  /** Un rol concreto, exista o no y sirva o no. */
  Optional<AssignableRole> findById(UUID id);

  /** Roles que porta una persona. Lo necesita `RN-SP-020` para mirar al superior. */
  Set<UUID> roleIdsOf(UUID userId);
}
