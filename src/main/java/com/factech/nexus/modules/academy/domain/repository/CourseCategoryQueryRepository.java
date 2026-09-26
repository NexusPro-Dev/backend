package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.application.ListCourseCategoriesRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las lecturas de la categoría (`RF-AC-002`, `RF-AC-003`), sobre proyecciones y no sobre la
 * entidad: el listado y el detalle no cargan nada que después haya que descartar.
 *
 * <p><b>{@code courseCount} viaja en la misma sentencia que la fila</b>, como subconsulta escalar
 * sobre {@code course_category_items} unida a {@code courses} por {@code deleted_at IS NULL}. Ni la
 * cuenta ni la portada cuestan una consulta por fila. Real desde `RF-AC-016`, que creó la tabla;
 * hasta entonces era un literal cero.
 */
public interface CourseCategoryQueryRepository {

  Optional<CourseCategoryRow> findDetail(UUID id);

  /**
   * Los cursos vivos clasificados en la categoría, en su orden global (`RN-AC-002`), en una
   * sentencia (`RF-AC-016`, `CA-AC-127`).
   */
  List<CategoryCourseRow> findAliveCoursesOf(UUID categoryId);

  List<CourseCategoryRow> search(
      ListCourseCategoriesRequest filtros, String ordenamiento, int offset, int limit);

  long count(ListCourseCategoriesRequest filtros);

  /**
   * Todas las categorías vivas en su orden (`RN-AC-002`), sin paginar: los cajones del catálogo del
   * alumno (`RF-AC-033`), incluidas las vacías.
   */
  List<CourseCategoryRow> findAlive();

  /** Una categoría como sale de la tabla, con su cuenta de cursos vivos ya hecha. */
  record CourseCategoryRow(
      UUID id,
      String name,
      String description,
      String color,
      String icon,
      int displayOrder,
      UUID coverImageId,
      long courseCount,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt) {

    public boolean retirada() {
      return deletedAt != null;
    }
  }

  /**
   * Un curso dentro del detalle de su categoría, con {@code offerable} decidido por {@code
   * CourseOfferability} sobre las mismas cuentas que el listado de cursos (`RN-AC-015`).
   */
  record CategoryCourseRow(
      UUID id, String title, String status, int displayOrder, boolean offerable) {}
}
