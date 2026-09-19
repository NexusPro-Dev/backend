package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.LessonRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ModuleRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las lecturas del módulo para las operaciones que devuelven el módulo (`RF-AC-022` a `RF-AC-027`):
 * su fila con las cuentas, y sus lecciones en orden. Las mismas proyecciones que el árbol del curso
 * (`CourseQueryRepository`), leídas para un módulo.
 */
public interface CourseModuleQueryRepository {

  /** El módulo de ese curso, en cualquier estado; vacío si no existe o es de otro curso. */
  Optional<ModuleRow> findDetail(UUID courseId, UUID moduleId);

  /** Las lecciones del módulo en su orden, vivas y retiradas, sin contenido. */
  List<LessonRow> findLessonsOf(UUID moduleId);
}
