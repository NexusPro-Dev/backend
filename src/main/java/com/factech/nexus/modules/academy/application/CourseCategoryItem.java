package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository.CourseCategoryRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila del listado de categorías (`RF-AC-002`): lo suyo, la portada y <b>cuántos cursos vivos
 * tiene</b> — vivos y no ofrecidos (`spec.md` §14.2). Sin descripción ni cursos: eso es el detalle.
 *
 * <p>{@code coverImageUrl} <b>siempre presente</b>, nulo cuando no hay; {@code deletedAt} solo en
 * una retirada.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CourseCategoryItem(
    UUID id,
    String name,
    String color,
    String icon,
    int displayOrder,
    String coverImageUrl,
    long courseCount,
    OffsetDateTime createdAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime deletedAt) {

  public static CourseCategoryItem from(CourseCategoryRow fila) {
    return new CourseCategoryItem(
        fila.id(),
        fila.name(),
        fila.color(),
        fila.icon(),
        fila.displayOrder(),
        AcademyImageUrls.de(fila.coverImageId()),
        fila.courseCount(),
        fila.createdAt(),
        fila.deletedAt());
  }
}
