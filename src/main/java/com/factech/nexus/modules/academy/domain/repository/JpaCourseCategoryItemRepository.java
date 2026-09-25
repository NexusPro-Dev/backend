package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.CourseCategoryItem;
import com.factech.nexus.modules.academy.domain.models.CourseCategoryItemId;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link CourseCategoryItemRepository} sobre JPA. */
@Repository
public class JpaCourseCategoryItemRepository implements CourseCategoryItemRepository {

  private static final String PK = "pk_course_category_items";

  private final EntityManager em;

  public JpaCourseCategoryItemRepository(EntityManager em) {
    this.em = em;
  }

  /** El `409` de `RF-AC-016` `EX-003`, el mismo por la comprobación previa y por la carrera. */
  public static BusinessRuleException yaClasificado(String nombreDeLaCategoria) {
    String mensaje =
        "El curso ya está clasificado en la categoría %s.".formatted(nombreDeLaCategoria);
    return new BusinessRuleException(
        "EX-003", mensaje, List.of(new FieldError("categoryId", "EX-003", mensaje)));
  }

  @Override
  public CourseCategoryItem save(CourseCategoryItem fila, String nombreDeLaCategoria) {
    try {
      em.persist(fila);
      em.flush();
      return fila;
    } catch (PersistenceException fallo) {
      for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
        if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
            && PK.equals(violacion.getConstraintName())) {
          // El camino de la CARRERA: la comprobación previa no vio la fila porque
          // todavía no estaba. Responde lo mismo que ella.
          throw yaClasificado(nombreDeLaCategoria);
        }
      }
      throw fallo;
    }
  }

  @Override
  public Optional<CourseCategoryItem> find(UUID courseId, UUID categoryId) {
    if (courseId == null || categoryId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        em.find(CourseCategoryItem.class, new CourseCategoryItemId(courseId, categoryId)));
  }

  @Override
  public void delete(CourseCategoryItem fila) {
    em.remove(fila);
    em.flush();
  }
}
