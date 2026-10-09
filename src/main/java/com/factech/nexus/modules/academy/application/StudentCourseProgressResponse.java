package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.models.LessonProgress;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CourseRow;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.ProgressRow;
import com.factech.nexus.modules.system.users.application.UserCatalog.UserView;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * El progreso de un alumno en un curso, lección a lección (`RF-AC-041`): el árbol ofrecido hoy con
 * el avance de cada lección, y aparte lo que vio de lo que hoy no se ofrece (`RN-AC-023`). Todo
 * siempre presente; las fechas de lo no abierto, nulas.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "StudentCourseProgressResponse")
public record StudentCourseProgressResponse(
    ProgressStudentRef student,
    ProgressCourseRef course,
    CourseProgressSummary progress,
    List<ProgressModuleItem> modules,
    @Schema(description = "Lecciones con progreso que hoy no se ofrecen; no cuentan en el avance.")
        List<ProgressLessonItem> notOffered) {

  /** El alumno: identidad, como la publica `SP`. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "ProgressStudentRef")
  public record ProgressStudentRef(UUID id, String username, String fullName) {

    public static ProgressStudentRef from(UserView persona) {
      return new ProgressStudentRef(persona.id(), persona.username(), persona.fullName());
    }
  }

  /** El curso, vivo o retirado. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "ProgressCourseRef")
  public record ProgressCourseRef(UUID id, String title, String status, boolean deleted) {

    public static ProgressCourseRef from(CourseRow fila) {
      return new ProgressCourseRef(fila.id(), fila.title(), fila.status(), fila.retirado());
    }
  }

  /** El avance del curso con la primera apertura y la última actividad. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "CourseProgressSummary")
  public record CourseProgressSummary(
      int percent,
      long completedLessons,
      long lessonCount,
      long watchedSeconds,
      long totalSeconds,
      OffsetDateTime firstOpenedAt,
      OffsetDateTime lastActivityAt) {

    public static CourseProgressSummary from(
        ProgressFigures cifras, OffsetDateTime primera, OffsetDateTime ultima) {
      return new CourseProgressSummary(
          cifras.percent(),
          cifras.completedLessons(),
          cifras.lessonCount(),
          cifras.watchedSeconds(),
          cifras.totalSeconds(),
          primera,
          ultima);
    }
  }

  /** Un módulo ofrecible con sus lecciones ofrecibles. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "ProgressModuleItem")
  public record ProgressModuleItem(
      UUID id, String title, int displayOrder, List<ProgressLessonItem> lessons) {}

  /** Una lección con lo que el alumno lleva de ella. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "ProgressLessonItem")
  public record ProgressLessonItem(
      UUID id,
      UUID moduleId,
      String title,
      String type,
      int durationSeconds,
      int displayOrder,
      int watchedSeconds,
      int percent,
      boolean completed,
      OffsetDateTime completedAt,
      OffsetDateTime firstOpenedAt,
      OffsetDateTime lastOpenedAt) {

    /** Con la fila de progreso, o nula si nunca la abrió. */
    public static ProgressLessonItem of(
        UUID id,
        UUID moduleId,
        String title,
        String type,
        int duracion,
        int orden,
        ProgressRow progreso) {
      int vistos = progreso == null ? 0 : progreso.watchedSeconds();
      boolean completada = progreso != null && progreso.completada();
      return new ProgressLessonItem(
          id,
          moduleId,
          title,
          type,
          duracion,
          orden,
          vistos,
          LessonProgress.porcentajeDeLeccion(vistos, duracion, completada),
          completada,
          progreso == null ? null : progreso.completedAt(),
          progreso == null ? null : progreso.firstOpenedAt(),
          progreso == null ? null : progreso.lastOpenedAt());
    }
  }
}
