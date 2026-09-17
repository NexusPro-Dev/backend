package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.domain.repository.UserQueryRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Los roles de las personas de la estructura comercial, listos para publicar (10-09-2026).
 *
 * <p><b>Existe para que los dos casos de uso que comparten `CommercialStructureResponse` los
 * traduzcan igual</b>: `RF-SP-042` al consultar el equipo y `RF-SP-041` al reasignar. Escrito dos
 * veces, un día uno ordenaría por código y el otro no, y la misma persona saldría con los roles en
 * distinto orden según el endpoint que la devolviera.
 *
 * <p>El orden lo fija la consulta —por código—, y se conserva tal cual: {@code rolesOf} ya ordena
 * por {@code user_id, code}.
 */
final class PersonRoles {

  private PersonRoles() {}

  /** Traduce lo que devuelve el puerto de lectura al {@code RoleRef} que publica el contrato. */
  static Map<UUID, List<UserResponse.RoleRef>> de(
      Map<UUID, List<UserQueryRepository.RoleRow>> filas) {

    return filas.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entrada ->
                    entrada.getValue().stream()
                        .map(rol -> new UserResponse.RoleRef(rol.id(), rol.code(), rol.name()))
                        .toList()));
  }
}
