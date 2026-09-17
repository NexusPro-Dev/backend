package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository.CategoryCourseRow;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository.CourseCategoryRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * La categoría como la ve administración (`RF-AC-001`, `RF-AC-003`, `RF-AC-004`): lo suyo, la
 * portada y <b>sus cursos vivos en orden</b>, cada uno con su estado y si se ofrece.
 *
 * <p>Es la respuesta del alta, del detalle y de la corrección, para que el frontend tenga una sola
 * pantalla de categoría. <b>{@code coverImageUrl}, {@code description} y {@code courses} siempre
 * presentes</b> —nulos o vacíos cuando no hay—; {@code deletedAt} y {@code deletionReason} solo en
 * una retirada.
 *
 * <p><b>Los cursos viajan vacíos hasta `RF-AC-016`</b>, y {@code offerable} es falso hasta que el
 * bloque 3 construya la ofrecibilidad (`RN-AC-015`): las tres enmiendas están declaradas en la spec
 * de `RF-AC-003` §15.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CourseCategoryDetailResponse(
    UUID id,
    String name,
    String description,
    String color,
    String icon,
    int displayOrder,
    String coverImageUrl,
    long courseCount,
    List<CategoryCourseItem> courses,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime deletedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) String deletionReason) {

  /** Un curso dentro de su categoría: lo justo para reordenar y saber si el cajón está lleno. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CategoryCourseItem(
      UUID id, String title, String status, int displayOrder, boolean offerable) {

    static CategoryCourseItem from(CategoryCourseRow fila) {
      return new CategoryCourseItem(
          fila.id(), fila.title(), fila.status(), fila.displayOrder(), fila.offerable());
    }
  }

  public static CourseCategoryDetailResponse from(
      CourseCategoryRow fila, List<CategoryCourseRow> cursos, String motivoDeRetiro) {
    return new CourseCategoryDetailResponse(
        fila.id(),
        fila.name(),
        fila.description(),
        fila.color(),
        fila.icon(),
        fila.displayOrder(),
        AcademyImageUrls.de(fila.coverImageId()),
        fila.courseCount(),
        cursos.stream().map(CategoryCourseItem::from).toList(),
        fila.createdAt(),
        fila.updatedAt(),
        fila.deletedAt(),
        motivoDeRetiro);
  }
}
