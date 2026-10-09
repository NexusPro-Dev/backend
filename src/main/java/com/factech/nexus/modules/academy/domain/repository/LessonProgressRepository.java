package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.LessonProgress;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * El progreso del alumno (`lesson_progress`, `RN-AC-021` a `RN-AC-024`): las dos escrituras del
 * aula y las lecturas del aula, del detalle y del listado.
 *
 * <p><b>Las escrituras no leen antes</b>: son {@code INSERT … ON CONFLICT DO UPDATE … RETURNING}, y
 * el máximo y la primera fecha los conserva el motor.
 */
public interface LessonProgressRepository {

  /**
   * Anota que esa persona abrió esa lección (`RN-AC-022`): la crea con cero segundos o mueve la
   * última apertura. Si {@code completa}, la completa ahora salvo que ya lo estuviera.
   */
  ProgressRow open(UUID userId, UUID lessonId, boolean completa);

  /**
   * Guarda el máximo entre lo que había y {@code segundos} —ya acotados— y completa si el máximo
   * llega a {@code umbral} (`RN-AC-021`). Abre la lección si no lo estaba.
   */
  ProgressRow report(UUID userId, UUID lessonId, int segundos, int umbral);

  /** Las filas de esa persona en esas lecciones, por lección. Las que no tiene, no están. */
  Map<UUID, ProgressRow> findOfUserInLessons(UUID userId, Collection<UUID> lessonIds);

  /**
   * Las filas de esa persona en las lecciones de ese curso —vivas o retiradas—, con lo que hace
   * falta para enseñar las que hoy no se ofrecen (`RF-AC-041`, {@code notOffered}).
   */
  List<CourseLessonProgressRow> findOfUserInCourse(UUID userId, UUID courseId);

  /**
   * El avance de esa persona en cada curso, sobre las lecciones ofrecibles hoy (`RN-AC-023`), en
   * una sentencia. Los cursos sin lecciones ofrecibles no están.
   */
  Map<UUID, CourseFigures> figuresOfUser(UUID userId, Collection<UUID> courseIds);

  /** La página del listado (`RF-AC-040`), por última actividad descendente. */
  List<StudentCourseRow> search(StudentProgressFilter filtro, int offset, int size);

  long count(StudentProgressFilter filtro);

  /** Lo guardado de una persona en una lección. */
  record ProgressRow(
      UUID lessonId,
      int watchedSeconds,
      OffsetDateTime completedAt,
      OffsetDateTime firstOpenedAt,
      OffsetDateTime lastOpenedAt) {

    public boolean completada() {
      return completedAt != null;
    }
  }

  /** Una fila de progreso con su lección, ofrecible hoy o no. */
  record CourseLessonProgressRow(
      ProgressRow progress,
      UUID moduleId,
      String moduleTitle,
      String title,
      String type,
      int durationSeconds,
      int displayOrder,
      boolean offerable) {}

  /**
   * Las sumas del avance de un curso (`RN-AC-023`); el porcentaje lo calcula {@link #percent()}.
   */
  record CourseFigures(
      long lessonCount, long completedLessons, long watchedSeconds, long totalSeconds) {

    public static final CourseFigures CERO = new CourseFigures(0, 0, 0, 0);

    public int percent() {
      return LessonProgress.porcentaje(watchedSeconds, totalSeconds);
    }

    /** Todas las lecciones ofrecibles completadas, y al menos una. */
    public boolean completed() {
      return lessonCount > 0 && completedLessons == lessonCount;
    }
  }

  /** Una fila alumno–curso del listado. */
  record StudentCourseRow(
      UUID userId,
      UUID courseId,
      String courseTitle,
      String courseStatus,
      boolean courseDeleted,
      CourseFigures figures,
      OffsetDateTime firstOpenedAt,
      OffsetDateTime lastActivityAt) {}

  /**
   * El predicado del listado: el alcance —{@code everyone}, o las personas y el actor como
   * instructor— y los tres filtros.
   */
  record StudentProgressFilter(
      boolean everyone,
      Set<UUID> people,
      UUID instructorId,
      UUID userId,
      UUID courseId,
      Boolean completed) {}
}
