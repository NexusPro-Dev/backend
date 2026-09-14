package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.ProductComment;
import java.util.Optional;
import java.util.UUID;

/**
 * La reseña por su clave (`RF-PM-009` a `RF-PM-011`, `RF-PM-013`).
 *
 * <p>Todo lo que devuelve es una reseña <b>viva</b>: la retirada no la devuelve nadie —ni la lista,
 * ni la propia, ni las dos escrituras—, de modo que para el sistema y para el autor «retirada» y
 * «no existe» son lo mismo (`RF-PM-011` `EX-001`).
 */
public interface ProductCommentRepository {

  /**
   * El alta. Traduce {@code uq_product_comments_autor} —la carrera entre dos altas del mismo actor
   * sobre el mismo producto— al mismo {@code 409} que la comprobación previa (`RN-PM-026`).
   */
  ProductComment save(ProductComment resena);

  /** `RN-PM-026`, el camino normal: ¿tiene ya el actor una reseña viva sobre ese producto? */
  boolean existsLiveByProductAndUser(UUID productId, UUID userId);

  /** La propia (`RF-PM-013`): a lo sumo una, por el índice único parcial. Sin bloqueo. */
  Optional<ProductComment> findLiveByProductAndUser(UUID productId, UUID userId);

  /**
   * Para corregir y retirar (`RF-PM-010`, `RF-PM-011`): la reseña viva, <b>de ese producto</b>, con
   * bloqueo de escritura. Una que exista bajo otro producto no se encuentra: la ruta dice de qué
   * producto es, y una petición que se contradice no se «arregla».
   */
  Optional<ProductComment> findLiveByIdAndProductForUpdate(UUID commentId, UUID productId);

  void flush();
}
