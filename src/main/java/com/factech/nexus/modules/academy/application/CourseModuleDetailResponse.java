package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.application.CourseDetailResponse.LessonSummary;
import com.factech.nexus.modules.academy.domain.models.ModuleOfferability;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.LessonRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ModuleRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * El módulo como lo devuelven sus escrituras (`RF-AC-022` a `RF-AC-027`): lo suyo, sus lecciones en
 * orden —vivas y retiradas marcadas, sin contenido— y si se ofrece con su motivo. <b>Las escrituras
 * sobre un módulo devuelven el módulo, no el curso</b> (`RF-AC-022` §14.1): el curso entero es lo
 * que el frontend ya tiene abierto, y lo que cambió es una fila de su árbol.
 *
 * <p>Es la misma forma que el detalle del curso enseña por módulo, más las descripciones y el
 * video.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "CourseModuleDetailResponse")
public record CourseModuleDetailResponse(
    UUID id,
    UUID courseId,
    String title,
    String shortDescription,
    String longDescription,
    String presentationVideoUrl,
    int displayOrder,
    String status,
    String coverImageUrl,
    long durationMinutes,
    List<LessonSummary> lessons,
    boolean offerable,
    String offerableReason,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static CourseModuleDetailResponse from(ModuleRow fila, List<LessonRow> lecciones) {
    ModuleOfferability.Resultado ofrecibilidad = fila.ofrecibilidad();
    return new CourseModuleDetailResponse(
        fila.id(),
        fila.courseId(),
        fila.title(),
        fila.shortDescription(),
        fila.longDescription(),
        fila.presentationVideoUrl(),
        fila.displayOrder(),
        fila.status(),
        AcademyImageUrls.de(fila.coverImageId()),
        fila.durationMinutes(),
        lecciones.stream().map(LessonSummary::from).toList(),
        ofrecibilidad.offerable(),
        ofrecibilidad.reason(),
        fila.createdAt(),
        fila.updatedAt());
  }
}
