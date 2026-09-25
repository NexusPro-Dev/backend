package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.CourseCategoryItem;
import java.util.Optional;
import java.util.UUID;

/**
 * Las filas de clasificación curso-categoría (`RF-AC-016`, `RF-AC-017`).
 *
 * <p>La forma de {@code PackageItemRepository}: la fila se lee y se escribe <b>bajo el bloqueo del
 * curso</b>, que toma el caso de uso con {@link CourseRepository#findAliveByIdForUpdate}, y por eso
 * aquí ninguna lectura bloquea. La clave primaria es la red de la unicidad (`RN-AC-010`) si ese
 * bloqueo faltara, y se traduce al mismo {@code 409}.
 */
public interface CourseCategoryItemRepository {

  /**
   * Inserta traduciendo {@code pk_course_category_items} al `409` de `EX-003`, que nombra la
   * categoría: por eso viaja el nombre.
   */
  CourseCategoryItem save(CourseCategoryItem fila, String nombreDeLaCategoria);

  Optional<CourseCategoryItem> find(UUID courseId, UUID categoryId);

  void delete(CourseCategoryItem fila);
}
