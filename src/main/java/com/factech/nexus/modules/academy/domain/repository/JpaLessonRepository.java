package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.Lesson;
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
 * {@link LessonRepository} sobre JPA. La pertenencia al curso se comprueba con un {@code JOIN} a
 * {@code course_modules} en la misma sentencia bloqueante: la lección se bloquea, su módulo no
 * (`RF-AC-028` §8: cada alta bloquea solo a su padre inmediato, y las escrituras sobre la lección
 * solo a la lección).
 */
@Repository
public class JpaLessonRepository implements LessonRepository {

  static final String UQ_TITULO = "uq_lessons_title";
  public static final String MENSAJE_TITULO =
      "Ya existe una lección con ese título en este módulo.";

  private final EntityManager em;

  public JpaLessonRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean existsAliveTitleInModule(UUID moduleId, String title) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM lessons
             WHERE deleted_at IS NULL
               AND module_id = CAST(:modulo AS uuid)
               AND f_unaccent(lower(title)) = f_unaccent(lower(CAST(:title AS text)))
             LIMIT 1
            """)
        .setParameter("modulo", moduleId.toString())
        .setParameter("title", title)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsAliveTitleInModuleForOther(UUID moduleId, String title, UUID lessonId) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM lessons
             WHERE deleted_at IS NULL
               AND module_id = CAST(:modulo AS uuid)
               AND id <> CAST(:id AS uuid)
               AND f_unaccent(lower(title)) = f_unaccent(lower(CAST(:title AS text)))
             LIMIT 1
            """)
        .setParameter("modulo", moduleId.toString())
        .setParameter("id", lessonId.toString())
        .setParameter("title", title)
        .getResultList()
        .isEmpty();
  }

  @Override
  public Lesson save(Lesson leccion) {
    try {
      em.persist(leccion);
      em.flush();
      return leccion;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<Lesson> findAliveByIdInModuleForUpdate(
      UUID courseId, UUID moduleId, UUID lessonId) {
    return buscar(courseId, moduleId, lessonId, true);
  }

  @Override
  public Optional<Lesson> findByIdInModuleForUpdate(UUID courseId, UUID moduleId, UUID lessonId) {
    return buscar(courseId, moduleId, lessonId, false);
  }

  private Optional<Lesson> buscar(UUID courseId, UUID moduleId, UUID lessonId, boolean soloViva) {
    if (courseId == null || moduleId == null || lessonId == null) {
      return Optional.empty();
    }
    // El módulo tiene que ser del curso de la ruta; se comprueba por consulta y
    // no por navegación, porque la entidad no conoce a su módulo.
    return em
        .createQuery(
            "SELECT l FROM Lesson l WHERE l.id = :id AND l.moduleId = :modulo"
                + (soloViva ? " AND l.deletedAt IS NULL" : "")
                + " AND EXISTS (SELECT 1 FROM CourseModule m WHERE m.id = l.moduleId"
                + " AND m.courseId = :curso)",
            Lesson.class)
        .setParameter("id", lessonId)
        .setParameter("modulo", moduleId)
        .setParameter("curso", courseId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public List<Lesson> findAliveByModuleForUpdate(UUID moduleId) {
    return em.createQuery(
            "SELECT l FROM Lesson l WHERE l.moduleId = :modulo AND l.deletedAt IS NULL"
                + " ORDER BY l.displayOrder, l.id",
            Lesson.class)
        .setParameter("modulo", moduleId)
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
