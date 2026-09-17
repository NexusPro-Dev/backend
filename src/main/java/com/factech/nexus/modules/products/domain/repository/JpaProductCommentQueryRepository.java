package com.factech.nexus.modules.products.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación nativa de {@link ProductCommentQueryRepository}. Ver el Javadoc de la interfaz.
 */
@Repository
public class JpaProductCommentQueryRepository implements ProductCommentQueryRepository {

  private final EntityManager em;

  public JpaProductCommentQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CommentRow> findPageByProduct(UUID productId, int offset, int limit) {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT c.id AS id, c.rating AS rating, c.comment AS comment,
                       c.created_at AS created_at, c.updated_at AS updated_at,
                       u.first_name AS first_name, u.last_name AS last_name
                  FROM product_comments c
                  JOIN users u ON u.id = c.user_id
                 WHERE c.product_id = :producto AND c.deleted_at IS NULL
                 ORDER BY c.created_at DESC, c.id DESC
                 OFFSET :salto LIMIT :tope
                """,
                Tuple.class)
            .setParameter("producto", productId)
            .setParameter("salto", offset)
            .setParameter("tope", limit)
            .getResultList();

    List<CommentRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new CommentRow(
              (UUID) fila.get("id"),
              ((Number) fila.get("rating")).intValue(),
              (String) fila.get("comment"),
              (String) fila.get("first_name"),
              (String) fila.get("last_name"),
              momento(fila.get("created_at")),
              momento(fila.get("updated_at"))));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public long countLiveByProduct(UUID productId) {
    Object total =
        em.createNativeQuery(
                "SELECT count(*) FROM product_comments"
                    + " WHERE product_id = :producto AND deleted_at IS NULL")
            .setParameter("producto", productId)
            .getSingleResult();
    return ((Number) total).longValue();
  }

  private static OffsetDateTime momento(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime instante -> instante;
      case Instant instante -> instante.atOffset(ZoneOffset.UTC);
      case Timestamp marca -> marca.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }
}
