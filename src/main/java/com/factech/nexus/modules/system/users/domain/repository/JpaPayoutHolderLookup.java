package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.application.PayoutHolderLookup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de {@link PayoutHolderLookup}: una lectura por clave, unida al tipo de documento. */
@Repository
public class JpaPayoutHolderLookup implements PayoutHolderLookup {

  private final EntityManager em;

  public JpaPayoutHolderLookup(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<HolderView> holderOf(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT u.id AS id, u.first_name AS nombre, u.last_name AS apellido,
                       u.country_id AS pais, d.abbreviation AS tipo,
                       u.document_number AS numero
                  FROM users u
                  LEFT JOIN document_types d ON d.id = u.document_type_id
                 WHERE u.id = :id AND u.deleted_at IS NULL
                """,
                Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new HolderView(
                    (UUID) f.get("id"),
                    (String) f.get("nombre"),
                    (String) f.get("apellido"),
                    (UUID) f.get("pais"),
                    f.get("tipo") == null ? null : ((String) f.get("tipo")).trim(),
                    (String) f.get("numero")));
  }
}
