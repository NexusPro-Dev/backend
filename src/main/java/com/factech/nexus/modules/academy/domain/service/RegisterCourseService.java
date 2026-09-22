package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.application.RegisterCourseRequest;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import com.factech.nexus.modules.academy.domain.repository.JpaCourseRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-008`: registrar un curso.
 *
 * <p><b>El alta de la categoría con una entidad más ancha, dos puertos de `SP` y un objeto de
 * ofrecibilidad por delante.</b> Título contra los vivos, el instructor por {@link
 * InstructorVerifier} —existe, no retirado, porta {@code courses:teach}—, inserción en {@code
 * INACTIVO}, auditoría, y la relectura del detalle con su forma completa: listas vacías, portada
 * nula y {@code offerable: false} diciendo «inactivo». Cinco sentencias y una más para releer.
 */
@Service
public class RegisterCourseService {

  private final CourseRepository cursos;
  private final InstructorVerifier instructor;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final CourseDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public RegisterCourseService(
      CourseRepository cursos,
      InstructorVerifier instructor,
      AuditWriter auditoria,
      UuidV7Generator ids,
      CourseDetailReader detalle) {
    this(cursos, instructor, auditoria, ids, detalle, Clock.systemUTC());
  }

  RegisterCourseService(
      CourseRepository cursos,
      InstructorVerifier instructor,
      AuditWriter auditoria,
      UuidV7Generator ids,
      CourseDetailReader detalle,
      Clock reloj) {
    this.cursos = cursos;
    this.instructor = instructor;
    this.auditoria = auditoria;
    this.ids = ids;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public CourseDetailResponse register(RegisterCourseRequest peticion) {
    // Solo contra los VIVOS (`EX-001`): un retirado libera el título. La red
    // es el índice parcial, que muerde en el INSERT y sale con el mismo código.
    if (peticion.title() != null && cursos.existsAliveTitle(peticion.title())) {
      throw new BusinessRuleException(
          "EX-001",
          JpaCourseRepository.MENSAJE_TITULO,
          List.of(new FieldError("title", "EX-001", JpaCourseRepository.MENSAJE_TITULO)));
    }

    instructor.verificar(peticion.instructorId());

    Course nuevo =
        cursos.save(
            Course.create(
                ids.next(),
                peticion.title(),
                peticion.instructorId(),
                peticion.difficulty(),
                peticion.shortDescription(),
                peticion.longDescription(),
                peticion.introVideoUrl(),
                peticion.displayOrder(),
                OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(
            CourseDetailReader.MODULO,
            CourseDetailReader.ENTIDAD,
            nuevo.getId(),
            ChangeAction.CREATE,
            nuevo.instantanea()));

    return detalle.leer(nuevo.getId());
  }
}
