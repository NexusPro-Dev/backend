package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.CourseProduct;
import java.util.Optional;
import java.util.UUID;

/**
 * Las filas de visibilidad por servicio (`RF-AC-037`, `RF-AC-038`).
 *
 * <p>La forma de {@link CourseCategoryItemRepository}: se leen y se escriben <b>bajo el bloqueo del
 * curso</b>, y la clave primaria es la red de la unicidad (`RN-AC-020`) traducida al mismo {@code
 * 409}.
 */
public interface CourseProductRepository {

  /**
   * Inserta traduciendo {@code pk_course_products} al `409` de `EX-004`, que nombra el servicio.
   */
  CourseProduct save(CourseProduct fila, String codigoDelProducto);

  Optional<CourseProduct> find(UUID courseId, UUID productId);

  void delete(CourseProduct fila);
}
