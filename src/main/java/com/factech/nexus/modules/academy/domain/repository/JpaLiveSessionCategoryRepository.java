package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.LiveSessionCategory;
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
 * {@link LiveSessionCategoryRepository} sobre JPA. Como {@link JpaCourseCategoryRepository}, la
 * traducción de la unicidad se decide por el <b>nombre</b> de la restricción.
 */
@Repository
public class JpaLiveSessionCategoryRepository implements LiveSessionCategoryRepository {

  private static final String UQ_NOMBRE = "uq_live_session_categories_name";
  static final String NOMBRE_REPETIDO = "Ya existe una categoría con ese nombre.";

  private final EntityManager em;

  public JpaLiveSessionCategoryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean existsAliveName(String name, UUID exceptId) {
    // El mismo predicado que `uq_live_session_categories_name`.
    return !em.createNativeQuery(
            """
            SELECT 1 FROM live_session_categories
             WHERE deleted_at IS NULL
               AND id <> CAST(:id AS uuid)
               AND f_unaccent(lower(name)) = f_unaccent(lower(CAST(:name AS text)))
             LIMIT 1
            """)
        .setParameter("name", name)
        .setParameter("id", (exceptId == null ? new UUID(0, 0) : exceptId).toString())
        .getResultList()
        .isEmpty();
  }

  @Override
  public LiveSessionCategory save(LiveSessionCategory categoria, String codigo) {
    try {
      em.persist(categoria);
      em.flush();
      return categoria;
    } catch (PersistenceException fallo) {
      throw traducir(fallo, codigo);
    }
  }

  @Override
  public void flush(String codigo) {
    try {
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo, codigo);
    }
  }

  @Override
  public Optional<LiveSessionCategory> findByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT c FROM LiveSessionCategory c WHERE c.id = :id", LiveSessionCategory.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<LiveSessionCategory> findAliveById(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT c FROM LiveSessionCategory c WHERE c.id = :id AND c.deletedAt IS NULL",
            LiveSessionCategory.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public List<LiveSessionCategory> findAlive() {
    return em.createQuery(
            "SELECT c FROM LiveSessionCategory c WHERE c.deletedAt IS NULL"
                + " ORDER BY c.displayOrder, lower(c.name), c.id",
            LiveSessionCategory.class)
        .getResultList();
  }

  private static RuntimeException traducir(PersistenceException fallo, String codigo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
          && UQ_NOMBRE.equals(violacion.getConstraintName())) {
        return new BusinessRuleException(
            codigo, NOMBRE_REPETIDO, List.of(new FieldError("name", codigo, NOMBRE_REPETIDO)));
      }
    }
    return fallo;
  }
}
