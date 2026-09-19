package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.CourseModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Escritura y lectura bloqueante del módulo (`RF-AC-022` a `RF-AC-025`, y las lecciones, que
 * bloquean su módulo antes de escribir).
 *
 * <p><b>Toda lectura por identificador lleva el curso</b>: la ruta afirma la pertenencia, y un
 * módulo de otro curso es «no existe» (`404`) y no un `409`. La unicidad del título es dentro del
 * curso, comprobada antes y traducida después por {@code uq_course_modules_title}.
 */
public interface CourseModuleRepository {

  boolean existsAliveTitleInCourse(UUID courseId, String title);

  boolean existsAliveTitleInCourseForOther(UUID courseId, String title, UUID moduleId);

  CourseModule save(CourseModule modulo);

  Optional<CourseModule> findAliveByIdInCourseForUpdate(UUID courseId, UUID moduleId);

  Optional<CourseModule> findByIdInCourseForUpdate(UUID courseId, UUID moduleId);

  /** Los módulos vivos del curso, bloqueados, en su orden: lo que el arrastre recorre. */
  List<CourseModule> findAliveByCourseForUpdate(UUID courseId);

  void flush();
}
