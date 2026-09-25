package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.CourseCategory;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link CourseCategoryRepository} sobre JPA.
 *
 * <p><b>La traducción de la unicidad es lo único que este repositorio decide</b>, y la decide por
 * el <b>nombre</b> de la restricción, como en `PM`: {@code uq_course_categories_name} es `EX-001`
 * de `RF-AC-001` y `EX-002` de `RF-AC-004` — el mismo {@code 409} con el mismo mensaje, para que
 * quien llama no note si entró por la comprobación previa o por la carrera.
 */
@Repository
public class JpaCourseCategoryRepository implements CourseCategoryRepository {

  private static final String UQ_NOMBRE = "uq_course_categories_name";

  private final EntityManager em;

  public JpaCourseCategoryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean existsAliveName(String name) {
    // El mismo predicado que `uq_course_categories_name`, a propósito: si esta
    // lectura mirara otra cosa que el índice, la comprobación previa y la
    // traducción de la carrera dejarían pasar casos distintos.
    return !em.createNativeQuery(
            """
            SELECT 1 FROM course_categories
             WHERE deleted_at IS NULL
               AND f_unaccent(lower(name)) = f_unaccent(lower(CAST(:name AS text)))
             LIMIT 1
            """)
        .setParameter("name", name)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsAliveNameForOther(String name, UUID categoryId) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM course_categories
             WHERE deleted_at IS NULL
               AND id <> CAST(:id AS uuid)
               AND f_unaccent(lower(name)) = f_unaccent(lower(CAST(:name AS text)))
             LIMIT 1
            """)
        .setParameter("name", name)
        .setParameter("id", categoryId.toString())
        .getResultList()
        .isEmpty();
  }

  @Override
  public CourseCategory save(CourseCategory categoria) {
    try {
      em.persist(categoria);
      em.flush();
      return categoria;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<CourseCategory> findAliveByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT c FROM CourseCategory c WHERE c.id = :id AND c.deletedAt IS NULL",
            CourseCategory.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<CourseCategory> findAliveById(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT c FROM CourseCategory c WHERE c.id = :id AND c.deletedAt IS NULL",
            CourseCategory.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public List<CourseCategory> findAliveByIds(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return em.createQuery(
            "SELECT c FROM CourseCategory c WHERE c.id IN :ids AND c.deletedAt IS NULL",
            CourseCategory.class)
        .setParameter("ids", ids)
        .getResultList();
  }

  @Override
  public Optional<CourseCategory> findByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT c FROM CourseCategory c WHERE c.id = :id", CourseCategory.class)
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

  private static RuntimeException traducir(PersistenceException fallo) {
    if (UQ_NOMBRE.equals(nombreDeRestriccion(fallo))) {
      String mensaje = "Ya existe una categoría con ese nombre.";
      return new BusinessRuleException(
          "EX-001", mensaje, List.of(new FieldError("name", "EX-001", mensaje)));
    }
    return fallo;
  }

  private static String nombreDeRestriccion(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion) {
        return violacion.getConstraintName();
      }
    }
    return null;
  }
}
