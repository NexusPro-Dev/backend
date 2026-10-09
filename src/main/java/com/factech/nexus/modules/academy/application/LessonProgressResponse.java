package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.models.LessonProgress;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.ProgressRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * El avance de una lección para quien la estudia (`RF-AC-039`): lo guardado —que puede ser más que
 * lo recién reportado—, la duración de hoy y el porcentaje calculado con {@link LessonProgress}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "LessonProgressResponse")
public record LessonProgressResponse(
    UUID lessonId,
    int watchedSeconds,
    int durationSeconds,
    @Schema(description = "Entero, hacia abajo, tope 100; 100 si está completada.") int percent,
    boolean completed,
    OffsetDateTime completedAt,
    OffsetDateTime firstOpenedAt,
    OffsetDateTime lastOpenedAt) {

  public static LessonProgressResponse from(ProgressRow fila, int duracion) {
    return new LessonProgressResponse(
        fila.lessonId(),
        fila.watchedSeconds(),
        duracion,
        LessonProgress.porcentajeDeLeccion(fila.watchedSeconds(), duracion, fila.completada()),
        fila.completada(),
        fila.completedAt(),
        fila.firstOpenedAt(),
        fila.lastOpenedAt());
  }
}
