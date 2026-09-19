package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.CourseModule;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link CourseModuleRepository} sobre JPA. La unicidad se traduce por el nombre de la restricción,
 * como en el curso: {@code uq_course_modules_title} es `EX-002` de `RF-AC-022` y `EX-001` de
 * `RF-AC-023`, el mismo {@code 409} con el mismo mensaje.
 */
@Repository
public class JpaCourseModuleRepository implements CourseModuleRepository {

  static final String UQ_TITULO = "uq_course_modules_title";
  public static final String MENSAJE_TITULO = "Ya existe un módulo con ese título en este curso.";

  private final EntityManager em;

  public JpaCourseModuleRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean existsAliveTitleInCourse(UUID courseId, String title) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM course_modules
             WHERE deleted_at IS NULL
               AND course_id = CAST(:curso AS uuid)
               AND f_unaccent(lower(title)) = f_unaccent(lower(CAST(:title AS text)))
             LIMIT 1
            """)
        .setParameter("curso", courseId.toString())
        .setParameter("title", title)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsAliveTitleInCourseForOther(UUID courseId, String title, UUID moduleId) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM course_modules
             WHERE deleted_at IS NULL
               AND course_id = CAST(:curso AS uuid)
               AND id <> CAST(:id AS uuid)
               AND f_unaccent(lower(title)) = f_unaccent(lower(CAST(:title AS text)))
             LIMIT 1
            """)
        .setParameter("curso", courseId.toString())
        .setParameter("id", moduleId.toString())
        .setParameter("title", title)
        .getResultList()
        .isEmpty();
  }

  @Override
  public CourseModule save(CourseModule modulo) {
    try {
      em.persist(modulo);
      em.flush();
      return modulo;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<CourseModule> findAliveByIdInCourseForUpdate(UUID courseId, UUID moduleId) {
    if (courseId == null || moduleId == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT m FROM CourseModule m WHERE m.id = :id AND m.courseId = :curso"
                + " AND m.deletedAt IS NULL",
            CourseModule.class)
        .setParameter("id", moduleId)
        .setParameter("curso", courseId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<CourseModule> findByIdInCourseForUpdate(UUID courseId, UUID moduleId) {
    if (courseId == null || moduleId == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT m FROM CourseModule m WHERE m.id = :id AND m.courseId = :curso",
            CourseModule.class)
        .setParameter("id", moduleId)
        .setParameter("curso", courseId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public List<CourseModule> findAliveByCourseForUpdate(UUID courseId) {
    return em.createQuery(
            "SELECT m FROM CourseModule m WHERE m.courseId = :curso AND m.deletedAt IS NULL"
                + " ORDER BY m.displayOrder, m.id",
            CourseModule.class)
        .setParameter("curso", courseId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultList();
  }

  @Override
  public void flush() {
    try {
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  private static RuntimeException traducir(PersistenceException fallo) {
    if (UQ_TITULO.equals(JpaCourseRepository.nombreDeRestriccion(fallo))) {
      return new BusinessRuleException(
          "EX-002", MENSAJE_TITULO, List.of(new FieldError("title", "EX-002", MENSAJE_TITULO)));
    }
    return fallo;
  }
}
