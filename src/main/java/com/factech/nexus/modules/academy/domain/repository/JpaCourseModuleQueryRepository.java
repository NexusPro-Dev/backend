package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.LessonRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ModuleRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CourseModuleQueryRepository} sobre SQL nativo.
 *
 * <p><b>Las columnas del módulo y las de la lección viven aquí y en {@link
 * JpaLessonQueryRepository}, y el árbol del curso las reutiliza</b> ({@code
 * JpaCourseQueryRepository.findModulesOf} y {@code findLessonsOfModules}): una sola definición de
 * «lección ofrecible» y de «duración del módulo» para el detalle del curso, el del módulo y el
 * aula.
 */
@Repository
public class JpaCourseModuleQueryRepository implements CourseModuleQueryRepository {

  /** Una lección ofrecible (`RN-AC-015`): activa, viva y con contenido. Alias {@code l}. */
  static final String LECCION_OFRECIBLE =
      "l.deleted_at IS NULL AND l.status = 'ACTIVO' AND l.content IS NOT NULL";

  /**
   * Un módulo ofrecible (`RN-AC-015`): activo, vivo y con al menos una lección ofrecible. Alias
   * {@code m}.
   */
  static final String MODULO_OFRECIBLE =
      "m.deleted_at IS NULL AND m.status = 'ACTIVO' AND EXISTS (SELECT 1 FROM lessons l"
          + " WHERE l.module_id = m.id AND "
          + LECCION_OFRECIBLE
          + ")";

  /** Las columnas del módulo con sus dos cuentas, sobre el alias {@code m}. */
  static final String COLUMNAS_DEL_MODULO =
      """
      m.id AS id, m.course_id AS course_id, m.title AS title,
      m.short_description AS short_description, m.long_description AS long_description,
      m.presentation_video_url AS presentation_video_url, m.display_order AS display_order,
      m.status AS status, m.cover_image_id AS cover_image_id,
      (SELECT count(*) FROM lessons l WHERE l.module_id = m.id AND
      """
          + " "
          + LECCION_OFRECIBLE
          + """
          ) AS offerable_lesson_count,
          COALESCE((SELECT sum(l.duration_minutes) FROM lessons l WHERE l.module_id = m.id
                       AND l.deleted_at IS NULL AND l.status = 'ACTIVO'), 0) AS duration_minutes,
          m.created_at AS created_at, m.updated_at AS updated_at, m.deleted_at AS deleted_at
          """;

  private final EntityManager em;

  public JpaCourseModuleQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ModuleRow> findDetail(UUID courseId, UUID moduleId) {
    if (courseId == null || moduleId == null) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT "
                    + COLUMNAS_DEL_MODULO
                    + " FROM course_modules m WHERE m.id = :id AND m.course_id = :curso",
                Tuple.class)
            .setParameter("id", moduleId)
            .setParameter("curso", courseId)
            .getResultList();
    return filas.stream().map(JpaCourseModuleQueryRepository::modulo).findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public List<LessonRow> findLessonsOf(UUID moduleId) {
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT "
                    + JpaLessonQueryRepository.COLUMNAS_DEL_RESUMEN
                    + " FROM lessons l WHERE l.module_id = :modulo"
                    + " ORDER BY l.display_order, l.id",
                Tuple.class)
            .setParameter("modulo", moduleId)
            .getResultList();
    return filas.stream().map(JpaLessonQueryRepository::resumen).toList();
  }

  static ModuleRow modulo(Tuple fila) {
    return new ModuleRow(
        (UUID) fila.get("id"),
        (UUID) fila.get("course_id"),
        (String) fila.get("title"),
        (String) fila.get("short_description"),
        (String) fila.get("long_description"),
        (String) fila.get("presentation_video_url"),
        ((Number) fila.get("display_order")).intValue(),
        (String) fila.get("status"),
        (UUID) fila.get("cover_image_id"),
        ((Number) fila.get("offerable_lesson_count")).longValue(),
        ((Number) fila.get("duration_minutes")).longValue(),
        JpaCourseQueryRepository.momento(fila.get("created_at")),
        JpaCourseQueryRepository.momento(fila.get("updated_at")),
        JpaCourseQueryRepository.momento(fila.get("deleted_at")));
  }
}
