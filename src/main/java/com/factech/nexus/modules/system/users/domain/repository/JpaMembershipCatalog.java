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

  /**
   * El código de la membresía de arranque (`RN-SP-018`). <b>Es un literal a propósito</b>: `V46` la
   * siembra con ese código en todos los entornos, `uq_memberships_code` lo hace único y `RN-SP-008`
   * impide borrar la fila.
   */
  private static final String CODIGO_SUELO = "FREE";

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

  /**
   * El suelo, por <b>código</b>. El literal vive aquí, junto al SQL que lo busca, y no repartido
   * por los tres servicios que lo necesitan.
   */
  @Override
  public MembershipRef floor() {
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT id AS id, code AS code, name AS name, level AS level"
                    + " FROM memberships WHERE code = :codigo",
                Tuple.class)
            .setParameter("codigo", CODIGO_SUELO)
            .getResultList();

    return filas.stream()
        .map(
            fila ->
                new MembershipRef(
                    (UUID) fila.get("id"),
                    (String) fila.get("code"),
                    (String) fila.get("name"),
                    ((Number) fila.get("level")).shortValue()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "El catálogo no tiene la membresía de arranque '"
                        + CODIGO_SUELO
                        + "' (RN-SP-018). La siembra V46 debería haberla creado, y V57 aborta si"
                        + " falta: una base que llegue aquí sin ella está mal construida."));
  }
}
