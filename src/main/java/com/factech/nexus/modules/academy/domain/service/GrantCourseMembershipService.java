package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.application.GrantCourseMembershipRequest;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.repository.CourseMembershipRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import com.factech.nexus.modules.academy.domain.repository.JpaCourseMembershipRepository;
import com.factech.nexus.modules.system.memberships.application.MembershipCatalog;
import com.factech.nexus.modules.system.memberships.application.MembershipCatalog.MembershipView;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-020`: dar visibilidad de un curso a una membresía.
 *
 * <p>`RF-AC-037` con la membresía de `SP` en lugar del servicio de `PM`: el curso bloqueado, la
 * membresía por {@link MembershipCatalog} —el puerto que `PM` pidió el 27-08-2026—, la pareja bajo
 * el bloqueo, y la escritura en {@link CourseAccessWriter}. <b>Una lista y no un nivel mínimo</b>:
 * dar {@code ORO} no abre el curso a {@code PLATINO}. <b>No exige que el curso esté activo</b>: la
 * lista se arma antes de publicar.
 */
@Service
public class GrantCourseMembershipService {

  private final CourseRepository cursos;
  private final MembershipCatalog catalogo;
  private final CourseMembershipRepository filas;
  private final CourseAccessWriter llaves;
  private final CourseDetailReader detalle;

  public GrantCourseMembershipService(
      CourseRepository cursos,
      MembershipCatalog catalogo,
      CourseMembershipRepository filas,
      CourseAccessWriter llaves,
      CourseDetailReader detalle) {
    this.cursos = cursos;
    this.catalogo = catalogo;
    this.filas = filas;
    this.llaves = llaves;
    this.detalle = detalle;
  }

  @Transactional
  public CourseDetailResponse grant(UUID courseId, GrantCourseMembershipRequest peticion) {
    Course curso = ClassifyCourseService.cursoVivo(cursos, courseId);

    MembershipView membresia =
        catalogo
            .find(peticion.membershipId())
            .orElseThrow(
                () -> {
                  String mensaje = "La membresía indicada no existe.";
                  return new UnprocessableEntityException(
                      "EX-002",
                      mensaje,
                      List.of(new FieldError("membershipId", "EX-002", mensaje)));
                });

    if (filas.find(curso.getId(), membresia.id()).isPresent()) {
      throw JpaCourseMembershipRepository.yaAbre(membresia.code());
    }

    llaves.darMembresia(curso.getId(), membresia);
    return detalle.leer(curso.getId());
  }
}
