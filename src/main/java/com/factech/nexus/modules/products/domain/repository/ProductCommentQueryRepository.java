package com.factech.nexus.modules.products.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * La lista pública de reseñas de un producto (`RF-PM-012`).
 *
 * <h2>El nombre del autor entra por un {@code JOIN} a {@code users}, y ese es el precedente de
 * `RF-PM-002` con {@code memberships}</h2>
 *
 * <p>La regla de ArchUnit prohíbe importar repositorios y entidades de {@code SP}, no nombrar sus
 * tablas en una sentencia. <b>Ninguna regla se decide con ese {@code JOIN}</b>: quién es el autor
 * lo dice {@code product_comments.user_id}; el {@code JOIN} solo le pone nombre. Y se seleccionan
 * <b>exactamente</b> {@code first_name} y {@code last_name}: lo que no se selecciona no puede
 * filtrarse a la respuesta (`RN-PM-030`), que es la misma disciplina con la que las consultas
 * públicas no seleccionan {@code purchase_price}.
 */
public interface ProductCommentQueryRepository {

  /**
   * Una página de reseñas vivas, de la más reciente a la más antigua con el identificador como
   * desempate, con el nombre y apellido del autor <b>en la misma sentencia</b>.
   */
  List<CommentRow> findPageByProduct(UUID productId, int offset, int limit);

  long countLiveByProduct(UUID productId);

  /** Lo que la lista pública publica de una reseña, y nada más. */
  record CommentRow(
      UUID id,
      int rating,
      String comment,
      String authorFirstName,
      String authorLastName,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
