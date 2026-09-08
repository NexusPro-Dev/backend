package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.AssignableCountry;
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
 *
 * <p><b>El país entra por aquí y no por cada caso de uso</b> (`RN-SP-034`), y es lo que garantiza
 * que {@code country} esté en las cinco respuestas y no solo en las que alguien se acordara de
 * llenar. Es además el único campo de esta respuesta que <b>nunca es nulo</b>: {@code roles} puede
 * venir vacía, y la membresía y el superior pueden faltar, pero la columna es {@code NOT NULL} — de
 * modo que un nulo aquí solo podría venir de un país borrado, que `RN-SP-009` no permite.
 */
final class UserResponses {

  private UserResponses() {}

  static UserResponse de(
      User usuario,
      List<AssignableRole> roles,
      UserRepository usuarios,
      AssignableCountry paises,
      UUID userId) {
    return UserResponse.from(
        usuario,
        roles.stream()
            .sorted(Comparator.comparing(AssignableRole::code))
            .map(rol -> new UserResponse.RoleRef(rol.id(), rol.code(), rol.name()))
            .toList(),
        paises
            .find(usuario.getCountryId())
            .map(pais -> new UserResponse.CountryRef(pais.id(), pais.code(), pais.name()))
            .orElse(null),
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
