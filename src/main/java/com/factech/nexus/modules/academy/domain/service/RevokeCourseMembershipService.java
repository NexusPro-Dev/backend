package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.models.CourseMembership;
import com.factech.nexus.modules.academy.domain.repository.CourseMembershipRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-021`: quitar la visibilidad de un curso a una membresía.
 *
 * <p>{@link RevokeCourseProductService} con la membresía: <b>sin motivo, borrado físico, {@code
 * ASSOCIATION}</b>, y el curso en la respuesta. <b>Quitar la última nunca se rechaza</b>: si
 * tampoco tiene servicios, el curso deja de ofrecerse y el detalle lo dice.
 */
@Service
public class RevokeCourseMembershipService {

  private final CourseRepository cursos;
  private final CourseMembershipRepository filas;
  private final CourseQueryRepository consultas;
  private final AuditWriter auditoria;
  private final CourseDetailReader detalle;

  public RevokeCourseMembershipService(
      CourseRepository cursos,
      CourseMembershipRepository filas,
      CourseQueryRepository consultas,
      AuditWriter auditoria,
      CourseDetailReader detalle) {
    this.cursos = cursos;
    this.filas = filas;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.detalle = detalle;
  }

  @Transactional
  public CourseDetailResponse revoke(UUID courseId, UUID membershipId) {
    Course curso = ClassifyCourseService.cursoVivo(cursos, courseId);
    CourseMembership fila =
        filas
            .find(curso.getId(), membershipId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-002", "La membresía indicada no abre este curso."));

    // La instantánea ANTES de borrar: después el JOIN ya no encuentra la fila.
    String codigo =
        consultas.findMembershipsOf(curso.getId()).stream()
            .filter(m -> m.id().equals(membershipId))
            .map(MembershipRef::code)
            .findFirst()
            .orElse(null);
    Map<String, Object> instantanea = fila.instantanea(codigo);

    filas.delete(fila);

    auditoria.recordDeletion(
        new DeletionEvent(
            CourseDetailReader.MODULO,
            CourseAccessWriter.ENTIDAD_MEMBRESIAS,
            curso.getId(),
            DeletionType.ASSOCIATION,
            null,
            instantanea));

    return detalle.leer(curso.getId());
  }
}
