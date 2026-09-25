package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.CourseProduct;
import com.factech.nexus.modules.academy.domain.models.CourseProductId;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link CourseProductRepository} sobre JPA. */
@Repository
public class JpaCourseProductRepository implements CourseProductRepository {

  private static final String PK = "pk_course_products";

  private final EntityManager em;

  public JpaCourseProductRepository(EntityManager em) {
    this.em = em;
  }

  /** El `409` de `RF-AC-037` `EX-004`, el mismo por la comprobación previa y por la carrera. */
  public static BusinessRuleException yaAbre(String codigoDelProducto) {
    String mensaje = "El servicio %s ya abre este curso.".formatted(codigoDelProducto);
    return new BusinessRuleException(
        "EX-004", mensaje, List.of(new FieldError("productId", "EX-004", mensaje)));
  }

  @Override
  public CourseProduct save(CourseProduct fila, String codigoDelProducto) {
    try {
      em.persist(fila);
      em.flush();
      return fila;
    } catch (PersistenceException fallo) {
      for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
        if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
            && PK.equals(violacion.getConstraintName())) {
          throw yaAbre(codigoDelProducto);
        }
      }
      throw fallo;
    }
  }

  @Override
  public Optional<CourseProduct> find(UUID courseId, UUID productId) {
    if (courseId == null || productId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        em.find(CourseProduct.class, new CourseProductId(courseId, productId)));
  }

  @Override
  public void delete(CourseProduct fila) {
    em.remove(fila);
    em.flush();
  }
}
