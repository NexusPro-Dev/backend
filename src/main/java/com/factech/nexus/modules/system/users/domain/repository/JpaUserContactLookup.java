package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.application.UserContactLookup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link UserContactLookup} sobre una sentencia: la persona viva con su correo y su nombre. */
@Repository
public class JpaUserContactLookup implements UserContactLookup {

  private final EntityManager em;

  public JpaUserContactLookup(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserContact> contactOf(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT id, email, first_name, last_name FROM users"
                    + " WHERE id = :id AND deleted_at IS NULL",
                Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            fila ->
                new UserContact(
                    (UUID) fila.get("id"),
                    (String) fila.get("email"),
                    (String) fila.get("first_name"),
                    (String) fila.get("last_name")));
  }
}
