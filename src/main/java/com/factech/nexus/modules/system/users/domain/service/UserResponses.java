package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Arma la respuesta de una persona leyendo su estructura vigente.
 *
 * <p>Existe para que las tres operaciones que devuelven {@link UserResponse} —el alta, la
 * asignación de roles y el retiro— <b>no describan la misma cosa de tres maneras</b>. La respuesta
 * de un retiro es donde más importa: la cascada de `RN-SP-015` y `RN-SP-019` borra la membresía y
 * cierra el superior, y si cada caso de uso compusiera su respuesta a mano, el que se olvidara de
 * releerlos devolvería una foto de antes de la operación sin que nada fallara.
 *
 * <p>Se lee de la base y no del estado en memoria por lo mismo: lo que se devuelve es lo que quedó.
 */
final class UserResponses {

  private UserResponses() {}

  static UserResponse de(
      User usuario, List<AssignableRole> roles, UserRepository usuarios, UUID userId) {
    return UserResponse.from(
        usuario,
        roles.stream()
            .sorted(Comparator.comparing(AssignableRole::code))
            .map(rol -> new UserResponse.RoleRef(rol.id(), rol.code(), rol.name()))
            .toList(),
        usuarios
            .findMembership(userId)
            .map(
                membresia ->
                    new UserResponse.MembershipRef(
                        membresia.membershipId(),
                        membresia.code(),
                        membresia.name(),
                        membresia.endsAt()))
            .orElse(null),
        usuarios
            .findActiveSupervisor(userId)
            .map(
                superior ->
                    new UserResponse.SupervisorRef(
                        superior.supervisorId(),
                        superior.username(),
                        superior.firstName(),
                        superior.lastName()))
            .orElse(null));
  }
}
