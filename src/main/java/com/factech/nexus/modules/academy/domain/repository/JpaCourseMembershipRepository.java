package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.CourseMembership;
import com.factech.nexus.modules.academy.domain.models.CourseMembershipId;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link CourseMembershipRepository} sobre JPA. */
@Repository
public class JpaCourseMembershipRepository implements CourseMembershipRepository {

  private static final String PK = "pk_course_memberships";

  private final EntityManager em;

  public JpaCourseMembershipRepository(EntityManager em) {
    this.em = em;
  }

  /** El `409` de `RF-AC-020` `EX-003`, el mismo por la comprobación previa y por la carrera. */
  public static BusinessRuleException yaAbre(String codigoDeLaMembresia) {
    String mensaje = "La membresía %s ya abre este curso.".formatted(codigoDeLaMembresia);
    return new BusinessRuleException(
        "EX-003", mensaje, List.of(new FieldError("membershipId", "EX-003", mensaje)));
  }

  @Override
  public CourseMembership save(CourseMembership fila, String codigoDeLaMembresia) {
    try {
      em.persist(fila);
      em.flush();
      return fila;
    } catch (PersistenceException fallo) {
      for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
        if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
            && PK.equals(violacion.getConstraintName())) {
          throw yaAbre(codigoDeLaMembresia);
        }
      }
      throw fallo;
    }
  }

  @Override
  public Optional<CourseMembership> find(UUID courseId, UUID membershipId) {
    if (courseId == null || membershipId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        em.find(CourseMembership.class, new CourseMembershipId(courseId, membershipId)));
  }

  @Override
  public void delete(CourseMembership fila) {
    em.remove(fila);
    em.flush();
  }
}
