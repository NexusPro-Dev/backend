package com.factech.nexus.modules.system.users.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Consulta directa sobre `memberships`, igual que {@code JpaRoleCatalog} la hace sobre `roles`.
 *
 * <p>No pasa por el agregado de membresías a propósito: cargarlo traería su cadena y sus vecinas
 * para responder por un solo eslabón.
 */
@Repository
public class JpaMembershipCatalog implements MembershipCatalog {

  private final EntityManager em;

  public JpaMembershipCatalog(EntityManager em) {
    this.em = em;
  }

  @Override
  public Optional<MembershipRef> find(UUID membershipId) {
    if (membershipId == null) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT id AS id, code AS code, name AS name, level AS level"
                    + " FROM memberships WHERE id = :id",
                Tuple.class)
            .setParameter("id", membershipId)
            .getResultList();

    return filas.stream()
        .map(
            fila ->
                new MembershipRef(
                    (UUID) fila.get("id"),
                    (String) fila.get("code"),
                    (String) fila.get("name"),
                    ((Number) fila.get("level")).shortValue()))
        .findFirst();
  }
}
