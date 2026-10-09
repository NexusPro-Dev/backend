package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.application.StudentCourseProgressResponse.ProgressCourseRef;
import com.factech.nexus.modules.academy.application.StudentCourseProgressResponse.ProgressStudentRef;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.StudentCourseRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** Una fila alumno–curso del listado del progreso (`RF-AC-040`), con su avance (`RN-AC-023`). */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "StudentProgressItem")
public record StudentProgressItem(
    ProgressStudentRef student,
    ProgressCourseRef course,
    int percent,
    long completedLessons,
    long lessonCount,
    long watchedSeconds,
    long totalSeconds,
    @Schema(description = "Todas las lecciones ofrecibles del curso completadas, y al menos una.")
        boolean completed,
    OffsetDateTime firstOpenedAt,
    OffsetDateTime lastActivityAt) {

  public static StudentProgressItem from(StudentCourseRow fila, ProgressStudentRef alumno) {
    var cifras = fila.figures();
    return new StudentProgressItem(
        alumno,
        new ProgressCourseRef(
            fila.courseId(), fila.courseTitle(), fila.courseStatus(), fila.courseDeleted()),
        cifras.percent(),
        cifras.completedLessons(),
        cifras.lessonCount(),
        cifras.watchedSeconds(),
        cifras.totalSeconds(),
        cifras.completed(),
        fila.firstOpenedAt(),
        fila.lastActivityAt());
  }
}
