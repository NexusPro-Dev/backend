package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.ProductComment;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Implementación JPA de {@link ProductCommentRepository}.
 *
 * <h2>La traducción de {@code uq_product_comments_autor}</h2>
 *
 * <p>Es el mismo mecanismo que {@link JpaProductRepository} usa con sus tres restricciones: se
 * busca la {@code ConstraintViolationException} en la cadena de causas, se compara el nombre, y se
 * devuelve la {@code BusinessRuleException} de {@code EX-002} <b>con el mismo mensaje</b> que la
 * comprobación previa del servicio. Es el camino de la carrera —dos altas simultáneas del mismo
 * actor—; el mensaje accionable lo da la comprobación, que es el camino normal. Lo que este {@code
 * catch} garantiza es que la carrera sea un {@code 409} y no un {@code 500} (`CA-PM-179`).
 */
@Repository
public class JpaProductCommentRepository implements ProductCommentRepository {

  static final String UQ_AUTOR = "uq_product_comments_autor";

  /** El mismo texto que el servicio: la carrera y el camino normal responden igual. */
  public static final String MENSAJE_YA_RESENADO =
      "Ya reseñaste este producto. Puedes corregir tu reseña.";

  private final EntityManager em;

  public JpaProductCommentRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public ProductComment save(ProductComment resena) {
    try {
      em.persist(resena);
      em.flush();
      return resena;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public boolean existsLiveByProductAndUser(UUID productId, UUID userId) {
    return !em.createQuery(
            """
            SELECT 1 FROM ProductComment c
             WHERE c.productId = :producto AND c.userId = :autor AND c.deletedAt IS NULL
            """,
            Integer.class)
        .setParameter("producto", productId)
        .setParameter("autor", userId)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  @Override
  public Optional<ProductComment> findLiveByProductAndUser(UUID productId, UUID userId) {
    if (productId == null || userId == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            """
            SELECT c FROM ProductComment c
             WHERE c.productId = :producto AND c.userId = :autor AND c.deletedAt IS NULL
            """,
            ProductComment.class)
        .setParameter("producto", productId)
        .setParameter("autor", userId)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<ProductComment> findLiveByIdAndProductForUpdate(UUID commentId, UUID productId) {
    if (commentId == null || productId == null) {
      return Optional.empty();
    }
    // NO lleva `AND c.userId = :actor`, y es a propósito: filtrar por actor
    // convertiría la reseña ajena en «no existe» (`404`) cuando la regla dice
    // «no es tuya» (`403`). La propiedad la comprueba el servicio DESPUÉS de
    // encontrarla (`RF-PM-010` §5).
    return em
        .createQuery(
            """
            SELECT c FROM ProductComment c
             WHERE c.id = :id AND c.productId = :producto AND c.deletedAt IS NULL
            """,
            ProductComment.class)
        .setParameter("id", commentId)
        .setParameter("producto", productId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public void flush() {
    em.flush();
  }

  private static RuntimeException traducir(PersistenceException fallo) {
    if (UQ_AUTOR.equals(nombreDeRestriccion(fallo))) {
      return new BusinessRuleException(
          "EX-002",
          MENSAJE_YA_RESENADO,
          List.of(new FieldError("productId", "EX-002", MENSAJE_YA_RESENADO)));
    }
    return fallo;
  }

  private static String nombreDeRestriccion(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion) {
        return violacion.getConstraintName();
      }
    }
    return null;
  }
}
