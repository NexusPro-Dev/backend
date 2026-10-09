package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.CourseFigures;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * El avance de una persona en un curso (`RN-AC-023`), sobre las lecciones ofrecibles hoy: la misma
 * forma en el aula (`RF-AC-033`, `RF-AC-034`) y en las consultas del progreso (`RF-AC-040`,
 * `RF-AC-041`).
 */
@Schema(name = "ProgressFigures")
public record ProgressFigures(
    @Schema(
            description =
                "Lo visto —acotado a cada duración, entera si la lección está completada— entre la"
                    + " duración total, entero hacia abajo.")
        int percent,
    long completedLessons,
    long lessonCount,
    long watchedSeconds,
    long totalSeconds) {

  public static final ProgressFigures CERO = from(CourseFigures.CERO);

  public static ProgressFigures from(CourseFigures cifras) {
    return new ProgressFigures(
        cifras.percent(),
        cifras.completedLessons(),
        cifras.lessonCount(),
        cifras.watchedSeconds(),
        cifras.totalSeconds());
  }
}
