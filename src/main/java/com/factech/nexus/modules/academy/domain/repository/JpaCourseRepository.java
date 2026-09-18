package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.Course;
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
 * {@link CourseRepository} sobre JPA.
 *
 * <p>La traducción de la unicidad por el <b>nombre</b> de la restricción, como en la categoría:
 * {@code uq_courses_title} es `EX-001` de `RF-AC-008` y de `RF-AC-011` — el mismo {@code 409} con
 * el mismo mensaje, para que quien llama no note si entró por la comprobación previa o por la
 * carrera.
 */
@Repository
public class JpaCourseRepository implements CourseRepository {

  static final String UQ_TITULO = "uq_courses_title";
  public static final String MENSAJE_TITULO = "Ya existe un curso con ese título.";

  private final EntityManager em;

  public JpaCourseRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean existsAliveTitle(String title) {
    // El mismo predicado que `uq_courses_title`, a propósito.
    return !em.createNativeQuery(
            """
            SELECT 1 FROM courses
             WHERE deleted_at IS NULL
               AND f_unaccent(lower(title)) = f_unaccent(lower(CAST(:title AS text)))
             LIMIT 1
            """)
        .setParameter("title", title)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsAliveTitleForOther(String title, UUID courseId) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM courses
             WHERE deleted_at IS NULL
               AND id <> CAST(:id AS uuid)
               AND f_unaccent(lower(title)) = f_unaccent(lower(CAST(:title AS text)))
             LIMIT 1
            """)
        .setParameter("title", title)
        .setParameter("id", courseId.toString())
        .getResultList()
        .isEmpty();
  }

  @Override
  public Course save(Course curso) {
    try {
      em.persist(curso);
      em.flush();
      return curso;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<Course> findAliveByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT c FROM Course c WHERE c.id = :id AND c.deletedAt IS NULL", Course.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Course> findByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT c FROM Course c WHERE c.id = :id", Course.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public void flush() {
    try {
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  static RuntimeException traducir(PersistenceException fallo) {
    if (UQ_TITULO.equals(nombreDeRestriccion(fallo))) {
      return new BusinessRuleException(
          "EX-001", MENSAJE_TITULO, List.of(new FieldError("title", "EX-001", MENSAJE_TITULO)));
    }
    return fallo;
  }

  static String nombreDeRestriccion(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion) {
        return violacion.getConstraintName();
      }
    }
    return null;
  }
}
