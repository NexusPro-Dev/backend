package com.factech.nexus.modules.system.roles.domain.repository;

import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.models.RoleCode;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia del agregado {@link Role} (`RF-SP-001` · `T-11`).
 *
 * <p>Las dos comprobaciones de existencia no son lo que garantiza `RN-SEG-001`: eso lo hacen los
 * índices únicos parciales del esquema. Existen <b>para poder dar un mensaje preciso</b> —cuál de
 * los dos está duplicado, el código o el nombre— antes de intentar la inserción. La restricción
 * decide; el {@code SELECT} solo redacta.
 *
 * <p>Verificar la unicidad <b>solo</b> con estas consultas no serviría: dos altas simultáneas con
 * el mismo código pasan ambas la comprobación y la segunda moriría con un error de integridad
 * convertido en {@code 500}, que es justo el caso límite que `spec.md` §13 prohíbe.
 */
public interface RoleRepository {

  /**
   * Persiste un rol nuevo.
   *
   * @throws com.factech.nexus.shared.error.BusinessRuleException si el alta viola {@code
   *     uq_roles_code} o {@code uq_roles_name}, distinguiendo cuál de los dos
   */
  Role save(Role rol);

  /**
   * Busca por identificador, incluidos los eliminados lógicamente: quien llama decide qué hacer.
   */
  Optional<Role> findById(UUID id);

  /** ¿Hay ya un rol no eliminado con ese código? (`RN-SEG-001`) */
  boolean existsActiveCode(RoleCode code);

  /** ¿Hay ya un rol no eliminado con ese nombre? (`RN-SEG-001`) */
  boolean existsActiveName(String name);
}
