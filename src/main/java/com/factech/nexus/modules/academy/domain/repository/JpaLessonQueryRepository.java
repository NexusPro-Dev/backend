package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.LessonRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link LessonQueryRepository} sobre SQL nativo. <b>Dos bloques de columnas</b>: el resumen —sin
 * contenido, solo si lo tiene— para el árbol del curso y del módulo, y el detalle con el contenido
 * entero para quien lo pide por lección.
 */
@Repository
public class JpaLessonQueryRepository implements LessonQueryRepository {

  /** El resumen de una lección para un árbol, sobre el alias {@code l}: sin contenido. */
  static final String COLUMNAS_DEL_RESUMEN =
      """
      l.id AS id, l.module_id AS module_id, l.type AS type, l.title AS title,
      l.description AS description, l.duration_seconds AS duration_seconds,
      l.display_order AS display_order, l.status AS status, l.open AS open,
      (l.content IS NOT NULL) AS has_content, l.deleted_at AS deleted_at
      """;

  private static final String COLUMNAS_DEL_DETALLE =
      """
      l.id AS id, l.module_id AS module_id, m.course_id AS course_id, l.type AS type,
      l.title AS title, l.description AS description, l.content AS content,
      l.duration_seconds AS duration_seconds, l.display_order AS display_order,
      l.open AS open, l.status AS status,
      l.created_at AS created_at, l.updated_at AS updated_at, l.deleted_at AS deleted_at
      """;

  private final EntityManager em;

  public JpaLessonQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LessonDetailRow> findDetail(UUID courseId, UUID moduleId, UUID lessonId) {
    if (courseId == null || moduleId == null || lessonId == null) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT "
                    + COLUMNAS_DEL_DETALLE
                    + " FROM lessons l JOIN course_modules m ON m.id = l.module_id"
                    + " WHERE l.id = :id AND l.module_id = :modulo AND m.course_id = :curso",
                Tuple.class)
            .setParameter("id", lessonId)
            .setParameter("modulo", moduleId)
            .setParameter("curso", courseId)
            .getResultList();
    return filas.stream().findFirst().map(JpaLessonQueryRepository::detalle);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ClassroomLessonRow> findClassroomLesson(UUID courseId, UUID lessonId) {
    if (courseId == null || lessonId == null) {
      return Optional.empty();
    }
    // Las subconsultas reutilizan los alias `l` y `m` en su propio ámbito: el
    // de dentro gana, y cuentan lo del módulo y lo del curso, no esta fila.
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT "
                    + COLUMNAS_DEL_DETALLE
                    + ", m.status AS m_status, (m.deleted_at IS NOT NULL) AS m_retirado,"
                    + " (SELECT count(*) FROM lessons l WHERE l.module_id = m.id AND "
                    + JpaCourseModuleQueryRepository.LECCION_OFRECIBLE
                    + ") AS m_ofrecibles,"
                    + " c.status AS c_status, (c.deleted_at IS NOT NULL) AS c_retirado,"
                    + " (c.short_description IS NOT NULL) AS c_corta,"
                    + " (c.long_description IS NOT NULL) AS c_larga, "
                    + JpaCourseQueryRepository.CUENTA_DE_MODULOS_OFRECIBLES
                    + " AS c_modulos, "
                    + JpaCourseQueryRepository.CUENTA_DE_MEMBRESIAS
                    + " AS c_membresias, "
                    + JpaCourseQueryRepository.CUENTA_DE_SERVICIOS
                    + " AS c_servicios"
                    + " FROM lessons l JOIN course_modules m ON m.id = l.module_id"
                    + " JOIN courses c ON c.id = m.course_id AND c.id = :curso"
                    + " WHERE l.id = :id",
                Tuple.class)
            .setParameter("id", lessonId)
            .setParameter("curso", courseId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            fila ->
                new ClassroomLessonRow(
                    detalle(fila),
                    (String) fila.get("m_status"),
                    (Boolean) fila.get("m_retirado"),
                    ((Number) fila.get("m_ofrecibles")).longValue(),
                    (String) fila.get("c_status"),
                    (Boolean) fila.get("c_retirado"),
                    (Boolean) fila.get("c_corta"),
                    (Boolean) fila.get("c_larga"),
                    ((Number) fila.get("c_modulos")).longValue(),
                    ((Number) fila.get("c_membresias")).longValue(),
                    ((Number) fila.get("c_servicios")).longValue()));
  }

  private static LessonDetailRow detalle(Tuple fila) {
    return new LessonDetailRow(
        (UUID) fila.get("id"),
        (UUID) fila.get("module_id"),
        (UUID) fila.get("course_id"),
        (String) fila.get("type"),
        (String) fila.get("title"),
        (String) fila.get("description"),
        (String) fila.get("content"),
        ((Number) fila.get("duration_seconds")).intValue(),
        ((Number) fila.get("display_order")).intValue(),
        (Boolean) fila.get("open"),
        (String) fila.get("status"),
        JpaCourseQueryRepository.momento(fila.get("created_at")),
        JpaCourseQueryRepository.momento(fila.get("updated_at")),
        JpaCourseQueryRepository.momento(fila.get("deleted_at")));
  }

  static LessonRow resumen(Tuple fila) {
    return new LessonRow(
        (UUID) fila.get("id"),
        (UUID) fila.get("module_id"),
        (String) fila.get("type"),
        (String) fila.get("title"),
        (String) fila.get("description"),
        ((Number) fila.get("duration_seconds")).intValue(),
        ((Number) fila.get("display_order")).intValue(),
        (String) fila.get("status"),
        (Boolean) fila.get("open"),
        (Boolean) fila.get("has_content"),
        JpaCourseQueryRepository.momento(fila.get("deleted_at")));
  }
}
