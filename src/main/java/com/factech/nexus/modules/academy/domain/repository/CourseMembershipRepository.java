package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.CourseMembership;
import java.util.Optional;
import java.util.UUID;

/**
 * Las filas de visibilidad por membresía (`RF-AC-020`, `RF-AC-021`), con la forma de {@link
 * CourseProductRepository}: bajo el bloqueo del curso, y la clave primaria como red traducida al
 * mismo {@code 409}.
 */
public interface CourseMembershipRepository {

  /** Inserta traduciendo {@code pk_course_memberships} al `409` de `EX-003`, que la nombra. */
  CourseMembership save(CourseMembership fila, String codigoDeLaMembresia);

  Optional<CourseMembership> find(UUID courseId, UUID membershipId);

  void delete(CourseMembership fila);
}
