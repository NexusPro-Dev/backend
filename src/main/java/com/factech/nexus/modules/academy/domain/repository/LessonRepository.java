package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.Lesson;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Escritura y lectura bloqueante de la lección (`RF-AC-028` a `RF-AC-031`).
 *
 * <p><b>Toda lectura por identificador lleva el módulo y el curso</b>: la ruta afirma la
 * pertenencia en los tres niveles, y una lección de otro módulo —o de un módulo de otro curso— es
 * «no existe». La unicidad del título es dentro del módulo, traducida por {@code uq_lessons_title}.
 */
public interface LessonRepository {

  boolean existsAliveTitleInModule(UUID moduleId, String title);

  boolean existsAliveTitleInModuleForOther(UUID moduleId, String title, UUID lessonId);

  Lesson save(Lesson leccion);

  Optional<Lesson> findAliveByIdInModuleForUpdate(UUID courseId, UUID moduleId, UUID lessonId);

  Optional<Lesson> findByIdInModuleForUpdate(UUID courseId, UUID moduleId, UUID lessonId);

  /** Las lecciones vivas del módulo, bloqueadas, en su orden: lo que el arrastre recorre. */
  List<Lesson> findAliveByModuleForUpdate(UUID moduleId);

  void flush();
}
