package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.ProgressRow;
import com.factech.nexus.modules.academy.domain.repository.LessonQueryRepository.LessonDetailRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * La lección que se estudia (`RF-AC-035`), <b>con su contenido tal como se guardó</b> —la URL de un
 * {@code VIDEO}, el Markdown de un {@code TEXTO}—. Sin estado ni {@code accessible}: si se
 * devolvió, se ofrece y se abre. Desde el 09-10-2026 trae el avance de quien mira (`RN-AC-022`),
 * para que el reproductor retome donde se quedó.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "ClassroomLessonResponse")
public record ClassroomLessonResponse(
    UUID id,
    UUID courseId,
    UUID moduleId,
    String type,
    String title,
    String description,
    String content,
    int durationSeconds,
    int displayOrder,
    boolean open,
    @Schema(description = "La mayor posición reportada por quien mira; 0 en un TEXTO.")
        int watchedSeconds,
    @Schema(description = "Cuándo la completó quien mira; nulo si no la ha completado.")
        OffsetDateTime completedAt) {

  public static ClassroomLessonResponse from(LessonDetailRow fila, ProgressRow progreso) {
    return new ClassroomLessonResponse(
        fila.id(),
        fila.courseId(),
        fila.moduleId(),
        fila.type(),
        fila.title(),
        fila.description(),
        fila.content(),
        fila.durationSeconds(),
        fila.displayOrder(),
        fila.open(),
        progreso.watchedSeconds(),
        progreso.completedAt());
  }
}
