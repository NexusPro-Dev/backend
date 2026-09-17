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
 * cuenta ni la portada cuestan una consulta por fila. <b>Hasta `RF-AC-016` la subconsulta no
 * existe</b> —no hay tabla que consultar— y la cuenta es un literal cero; ese requerimiento la
 * sustituye y la prueba `CA-AC-010` deja de ser trivial.
 */
public interface CourseCategoryQueryRepository {

  Optional<CourseCategoryRow> findDetail(UUID id);

  /**
   * Los cursos vivos clasificados en la categoría, en su orden global (`RN-AC-002`). <b>Vacío hasta
   * `RF-AC-016`</b>, que crea la tabla de clasificación.
   */
  List<CategoryCourseRow> findAliveCoursesOf(UUID categoryId);

  List<CourseCategoryRow> search(
      ListCourseCategoriesRequest filtros, String ordenamiento, int offset, int limit);

  long count(ListCourseCategoriesRequest filtros);

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
   * Un curso dentro del detalle de su categoría. {@code offerable} es literal falso hasta que el
   * bloque 3 de `ac.md` §6.1 construya la ofrecibilidad (`RN-AC-015`).
   */
  record CategoryCourseRow(
      UUID id, String title, String status, int displayOrder, boolean offerable) {}
}
