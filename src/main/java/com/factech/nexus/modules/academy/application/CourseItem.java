package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.application.CourseDetailResponse.CourseCategoryRef;
import com.factech.nexus.modules.academy.application.CourseDetailResponse.InstructorRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CategoryRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CourseRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Una fila del listado de cursos (`RF-AC-009`): lo suyo, el instructor resuelto, la portada, sus
 * categorías vivas, si se ofrece y cuántos módulos y lecciones <b>vivos</b> tiene. Sin descripción
 * larga, video ni árbol: eso es el detalle.
 *
 * <p>{@code coverImageUrl} y {@code categories} <b>siempre presentes</b>; {@code deletedAt} solo en
 * un retirado. {@code offerable} lo decide {@code CourseOfferability} por fila con las cuentas que
 * vinieron en la sentencia, de modo que cuadra con el detalle (`CA-AC-044`).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CourseItem(
    UUID id,
    String title,
    InstructorRef instructor,
    String difficulty,
    String shortDescription,
    int displayOrder,
    String status,
    String coverImageUrl,
    List<CourseCategoryRef> categories,
    boolean offerable,
    long moduleCount,
    long lessonCount,
    OffsetDateTime createdAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime deletedAt) {

  public static CourseItem from(CourseRow fila, List<CategoryRef> categorias) {
    return new CourseItem(
        fila.id(),
        fila.title(),
        InstructorRef.from(fila),
        fila.difficulty(),
        fila.shortDescription(),
        fila.displayOrder(),
        fila.status(),
        AcademyImageUrls.de(fila.coverImageId()),
        categorias.stream().map(CourseCategoryRef::from).toList(),
        fila.ofrecibilidad().offerable(),
        fila.moduleCount(),
        fila.lessonCount(),
        fila.createdAt(),
        fila.deletedAt());
  }
}
