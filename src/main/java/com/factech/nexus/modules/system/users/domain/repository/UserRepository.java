package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.domain.models.Email;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.models.Username;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de escritura de personas (`RF-SP-024`).
 *
 * <p>Las dos comprobaciones de existencia no son lo que garantiza `RN-SP-016` —eso lo hacen los
 * índices únicos <b>totales</b>—: existen para poder decir <b>cuál</b> de las dos identidades está
 * en uso antes de intentar la inserción. La restricción decide; el {@code SELECT} solo redacta.
 *
 * <p><b>La membresía y el superior se escriben desde aquí y no desde sus propios agregados</b>,
 * porque el alta las escribe <b>en su misma transacción</b>: `CA-SP-373` y `CA-SP-397` exigen que
 * no exista un instante en que el consumidor esté sin membresía ni el vendedor sin superior.
 */
public interface UserRepository {

  /**
   * Persiste la persona y sus roles.
   *
   * @throws com.factech.nexus.shared.error.BusinessRuleException si viola {@code uq_users_username}
   *     o {@code uq_users_email}, distinguiendo cuál
   */
  User save(User usuario);

  /** ¿Hay ya alguien con ese nombre de usuario? La comparación ignora la caja. */
  boolean existsUsername(Username username);

  /** ¿Hay ya alguien con ese correo? El correo se compara ya normalizado. */
  boolean existsEmail(Email email);

  /** Persona que <b>sirve como superior</b>: existe, no está eliminada y está `ACTIVO`. */
  Optional<User> findUsableById(UUID id);

  /** Concede la membresía. Fila única por persona: `RN-SP-014` lo garantiza en el esquema. */
  void assignMembership(UUID userId, UUID membershipId, OffsetDateTime ahora);

  /** Abre la asignación de superior comercial, sin fecha de fin. */
  void assignSupervisor(UUID id, UUID userId, UUID supervisorId, OffsetDateTime ahora);
}
